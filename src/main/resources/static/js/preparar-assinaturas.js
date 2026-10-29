const empresa = document.getElementById('empresa');
const arquivoPdf = document.getElementById('arquivo-pdf');
const chaveApi = document.getElementById('chave-api');
const token = document.getElementById('token');
const listaSignatarios = document.getElementById('lista-signatarios');
const seletorSignatario = document.getElementById('signatario-atual');
const seletorTipo = document.getElementById('tipo-campo');
const visualizador = document.getElementById('visualizador');
const imagemPagina = document.getElementById('imagem-pagina');
const camadaCampos = document.getElementById('camada-campos');
const erroPreparacao = document.getElementById('erro-preparacao');
const resultadoConvites = document.getElementById('resultado-convites');
const nomeTipos = { ASSINATURA: 'Assinatura', RUBRICA: 'Rubrica', SELFIE: 'Selfie' };
const signatarios = [{ id: crypto.randomUUID(), nome: '', email: '', cpf: '', rg: '', whatsapp: '', qualificacao: '', formaDeAutenticacao: ['tela'], formaDeNotificacao: [], campos: [] }];
let paginaAtual = 1;
let totalPaginas = 0;
let enderecoImagem = null;
let identificadorEnvelope = new URLSearchParams(location.search).get('envelope');
let enderecoFinal = null;
let carregandoPdf = false;
let versaoPdf = 0;
const documentos = [{ id: crypto.randomUUID(), nome: '', arquivo: null, paginas: 0, pagina: 1 }];
let documentoAtual = documentos[0];
let recursos = { entregaDisponivel: false, usuario: '' };
let convitesGerados = [];
const nomeDocumento = document.getElementById('nome-documento');
const rotulosMetodos = { tela: 'Assinatura na tela', email: 'Código por e-mail', whatsapp: 'Código por WhatsApp', selfie: 'Selfie' };

async function verificarCanais() {
  const resposta = await verificarResposta(await fetch('/api/assineai/recursos', { headers: credenciais(), cache: 'no-store' }));
  recursos = await resposta.json();
  if (recursos.empresa) {
    empresa.value ||= recursos.empresa.EmpresaNome || '';
    document.getElementById('empresa-cnpj').value = recursos.empresa.EmpresaCNPJ || '';
    document.getElementById('empresa-email').value = recursos.empresa.EmpresaEmail || '';
    document.getElementById('tema-assinatura').value = String(recursos.empresa.EmpresaTemaTelaAssinatura || 'firma').toLowerCase().includes('caderno') ? 'Caderno' : /solida|sólida/i.test(recursos.empresa.EmpresaTemaTelaAssinatura || '') ? 'Cor Solida' : 'firma';
  }
  document.getElementById('disponibilidade-canais').textContent = recursos.entregaDisponivel
    ? 'Canais disponíveis: e-mail e WhatsApp.'
    : 'Entrega não configurada. Compartilhe os links manualmente; assinatura na tela e selfie estão disponíveis.';
  atualizarSignatarios();
}
document.getElementById('verificar-canais').addEventListener('click', () => verificarCanais().catch(erro => mostrarErro(erro.message)));
for (const entrada of [chaveApi, token]) entrada.addEventListener('input', () => {
  recursos = { entregaDisponivel: false, usuario: '' };
  document.getElementById('disponibilidade-canais').textContent = 'Credenciais alteradas. Verifique os canais novamente.';
  document.querySelectorAll('[data-canal-entrega]').forEach(campo => { campo.disabled = !campo.checked; });
});

function atualizarDocumentos() {
  const lista = document.getElementById('lista-documentos');
  lista.replaceChildren();
  for (const [indice, documento] of documentos.entries()) {
    const linha = document.createElement('div');
    linha.className = 'documento-wizard';
    const editar = document.createElement('button');
    editar.type = 'button';
    editar.textContent = `${indice + 1}. ${documento.nome || 'Novo documento'} · ${documento.paginas ? `${documento.paginas} pág.` : 'PDF não aberto'}`;
    editar.setAttribute('aria-pressed', String(documento === documentoAtual));
    editar.disabled = carregandoPdf;
    editar.addEventListener('click', () => selecionarDocumento(documento));
    const remover = document.createElement('button');
    remover.type = 'button'; remover.textContent = 'REMOVER';
    remover.disabled = documentos.length === 1 || carregandoPdf;
    remover.setAttribute('aria-label', `Remover documento ${documento.nome || indice + 1}`);
    remover.addEventListener('click', () => {
      documentos.splice(documentos.indexOf(documento), 1);
      for (const pessoa of signatarios) pessoa.campos = pessoa.campos.filter(campo => campo.documento !== documento.id);
      if (documento === documentoAtual) selecionarDocumento(documentos[0]);
      atualizarDocumentos(); atualizarSignatarios(); desenharCampos();
    });
    linha.append(editar, remover); lista.append(linha);
  }
}

async function selecionarDocumento(documento) {
  if (carregandoPdf) return;
  const versao = ++versaoPdf;
  documentoAtual.pagina = paginaAtual;
  documentoAtual = documento;
  nomeDocumento.value = documento.nome;
  const arquivos = new DataTransfer();
  if (documento.arquivo) arquivos.items.add(documento.arquivo);
  arquivoPdf.files = arquivos.files;
  totalPaginas = documento.paginas;
  paginaAtual = documento.pagina;
  visualizador.hidden = true;
  document.getElementById('header-posicionamento').hidden = true;
  document.getElementById('etapa-posicionamento').hidden = true;
  atualizarDocumentos(); atualizarControlesPdf();
  if (!totalPaginas) return;
  carregandoPdf = true; atualizarControlesPdf();
  try { await mostrarPagina(paginaAtual, versao); }
  catch (erro) { if (versao === versaoPdf) mostrarErro(erro.message); }
  finally { if (versao === versaoPdf) { carregandoPdf = false; atualizarControlesPdf(); atualizarDocumentos(); } }
}

nomeDocumento.addEventListener('input', () => { documentoAtual.nome = nomeDocumento.value; atualizarDocumentos(); });
document.getElementById('adicionar-documento').addEventListener('click', () => {
  if (carregandoPdf) return;
  if (documentos.length >= 10) return mostrarErro('Limite de 10 documentos por assinatura.');
  const documento = { id: crypto.randomUUID(), nome: '', arquivo: null, paginas: 0, pagina: 1 };
  documentos.push(documento); selecionarDocumento(documento); nomeDocumento.focus();
});

function validarPreparacao() {
  if (!empresa.value.trim()) throw new Error('Informe o nome da empresa.');
  if (!signatarios.length) throw new Error('Adicione pelo menos um signatário.');
  if (documentos.some(d => !d.nome.trim() || !d.arquivo || !d.paginas)) throw new Error('Dê um nome e abra o PDF de cada documento.');
  if (signatarios.reduce((n, s) => n + s.campos.length, 0) > 100) throw new Error('Limite de 100 campos por assinatura.');
}

function mostrarRevisao() {
  const painel = document.getElementById('revisao-assinatura');
  painel.replaceChildren();
  const titulo = document.createElement('h2'); titulo.textContent = 'CONFIRA ANTES DE FINALIZAR'; painel.append(titulo);
  for (const documento of documentos) {
    const linha = document.createElement('p');
    linha.textContent = `${documento.nome} · ${documento.paginas} página(s) · ${signatarios.reduce((n, s) => n + s.campos.filter(c => c.documento === documento.id).length, 0)} campo(s)`;
    painel.append(linha);
  }
  for (const pessoa of signatarios) {
    const linha = document.createElement('p');
    linha.textContent = `${pessoa.nome} · ${pessoa.qualificacao || 'Signatário'} · Autenticação: ${pessoa.formaDeAutenticacao.map(a => rotulosMetodos[a]).join(', ') || 'Marcas posicionadas no PDF'} · Notificação: ${pessoa.formaDeNotificacao.join(', ') || 'Link manual'}`;
    painel.append(linha);
  }
}

if (new URLSearchParams(location.search).get('origem') === 'advai') {
  const transferencia = sessionStorage.getItem('advai-proposta-pdf');
  sessionStorage.removeItem('advai-proposta-pdf');
  if (transferencia) {
    try {
      const dados = JSON.parse(transferencia);
      const bytes = Uint8Array.from(atob(dados.conteudo.split(',')[1]), letra => letra.charCodeAt(0));
      const arquivos = new DataTransfer();
      arquivos.items.add(new File([bytes], 'proposta.pdf', { type: 'application/pdf' }));
      arquivoPdf.files = arquivos.files;
      documentoAtual.arquivo = arquivos.files[0];
      documentoAtual.nome = 'Proposta';
      nomeDocumento.value = 'Proposta';
      empresa.value = dados.empresa;
      empresa.dispatchEvent(new Event('input'));
    } catch { mostrarErro('Não foi possível carregar a proposta. Selecione o PDF manualmente.'); }
  }
}

empresa.addEventListener('input', () => {
  document.querySelector('.marca').firstChild.textContent = empresa.value.trim() || 'aFIRMA.';
});

function mostrarErro(mensagem) {
  erroPreparacao.textContent = mensagem;
  erroPreparacao.hidden = false;
  erroPreparacao.scrollIntoView({ block: 'nearest' });
}

function credenciais() {
  if (!token.value.trim()) throw new Error('Entre com a conta da empresa ou informe o token JWT.');
  return { ...(chaveApi.value.trim() ? { 'X-API-Key': chaveApi.value.trim() } : {}), Authorization: `Bearer ${token.value.trim()}` };
}

async function verificarResposta(resposta) {
  if (resposta.ok) return resposta;
  const dados = await resposta.json().catch(() => null);
  throw new Error(dados?.mensagem || `Operação recusada (${resposta.status}).`);
}

// Wizard Logic
const s1 = document.getElementById('step-1-signatarios');
const s2 = document.getElementById('step-2-documentos');
const s3 = document.getElementById('step-3-finalizar');
const b1 = document.getElementById('btn-step-1');
const b2 = document.getElementById('btn-step-2');
const b3 = document.getElementById('btn-step-3');

function changeStep(from, to, btnFrom, btnTo) {
  from.hidden = true;
  to.hidden = false;
  btnFrom.classList.remove('ativo');
  btnFrom.classList.add('inativo');
  btnTo.classList.remove('inativo');
  btnTo.classList.add('ativo');

  for (const botao of [b1, b2, b3]) {
    if (botao === btnTo) botao.setAttribute('aria-current', 'step');
    else botao.removeAttribute('aria-current');
  }
  to.scrollIntoView({ block: 'start', behavior: 'smooth' });
}

document.getElementById('ir-step-2').addEventListener('click', () => {
  erroPreparacao.hidden = true;
  if (!signatarios.length) return mostrarErro('Adicione pelo menos um signatário.');
  if (signatarios.some(s => !s.nome.trim())) return mostrarErro('Preencha o nome de todos os signatários.');
  if (signatarios.some(s => s.email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(s.email.trim()))) return mostrarErro('Informe um e-mail válido ou deixe o campo vazio.');
  if (signatarios.some(s => [...s.formaDeAutenticacao, ...s.formaDeNotificacao].includes('email') && !s.email.trim())) return mostrarErro('Informe e-mail para os canais selecionados.');
  if (signatarios.some(s => [...s.formaDeAutenticacao, ...s.formaDeNotificacao].includes('whatsapp') && !/^\d{10,15}$/.test(s.whatsapp.replace(/\D/g, '')))) return mostrarErro('Informe WhatsApp com DDD para os canais selecionados.');
  if (!recursos.entregaDisponivel && signatarios.some(s => s.formaDeNotificacao.length || s.formaDeAutenticacao.some(a => ['email', 'whatsapp'].includes(a)))) return mostrarErro('Verifique a disponibilidade dos canais com as credenciais atuais.');
  changeStep(s1, s2, b1, b2);
});

document.getElementById('voltar-step-1').addEventListener('click', () => {
  changeStep(s2, s1, b2, b1);
});

document.getElementById('ir-step-3').addEventListener('click', () => {
  erroPreparacao.hidden = true;
  try { validarPreparacao(); } catch (erro) { return mostrarErro(erro.message); }
  mostrarRevisao();
  changeStep(s2, s3, b2, b3);
});

document.getElementById('voltar-step-2').addEventListener('click', () => {
  changeStep(s3, s2, b3, b2);
});

function atualizarSeletor() {
  const selecionado = seletorSignatario.value;
  seletorSignatario.replaceChildren(...signatarios.map((item, indice) => {
    const opcao = document.createElement('option');
    opcao.value = item.id;
    opcao.textContent = `${indice + 1}. ${item.nome.trim() || 'Signatário'} · ${item.campos.length} campo(s)`;
    return opcao;
  }));
  if (signatarios.some(item => item.id === selecionado)) seletorSignatario.value = selecionado;
  atualizarResumo();
}

function atualizarResumo() {
  const pendentes = signatarios.filter(item => !item.campos.length);
  document.getElementById('resumo-campos').textContent = pendentes.length
    ? `Falta posicionar: ${pendentes.map(item => item.nome.trim() || 'Signatário').join(', ')}.`
    : `Tudo pronto: ${signatarios.reduce((total, item) => total + item.campos.length, 0)} campo(s) para ${signatarios.length} signatário(s).`;
}

function atualizarSignatarios() {
  listaSignatarios.replaceChildren();
  for (const signatario of signatarios) {
    const cartao = document.createElement('div');
    cartao.className = 'cartao-signatario cartao-novo-assinante';

    // Nome
    const nome = document.createElement('div');
    nome.className = 'campo';
    nome.innerHTML = '<label>NOME *</label>';
    const entradaNome = document.createElement('input');
    entradaNome.maxLength = 255;
    entradaNome.value = signatario.nome;
    entradaNome.placeholder = 'Nome do signatário';
    entradaNome.addEventListener('input', () => { signatario.nome = entradaNome.value; atualizarSeletor(); desenharCampos(); });
    nome.append(entradaNome);

    // Email
    const email = document.createElement('div');
    email.className = 'campo';
    email.innerHTML = '<label>E-MAIL (OPCIONAL)</label>';
    const entradaEmail = document.createElement('input');
    entradaEmail.type = 'email';
    entradaEmail.maxLength = 254;
    entradaEmail.value = signatario.email;
    entradaEmail.placeholder = 'pessoa@empresa.com';
    entradaEmail.addEventListener('input', () => { signatario.email = entradaEmail.value; });
    email.append(entradaEmail);

    // CPF
    const divCpf = document.createElement('div');
    divCpf.className = 'campo';
    divCpf.innerHTML = '<label>CPF (OPCIONAL)</label>';
    const entradaCpf = document.createElement('input');
    entradaCpf.placeholder = '000.000.000-00';
    entradaCpf.value = signatario.cpf || '';
    entradaCpf.addEventListener('input', () => { signatario.cpf = entradaCpf.value; });
    divCpf.append(entradaCpf);

    // RG
    const divRg = document.createElement('div');
    divRg.className = 'campo';
    divRg.innerHTML = '<label>RG</label>';
    const entradaRg = document.createElement('input');
    entradaRg.placeholder = '00.000.000-X';
    entradaRg.value = signatario.rg || '';
    entradaRg.addEventListener('input', () => { signatario.rg = entradaRg.value; });
    divRg.append(entradaRg);

    // Whatsapp
    const divZap = document.createElement('div');
    divZap.className = 'campo';
    divZap.innerHTML = '<label>WHATSAPP (OPCIONAL)</label>';
    const entradaZap = document.createElement('input');
    entradaZap.placeholder = '+5511999999999';
    entradaZap.value = signatario.whatsapp || '';
    entradaZap.addEventListener('input', () => { signatario.whatsapp = entradaZap.value; });
    divZap.append(entradaZap);

    // Qualificação
    const divQualificacao = document.createElement('div');
    divQualificacao.className = 'campo';
    divQualificacao.innerHTML = '<label>VAI ASSINAR COMO?</label>';
    const entradaQualificacao = document.createElement('input');
    entradaQualificacao.placeholder = 'Associado, Empresa, Motorista...';
    entradaQualificacao.maxLength = 255;
    entradaQualificacao.setAttribute('list', 'qualificacoes-signatario');
    entradaQualificacao.value = signatario.qualificacao || '';
    entradaQualificacao.addEventListener('input', () => { signatario.qualificacao = entradaQualificacao.value; });
    divQualificacao.append(entradaQualificacao);

    // Área de rodapé do cartão (Quantidade e Remover) - Ocupando a linha inteira no grid
    const rodapeCartao = document.createElement('div');
    rodapeCartao.className = 'cartao-rodape-acoes';

    const quantidade = document.createElement('p');
    quantidade.textContent = `${signatario.campos.length} campo(s) posicionado(s)`;

    const remover = document.createElement('button');
    remover.type = 'button';
    remover.className = 'acao secundario';
    remover.textContent = 'REMOVER SIGNATÁRIO';
    remover.addEventListener('click', () => {
      signatarios.splice(signatarios.indexOf(signatario), 1);
      atualizarSignatarios();
      desenharCampos();
    });

    rodapeCartao.append(quantidade, remover);
    const escolhas = document.createElement('div'); escolhas.className = 'escolhas-signatario';
    for (const [propriedade, legenda, metodos] of [
      ['formaDeAutenticacao', 'FORMA DE AUTENTICAÇÃO', [['tela', 'Assinatura na tela'], ['email', 'Código por e-mail'], ['whatsapp', 'Código por WhatsApp'], ['selfie', 'Selfie']]],
      ['formaDeNotificacao', 'FORMA DE NOTIFICAÇÃO', [['email', 'E-mail'], ['whatsapp', 'WhatsApp']]]
    ]) {
      const grupo = document.createElement('fieldset');
      const titulo = document.createElement('legend'); titulo.textContent = legenda; grupo.append(titulo);
      for (const [valor, rotulo] of metodos) {
        const label = document.createElement('label');
        const entrada = document.createElement('input'); entrada.type = 'checkbox'; entrada.value = valor;
        entrada.checked = signatario[propriedade].includes(valor);
        if (['email', 'whatsapp'].includes(valor)) { entrada.dataset.canalEntrega = ''; entrada.disabled = !recursos.entregaDisponivel && !entrada.checked; }
        entrada.addEventListener('change', () => {
          signatario[propriedade] = entrada.checked ? [...signatario[propriedade], valor] : signatario[propriedade].filter(item => item !== valor);
        });
        label.append(entrada, document.createTextNode(rotulo)); grupo.append(label);
      }
      escolhas.append(grupo);
    }

    for (const entrada of [entradaCpf, entradaRg, entradaZap]) entrada.maxLength = 255;
    for (const [grupo, entrada, sufixo] of [[nome, entradaNome, 'nome'], [email, entradaEmail, 'email'],
      [divCpf, entradaCpf, 'cpf'], [divRg, entradaRg, 'rg'], [divZap, entradaZap, 'whatsapp'], [divQualificacao, entradaQualificacao, 'qualificacao']]) {
      entrada.id = `${signatario.id}-${sufixo}`;
      grupo.querySelector('label').htmlFor = entrada.id;
    }
    cartao.append(nome, email, divCpf, divRg, divZap, divQualificacao, escolhas, rodapeCartao);
    listaSignatarios.append(cartao);
  }
  atualizarSeletor();
}

document.getElementById('adicionar-signatario').addEventListener('click', () => {
  if (signatarios.length >= 20) return mostrarErro('Limite de 20 signatários.');
  const signatario = { id: crypto.randomUUID(), nome: '', email: '', cpf: '', rg: '', whatsapp: '', qualificacao: '', formaDeAutenticacao: ['tela'], formaDeNotificacao: [], campos: [] };
  signatarios.push(signatario);
  atualizarSignatarios();
  seletorSignatario.value = signatario.id;
});

function atualizarControlesPdf() {
  document.getElementById('abrir-pdf').disabled = carregandoPdf;
  document.getElementById('pagina-anterior').disabled = carregandoPdf || paginaAtual <= 1;
  document.getElementById('pagina-seguinte').disabled = carregandoPdf || paginaAtual >= totalPaginas;
  document.getElementById('adicionar-campo').disabled = carregandoPdf || !totalPaginas;
  document.getElementById('ir-step-3').disabled = carregandoPdf;
  document.getElementById('adicionar-documento').disabled = carregandoPdf;
  atualizarDocumentos();
  visualizador.inert = carregandoPdf;
  visualizador.setAttribute('aria-busy', String(carregandoPdf));
  document.getElementById('estado-pdf').hidden = !carregandoPdf;
  document.getElementById('estado-pdf').textContent = 'Carregando página do documento…';
}

async function mostrarPagina(pagina = paginaAtual, versao = versaoPdf) {
  const dados = new FormData();
  dados.append('pdf', arquivoPdf.files[0]);
  dados.append('pagina', String(pagina));
  const resposta = await verificarResposta(await fetch('/api/assineai/previsualizar', {
    method: 'POST', headers: credenciais(), body: dados, cache: 'no-store'
  }));
  const novoEndereco = URL.createObjectURL(await resposta.blob());
  const imagem = new Image();
  try {
    imagem.src = novoEndereco;
    await imagem.decode();
    if (versao !== versaoPdf) { URL.revokeObjectURL(novoEndereco); return; }
    imagemPagina.src = novoEndereco;
    if (enderecoImagem) URL.revokeObjectURL(enderecoImagem);
    enderecoImagem = novoEndereco;
  } catch {
    URL.revokeObjectURL(novoEndereco);
    throw new Error('Não foi possível exibir a página do PDF. Tente novamente.');
  }
  paginaAtual = pagina;
  documentoAtual.pagina = pagina;
  visualizador.hidden = false;
  document.getElementById('header-posicionamento').hidden = false;
  document.getElementById('etapa-posicionamento').hidden = false;
  document.getElementById('numero-pagina').textContent = `PÁGINA ${paginaAtual} / ${totalPaginas}`;
  desenharCampos();
}

document.getElementById('abrir-pdf').addEventListener('click', async () => {
  if (carregandoPdf) return;
  erroPreparacao.hidden = true;
  const versao = versaoPdf;
  carregandoPdf = true;
  atualizarControlesPdf();
  try {
    if (!arquivoPdf.files[0]) throw new Error('Escolha um PDF.');
    const dados = new FormData();
    dados.append('pdf', arquivoPdf.files[0]);
    const resposta = await verificarResposta(await fetch('/api/assineai/paginas', {
      method: 'POST', headers: credenciais(), body: dados, cache: 'no-store'
    }));
    const quantidade = (await resposta.json()).paginas;
    if (versao !== versaoPdf) return;
    if (!Number.isInteger(quantidade) || quantidade < 1) throw new Error('O PDF não contém páginas válidas.');
    totalPaginas = quantidade;
    documentoAtual.paginas = quantidade;
    await mostrarPagina(1, versao);
  } catch (erro) {
    if (versao === versaoPdf) mostrarErro(erro.message);
  } finally {
    if (versao === versaoPdf) { carregandoPdf = false; atualizarControlesPdf(); atualizarDocumentos(); }
  }
});

arquivoPdf.addEventListener('change', () => {
  versaoPdf++;
  carregandoPdf = false;
  totalPaginas = 0;
  paginaAtual = 1;
  visualizador.hidden = true;
  if (enderecoImagem) URL.revokeObjectURL(enderecoImagem);
  enderecoImagem = null;
  document.getElementById('header-posicionamento').hidden = true;
  document.getElementById('etapa-posicionamento').hidden = true;
  document.getElementById('numero-pagina').textContent = 'Abra o PDF para começar.';
  documentoAtual.arquivo = arquivoPdf.files[0] || null;
  documentoAtual.paginas = 0; documentoAtual.pagina = 1;
  if (!documentoAtual.nome && documentoAtual.arquivo) {
    documentoAtual.nome = documentoAtual.arquivo.name.replace(/\.pdf$/i, '').slice(0, 255);
    nomeDocumento.value = documentoAtual.nome;
  }
  for (const signatario of signatarios) signatario.campos = signatario.campos.filter(campo => campo.documento !== documentoAtual.id);
  atualizarDocumentos();
  if (!token.value) token.value = localStorage.getItem('assineai-token') || '';
  if (!chaveApi.value) chaveApi.value = localStorage.getItem('assineai-chave') || '';
  atualizarSignatarios();
  atualizarControlesPdf();
});

async function navegarPagina(deslocamento) {
  const pagina = paginaAtual + deslocamento;
  if (carregandoPdf || pagina < 1 || pagina > totalPaginas) return;
  const versao = versaoPdf;
  carregandoPdf = true;
  atualizarControlesPdf();
  erroPreparacao.hidden = true;
  try { await mostrarPagina(pagina, versao); }
  catch (erro) { if (versao === versaoPdf) mostrarErro(erro.message); }
  finally { if (versao === versaoPdf) { carregandoPdf = false; atualizarControlesPdf(); atualizarDocumentos(); } }
}

document.getElementById('pagina-anterior').addEventListener('click', () => navegarPagina(-1));
document.getElementById('pagina-seguinte').addEventListener('click', () => navegarPagina(1));

function adicionarCampo(x, y) {
  if (carregandoPdf || visualizador.hidden) return;
  const signatario = signatarios.find(item => item.id === seletorSignatario.value);
  if (!signatario) return mostrarErro('Adicione e selecione um signatário.');
  if (signatarios.reduce((n, s) => n + s.campos.length, 0) >= 100) return mostrarErro('Limite de 100 campos por assinatura.');
  const tipo = seletorTipo.value;
  const largura = tipo === 'SELFIE' ? 0.2 : tipo === 'RUBRICA' ? 0.18 : 0.3;
  const altura = tipo === 'SELFIE' ? 0.2 : tipo === 'RUBRICA' ? 0.07 : 0.1;
  signatario.campos.push({ id: crypto.randomUUID(), tipo, pagina: paginaAtual, documento: documentoAtual.id,
    posicaoX: Math.max(0, Math.min(1 - largura, x - largura / 2)),
    posicaoY: Math.max(0, Math.min(1 - altura, y - altura / 2)), largura, altura });
  atualizarSignatarios();
  seletorSignatario.value = signatario.id;
  desenharCampos();
  camadaCampos.lastElementChild?.focus({ preventScroll: true });
}

camadaCampos.addEventListener('click', evento => {
  if (evento.target !== camadaCampos) return;
  const retangulo = camadaCampos.getBoundingClientRect();
  adicionarCampo((evento.clientX - retangulo.left) / retangulo.width, (evento.clientY - retangulo.top) / retangulo.height);
});
document.getElementById('adicionar-campo').addEventListener('click', () => adicionarCampo(0.5, 0.5));
seletorSignatario.addEventListener('change', desenharCampos);

function desenharCampos() {
  camadaCampos.replaceChildren();
  for (const signatario of signatarios) {
    for (const campo of signatario.campos.filter(item => item.pagina === paginaAtual && item.documento === documentoAtual.id)) {
      const caixa = document.createElement('div');
      caixa.className = 'campo-posicionado';
      caixa.dataset.tipo = campo.tipo;
      caixa.tabIndex = 0;
      caixa.setAttribute('aria-label', `${nomeTipos[campo.tipo]} de ${signatario.nome}, página ${campo.pagina}. Use as setas para mover.`);
      caixa.classList.toggle('campo-ativo', signatario.id === seletorSignatario.value);
      caixa.style.left = `${campo.posicaoX * 100}%`;
      caixa.style.top = `${campo.posicaoY * 100}%`;
      caixa.style.width = `${campo.largura * 100}%`;
      caixa.style.height = `${campo.altura * 100}%`;
      const texto = document.createElement('span');
      texto.textContent = `${nomeTipos[campo.tipo]} · ${signatario.nome.trim() || 'Signatário'}`;
      const remover = document.createElement('button');
      remover.type = 'button';
      remover.setAttribute('aria-label', 'Remover campo');
      remover.textContent = '×';
      remover.addEventListener('click', evento => {
        evento.stopPropagation();
        signatario.campos.splice(signatario.campos.indexOf(campo), 1);
        atualizarSignatarios();
        desenharCampos();
      });
      const alca = document.createElement('span');
      alca.className = 'alca-redimensionar';
      alca.setAttribute('aria-hidden', 'true');
      caixa.append(texto, remover, alca);
      const atualizarGeometria = () => {
        caixa.style.left = `${campo.posicaoX * 100}%`;
        caixa.style.top = `${campo.posicaoY * 100}%`;
        caixa.style.width = `${campo.largura * 100}%`;
        caixa.style.height = `${campo.altura * 100}%`;
      };
      caixa.addEventListener('keydown', evento => {
        if (evento.target !== caixa) return;
        const direcoes = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] };
        const direcao = direcoes[evento.key];
        if (!direcao) return;
        evento.preventDefault();
        const passo = evento.shiftKey ? 0.02 : 0.002;
        campo.posicaoX = Math.max(0, Math.min(1 - campo.largura, campo.posicaoX + direcao[0] * passo));
        campo.posicaoY = Math.max(0, Math.min(1 - campo.altura, campo.posicaoY + direcao[1] * passo));
        atualizarGeometria();
      });
      caixa.addEventListener('pointerdown', evento => {
        if (evento.target === remover || evento.button !== 0) return;
        evento.preventDefault();
        caixa.focus({ preventScroll: true });
        caixa.setPointerCapture(evento.pointerId);
        const redimensionar = evento.target === alca;
        const inicio = { x: evento.clientX, y: evento.clientY, ...campo };
        const retangulo = camadaCampos.getBoundingClientRect();
        const mover = movimento => {
          const dx = (movimento.clientX - inicio.x) / retangulo.width;
          const dy = (movimento.clientY - inicio.y) / retangulo.height;
          if (redimensionar) {
            campo.largura = Math.max(0.08, Math.min(1 - campo.posicaoX, inicio.largura + dx));
            campo.altura = Math.max(0.04, Math.min(1 - campo.posicaoY, inicio.altura + dy));
          } else {
            campo.posicaoX = Math.max(0, Math.min(1 - campo.largura, inicio.posicaoX + dx));
            campo.posicaoY = Math.max(0, Math.min(1 - campo.altura, inicio.posicaoY + dy));
          }
          atualizarGeometria();
        };
        const terminar = () => {
          caixa.removeEventListener('pointermove', mover);
          caixa.removeEventListener('pointerup', terminar);
          caixa.removeEventListener('pointercancel', terminar);
          caixa.removeEventListener('lostpointercapture', terminar);
        };
        caixa.addEventListener('pointermove', mover);
        caixa.addEventListener('pointerup', terminar);
        caixa.addEventListener('pointercancel', terminar);
        caixa.addEventListener('lostpointercapture', terminar);
      });
      camadaCampos.append(caixa);
    }
  }
}

document.getElementById('criar-envelope').addEventListener('click', async () => {
  erroPreparacao.hidden = true;
  const botao = document.getElementById('criar-envelope');
  const nomeAssinatura = document.getElementById('nome-assinatura');
  const loading = document.getElementById('loading-finalizacao');

  try {
    if (!nomeAssinatura.value.trim()) {
      throw new Error('Informe o nome da assinatura.');
    }
    botao.hidden = true;
    document.getElementById('voltar-step-2').hidden = true;
    loading.hidden = false;

    validarPreparacao();
    await verificarResposta(await fetch('/api/assineai/empresa', { method: 'PUT', headers: { ...credenciais(), 'Content-Type': 'application/json' }, body: JSON.stringify({ EmpresaNome: empresa.value.trim(), EmpresaCNPJ: document.getElementById('empresa-cnpj').value.trim(), EmpresaEmail: document.getElementById('empresa-email').value.trim(), EmpresaTemaTelaAssinatura: document.getElementById('tema-assinatura').value }) }));
    const dados = new FormData();
    for (const documento of documentos) dados.append('pdfs', documento.arquivo);
    dados.append('dados', new Blob([JSON.stringify({
      nomeEmpresa: empresa.value.trim(),
      nomeAssinatura: nomeAssinatura.value.trim(),
      documentos: documentos.map(d => ({ nome: d.nome.trim() })),
      signatarios: signatarios.map(item => ({
        nome: item.nome.trim(),
        email: item.email.trim(),
        cpf: item.cpf ? item.cpf.trim() : null,
        rg: item.rg ? item.rg.trim() : null,
        whatsapp: item.whatsapp ? item.whatsapp.trim() : null,
        qualificacao: item.qualificacao ? item.qualificacao.trim() : null,
        formaDeAutenticacao: item.formaDeAutenticacao,
        formaDeNotificacao: item.formaDeNotificacao,
        campos: item.campos.map(({ id, documento, ...campo }) => ({ ...campo, documento: documentos.findIndex(d => d.id === documento) }))
      }))
    })], { type: 'application/json' }));

    const resposta = await verificarResposta(await fetch('/api/assineai/assinaturas', {
      method: 'POST', headers: credenciais(), body: dados, cache: 'no-store'
    }));
    const criado = await resposta.json();
    identificadorEnvelope = criado.identificador;

    loading.hidden = true;
    convitesGerados = criado.convites;
    mostrarConvites(criado.convites);
    if (signatarios.some(s => s.formaDeNotificacao.length)) {
      document.getElementById('enviar-notificacoes').hidden = false;
      await enviarNotificacoes();
    }
  } catch (erro) {
    mostrarErro(erro.message);
    botao.hidden = false;
    document.getElementById('voltar-step-2').hidden = false;
    loading.hidden = true;
  }
});

function mostrarConvites(convites) {
  resultadoConvites.hidden = false;
  const lista = document.getElementById('lista-convites');
  lista.replaceChildren();
  for (const convite of convites) {
    const cartao = document.createElement('div');
    cartao.className = 'cartao-convite';
    const nome = document.createElement('strong');
    nome.textContent = `${convite.nome}${convite.email ? ` · ${convite.email}` : ''}`;
    const link = document.createElement('a');
    link.href = `${location.origin}/web-panels/assinar-documentos/${convite.token}`;
    link.textContent = link.href;
    link.rel = 'noreferrer';
    const copiar = document.createElement('button');
    copiar.type = 'button';
    copiar.textContent = 'COPIAR LINK';
    copiar.addEventListener('click', async () => {
      try { await navigator.clipboard.writeText(link.href); copiar.textContent = 'COPIADO'; }
      catch { mostrarErro('Não foi possível copiar. Selecione e copie o link exibido.'); }
    });
    cartao.append(nome, document.createElement('br'), link, document.createElement('br'), copiar);
    lista.append(cartao);
  }
  resultadoConvites.scrollIntoView({ block: 'nearest' });
}

window.addEventListener('pagehide', () => {
  if (enderecoImagem) URL.revokeObjectURL(enderecoImagem);
  if (enderecoFinal) URL.revokeObjectURL(enderecoFinal);
});

document.getElementById('adicionar-eu').addEventListener('click', async () => {
  try {
    if (!recursos.usuario) await verificarCanais();
    if (signatarios.length >= 20) throw new Error('Limite de 20 signatários.');
    const nome = recursos.usuario;
    const vazio = signatarios.length === 1 && !signatarios[0].nome.trim() ? signatarios[0] : null;
    const pessoa = vazio || { id: crypto.randomUUID(), nome: '', email: '', cpf: '', rg: '', whatsapp: '', qualificacao: '', formaDeAutenticacao: ['tela'], formaDeNotificacao: [], campos: [] };
    pessoa.nome = nome; pessoa.email = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(nome) ? nome : '';
    pessoa.qualificacao = 'Empresa';
    if (!vazio) signatarios.push(pessoa);
    atualizarSignatarios();
  } catch (erro) { mostrarErro(erro.message); }
});

async function enviarNotificacoes() {
  const botao = document.getElementById('enviar-notificacoes');
  const estado = document.getElementById('estado-notificacoes');
  botao.disabled = true; estado.textContent = 'Enviando notificações…';
  try {
    const resposta = await verificarResposta(await fetch(`/api/assineai/assinaturas/${identificadorEnvelope}/notificar`, {
      method: 'POST', headers: { ...credenciais(), 'Content-Type': 'application/json' },
      body: JSON.stringify(convitesGerados.map(c => c.token))
    }));
    const resultados = await resposta.json();
    estado.textContent = resultados.map(r => `${r.nome} (${r.canal}): ${r.enviado ? 'enviado' : 'falha na entrega; compartilhe o link manualmente'}`).join(' · ');
    botao.hidden = resultados.every(r => r.enviado);
  } catch (erro) { estado.textContent = `Convites criados. Não foi possível enviar notificações: ${erro.message} Compartilhe os links ou tente novamente.`; }
  finally { botao.disabled = false; }
}
document.getElementById('enviar-notificacoes').addEventListener('click', enviarNotificacoes);

// Renderiza o signatário inicial
document.addEventListener('DOMContentLoaded', () => {
  if (!token.value) token.value = localStorage.getItem('assineai-token') || '';
  if (!chaveApi.value) chaveApi.value = localStorage.getItem('assineai-chave') || '';
  atualizarSignatarios();
  atualizarControlesPdf();
});

// Renderiza o signatário inicial
document.addEventListener('DOMContentLoaded', () => {
  atualizarSignatarios();
});
