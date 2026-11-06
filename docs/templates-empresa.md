# Designs da folha adicional de assinaturas

Este recurso personaliza **a folha de registro de assinaturas acrescentada ao PDF**, com os dados reais do documento e dos signatários. O documento enviado não é recomposto, e a tela do convite não é o alvo da personalização.

## Escolher e editar

Entre em `/firma` e salve o nome daFIRMA. Depois abra **AssineAI → Telas → Página de assinatura**, em `/assineai/pagina-assinatura`. O editor usa o token salvo; também dá para informar outro token em **Carregar modelos**.

A galeria usa miniaturas renderizadas pelo próprio gerador de PDF:

| Design | Composição |
| --- | --- |
| Atlas | Grade técnica, régua lateral, recortes diagonais, informações dos signatários em duas colunas |
| Botânica | Folhagem vetorial, formas orgânicas, faixas arredondadas e paleta mineral |
| Noir | Moldura art déco dupla, diamantes, fundo noturno, ouro e título centralizado |
| Prisma | Faixas diagonais, cantos em camadas, tipografia em duas linhas e violeta |
| Editorial | Numeração de registro, inicial ampliada, terracota, títulos editoriais e filetes |
| Linho | Textura vetorial de papel, monograma, tipografia serifada e papel timbrado |

Selecione um design ou clique em **Criar minha versão**. **Começar do zero** abre uma composição sem os ornamentos dos modelos predefinidos. Também é possível carregar uma imagem de fundo/marca d’água e ajustar sua opacidade. É possível ajustar cores, logotipo, tipografia, título, linha de apresentação e assinatura da marca. Em **Composição livre dos blocos**, ative as alças para arrastar e redimensionar as quatro áreas da folha. Também é possível usar as setas do teclado, medidas em pontos, desfazer e refazer. Blocos não podem se sobrepor nem sair da página.

Clique em **Definir como padrão** e **Salvar modelo**. A próxima geração da folha no fluxo de assinatura usa o modelo ativo da empresa. PDFs já gerados não mudam só porque a preferência foi alterada. **Baixar amostra em PDF** contém apenas a folha, com dados fictícios para avaliação visual. Não cria um contrato ou documento de negócio.

O editor no ADVai permite exportar e importar a biblioteca em JSON; a importação passa pela validação do servidor antes de entrar no editor. São aceitas até 24 versões e 4 MB por biblioteca. Logotipos são PNG/JPEG, até 500 KB.

## Campos preservados

Todos os designs recebem o mesmo conjunto de dados:

- Nome da empresa, título do documento, identificador, SHA-256 original e endereço de autenticidade.
- Nome e qualificação do signatário, situação, data e métodos de assinatura.
- E-mail, WhatsApp, CPF, RG, IP, dispositivo e localização informada, quando disponíveis.
- Emissão, orientações de integridade e imagens reais de assinatura/selfie, quando coletadas.

Campos sem informação são identificados como não informados. A composição quebra linhas e cria folhas de continuação para preservar dados longos e múltiplos signatários. Imagens de evidência recebem folhas com a mesma identidade visual. Os textos de prova não podem ser apagados, substituídos por valores fixos do editor ou ocultados por remoção de blocos.

A certificação digital acontece depois de acrescentar as folhas, de modo que elas integram os bytes cobertos pela assinatura criptográfica. O anexo usa o modo incremental do OpenPDF, preservando assinaturas anteriores quando existem.

## Fluxos

- **Assineai / preparar convites:** a geração de `DocumentoAssinadoBuffer` busca a empresa vinculada à assinatura e aplica seu modelo ativo. O desenho e os campos sobre o documento original continuam no fluxo existente.
- **Assinatura direta / certificado ou carteira:** em `/assinatura`, marque **Adicionar página personalizada** e use **Carregar modelos do meu perfil**. É necessário ter entrado em `/empresa`. O modelo selecionado é enviado como `templatePagina`, validado e aplicado antes da assinatura. Sem seleção, usa Atlas.
- `PaginaComprovante` e `PdfAssineai` agora compartilham o renderizador de folhas `PaginaRegistroAssinaturas`.

## Estrutura e API

A migração V24 acrescenta a coluna JSONB `EmpresaTemplates`. O contrato da biblioteca é versão 2: `{versao: 2, ativo: "id", modelos: [...]}`. A versão 1 pertencia à implementação equivocada de páginas de convite e documentos completos; ela não é usada no relatório. Ao salvar uma escolha nova, o perfil grava a versão 2.

Os modelos guardam design, paleta, tipografia, textos de marca e posições de `cabecalho`, `documento`, `signatarios` e `integridade`. O servidor exige esses quatro blocos, dimensões mínimas, áreas sem sobreposição, cores válidas e imagens locais embutidas. Conteúdo HTML/SVG, scripts e imagens externas não são recursos permitidos do modelo.

- `GET /api/assineai/empresa/templates`: catálogo de designs.
- `POST /api/assineai/empresa/templates/previa`: `{modelo, empresa, formato: "png" | "pdf"}`; renderiza dados de amostra.
- `POST /api/assineai/empresa/templates/validar`: valida a biblioteca importada.
- `PUT /api/assineai/empresa`: salva a biblioteca na empresa autenticada, sem aceitar outro ID de empresa.

A prévia é o PDF real renderizado em PNG pelo PDFBox; não é uma aproximação em HTML. Não foi acrescentada uma dependência de editor ou serviço Rails. A estrutura de áreas posicionadas do DocuSeal foi examinada como referência, e o motor OpenPDF/PDFBox já presente na FIRMA foi reutilizado para o resultado final. Não foi copiado código do DocuSeal.

## Testes

```sh
./mvnw -q -Djava.awt.headless=true test
```

`TemplatesEmpresaTest` verifica os seis designs com o mesmo conjunto de dados, rejeição de modelos inseguros, blocos obrigatórios, sobreposição, paginação com 25 participantes e dados longos, preservação da primeira página e links de autenticidade. `ServicoAssinaturaPdfTest` verifica a assinatura criptográfica cobrindo integralmente o documento em cada um dos seis designs, além da preservação de assinaturas anteriores.

O teste de navegador usa uma instância **descartável** com PostgreSQL, Redis e segredo JWT de teste. Cria duas empresas, configura um design, ajusta posições, salva, recarrega, assina um PDF e verifica os dados e a cor do design na folha efetivamente acrescentada. Requer Chrome, Playwright e `pdftotext` (Poppler).

```sh
npm install --prefix /tmp/firma-browser playwright --no-audit --no-fund
NODE_PATH=/tmp/firma-browser/node_modules \
FIRMA_TEST_BASE_URL=http://127.0.0.1:18089 \
node src/test/js/templates-empresa.browser.cjs
```

`CHROME_BIN` indica o executável do Chrome. `FIRMA_TEST_ARTIFACTS` escolhe o diretório das evidências; padrão `/tmp/firma-registro-browser`.

Os dados e a aparência do convite ficam em `/firma`. Salvar esses dados não altera a biblioteca de modelos. No ADVai, **Salvar modelo** atualiza somente `EmpresaTemplates`.

O padrão pertence à empresa e é aplicado no servidor aos convites, à assinatura individual por certificado e à preparação de assinatura por carteira. O botão Salvar como padrão da empresa grava a escolha imediatamente. PDFs já finalizados permanecem inalterados.

O primeiro modelo é daFIRMA / Relatório de assinaturas: folha branca, tipografia sem serifa, linhas cinza, QR de autenticidade e participantes em lista. É o padrão inicial de empresas sem biblioteca definida. Bibliotecas existentes mantêm seu modelo ativo; o relatório pode ser adicionado pela galeria sem substituir as versões salvas.


## Bancada documental nativa em Java

A página `/assineai/pagina-assinatura` também prepara documentos reais. O fluxo principal usa `/api/assineai/editor`; não exige Rails, serviço DocuSeal ou licença Pro. A interface é JavaScript e o processamento, a persistência e as assinaturas são Java. O conector externo anterior permanece opcional e recolhido.

- Abra o PDF; arraste assinatura, rubrica, texto, data, seleção, número ou anexo para a página. Selecione o campo para definir nome, papel e obrigatoriedade. Setas movem; Shift acelera; Delete exclui; Ctrl/Cmd+C/V copia/cola; Ctrl/Cmd+D duplica; Ctrl/Cmd+Z desfaz e Shift+Z refaz. A centralização e a detecção de campos AcroForm têm botões próprios.
- O menu Páginas gira, reordena, duplica, exclui, recorta, oculta áreas e acrescenta outros PDFs. Recorte e ocultação rasterizam a página afetada para retirar o conteúdo original, com perda de texto pesquisável nessa página. PDFs com assinatura digital não são reestruturados. As posições dos campos acompanham rotação, ordem e recorte. Campos cortados ou sobre áreas ocultadas são removidos. Desfazer recupera PDF e campos.
- Salvar modelo grava PDF e campos no PostgreSQL com proprietário, versão e SHA-256. As migrações V42 a V44 acrescentam modelos, campos de preenchimento e anexos. A edição concorrente exige a versão atual; uma edição desatualizada recebe 409. Abrir uma revisão histórica permite inspecioná-la ou salvá-la como cópia. Cada identidade só acessa seus modelos e envelopes.
- Preparar participantes vincula papéis a pessoas. Gerar convites usa o motor de envelopes da FIRMA, sem enviar notificações automaticamente. Acompanhamento mostra participantes e histórico de eventos, permite baixar o PDF atual e finalizar com certificado PFX/P12 após todas as assinaturas.
- No convite, o participante preenche os campos, anexa documentos e desenha as marcas solicitadas. O Java valida obrigatoriedade, data, número, limites e propriedade dos campos; grava valores no PDF e registra a confirmação no histórico. Anexos PDF/PNG/JPEG de até 2 MB ficam associados ao campo com SHA-256; só o participante desse convite e o proprietário do envelope podem baixá-los.
- O mapa JSON exportado continua vinculado ao SHA-256 do arquivo. Os modelos salvos são a opção persistente; edições locais precisam ser salvas ou exportadas antes de trocar de documento ou sair.

Limites: PDF até 20 MB, 100 páginas, combinação de até 10 arquivos, 200 campos no modelo e 100 no envelope, 20 participantes. O editor usa limite de requisições separado (`app.editor.requisicoes-por-minuto`, padrão 180). A finalização continua exigindo o certificado da empresa; as marcas e os campos preenchidos não substituem essa etapa.

Validação automatizada: `EditorDocumentosPdfTest` verifica operações e remoção do conteúdo ocultado; `editor-firma-native.browser.cjs` usa backend Java/PostgreSQL reais para modelos, versões, isolamento, conflitos, manipulação de páginas, convites, preenchimento, marcas e anexos. O navegador usa Playwright e Chrome (`NODE_PATH` pode apontar para a instalação de Playwright). A instância local de validação roda em `127.0.0.1:18092`, com armazenamento no PostgreSQL e limite elevado apenas para testes.

Esta entrega cobre o fluxo de PDF, modelos e assinatura. Ainda não reproduz todo o produto DocuSeal: seleção múltipla, fórmulas e condições entre campos, edição rica de conteúdo textual e equivalência de todos os recursos comerciais permanecem fora desta implementação.
