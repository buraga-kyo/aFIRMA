# Editor de e-mails

O Trator carrega o editor Maily e o renderizador de e-mails do pacote local em
`src/main/resources/static/js/vendor`. Os arquivos distribuídos ficam no
repositório, como os demais recursos estáticos, para o build Maven e a execução
do Spring Boot funcionarem sem Node.

Para reconstruir depois de modificar o editor ou suas dependências:

```sh
npm --prefix frontend/email ci
npm --prefix frontend/email run build
```

`editor.jsx` adapta o editor ao Trator e recebe as variáveis de cada fluxo.
O documento Maily em JSON continua editável. A aplicação valida o HTML
renderizado antes de salvar ou enviar e aplica os valores reais no momento do envio.
