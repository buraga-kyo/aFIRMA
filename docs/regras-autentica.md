# Regras do Autentica no Zapiou da FIRMA

> Este mapa rastreia implementações e adaptações. As lacunas funcionais verificadas em 01/11/2026 estão na [auditoria de paridade](auditoria-autentica-firma.md).

A home e o design são da FIRMA. O atendimento é um módulo, acessado pelo Zapiou. A aplicação é gratuita: não há contratação, renovação, cobrança ou desbloqueio de funcionalidades por plano. As tabelas antigas de planos e seus vínculos permanecem apenas como histórico; não aparecem no catálogo de módulos e não autorizam nem bloqueiam operações.

## Rastreabilidade

Foram comparadas as rotas, consultas, serviços, SDK de identidade e tarefas agendadas em `/home/aluno/autentica/zapio`. O inventário [autentica-regras-origem.json](autentica-regras-origem.json) registra arquivos, funções exportadas, rotas e SHA-256, sem copiar credenciais. Exemplos de helpers e código não chamado pela aplicação não são tratados como funcionalidades operacionais. Planos estão excluídos.

| Área da origem | Implementação na FIRMA | Validação |
| --- | --- | --- |
| `TabelaContato`, `TabelaSetor`, `TabelaAtendente`, `TabelaAtendenteSetor`, `TabelaTag`, `TabelaMensagemRapida`, `TabelaListaContato` | `ServicoAutentica`, `RegrasAutentica`, espelhos: validação, normalização, duplicidade, relacionamentos e propriedade | Navegador, PostgreSQL e validação de vínculo duplicado |
| `TabelaAtendimento`, `TabelaAtendimentoAcao`, `TabelaAtendimentoTag` | `OperacaoAutentica`: atendimento aberto único por canal/contato, atribuição, transferência, finalização, reativação, avaliação, leitura, tags e histórico | Navegador, ações reais e migrações |
| Métricas de atendimento | V29 e V33: duração, primeira atribuição, respostas, ativo/receptivo, última mensagem e contagem de não lidas | PostgreSQL e finalização pelo navegador |
| `TabelaMensagem`, `EnviarMensagemBaileys`, controlador WhatsApp | `ChatTransporte`, `EventosAutentica`, ponte: texto, resposta, anexos, ACK, exclusão, edição, reação e mensagens de outro dispositivo | Ponte local simulada e callbacks autenticados |
| Meta: mensagens, templates e mídia | `ServicoCanalOficial`, `TemplatesMetaAutentica`, `MensagensInterativasAutentica`: componentes, exemplos, cabeçalho, rodapé, botões, publicação/edição, consulta de status, exclusão remota, arquivo e upload de exemplo | Testes dos componentes e verificações de propriedade; chamadas reais dependem da instalação |
| `TabelaDisparoMassa` | `DisparosAutentica`: normalização e deduplicação, variáveis, saudação, templates, anexos, agenda, dias, janela, lote, intervalo, limite operacional diário, cancelamento, histórico e retomada de falhas | Executor real com transporte local simulado |
| Campanhas anteriores da FIRMA | `ProcessadorCampanhas` continua responsável por elas; V34 apresenta seu histórico no contrato Autentica e mantém cancelamento/retomada | Migração e projeção sem duplicar executores |
| `TabelaChatbotDefinicao`, `TabelaChatbotMetadata`, scripts ativos | `CompiladorBotAutentica`, `MotorFluxoBot`, `ServicoBots`: tarefas, perguntas, respostas, variáveis, condições, setor, atendimento, REST, hControl, horários e sessões | Quatro vetores calculados pelo código Node original, execução de conversa e deduplicação |
| n8n | `N8nAutentica`, API nativa e `ServicoBots`: início, continuação, mensagem, contexto, atendimento, finalização e cancelamento | Isolamento de sessão; comunicação externa depende do workflow configurado |
| `TabelaInteligenciaArtificial` | OpenAI/ChatGPT e DeepSeek, credencial por empresa, seleção por canal/setor, exclusividade, sugestão manual, contexto e ativador automático com fila persistente | Validações de configuração/propriedade; resposta real depende da chave da empresa |
| Conteúdo de IA | `ConteudoAutentica`: extrair texto de PDFs e de páginas HTTPS públicas | Validação de formato, tamanho e destino |
| `TabelaIntegracao`, SGA, Asaas, hControl | `IntegracoesAutentica`: habilitação, credenciais da empresa, consultas, autenticação hControl, paginação e filtros; operações de cobrança pertencem aos sistemas dos clientes | Regras locais; autenticação e respostas reais dependem dos fornecedores |
| `TabelaChaveAPI` e API v1 | Emissão, alteração e revogação de UUID; autorização por cliente, envio persistido, consulta e identificação de requisição; aliases `/API/v1` | Navegador, transporte simulado, revogação e requisição repetida |
| `TabelaWebhook`, `TabelaWebhookLog` | Eventos `mensagem_recebida`, `status_canal`, `chatbot_externo_assumido`, seleção de canais, fila, tentativas e diário de entregas | Geração em PostgreSQL; entrega externa depende do endereço do cliente |
| Usuários, perfis, permissões, sessões e estratégia local | PBKDF2 compatível, carga de perfis da origem, permissões individuais, bloqueio, revogação, logout, senha própria, recuperação por código de seis dígitos/10 minutos, limite de tentativas e perfil de atendimento | Vetor do Node, isolamento, perfis e revogação |
| `TabelaFCMToken` | Cadastro protegido e manutenção pelo SDK oficial Firebase; indisponibilidade não apaga tokens válidos | Cadastro/escopo local; validação remota depende de Firebase configurado |
| Indicadores de gestor e atendente | `IndicadoresAutentica`: período/setor, cards, atendente, setor, tag, hora, dia, mês e ativo/receptivo | Navegador e isolamento entre empresas |
| Tarefas periódicas | Executores existentes, limpeza de logs, sessões e arquivos referenciados; inatividade de atendimento/bot | Código existente reutilizado, vínculos sincronizados e teste de fila |

## Decisões de adaptação

- Cada empresa da FIRMA tem seu contexto e repositório. Um administrador de uma empresa não administra as demais.
- Exclusões de cadastros com estado de uso são lógicas e registradas separadamente da desativação. Os excluídos saem das listagens; os apenas inativos permanecem visíveis. Vínculos removíveis são excluídos. Históricos de conversas permanecem.
- A navegação, busca, filtros e atualização do chat são nativos. O servidor Socket.IO e o frontend Vue da origem não são dependências da FIRMA.
- O transporte WhatsApp da instalação é Baileys ou Meta. Credenciais e pastas de sessão WWebJS de outra instalação não são transplantadas: é necessário conectar o canal na FIRMA.
- Botões e listas interativas seguem o suporte Meta. O transporte Baileys recusa essa operação explicitamente; não apresenta uma confirmação fictícia.
- O push automático estava comentado no processamento de mensagens da origem; não foi ativado silenciosamente. Cadastro e manutenção de tokens ficam disponíveis quando o Firebase é configurado.
- Recuperação de senha do Zapiou se aplica aos usuários de `autentica_identidade.Usuario`. A conta proprietária do AssineAi mantém seu fluxo de identidade anterior.

## Agenda e confirmação de envio

O limite diário configurado em um disparo é uma preferência operacional, não uma cota comercial. Campo vazio significa ausência desse limite. Dias da semana usam `0` para domingo até `6` para sábado. As janelas usam `America/Sao_Paulo`, conforme o ambiente da origem. Fora da janela, a agenda persiste o próximo horário permitido, liberando o executor para outras campanhas.

A fila grava cada destinatário antes da execução. A API nativa exige `Idempotency-Key`; repetir a mesma identificação na mesma empresa consulta o envio existente. Os aliases da API original aceitam essa identificação opcionalmente, preservando os clientes anteriores.

Um envio interrompido sem confirmação fica `INCERTO` e não é repetido automaticamente. O operador deve conferir o provedor. A ação de repetir falhas atua sobre falhas conhecidas, preservando confirmações anteriores. Mensagens só recebem confirmação de sucesso depois da resposta do transporte.

As campanhas anteriores usam seu executor existente; as novas usam a fila Autentica. A presença em `firma_autentica_disparo` define essa responsabilidade, evitando duas rotinas enviando a mesma campanha.

## Migrações

V28 remove os triggers de plano e acrescenta a agenda persistente. V29 registra eventos e métricas. V30 sincroniza vínculos de setor e encerra metadados de bots. V31 cuida de recuperação, revogação e ativação de IA. V32 guarda conteúdo de envios. V33 atualiza a última mensagem e permite arquivos remotos registrados. V34 apresenta os detalhes das campanhas anteriores. V35 acrescenta a chave de idempotência dos envios, preservando o checksum original da V28 já aplicada. As 33 tabelas da origem continuam com seus nomes, campos e relacionamentos.

## Configuração e serviços externos

Não são copiadas credenciais da instalação de origem. Para usar os fornecedores, configure as credenciais próprias da empresa e da instalação:

- Baileys: ponte em `iniciar-chat.sh`, `APP_CHAT_BRIDGE_KEY` e conexão pelo QR do canal.
- Meta: token/aplicativo no cadastro da empresa e identificadores no canal. `APP_META_VERSAO` continua configurável.
- IA: chave de cada agente, ou referência `APP_IA_TOKEN_*`.
- E-mail de recuperação: `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, credenciais/TLS do SMTP e `APP_EMAIL_FROM`.
- SGA, Asaas e hControl: bases `SGA_BASE_URL`, `ASAAS_BASE_URL`, `HCONTROL_BASE_URL` e dados da integração. hControl aceita `clientId` e `secretId` no formato da origem.
- Firebase: `APP_FIREBASE_ATIVO=true`, projeto e credenciais próprias por `GOOGLE_APPLICATION_CREDENTIALS`. O [SDK oficial](https://firebase.google.com/docs/admin/setup) administra a autenticação e validação de tokens.

A implementação não substitui a homologação com essas contas. Testes locais com ponte simulada não comprovam aprovação de templates Meta, validade de tokens, disponibilidade de workflows ou respostas dos sistemas externos.

## Verificação reproduzível

```sh
./mvnw -q -Djava.awt.headless=true test
python3 scripts/verificar-autentica.py
node scripts/autentica/extrair-regras.cjs
python3 scripts/autentica/inventariar-regras.py
```

O teste de PostgreSQL cria e remove uma base descartável no container informado. Não usa a configuração do banco normal. O teste de navegador cria contas e dados sintéticos e deve apontar para uma instância isolada com transporte desativado:

```sh
NODE_PATH=/tmp/firma-browser/node_modules \
FIRMA_TEST_BASE_URL=http://127.0.0.1:18089 \
node src/test/js/autentica.browser.cjs
```

Para verificar os executores sem enviar mensagens reais, execute a ponte simulada e a aplicação **em uma base isolada**, com `APP_CHAT_BRIDGE_URL=http://127.0.0.1:18091`, a chave de teste da ponte e `APP_AUTENTICA_ROTINAS=true`:

```sh
node src/test/js/ponte-falsa.cjs
# Em outro terminal, com a aplicação de teste iniciada:
node src/test/js/autentica.workers.cjs
```

A ponte de teste escuta somente em `127.0.0.1`; não carrega Baileys nem acessa WhatsApp. A configuração de execução deve apontar a aplicação para essa ponte, nunca para canais reais durante os testes.

Os contratos de SGA, Asaas, hControl e recuperação de senha também possuem simuladores locais:

```sh
node src/test/js/provedores-falsos.cjs
node src/test/js/smtp-falso.cjs
```

Na aplicação isolada, configure as três bases de integração para `http://127.0.0.1:18094` e o SMTP para `127.0.0.1:18092`, sem autenticação nem TLS. Execute o teste de filas com `FIRMA_TEST_PROVEDORES_URL=http://127.0.0.1:18094 FIRMA_TEST_SMTP_URL=http://127.0.0.1:18093`. O SMTP simulado apenas armazena mensagens em memória. As bases de integração são configurações confiáveis do servidor e aceitam serviços internos; destinos informados por usuários em webhooks e extração de conteúdo continuam sujeitos à validação de endereço público.

## Trator do Zapiou e histórico do WhatsApp

O acesso ao Zapiou leva a `/chat`, o Trator de Relacionamento. As abas ficam no trator e abrem os menus de cada grupo. Conversas e cadastros usam a mesma `FirmaTabela` das assinaturas, com seleção, ações, colunas, filtros no header, ordenação e paginação. O cadastro e a edição respeitam as permissões da empresa.

**Conectar / QR** abre um painel que consulta a ponte a cada dois segundos: conectando, QR disponível, conectado ou erro. O QR se renova no próprio painel. Fechar o painel interrompe a consulta do navegador, sem interromper o canal. **Importar conversas** abre a situação da sincronização; **Importar / buscar mais conversas** solicita lotes anteriores usando os cursores já recebidos.

A ponte habilita `syncFullHistory`, usa o perfil de navegador Web compatível com o pareamento e recebe `messaging-history.set`, conforme a [documentação oficial do Baileys](https://github.com/WhiskeySockets/docs/blob/main/advanced/history-sync.mdx). São importadas conversas individuais identificáveis por número, inclusive mensagens enviadas pelo próprio número. LIDs são associados a números quando o WhatsApp fornece esse vínculo nos contatos. Grupos, status e itens sem identificação suportada entram na contagem de itens não suportados. A importação não fabrica anexos antigos: mídia não entregue aparece identificada como indisponível.

O WhatsApp determina quais períodos e lotes serão disponibilizados. Conectar e solicitar histórico não garantem uma cópia integral do aparelho. A primeira sincronização pode demorar; o celular deve permanecer conectado à internet. Um canal previamente pareado não necessariamente recebe novamente toda a sincronização inicial. Os contadores na tela representam o que foi persistido, e não uma estimativa do total existente no celular.

V36 acrescenta o diário idempotente dos lotes e a associação estável da conversa ao atendimento. Reenvios de lote e mensagens já conhecidas não duplicam o histórico. A ponte conserva os lotes pendentes em disco para tentar novamente se o backend estiver indisponível. As datas do WhatsApp são preservadas, a leitura é paginada por data e ID, e lotes antigos não substituem a última mensagem mais recente. Importação não dispara bots, IA, webhooks nem incrementa mensagens não lidas. Novas conversas importadas entram em **Pendentes**; conversas que já estavam em atendimento mantêm sua atribuição.

Verificações adicionais, sempre em instância isolada:

```sh
node --test src/test/js/historico.test.cjs
# Com ponte simulada, chave de teste e aplicação apontando para ela:
NODE_PATH=/tmp/firma-browser/node_modules node src/test/js/autentica.historico.cjs
```

O teste integrado verifica lotes concorrentes e duplicados, histórico com mais de 100 mensagens, datas e última mensagem, isolamento de empresas, ausência de automações e a interface do QR. Ele não conecta um número real nem comprova a quantidade de histórico que uma conta real fornecerá.

A ponte limita a espera inicial do QR a 45 segundos. Falhas anteriores ao pareamento encerram a tentativa e aparecem no painel; não provocam reconexões infinitas. O reinício solicitado após a leitura do QR e as reconexões de uma sessão já conectada têm limite de tentativas. O perfil Desktop/Darwin foi substituído pelo perfil Web após reproduzir encerramentos 428 antes do QR na instalação.

## Exclusão e desativação

V37 acrescenta `firma_autentica_exclusao`, sem alterar as tabelas do contrato original. Ao excluir um cadastro preservado para o histórico, o servidor desativa o registro e grava a exclusão na mesma transação. As listagens e os totais desconsideram esse registro, inclusive depois de recarregar a página. A edição de um cadastro excluído responde `410 Gone`; leituras necessárias ao histórico continuam disponíveis. A desativação feita pelo campo de status não grava exclusão e mantém o registro visível.

DELETE dos cadastros responde `{excluido: true, id}` depois da operação. A interface verifica essa confirmação, invalida o cache e atualiza a tabela. Se a atualização falhar após a exclusão, a mensagem informa essa situação. Pedidos repetidos de exclusão lógica são idempotentes.

Regressão em aplicação e banco isolados: `NODE_PATH=/tmp/firma-browser/node_modules node src/test/js/autentica.exclusao.cjs`. Verifica exclusão pela interface, recarga, preservação de inativos e históricos, edição bloqueada, repetição e isolamento entre empresas.

## Segurança no rodapé e reutilização do número

O Trator do Zapiou usa exatamente o mesmo fragmento de rodapé das assinaturas (`fragments/trator.html`): chave da API, token JWT, Conectar e credenciais de teste quando disponíveis. O token é validado antes de substituir o acesso local. O Zapiou não apresenta formulários de usuário/senha; o chat reutiliza o JWT informado no Trator. A chave não é exigida pelas rotas nativas do Zapiou, assim como ocorre na fonte de produção das assinaturas.

V38 libera o número de canais registrados como excluídos em V37. O histórico mantém o canal anterior e seu identificador; um novo cadastro recebe novos IDs. A unicidade da tabela operacional considera somente canais não excluídos. Canais apenas inativos continuam reservando seus números. A validação normaliza números brasileiros e verifica duplicidade na mesma empresa antes da escrita, com bloqueio transacional. A migração também atualiza os registros que já haviam sido excluídos antes de V38.

Os testes incluem o modal da tabela mãe, cancelamento por botão/Escape, confirmação, descarte da instância, rodapé com token inválido, layout móvel e a sequência excluir → recadastrar o mesmo número sem modificar o histórico. O teste de migração cobre uma exclusão anterior a V38 e a reutilização posterior do número.
