(() => {
  const paginaRestrita = ['/preparar-assinaturas', '/assinatura', '/assineai/pagina-assinatura', '/advai'].includes(location.pathname);
  if (paginaRestrita) document.documentElement.style.visibility = 'hidden';
  const chaveArmazenamento = 'afirma-acesso-compartilhado';
  const requisitar = window.fetch.bind(window);
  const camposToken = '#lista-token, #firma-token, #token, #empresa-token, #form-acesso input[name="token"]';
  const camposChave = '#lista-chave, #firma-chave, #chave-api, #chave, #form-acesso input[name="chave"]';
  let temporizador;
  let conectando = false;
  let encerramentoPendente = Promise.resolve();
  let acessoAtivado = null;
  let configuracao;
  const configuracaoPronta = requisitar('/api/acesso/configuracao', { cache: 'no-store' })
    .then(async resposta => {
      if (!resposta.ok) throw new Error('Não foi possível carregar a configuração de acesso.');
      configuracao = await resposta.json();
      return configuracao;
    });
  configuracaoPronta.catch(() => {});

  function ler() {
    try {
      const dados = JSON.parse(localStorage.getItem(chaveArmazenamento) || 'null');
      return dados && typeof dados.token === 'string' && typeof dados.chave === 'string'
        && Number.isFinite(dados.expiraEm) ? dados : null;
    } catch { return null; }
  }
  function ativa() { const dados = ler(); return !!dados && dados.expiraEm > Date.now(); }
  function preencher(dados) {
    for (const campo of document.querySelectorAll(camposToken)) campo.value = dados?.token || '';
    for (const campo of document.querySelectorAll(camposChave)) campo.value = dados?.chave || '';
    for (const campo of document.querySelectorAll(`${camposToken}, ${camposChave}`)) {
      if (campo.closest('.rodape-fixo-trator, #firma-login')) continue;
      campo.required = false;
      campo.hidden = true;
      for (const rotulo of document.querySelectorAll(`label[for="${campo.id}"]`)) rotulo.hidden = true;
      const grupo = campo.closest('.campo') || campo.closest('label');
      if (grupo) grupo.hidden = true;
    }
  }
  function sincronizarLegado(dados) {
    // As telas existentes leem essas chaves. O prazo único sempre vem do registro compartilhado.
    for (const nome of ['assineai-token', 'assineai-chave', 'chat-token', 'chat-chave']) {
      const valor = nome.endsWith('token') ? dados?.token : dados?.chave;
      if (valor) localStorage.setItem(nome, valor); else localStorage.removeItem(nome);
    }
  }
  function atualizarEstado() {
    const dados = ler();
    const conectado = !!dados && dados.expiraEm > Date.now();
    const estado = document.getElementById('estado-acesso-compartilhado');
    if (estado) {
      if (conectado) {
        estado.textContent = '';
        estado.hidden = true;
      } else { estado.hidden = false; estado.textContent = 'Acesso desconectado. Conecte pelo rodapé do trator.'; }
    }
    if (dados && !conectado) desconectar('Sua conexão expirou. Conecte novamente no rodapé do trator.');
  }
  function desconectar(mensagem = 'Acesso desconectado.') {
    const haviaAcesso = !!ler();
    localStorage.removeItem(chaveArmazenamento);
    sincronizarLegado(null);
    preencher(null);
    clearTimeout(temporizador);
    acessoAtivado = null;
    document.dispatchEvent(new CustomEvent('firma:desconectado'));
    const estado = document.getElementById('estado-acesso-compartilhado');
    if (estado) { estado.hidden = false; estado.textContent = mensagem; }
    if (haviaAcesso) encerramentoPendente = requisitar('/api/acesso/sair', { method: 'POST', keepalive: true }).catch(() => {});
    if (paginaRestrita) {
      document.documentElement.style.visibility = 'hidden';
      location.replace('/assineai/trator?acesso=necessario');
    }
  }
  function restaurar() {
    const dados = ler();
    if (!dados || dados.expiraEm <= Date.now()) { desconectar(); return; }
    sincronizarLegado(dados);
    preencher(dados);
    clearTimeout(temporizador);
    temporizador = setTimeout(atualizarEstado, Math.max(0, dados.expiraEm - Date.now()));
    atualizarEstado();
    const identificador = `${dados.token}:${dados.expiraEm}`;
    if (acessoAtivado !== identificador) {
      acessoAtivado = identificador;
      document.dispatchEvent(new CustomEvent('firma:acesso-conectado'));
    }
  }
  async function conectar(chave, token) {
    await encerramentoPendente;
    const existente = ler();
    if (ativa() && existente.token === token.trim() && existente.chave === chave.trim()) {
      const resposta = await requisitar('/api/acesso/sessao', { cache: 'no-store' });
      if (resposta.ok) return existente;
    }
    if (conectando) throw new Error('Aguarde a conexão em andamento.');
    conectando = true;
    try {
      await configuracaoPronta;
      token = token.trim(); chave = chave.trim();
      if (!token) throw new Error('Informe o token no rodapé.');
      const resposta = await requisitar('/api/acesso/conectar', {
        method: 'POST', headers: { Authorization: `Bearer ${token}`, ...(chave ? { 'X-API-Key': chave } : {}),
          ...(existente && existente.token === token && existente.chave === chave ? { 'X-Acesso-Expira-Em': String(existente.expiraEm) } : {}) }, cache: 'no-store'
      });
      const dados = await resposta.json().catch(() => ({}));
      if (!resposta.ok) throw new Error(dados.mensagem || dados.detail || 'Acesso inválido ou expirado. Confira as credenciais.');
      if (!Number.isFinite(dados.expiraEm) || dados.expiraEm <= Date.now()) throw new Error('O acesso recebido já expirou.');
      document.dispatchEvent(new CustomEvent('firma:desconectado'));
      localStorage.setItem(chaveArmazenamento, JSON.stringify({ chave, token, expiraEm: dados.expiraEm }));
      restaurar();
      return dados;
    } finally { conectando = false; }
  }
  async function usarTeste() {
    const configuracao = await configuracaoPronta;
    if (!configuracao.testeLocal) throw new Error('Credenciais de teste indisponíveis neste ambiente.');
    const resposta = await requisitar('/api/acesso/teste', { method: 'POST', cache: 'no-store' });
    if (!resposta.ok) throw new Error('Não foi possível emitir as credenciais de teste.');
    const dados = await resposta.json();
    return conectar(dados.chave, dados.token);
  }
  window.acessoFirma = Object.freeze({ ativa, conectar, desconectar, usarTeste });

  window.fetch = (recurso, opcoes = {}) => {
    const endereco = new URL(recurso instanceof Request ? recurso.url : recurso, location.href);
    const apiAdministrativa = endereco.origin === location.origin && endereco.pathname.startsWith('/api/')
      && !endereco.pathname.startsWith('/api/convites/') && !endereco.pathname.startsWith('/api/acesso/')
      && !['/api/assinaturas/politica', '/api/firma/login', '/api/firma/recuperar-senha', '/api/firma/redefinir-senha'].includes(endereco.pathname);
    if (!apiAdministrativa) return requisitar(recurso, opcoes);
    const dados = ler();
    if (dados && dados.expiraEm <= Date.now()) {
      desconectar('Sua conexão expirou. Conecte novamente no rodapé do trator.');
      return Promise.resolve(new Response(JSON.stringify({ mensagem: 'Sua conexão expirou. Reconecte no trator.' }),
        { status: 401, headers: { 'Content-Type': 'application/json' } }));
    }
    if (!dados) return requisitar(recurso, opcoes);
    const cabecalhos = new Headers(opcoes.headers || (recurso instanceof Request ? recurso.headers : undefined));
    if (!cabecalhos.get('Authorization') || /^Bearer\s*$/.test(cabecalhos.get('Authorization'))) cabecalhos.set('Authorization', `Bearer ${dados.token}`);
    if (!cabecalhos.get('X-API-Key') && dados.chave) cabecalhos.set('X-API-Key', dados.chave);
    return requisitar(recurso, { ...opcoes, headers: cabecalhos }).then(resposta => {
      if (resposta.status === 401 && ler()?.token === dados.token && cabecalhos.get('Authorization') === `Bearer ${dados.token}`) {
        desconectar('O acesso foi recusado ou expirou. Conecte novamente no trator.');
      }
      return resposta;
    });
  };
  // Um acesso antigo sem prazo exige reconexão, em vez de receber validade retroativa.
  try { const dados = ler(); sincronizarLegado(dados && dados.expiraEm > Date.now() ? dados : null); } catch {}

  async function prepararNavegacao() {
    const dados = ler();
    if (!dados || !ativa()) {
      desconectar('A conexão expirou. Use Conectar ou Credenciais de teste no rodapé.');
      document.getElementById('lista-conectar')?.focus();
      return false;
    }
    try {
      await conectar(dados.chave, dados.token);
      const resposta = await requisitar('/api/acesso/sessao', { cache: 'no-store' });
      if (!resposta.ok) throw new Error('Não foi possível confirmar a conexão do navegador. Reconecte pelo rodapé.');
      return true;
    } catch (erro) {
      const estado = document.getElementById('estado-acesso-compartilhado');
      if (estado) { estado.hidden = false; estado.textContent = erro.message; }
      return false;
    }
  }
  document.addEventListener('click', async evento => {
    const link = evento.target.closest('a[href]');
    if (!link || evento.defaultPrevented || evento.button !== 0 || evento.ctrlKey || evento.metaKey || evento.shiftKey || evento.altKey) return;
    const endereco = new URL(link.href, location.href);
    if (endereco.origin !== location.origin || !['/preparar-assinaturas', '/assinatura', '/assineai/pagina-assinatura', '/advai'].includes(endereco.pathname)) return;
    evento.preventDefault();
    if (link.dataset.abrindoAcesso) return;
    link.dataset.abrindoAcesso = 'true';
    try {
      if (await prepararNavegacao()) {
        if (link.target === '_blank') window.open(endereco.href, '_blank', 'noopener');
        else location.assign(endereco.href);
      }
    } finally { delete link.dataset.abrindoAcesso; }
  }, true);
  async function validarEntrada() {
    if (!paginaRestrita) return true;
    try {
      if (!ativa()) throw new Error('Acesso ausente');
      const resposta = await requisitar('/api/acesso/sessao', { cache: 'no-store' });
      if (!resposta.ok) throw new Error('Acesso recusado');
      document.documentElement.style.visibility = '';
      return true;
    } catch {
      desconectar('Conecte novamente pelo rodapé do trator.');
      return false;
    }
  }
  async function iniciar() {
    if (!await validarEntrada()) return;
    const rodape = document.querySelector('.acesso-lista-rodape');
    const estado = document.createElement('span');
    estado.id = 'estado-acesso-compartilhado'; estado.setAttribute('role', 'status');
    estado.setAttribute('aria-live', 'off');
    if (rodape) {
      rodape.append(estado);
      const sair = document.createElement('button');
      sair.type = 'button'; sair.className = 'acao'; sair.textContent = 'Desconectar';
      sair.addEventListener('click', () => desconectar()); rodape.append(sair);
    }
    else if (!paginaRestrita) {
      const aviso = document.createElement('p'); aviso.className = 'nota-seguranca';
      const link = document.createElement('a'); link.href = '/assineai/trator'; link.textContent = 'Gerenciar acesso no trator';
      aviso.append(estado, document.createTextNode(' '), link);
      (document.querySelector('main') || document.body).prepend(aviso);
    } else {
      estado.hidden = true; document.body.append(estado);
    }
    for (const [identificador, operacao] of [
      ['lista-conectar', () => conectar(document.getElementById('lista-chave').value, document.getElementById('lista-token').value)],
      ['usar-credenciais-teste', usarTeste]
    ]) {
      const botao = document.getElementById(identificador);
      if (!botao) continue;
      botao.addEventListener('click', async evento => {
        evento.preventDefault(); evento.stopImmediatePropagation();
        if (botao.disabled) return;
        botao.disabled = true;
        try { await operacao(); } catch (erro) { estado.hidden = false; estado.textContent = erro.message; }
        finally { botao.disabled = false; }
      }, true);
    }
    const teste = document.getElementById('usar-credenciais-teste');
    configuracaoPronta.then(configuracao => { if (teste) teste.hidden = !configuracao.testeLocal; }).catch(erro => { estado.textContent = erro.message; });
    for (const identificador of ['entrar', 'teste']) {
      const botao = document.getElementById(identificador);
      if (botao && !botao.closest('.rodape-fixo-trator')) botao.hidden = true;
    }
    restaurar();
    atualizarEstado();
  }
  window.addEventListener('storage', evento => {
    if (evento.key === chaveArmazenamento || evento.key === null) restaurar();
  });
  window.addEventListener('pageshow', async evento => {
    if (evento.persisted && !await validarEntrada()) return;
    if (document.getElementById('estado-acesso-compartilhado')) restaurar();
  });
  document.addEventListener('visibilitychange', () => { if (!document.hidden) atualizarEstado(); });
  document.addEventListener('DOMContentLoaded', iniciar, { once: true });
})();
