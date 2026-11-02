# Um trator daFIRMA

O trator de assinaturas é a referência. Assinaturas, conversas e cadastros de relacionamento são montados por `FirmaTrator.montar`, que usa a mesma `FirmaTabela`. Os módulos declaram suas colunas e operações; não precisam lembrar de habilitar cada recurso básico.

| Recurso da referência | Assinaturas | Conversas do Zapiou |
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

- `python scripts/verificar-zapiou.py`: recursos, ações reais simuladas, seleção entre páginas, seletor de colunas, filtros salvos, busca com prévia, exportação, importação e conexão.
- `python scripts/verificar-tabela.py`: componente base e trator de assinaturas real, incluindo os mesmos recursos e os recados com X.
- `python scripts/verificar-mensagens.py`: avisos em desktop/celular e sobre formulários, fechamento e ausência de reaparecimento.
- `./mvnw -Dtest=ServicoPlanoBuscaConversasTest,FiltroTratorAutenticaTest test`: planos válidos, recusa de campos de outro módulo e filtros do servidor.

## Configuração daFIRMA

`/firma` usa formulários para dados, aparência e canais. Não incorpora o trator. Os modelos da folha de assinaturas ficam no ADVai, em `/advai#editor-templates`. Conversas e cadastros continuam nos tratores do Zapiou.
