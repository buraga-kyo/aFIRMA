# Chat WhatsApp da aFirma

## TalkAtive integrado

Com `APP_CHATWOOT_URL=http://127.0.0.1:3100/` no `.env`, `/chat` mostra a central de atendimento dentro da navegação aFIRMA. Inicie a instância Chatwoot descrita em `../../chatwoot-afirma/docs/afirma.md` e o backend aFirma. A interface antiga continua disponível quando `APP_CHATWOOT_URL` não está definido. O cadastro e a sessão do Chatwoot ainda são próprios.

A tela `/chat` ocupa a janela inteira. A lateral do Chatwoot mostra a marca aFIRMA e apenas os grupos Conversas e Configurações. Dentro de Configurações fica o botão Canais da aFIRMA, que abre `/chat/canais`. A navegação entre as duas aplicações usa mensagens verificadas pela origem do Chatwoot. A autenticação do Chatwoot ainda é independente; a integração visual não substitui a futura sincronização de identidade e dados.

O menu `Canais` em `/chat/canais` usa as tabelas e a API da aFIRMA para criar canais Baileys, listar estado, conectar e mostrar QR. Também permite excluir um canal: a conexão é encerrada, o canal fica inativo e some da lista; o histórico permanece nas tabelas da aFIRMA. Ele exige JWT com `chat.manage` e `X-API-Key`; no perfil `local-test`, o botão de credenciais locais facilita a conferência. A criação da caixa correspondente no Chatwoot ainda é manual. O canal da aFIRMA e a caixa Chatwoot ainda usam vínculo configurado por variáveis de ambiente para um único canal.

A caixa de API `WhatsApp aFirma` usa o canal Baileys existente. Configure `APP_CHATWOOT_API_URL`, `APP_CHATWOOT_INBOX_ID`, `APP_CHATWOOT_INBOX_NUMERO`, `APP_CHATWOOT_CANAL_ID` e `APP_CHATWOOT_WEBHOOK_SECRET` no `.env`. Mensagens recebidas são registradas no backend aFirma e no Chatwoot. Respostas públicas dessa caixa passam pelo webhook assinado e seguem para o WhatsApp. O webhook só aceita mensagens de texto da caixa configurada. A associação automática de atendentes e setores entre as duas bases ainda exige sincronização.

O backend Spring mantém canais, atendentes, setores, contatos, atendimentos e mensagens no PostgreSQL. Um processo Node.js separado usa Baileys para conectar números WhatsApp. A ponte aceita somente requisições locais com `APP_CHAT_BRIDGE_KEY`. Sessões e eventos pendentes ficam em `chat-baileys/dados`, fora do Git. Faça backup dessa pasta e proteja suas permissões.

## Iniciar

Configure `APP_CHAT_BRIDGE_KEY` no `.env` com pelo menos 32 caracteres aleatórios. Inicie PostgreSQL e Redis, depois o backend com `./iniciar.sh`. Em outro terminal, execute `npm --prefix chat-baileys ci` e `./iniciar-chat.sh`. A ponte atende somente `127.0.0.1:8091`. Após cadastrar um canal, chame `POST /api/chat/admin/canais/{id}/conectar`; consulte `GET /api/chat/admin/canais/{id}/qr` até obter o QR.

A interface fica em `/chat`. Ela aceita JWT e, no perfil `local-test`, oferece botão para carregar o token fixo abaixo.

A gestão dos bots fica em `/chat/gestao`, na área **Bots**. O fluxo interno usa `nodes` e `edges`, com etapas `tarefa`, `script` (`transferir_setor` ou `gerar_atendimento`) e `integracao` (`rest`). Cada tarefa pode ter `pergunta`, `resposta`, `variavel` e `acoes` com condições como `&mensagem === '1'`. O início é uma ligação com `source: "start"`; `target: "end"` encerra a sessão. Sessões podem ser consultadas e canceladas pela tela. Os fluxos financeiros ficam fora do escopo desta implementação.

Bots externos recebem `POST` em `enderecoExterno` com `canal`, `sessao`, `telefone` e `mensagem`. O workflow pode usar as rotas autenticadas `PUT /api/relacionamento/bots/sessoes/{id}/continuacao` (`{"endereco":"https://..."}`), `POST /api/relacionamento/bots/sessoes/{id}/mensagens` (`{"texto":"..."}`), `POST /api/relacionamento/bots/sessoes/{id}/atendimento` (`{"setorId":1}`) e `POST /api/relacionamento/bots/sessoes/{id}/finalizar`. Essas chamadas exigem a mesma chave de API e um JWT com `chat.manage` da empresa da sessão. A URL de continuação recebe `sessao` e `mensagem` quando chegar a próxima resposta do contato.

## Acesso

Todas as APIs de chat exigem `X-API-Key` e JWT. Gestão exige escopo `chat.manage`; atendimento exige `chat.use` ou `chat.manage`. Identidade do atendente é o `sub` do JWT. Cadastre um atendente com `identidade` igual a esse `sub` e vincule seus setores. A assinatura de PDF mantém `pdf.sign`. O provedor OAuth precisa emitir os novos escopos para usuários humanos.

Com o perfil `local-test`, este token fixo tem `sub=teste-local` e os escopos `pdf.sign chat.manage chat.use`. Só funciona no servidor local de teste:

Para usar as credenciais deste README, pare o backend atual e inicie com `SPRING_PROFILES_ACTIVE=local-test ./iniciar.sh`. Sem esse perfil, o servidor usa o emissor JWT e a chave de API configurados no `.env`; as credenciais de teste recebem HTTP 401. Em `/chat/canais`, o botão “Usar credenciais locais” aparece somente quando `local-test` está ativo.

A chave de API local é `afirma-teste-local-2026-chave-api-exclusiva`. O botão da interface preenche ambos.

```text
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwOi8vMTI3LjAuMC4xOjgwODAvbG9jYWwtdGVzdCIsInN1YiI6InRlc3RlLWxvY2FsIiwiYXVkIjpbImFmaXJtYS5sb2NhbC10ZXN0Il0sImlhdCI6MTc5MjExOTYwMCwiZXhwIjoyMDk3MjczNjAwLCJzY29wZSI6InBkZi5zaWduIGNoYXQubWFuYWdlIGNoYXQudXNlIn0.3GXptW7Lrwl4knUYrT_cp2akCtucciv5UefkV1Qg4AY
```

## Fluxo

1. Crie setor em `POST /api/chat/admin/setores` com `{"nome":"Atendimento"}`.
2. Crie atendente em `POST /api/chat/admin/atendentes` com `{"identidade":"teste-local","nome":"Teste","setores":[1]}`.
3. Crie canal em `POST /api/chat/admin/canais` com `{"nome":"Principal","telefone":"5511999999999","setorPadraoId":1}`.
4. Conecte o canal e leia o QR. Mensagem recebida cria contato e atendimento pendente. Use `GET /api/chat/atendimentos` para consultar a fila.
5. Assuma em `POST /api/chat/atendimentos/{id}/assumir`, envie texto em `POST /api/chat/atendimentos/{id}/mensagens` com `{"texto":"Olá"}`. Transfira ou finalize pelos endpoints do atendimento.

Atendentes também podem iniciar conversas em `POST /api/chat/atendimentos` ou pela interface. O canal pode definir `inatividadeMinutos` e `mensagemEncerramento`; atendimentos inativos são finalizados em rotina de um minuto. Canais ativos são retomados automaticamente após reiniciar o backend, exceto no perfil `local-test`, em que a conexão é manual.

Mensagens repetidas não criam cópias. Um contato tem no máximo um atendimento aberto por canal. Atendentes só assumem atendimentos de seus setores. Gestores podem transferir e finalizar qualquer atendimento. Desativar atendente devolve seus atendimentos à fila.

Esta primeira integração processa texto e legendas recebidas; anexos sem legenda, templates e grupos ainda não entram no histórico. A ponte Baileys não usa a API oficial da Meta.
