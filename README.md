# aFIRMA

iniciando a migração para o Java

## API de assinatura

`POST /api/pdfs/assinar` recebe `multipart/form-data` com os campos `pdf`,
`certificado` (PKCS#12) e `senha`. Retorna `application/pdf` como anexo. O
certificado e a senha ainda são enviados a cada requisição; não há integração
com uma tabela de certificados neste projeto.

A operação exige **as duas credenciais**:

- `Authorization: Bearer <JWT>` emitido pelo provedor configurado, com público
  (`aud`) esperado e escopo `pdf.sign`.
- `X-API-Key: <chave>` com pelo menos 32 caracteres. Defina
  `APP_API_KEY_SHA256` com o SHA-256 hexadecimal da chave, nunca com a chave em
  texto claro.

Configure `APP_JWT_ISSUER_URI`, `APP_JWT_AUDIENCE` e `APP_API_KEY_SHA256` no
ambiente. O emissor JWT deve usar HTTPS. Sem essas variáveis, a aplicação não
deve iniciar. Configure PostgreSQL por `SPRING_DATASOURCE_URL`,
`SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD`. Configure Redis
por `SPRING_DATA_REDIS_HOST` e `SPRING_DATA_REDIS_PORT`. Exemplo para gerar a
chave e seu resumo:

```sh
openssl rand -hex 32
printf '%s' 'CHAVE_GERADA' | sha256sum
```

O limite inicial do PDF é 10 MiB. O administrador pode alterá-lo no PostgreSQL:

```sql
UPDATE configuracao_assinatura
SET tamanho_maximo_pdf_bytes = 10485760
WHERE id = 1;
```

O banco restringe o valor a 1–20 MiB. O servidor mantém teto fixo de 20 MiB
por arquivo e 23 MiB por requisição. A API lê no máximo 2 MiB do certificado,
confere o MIME declarado (`application/pdf`), o cabeçalho do buffer e a
estrutura do PDF ao assinar. Requisições acima do limite retornam 413;
MIME inválido retorna 415.

O Redis conta até 30 requisições por minuto por endereço remoto. A instância
aceita no máximo 2 assinaturas simultâneas. Ajuste ambos em
`app.assinatura` no `application.yaml`. Se Redis ou a configuração no banco
falhar, a assinatura é recusada. O filtro usa o endereço da conexão; não confia
em `X-Forwarded-For` enviado pelo cliente.

Em produção, coloque a API atrás de proxy com TLS, limite de corpo, tempo
máximo de upload e proteção contra DDoS. Restrinja acesso a PostgreSQL e Redis
à rede privada. Configure o proxy para preservar o endereço real do cliente
somente quando o proxy for confiável. O limite de código não substitui WAF,
proteção de rede, varredura de arquivos nem atualização de dependências. Um
PDF malicioso pode explorar falhas do parser antes da validação estrutural;
execute o serviço com limites de CPU e memória.
