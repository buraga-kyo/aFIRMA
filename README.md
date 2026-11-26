<div align="center">
  
# { aFIRMA }
</div>

<p align="center">
  <img src="./Trator daFIRMA.png" alt="imagem do trator daFIRMA">
</p>

### A FIRma MAxima
```
A
  FIRma
     MAxima

A
FIR     ma
MA      xima

a
 fir             MA
    ma             XIMA
```

### em resumo aFIRMA é um ERP e CRM completo para qualquer empresa.
Se precisar de ajuda para implementar aFIRMA na sua firma entre em contato comigo por <sprtj@protonmail.com> eu aceito cripto como pagamento e se quiser me ajudar a comprar café aFIRMA agradece!

#### BTC (Native SegWit)
```
bc1qkvg28q9jatqm884ghkdvngzc38ahn9kxum4xkd
```
#### ETH
```
0x1A461A6ef7Ba0d1715ca143Eb74F5A2Ad245AF1A
```

#### é aFIRMA demonstrando a essência daFIRMA, praFIRMA, comFIRMA? pera comFIRMAndo... comFIRMAdo!
Em breve vai ter videos mostrando como plantar e colher os fruto daFIRMA no seu aparato tecnologico mas por enquanto eu tive paciencia pra ensinar mais ou menos isso ai a baixo: 

vou deixar o passo a passo de como assinar um PDF manualmente aqui, e cara, eu uso linux, se vira pra fazer no Ruindows ai valeu?!;
Em breve receio ter que colocar essas instruções em outro arquivo, enfim por enquanto segue:

### Requisitos mínimos

Linux com Bash 5.1 ou mais novo, Java 25 ou mais novo, Node.js 20 ou mais novo com npm, Docker com o plugin Compose e curl.

### Subir a aFIRMA

Pra desenvolver e testar, rode:

```sh
./afirma --dev
```

Abra http://127.0.0.1:8081 e use o acesso de teste no Trator daFIRMA. O perfil `local-test` usa credenciais próprias, banco, Redis, MinIO e dados do Baileys separados da produção. O Mailpit recebe os e-mails de teste em http://127.0.0.1:8025. Pra trocar as portas da aplicação e da ponte, use `AFIRMA_DEV_PORTA` e `AFIRMA_DEV_PORTA_PONTE`.

Pra produção, copie `.env.example` para `.env`, preencha as credenciais do OAuth, da API, do PostgreSQL, do MinIO e do Baileys e rode:

```sh
./afirma --prod
```

A aplicação abre na porta 8080 por padrão. Em banco já criado, mantenha a senha atual até alterar a senha do usuário no PostgreSQL. O `.env` fica fora do Git.

Os dois modos sobem PostgreSQL, Redis, MinIO, a ponte Baileys e a aplicação Java. O modo de desenvolvimento também sobe o Mailpit. Chatwoot e SMS Gateway for Android são integrações externas configuradas à parte.

Ctrl+C encerra a aplicação Java e a ponte. Os serviços do Docker continuam disponíveis pra próxima inicialização. Se quiser parar os serviços de desenvolvimento também, pare os contêineres do projeto `afirma-dev` pelo Docker.

### Gerar uma chave para a API
```sh
openssl rand -hex 32
printf '%s' 'CHAVE_GERADA' | sha256sum
```
A chave que saiu do OpenSSL vai em `APP_API_KEY` no `.env`. O resultado do `sha256sum` vai em `APP_API_KEY_SHA256`. Na tela você usa a chave original.

### Provedor de Identidade Oauth
Vai precisar criar uma conta em https://manage.auth0.com/
criar uma aplicação machine to machine
em Permissions vai criar uma que se chama pdf.sign
em Application Access vai permitir User-delegated Access e Client Access
De qualquer forma não estou satisfeito com esse serviço ai de Oauth, em breve, vou atraz de outra solução para segurança mas por enquanto é isso...
Dito isto no QuickStart vc vai gerar o token em Ger Acess Token. Coloque o emissor em `APP_JWT_ISSUER_URI` e o audience em `APP_JWT_AUDIENCE` no `.env`.

### Relacionamento daFIRMA

A página `/` abre AssineAi, Zapiou e ADVai. O Trator de Relacionamento fica em `/chat`, com o mesmo componente de tabela das assinaturas. Cadastros, permissões, atendimentos, campanhas e automações são controlados pela aplicação Java. O motor de chat funciona como serviço integrado. As APIs de negócio usam `/api/firma` e exigem autenticação e autorização por empresa. Veja [o contrato de dados](docs/relacionamento.md), [as regras operacionais](docs/regras-relacionamento.md) e [a cobertura verificada](docs/auditoria-relacionamento-firma.md).

### ADVai, pra montar a proposta

Abra `http://127.0.0.1:8081/advai` depois de iniciar com `./afirma --dev`. Preencha empresa, cliente, serviços, honorários e o texto da proposta. Com a chave da API e o JWT, dá para usar o catálogo do `gerador-de-propostas-advocaticias`, adicionar serviços e editar os que você escolheu. Depois é só gerar PDF ou DOCX. O botão **Preparar assinatura** manda o PDF para a tela de convites.

Confira o texto antes de mandar para o cliente. Por enquanto ele é montado com o que você preencheu; a IA dessa parte ainda não entrou na roça.

### Quem já usa o Assineai

O cadastro novo usa as tabelas originais do Assineai, com os mesmos IDs inteiros, vínculos e tokens. Cada PDF guarda seu original, sua versão assinada e sua consulta de autenticidade. Os registros que foram criados com UUID na Firma continuam na opção **Registros anteriores da Firma**, lá na tabela.

Em `/empresa` você entra na conta, preenche CNPJ/e-mail e escolhe o design da folha de registro acrescentada ao PDF. O estúdio permite editar a composição e criar versões próprias; veja [os designs e o editor](docs/templates-empresa.md). No `local-test`, dá para cadastrar e entrar com o segredo de teste. Para manter as sessões antigas em produção, configure `APP_ASSINEAI_JWT_SECRET` ou `JWT_SECRET` com o segredo que o Assineai já usava.

Os registros antigos que ainda não têm dono na estrutura nova usam a empresa de `APP_ASSINEAI_EMPRESA_LEGADA_ID`. Se seus clientes antigos chamam as rotas sem Authorization, existe `APP_ASSINEAI_COMPATIBILIDADE_PUBLICA=true` para essas rotas, associado à empresa configurada. A API nova `/api/assineai` continua exigindo autenticação.

Para certificar cada PDF automaticamente, configure `APP_ASSINEAI_CERTIFICADO_PATH` e `APP_ASSINEAI_CERTIFICADO_SENHA`. Também dá para informar PFX/P12 e senha pelo histórico. Sem certificado, a situação fica **Aguardando certificado**. Os links antigos precisam continuar chegando ao mesmo domínio e caminho quando você trocar o serviço.

### Convites para signatários


Abra `http://127.0.0.1:8080/preparar-assinaturas`. Entre pela tela **Empresa** ou informe seu JWT em **Conta da empresa e canais de entrega**. A chave da API continua necessária para os registros anteriores da Firma. A ordem é a mesma do Assineai antigo: signatários → documentos → finalização. Adicione até 20 signatários e até 10 PDFs com nomes próprios (até 100 páginas e 20 MB no conjunto, respeitando também o limite de PDF configurado). E-mail e WhatsApp são opcionais, obrigatórios apenas quando usados em um canal selecionado. Posicione até 100 campos de assinatura, rubrica ou selfie. Clique na página para criar cada campo e arraste para ajustar. Gere os convites e copie os links; eles aparecem somente na resposta de criação e expiram em 7 dias. Compartilhe cada link com o signatário correspondente.

Revise documentos, participantes e métodos na última etapa antes de gerar os convites. A lista de assinaturas oferece o botão **HISTÓRICO**, com documentos, situação e eventos por signatário.

Cada signatário lê o PDF no próprio link, desenha assinatura e rubrica, envia selfie quando solicitada e confirma o aceite. O PDF é atualizado com as marcas assim que cada pessoa assina; os próximos signatários e o criador podem baixar essa versão atual. Os documentos novos mantêm originais, versões assinadas e certificação individual. O convite permite selecionar e baixar cada PDF. O link comprova posse do convite. Se códigos por e-mail e WhatsApp forem selecionados, um dos canais válidos atende à verificação, como no Assineai de produção. A selfie é uma imagem solicitada, sem validação biométrica automática. Quando todos concluírem, volte à página de preparação, atualize o estado e finalize com o certificado digital da empresa. O PDF final inclui as marcas nos lugares escolhidos e uma assinatura criptográfica sobre o documento completo. Após a finalização, os links permitem baixar o PDF final por mais 7 dias.

Para habilitar entrega de códigos e notificações por e-mail e WhatsApp, configure `APP_ASSINATURAS_ENTREGA_URL` e, quando necessário, `APP_ASSINATURAS_ENTREGA_SEGREDO`. O gateway recebe um POST JSON com `canal` (`email` ou `whatsapp`), `destino`, `tipo` (`codigo`, `convite` ou `observador`) e `conteudo`, usando `Authorization: Bearer` quando houver segredo. Respostas 2xx confirmam a entrega ao gateway. Sem gateway, a interface permite compartilhar links manualmente. Falhas ao notificar não desfazem os convites criados e aparecem na tela para nova tentativa. Códigos duram 10 minutos, têm até cinco tentativas e um intervalo mínimo de um minuto entre emissões. O sistema guarda apenas o hash do código.

Na preparação, cada signatário aceita até 20 observadores com e-mail, WhatsApp ou os dois. As atualizações são enfileiradas na ordem das movimentações e incluem a primeira abertura ou substituição do convite, a recusa e a assinatura do signatário vinculado. Os observadores também recebem avisos de criação, conclusão e cancelamento do envelope. O e-mail do observador também pode ser enviado pelo SMTP da aFIRMA. A mensagem traz o título e a movimentação, sem link de convite ou PDF.

Para habilitar SMS no wizard, ative o modo **Local Server** no [SMS Gateway for Android](https://github.com/capcom6/android-sms-gateway#local-server). Configure `APP_ASSINATURAS_SMS_URL` com o endpoint completo (por exemplo, `http://IP_DO_ANDROID:8080/message` em rede privada), mais `APP_ASSINATURAS_SMS_USUARIO` e `APP_ASSINATURAS_SMS_SENHA` com as credenciais do gateway. Use uma rede privada ou HTTPS e reinicie o aFIRMA. O wizard usa o mesmo campo **Celular** para WhatsApp e SMS. Selecione **Código por SMS** para exigir o código no convite ou **SMS** para enviar o link. O acesso inicial ao convite continua usando o e-mail e o aceite do gateway não confirma a entrega ao celular.

O perfil exige PostgreSQL e Redis configurados no `.env`, mas dispensa o provedor OAuth externo. Ele aceita apenas conexões em `127.0.0.1`. Informe na tela:


### CHAVE DA API
```text
afirma-teste-local-2026-chave-api-exclusiva
```
### Conexão compartilhada

Abra `/afirma/trator` e use **Conectar** ou **Credenciais de teste** no rodapé. O acesso é reaproveitado nas outras telas e expira em 30 minutos por padrão. Configure `APP_SESSAO_ACESSO_MINUTOS` no `.env` e reinicie para mudar o prazo. Navegar ou atualizar a página não renova a sessão. O prazo também respeita a expiração do JWT externo. Credenciais fixas antigas de teste foram substituídas pelo botão do rodapé.

## TOKEN JWT
```text
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwOi8vMTI3LjAuMC4xOjgwODAvbG9jYWwtdGVzdCIsInN1YiI6InRlc3RlLWxvY2FsIiwiYXVkIjpbImFmaXJtYS5sb2NhbC10ZXN0Il0sImlhdCI6MTc5MjExOTYwMCwiZXhwIjoyMDk3MjczNjAwLCJzY29wZSI6InBkZi5zaWduIn0.n1fXvSU8qCLktT4SwVGcerEsfzXi8e7KVvTaT4XRSDU
```
## Para fodidos, certificados vencidos....
Para testar com certificado vencido, coloque `APP_PERMITIR_CERTIFICADO_VENCIDO=true` no `.env` e reinicie a aplicação. O padrão é `false`. A opção vale para todas as assinaturas enquanto estiver ativa, inclusive pela tela. Ela permite apenas certificado vencido; PDF, chave privada e senha continuam obrigatórios. Volte para `false` após o teste.

## Lista de documentos do AssineAi

Abra `http://127.0.0.1:8080/afirma/trator`. A tela exige chave da API e JWT com escopo `pdf.sign`; no perfil `local-test`, use as credenciais locais descritas acima. O título abre o PDF, e a lista mostra os envelopes criados pelo usuário autenticado. A exclusão é lógica e invalida os convites do documento.

Para guardar novos PDFs no MinIO, configure `ASSINEAI_MINIO_ENDPOINT`, `ASSINEAI_MINIO_ACCESS_KEY`, `ASSINEAI_MINIO_SECRET_KEY` e `ASSINEAI_MINIO_BUCKET` no `.env` (veja `.env.example`), depois execute `docker compose up -d`. Registros antigos com PDF no PostgreSQL continuam legíveis. Sem essas variáveis, o armazenamento anterior continua ativo.

O **Burro de Carga** usa GPT se você configurar `ASSINEAI_GPT_API_KEY`. Para escolher o modelo, use `ASSINEAI_GPT_MODEL`. Ele monta o plano de busca e mostra antes de aplicar: confira os filtros e confirme na tela.

## Vai usar a tabela em outra tela?

O [guia da tabela](docs/tabela.md) mostra como instanciar a classe, escolher colunas e ligar só os recursos que sua tela precisa. As abas e os comandos do trator ficam na própria barra da tabela.

## Para quem não gosta de tela, vai toma no curl....

```curl
curl -f 'http://localhost:8080/api/pdfs/assinar' \
-H 'Authorization: Bearer eyJhbGciOi1NiIsInR5cCI6IkpXVCIsImtpZCI6IjJ5dFNQMXlSVlNESENEbC1JeVEwNiJ9.eyJpc3MiOiJodHRwczovL2Rldi12cmI1cDhoN3RveWpzZnlyLnVzLmF1dGgwLmNvbS8iLCJzdWIiOiJCaUJpOWhWQnZ5ZWxPZ21HandCVzZBUjg1RUJGNldFNUBjbGllbnRzIiwiYXVkIjoiYWZpcm1hLmxvY2FsIiwiaWF0IjoxNzkyMDY0OTg5LCJleHAiOjE3OTIxNTEzODksInNjb3BlIjoicGRmLnNpZ24iLCJndHkiOiJjbGllbnQtY3JlZGVudGlhbHMiLCJhenAiOiJCaUJpOWhWQnZ5ZWxPZ21HandCVzZBUjg1RUJGNldFNSJ9.XUjrPXhq-lzj7voN-WfDE47aClJ18-2MsdxIrHkUDdKYDiVHVSu9dX8wi0lA4-WmTnHFVp_au22n2fFkj5eYUtiyuRnNzETJsMJrDfqUevfczk9XQ9JNdwi7Ul9DtEU4dUsL_RxhYIDfqbv8de_K4DrHP9oCnMoxoMXUW1sp8yxp33rCu4nK0aTdByNZB_UZUiabxOrmWUw0jM8X3kYVcIgcqwWA7YL4C2sedDO8m-CT6MQ6pAEsK-Z7LD7FRasvEMnvFka4GMW_g-ezGjA2fJU2JdFykHKV0E-rqBP5dPHO6OJ2yjMAvPbSrEpsx9QLgl3NOQyvk--jL5-TeBoofQ' \
-H 'X-API-Key: 1b913c1ea24dafc2b46358f76779a7967a9630efea4cae21b1b7bf62c24' \
-F 'pdf=@pdf.pdf;type=application/pdf' \
-F 'certificado=@certificado.pfx' \
-F 'senha=123123' \
-o documento-assinado.pdf
```

## Revisão da assinatura digital

A revisão de assinatura foi integrada à versão atual do GitHub. O trator, os lotes, o editor e os modelos da empresa permanecem como base. Consulte o [passo a passo](docs/como-usar-assinatura.md) e a [documentação da assinatura](docs/assinatura-digital.md). As credenciais de envio e a senha do certificado ficam exclusivamente no `.env`.

Configuração das publicações Nostr e das provas de anterioridade: [NOSTR.md](NOSTR.md).
