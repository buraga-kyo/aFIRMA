# Referências dos testes

- BIP-340: https://github.com/bitcoin/bips/blob/master/bip-0340/test-vectors.csv
- NIP-44: https://github.com/paulmillr/nip44/blob/main/nip44.vectors.json
- Exemplo OTS: https://github.com/opentimestamps/java-opentimestamps/tree/master/examples (hello-world.txt e hello-world.txt.ots).
- eventos-nostr-tools.json: eventos de teste gerados com nostr-tools, incluindo Unicode e caracteres de controle, para conferir a serialização entre navegador e Java.

Os vetores BIP-340 exercitados usam mensagens de 32 bytes, como os hashes de eventos Nostr. As mensagens NIP-44 da aplicação têm limite de 65535 bytes. Os arquivos externos são preservados como fixtures para interoperabilidade; consulte as licenças nos respectivos projetos.
