(() => {
  const porId = id => document.getElementById(id),
    acesso = porId('firma-login'),
    busca = porId('firma-busca'),
    links = [...document.querySelectorAll('.firma-menu a')];
  const atual = links.find(registro => registro.getAttribute('aria-current') === 'page');
  porId('firma-pagina').textContent = atual?.querySelector('span:last-child')?.textContent.trim() || 'Zapiou';
  function aviso(texto) {
    const painel = porId('relacionamento-aviso') || porId('chat-aviso') || porId('indicadores-aviso');
    if (painel) painel.textContent = texto;
  }
  function abrirAcesso() {
    if (porId('lista-token')) {
      porId('lista-token').focus();
      return;
    }
    porId('firma-chave').value = localStorage.getItem('assineai-chave') || '';
    porId('firma-token').value = localStorage.getItem('assineai-token') || '';
    acesso.showModal();
    porId('firma-token').focus();
  }
  porId('firma-entrar').onclick = porId('firma-conta').onclick = abrirAcesso;
  document.querySelector('[data-firma-chat-acesso]')?.addEventListener('click', abrirAcesso);
  document.querySelectorAll('[data-firma-fechar]').forEach(botaoAtual => botaoAtual.onclick = () => botaoAtual.closest('dialog').close());
  function montarGrupos() {
    const dominio = porId('firma-chat-grupos');
    if (!dominio) return;
    document.querySelectorAll('.firma-chat-menu-popover').forEach(paragrafo => paragrafo.remove());
    dominio.replaceChildren();
    const grupos = new Map();
    let grupo;
    for (const registro of links) {
      if (registro.previousElementSibling?.classList.contains('firma-menu-grupo')) grupo = registro.previousElementSibling.textContent;
      if (registro.hidden || grupo === 'aFIRMA') continue;
      if (!grupos.has(grupo)) grupos.set(grupo, []);
      grupos.get(grupo).push(registro);
    }
    let indice = 0;
    // Uma única aba reúne as telas, com os grupos preservados dentro do menu.
    for (const [titulo, itens] of [['Telas', [...grupos.values()].flat()]]) {
      const botao = document.createElement('button'),
        menu = document.createElement('div');
      botao.type = 'button';
      botao.className = 'trator-aba firma-chat-grupo-aba';
      botao.textContent = titulo.toLocaleUpperCase('pt-BR');
      botao.dataset.grupo = titulo;
      botao.setAttribute('aria-expanded', 'false');
      botao.setAttribute('aria-haspopup', 'menu');
      menu.id = 'firma-chat-menu-' + indice++;
      menu.className = 'firma-chat-menu-popover';
      menu.setAttribute('popover', 'auto');
      menu.setAttribute('aria-label', titulo);
      botao.setAttribute('aria-controls', menu.id);
      let grupoAnterior;
      for (const registro of itens) {
        const nomeGrupo = [...grupos].find(([, registros]) => registros.includes(registro))?.[0];
        if (nomeGrupo !== grupoAnterior) {
          const tituloGrupo = document.createElement('h3');
          tituloGrupo.textContent = nomeGrupo;
          menu.append(tituloGrupo);
          grupoAnterior = nomeGrupo;
        }
        menu.append(registro.cloneNode(true));
      }
      menu.addEventListener('toggle', () => botao.setAttribute('aria-expanded', String(menu.matches(':popover-open'))));
      botao.onclick = () => {
        if (menu.matches(':popover-open')) {
          menu.hidePopover();
          return;
        }
        const retangulo = botao.getBoundingClientRect();
        menu.style.left = Math.max(8, Math.min(retangulo.left, innerWidth - 268)) + 'px';
        menu.style.top = retangulo.bottom + 'px';
        menu.style.maxHeight = Math.max(120, innerHeight - retangulo.bottom - 16) + 'px';
        menu.showPopover();
      };
      document.body.append(menu);
      dominio.append(botao);
    }
  }
  montarGrupos();
  function resultados() {
    const elemento = porId('firma-busca-input').value.toLocaleLowerCase('pt-BR');
    porId('firma-busca-resultados').replaceChildren();
    links.filter(registro => registro.textContent.toLocaleLowerCase('pt-BR').includes(elemento)).forEach(registro => {
      const botaoAtual = registro.cloneNode(true);
      botaoAtual.removeAttribute('aria-current');
      porId('firma-busca-resultados').append(botaoAtual);
    });
  }
  function abrirBusca() {
    busca.showModal();
    porId('firma-busca-input').value = '';
    resultados();
    porId('firma-busca-input').focus();
  }
  document.querySelectorAll('[data-firma-buscar]').forEach(botaoAtual => botaoAtual.onclick = abrirBusca);
  porId('firma-busca-input').oninput = resultados;
  document.addEventListener('keydown', evento => {
    if ((evento.ctrlKey || evento.metaKey) && evento.key.toLowerCase() === 'k') {
      evento.preventDefault();
      abrirBusca();
    }
  });
  porId('firma-menu-toggle').onclick = () => {
    const noCelular = matchMedia('(max-width:850px)').matches;
    document.body.classList.toggle(noCelular ? 'firma-menu-aberto' : 'firma-menu-recolhido');
    porId('firma-menu-toggle').setAttribute('aria-expanded', String(document.body.classList.contains('firma-menu-aberto')));
  };
  async function consultar(token) {
    const resposta = await fetch('/api/firma/contexto', {
      headers: {
        Authorization: 'Bearer ' + token
      },
      cache: 'no-store'
    });
    if (!resposta.ok) throw Error('Token inválido ou expirado. Informe um JWT válido.');
    return resposta.json();
  }
  let versaoConexao = 0;
  function desconectar() {
    versaoConexao++;
    delete window.FirmaAcesso;
    delete window.FirmaContexto;
    document.body.classList.remove('firma-autenticado');
    porId('firma-sessao').textContent = 'Acesse seu espaço';
    porId('firma-sair').hidden = true;
    porId('firma-entrar').textContent = 'Conectar acesso';
    if (porId('lista-conectar')) porId('lista-conectar').textContent = '✓ CONECTAR';
  }
  document.addEventListener('firma:desconectado', desconectar);
  async function conectar(chave, token) {
    document.dispatchEvent(new CustomEvent('firma:desconectado'));
    token = token.trim();
    if (!token) throw Error('Informe o token JWT.');
    const versao = ++versaoConexao,
      dados = await consultar(token);
    if (versao !== versaoConexao) throw Error('As credenciais foram alteradas. Conecte novamente.');
    await acessoFirma.conectar(chave, token);
    ativar(dados, token);
    if (acesso.open) acesso.close();
  }
  porId('firma-login-form').onsubmit = async evento => {
    evento.preventDefault();
    const botaoAtual = evento.target.querySelector('[type=submit]');
    botaoAtual.disabled = true;
    porId('firma-login-erro').textContent = '';
    try {
      await conectar(porId('firma-chave').value, porId('firma-token').value);
    } catch (evento) {
      porId('firma-login-erro').textContent = evento.message;
    } finally {
      botaoAtual.disabled = false;
    }
  };
  porId('firma-sair').onclick = async () => {
    try {
      await fetch('/api/firma/logout', {
        method: 'POST',
        headers: {
          Authorization: 'Bearer ' + localStorage.getItem('assineai-token')
        }
      });
    } catch {}
    acessoFirma.desconectar();
    location.reload();
  };
  if (porId('lista-conectar')) {
    porId('lista-chave').value = localStorage.getItem('assineai-chave') || '';
    porId('lista-token').value = localStorage.getItem('assineai-token') || '';
    porId('lista-conectar').onclick = async () => {
      const elementoAtual = porId('lista-conectar');
      elementoAtual.disabled = true;
      aviso('Conectando…');
      try {
        await conectar(porId('lista-chave').value, porId('lista-token').value);
      } catch (evento) {
        aviso(evento.message);
      } finally {
        elementoAtual.disabled = false;
      }
    };
    for (const id of ['lista-chave', 'lista-token']) porId(id).addEventListener('input', () => document.dispatchEvent(new CustomEvent('firma:desconectado')));
    const teste = porId('usar-credenciais-teste');
    if (teste && document.body.dataset.tokenTeste && document.body.dataset.chaveTeste) {
      teste.hidden = false;
      teste.onclick = () => {
        porId('lista-chave').value = document.body.dataset.chaveTeste;
        porId('lista-token').value = document.body.dataset.tokenTeste;
        porId('lista-conectar').click();
      };
      if (!porId('lista-chave').value && !porId('lista-token').value) {
        porId('lista-chave').value = document.body.dataset.chaveTeste;
        porId('lista-token').value = document.body.dataset.tokenTeste;
      }
    }
  }
  function ativar(dados, token) {
    window.FirmaContexto = dados;
    window.FirmaAcesso = Object.freeze({
      token,
      contexto: dados
    });
    const permitidos = new Set(dados.modulos.map(modulo => modulo.slug));
    links.forEach(registro => {
      const endereco = new URL(registro.href);
      if (endereco.pathname === '/sistema') registro.hidden = !permitidos.has(endereco.searchParams.get('modulo') === 'indicadores' ? 'atendimentos' : endereco.searchParams.get('modulo'));
    });
    montarGrupos();
    document.body.classList.add('firma-autenticado');
    porId('firma-empresa').textContent = dados.nome || 'Sua empresa';
    porId('firma-usuario').textContent = dados.nome || 'Credenciais';
    porId('firma-sessao').textContent = 'Acesso da FIRMA';
    porId('firma-entrar').textContent = 'Credenciais';
    porId('firma-sair').hidden = false;
    if (porId('lista-conectar')) porId('lista-conectar').textContent = 'Credenciais';
    document.dispatchEvent(new CustomEvent('firma:contexto', {
      detail: dados
    }));
  }
  async function contexto() {
    const token = localStorage.getItem('assineai-token');
    if (!token) return;
    try {
      const versao = ++versaoConexao;
      const dados = await consultar(token);
      if (versao === versaoConexao && acessoFirma.ativa() && token === localStorage.getItem('assineai-token')) ativar(dados, token);
    } catch (evento) {
      aviso(evento.message);
    }
  }
  window.addEventListener('storage', evento => {
    if (evento.key === 'afirma-acesso-compartilhado') contexto();
  });
  document.addEventListener('firma:acesso-conectado', contexto);
  if (acessoFirma.ativa()) contexto();
})();
