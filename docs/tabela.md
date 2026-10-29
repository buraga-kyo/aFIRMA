# Manual da FirmaTabela

`FirmaTabela` monta a tabela para você. Passe o elemento onde ela vai ficar, as colunas e os recursos que quer usar. Os botões e as regras da sua tela ficam no código da página, junto com a autenticação.

O código fica em `src/main/resources/static/js/tabela-core.js` e o CSS em `src/main/resources/static/css/tabela-core.css`. Para ver funcionando na Firma, olhe `documentos-assinatura.js` e `templates/documentos-assinatura.html`: é a tabela do trator de assinaturas.

## Pra começar

Carregue o CSS e o script antes do código da página. Você só precisa deixar um elemento vazio no HTML; a classe cria o resto.

```html
<link rel="stylesheet" href="/css/tabela-core.css">
<script src="/js/tabela-core.js" defer></script>
<script src="/js/clientes.js" defer></script>
<div id="clientes"></div>
```

```javascript
// clientes.js: tabela somente de leitura, sem controles adicionais.
const tabela = new FirmaTabela({
  elemento: '#clientes', // seletor CSS ou HTMLElement
  rotulo: 'Clientes',   // nome acessível da tabela
  recursos: false,      // desativa todos os recursos opcionais
  colunas: [
    { id: 'nome', nome: 'Nome', largura: 300 },
    { id: 'email', nome: 'E-mail', largura: 260 }
  ],
  dados: [
    { nome: 'Ana', email: 'ana@exemplo.com' },
    { nome: 'Bruno', email: 'bruno@exemplo.com' }
  ]
});
```

O construtor desenha a tabela, mas não consulta automaticamente a API. Para dados remotos, chame `await tabela.carregar()`. `autoInit: false` adia a primeira renderização quando não houver `dados`.

Cada destino aceita uma instância. Para remontar no mesmo elemento, primeiro chame `destruir()`. Os IDs internos recebem um prefixo automático; `containerId` permite definir um prefixo único e estável. Duas tabelas na mesma página não devem repetir esse prefixo.

## Recursos opcionais

`recursos` aceita um objeto. Propriedades omitidas usam os padrões abaixo. `recursos: false` desativa todos os recursos, inclusive ações automáticas.

| Propriedade | Padrão | Comportamento |
| --- | --- | --- |
| `selecao` | `false` | Checkbox por linha e seleção dos registros carregados/filtrados. |
| `acoes` | `false`* | Coluna de ações renderizada pela página. |
| `paginacao` | `true` | Navegação, tamanho de página e salto. |
| `selecionarColunas` | `true` | Seletor de colunas visíveis. |
| `filtrosHeader` | `true` | Filtros no menu de cada cabeçalho. |
| `ordenacao` | `true` | Ordenação crescente/decrescente no cabeçalho. |
| `redimensionamento` | `true` | Alças de largura, com mouse/toque ou setas do teclado. |
| `reordenacao` | `false` | Comandos para mover uma coluna para o início/fim. Não cria fixação sticky. |
| `filtrosSalvos` | `false` | Salvar filtros por nome e reaplicá-los no cabeçalho. |
| `buscaIA` | `false` | Campo de busca, prévia e aplicação do plano. Disponível no modo local. |
| `edicao` | `false` | Edição com confirmação por Enter ou pelo formulário de detalhes. Exige `salvar`. |
| `detalhes` | `false` | Diálogo de leitura acionado por botão da página ou pelo método `detalhes`. |
| `persistencia` | `false` | Salva larguras, visibilidade/ordem e filtros nomeados no localStorage. |

\* Quando `renderAcoes` é informado, `acoes` é habilitado por conveniência, a menos que explicitamente desativado.

Seleção, ações, filtros, ordenação e seletor são independentes. Por exemplo, é possível ordenar sem disponibilizar filtros ou reordenar sem permitir ocultar colunas. Filtros salvos dependem de `filtrosHeader` para aparecer no menu; sem persistência, duram apenas enquanto existir a instância.

```javascript
recursos: {
  selecao: true,
  acoes: false,
  selecionarColunas: false,
  filtrosHeader: false,
  ordenacao: true,
  redimensionamento: false
}
```

Um recurso desativado não cria seus controles ou manipuladores específicos. Os métodos `filtrar`, `ordenar` e `definirColunas` recusam operações desativadas; não servem para contornar a configuração. Permissões da interface não substituem autorização no backend.

## Definição de colunas

| Propriedade | Uso |
| --- | --- |
| `id` | Campo da linha; obrigatório e único. `selecao` e `acoes` são reservados. |
| `nome` | Texto do cabeçalho. `header` é um alias; na ausência dos dois, usa `id`. |
| `tipo` | `texto`, `numero`, `data` ou `enum`. Afeta formatação, ordenação e filtro. |
| `largura` | Largura inicial em pixels; padrão 200. |
| `larguraMinima` / `larguraMaxima` | Limites em pixels; padrões 80 e 600. |
| `visivel` | `false` oculta inicialmente; padrão `true`. |
| `ocultavel` | `false` mantém a coluna obrigatória, inclusive no seletor e na restauração de layout. |
| `filtrosHeader`, `ordenacao`, `redimensionamento`, `reordenacao`, `edicao` | `false` desativa o recurso para esta coluna. Não habilita um recurso globalmente desativado. |
| `editavel` | `false` impede edição desta coluna. Use para IDs, campos calculados e dados não suportados pelo endpoint. |
| `valor(item)` | Extrai um valor aninhado ou calculado. Sem callback, usa `item[id]`. |
| `formatar(valor, item)` | Formatação textual usada na exibição e nos filtros textuais. |
| `render(item, td, instancia)` | Renderização personalizada desta célula, com precedência sobre `renderCelula`. |

```javascript
colunas: [
  { id: 'id', nome: 'Código', largura: 100, ocultavel: false, editavel: false },
  { id: 'nome', nome: 'Cliente', largura: 300 },
  { id: 'criadoEm', nome: 'Cadastro', tipo: 'data', editavel: false },
  { id: 'total', nome: 'Total', tipo: 'numero', filtrosHeader: false },
  { id: 'empresa', nome: 'Empresa', valor: item => item.empresa?.nome, editavel: false }
]
```

Datas são tratadas pelo `tipo`, independentemente do nome do campo. O fim de um filtro por dia inclui o dia inteiro; datas sem horário usam a interpretação de `Date` do navegador. Números são ordenados pelo valor bruto. Intervalos numéricos aceitam zero. Sem renderização personalizada, valores são inseridos com `textContent`: vírgulas não criam links ou badges e HTML não é interpretado.

## Com mais recursos ligados

```html
<div id="contratos"></div>
```

```javascript
const tabela = new FirmaTabela({
  elemento: '#contratos',
  containerId: 'contratos',
  storageId: 'contratos-usuario-42', // incluir usuário/empresa quando necessário
  endpoint: '/api/contratos',
  credenciais: () => ({ Authorization: `Bearer ${obterToken()}` }),
  tamanhoPagina: 10,
  tamanhosPagina: [10, 25, 50],
  recursos: {
    selecao: true, acoes: true, paginacao: true, selecionarColunas: true,
    filtrosHeader: true, ordenacao: true, redimensionamento: true,
    reordenacao: true, filtrosSalvos: true, persistencia: true,
    buscaIA: true, detalhes: true, edicao: true
  },
  colunas: [
    { id: 'id', nome: 'Código', ocultavel: false, editavel: false },
    { id: 'titulo', nome: 'Título', largura: 320 },
    { id: 'criadoEm', nome: 'Criado em', tipo: 'data', editavel: false }
  ],
  slots: {
    comandos: (destino, instancia) => {
      const novo = document.createElement('a');
      novo.href = '/contratos/novo'; novo.textContent = '+ Contrato';
      destino.append(novo);
    },
    rodape: destino => destino.append(document.createTextNode('Contratos da empresa'))
  },
  renderAcoes: (item, td, estado, render, api, instancia) => {
    const ver = document.createElement('button'); ver.type = 'button';
    ver.textContent = 'Ver'; ver.onclick = () => instancia.detalhes(item);
    td.append(ver);
  },
  salvar: (item, alteracoes, instancia) => instancia.api(`/${item.id}`, {
    method: 'PATCH', body: JSON.stringify(alteracoes)
  }),
  buscaEndpoint: '/api/contratos/plano-busca',
  textos: { busca: 'Descreva os contratos que procura...' },
  onErro: erro => console.error(erro),
  onSelecao: ids => console.log('Selecionados:', ids)
});
await tabela.carregar();
```

`salvar(item, alteracoes, instancia)` deve concluir a gravação real e pode retornar o registro atualizado, um objeto parcial ou nada. Em caso de falha, deve lançar um erro. O núcleo só modifica o registro após sucesso. Edição inline: Enter confirma, Escape cancela e sair da célula cancela sem gravar. Campos complexos, enums com opções específicas, datas com conversão de fuso e formulários de negócio devem ter editores próprios da página; o editor padrão suporta texto e número.

Também é possível usar `renderCelula(item, idColuna, td, estado, render, api, instancia)` para personalizar várias colunas. Use criação de nós e `textContent` para dados externos. `onRowClick(item, estado, render, instancia)` ignora interações com botões, links e campos.

## Dados locais e API

Há três formas de fornecer dados:

1. `dados: []` no construtor ou `definirDados(itens)` depois, sem rede.
2. `endpoint`, com `carregar()` fazendo GET e esperando um array JSON no modo local.
3. `carregarDados(consulta, instancia)`, um callback assíncrono que substitui o GET padrão.

`consulta` contém `{ pagina, tamanho, ordem, filtros, signal }`. Passe `signal` ao seu `fetch` para cancelar requisições antigas. A instância também ignora respostas antigas que cheguem depois da mais recente.

`headers` aceita um objeto estático; `credenciais()` fornece headers dinâmicos e tem precedência sobre `headers`. Não exige autenticação para arrays ou endpoints públicos. O helper `api(path, options)` concatena `endpoint + path`, aceita headers adicionais e trata HTTP 204 sem tentar ler JSON.

`mapearResposta(resposta)` adapta a resposta antes de validá-la. Getters de configuração, como `get endpoint()`, são preservados e consultados a cada requisição.

`carregar()` retorna `true` em sucesso ou `false` em erro/cancelamento. Erros são exibidos no status e enviados para `onErro(erro, instancia)` e `toast(mensagem, true)`, quando definidos. Dados já carregados não são apagados em uma falha de rede.

### Modo servidor

Para conjuntos grandes, use `modo: 'servidor'`. O backend passa a filtrar, ordenar e paginar. A tabela espera **somente os registros da página** em `{ itens: [], total: 123 }`; `total` deve representar a contagem após os filtros, antes da paginação.

```javascript
const tabela = new FirmaTabela({
  elemento: '#clientes',
  modo: 'servidor',
  colunas: [{ id: 'nome', nome: 'Nome' }],
  carregarDados: async ({ pagina, tamanho, filtros, ordem, signal }) => {
    const params = new URLSearchParams({
      page: pagina - 1, size: tamanho,
      filtros: JSON.stringify(filtros), ordem: JSON.stringify(ordem)
    });
    const resposta = await fetch(`/api/clientes?${params}`, { signal });
    if (!resposta.ok) throw new Error('Não foi possível carregar os clientes.');
    const dados = await resposta.json();
    return { itens: dados.content, total: dados.totalElements };
  }
});
await tabela.carregar();
```

Sem `carregarDados`, o GET padrão envia `pagina` (base 1), `tamanho`, `ordem` e `filtros` (os dois últimos como JSON na query). `parametros(consulta)` pode substituir esses parâmetros; `mapearResposta` pode adaptar o retorno. O backend deve implementar explicitamente esse contrato; a opção não altera endpoints existentes.

Alterar página, filtro ou ordem faz nova consulta. Não há uma segunda paginação local sobre a página recebida. No modo servidor, filtros textuais usam um campo “contém”, pois a página atual não contém necessariamente todos os valores possíveis. Busca por IA não é habilitada nesse modo nesta versão.

## Seleção

`selecao: true` exige `id` único e não nulo em cada registro. Para outro identificador, forneça `chaveLinha: item => item.codigo`. O tipo da chave é preservado: números continuam números.

O checkbox do cabeçalho seleciona os registros carregados e filtrados. No modo local, isso inclui as outras páginas; no servidor, vale somente para a página carregada. Seleções de páginas remotas anteriores permanecem até `limparSelecao()`. Não representa seleção automática de todo o resultado remoto. Ao trocar empresa, conta ou fonte de dados, limpe a seleção explicitamente.

## Busca com IA

Use `buscaIA: true` e `buscaEndpoint` (URL completa do endpoint) ou `interpretarBusca(consulta, instancia)`. A resposta deve ter o formato:

```json
{
  "summary": "Contratos pendentes, mais recentes primeiro",
  "steps": [
    { "op": "column-filter", "column": "status", "type": "equals", "value": "PENDENTE" },
    { "op": "sort", "column": "criadoEm", "dir": "desc" }
  ]
}
```

Filtros aceitam `equals`, `contains` e `range` (com `from`/`to`). O componente valida colunas e recursos permitidos, mostra a prévia e só aplica quando o usuário confirma. É possível remover etapas antes de aplicar. Cancelar não altera os filtros. Filtros aplicados aparecem nos chips e podem ser removidos individualmente. A requisição padrão é POST com `{ consulta }` e os mesmos headers da API. Não há dependência de fornecedor de IA no núcleo.

## Seus botões, textos e cores

`slots.comandos` e `slots.rodape` aceitam um `Node`, `DocumentFragment` ou função `(destino, instancia)`. Nós fornecidos são movidos para o destino. Para reutilizar um `<template>`, passe `template.content.cloneNode(true)`.

A navegação aFIRMA./AssineAI/T@klATIVE/ADVai pertence à aplicação e entra pelo slot de comandos. Exportação, importação, credenciais e histórico também pertencem à página. Os nomes “área”, “terreno” e “sementes” são da tela do trator. Passe esses textos na configuração para poder usar a mesma classe em outras telas.

Os textos configuráveis são `colunas`, `acoes`, `vazio`, `semResultados`, `carregando`, `busca` e `pagina`. `pagina` é uma função que recebe `{ pagina, totalPaginas, tamanho, total }`.

No trator:

```javascript
textos: {
  busca: 'FALAR COM O BURRO DE CARGA...',
  pagina: ({ pagina, totalPaginas, tamanho }) =>
    `ÁREA ${pagina} de ${totalPaginas} do TERRENO → ${tamanho} Sementes ▾`
}
```

O CSS genérico usa os tokens da FIRMA e mantém os seletores dentro de `.af-tabela`, exceto overlays identificados pela instância. O tema das abas curvas continua em `documentos-assinatura.css`, fora do núcleo. Cada aplicação pode carregar seu tema após `tabela-core.css`.

## Métodos da classe

| Método | O que faz |
| --- | --- |
| `carregar()` | Consulta a fonte; Promise de booleano. |
| `definirDados(itens)` | Substitui o array local, remove seleções órfãs e redesenha. Apenas modo local. |
| `dados()` | Retorna os registros filtrados/ordenados antes da paginação local; no servidor, a página recebida. |
| `filtrar(id, filtro)` | Aplica filtro: texto, array de valores ou `{ from, to }`. `null` limpa a coluna. |
| `limparFiltros()` | Remove todos os filtros e volta à primeira página. |
| `ordenar(id, dir)` | Ordenação `asc`/`desc`, respeitando permissões. |
| `definirColunas(ids)` | Define colunas visíveis e ordem; mantém colunas obrigatórias. Exige seletor habilitado. |
| `irParaPagina(numero)` | Navega dentro dos limites. |
| `definirTamanhoPagina(numero)` | Define inteiro positivo e volta à primeira página. |
| `obterSelecionados()` | Retorna array de chaves selecionadas. |
| `limparSelecao()` | Limpa seleção e notifica `onSelecao`. |
| `detalhes(item, editar = false)` | Abre o diálogo conforme os recursos habilitados. |
| `api(path, options)` | Fetch autenticado em `endpoint + path`. |
| `render()` | Redesenha sem consultar a API. |
| `getEstado()` | Acesso ao estado para compatibilidade. Prefira métodos públicos para alterar. |
| `destruir()` | Cancela requisições, remove eventos/overlays e libera o destino para remontagem. |

No modo servidor, os métodos que mudam página/filtro/ordem retornam a Promise de `carregar()`. No local, atualizam imediatamente. Callbacks disponíveis: `onRender(instancia)`, `onCarregar(instancia)`, `onErro(erro, instancia)`, `onSelecao(ids, instancia)`, `onRowClick`, `toast` e os renderizadores descritos acima.

## Migração e compatibilidade

`window.aFirmaTabela(config)` permanece como fábrica de `new FirmaTabela(config)`. Chamadas antigas com HTML manual podem usar `containerId` e o `<main>`/`.af-tabela` que contém os IDs do componente; esse modo mantém a estrutura existente. Para novas tabelas, use `elemento`: montagem automática é a API recomendada e dispensa a lista de IDs manuais.

Os nomes canônicos da busca são `buscaEndpoint` e o recurso `buscaIA`. `iaEndpoint` e `iaFormId` são aliases de compatibilidade para o manual antigo; `buscaFormId` continua disponível no modo de HTML manual. As permissões e os novos padrões de recursos também se aplicam à fábrica antiga: configure-os explicitamente ao migrar.

O trator foi migrado para montagem automática. Seus comandos/rodapé vêm de templates; títulos e termos agrícolas vêm de `textos`. No trator, editar o título envia um PATCH para a API. Só `titulo` fica editável, porque é o campo que esses endpoints aceitam alterar.

## Verificação

Execute:

```sh
python scripts/verificar-tabela.py
node --check src/main/resources/static/js/tabela-core.js
node --check src/main/resources/static/js/documentos-assinatura.js
```

O script usa Chrome/Chromium headless e testes de DOM sem instalar dependências. Cobre recursos opcionais, permissões por coluna, dados locais/remotos, múltiplas instâncias, menus, seleção, persistência, concorrência, gravação e destruição. Esses testes simulam as respostas da API. Para conferir o backend, rode também os testes dos endpoints que sua tela usa.
