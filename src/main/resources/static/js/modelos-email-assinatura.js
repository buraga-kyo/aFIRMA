(() => {
  const porId = nome => document.getElementById('email-modelo-' + nome);
  const grade = porId('grade');
  const previa = porId('previa');
  const status = porId('status');
  const assunto = porId('assunto');
  const titulo = porId('titulo');
  const painel = document.getElementById('modelos-email');
  const painelPrevia = porId('painel-previa');
  const statusPrevia = porId('previa-status');
  const janelaHistorico = porId('janela-historico');
  const janelaHtml = porId('janela-html');
  const variavel = porId('variavel');
  const acesso = () => localStorage.getItem('assineai-token') || '';
  const seletorMomento = porId('momento');
  let momento = 'verificacao';
  function prepararVariaveis(catalogo) {
    const preencherSeletor = (seletor, itens) => seletor.replaceChildren(seletor.options[0],
      ...itens.map(item => new Option(item.rotulo, item.nome)));
    preencherSeletor(variavel, catalogo);
    preencherSeletor(porId('variavel-assunto'), catalogo.filter(item => item.nome !== 'codigo_verificacao'));
    return catalogo.map(item => ({
      name: item.nome, label: item.rotulo, required: item.nome === 'codigo_verificacao', hideDefaultValue: true
    }));
  }
  let editor;
  let versoes = [];
  let geracao = 0;
  let registro;
  let acaoInicial;
  let temporizadorPrevia;
  let versaoPrevia = 0;
  let ultimaConsultaPrevia = 0;
  let observadorPrevia;

  function informar(texto, erro = false) {
    status.textContent = texto;
    status.dataset.firmaTipo = erro ? 'erro' : 'sucesso';
    status.hidden = !texto;
  }

  async function pedir(caminho = '', metodo = 'GET', modelo, repeticoes = 0) {
    const token = acesso();
    const atual = geracao;
    const resposta = await fetch('/api/assineai/modelos-email/' + momento + caminho, {
      method: metodo,
      headers: { Authorization: 'Bearer ' + token, ...(modelo ? { 'Content-Type': 'application/json' } : {}) },
      body: modelo ? JSON.stringify(modelo) : undefined,
      cache: 'no-store'
    });
    if (atual !== geracao || token !== acesso()) throw new DOMException('Acesso alterado.', 'AbortError');
    const segundos = Number(resposta.headers.get('Retry-After'));
    if (metodo === 'GET' && resposta.status === 429 && segundos > 0 && segundos <= 2 && repeticoes < 2) {
      await new Promise(concluir => setTimeout(concluir, segundos * 1000));
      if (atual !== geracao || token !== acesso()) throw new DOMException('Acesso alterado.', 'AbortError');
      return pedir(caminho, metodo, modelo, repeticoes + 1);
    }
    const dados = await resposta.json().catch(() => ({}));
    if (atual !== geracao || token !== acesso()) throw new DOMException('Acesso alterado.', 'AbortError');
    if (!resposta.ok) throw Error(dados.detail || dados.mensagem || 'Operação recusada (' + resposta.status + ').');
    return dados;
  }

  async function executar(operacao) {
    if (grade.inert) return;
    grade.inert = true;
    porId('carregar').disabled = true;
    const atual = geracao;
    informar('');
    try {
      await operacao();
    } catch (erro) {
      if (atual === geracao) informar(erro.message, true);
    } finally {
      if (atual === geracao) {
        grade.inert = false;
        porId('carregar').disabled = false;
      }
    }
  }

  async function modeloAtual(validar = true) {
    if (validar && !assunto.reportValidity()) throw Error('Informe o assunto do e-mail.');
    const atual = geracao;
    const assuntoAtual = assunto.value;
    const corpo = await editor.exportar();
    if (atual !== geracao) throw new DOMException('Acesso alterado.', 'AbortError');
    return { assunto: assuntoAtual, ...corpo };
  }

  function preencher(estado) {
    registrar(estado);
    assunto.value = estado.rascunho.assunto;
    editor.carregar(estado.rascunho.documento, estado.rascunho.html);
  }

  function registrar(estado) {
    registro = estado.id;
    seletorMomento.disabled = registro !== null;
    titulo.value = estado.titulo;
    historico(estado);
  }

  async function entradaAtual() {
    if (!titulo.reportValidity()) throw Error('Informe o título do template.');
    return { titulo: titulo.value, modelo: await modeloAtual() };
  }
  const caminhoRegistro = () => registro === null ? '' : '/' + registro;

  function irParaPrevia() {
    painelPrevia.scrollIntoView({ block: 'start' });
    porId('titulo-previa').focus({ preventScroll: true });
  }

  function agendarPrevia() {
    if (grade.hidden || !editor) return;
    versaoPrevia++;
    clearTimeout(temporizadorPrevia);
    const atraso = Math.max(900, 3000 - (Date.now() - ultimaConsultaPrevia));
    temporizadorPrevia = setTimeout(atualizarPrevia, atraso);
  }

  async function atualizarPrevia() {
    clearTimeout(temporizadorPrevia);
    const versao = ++versaoPrevia;
    if (!assunto.value.trim()) {
      statusPrevia.textContent = 'Preencha o assunto para atualizar a prévia.';
      return;
    }
    if (!previa.hasAttribute('srcdoc')) statusPrevia.textContent = 'Carregando prévia…';
    try {
      const modelo = await modeloAtual(false);
      if (versao !== versaoPrevia) return;
      ultimaConsultaPrevia = Date.now();
      const dados = await pedir('/previa', 'POST', modelo);
      if (versao !== versaoPrevia) return;
      mostrarPrevia(dados.mensagem);
      statusPrevia.textContent = '';
    } catch (erro) {
      if (versao === versaoPrevia) statusPrevia.textContent = erro.message;
    }
  }

  function mostrarPrevia(mensagem) {
    porId('assunto-previa').textContent = mensagem.assunto;
    previa.srcdoc = '<!doctype html><html lang="pt-BR"><meta charset="utf-8">'
      + '<meta name="viewport" content="width=device-width, initial-scale=1">'
      + '<meta http-equiv="Content-Security-Policy" content="default-src \'none\'; style-src \'unsafe-inline\'; img-src https:">'
      + '<style>html{overflow:hidden}body{display:flow-root;margin:0;padding:12px;'
      + 'overflow-wrap:anywhere}img{max-width:100%;height:auto}</style><body>' + mensagem.html;
  }

  previa.addEventListener('load', () => {
    observadorPrevia?.disconnect();
    const documento = previa.contentDocument;
    if (!previa.hasAttribute('srcdoc') || !documento?.body) return;
    const corpo = documento.body;
    const ajustarAltura = () => {
      previa.style.height = Math.ceil(corpo.getBoundingClientRect().height) + 4 + 'px';
    };
    ajustarAltura();
    observadorPrevia = new ResizeObserver(ajustarAltura);
    observadorPrevia.observe(corpo);
    documento.addEventListener('click', evento => {
      if (evento.target.closest('a')) evento.preventDefault();
    });
  });

  function historico(estado) {
    versoes = estado.historico;
    porId('ativo').textContent = versoes.length
      ? 'Última publicação: ' + new Date(versoes[0].publicadoEm).toLocaleString('pt-BR')
      : 'Nenhuma publicação registrada.';
    porId('versoes').replaceChildren(...versoes.map(versao => new Option(
      new Date(versao.publicadoEm).toLocaleString('pt-BR') + ' — ' + versao.assunto, versao.id)));
    porId('restaurar').disabled = porId('versoes').disabled = !versoes.length;
  }

  async function carregar() {
    if (!acesso() || painel.hidden) return;
    await executar(async () => {
      if (!window.FirmaEditorEmail) throw Error('O editor de e-mails não foi carregado. Recarregue o Trator.');
      const atual = geracao;
      const dados = await pedir(registro === null ? '/novo' : caminhoRegistro());
      const instancia = editor ??= window.FirmaEditorEmail.montar(porId('maily'), prepararVariaveis(dados.variaveis), agendarPrevia);
      await instancia.pronto;
      if (atual !== geracao) return;
      preencher(dados.estado);
      porId('teste').disabled = !dados.testeDisponivel;
      porId('teste').title = dados.testeDisponivel ? 'Enviar ao Mailpit' : 'Disponível com o Mailpit e SMTP local';
      grade.hidden = false;
      porId('carregar').hidden = true;
      if (acaoInicial === 'historico') janelaHistorico.showModal();
      void atualizarPrevia();
      if (acaoInicial === 'previa') requestAnimationFrame(irParaPrevia);
      if (registro === null) {
        // O campo recebe foco depois que o grupo deixa de estar inerte.
        requestAnimationFrame(() => { if (atual === geracao) titulo.focus(); });
      }
      acaoInicial = undefined;
    });
  }

  porId('formatacao').addEventListener('mousedown', evento => {
    if (evento.target.closest('[data-formato]')) evento.preventDefault();
  });
  porId('formatacao').addEventListener('click', evento => {
    const botao = evento.target.closest('[data-formato]');
    if (botao) editor.formatar(botao.dataset.formato);
  });
  porId('blocos').addEventListener('click', () => editor.abrirBlocos());
  variavel.addEventListener('change', () => {
    if (!variavel.value) return;
    editor.inserirVariavel(variavel.value);
    variavel.value = '';
  });
  porId('variavel-assunto').addEventListener('change', evento => {
    const seletor = evento.currentTarget;
    if (!seletor.value) return;
    const texto = '{{' + seletor.value + '}}';
    const inicio = assunto.selectionStart;
    const fim = assunto.selectionEnd;
    seletor.value = '';
    if (assunto.value.length - (fim - inicio) + texto.length > assunto.maxLength) {
      informar('O assunto aceita até 180 caracteres.', true);
      return;
    }
    assunto.setRangeText(texto, inicio, fim, 'end');
    assunto.focus();
    agendarPrevia();
  });
  assunto.addEventListener('input', agendarPrevia);
  porId('modo').addEventListener('click', () => executar(async () => {
    const dados = await pedir('/previa', 'POST', await modeloAtual());
    porId('html').value = dados.modelo.html;
    janelaHtml.showModal();
  }));
  porId('largura').addEventListener('change', evento => { previa.dataset.largura = evento.target.value; });
  porId('prever').addEventListener('click', irParaPrevia);
  porId('atualizar-previa').addEventListener('click', atualizarPrevia);
  porId('historico').addEventListener('click', () => janelaHistorico.showModal());
  porId('form').addEventListener('submit', evento => {
    evento.preventDefault();
    executar(async () => {
      registrar(await pedir(caminhoRegistro(), registro === null ? 'POST' : 'PUT', await entradaAtual()));
      painel.dispatchEvent(new Event('cartas:atualizado'));
      informar('Rascunho salvo.');
    });
  });
  porId('publicar').addEventListener('click', () => executar(async () => {
    registrar(await pedir(caminhoRegistro() + '/publicar', 'POST', await entradaAtual()));
    painel.dispatchEvent(new Event('cartas:atualizado'));
    informar('O template está ativo para os próximos envios deste momento.');
  }));
  porId('restaurar').addEventListener('click', () => executar(async () => {
    const versao = versoes.find(item => String(item.id) === porId('versoes').value);
    if (!versao) return;
    preencher(await pedir(caminhoRegistro() + '/historico/' + versao.id + '/restaurar', 'POST'));
    painel.dispatchEvent(new Event('cartas:atualizado'));
    janelaHistorico.close();
    void atualizarPrevia();
    informar('Versão salva como rascunho. Publique para ativá-la.');
  }));
  porId('teste').addEventListener('click', () => executar(async () => {
    const dados = await pedir('/teste', 'POST', await modeloAtual());
    informar('E-mail de teste enviado ao Mailpit para ' + dados.destino + '.');
  }));
  porId('carregar').addEventListener('click', carregar);
  function limpar() {
    geracao++;
    grade.inert = false;
    grade.hidden = true;
    versaoPrevia++;
    clearTimeout(temporizadorPrevia);
    observadorPrevia?.disconnect();
    statusPrevia.textContent = '';
    previa.style.removeProperty('height');
    janelaHistorico.close();
    janelaHtml.close();
    porId('carregar').disabled = false;
    porId('carregar').hidden = true;
    titulo.value = assunto.value = '';
    porId('html').value = '';
    previa.removeAttribute('srcdoc');
    porId('assunto-previa').textContent = '';
    historico({ historico: [] });
    informar('');
    editor?.desmontar();
    editor = undefined;
  }

  window.CartasEditor = {
    abrir(id, acao, etapa) {
      limpar();
      registro = id;
      momento = etapa;
      seletorMomento.value = etapa;
      seletorMomento.disabled = id !== null;
      acaoInicial = acao;
      painel.hidden = false;
      porId('carregar').hidden = false;
      carregar();
    },
    fechar() {
      limpar();
      painel.hidden = true;
    }
  };
  seletorMomento.addEventListener('change', () => window.CartasEditor.abrir(null, undefined, seletorMomento.value));
  porId('voltar').addEventListener('click', () => painel.dispatchEvent(new Event('cartas:voltar')));
})();
