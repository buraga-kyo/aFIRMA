# aFIRMA

### aFIRMA é uma acronimo recursivo para "A FIRma MAxima" 
Todos os recursos para planejamento, execução de tarefas e gestão de relacionamento com clientes
aFIRMA vai ter vai disponibilizar de forma gratuita, se você quiser fechar esse repositorio ou precisa de ajudar para implementar aFIRMA na sua firma
entre em contato comigo por <sprtj@protonmail.com> eu aceito cripto como pagamento e se quiser me ajudar a comprar café para continuar desenvolvendo aFIRMA, eu, ficaria, agradicido ;)

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

### Gerar uma chave para a API
```sh
openssl rand -hex 32
printf '%s' 'CHAVE_GERADA' | sha256sum
```
se o shar256sum gerar com um "tokentokenokentoken -" esse "-" não faz parte

### Provedor de Identidade Oauth
Vai precisar criar uma conta em https://manage.auth0.com/
criar uma aplicação machine to machine
em Permissions vai criar uma que se chama pdf.sign
em Application Access vai permitir User-delegated Access e Client Access
cara se ta confuso e não quer pagar pra eu configurar pra você vou te contar um segredo; joga em qualquer IA meia boca que ela vai configurar isso tudo ai.  
De qualquer forma não estou satisfeito com esse serviço ai de Oauth, em breve, vou atraz de outra solução para segurança mas por enquanto é isso...
Dito isto no QuickStart vc vai gerar o token em Ger Acess Token e ja vai ter o seu provedor e audience, agora é so jogar eles em uma variavel ambiente
```sh
export APP_JWT_ISSUER_URI='https://SEU-EMISSOR'
export APP_JWT_AUDIENCE='SEU-PUBLICO'
export APP_API_KEY_SHA256='SHA256_DA_CHAVE'
```

### Agora vamos ver o postgres e redis; no compose.yaml do projeto você deve deixar correto onde os serviços estão rodando

```yaml
services:
  postgres:
    image: 'postgres:latest'
    environment:
      - 'POSTGRES_DB=mydatabase'
      - 'POSTGRES_PASSWORD=secret'
      - 'POSTGRES_USER=myuser'
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

Tomara que tenha saidas como "accepting connections" e "PONG" se não, bom, taca na IA, ela vai saber resolver kkk ou me paga...

### Terminar de configurar mais variavel ambiente (se vc não sabe oq é isso, provavelmente vc é um vibecoder, sai daqui.)
```sh
export SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5432/mydatabase'
export SPRING_DATASOURCE_USERNAME='myuser'
export SPRING_DATASOURCE_PASSWORD='secret'
export SPRING_DATA_REDIS_HOST='localhost'
export SPRING_DATA_REDIS_PORT='6379'
```

### Iniciar a aplicação (e agora que a gente começa a orar também)
```sh
./mvnw spring-boot:run
```

### Assinar um PDF com certificado digital valido
```curl
curl -f 'http://localhost:8080/api/pdfs/assinar' \          
-H 'Authorization: Bearer eyJhbGciOi1NiIsInR5cCI6IkpXVCIsImtpZCI6IjJ5dFNQMXlSVlNESENEbC1JeVEwNiJ9.eyJpc3MiOiJodHRwczovL2Rldi12cmI1cDhoN3RveWpzZnlyLnVzLmF1dGgwLmNvbS8iLCJzdWIiOiJCaUJpOWhWQnZ5ZWxPZ21HandCVzZBUjg1RUJGNldFNUBjbGllbnRzIiwiYXVkIjoiYWZpcm1hLmxvY2FsIiwiaWF0IjoxNzkyMDY0OTg5LCJleHAiOjE3OTIxNTEzODksInNjb3BlIjoicGRmLnNpZ24iLCJndHkiOiJjbGllbnQtY3JlZGVudGlhbHMiLCJhenAiOiJCaUJpOWhWQnZ5ZWxPZ21HandCVzZBUjg1RUJGNldFNSJ9.XUjrPXhq-lzj7voN-WfDE47aClJ18-2MsdxIrHkUDdKYDiVHVSu9dX8wi0lA4-WmTnHFVp_au22n2fFkj5eYUtiyuRnNzETJsMJrDfqUevfczk9XQ9JNdwi7Ul9DtEU4dUsL_RxhYIDfqbv8de_K4DrHP9oCnMoxoMXUW1sp8yxp33rCu4nK0aTdByNZB_UZUiabxOrmWUw0jM8X3kYVcIgcqwWA7YL4C2sedDO8m-CT6MQ6pAEsK-Z7LD7FRasvEMnvFka4GMW_g-ezGjA2fJU2JdFykHKV0E-rqBP5dPHO6OJ2yjMAvPbSrEpsx9QLgl3NOQyvk--jL5-TeBoofQ' \
-H 'X-API-Key: 1b913c1ea24dafc2b46358f76779a7967a9630efea4cae21b1b7bf62c24' \
-F 'pdf=@pdf.pdf;type=application/pdf' \
-F 'certificado=@certificado.pfx' \
-F 'senha=123123' \
-F 'permitirCertificadoVencido=true' \
-o documento-assinado.pdf
```
