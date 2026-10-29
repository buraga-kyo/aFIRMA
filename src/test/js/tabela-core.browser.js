(async () => {
  const resultados = [];
  const assert = (condicao, mensagem) => { if (!condicao) throw new Error(mensagem); };
  const destino = () => { const div = document.createElement('div'); document.body.append(div); return div; };
  const config = extras => ({ elemento: destino(), colunas: [{ id: 'nome', nome: 'Nome' }, { id: 'valor', nome: 'Valor', tipo: 'numero' }], ...extras });
  async function testar(nome, fn) { try { await fn(); resultados.push({ nome, ok: true }); } catch (erro) { resultados.push({ nome, ok: false, erro: erro.stack }); } }

  await testar('Tabela mínima: montagem, texto seguro e recursos ausentes', () => {
    const t = new FirmaTabela(config({ recursos: false, dados: [{ nome: '<b>A, B</b>', valor: 0 }] }));
    assert(t.raiz.querySelectorAll('thead th').length === 2, 'Ações/seleção indevidas');
    assert(t.raiz.querySelectorAll('tbody td').length === 2, 'Células incorretas');
    assert(!t.raiz.querySelector('input,button,.alca-coluna,.lista-rodape,.lista-toolbar'), 'Recursos desativados ainda renderizados');
    assert(t.raiz.querySelector('td').textContent === '<b>A, B</b>' && !t.raiz.querySelector('td b,td a'), 'Renderização genérica alterou o valor');
    t.destruir();
  });
  await testar('Colunas: largura inicial, obrigatória, oculta e permissões individuais', () => {
    const t = new FirmaTabela(config({ colunas: [
      { id: 'nome', header: 'Cliente', largura: 310, ocultavel: false, filtrosHeader: false, ordenacao: false, redimensionamento: false },
      { id: 'valor', nome: 'Valor', tipo: 'numero', visivel: false }
    ], dados: [{ nome: 'A', valor: 2 }] }));
    assert(t.raiz.querySelector('thead th').textContent === 'Cliente', 'Alias header não funciona');
    assert(!t.raiz.querySelector('thead button,.alca-coluna'), 'Permissão por coluna ignorada');
    assert(t.raiz.querySelector('col').style.width === '310px', 'Largura ignorada');
    t.definirColunas(['valor']); assert(t.getEstado().visiveis.includes('nome'), 'Coluna obrigatória foi ocultada');
    let recusou = false; try { t.filtrar('nome', 'A'); } catch { recusou = true; } assert(recusou, 'Filtro proibido aceito');
    t.destruir();
  });
  await testar('Filtros numéricos, datas por tipo, ordenação e paginação', () => {
    const t = new FirmaTabela(config({ tamanhoPagina: 1, colunas: [
      { id: 'valor', tipo: 'numero' }, { id: 'criadoEm', tipo: 'data' }
    ], dados: [{ valor: 10, criadoEm: '2026-10-29T18:00:00Z' }, { valor: 2, criadoEm: '2026-10-28T18:00:00Z' }, { valor: 0, criadoEm: '2026-10-29T22:00:00Z' }] }));
    t.ordenar('valor'); assert(t.dados()[0].valor === 0, 'Ordenação numérica incorreta');
    t.irParaPagina(2); assert(t.raiz.querySelector('tbody td').textContent === '2', 'Página incorreta');
    t.filtrar('valor', { from: 0, to: 0 }); assert(t.dados().length === 1 && t.dados()[0].valor === 0, 'Faixa zero ignorada');
    t.limparFiltros(); t.filtrar('criadoEm', { from: '2026-10-29', to: '2026-10-29' }); assert(t.dados().length === 2, 'Data por tipo ou último dia incorreto');
    t.destruir();
  });
  await testar('Seleção, chave customizada e ações são independentes', () => {
    const t = new FirmaTabela(config({ recursos: { selecao: true }, chaveLinha: item => item.codigo, dados: [{ codigo: 'a', nome: 'A' }, { codigo: 'b', nome: 'B' }] }));
    const checks = t.raiz.querySelectorAll('tbody input'); checks[0].click();
    assert(t.obterSelecionados()[0] === 'a', 'Chave customizada ignorada');
    assert(t.raiz.querySelector('thead input').indeterminate, 'Seleção parcial sem indeterminate');
    t.definirDados([{ codigo: 'b', nome: 'B' }]); assert(t.obterSelecionados().length === 0, 'Seleção órfã mantida');
    assert(t.raiz.querySelectorAll('thead th').length === 3, 'Coluna ações vazia criada'); t.destruir();
    const a = new FirmaTabela(config({ recursos: { acoes: true }, renderAcoes: (_, td) => td.append('ação'), dados: [{ nome: 'A' }] }));
    assert(a.raiz.querySelector('tbody td').textContent === 'ação' && !a.raiz.querySelector('input[type=checkbox]'), 'Ações dependem de seleção'); a.destruir();
  });
  await testar('Menus: alinhamento, toggle, fora, dentro, Escape e isolamento entre instâncias', () => {
    const a = new FirmaTabela(config()), b = new FirmaTabela(config());
    const ab = a.no('colunas-botao'), am = a.no('menu-colunas'); ab.click();
    const ar = ab.getBoundingClientRect(), mr = am.getBoundingClientRect();
    assert(!am.hidden && Math.abs(mr.top - ar.bottom - 6) < 1, `Menu desalinhado: ${JSON.stringify({hidden:am.hidden,top:mr.top,bottom:ar.bottom,style:am.getAttribute('style')})}`);
    ab.click(); assert(am.hidden && ab.getAttribute('aria-expanded') === 'false', 'Toggle não fecha');
    ab.click(); am.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true })); assert(!am.hidden, 'Clicar dentro fechou');
    document.body.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true })); assert(am.hidden, 'Clicar fora não fecha');
    ab.click(); b.no('colunas-botao').click(); assert(!am.hidden, 'Abrir B alterou A sem interação externa');
    const check = am.querySelector('input'); check.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    assert(am.hidden && !b.no('menu-colunas').hidden, 'Escape afetou outra instância');
    a.destruir(); assert(!b.no('menu-colunas').hidden, 'Destruir A afetou B'); b.destruir();
  });
  await testar('Reordenação funciona sem seletor de colunas', () => {
    const t = new FirmaTabela(config({ recursos: { selecionarColunas: false, reordenacao: true } }));
    t.raiz.querySelector('thead button').click();
    [...t.menuHeader.menu.querySelectorAll('button')].find(b => b.textContent === 'Mover para o fim').click();
    assert(t.estado.visiveis[1] === 'nome', 'Reordenar depende do seletor'); t.destruir();
  });
  await testar('Persistência isolada e larguras por coluna', () => {
    const storageId = 'teste-tabela-' + Date.now(); const el = destino();
    const t = new FirmaTabela(config({ elemento: el, storageId, recursos: { persistencia: true } }));
    t.definirColunas(['valor']); t.destruir();
    const outra = new FirmaTabela(config({ elemento: el, storageId, recursos: { persistencia: true } }));
    assert(outra.estado.visiveis.join() === 'valor', 'Layout não restaurado'); outra.destruir();
    ['layout', 'larguras', 'filtros'].forEach(s => localStorage.removeItem(`${storageId}-${s}`));
  });
  await testar('Servidor: total remoto, página, filtro, ordenação e ausência de segunda paginação', async () => {
    const consultas = [], itens = Array.from({ length: 12 }, (_, i) => ({ id: i, nome: 'N' + i, valor: i }));
    const t = new FirmaTabela(config({ modo: 'servidor', tamanhoPagina: 5, carregarDados: async q => {
      consultas.push(q); return { itens: itens.slice((q.pagina - 1) * q.tamanho, q.pagina * q.tamanho), total: 12 };
    } }));
    assert(await t.carregar(), 'Carregamento falhou'); await t.irParaPagina(2);
    assert(consultas.at(-1).pagina === 2 && t.raiz.querySelectorAll('tbody tr').length === 5 && t.dados()[0].id === 5, 'Página remota cortada novamente');
    await t.filtrar('nome', 'N'); assert(consultas.at(-1).pagina === 1 && consultas.at(-1).filtros.nome === 'N', 'Filtro não enviado');
    await t.ordenar('valor', 'desc'); assert(consultas.at(-1).ordem.dir === 'desc', 'Ordenação não enviada'); t.destruir();
  });
  await testar('Requisições concorrentes: resposta antiga não sobrescreve a nova', async () => {
    const resolver = []; const t = new FirmaTabela(config({ carregarDados: () => new Promise(resolve => resolver.push(resolve)) }));
    const primeira = t.carregar(), segunda = t.carregar(); resolver[1]([{ nome: 'nova' }]); await segunda; resolver[0]([{ nome: 'velha' }]); await primeira;
    assert(t.dados()[0].nome === 'nova', 'Resposta antiga sobrescreveu dados'); t.destruir();
  });
  await testar('Headers opcionais, endpoint com query e tratamento de erro', async () => {
    const fetchOriginal = window.fetch; const chamadas = [];
    window.fetch = async (url, opcoes) => { chamadas.push({ url, opcoes }); return new Response(JSON.stringify({ itens: [{ nome: 'A' }], total: 1 })); };
    const t = new FirmaTabela(config({ modo: 'servidor', endpoint: '/clientes?ativo=1', headers: { 'X-Teste': 'ok' } }));
    try {
      assert(await t.carregar(), 'API falhou'); assert(chamadas[0].url.startsWith('/clientes?ativo=1&pagina='), 'Query inválida');
      assert(chamadas[0].opcoes.headers.get('X-Teste') === 'ok', 'Headers ignorados');
      window.fetch = async () => new Response('erro', { status: 500 }); assert(await t.carregar() === false && t.no('estado').textContent.includes('500'), 'Erro silencioso');
    } finally { window.fetch = fetchOriginal; t.destruir(); }
  });
  await testar('Edição só confirma depois de salvar; falhas mantêm o valor original', async () => {
    let falhar = true; const item = { nome: 'Antes' }; const t = new FirmaTabela(config({ recursos: { edicao: true }, salvar: async () => { if (falhar) throw new Error('Falha de gravação'); }, dados: [item] }));
    assert(await t.salvar(item, { nome: 'Depois' }) === false && item.nome === 'Antes', 'Mutação antes de confirmar a gravação');
    falhar = false; assert(await t.salvar(item, { nome: 'Depois' }) && item.nome === 'Depois', 'Gravação não aplicada'); t.destruir();
  });
  await testar('IA: endpoint absoluto, preview, validação e aplicação', async () => {
    const fetchOriginal = window.fetch; let url;
    window.fetch = async u => { url = u; return new Response(JSON.stringify({ steps: [{ op: 'column-filter', column: 'nome', type: 'contains', value: 'Ana' }] })); };
    const t = new FirmaTabela(config({ endpoint: '/clientes', buscaEndpoint: '/ia/plano', recursos: { buscaIA: true }, dados: [{ nome: 'Ana' }, { nome: 'Bia' }] }));
    try {
      await t.buscarIA('Ana'); assert(url === '/ia/plano' && t.dados().length === 2, 'Endpoint incorreto ou aplicação sem preview');
      [...t.no('plano-busca').querySelectorAll('button')].find(b => b.textContent === 'Aplicar').click(); assert(t.dados().length === 1, 'Plano não aplicado');
    } finally { window.fetch = fetchOriginal; t.destruir(); }
  });
  await testar('Destruição: requisição cancelada, overlays removidos e remontagem', async () => {
    const raiz = destino(); let sinal;
    const t = new FirmaTabela(config({ elemento: raiz, carregarDados: q => { sinal = q.signal; return new Promise(() => {}); } }));
    t.raiz.querySelector('thead button').click(); const menu = t.menuHeader.menu;
    t.carregar(); t.destruir(); assert(sinal.aborted && !menu.isConnected && !raiz.children.length, 'Recursos não liberados');
    const nova = new FirmaTabela(config({ elemento: raiz })); assert(nova.raiz.querySelector('table'), 'Remontagem falhou'); nova.destruir();
  });
  const saida = document.createElement('pre'); saida.id = 'resultado-testes'; saida.textContent = JSON.stringify(resultados); document.body.append(saida);
})();
