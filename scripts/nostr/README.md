# Biblioteca Nostr do Inventor

A página `/assineai/inventor` usa NIP-07 para solicitar assinaturas à extensão do participante. A chave privada não é recebida pela aplicação. A conferência usa `nostr-tools/pure` e as bibliotecas Noble, distribuídas localmente em `static/js/vendor/nostr.js`.

Para reconstruir a biblioteca:

```sh
cd scripts/nostr
npm ci
npm run build
```

Para conferir a interface e as provas, a partir da raiz do projeto, com Playwright instalado:

```sh
NODE_PATH=/caminho/do/playwright/node_modules node scripts/testar-inventor.cjs
```

O formato `afirma.nostr.aceite.v1` contém um evento Nostr de tipo 1 com uma declaração JSON no conteúdo. A declaração vincula protocolo, ação, SHA-256 e tamanho do PDF, nome do arquivo, identificador, desafio aleatório de 32 bytes, papel declarado e texto de aceite. A assinatura cobre a serialização NIP-01 do evento.

O participante guarda o PDF original e a prova JSON juntos. O PDF não é modificado. A conferência compara a assinatura, o protocolo, o hash e o tamanho do arquivo; opcionalmente compara uma chave pública esperada. A data do evento é declarada pelo assinante. Este experimento não associa automaticamente uma chave a uma identidade civil, não emite certificado ICP-Brasil, não publica em relays e não altera envelopes de assinatura existentes.

As versões das dependências estão fixadas no `package-lock.json`. As licenças estão em `src/main/resources/static/js/vendor/nostr-LICENSE.txt` a partir da raiz do projeto.
