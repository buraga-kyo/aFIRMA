(async () => {
  const resultados = [];
  const assert = (valor, mensagem) => { if (!valor) throw new Error(mensagem); };
  const esperar = () => new Promise(resolve => setTimeout(resolve, 20));
  const testar = async (nome, fn) => { try { await fn(); resultados.push({ nome, ok: true }); } catch (erro) { resultados.push({ nome, ok: false, erro: erro.stack }); } };
  await testar('Trator real: montagem, abas, fonte da marca e controles da página', () => {
    assert(!window.errosPagina.length, window.errosPagina.join('\n'));
    assert(window.tabelaTeste instanceof window.FirmaTabelaOriginal, 'Instância não criada');
    const raiz = document.getElementById('tabela-montagem');
    assert(raiz.querySelectorAll('.lista-toolbar').length === 1, 'Barras duplicadas');
    assert([...raiz.querySelectorAll('.trator-aba')].map(a => a.getAttribute('href')).join() === '/empresa,/assineai/trator,/chat,/advai', 'Links das abas incorretos');
    assert(raiz.querySelector('.trator-firma').closest('.toolbar-grupo').contains(document.getElementById('importar-dados')), 'Abas separadas dos comandos');
    assert(getComputedStyle(raiz.querySelector('.trator-marca')).fontFamily.includes('Archivo Black'), 'Fonte da marca perdida');
    assert(raiz.querySelector('.af-busca input').placeholder === 'FALAR COM O BURRO DE CARGA...', 'Busca incorreta');
    assert(raiz.querySelector('#tabela-pagina-texto').textContent.includes('ÁREA 1 de 1 do TERRENO'), 'Texto de paginação perdido');
    assert(document.getElementById('alternar-largura'), 'Controle de largura ausente');
  });
  await testar('Trator real: carregar via Conectar, renderização e edição PATCH do título', async () => {
    document.getElementById('lista-chave').value = 'teste'; document.getElementById('lista-token').value = 'teste';
    document.getElementById('lista-conectar').click(); await esperar();
    assert(window.tabelaTeste.dados().length === 1, 'Conectar não carregou dados');
    const raiz = document.getElementById('tabela-montagem');
    assert(raiz.querySelectorAll('thead th').length === 9 && raiz.querySelectorAll('tbody td').length === 9, 'Quantidade de colunas alterada');
    assert(raiz.querySelector('tbody .badge-roca-neon').textContent === 'Ana', 'Renderizador de signatários perdido');
    raiz.querySelector('.btn-editar-linha').click();
    const dialog = document.querySelector('dialog.af-detalhes'); assert(dialog?.open, 'Editar não abre');
    assert(dialog.querySelectorAll('input').length === 1, 'Campos sem suporte ficaram editáveis');
    dialog.querySelector('input').value = 'Contrato atualizado';
    [...dialog.querySelectorAll('button')].find(b => b.textContent === 'Salvar alterações').click(); await esperar();
    assert(window.chamadasAPI.some(c => c.metodo === 'PATCH' && c.url === '/api/assineai/assinaturas/1' && JSON.parse(c.body).titulo === 'Contrato atualizado'), 'PATCH não enviado ao endpoint certo');
    assert(window.tabelaTeste.dados()[0].titulo === 'Contrato atualizado', 'Edição não refletiu na tela');
  });
  await testar('Trator real: menu Colunas, ausência do fundo retangular e largura da página', () => {
    const b = document.getElementById('tabela-colunas-botao'), menu = document.getElementById('tabela-menu-colunas'); b.click();
    assert(!menu.hidden && Math.abs(menu.getBoundingClientRect().top - b.getBoundingClientRect().bottom - 6) < 1, 'Menu deslocado');
    b.click(); assert(menu.hidden, 'Toggle não fecha');
    assert(getComputedStyle(document.getElementById('importar-dados')).backgroundColor === 'rgba(0, 0, 0, 0)', 'Fundo retangular reapareceu');
    assert(document.documentElement.scrollWidth <= innerWidth, 'Rolagem horizontal na página');
    document.getElementById('exportar-dados').click(); assert(document.getElementById('modal-exportacao').open, 'Exportação perdeu o evento');
    document.getElementById('modal-exportacao').close();
  });
  localStorage.removeItem('assineai-chave'); localStorage.removeItem('assineai-token');
  const saida = document.createElement('pre'); saida.id = 'resultado-testes'; saida.textContent = JSON.stringify(resultados); document.body.append(saida);
})();
