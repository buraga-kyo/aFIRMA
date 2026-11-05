(async () => {
  const resultados = [];
  const assert = (condicao, mensagem) => {
    if (!condicao) throw new Error(mensagem);
  };
  const destino = () => {
    const div = document.createElement('div');
    document.body.append(div);
    return div;
  };
  const configuracao = extras => ({
    elemento: destino(),
    colunas: [{
      id: 'nome',
      nome: 'Nome'
    }, {
      id: 'valor',
      nome: 'Valor',
      tipo: 'numero'
    }],
    ...extras
  });
  async function testar(nome, executarAcao) {
    try {
      await executarAcao();
      resultados.push({
        nome,
        ok: true
      });
    } catch (erro) {
      resultados.push({
        nome,
        ok: false,
        erro: erro.stack
      });
    }
  }
  await testar('Tabela mínima: montagem, texto seguro e recursos ausentes', () => {
    const itemAtual = new FirmaTabela(configuracao({
      recursos: false,
      dados: [{
        nome: '<b>A, B</b>',
        valor: 0
      }]
    }));
    assert(itemAtual.raiz.querySelectorAll('thead th').length === 2, 'Ações/seleção indevidas');
    assert(itemAtual.raiz.querySelectorAll('tbody td').length === 2, 'Células incorretas');
    assert(!itemAtual.raiz.querySelector('input,button,.alca-coluna,.lista-rodape,.lista-toolbar'), 'Recursos desativados ainda renderizados');
    assert(itemAtual.raiz.querySelector('td').textContent === '<b>A, B</b>' && !itemAtual.raiz.querySelector('td b,td a'), 'Renderização genérica alterou o valor');
    itemAtual.destruir();
  });
  await testar('Colunas: largura inicial, obrigatória, oculta e permissões individuais', () => {
    const itemAtual = new FirmaTabela(configuracao({
      colunas: [{
        id: 'nome',
        header: 'Cliente',
        largura: 310,
        ocultavel: false,
        filtrosHeader: false,
        ordenacao: false,
        redimensionamento: false
      }, {
        id: 'valor',
        nome: 'Valor',
        tipo: 'numero',
        visivel: false
      }],
      dados: [{
        nome: 'A',
        valor: 2
      }]
    }));
    assert(itemAtual.raiz.querySelector('thead th').textContent === 'Cliente', 'Alias header não funciona');
    assert(!itemAtual.raiz.querySelector('thead button,.alca-coluna'), 'Permissão por coluna ignorada');
    assert(itemAtual.raiz.querySelector('col').style.width === '310px', 'Largura ignorada');
    itemAtual.definirColunas(['valor']);
    assert(itemAtual.getEstado().visiveis.includes('nome'), 'Coluna obrigatória foi ocultada');
    let recusou = false;
    try {
      itemAtual.filtrar('nome', 'A');
    } catch {
      recusou = true;
    }
    assert(recusou, 'Filtro proibido aceito');
    itemAtual.destruir();
  });
  await testar('Filtros numéricos, datas por tipo, ordenação e paginação', () => {
    const itemAtual = new FirmaTabela(configuracao({
      tamanhoPagina: 1,
      colunas: [{
        id: 'valor',
        tipo: 'numero'
      }, {
        id: 'criadoEm',
        tipo: 'data'
      }],
      dados: [{
        valor: 10,
        criadoEm: '2026-10-29T18:00:00Z'
      }, {
        valor: 2,
        criadoEm: '2026-10-28T18:00:00Z'
      }, {
        valor: 0,
        criadoEm: '2026-10-29T22:00:00Z'
      }]
    }));
    itemAtual.ordenar('valor');
    assert(itemAtual.dados()[0].valor === 0, 'Ordenação numérica incorreta');
    itemAtual.irParaPagina(2);
    assert(itemAtual.raiz.querySelector('tbody td').textContent === '2', 'Página incorreta');
    itemAtual.filtrar('valor', {
      from: 0,
      to: 0
    });
    assert(itemAtual.dados().length === 1 && itemAtual.dados()[0].valor === 0, 'Faixa zero ignorada');
    itemAtual.limparFiltros();
    itemAtual.filtrar('criadoEm', {
      from: '2026-10-29',
      to: '2026-10-29'
    });
    assert(itemAtual.dados().length === 2, 'Data por tipo ou último dia incorreto');
    itemAtual.destruir();
  });
  await testar('Seleção, chave customizada e ações são independentes', () => {
    const itemAtual = new FirmaTabela(configuracao({
      recursos: {
        selecao: true
      },
      chaveLinha: item => item.codigo,
      dados: [{
        codigo: 'a',
        nome: 'A'
      }, {
        codigo: 'b',
        nome: 'B'
      }]
    }));
    const checks = itemAtual.raiz.querySelectorAll('tbody input');
    checks[0].click();
    assert(itemAtual.obterSelecionados()[0] === 'a', 'Chave customizada ignorada');
    assert(itemAtual.raiz.querySelector('thead input').indeterminate, 'Seleção parcial sem indeterminate');
    itemAtual.definirDados([{
      codigo: 'b',
      nome: 'B'
    }]);
    assert(itemAtual.obterSelecionados().length === 0, 'Seleção órfã mantida');
    assert(itemAtual.raiz.querySelectorAll('thead th').length === 3, 'Coluna ações vazia criada');
    itemAtual.destruir();
    const registro = new FirmaTabela(configuracao({
      recursos: {
        acoes: true
      },
      renderAcoes: (_, celula) => celula.append('ação'),
      dados: [{
        nome: 'A'
      }]
    }));
    assert(registro.raiz.querySelector('tbody td').textContent === 'ação' && !registro.raiz.querySelector('input[type=checkbox]'), 'Ações dependem de seleção');
    registro.destruir();
  });
  await testar('Menus: alinhamento, toggle, fora, dentro, Escape e isolamento entre instâncias', () => {
    const registro = new FirmaTabela(configuracao()),
      botao = new FirmaTabela(configuracao());
    const ab = registro.no('colunas-botao'),
      am = registro.no('menu-colunas');
    ab.click();
    const botaoArquivo = ab.getBoundingClientRect(),
      mr = am.getBoundingClientRect();
    assert(!am.hidden && Math.abs(mr.top - botaoArquivo.bottom - 6) < 1, `Menu desalinhado: ${JSON.stringify({
      hidden: am.hidden,
      top: mr.top,
      bottom: botaoArquivo.bottom,
      style: am.getAttribute('style')
    })}`);
    ab.click();
    assert(am.hidden && ab.getAttribute('aria-expanded') === 'false', 'Toggle não fecha');
    ab.click();
    am.dispatchEvent(new PointerEvent('pointerdown', {
      bubbles: true
    }));
    assert(!am.hidden, 'Clicar dentro fechou');
    document.body.dispatchEvent(new PointerEvent('pointerdown', {
      bubbles: true
    }));
    assert(am.hidden, 'Clicar fora não fecha');
    ab.click();
    botao.no('colunas-botao').click();
    assert(!am.hidden, 'Abrir B alterou A sem interação externa');
    const marcador = am.querySelector('input');
    marcador.dispatchEvent(new KeyboardEvent('keydown', {
      key: 'Escape',
      bubbles: true
    }));
    assert(am.hidden && !botao.no('menu-colunas').hidden, 'Escape afetou outra instância');
    registro.destruir();
    assert(!botao.no('menu-colunas').hidden, 'Destruir A afetou B');
    botao.destruir();
  });
  await testar('Reordenação funciona sem seletor de colunas', () => {
    const itemAtual = new FirmaTabela(configuracao({
      recursos: {
        selecionarColunas: false,
        reordenacao: true
      }
    }));
    itemAtual.raiz.querySelector('thead button').click();
    [...itemAtual.menuHeader.menu.querySelectorAll('button')].find(botao => botao.textContent === 'Mover para o fim').click();
    assert(itemAtual.estado.visiveis[1] === 'nome', 'Reordenar depende do seletor');
    itemAtual.destruir();
  });
  await testar('Persistência isolada e larguras por coluna', () => {
    const storageId = 'teste-tabela-' + Date.now();
    const elementoAtual = destino();
    const itemAtual = new FirmaTabela(configuracao({
      elemento: elementoAtual,
      storageId,
      recursos: {
        persistencia: true
      }
    }));
    itemAtual.definirColunas(['valor']);
    itemAtual.destruir();
    const outra = new FirmaTabela(configuracao({
      elemento: elementoAtual,
      storageId,
      recursos: {
        persistencia: true
      }
    }));
    assert(outra.estado.visiveis.join() === 'valor', 'Layout não restaurado');
    outra.destruir();
    ['layout', 'larguras', 'filtros'].forEach(itemDoRegistro => localStorage.removeItem(`${storageId}-${itemDoRegistro}`));
  });
  await testar('Servidor: total remoto, página, filtro, ordenação e ausência de segunda paginação', async () => {
    const consultas = [],
      itens = Array.from({
        length: 12
      }, (_, indice) => ({
        id: indice,
        nome: 'N' + indice,
        valor: indice
      }));
    const itemAtual = new FirmaTabela(configuracao({
      modo: 'servidor',
      tamanhoPagina: 5,
      carregarDados: async consulta => {
        consultas.push(consulta);
        return {
          itens: itens.slice((consulta.pagina - 1) * consulta.tamanho, consulta.pagina * consulta.tamanho),
          total: 12
        };
      }
    }));
    assert(await itemAtual.carregar(), 'Carregamento falhou');
    await itemAtual.irParaPagina(2);
    assert(consultas.at(-1).pagina === 2 && itemAtual.raiz.querySelectorAll('tbody tr').length === 5 && itemAtual.dados()[0].id === 5, 'Página remota cortada novamente');
    await itemAtual.filtrar('nome', 'N');
    assert(consultas.at(-1).pagina === 1 && consultas.at(-1).filtros.nome === 'N', 'Filtro não enviado');
    await itemAtual.ordenar('valor', 'desc');
    assert(consultas.at(-1).ordem.dir === 'desc', 'Ordenação não enviada');
    itemAtual.destruir();
  });
  await testar('Requisições concorrentes: resposta antiga não sobrescreve a nova', async () => {
    const resolver = [];
    const itemAtual = new FirmaTabela(configuracao({
      carregarDados: () => new Promise(concluir => resolver.push(concluir))
    }));
    const primeira = itemAtual.carregar(),
      segunda = itemAtual.carregar();
    resolver[1]([{
      nome: 'nova'
    }]);
    await segunda;
    resolver[0]([{
      nome: 'velha'
    }]);
    await primeira;
    assert(itemAtual.dados()[0].nome === 'nova', 'Resposta antiga sobrescreveu dados');
    itemAtual.destruir();
  });
  await testar('Headers opcionais, endpoint com query e tratamento de erro', async () => {
    const fetchOriginal = window.fetch;
    const chamadas = [];
    window.fetch = async (endereco, opcoes) => {
      chamadas.push({
        url: endereco,
        opcoes
      });
      return new Response(JSON.stringify({
        itens: [{
          nome: 'A'
        }],
        total: 1
      }));
    };
    const itemAtual = new FirmaTabela(configuracao({
      modo: 'servidor',
      endpoint: '/clientes?ativo=1',
      headers: {
        'X-Teste': 'ok'
      }
    }));
    try {
      assert(await itemAtual.carregar(), 'API falhou');
      assert(chamadas[0].url.startsWith('/clientes?ativo=1&pagina='), 'Query inválida');
      assert(chamadas[0].opcoes.headers.get('X-Teste') === 'ok', 'Headers ignorados');
      window.fetch = async () => new Response('erro', {
        status: 500
      });
      assert((await itemAtual.carregar()) === false && itemAtual.no('estado').textContent.includes('500'), 'Erro silencioso');
    } finally {
      window.fetch = fetchOriginal;
      itemAtual.destruir();
    }
  });
  await testar('Edição só confirma depois de salvar; falhas mantêm o valor original', async () => {
    let falhar = true;
    const item = {
      nome: 'Antes'
    };
    const itemAtual = new FirmaTabela(configuracao({
      recursos: {
        edicao: true
      },
      salvar: async () => {
        if (falhar) throw new Error('Falha de gravação');
      },
      dados: [item]
    }));
    assert((await itemAtual.salvar(item, {
      nome: 'Depois'
    })) === false && item.nome === 'Antes', 'Mutação antes de confirmar a gravação');
    falhar = false;
    assert((await itemAtual.salvar(item, {
      nome: 'Depois'
    })) && item.nome === 'Depois', 'Gravação não aplicada');
    itemAtual.destruir();
  });
  await testar('IA: endpoint absoluto, preview, validação e aplicação', async () => {
    const fetchOriginal = window.fetch;
    let endereco;
    window.fetch = async enderecoAtual => {
      endereco = enderecoAtual;
      return new Response(JSON.stringify({
        steps: [{
          op: 'column-filter',
          column: 'nome',
          type: 'contains',
          value: 'Ana'
        }]
      }));
    };
    const itemAtual = new FirmaTabela(configuracao({
      endpoint: '/clientes',
      buscaEndpoint: '/ia/plano',
      recursos: {
        buscaIA: true
      },
      dados: [{
        nome: 'Ana'
      }, {
        nome: 'Bia'
      }]
    }));
    try {
      await itemAtual.buscarIA('Ana');
      assert(endereco === '/ia/plano' && itemAtual.dados().length === 2, 'Endpoint incorreto ou aplicação sem preview');
      [...itemAtual.no('plano-busca').querySelectorAll('button')].find(botao => botao.textContent === 'Aplicar').click();
      assert(itemAtual.dados().length === 1, 'Plano não aplicado');
    } finally {
      window.fetch = fetchOriginal;
      itemAtual.destruir();
    }
  });
  await testar('Limpar dados em desconexão cancela respostas pendentes e apaga seleção e filtros', async () => {
    let resolver, sinal;
    const itemAtual = new FirmaTabela(configuracao({
      modo: 'servidor',
      recursos: {
        selecao: true
      },
      carregarDados: consulta => {
        sinal = consulta.signal;
        return new Promise(registroAtual => resolver = registroAtual);
      }
    }));
    const carregamento = itemAtual.carregar();
    itemAtual.estado.selecionados.add(1);
    itemAtual.estado.filtros.nome = 'Antigo';
    itemAtual.limparDados();
    assert(sinal.aborted && !itemAtual.estado.carregando && !itemAtual.estado.total && !itemAtual.estado.todos.length && !itemAtual.obterSelecionados().length && !Object.keys(itemAtual.estado.filtros).length, 'Desconexão não limpou estado');
    resolver({
      itens: [{
        id: 1,
        nome: 'Resposta antiga'
      }],
      total: 1
    });
    await carregamento;
    assert(!itemAtual.dados().length, 'Resposta antiga reapareceu após desconexão');
    itemAtual.destruir();
  });
  await testar('Edição longa: X fixo visível e Fechar descartam sem salvar', async () => {
    let gravacoes = 0;
    const item = Object.fromEntries(Array.from({
      length: 24
    }, (_, indice) => ['campo' + indice, 'Valor ' + indice]));
    const itemAtual = new FirmaTabela(configuracao({
      colunas: Object.keys(item).map(id => ({
        id,
        nome: id
      })),
      dados: [item],
      recursos: {
        edicao: true,
        detalhes: true
      },
      salvar: () => {
        gravacoes++;
      }
    }));
    itemAtual.detalhes(item, true);
    let dadosAtual = itemAtual.dialogo;
    const entrada = dadosAtual.querySelector('input');
    entrada.value = 'Alteração não salva';
    dadosAtual.scrollTop = dadosAtual.scrollHeight;
    await new Promise(registroAtual => setTimeout(registroAtual, 20));
    const x = dadosAtual.querySelector('.af-detalhes-fechar'),
      retangulo = x.getBoundingClientRect(),
      caixa = dadosAtual.getBoundingClientRect();
    assert(retangulo.top >= caixa.top && retangulo.bottom <= caixa.bottom, 'X saiu da área visível ao rolar');
    assert(document.elementFromPoint(retangulo.left + retangulo.width / 2, retangulo.top + retangulo.height / 2) === x, 'X encoberto');
    x.click();
    await new Promise(registroAtual => setTimeout(registroAtual, 20));
    assert(!dadosAtual.open && !gravacoes && item.campo0 === 'Valor 0', 'X salvou ou não fechou');
    itemAtual.detalhes(item, true);
    dadosAtual = itemAtual.dialogo;
    [...dadosAtual.querySelectorAll('button')].find(botao => botao.textContent === 'Fechar').click();
    await new Promise(registroAtual => setTimeout(registroAtual, 20));
    assert(!dadosAtual.open && !gravacoes, 'Fechar salvou ou não fechou: ' + JSON.stringify({
      aberto: dadosAtual.open,
      gravacoes,
      referencia: !!itemAtual.dialogo
    }));
    itemAtual.destruir();
  });
  await testar('Destruição: requisição cancelada, overlays removidos e remontagem', async () => {
    const raiz = destino();
    let sinal;
    const itemAtual = new FirmaTabela(configuracao({
      elemento: raiz,
      carregarDados: consulta => {
        sinal = consulta.signal;
        return new Promise(() => {});
      }
    }));
    itemAtual.raiz.querySelector('thead button').click();
    const menu = itemAtual.menuHeader.menu;
    itemAtual.carregar();
    itemAtual.destruir();
    assert(sinal.aborted && !menu.isConnected && !raiz.children.length, 'Recursos não liberados');
    const nova = new FirmaTabela(configuracao({
      elemento: raiz
    }));
    assert(nova.raiz.querySelector('table'), 'Remontagem falhou');
    nova.destruir();
  });
  await testar('Exclusão centralizada: só executa após confirmar; cancelar e Escape preservam registro', async () => {
    let exclusoes = 0;
    const itemAtual = new FirmaTabela(configuracao({
      dados: [{
        id: 1,
        nome: 'Registro de teste'
      }],
      renderAcoes: (_r, celula) => celula.append(FirmaTabela.botaoAcao('remover', 'Excluir registro de teste', () => exclusoes++))
    }));
    const botao = itemAtual.raiz.querySelector('button.destrutivo'),
      esperar = () => new Promise(registroAtual => setTimeout(registroAtual, 100));
    botao.click();
    assert(exclusoes === 0 && itemAtual.confirmacaoExclusao?.open, 'Excluiu antes da confirmação');
    itemAtual.confirmacaoExclusao.querySelector('[value=cancelar]').click();
    await esperar();
    assert(exclusoes === 0 && !itemAtual.confirmacaoExclusao, 'Cancelar executou exclusão');
    botao.click();
    itemAtual.confirmacaoExclusao.dispatchEvent(new Event('cancel', {
      cancelable: true
    }));
    await esperar();
    assert(exclusoes === 0, 'Escape executou exclusão');
    botao.click();
    const dadosAtual = itemAtual.confirmacaoExclusao;
    dadosAtual.querySelector('[value=excluir]').click();
    await esperar();
    assert(exclusoes === 1, 'Confirmação não executou exatamente uma vez: ' + JSON.stringify({
      exclusoes,
      conectado: botao.isConnected,
      desabilitado: botao.disabled,
      aberto: !!itemAtual.confirmacaoExclusao,
      retorno: dadosAtual.returnValue
    }));
    botao.click();
    itemAtual.destruir();
    await esperar();
    assert(exclusoes === 1 && !document.querySelector('.af-confirmacao-exclusao'), 'Destruição deixou confirmação pendente');
  });
  const saida = document.createElement('pre');
  saida.id = 'resultado-testes';
  saida.textContent = JSON.stringify(resultados);
  document.body.append(saida);
})();
