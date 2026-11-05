/** Avisos compartilhados daFIRMA: fecháveis e propositalmente fora do eixo. */
(() => {
  if (window.FirmaMensagens) return;
  const mensagens = new Map(),
    origens = new Map();
  let sequencia = 0,
    painel;
  function montar() {
    if (painel) return painel;
    painel = document.createElement('div');
    painel.id = 'firma-mensagens';
    painel.className = 'firma-mensagens';
    painel.setAttribute('popover', 'manual');
    painel.setAttribute('aria-label', 'Mensagens daFIRMA');
    document.body.append(painel);
    return painel;
  }
  function posicionarCamada(trazerParaFrente = false) {
    if (!painel || !mensagens.size) return;
    const destino = [...document.querySelectorAll('dialog:modal')].at(-1) || document.body;
    if (painel.parentElement !== destino || trazerParaFrente) {
      if (painel.matches(':popover-open')) painel.hidePopover();
      if (painel.parentElement !== destino) destino.append(painel);
    }
    if (!painel.matches(':popover-open')) painel.showPopover();
  }
  function fechar(id) {
    const item = mensagens.get(id);
    if (!item) return;
    clearTimeout(item.timer);
    item.no.remove();
    mensagens.delete(id);
    if (!mensagens.size && painel?.matches(':popover-open')) painel.hidePopover();
  }
  function mostrar(texto, opcoes = {}) {
    texto = String(texto ?? '').trim();
    const id = opcoes.id || `mensagem-${++sequencia}`;
    if (!texto) {
      fechar(id);
      return id;
    }
    fechar(id);
    const no = document.createElement('section'),
      marca = document.createElement('small');
    const conteudo = document.createElement('p'),
      botao = document.createElement('button');
    const tipo = opcoes.tipo || 'info';
    no.className = 'firma-mensagem';
    no.dataset.mensagemId = id;
    no.dataset.tipo = tipo;
    no.setAttribute('role', tipo === 'erro' ? 'alert' : 'status');
    no.setAttribute('aria-atomic', 'true');
    marca.className = 'firma-mensagem-marca';
    marca.textContent = 'RECADOS daFIRMA.';
    conteudo.className = 'firma-mensagem-texto';
    conteudo.textContent = texto;
    botao.type = 'button';
    botao.className = 'firma-mensagem-fechar';
    botao.textContent = '×';
    botao.setAttribute('aria-label', 'Fechar mensagem');
    botao.title = 'Fechar mensagem';
    botao.onclick = () => fechar(id);
    no.append(marca, conteudo, botao);
    const item = {
        no,
        timer: null
      },
      duracao = opcoes.duracao ?? (tipo === 'erro' ? 0 : 8000);
    const agendar = () => {
      if (duracao > 0) item.timer = setTimeout(() => fechar(id), duracao);
    };
    no.addEventListener('mouseenter', () => clearTimeout(item.timer));
    no.addEventListener('mouseleave', agendar);
    no.addEventListener('focusin', () => clearTimeout(item.timer));
    no.addEventListener('focusout', agendar);
    mensagens.set(id, item);
    montar().append(no);
    // O popover manual permanece acessível também sobre formulários em dialog.
    posicionarCamada(true);
    agendar();
    return id;
  }
  function sincronizar() {
    document.querySelectorAll('[data-firma-mensagem]').forEach(origem => {
      if (!origens.has(origem)) {
        origens.set(origem, {
          id: `origem-${++sequencia}`,
          ultimo: ''
        });
        origem.classList.add('firma-mensagem-origem');
        // Só o aviso compartilhado anuncia o texto, sem duplicar a leitura.
        origem.removeAttribute('role');
        origem.removeAttribute('aria-live');
      }
      const estado = origens.get(origem),
        janela = origem.closest('dialog');
      const texto = origem.hidden || janela && !janela.open ? '' : origem.textContent.trim();
      if (!texto) {
        estado.ultimo = '';
        fechar(estado.id);
        return;
      }
      if (texto === estado.ultimo) return;
      estado.ultimo = texto;
      mostrar(texto, {
        id: estado.id,
        tipo: origem.dataset.firmaTipo || (origem.id.includes('erro') ? 'erro' : 'info')
      });
    });
    for (const [origem, estado] of origens) if (!origem.isConnected) {
      fechar(estado.id);
      origens.delete(origem);
    }
    posicionarCamada();
  }
  window.FirmaMensagens = Object.freeze({
    mostrar,
    fechar,
    limpar: () => [...mensagens.keys()].forEach(fechar)
  });
  sincronizar();
  new MutationObserver(registros => {
    // Alterações dos próprios avisos não podem se alimentar de volta.
    if (registros.some(registro => !painel?.contains(registro.target))) sincronizar();
  }).observe(document.body, {
    subtree: true,
    childList: true,
    characterData: true,
    attributes: true,
    attributeFilter: ['hidden', 'open', 'data-firma-tipo']
  });
})();
