(() => {
  const porId = (id) => document.getElementById(id);
  const trator = new TratorDocumentos({
    elemento: '#trator-documentos',
    editor: '#editor-templates',
  });
  let versao = 0;
  function aviso(texto, erro = false) {
    const no = porId('assineai-modelos-aviso');
    no.dataset.firmaTipo = erro ? 'erro' : 'info';
    no.textContent = texto;
    no.hidden = !texto;
  }
  async function pedido(metodo = 'GET', dados) {
    const token = porId('empresa-token').value.trim();
    if (!token) throw Error('Entre na FIRMA ou informe o token.');
    const resposta = await fetch('/api/assineai/empresa', {
      method: metodo,
      cache: 'no-store',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      ...(dados
        ? {
            body: JSON.stringify(dados),
          }
        : {}),
    });
    if (!resposta.ok) {
      const evento = await resposta.json().catch(() => ({}));
      throw Error(evento.mensagem || `Não deu pra carregar os modelos (${resposta.status}).`);
    }
    return resposta.json();
  }
  async function carregar() {
    const valor = ++versao,
      token = porId('empresa-token').value.trim();
    porId('editor-templates').hidden = true;
    try {
      const empresa = await pedido();
      if (valor !== versao || token !== porId('empresa-token').value.trim()) return;
      porId('perfil-nome').value = empresa.EmpresaNome || '';
      await EditorTemplates.carregar(empresa.EmpresaTemplates);
      if (valor !== versao || token !== porId('empresa-token').value.trim()) {
        porId('editor-templates').hidden = true;
        return;
      }
      window.dispatchEvent(new Event('firma:editor-carregado'));
      trator.encaixarFolha();
      porId('assineai-acesso-modelos').open = false;
      aviso('');
    } catch (evento) {
      if (valor === versao) aviso(evento.message, true);
    }
  }
  porId('assineai-carregar-modelos').onsubmit = (evento) => {
    evento.preventDefault();
    carregar();
  };
  porId('empresa-token').oninput = () => {
    versao++;
    porId('editor-templates').hidden = true;
    porId('perfil-nome').value = '';
  };
  async function salvar() {
    const elemento = porId('template-salvar'),
      valor = versao,
      token = porId('empresa-token').value.trim();
    elemento.disabled = true;
    try {
      await pedido('PUT', {
        EmpresaTemplates: EditorTemplates.dados(),
      });
      if (valor !== versao || token !== porId('empresa-token').value.trim()) return;
      EditorTemplates.salvo();
      aviso('Padrão salvo para todas as assinaturas da empresa na daFIRMA.');
    } catch (evento) {
      if (valor === versao) aviso(evento.message, true);
    } finally {
      elemento.disabled = false;
    }
  }
  porId('template-salvar').onclick = salvar;
  const definirPadrao = porId('template-ativar').onclick;
  porId('template-ativar').onclick = async () => {
    definirPadrao();
    await salvar();
  };
  porId('empresa-token').value = localStorage.getItem('assineai-token') || '';
  document.addEventListener('firma:acesso-conectado', () => carregar().catch(falha => aviso(falha.message, true)));
  if (porId('empresa-token').value) carregar();
})();
