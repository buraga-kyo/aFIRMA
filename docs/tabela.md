# Manual de Uso: Tabela Genérica aFIRMA

O componente `aFirmaTabela` foi criado para centralizar e padronizar toda a lógica complexa de listagem do sistema (paginação, redimensionamento de colunas, ordenação, persistência de filtros no LocalStorage, busca com IA, reordenação e seleção de linhas), mantendo o ecossistema Vanilla JS leve e sem acoplamento.

O backend (Java/Spring Boot) **não** deve conhecer as colunas ou as larguras. Ele deve apenas fornecer um endpoint REST retornando a lista de dados JSON e receber requisições padrões. A apresentação visual é inteiramente configurada no frontend através deste componente.

## Instalação

Adicione o script `tabela-core.js` no seu HTML **antes** do seu arquivo de script específico da página:

```html
<script src="/js/tabela-core.js" defer></script>
<script src="/js/sua-pagina.js" defer></script>
```

## Requisitos de HTML

Para o componente localizar a interface, você deve usar um prefixo (chamado de `containerId`, por padrão `tabela`). Os seguintes IDs devem existir dentro da sua página (exemplo usando o prefixo `tabela`):

- **Tabela:**
  - `<div id="tabela-scroll">` (O container que rola horizontalmente)
    - `<table id="tabela-tabela">`
      - `<colgroup id="tabela-colunas"></colgroup>`
      - `<thead><tr id="tabela-cabecalho"></tr></thead>`
      - `<tbody id="tabela-corpo"></tbody>`
- **Ferramentas:**
  - `<div id="tabela-chips"></div>` (Exibe os filtros ativos)
  - `<button id="tabela-colunas-botao">` (Abre o seletor de colunas visíveis)
  - `<div id="tabela-menu-colunas" class="menu"></div>` (O menu gerado para selecionar colunas)
  - `<div id="tabela-estado"></div>` (Exibe "Carregando..." ou erros)
- **Busca por IA (Opcional):**
  - `<form id="ia-form">` (O form de submissão do texto)
  - `<div id="tabela-plano-ia" class="menu"></div>` (Painel que exibe o plano gerado)
- **Paginação:**
  - `<button id="tabela-pagina-texto">` (Botão "PAGE X OF Y")
  - `<div id="tabela-menu-pagina" class="menu"></div>` (Menu de salto de página)
  - `<nav id="tabela-paginacao"></nav>` (Links numéricos de páginas)

## Como Inicializar

Dentro do seu script específico (`sua-pagina.js`), instancie a tabela chamando `window.aFirmaTabela()` passando o objeto de configuração:

```javascript
const tabela = window.aFirmaTabela({
  containerId: 'tabela',              // Prefixo para buscar os IDs no HTML
  endpoint: '/api/clientes',          // Endpoint raiz para buscar os dados (GET)
  storageId: 'assineai-clientes',     // Nome usado no LocalStorage para salvar filtros e larguras
  iaFormId: 'ia-form',                // Opcional: ID do form de busca por IA
  iaEndpoint: '/plano-busca',         // Opcional: Endpoint POST de interpretação (padrão: /plano-busca)
  
  // Função que retorna headers, como tokens JWT e API Keys, para injetar no fetch()
  credenciais: () => ({ 'X-API-Key': '123...', Authorization: 'Bearer ...' }),
  
  // Função opcional para exibir mensagens na tela (compatível com a toast() do sistema)
  toast: (msg, erro = false) => alert(msg),

  // Definição das colunas de dados
  colunas: [
    { id: 'id', nome: 'ID', tipo: 'texto', largura: 150 },
    { id: 'nome', nome: 'NOME', tipo: 'texto', largura: 300 },
    { id: 'dataCadastro', nome: 'CADASTRO', tipo: 'data', largura: 160 },
    { id: 'status', nome: 'STATUS', tipo: 'enum', largura: 150 }
  ],

  // Hook para desenhar a coluna de AÇÕES para a linha específica
  renderAcoes: (item, acoesTd, estado, render, api) => {
    const btnExcluir = document.createElement('button');
    btnExcluir.textContent = '✖';
    btnExcluir.onclick = () => api(`/${item.id}`, { method: 'DELETE' }).then(() => tabela.carregar());
    acoesTd.append(btnExcluir);
  },

  // (Opcional) Hook para interceptar a renderização do conteúdo das outras células
  renderCelula: (item, idColuna, td, estado, render, api) => {
    if (idColuna === 'nome') {
      td.innerHTML = `<strong>${item.nome}</strong>`;
    } else {
      td.textContent = item[idColuna] || '—';
    }
  },

  // (Opcional) Callback de quando o usuário clica numa linha
  onRowClick: (item, estado, render) => {
    console.log("Clicou no item:", item.nome);
  }
});

// A tabela já renderiza a casca automaticamente.
// Para buscar os dados da API:
tabela.carregar();
```

## API do Componente

A instância retornada de `aFirmaTabela` possui os seguintes métodos:

- **`carregar()`**: Dispara um GET no `endpoint`, carrega os dados e atualiza a tela.
- **`dados()`**: Retorna os itens carregados que estão **visíveis** neste momento (aplicando ordenação e os filtros atuais).
- **`obterSelecionados()`**: Retorna um array de Strings com os IDs de todos os itens que tiveram seus checkboxes marcados pelo usuário.
- **`getEstado()`**: Retorna o estado interno do componente (útil para ver quais colunas estão visíveis em `estado.visiveis`).
- **`render()`**: Força o redesenho da tabela sem buscar os dados na rede novamente.

## Por que extrair isso para JS e não para o Backend?
Como definido na filosofia *Lazy Senior* (Ponytail Mode) do projeto: o Java (backend) não precisa saber o tamanho em pixels ou o nome de exibição de uma coluna. Mudar uma largura de 150px para 200px não deve exigir recompilação de código Java.

Com o `tabela-core.js`, todas as tabelas do sistema se beneficiam automaticamente de arrastar e soltar colunas, busca generativa com GPT, caching de layout e seleção por checkboxes, deixando os arquivos locais de cada página responsáveis apenas pelos botões que lhe são específicos.