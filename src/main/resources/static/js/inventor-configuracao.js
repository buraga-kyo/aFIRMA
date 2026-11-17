(() => {
  const porId = id => document.getElementById(id);
  const base = '/api/assineai/nostr/conexoes';
  const acesso = () => localStorage.getItem('assineai-token') || '';
  let versao = 0, referenciaSalva = '', publicaCliente = '';
  const mensagem = (id, texto) => { porId(id).textContent = texto; };
  function limparSegredos() { porId('conexao-bunker').value = ''; porId('conexao-rpc-senha').value = ''; }
  async function pedir(caminho = '', dados, metodo = 'GET') {
    const token = acesso();
    const resposta = await fetch(base + caminho, { method: metodo, headers: { Authorization: `Bearer ${token}`, ...(dados ? { 'Content-Type': 'application/json' } : {}) }, ...(dados ? { body: JSON.stringify(dados) } : {}), cache: 'no-store' });
    if (acesso() !== token) throw Error('O acesso da empresa mudou. Atualize as configurações.');
    if (!resposta.ok) { const erro = await resposta.json().catch(() => ({})); throw Error(erro.detail || erro.mensagem || `Operação recusada (${resposta.status}).`); }
    return resposta.json();
  }
  function preencher(dados) {
    const nostr = dados.nostr || {}, bitcoin = dados.bitcoin || {};
    referenciaSalva = nostr.referencia || '';
    publicaCliente = nostr.chaveClientePublica || '';
    porId('conexao-cliente-copiar').hidden = !publicaCliente;
    porId('conexao-referencia').value = referenciaSalva || 'firma-principal';
    porId('conexao-publica').value = nostr.chavePublica || '';
    porId('conexao-relays').value = (nostr.relays || []).join('\n');
    porId('conexao-bunker').required = !nostr.bunkerSalvo;
    mensagem('conexao-bunker-ajuda', nostr.bunkerSalvo ? 'Conexão protegida já salva. Deixe em branco para manter.' : 'Cole o endereço bunker completo, incluindo o relay de conexão.');
    mensagem('conexao-cliente', nostr.chaveClientePublica ? `Chave pública do cliente de automação: ${nostr.chaveClientePublica}` : 'Ao salvar, a FIRMA gera a chave do cliente para você autorizar no assinador.');
    porId('conexao-rpc').value = bitcoin.endereco || '';
    porId('conexao-rpc-usuario').value = bitcoin.usuario || '';
    mensagem('conexao-senha-ajuda', bitcoin.senhaSalva ? 'Senha protegida já salva. Deixe em branco para manter.' : 'A senha não será exibida depois de salva.');
    limparSegredos();
    document.querySelectorAll('fieldset').forEach(campo => { campo.disabled = !dados.cofreDisponivel; });
    mensagem('conexoes-estado', dados.cofreDisponivel ? 'Acesso de administrador conferido. As alterações valem para esta empresa.' : 'O cofre de evidências precisa ser configurado na instalação antes de salvar credenciais.');
  }
  async function carregar() {
    const atual = ++versao;
    limparSegredos();
    document.querySelectorAll('fieldset').forEach(campo => { campo.disabled = true; });
    try { const dados = await pedir(); if (atual === versao) preencher(dados); }
    catch (erro) { if (atual === versao) mensagem('conexoes-estado', erro.message); }
  }
  async function executar(formulario, estado, trabalho) {
    const atual = versao;
    formulario.querySelector('fieldset').disabled = true;
    mensagem(estado, 'Aguarde…');
    try { await trabalho(); }
    catch (erro) { if (atual === versao) mensagem(estado, erro.message); }
    finally { limparSegredos(); if (atual === versao) formulario.querySelector('fieldset').disabled = false; }
  }
  porId('conexao-nostr').addEventListener('submit', evento => {
    evento.preventDefault();
    const dados = { referencia: porId('conexao-referencia').value.trim(), chavePublica: porId('conexao-publica').value.trim(), bunker: porId('conexao-bunker').value.trim(), relays: porId('conexao-relays').value.split(/\r?\n/).map(texto => texto.trim()).filter(Boolean) };
    executar(evento.currentTarget, 'conexao-nostr-estado', async () => { preencher(await pedir('/nostr', dados, 'PUT')); mensagem('conexao-nostr-estado', 'Conexão salva. Autorize a chave pública do cliente no assinador, confira a conexão e autorize as publicações no perfil da empresa.'); });
  });
  porId('conexao-bitcoin').addEventListener('submit', evento => {
    evento.preventDefault();
    const dados = { endereco: porId('conexao-rpc').value.trim(), usuario: porId('conexao-rpc-usuario').value.trim(), senha: porId('conexao-rpc-senha').value, remover: false };
    executar(evento.currentTarget, 'conexao-bitcoin-estado', async () => { preencher(await pedir('/bitcoin', dados, 'PUT')); mensagem('conexao-bitcoin-estado', 'Configuração salva. Teste o nó para conferir a rede e a sincronização.'); });
  });
  porId('conexao-nostr-testar').addEventListener('click', () => executar(porId('conexao-nostr'), 'conexao-nostr-estado', async () => {
    if (!referenciaSalva) throw Error('Salve a conexão antes de conferir o assinador.');
    const resultado = await pedir('/nostr/testar', { referencia: referenciaSalva }, 'POST');
    mensagem('conexao-nostr-estado', `Assinador conferido. Chave pública do perfil: ${resultado.chavePublica}`);
  }));
  porId('conexao-bitcoin-testar').addEventListener('click', () => executar(porId('conexao-bitcoin'), 'conexao-bitcoin-estado', async () => {
    const resultado = await pedir('/bitcoin/testar', {}, 'POST');
    mensagem('conexao-bitcoin-estado', resultado.rede === 'main' && resultado.sincronizado ? `Bitcoin Core na rede principal e sincronizado. Altura: ${resultado.blocos}.` : `Nó respondeu na rede ${resultado.rede}. A verificação exige rede principal e sincronização completa.`);
  }));
  porId('conexao-bitcoin-remover').addEventListener('click', () => executar(porId('conexao-bitcoin'), 'conexao-bitcoin-estado', async () => {
    preencher(await pedir('/bitcoin', { remover: true }, 'PUT'));
    mensagem('conexao-bitcoin-estado', 'Configuração desta empresa removida. Se a instalação tem um nó padrão, ele volta a ser usado. As provas existentes são preservadas.');
  }));
  porId('conexao-cliente-copiar').addEventListener('click', async () => {
    try { await navigator.clipboard.writeText(publicaCliente); mensagem('conexao-nostr-estado', 'Chave pública do cliente copiada. Autorize-a no assinador remoto.'); }
    catch { mensagem('conexao-nostr-estado', 'O navegador não permitiu copiar. Selecione a chave pública exibida acima.'); }
  });
  porId('conexoes-atualizar').addEventListener('click', carregar);
  document.addEventListener('firma:acesso-conectado', carregar);
  window.addEventListener('storage', evento => { if (evento.key === 'assineai-token') carregar(); });
  carregar();
})();
