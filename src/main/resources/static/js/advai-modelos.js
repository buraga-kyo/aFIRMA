(() => {
  const $ = id => document.getElementById(id);
  let versao = 0;
  function aviso(texto, erro = false) {
    const no = $('advai-modelos-aviso');
    no.dataset.firmaTipo = erro ? 'erro' : 'info'; no.textContent = texto; no.hidden = !texto;
  }
  async function pedido(metodo = 'GET', dados) {
    const token = $('empresa-token').value.trim();
    if (!token) throw Error('Entre na FIRMA ou informe o token.');
    const r = await fetch('/api/assineai/empresa', { method: metodo, cache: 'no-store', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, ...(dados ? { body: JSON.stringify(dados) } : {}) });
    if (!r.ok) { const e = await r.json().catch(() => ({})); throw Error(e.mensagem || `Não deu pra carregar os modelos (${r.status}).`); }
    return r.json();
  }
  async function carregar() {
    const v = ++versao, token = $('empresa-token').value.trim();
    $('editor-templates').hidden = true;
    try {
      const empresa = await pedido();
      if (v !== versao || token !== $('empresa-token').value.trim()) return;
      $('perfil-nome').value = empresa.EmpresaNome || '';
      await EditorTemplates.carregar(empresa.EmpresaTemplates);
      if (v !== versao || token !== $('empresa-token').value.trim()) { $('editor-templates').hidden = true; return; }
      aviso('');
    } catch (e) { if (v === versao) aviso(e.message, true); }
  }
  $('advai-carregar-modelos').onsubmit = e => { e.preventDefault(); carregar(); };
  $('empresa-token').oninput = () => { versao++; $('editor-templates').hidden = true; $('perfil-nome').value = ''; };
  $('template-salvar').onclick = async () => {
    const b = $('template-salvar'), v = versao, token = $('empresa-token').value.trim(); b.disabled = true;
    try {
      await pedido('PUT', { EmpresaTemplates: EditorTemplates.dados() });
      if (v !== versao || token !== $('empresa-token').value.trim()) return;
      EditorTemplates.salvo(); aviso('Modelo salvo.');
    } catch (e) { if (v === versao) aviso(e.message, true); }
    finally { b.disabled = false; }
  };
  $('empresa-token').value = localStorage.getItem('assineai-token') || '';
  if ($('empresa-token').value) carregar();
})();
