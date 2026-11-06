# Um trator daFIRMA

O trator de assinaturas é a referência. Assinaturas, conversas e cadastros de relacionamento são montados por `FirmaTrator.montar`, que usa a mesma `FirmaTabela`. Os módulos declaram suas colunas e operações; não precisam lembrar de habilitar cada recurso básico.

| Recurso da referência | Assinaturas | Zapiou |
| --- | --- | --- |
| Abas curvas na barra de comandos | Compartilhadas | Compartilhadas; grupos abrem seus menus |
| Coluna AÇÕES | Visualizar, editar, histórico, excluir documento | Visualizar, editar contato, histórico, finalizar/reativar atendimento |
| Checkbox por linha | Sim | Sim |
| Selecionar todos os registros filtrados | Sim | Sim, incluindo outras páginas locais |
| Contador da seleção no rodapé | Compartilhado | Compartilhado |
| Escolher colunas, selecionar todas e restaurar | Compartilhado | Compartilhado |
| Filtros e ordenação no cabeçalho | Compartilhados | Compartilhados; filas na coluna ATENDIMENTOS |
| Largura e ordem das colunas | Compartilhadas | Compartilhadas |
| Filtros salvos | Sim | Sim |
| Persistência de layout | Sim | Sim; chave por empresa e usuário |
| Busca do Burro de Carga | Plano com prévia e aplicação explícita | Mesmo fluxo; endpoint com as colunas de conversas |
| Detalhes e edição | Dados e título do documento | Dados e campos de contato permitidos pelo perfil |
| Paginação e tamanho de página | Compartilhados | Compartilhados |
| Tela inteira, 80% e centro | Compartilhados | Compartilhados |
| Rodapé de credenciais | Compartilhado | Compartilhado, conexão explícita |
| Recados com X, deslocados do centro | Compartilhados | Compartilhados |
| Exportar | Interface da referência: PDF; CSV/Excel ainda têm limitações preexistentes | CSV, JSON e impressão/PDF dos selecionados ou do resultado filtrado |
| Importar | Interface de arquivo da referência, ainda sem processamento completo | Solicitação real de histórico do WhatsApp por canal |

Atendimentos têm operação própria de finalizar/reativar e não permitem exclusão genérica na API. O Zapiou usa essas operações, sem apresentar uma lixeira que falharia ou apagaria o histórico. Editar contato grava nome, telefone e e-mail pela API e respeita o perfil. O duplo clique continua abrindo o chat abaixo da linha; a edição usa a ação correspondente.

Os cadastros remotos usam os mesmos recursos básicos e mantêm os formulários próprios para campos específicos. Busca por plano e edição inline são substituídas por seus controles suportados nesse modo.

## Verificação

- `python scripts/verificar-chat-firma.py`: recursos, ações reais simuladas, seleção entre páginas, seletor de colunas, filtros salvos, busca com prévia, exportação, importação e conexão.
- `python scripts/verificar-tabela.py`: componente base e trator de assinaturas real, incluindo os mesmos recursos e os recados com X.
- `python scripts/verificar-mensagens.py`: avisos em desktop/celular e sobre formulários, fechamento e ausência de reaparecimento.
- `./mvnw -Dtest=ServicoPlanoBuscaConversasTest,FiltroTratorFirmaTest test`: planos válidos, recusa de campos de outro módulo e filtros do servidor.

## Configuração daFIRMA

`/firma` usa formulários para dados, aparência e canais. Não incorpora o trator. Os modelos da folha de assinaturas ficam em AssineAI → Telas → Página de assinatura, em `/assineai/pagina-assinatura`. Conversas e cadastros continuam nos tratores de relacionamento.


## Faixa compartilhada de abas

`FirmaTrator.montar` prepara a faixa arrastável e o Burro de Carga automaticamente.
O desenho da pasta, as cores, as alturas e a sobreposição ficam em `trator-firma.css`.
Os módulos não precisam implementar o arraste nem posicionar o filtro por CSS.
A navegação também funciona por toque e pelas teclas de seta, Home e End.

Use `slots.comandos` para os módulos e comandos antes de Colunas.
Use `slots.abasFinais` para a navegação que deve aparecer depois de Colunas,
como o menu Telas. Esse conteúdo é montado uma vez e preservado nas atualizações.

```javascript
FirmaTrator.montar({
  elemento: '#minha-tabela',
  rotulo: 'Pedidos',
  dados: pedidos,
  colunas: [{ id: 'nome', nome: 'NOME' }],
  slots: {
    comandos: destino => destino.append(botaoNovoPedido),
    abasFinais: destino => destino.append(menuTelas)
  }
});
```

`FirmaTabela` continua sendo o núcleo de dados e renderização. `FirmaTrator`
adiciona a apresentação padrão daFIRMA. Os dois módulos usam a mesma camada.
Os eventos da faixa são removidos junto com a instância da tabela.


## Cadastros e consultas paginadas

Os tratores ativos estão nas telas de assinaturas, chat e nos 23 cadastros do
catálogo `relacionamento/modulos.json`. Os cadastros compartilham o template
`relacionamento.html`; os apelidos de rota usam esse mesmo template.

Para consultas paginadas, defina `buscarNoServidor(consulta, instancia)` e mantenha
`recursos.buscaIA: false`. A base cria o mesmo Burro de Carga e chama a consulta
quando o formulário é enviado, voltando à primeira página. Nesse modo o servidor
continua responsável por busca, filtros, ordenação e paginação dos registros.

Não crie outra faixa acima do cabeçalho de colunas. A navegação entre cadastros
fica na aba Telas; Atualizar e os comandos auxiliares ficam no rodapé existente.
A busca rápida aparece apenas na lista lateral do chat expandido. Ao recolher,
o campo é ocultado e a consulta é limpa.

Verificações de interface sem serviços externos:

```bash
python3 scripts/verificar-tabela.py
python3 scripts/verificar-chat-firma.py
python3 scripts/verificar-tratores.py
```

A última verificação percorre todos os cadastros em 1440 e 500 pixels e confere
estrutura, cores, limites de largura, envio da busca e remontagem na reconexão.
Os dados e respostas da API são simulados nessa verificação.


## Trator de documentos

O editor de página de assinatura usa `TratorDocumentos`, definido em
`trator-documentos.js`, com aparência em `trator-documentos.css`. É uma área
especializada de edição: modelos, ferramentas, zoom, prancheta e publicação.
Não instancia `FirmaTabela` nem `FirmaTrator` e não carrega o núcleo de tabelas.

O motor `EditorTemplates` permanece responsável pelos modelos, prévias, posições,
histórico e biblioteca. O trator organiza o espaço de trabalho e o acesso aos
comandos. As cores da bancada não alteram as cores do PDF da empresa.
