# Auditoria de paridade: Autentica e daFIRMA

Data: 01/11/2026. Escopo: código local em `/home/aluno/autentica/zapio`, implementação em `/home/aluno/aFIRMA` e metadados do PostgreSQL local usado pela aplicação. Não foi consultado um banco de produção do Autentica nem executado envio a WhatsApp, Meta ou outros fornecedores.

## Conclusão

Não encontrei tabelas ou chaves estrangeiras faltantes no contrato extraído. Encontrei fluxos de negócio incompletos e opções armazenadas que ainda não produzem o comportamento da origem. Existir uma tabela, coluna, endpoint ou item no catálogo não comprova a equivalência do fluxo completo.

A verificação estrutural abrange todo o manifesto. A revisão funcional abaixo registra os achados confirmados nos fluxos examinados; não certifica paridade semântica de todas as funções e integrações da aplicação.

## Estrutura conferida no banco em uso

| Item | Origem / manifesto | Banco daFIRMA | Resultado |
| --- | ---: | ---: | --- |
| Tabelas | 33 | 33 | Nenhuma ausente |
| Colunas | 383 | 383 | Nomes, tipos, tamanhos e obrigatoriedade conferidos |
| Chaves primárias | 33 | 33 | Presentes; IDs com sequência |
| Chaves estrangeiras | 57 | 57 | Tabelas/colunas de origem e destino e regras ON DELETE/ON UPDATE correspondentes |
| Índices explícitos da origem | 12 | 12 correspondentes | Presentes; além de dois índices acrescentados pela FIRMA |

As tabelas ficam nos schemas `autentica` e `autentica_identidade`. As tabelas operacionais anteriores da FIRMA e as tabelas auxiliares de migração, filas e idempotência são adicionais e não entram nessa contagem.

O campo `PlanoValor` foi conferido como `numeric(12,2)`, equivalente ao `DECIMAL(12,2)` do manifesto. Referências que na origem cruzavam dois bancos, como `AtendenteAutenticaAIUsuarioId` e `UsuarioClienteId`, não eram FKs entre os bancos; sua validação de aplicação não deve ser confundida com uma FK ausente na migração.

As 74 fontes do manifesto estrutural e as 107 fontes do inventário de regras mantêm os SHA-256 registrados. Também conferi que não há arquivos de modelos ou migrações nas quatro pastas de origem auditadas fora do manifesto: 26 modelos de domínio, seis modelos do pacote de identidade e 36 migrações. A tabela `UsuarioPermissao` está incluída pelo contrato das migrações.

## Lacunas funcionais confirmadas

### 1. Recebimento de mensagens da Meta; prioridade alta

Na origem, o webhook trata separadamente tipos de mensagem e respostas interativas. Localização gera arquivo com latitude/longitude; contatos recebidos são interpretados; reações atualizam a mensagem correspondente; há tratamento de mídia e respostas a mensagens.

Na FIRMA, `ControleCanalOficial.receber` extrai o corpo somente quando `type == text`. Para os demais tipos, entrega a `chat.receber` o marcador `[tipo]` e depois armazena o JSON bruto em `dados_externos`. Esse caminho não faz a interpretação equivalente de mídia, localização, contatos, reações e respostas interativas. Guardar o JSON original preserva informação, mas não conecta automaticamente os arquivos, mensagens e automações como a origem fazia.

Impacto: uma resposta a botão/lista pode chegar ao fluxo como `[interactive]`; mídia e localização não recebem o mesmo tratamento operacional da origem.

Evidências:

- Origem: `model/src/services/whatsapp/meta/webhook.js`, processamento a partir da linha 84 e leitura das respostas interativas por volta da linha 395; `model/src/services/whatsapp/utils/Meta.js:558` e `:581`.
- FIRMA: `src/main/java/com/afirma/assina/ControleCanalOficial.java:151`.

### 2. Iniciar e reativar atendimento pelo chat; prioridade alta

Na origem, `CriarAtendimentoViaChat` recebe atendente, texto inicial, template e mídia, cria o atendimento em andamento e envia a mensagem. Se o envio inicial falhar, desfaz o atendimento recém-criado. A reativação também passa pelo envio de template/mídia.

Na FIRMA, `OperacaoAutentica.nova` cria um atendimento pendente sem atribuir o atendente nem enviar mensagem inicial. A ação `reativar` muda o estado para pendente e limpa o atendente, sem integrar template/mídia nesse fluxo. O formulário do chat só envia canal, contato e setor.

Impacto: o fluxo da origem de iniciar/retomar atendimento com atendente e mensagem/template não está reproduzido. O envio de templates existe em outras APIs e campanhas da FIRMA; a lacuna é sua integração a essa operação do chat. A escolha de manter atendimento pendente pode ser uma adaptação de produto, mas não equivale automaticamente à regra da origem.

Evidências:

- Origem: `model/src/consultas/TabelaAtendimento.js:20` e `:161`; `model/src/services/whatsapp/controller/Mensagem.js:816` e `:1004`.
- FIRMA: `src/main/java/com/afirma/assina/OperacaoAutentica.java:32` e `:66`; formulário em `src/main/resources/templates/autentica-chat.html` e envio em `static/js/autentica-chat.js`.

### 3. Confirmação de leitura ao abrir a conversa; prioridade média

Na origem, `MarcarComoLido` consegue marcar todas as mensagens do atendimento no provedor WhatsApp, além do controle local.

Na FIRMA, abrir uma conversa chama a ação `lidas`, que apenas zera `AtendimentoQtdMensagensNaoLidas` no banco. Existe uma operação individual de mensagem `lida` que chama o transporte, mas o fluxo de abertura do chat não a utiliza nem executa um equivalente em lote.

Impacto: a conversa pode aparecer lida na FIRMA enquanto o WhatsApp ainda não recebeu a confirmação correspondente.

Evidências:

- Origem: `model/src/services/whatsapp/controller/Mensagem.js:1806`.
- FIRMA: `src/main/resources/static/js/autentica-chat.js:95`; `src/main/java/com/afirma/assina/OperacaoAutentica.java:72`; capacidade individual já existente em `AutenticaOperacoes.java:42` e `ControleAutentica.java:38`.

### 4. Sincronização automática das fotos de contatos; prioridade média

Na origem, uma rotina semanal chama `AtualizarFotosContato`, consulta `profilePictureUrl`, baixa a imagem e atualiza `ContatoAvatar` e `ContatoAvatarChave`.

Na FIRMA, os campos e a apresentação da foto existem. A coluna de contato usa `ContatoAvatar` salvo e exibe iniciais quando ele não está disponível. Não encontrei a rotina equivalente de buscar e renovar essas fotos no WhatsApp na ponte ou nos serviços Java.

Impacto: implementar a célula com foto não faz, por si só, que fotos ausentes sejam buscadas ou que URLs/imagens antigas sejam atualizadas.

Evidências:

- Origem: `model/src/scheduler/Tarefas/AtualizarFotoContato.js:5`; `model/src/services/whatsapp/baileys/Cliente.js:504` e `:539`; `model/src/consultas/TabelaContato.js:644`.
- FIRMA: `src/main/resources/static/js/autentica-chat.js:43`; serviços de ponte em `chat-baileys/servidor.cjs`, sem operação de foto de perfil.

### 5. Opção de exibir atendimentos de BOT por canal; prioridade média/baixa

A coluna `CanalVisualizaMenuBOTChat` foi migrada. Na aplicação original, o frontend lê essa configuração para mostrar o menu e consultar os atendimentos de BOT.

No Zapiou atual, não encontrei leitura dessa opção nos serviços ou no frontend. Os registros de BOT são tratados na coluna ATENDIMENTOS, independentemente dessa configuração. A interface nova pode deliberadamente substituir o menu antigo, mas a opção persistida ainda não tem uma regra equivalente aplicada à visibilidade.

Evidências:

- Origem: `model/src/consultas/TabelaAtendenteSetor.js:201`; frontend distribuído em `web/assets/chat.1.4.8.js`, propriedades `visualizaMenuBot`, `buscarAtendimentosBot` e `CanalVisualizaMenuBOTChat`.
- FIRMA: campo presente no manifesto e na V25; ausência de consumo dessa opção em `src/main/java`, `src/main/resources/static/js` e `src/main/resources/templates`.

## Diferenças documentadas ou intencionais

- Planos, limites comerciais e cobrança: a tabela `Plano` e seus vínculos permanecem; a operação comercial foi removida porque a FIRMA é gratuita. A V28 declara essa decisão. Não classifiquei isso como migração faltante.
- WWebJS: a origem tem esse motor; a instalação FIRMA executa Baileys/Meta. O espelho atualmente converte provedores diferentes de Meta para Baileys. Um registro histórico WWebJS não representa suporte operacional ao motor WWebJS.
- Atualização do chat: a origem transmite eventos por Socket.IO; o Zapiou atual consulta a cada 15 segundos. É uma adaptação arquitetural com diferença perceptível de latência, sem exigir uma tabela nova.
- Exclusões e isolamento entre empresas: há adaptações documentadas de exclusão lógica e repositório por empresa. A existência dos mesmos relacionamentos não implica que a aplicação permita os mesmos fluxos administrativos globais da origem.
- Firebase, IA, Meta, n8n, SGA, Asaas e hControl têm implementações e/ou adaptadores locais. Credenciais e teste real com cada fornecedor são uma categoria separada; sua ausência na instalação não prova ausência de tabela ou regra implementada.

## Prioridade sugerida

Primeiro, completar a recepção Meta e o fluxo de iniciar/reativar com mensagem/template. Depois, integrar a leitura no provedor e a sincronização de fotos. Por fim, definir a semântica de `CanalVisualizaMenuBOTChat` no trator novo, para que a configuração tenha efeito ou seja explicitamente substituída.

Esta auditoria não alterou as regras, não conectou números e não enviou mensagens. As consultas ao banco foram apenas de metadados.
