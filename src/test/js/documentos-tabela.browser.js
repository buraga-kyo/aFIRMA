(async () => {
  const resultados = [];
  const assert = (valor, mensagem) => {
    if (!valor) throw new Error(mensagem);
  };
  const esperar = () => new Promise(concluir => setTimeout(concluir, 20));
  const testar = async (nome, executarAcao) => {
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
  };
  await testar('Trator real: montagem, abas, fonte da marca e controles da página', () => {
    assert(!window.errosPagina.length, window.errosPagina.join('\n'));
    assert(window.tabelaTeste instanceof window.FirmaTabelaOriginal, 'Instância não criada');
    const raiz = document.getElementById('tabela-montagem');
    assert(raiz.querySelectorAll('.lista-toolbar').length === 1, 'Barras duplicadas');
    assert([...raiz.querySelectorAll('.trator-aba')].map(registro => registro.getAttribute('href')).join() === '/firma,/assineai/trator,/chat,/advai', 'Links das abas incorretos');
    assert(raiz.querySelector('.trator-firma').closest('.toolbar-grupo').contains(document.getElementById('importar-dados')), 'Abas separadas dos comandos');
    assert(getComputedStyle(raiz.querySelector('.trator-marca')).fontFamily.includes('Archivo Black'), 'Fonte da marca perdida');
    assert(raiz.querySelector('.af-busca input').placeholder === 'FALAR COM O BURRO DE CARGA...', 'Busca incorreta');
    const filtro = raiz.querySelector('.trator-burro-fixo');
    assert(raiz.classList.contains('trator-compartilhado') && raiz.querySelector('.trator-faixa-abas'), 'Estrutura compartilhada ausente');
    assert(getComputedStyle(filtro).position === 'absolute', 'Filtro não fica à frente das abas');
    assert(getComputedStyle(filtro.querySelector('.af-busca')).minHeight === '40px', 'Altura do Burro de Carga diferente da base comum');
    assert(raiz.querySelector('.trator-marca').getBoundingClientRect().top < filtro.getBoundingClientRect().top, 'Filtro esconde os topos das abas');
    assert(raiz.querySelector('#tabela-pagina-texto').textContent.includes('ÁREA 1 de 1 do TERRENO'), 'Texto de paginação perdido');
    assert(document.getElementById('alternar-largura'), 'Controle de largura ausente');
  });
  await testar('Trator real: carregar via Conectar, renderização e edição PATCH do título', async () => {
    document.getElementById('lista-chave').value = 'teste';
    document.getElementById('lista-token').value = 'teste';
    document.getElementById('lista-conectar').click();
    await esperar();
    assert(window.tabelaTeste.dados().length === 1, 'Conectar não carregou dados');
    const raiz = document.getElementById('tabela-montagem');
    assert(raiz.querySelectorAll('thead th').length === 9 && raiz.querySelectorAll('tbody td').length === 9, 'Quantidade de colunas alterada');
    assert(raiz.querySelector('tbody .badge-roca-neon').textContent === 'Ana', 'Renderizador de signatários perdido');
    raiz.querySelector('.btn-editar-linha').click();
    const janela = document.querySelector('dialog.af-detalhes');
    assert(janela?.open, 'Editar não abre');
    assert(janela.querySelectorAll('input').length === 1, 'Campos sem suporte ficaram editáveis');
    janela.querySelector('input').value = 'Contrato atualizado';
    [...janela.querySelectorAll('button')].find(botao => botao.textContent === 'Salvar alterações').click();
    await esperar();
    assert(window.chamadasAPI.some(item => item.metodo === 'PATCH' && item.url === '/api/assineai/assinaturas/1' && JSON.parse(item.body).titulo === 'Contrato atualizado'), 'PATCH não enviado ao endpoint certo');
    assert(window.tabelaTeste.dados()[0].titulo === 'Contrato atualizado', 'Edição não refletiu na tela');
  });
  await testar('Trator real: menu Colunas, ausência do fundo retangular e largura da página', () => {
    const botao = document.getElementById('tabela-colunas-botao'),
      menu = document.getElementById('tabela-menu-colunas');
    botao.click();
    assert(!menu.hidden && Math.abs(menu.getBoundingClientRect().top - botao.getBoundingClientRect().bottom - 6) < 1, 'Menu deslocado');
    botao.click();
    assert(menu.hidden, 'Toggle não fecha');
    assert(getComputedStyle(document.getElementById('importar-dados')).backgroundColor === 'rgba(0, 0, 0, 0)', 'Fundo retangular reapareceu');
    assert(document.documentElement.scrollWidth <= innerWidth, 'Rolagem horizontal na página');
    document.getElementById('exportar-dados').click();
    assert(document.getElementById('modal-exportacao').open, 'Exportação perdeu o evento');
    document.getElementById('modal-exportacao').close();
  });
  await testar('Assinaturas: recursos comuns e avisos daFIRMA com X fora do eixo', async () => {
    for (const [recurso, ativo] of Object.entries(FirmaTrator.recursos)) assert(window.tabelaTeste.recursos[recurso] === ativo, 'Recurso divergente: ' + recurso);
    document.getElementById('lista-token').value = '';
    document.getElementById('lista-conectar').click();
    await esperar();
    const cartao = document.querySelector('#firma-mensagens .firma-mensagem');
    assert(cartao?.dataset.tipo === 'erro' && cartao.textContent.includes('Informe o token'), 'Aviso de assinatura não usa mensagens comuns');
    assert(getComputedStyle(document.getElementById('lista-toast')).display === 'none', 'Aviso antigo continua sobre os controles');
    const retangulo = document.getElementById('firma-mensagens').getBoundingClientRect();
    assert(Math.abs(retangulo.left + retangulo.width / 2 - innerWidth / 2) > 4, 'Aviso sem deslocamento');
    cartao.querySelector('.firma-mensagem-fechar').click();
    assert(!document.querySelector('#firma-mensagens .firma-mensagem'), 'X não fechou aviso de assinatura');
    document.getElementById('lista-token').value = 'teste';
  });
  localStorage.removeItem('assineai-chave');
  localStorage.removeItem('assineai-token');
  const saida = document.createElement('pre');
  saida.id = 'resultado-testes';
  saida.textContent = JSON.stringify(resultados);
  document.body.append(saida);
})();
