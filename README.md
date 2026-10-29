# aFIRMA

### aFIRMA é uma acronimo recursivo para "A FIRma MAxima"
Todos os recursos para planejamento, execução de tarefas e gestão de relacionamento com clientes
aFIRMA vai ter vai disponibilizar de forma gratuita, se você quiser fechar esse repositorio ou precisa de ajudar para implementar aFIRMA na sua firma
entre em contato comigo por <sprtj@protonmail.com> eu aceito cripto como pagamento e se quiser me ajudar a comprar café para continuar desenvolvendo aFIRMA, ficaria, agradicido ;)

#### BTC (Native SegWit)
```
bc1qkvg28q9jatqm884ghkdvngzc38ahn9kxum4xkd
```
#### ETH
```
0x1A461A6ef7Ba0d1715ca143Eb74F5A2Ad245AF1A
```
### em resumo aFIRMA é um ERP e CRM completo para qualquer empresa.

por enquanto o que temos funcionando é a assinatura digital de documentos, a ideia é você usar o certificado da sua empresa (ou FIRMA rsrs) e pronto, vai sair assinando documentos com validade juridica sem ter que pagar
nada para ninguém, pois é XapZign, ZocuSign e etc vocês não são mais necessarios (mentira eles ainda tem muita coisa que não tem aqui, por enquanto....)

vou deixar o passo a passo de como assinar um PDF manualmente aqui, e cara, eu uso linux, se vira pra fazer no Ruindows ai valeu?!;
Em breve receio ter que colocar essas instruções em outro arquivo, enfim por enquanto segue:

### Requisitos minimos do sistema
Java 25+
Qualquer docker moderno
Qualquer linuxzinho barato

### Configuração local
Comece copiando `.env.example` para `.env` na raiz. A aplicação e o Docker Compose usam esse arquivo. Coloque ali o emissor e o audience do OAuth, além dos dados dos serviços que você vai usar. O `.env` fica fora do Git.

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

### Agora vamos ver o postgres e redis; no compose.yaml do projeto você deve deixar correto onde os serviços estão rodando

```yaml
services:
  postgres:
    image: 'postgres:latest'
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      POSTGRES_USER: ${POSTGRES_USER}
    ports:
      - '127.0.0.1:5432:5432'
  redis:
    image: 'redis:latest'
    ports:
      - '127.0.0.1:6379:6379'
```

### Agora ta no hora de subir tudo (que temos ate agora hehe)
```sh
docker compose up -d
```

### Confira se respondem
```sh
docker compose exec postgres pg_isready -U myuser -d mydatabase
docker compose exec redis redis-cli ping
```

As respostas esperadas são "accepting connections" e "PONG".

### Conferir o .env
Confira os dados do Postgres e Redis no `.env`. `SPRING_DATASOURCE_PASSWORD` precisa ser igual a `POSTGRES_PASSWORD`. Em banco já criado, mantenha a senha atual até alterar a senha do usuário no Postgres.

### Iniciar a aplicação (e agora que a gente começa a orar também)
```sh
./iniciar.sh
```
O script carrega `.env` e substitui valores antigos exportados no terminal.

## Para preguiçosos, tokens prontos...

Para testar a assinatura em `http://127.0.0.1:8080/`, inicie com o perfil `local-test`:

```sh
./iniciar.sh -Dspring-boot.run.profiles=local-test
```

### ADVai, pra montar a proposta

Abra `http://127.0.0.1:8080/advai` com o perfil `local-test`. Preencha empresa, cliente, serviços, honorários e o texto da proposta. Com a chave da API e o JWT, dá para usar o catálogo do `gerador-de-propostas-advocaticias`, adicionar serviços e editar os que você escolheu. Depois é só gerar PDF ou DOCX. O botão **Preparar assinatura** manda o PDF para a tela de convites.

Confira o texto antes de mandar para o cliente. Por enquanto ele é montado com o que você preencheu; a IA dessa parte ainda não entrou na roça.

### Quem já usa o Assineai

O cadastro novo usa as tabelas originais do Assineai, com os mesmos IDs inteiros, vínculos e tokens. Cada PDF guarda seu original, sua versão assinada e sua consulta de autenticidade. Os registros que foram criados com UUID na Firma continuam na opção **Registros anteriores da Firma**, lá na tabela.

Em `/empresa` você entra na conta, preenche CNPJ/e-mail e escolhe o tema. No `local-test`, dá para cadastrar e entrar com o segredo de teste. Para manter as sessões antigas em produção, configure `APP_ASSINEAI_JWT_SECRET` ou `JWT_SECRET` com o segredo que o Assineai já usava.

Os registros antigos que ainda não têm dono na estrutura nova usam a empresa de `APP_ASSINEAI_EMPRESA_LEGADA_ID`. Se seus clientes antigos chamam as rotas sem Authorization, existe `APP_ASSINEAI_COMPATIBILIDADE_PUBLICA=true` para essas rotas, associado à empresa configurada. A API nova `/api/assineai` continua exigindo autenticação.

Para certificar cada PDF automaticamente, configure `APP_ASSINEAI_CERTIFICADO_PATH` e `APP_ASSINEAI_CERTIFICADO_SENHA`. Também dá para informar PFX/P12 e senha pelo histórico. Sem certificado, a situação fica **Aguardando certificado**. Os links antigos precisam continuar chegando ao mesmo domínio e caminho quando você trocar o serviço.

### Convites para signatários


Abra `http://127.0.0.1:8080/preparar-assinaturas`. Entre pela tela **Empresa** ou informe seu JWT em **Conta da empresa e canais de entrega**. A chave da API continua necessária para os registros anteriores da Firma. A ordem é a mesma do Assineai antigo: signatários → documentos → finalização. Adicione até 20 signatários e até 10 PDFs com nomes próprios (até 100 páginas e 20 MB no conjunto, respeitando também o limite de PDF configurado). E-mail e WhatsApp são opcionais, obrigatórios apenas quando usados em um canal selecionado. Posicione até 100 campos de assinatura, rubrica ou selfie. Clique na página para criar cada campo e arraste para ajustar. Gere os convites e copie os links; eles aparecem somente na resposta de criação e expiram em 7 dias. Compartilhe cada link com o signatário correspondente.

Revise documentos, participantes e métodos na última etapa antes de gerar os convites. A lista de assinaturas oferece o botão **HISTÓRICO**, com documentos, situação e eventos por signatário.

Cada signatário lê o PDF no próprio link, desenha assinatura e rubrica, envia selfie quando solicitada e confirma o aceite. O PDF é atualizado com as marcas assim que cada pessoa assina; os próximos signatários e o criador podem baixar essa versão atual. Os documentos novos mantêm originais, versões assinadas e certificação individual. O convite permite selecionar e baixar cada PDF. O link comprova posse do convite. Se códigos por e-mail e WhatsApp forem selecionados, um dos canais válidos atende à verificação, como no Assineai de produção. A selfie é uma imagem solicitada, sem validação biométrica automática. Quando todos concluírem, volte à página de preparação, atualize o estado e finalize com o certificado digital da empresa. O PDF final inclui as marcas nos lugares escolhidos e uma assinatura criptográfica sobre o documento completo. Após a finalização, os links permitem baixar o PDF final por mais 7 dias.

Para habilitar entrega de códigos e notificações, configure `APP_ASSINATURAS_ENTREGA_URL` e, quando necessário, `APP_ASSINATURAS_ENTREGA_SEGREDO`. O gateway recebe um POST JSON com `canal` (`email` ou `whatsapp`), `destino`, `tipo` (`codigo` ou `convite`) e `conteudo`, usando `Authorization: Bearer` quando houver segredo. Respostas 2xx confirmam a entrega ao gateway. Sem gateway, a interface mantém canais externos indisponíveis e permite compartilhar links manualmente. Falhas ao notificar não desfazem os convites criados e aparecem na tela para nova tentativa. Códigos duram 10 minutos, têm até cinco tentativas e intervalo mínimo de um minuto entre emissões; somente o hash é persistido.

O perfil exige PostgreSQL e Redis configurados no `.env`, mas dispensa o provedor OAuth externo. Ele aceita apenas conexões em `127.0.0.1`. Informe na tela:


### CHAVE DA API
```text
afirma-teste-local-2026-chave-api-exclusiva
```
### TOKEN JWT
```text
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwOi8vMTI3LjAuMC4xOjgwODAvbG9jYWwtdGVzdCIsInN1YiI6InRlc3RlLWxvY2FsIiwiYXVkIjpbImFmaXJtYS5sb2NhbC10ZXN0Il0sImlhdCI6MTc5MjExOTYwMCwiZXhwIjoyMDk3MjczNjAwLCJzY29wZSI6InBkZi5zaWduIn0.n1fXvSU8qCLktT4SwVGcerEsfzXi8e7KVvTaT4XRSDU
```
## Para fodidos, certificados vencidos....
Para testar com certificado vencido, coloque `APP_PERMITIR_CERTIFICADO_VENCIDO=true` no `.env` e reinicie a aplicação. O padrão é `false`. A opção vale para todas as assinaturas enquanto estiver ativa, inclusive pela tela. Ela permite apenas certificado vencido; PDF, chave privada e senha continuam obrigatórios. Volte para `false` após o teste.

## Lista de documentos do AssineAi

Abra `http://127.0.0.1:8080/assineai/trator`. A tela exige chave da API e JWT com escopo `pdf.sign`; no perfil `local-test`, use as credenciais locais descritas acima. O título abre o PDF, e a lista mostra os envelopes criados pelo usuário autenticado. A exclusão é lógica e invalida os convites do documento.

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
