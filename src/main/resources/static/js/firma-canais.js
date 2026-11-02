(() => {
  const $ = id => document.getElementById(id);
  let modulo, canais = [], atual = null, versao = 0, versaoQR = 0, timer, tokenAtivo = '';
  const rotulos = {
    CanalNome: 'NOME DO CANAL', CanalNumeroCelular: 'NÚMERO DO WHATSAPP', CanalStatusUso: 'CANAL ATIVO',
    CanalProvedorAPI: 'CONEXÃO', CanalMetaNumeroIdentificador: 'IDENTIFICADOR DO NÚMERO NA META', CanalMetaWAID: 'CONTA WHATSAPP BUSINESS (WABA ID)',
    CanalMsgEncerramentoAtendimento: 'MENSAGEM AO FINALIZAR ATENDIMENTO', CanalVisualizaMenuBOTChat: 'MOSTRAR ATENDIMENTOS DE BOTS',
    CanalFinalizaAtendimentoPorTempoInatividade: 'FINALIZAR POR INATIVIDADE', CanalAtendimentoTempoInatividade: 'TEMPO SEM RESPOSTA (MINUTOS)', CanalMsgFinalizarTempoInatividade: 'MENSAGEM AO FINALIZAR POR INATIVIDADE'
  };
  const estados = { CONECTANDO: 'Conectando. Aguarde o QR.', QR_PENDENTE: 'Escaneie o QR no WhatsApp.', CONECTADO: 'WhatsApp conectado.', DESCONECTADO: 'WhatsApp desconectado.' };
  function aviso(texto, erro = false) {
    const no = $('mensagem-empresa'); no.dataset.firmaTipo = erro ? 'erro' : 'info'; no.textContent = texto; no.hidden = !texto;
  }
  async function api(caminho, metodo = 'GET', dados) {
    const token = tokenAtivo;
    if (!token || token !== $('empresa-token').value.trim()) throw Error('Carregue a FIRMA antes de configurar o canal.');
    const r = await fetch('/api/autentica/' + caminho, { method: metodo, cache: 'no-store', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, ...(dados ? { body: JSON.stringify(dados) } : {}) });
    if (token !== tokenAtivo || token !== $('empresa-token').value.trim()) throw Error('O acesso mudou. Carregue a FIRMA novamente.');
    const d = await r.json().catch(() => ({}));
    if (!r.ok) throw Error(d.mensagem || d.message || `Operação recusada (${r.status}).`);
    return d;
  }
  function pararQR() { versaoQR++; clearTimeout(timer); $('firma-canal-painel').hidden = true; $('firma-canal-qr').hidden = true; $('firma-canal-qr').removeAttribute('src'); }
  function permissoes() {
    $('firma-canal-novo').hidden = !modulo?.criavel;
    $('firma-canal-salvar').hidden = atual ? !modulo?.editavel : !modulo?.criavel;
  }
  function preencher(registro) {
    pararQR(); atual = registro; permissoes();
    const campos = $('firma-canal-campos'); campos.replaceChildren();
    const pode = registro ? modulo.editavel : modulo.criavel;
    for (const c of modulo.campos) {
      if (['CanalStatus', 'CanalNomePastaWhatsApp'].includes(c.nome)) continue;
      const grupo = document.createElement('div'); grupo.className = 'campo'; grupo.dataset.campo = c.nome;
      const label = document.createElement('label'); label.textContent = rotulos[c.nome] || c.nome.replace(/^Canal/, '').replace(/([a-z])([A-Z])/g, '$1 $2').toUpperCase();
      let el;
      if (c.nome === 'CanalProvedorAPI') {
        el = document.createElement('select'); el.add(new Option('WhatsApp com QR (Baileys)', 'Baileys')); el.add(new Option('WhatsApp Business (Meta)', 'Meta'));
        if (registro?.[c.nome] && !['Baileys', 'Meta'].includes(registro[c.nome])) el.add(new Option(registro[c.nome], registro[c.nome]));
      } else if (['TEXT', 'JSON', 'JSONB'].includes(c.tipo)) {
        el = document.createElement('textarea'); grupo.classList.add('campo-largo');
      } else {
        el = document.createElement('input'); el.type = c.tipo === 'BOOLEAN' ? 'checkbox' : c.tipo === 'PASSWORD' ? 'password' : c.tipo === 'INTEGER' ? 'number' : 'text';
        if (c.tipo.startsWith('VARCHAR')) el.maxLength = Number(c.tipo.match(/\d+/)[0]);
        if (el.type === 'password') { el.autocomplete = 'new-password'; el.placeholder = registro ? 'Deixe vazio para manter' : ''; }
        if (el.type === 'number') { el.step = '1'; el.min = '0'; }
      }
      el.id = 'firma-' + c.nome; el.name = c.nome; el.dataset.tipo = c.tipo; el.disabled = !pode; label.htmlFor = el.id;
      const valor = registro?.[c.nome] ?? ({ CanalProvedorAPI: 'Baileys', CanalStatusUso: true }[c.nome]);
      if (el.type === 'checkbox') { el.checked = valor === true; grupo.classList.add('campo-checkbox'); }
      else if (valor != null) el.value = ['JSON', 'JSONB'].includes(c.tipo) ? JSON.stringify(valor, null, 2) : String(valor);
      el.required = Boolean(c.obrigatorio) && el.type !== 'checkbox' && !(el.type === 'password' && registro);
      grupo.append(label, el); campos.append(grupo);
    }
    const ajustarCampos = () => {
      const meta = $('firma-CanalProvedorAPI')?.value === 'Meta';
      const inatividade = $('firma-CanalFinalizaAtendimentoPorTempoInatividade')?.checked;
      for (const grupo of campos.children) {
        const nome = grupo.dataset.campo;
        grupo.hidden = nome.startsWith('CanalMeta') ? !meta : ['CanalAtendimentoTempoInatividade', 'CanalMsgFinalizarTempoInatividade'].includes(nome) ? !inatividade : false;
      }
    };
    campos.onchange = ajustarCampos; ajustarCampos();
    $('firma-canal-form').hidden = false; $('firma-canal-cancelar').hidden = !pode;
    $('firma-canal-conexao').hidden = !registro;
    $('firma-canal-estado').textContent = registro ? (estados[registro.CanalStatus] || registro.CanalStatus || 'Consulte a conexão do canal.') : '';
    const acoes = $('firma-canal-acoes'); acoes.replaceChildren();
    if (registro) for (const [acao, titulo] of [['conectar', 'CONECTAR / QR'], ['estado', 'CONSULTAR CONEXÃO'], ['importar', 'IMPORTAR CONVERSAS'], ['desconectar', 'DESCONECTAR']]) {
      if (!modulo.acoes.includes(acao) || registro.CanalProvedorAPI === 'Meta' && acao !== 'estado') continue;
      const botao = document.createElement('button'); botao.type = 'button'; botao.className = 'botao-carteira'; botao.textContent = titulo;
      botao.onclick = async () => {
        const id = registro.id; botao.disabled = true;
        try {
          if (acao === 'conectar') { await api(`canais/${id}/conectar`, 'POST'); if (atual?.id === id) acompanhar(id); }
          else if (acao === 'importar') { await api(`canais/${id}/importar`, 'POST'); aviso('Histórico solicitado. O WhatsApp define quais mensagens ficam disponíveis.'); }
          else { const d = await api(`canais/${id}/${acao}`, 'POST'); if (atual?.id === id) { if (acao === 'desconectar') pararQR(); $('firma-canal-estado').textContent = d.erro || estados[d.estado] || d.estado || 'Canal desconectado.'; } }
        } catch (e) { aviso(e.message, true); } finally { botao.disabled = false; }
      }; acoes.append(botao);
    }
  }
  async function acompanhar(id) {
    pararQR(); const v = versaoQR; $('firma-canal-painel').hidden = false;
    const inicio = Date.now();
    async function consultar() {
      try {
        const d = await api(`canais/${id}/conexao`, 'POST');
        if (v !== versaoQR || atual?.id !== id) return;
        $('firma-canal-estado').textContent = d.erro || estados[d.estado] || d.estado;
        $('firma-canal-qr').hidden = !d.qr;
        if (d.qr) $('firma-canal-qr').src = d.qr;
        if (d.estado === 'CONECTADO') {
          $('firma-canal-painel').hidden = true;
          const h = await api(`canais/${id}/historico-status`, 'POST');
          if (v === versaoQR) aviso(`${h.conversas || 0} conversas e ${h.mensagens || 0} mensagens no histórico.`);
          return;
        }
        if (d.estado === 'DESCONECTADO' || Date.now() - inicio > 120000) return;
        timer = setTimeout(consultar, 2000);
      } catch (e) { if (v === versaoQR) aviso(e.message, true); }
    }
    consultar();
  }
  async function listar(selecionado) {
    const v = ++versao; pararQR(); const lista = []; let pagina = 1, total = 0;
    do {
      const d = await api(`registros/canais?tamanho=100&pagina=${pagina++}`);
      if (v !== versao) return;
      lista.push(...d.itens); total = d.total;
      if (!d.itens.length) break;
    } while (lista.length < total);
    canais = lista;
    const select = $('firma-canal'); select.replaceChildren(new Option(canais.length ? 'Selecione o canal' : 'Nenhum canal cadastrado', ''));
    canais.forEach(r => select.add(new Option(r.CanalNome || r.CanalNumeroCelular || `Canal ${r.id}`, r.id)));
    const registro = canais.find(r => r.id === selecionado) || canais[0];
    if (registro) { select.value = registro.id; preencher(registro); }
    else if (modulo.criavel) preencher(null);
    else { atual = null; $('firma-canal-form').hidden = true; $('firma-canal-conexao').hidden = true; }
  }
  async function carregar() {
    versao++; pararQR(); atual = null; modulo = null; $('firma-canais').hidden = true;
    tokenAtivo = $('empresa-token').value.trim(); const token = tokenAtivo;
    try {
      const c = await api('contexto'); if (token !== tokenAtivo) return;
      modulo = c.modulos.find(m => m.slug === 'canais');
      if (!modulo) return;
      $('firma-canais').hidden = false; permissoes(); await listar();
    } catch (e) { aviso(e.message, true); }
  }
  $('firma-canal').onchange = () => { const registro = canais.find(r => String(r.id) === $('firma-canal').value); if (registro) preencher(registro); else { pararQR(); atual = null; $('firma-canal-form').hidden = true; $('firma-canal-conexao').hidden = true; } };
  $('firma-canal-novo').onclick = () => { $('firma-canal').value = ''; preencher(null); $('firma-CanalNome')?.focus(); };
  $('firma-canal-atualizar').onclick = () => listar(atual?.id).catch(e => aviso(e.message, true));
  $('firma-canal-cancelar').onclick = () => { const registro = canais.find(r => String(r.id) === $('firma-canal').value); if (registro) preencher(registro); else if (canais.length) { $('firma-canal').value = canais[0].id; preencher(canais[0]); } else { pararQR(); $('firma-canal-form').hidden = true; } };
  $('firma-canal-fechar').onclick = pararQR;
  $('firma-canal-form').onsubmit = async e => {
    e.preventDefault(); const b = $('firma-canal-salvar'), v = versao, id = atual?.id; b.disabled = true;
    try {
      const dados = {};
      for (const el of e.target.querySelectorAll('[name]')) {
        if (el.disabled || el.type === 'password' && !el.value) continue;
        dados[el.name] = el.type === 'checkbox' ? el.checked : el.value === '' ? null : el.type === 'number' ? Number(el.value) : ['JSON', 'JSONB'].includes(el.dataset.tipo) ? JSON.parse(el.value) : el.value;
      }
      const salvo = await api('registros/canais' + (id ? '/' + id : ''), id ? 'PUT' : 'POST', dados);
      if (v !== versao) return;
      await listar(salvo.id); aviso('Canal salvo.');
    } catch (e) { if (v === versao) aviso(e.message, true); } finally { b.disabled = false; }
  };
  $('empresa-token').addEventListener('input', () => { tokenAtivo = ''; versao++; pararQR(); $('firma-canais').hidden = true; });
  window.addEventListener('pagehide', pararQR);
  window.FirmaCanais = Object.freeze({ carregar });
})();
