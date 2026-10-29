# A cara da FIRMA

A FIRMA tem verde escuro, limão, terra e laranja. A ideia é manter essa cara quando entrar tela nova, sem cada sistema parecer que veio de um lugar diferente. As cores já estão no CSS; use as variáveis que existem ali.

## As cores

| Variável CSS | Cor | Onde usar |
| --- | --- | --- |
| `--background` | `#0E2117` | Fundo da página e dos controles |
| `--card` | `#173225` | Painéis |
| `--secondary` | `#3B2A1A` | Faixas e detalhes em terra |
| `--border` | `#2E5A3F` | Bordas e divisórias |
| `--foreground` | `#F2F7EC` | Texto principal |
| `--muted-foreground` | `#A9C2AC` | Descrições e texto secundário |
| `--primary` | `#C9FF3D` | Destaques, foco e ações |
| `--accent` | `#E8792B` | Laranja das sombras e erros |

Veja `src/main/resources/static/css/assinatura.css`. As sombras também já estão lá: `--hard-shadow` e `--electric-shadow`. São sólidas e deslocadas. A elétrica usa o laranja.

## A logo e as letras

A logo é **aFIRMA.**, escrita em `Archivo Black`, peso 400 e espaçamento `-.075em`. Use a mesma fonte na marca da página inicial e na aba do trator. Para os textos e controles, use `Inter`. As fontes vêm do Google Fonts e têm `sans-serif` como alternativa.

Os títulos podem ser grandes e em caixa alta. O limão destaca o que precisa chamar atenção; os textos de apoio usam `--muted-foreground`.

## Cada coisa no seu lugar

A página inicial mantém o header dela. As telas que ainda não têm trator próprio também mantêm o header que já tinham.

No trator de assinaturas, a navegação fica na mesma barra de **+ Assinatura**, Exportar, Importar e Colunas. A ordem é **aFIRMA. | AssineAI | T@klATIVE | ADVai**. A aba aFIRMA. abre o perfil da empresa. Ali não tem botão para voltar à página inicial.

As abas seguem as curvas e a sobreposição de `tabs_tabela.png`, mas as cores são as da FIRMA. O desenho da referência serve para a forma, não para copiar a paleta. A barra acomoda os controles conforme a largura da tela, sem scroll horizontal. A tabela pode rolar quando precisar mostrar mais colunas.

Painéis e campos usam bordas verdes; as abas têm seu próprio contorno curvo. Botões principais usam limão com texto verde escuro. Erros usam laranja e precisam dizer o que aconteceu. O foco do teclado tem que continuar visível.

No rodapé do trator ficam as credenciais e a origem dos registros. Conectar e Credenciais de teste ficam à direita. A paginação usa **ÁREA 1 de 1 do TERRENO → 10 Sementes**, e o controle de largura fica no canto direito da barra inferior da tabela.

## No celular também tem que funcionar

Até 760px, reorganize campos e botões para caber na tela. Os detalhes decorativos podem sair, mas os controles precisam continuar acessíveis. Use `prefers-reduced-motion: reduce` para respeitar quem desativou animações.

Antes de inventar outro componente, veja se o que já existe resolve. Para tabelas, comece pelo [guia da FirmaTabela](docs/tabela.md). Os nomes e os botões de cada sistema ficam na página que usa a classe.
