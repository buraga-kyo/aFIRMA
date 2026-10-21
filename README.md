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
O projeto lê `.env` na raiz. O Docker Compose também usa esse arquivo. Copie `.env.example` para `.env` se ainda não existir. Informe emissor e público do seu provedor OAuth. O arquivo `.env` fica fora do Git.

### Gerar uma chave para a API
```sh
openssl rand -hex 32
printf '%s' 'CHAVE_GERADA' | sha256sum
```
Guarde a chave original em `APP_API_KEY` no `.env` para consultar quando for usar a tela. Coloque o resumo SHA-256 em `APP_API_KEY_SHA256`.

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

### ADVai: propostas jurídicas

Abra `http://127.0.0.1:8080/advai` no perfil `local-test`. Informe a empresa, o cliente, os serviços, honorários e textos. Após preencher a chave da API e o JWT, o catálogo do projeto `gerador-de-propostas-advocaticias` fica disponível; você também pode adicionar serviços próprios e editar os itens escolhidos. Gere PDF ou DOCX. O botão **Preparar assinatura** leva o PDF gerado à tela de convites. Revise o conteúdo antes do envio. A redação é baseada nos campos preenchidos; esta versão ainda não chama um provedor de IA.

### Convites para signatários


Abra `http://127.0.0.1:8080/preparar-assinaturas`. Informe empresa, PDF, chave da API e token JWT. Adicione até 20 signatários e posicione até 100 campos de assinatura, rubrica ou selfie. Clique na página para criar cada campo e arraste para ajustar. Gere os convites e copie os links; eles aparecem somente na resposta de criação e expiram em 7 dias. Compartilhe cada link com o signatário correspondente.

Cada signatário lê o PDF no próprio link, desenha assinatura e rubrica, envia selfie quando solicitada e confirma o aceite. O PDF é atualizado com as marcas assim que cada pessoa assina; os próximos signatários e o criador podem baixar essa versão atual. O link comprova posse do convite; esta etapa não verifica identidade por e-mail ou biometria. Quando todos concluírem, volte à página de preparação, atualize o estado e finalize com o certificado digital da empresa. O PDF final inclui as marcas nos lugares escolhidos e uma assinatura criptográfica sobre o documento completo. Após a finalização, os links permitem baixar o PDF final por mais 7 dias.

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
