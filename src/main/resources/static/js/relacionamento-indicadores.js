(() => {
  const porId = id => document.getElementById(id);
  // Rascunho visual. A escolha das métricas fica para depois.
  const nomesSoltos = ['Sacadas de vento', 'Parafusos de domingo', 'Talvez por hectare',
    'Poeira acumulada', 'Café em trânsito', 'Porteiras invisíveis', 'Coisas quase prontas', 'Saldo de assobio'];
  const nomesDosGraficos = ['O tanto do trem', 'Mapa do quase', 'Curva do vai saber'];
  const nomesDasLinhas = ['Balaiada A', 'Lote do meio', 'Depois a gente vê'];

  // const inicio = porId('indicadores-inicio').value;
  // const fim = porId('indicadores-fim').value;
  // const recorte = porId('indicadores-setor').value;
  // Falta decidir se esses campos vão existir mesmo.

  // async function consultarNumeros() {
  //   const resposta = await fetch('/api/firma/indicadores', { headers: cabecalhos });
  //   return resposta.json();
  // }
  // A consulta fica desligada enquanto não houver definição das métricas.

  function embaralhar() {
    porId('indicadores-cards').replaceChildren();
    for (const nome of nomesSoltos) {
      const cartao = document.createElement('article'),
        rotulo = document.createElement('small'),
        numero = document.createElement('strong');
      rotulo.textContent = nome;
      numero.textContent = Math.floor(Math.random() * 700).toLocaleString('pt-BR');
      cartao.append(rotulo, numero);
      porId('indicadores-cards').append(cartao);
    }

    // const variacao = numeroAtual - numeroAnterior;
    // const unidade = 'a definir';
    // cartao.append(variacao, unidade);

    porId('indicadores-graficos').replaceChildren();
    for (const nome of nomesDosGraficos) {
      const secao = document.createElement('section'),
        titulo = document.createElement('h2');
      titulo.textContent = nome;
      secao.append(titulo);
      for (const nomeDaLinha of nomesDasLinhas) {
        const linha = document.createElement('div'),
          rotulo = document.createElement('span'),
          barra = document.createElement('meter'),
          numero = document.createElement('strong');
        rotulo.textContent = nomeDaLinha;
        barra.max = 100;
        barra.value = Math.floor(Math.random() * 100);
        barra.setAttribute('aria-label', nome + ': ' + nomeDaLinha);
        numero.textContent = barra.value;
        linha.append(rotulo, barra, numero);
        secao.append(linha);
      }
      porId('indicadores-graficos').append(secao);
    }
    // desenharMeta(metaQueAindaNaoExiste);
    // compararComPeriodoAnterior();
    // salvarPreferenciasDoPainel();
  }

  porId('indicadores-filtros').onsubmit = evento => {
    evento.preventDefault();
    embaralhar();
  };
  embaralhar();
})();
