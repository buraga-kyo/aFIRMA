# Publicações da FIRMA no Nostr

A aplicação Java faz o pareamento NIP-46, assina com o assinador remoto, publica nos relays e mantém a fila no PostgreSQL. Não há serviço Node ou Python para executar essa parte. A chave principal do perfil permanece no assinador remoto. A chave cliente autoriza pedidos de automação e também precisa ser protegida e revogada no assinador quando necessário.

Copie `nostr-identidades.example.json` para um caminho privado fora do repositório. Preencha a empresa, a chave pública hexadecimal do perfil, o endereço bunker fornecido pelo assinador e pelo menos dois relays públicos. Deixe `chaveCliente` vazia na primeira configuração. Prepare o arquivo com o próprio pacote Java:

```sh
chmod 600 /caminho/privado/nostr.json
java --enable-native-access=ALL-UNNAMED -jar target/assina-0.0.1-SNAPSHOT.jar --preparar-nostr /caminho/privado/nostr.json
```

Confira o nome do jar gerado em `target`. Esse comando gera somente a credencial de transporte que estiver faltando, preserva as existentes e grava o arquivo com permissão 600. Exibe apenas a chave pública do cliente. Não inicia o servidor nem acessa o banco. Autorize esse cliente no assinador remoto para eventos de perfil e publicações, kinds 0 e 1. O assinador precisa suportar NIP-46 com NIP-44.

Defina `APP_NOSTR_CONFIG` com o caminho absoluto do arquivo e reinicie a aplicação. Use também `--enable-native-access=ALL-UNNAMED` na JVM do servidor: a biblioteca secp256k1 utiliza libsecp256k1 nativa empacotada no jar. Não execute outro worker, porta ou token de worker. Na tela O Inventor, o administrador escolhe a identidade da empresa e confirma a autorização para publicar automaticamente. Pausar interrompe novas publicações; provas já publicadas continuam sendo verificadas.

## Anterioridade

O evento público contém um compromisso criptográfico com sal aleatório. O documento e o pedido privado ficam fora dos relays. Preserve o pacote de comprovação, o documento original, o evento e o arquivo `.ots`: são necessários para conferir a relação entre o documento e a publicação.

Os calendários OpenTimestamps recebem um hash, não o documento. Para confirmar a ancoragem Bitcoin, configure `APP_BITCOIN_RPC_URL`, `APP_BITCOIN_RPC_USER` e `APP_BITCOIN_RPC_PASSWORD` para seu Bitcoin Core sincronizado na rede principal. O RPC aceita HTTPS ou HTTP local. São exigidas seis confirmações. Sem esse nó, a aplicação preserva a prova pendente; não apresenta a confirmação como concluída. O horário declarado pelo evento Nostr não é um carimbo independente de tempo. A ancoragem fornece evidência de existência anterior ao bloco, sem certificar identidade civil ou validade jurídica por si só.

O banco limita pedidos ao assinador a 100 por identidade por hora e publicações a 500 por empresa por dia. As migrações V59 e V60 fazem parte da implantação. Restrinja o assinador às permissões necessárias e mantenha cópias privadas das credenciais e provas.

## JavaScript no projeto

`scripts/nostr` gera o pacote usado no navegador, e os testes de navegador usam ferramentas JavaScript de desenvolvimento. O conector existente `chat-baileys` permanece uma exceção: depende da biblioteca Baileys para WhatsApp Web. Ele não participa da assinatura ou publicação Nostr.

## Configurar pela tela

Em **O Inventor → Configurações**, o administrador cadastra o assinador NIP-46, os relays públicos e o RPC Bitcoin Core da empresa. O endereço direto é `/assineai/inventor/configuracao`. As alterações são usadas sem reiniciar o servidor. Os dados são criptografados no PostgreSQL, separados por empresa, com chave derivada do cofre de evidências. A migração V61 cria esse armazenamento e a auditoria de alterações.

O cofre requer a configuração existente `ASSINEAI_SEGREDO_EVIDENCIAS` ou `ASSINEAI_CHAVES_EVIDENCIAS` com `ASSINEAI_CHAVE_ATIVA`. Essa chave mestra continua fora da tela e do banco. Preserve as chaves históricas para ler configurações anteriores e mantenha backup seguro. O formulário informa quando o cofre ainda não está disponível.

A tela gera a credencial cliente Nostr; exibe somente sua chave pública para autorização no assinador remoto. Bunker e senha RPC são de gravação: em uma edição, deixar em branco mantém o valor anterior. Remover a configuração Bitcoin permite cadastrá-la novamente sem senha. Alterações na conexão Nostr pausam a publicação: confira o assinador e autorize novamente o perfil na área da empresa. O teste de conexão Nostr confere a identidade; não publica um evento nem garante que o assinador autorizou todos os kinds.

As configurações cadastradas na tela têm prioridade para a conexão correspondente. `APP_NOSTR_CONFIG` e `APP_BITCOIN_RPC_*` continuam aceitos como configuração da instalação. Remover o Bitcoin da empresa volta ao nó padrão da instalação, se houver. Para liberar RPC em HTTP local ou na rede privada, o operador precisa definir exatamente esse endereço em `APP_BITCOIN_RPC_URL`; o formulário não concede acesso livre à rede interna. Mantenha restrições de saída na rede do servidor para impedir acesso a serviços internos por resolução DNS maliciosa.

Os relays cadastrados pela tela precisam resolver para endereços públicos. O teste de Bitcoin consulta somente `getblockchaininfo`: não opera carteira nem movimenta fundos. Rede principal e sincronização completa continuam necessárias para confirmar anterioridade. A habilitação global do experimento Ethereum e os parâmetros internos de agendamento permanecem como configuração da instalação.
