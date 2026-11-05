(async () => {
  const resultados = [],
    esperar = (milissegundos = 20) => new Promise(registro => setTimeout(registro, milissegundos));
  const assert = (ok, mensagem) => {
    if (!ok) throw Error(mensagem);
  };
  async function teste(nome, executarAcao) {
    try {
      await executarAcao();
      resultados.push({
        nome,
        ok: true
      });
    } catch (evento) {
      resultados.push({
        nome,
        ok: false,
        erro: evento.stack
      });
    }
  }
  const origem = document.createElement('p');
  origem.id = 'chat-aviso';
  origem.dataset.firmaMensagem = '';
  origem.textContent = 'Conecte o token no rodapé para carregar as conversas.';
  document.body.append(origem);
  await esperar();
  await teste('Aviso inicial usa o recado comum, com X e posição fora do eixo', () => {
    const cartao = document.querySelector('.firma-mensagem'),
      botao = cartao.querySelector('button');
    assert(cartao.textContent.includes('Conecte o token'), 'Texto ausente');
    assert(botao.getAttribute('aria-label') === 'Fechar mensagem', 'X sem nome acessível');
    assert(getComputedStyle(origem).display === 'none', 'Aviso antigo continua sobre o botão');
    const retangulo = document.getElementById('firma-mensagens').getBoundingClientRect();
    const x = retangulo.left + retangulo.width / 2,
      y = retangulo.top + retangulo.height / 2;
    assert(Math.abs(x - innerWidth / 2) > 4 && Math.abs(x - innerWidth / 2) < 100, 'Posição horizontal não é deslocada do centro');
    assert(Math.abs(y - innerHeight / 2) > 4 && Math.abs(y - innerHeight / 2) < 100, 'Posição vertical fora do padrão');
  });
  await teste('X fecha de verdade e alterações da página não ressuscitam o mesmo aviso', async () => {
    document.querySelector('.firma-mensagem-fechar').click();
    await esperar();
    const div = document.createElement('div');
    div.textContent = 'Atualização da tabela';
    document.body.append(div);
    await esperar();
    assert(!document.querySelector('.firma-mensagem'), 'Aviso fechado reapareceu');
    assert(!document.getElementById('firma-mensagens').matches(':popover-open'), 'Camada vazia permanece aberta');
  });
  await teste('Limpar a mensagem de origem remove o recado imediatamente ao conectar', async () => {
    origem.textContent = 'Conectando…';
    await esperar();
    assert(document.querySelector('.firma-mensagem'), 'Aviso novo ausente');
    origem.textContent = '';
    await esperar();
    assert(!document.querySelector('.firma-mensagem'), 'Aviso vazio permaneceu visível');
  });
  await teste('Erros são fecháveis sobre dialog e não roubam o foco do formulário', async () => {
    const janela = document.createElement('dialog');
    janela.innerHTML = '<input id="campo-teste"><p id="erro-teste" data-firma-mensagem hidden></p>';
    document.body.append(janela);
    janela.showModal();
    const entrada = janela.querySelector('input');
    entrada.focus();
    const erro = janela.querySelector('p');
    erro.textContent = 'O acesso expirou.';
    erro.hidden = false;
    await esperar();
    const cartao = document.querySelector('.firma-mensagem');
    assert(cartao.dataset.tipo === 'erro', 'Tipo do erro incorreto');
    assert(document.activeElement === entrada, 'Aviso roubou foco da edição');
    const botao = cartao.querySelector('button'),
      retangulo = botao.getBoundingClientRect();
    assert(document.elementFromPoint(retangulo.left + retangulo.width / 2, retangulo.top + retangulo.height / 2) === botao, 'Dialog impede fechar o aviso');
    botao.click();
    await esperar();
    assert(!document.querySelector('.firma-mensagem'), 'X não fechou erro');
    erro.textContent = 'Outro erro';
    await esperar();
    assert(document.querySelector('.firma-mensagem'), 'Erro seguinte ausente');
    janela.close();
    await esperar();
    assert(!document.querySelector('.firma-mensagem'), 'Erro de formulário fechado permaneceu aberto');
    janela.remove();
  });
  await teste('Mensagens informativas expiram; passar o mouse pausa o fechamento', async () => {
    FirmaMensagens.mostrar('Salvo.', {
      id: 'salvo',
      duracao: 60
    });
    const cartao = document.querySelector('[data-mensagem-id=salvo]');
    cartao.dispatchEvent(new Event('mouseenter'));
    await esperar(90);
    assert(cartao.isConnected, 'Aviso expirou durante a leitura');
    cartao.dispatchEvent(new Event('mouseleave'));
    await esperar(90);
    assert(!cartao.isConnected, 'Aviso não expirou');
  });
  await teste('Conteúdo é texto seguro e todas as mensagens têm X', () => {
    FirmaMensagens.mostrar('<img src=x onerror=alert(1)>', {
      id: 'seguro',
      duracao: 0
    });
    const cartao = document.querySelector('[data-mensagem-id=seguro]');
    assert(!cartao.querySelector('img') && cartao.textContent.includes('<img'), 'Conteúdo interpretado como HTML');
    assert(cartao.querySelector('.firma-mensagem-fechar'), 'Mensagem sem X');
    FirmaMensagens.limpar();
  });
  await teste('Página não gerou erros JavaScript', () => assert(!window.errosMensagens.length, window.errosMensagens.join('\n')));
  const out = document.createElement('pre');
  out.id = 'resultado-testes';
  out.textContent = JSON.stringify(resultados);
  document.body.append(out);
})();
