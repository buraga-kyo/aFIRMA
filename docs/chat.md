# Chat WhatsApp da aFirma

O backend Spring mantém canais, atendentes, setores, contatos, atendimentos e mensagens no PostgreSQL. Um processo Node.js separado usa Baileys para conectar números WhatsApp. A ponte aceita somente requisições locais com `APP_CHAT_BRIDGE_KEY`. Sessões e eventos pendentes ficam em `chat-baileys/dados`, fora do Git. Faça backup dessa pasta e proteja suas permissões.

## Iniciar

Configure `APP_CHAT_BRIDGE_KEY` no `.env` com pelo menos 32 caracteres aleatórios. Inicie PostgreSQL e Redis, depois o backend com `./iniciar.sh`. Em outro terminal, execute `npm --prefix chat-baileys ci` e `./iniciar-chat.sh`. A ponte atende somente `127.0.0.1:8091`. Após cadastrar um canal, chame `POST /api/chat/admin/canais/{id}/conectar`; consulte `GET /api/chat/admin/canais/{id}/qr` até obter o QR.

A interface fica em `/chat`. Ela aceita JWT e, no perfil `local-test`, oferece botão para carregar o token fixo abaixo.

## Acesso

Todas as APIs de chat exigem `X-API-Key` e JWT. Gestão exige escopo `chat.manage`; atendimento exige `chat.use` ou `chat.manage`. Identidade do atendente é o `sub` do JWT. Cadastre um atendente com `identidade` igual a esse `sub` e vincule seus setores. A assinatura de PDF mantém `pdf.sign`. O provedor OAuth precisa emitir os novos escopos para usuários humanos.

Com o perfil `local-test`, este token fixo tem `sub=teste-local` e os escopos `pdf.sign chat.manage chat.use`. Só funciona no servidor local de teste:

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
