# Design do sistema aFIRMA

Este documento define a identidade visual de todo o sistema. A tela de assinatura é a implementação atual e serve de referência concreta para aplicar a mesma linguagem às próximas telas. Identidade: verde profundo, limão elétrico, terra e laranja, com tipografia forte e formas retas.

## Paleta e tokens atuais

| Token CSS | Valor | Função no sistema |
| --- | --- | --- |
| `--background` | `#0E2117` | Fundo de páginas e controles |
| `--card` | `#173225` | Painéis e superfícies elevadas |
| `--secondary` | `#3B2A1A` | Faixas, sombras de painéis e fundos de aviso |
| `--border` | `#2E5A3F` | Divisores, controles e contornos |
| `--foreground` | `#F2F7EC` | Títulos e texto principal |
| `--muted-foreground` | `#A9C2AC` | Descrições, rótulos e informações secundárias |
| `--primary` | `#C9FF3D` | Destaques, números, foco e ações |
| `--accent` | `#E8792B` | Sombras elétricas e indicação de erro |

Os nomes e valores acima já existem em `src/main/resources/static/css/assinatura.css`. O CSS também define `--hard-shadow: 4px 4px 0 #0E2117` e `--electric-shadow: 4px 4px 0 #E8792B`. Use sombras sólidas deslocadas, sem degradê. A sombra elétrica destaca ações; painéis podem usar a cor secundária.

## Tipografia e forma

- `Archivo Black` em títulos, marca e números de etapas. `Inter` no corpo, rótulos e controles. Fontes carregadas pelo Google Fonts, com `sans-serif` como alternativa.
- Títulos de destaque em caixa alta, grandes e compactos. Use limão para realçar palavras ou trechos importantes.
- Rótulos e selos usam tamanho pequeno, peso alto e espaçamento entre letras. Descrições usam texto suave.
- Painéis, campos e botões têm cantos retos. Divisores e contornos usam 2px. Áreas de envio de arquivo podem usar borda tracejada.
- Ações principais têm fundo limão, texto verde profundo, borda de 2px e sombra laranja deslocada. Ao passar o ponteiro, ação e sombra se deslocam.
- Foco visível usa contorno limão de 3px, afastado 4px. Evitar depender só de cor para identificar controles.

## Componentes e hierarquia

- Cabeçalho com marca `aFIRMA.` e identificação breve da área atual. A marca usa ponto limão.
- Abertura de página com sobretítulo, título forte e explicação curta. Elementos decorativos podem usar contorno limão e sombra laranja, como o carimbo da tela de assinatura.
- Conteúdo principal em painéis verde mata, separados do fundo profundo por contorno e sombra. Faixas terra podem identificar seções.
- Processos com várias etapas usam números limão e títulos claros. Campos têm rótulos legíveis, fundo profundo e borda verde. Agrupe campos relacionados.
- Ação principal deve ser visualmente dominante. Resultados positivos usam borda limão e mensagem clara. Erros usam borda laranja, fundo terra e texto claro.
- Rodapé discreto em texto suave. A assinatura verbal existente é “RAIZ NO CAMPO. OLHAR NO FUTURO.”

## Responsividade e movimento

- Use largura máxima de 1280px e conteúdo centralizado. No desktop, apresentações e grupos de campos podem ocupar colunas.
- Até 760px, elementos decorativos e selos auxiliares podem sumir. Campos passam para uma coluna, ações ocupam toda a largura e espaçamentos laterais caem para 20px.
- `prefers-reduced-motion: reduce` desativa transições e rolagem suave.

## Regra de manutenção

Ao criar outra tela, aplique esta paleta, tipografia, hierarquia e regras de componentes. Confira `src/main/resources/templates/assinatura.html` e `src/main/resources/static/css/assinatura.css` para ver a implementação atual. Mantenha os nomes dos tokens consistentes entre telas.
