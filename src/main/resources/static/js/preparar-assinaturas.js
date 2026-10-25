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
const finalizacao = document.getElementById('finalizacao');
const nomeTipos = { ASSINATURA: 'Assinatura', RUBRICA: 'Rubrica', SELFIE: 'Selfie' };
const signatarios = [];
let paginaAtual = 1;
let totalPaginas = 0;
let enderecoImagem = null;
let identificadorEnvelope = new URLSearchParams(location.search).get('envelope');
let enderecoFinal = null;

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
      empresa.value = dados.empresa;
      empresa.dispatchEvent(new Event('input'));
    } catch { mostrarErro('Não foi possível carregar a proposta. Selecione o PDF manualmente.'); }
  }
}

empresa.addEventListener('input', () => {
  document.querySelector('.marca').firstChild.textContent = empresa.value.trim() || 'aFIRMA.';
  document.getElementById('rodape-empresa').textContent = empresa.value.trim() || 'aFIRMA.';
});

function mostrarErro(mensagem) {
  erroPreparacao.textContent = mensagem;
  erroPreparacao.hidden = false;
  erroPreparacao.scrollIntoView({ block: 'nearest' });
}

function credenciais() {
  if (!chaveApi.value.trim() || !token.value.trim()) throw new Error('Informe chave da API e token JWT.');
  return { 'X-API-Key': chaveApi.value.trim(), Authorization: `Bearer ${token.value.trim()}` };
}

async function verificarResposta(resposta) {
  if (resposta.ok) return resposta;
  const dados = await resposta.json().catch(() => null);
  throw new Error(dados?.mensagem || `Operação recusada (${resposta.status}).`);
}

function atualizarSignatarios() {
  listaSignatarios.replaceChildren();
  seletorSignatario.replaceChildren();
  for (const signatario of signatarios) {
    const cartao = document.createElement('div');
    cartao.className = 'cartao-signatario';
    const campos = document.createElement('div');
    campos.className = 'campos';
    const nome = document.createElement('div');
    nome.className = 'campo';
    const rotuloNome = document.createElement('label');
    rotuloNome.textContent = 'NOME';
    const entradaNome = document.createElement('input');
    entradaNome.maxLength = 120;
    entradaNome.value = signatario.nome;
    entradaNome.placeholder = 'Nome do signatário';
    entradaNome.addEventListener('input', () => { signatario.nome = entradaNome.value; atualizarSeletor(); desenharCampos(); });
    nome.append(rotuloNome, entradaNome);
    const email = document.createElement('div');
    email.className = 'campo';
    const rotuloEmail = document.createElement('label');
    rotuloEmail.textContent = 'E-MAIL PARA IDENTIFICAÇÃO';
    const entradaEmail = document.createElement('input');
    entradaEmail.type = 'email';
    entradaEmail.maxLength = 254;
    entradaEmail.value = signatario.email;
    entradaEmail.placeholder = 'pessoa@empresa.com';
    entradaEmail.addEventListener('input', () => { signatario.email = entradaEmail.value; });
    email.append(rotuloEmail, entradaEmail);
    campos.append(nome, email);
    const quantidade = document.createElement('p');
    quantidade.textContent = `${signatario.campos.length} campo(s) posicionado(s)`;
    const remover = document.createElement('button');
    remover.type = 'button';
    remover.textContent = 'REMOVER SIGNATÁRIO';
    remover.addEventListener('click', () => {
      signatarios.splice(signatarios.indexOf(signatario), 1);
      atualizarSignatarios();
      desenharCampos();
    });
    cartao.append(campos, quantidade, remover);
    listaSignatarios.append(cartao);
  }
  atualizarSeletor();
}

function atualizarSeletor() {
  const selecionado = seletorSignatario.value;
  seletorSignatario.replaceChildren();
  for (const signatario of signatarios) {
    const opcao = document.createElement('option');
    opcao.value = signatario.id;
    opcao.textContent = signatario.nome.trim() || 'Signatário sem nome';
    seletorSignatario.append(opcao);
  }
  if (signatarios.some(signatario => signatario.id === selecionado)) seletorSignatario.value = selecionado;
}

document.getElementById('adicionar-signatario').addEventListener('click', () => {
  if (signatarios.length >= 20) return mostrarErro('Limite de 20 signatários.');
  const signatario = { id: crypto.randomUUID(), nome: '', email: '', campos: [] };
  signatarios.push(signatario);
  atualizarSignatarios();
  seletorSignatario.value = signatario.id;
});

async function mostrarPagina() {
  if (!arquivoPdf.files[0] || !totalPaginas) return;
  const dados = new FormData();
  dados.append('pdf', arquivoPdf.files[0]);
  dados.append('pagina', String(paginaAtual));
  const resposta = await verificarResposta(await fetch('/api/envelopes/previsualizar', {
    method: 'POST', headers: credenciais(), body: dados, cache: 'no-store'
  }));
  const novoEndereco = URL.createObjectURL(await resposta.blob());
  await new Promise((resolver, rejeitar) => {
    imagemPagina.onload = resolver;
    imagemPagina.onerror = rejeitar;
    imagemPagina.src = novoEndereco;
  });
  if (enderecoImagem) URL.revokeObjectURL(enderecoImagem);
  enderecoImagem = novoEndereco;
  visualizador.hidden = false;
  document.getElementById('numero-pagina').textContent = `PÁGINA ${paginaAtual} / ${totalPaginas}`;
  desenharCampos();
}

document.getElementById('abrir-pdf').addEventListener('click', async () => {
  erroPreparacao.hidden = true;
  try {
    if (!arquivoPdf.files[0]) throw new Error('Escolha um PDF.');
    const dados = new FormData();
    dados.append('pdf', arquivoPdf.files[0]);
    const resposta = await verificarResposta(await fetch('/api/envelopes/paginas', {
      method: 'POST', headers: credenciais(), body: dados, cache: 'no-store'
    }));
    totalPaginas = (await resposta.json()).paginas;
    paginaAtual = 1;
    await mostrarPagina();
  } catch (erro) { mostrarErro(erro.message); }
});

arquivoPdf.addEventListener('change', () => {
  totalPaginas = 0;
  visualizador.hidden = true;
  document.getElementById('numero-pagina').textContent = 'Abra o PDF para começar.';
  for (const signatario of signatarios) signatario.campos = [];
  atualizarSignatarios();
});

document.getElementById('pagina-anterior').addEventListener('click', async () => {
  if (paginaAtual > 1) { paginaAtual--; try { await mostrarPagina(); } catch (erro) { mostrarErro(erro.message); } }
});
document.getElementById('pagina-seguinte').addEventListener('click', async () => {
  if (paginaAtual < totalPaginas) { paginaAtual++; try { await mostrarPagina(); } catch (erro) { mostrarErro(erro.message); } }
});

camadaCampos.addEventListener('click', evento => {
  if (evento.target !== camadaCampos) return;
  const signatario = signatarios.find(item => item.id === seletorSignatario.value);
  if (!signatario) return mostrarErro('Adicione e selecione um signatário.');
  const retangulo = camadaCampos.getBoundingClientRect();
  const tipo = seletorTipo.value;
  const largura = tipo === 'SELFIE' ? 0.2 : tipo === 'RUBRICA' ? 0.18 : 0.3;
  const altura = tipo === 'SELFIE' ? 0.2 : tipo === 'RUBRICA' ? 0.07 : 0.1;
  const x = Math.max(0, Math.min(1 - largura, (evento.clientX - retangulo.left) / retangulo.width));
  const y = Math.max(0, Math.min(1 - altura, (evento.clientY - retangulo.top) / retangulo.height));
  signatario.campos.push({ id: crypto.randomUUID(), tipo, pagina: paginaAtual,
    posicaoX: x, posicaoY: y, largura, altura });
  atualizarSignatarios();
  seletorSignatario.value = signatario.id;
  desenharCampos();
});

function desenharCampos() {
  camadaCampos.replaceChildren();
  for (const signatario of signatarios) {
    for (const campo of signatario.campos.filter(item => item.pagina === paginaAtual)) {
      const caixa = document.createElement('div');
      caixa.className = 'campo-posicionado';
      caixa.dataset.tipo = campo.tipo;
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
      caixa.append(texto, remover);
      caixa.addEventListener('pointerdown', evento => {
        if (evento.target === remover) return;
        evento.preventDefault();
        caixa.setPointerCapture(evento.pointerId);
        const inicioX = evento.clientX;
        const inicioY = evento.clientY;
        const posicaoX = campo.posicaoX;
        const posicaoY = campo.posicaoY;
        const mover = movimento => {
          const retangulo = camadaCampos.getBoundingClientRect();
          campo.posicaoX = Math.max(0, Math.min(1 - campo.largura,
            posicaoX + (movimento.clientX - inicioX) / retangulo.width));
          campo.posicaoY = Math.max(0, Math.min(1 - campo.altura,
            posicaoY + (movimento.clientY - inicioY) / retangulo.height));
          caixa.style.left = `${campo.posicaoX * 100}%`;
          caixa.style.top = `${campo.posicaoY * 100}%`;
        };
        caixa.addEventListener('pointermove', mover);
        caixa.addEventListener('pointerup', () => caixa.removeEventListener('pointermove', mover), { once: true });
      });
      camadaCampos.append(caixa);
    }
  }
}

document.getElementById('criar-envelope').addEventListener('click', async () => {
  erroPreparacao.hidden = true;
  const botao = document.getElementById('criar-envelope');
  try {
    if (!empresa.value.trim() || !arquivoPdf.files[0] || !totalPaginas) {
      throw new Error('Informe empresa e abra o PDF.');
    }
    if (!signatarios.length || signatarios.some(item => !item.nome.trim() ||
      !item.email.trim() || !item.campos.length)) {
      throw new Error('Cada signatário precisa de nome, e-mail e pelo menos um campo.');
    }
    botao.disabled = true;
    const dados = new FormData();
    dados.append('pdf', arquivoPdf.files[0]);
    dados.append('dados', new Blob([JSON.stringify({ nomeEmpresa: empresa.value.trim(),
      signatarios: signatarios.map(item => ({ nome: item.nome.trim(), email: item.email.trim(),
        campos: item.campos.map(({ id, ...campo }) => campo) })) })], { type: 'application/json' }));
    const resposta = await verificarResposta(await fetch('/api/envelopes', {
      method: 'POST', headers: credenciais(), body: dados, cache: 'no-store'
    }));
    const criado = await resposta.json();
    identificadorEnvelope = criado.identificador;
    history.replaceState(null, '', `?envelope=${identificadorEnvelope}`);
    mostrarConvites(criado.convites);
    await atualizarEstado();
  } catch (erro) { mostrarErro(erro.message); botao.disabled = false; }
});

function mostrarConvites(convites) {
  resultadoConvites.hidden = false;
  document.getElementById('identificador-envelope').textContent = `Documento: ${identificadorEnvelope}`;
  const lista = document.getElementById('lista-convites');
  lista.replaceChildren();
  for (const convite of convites) {
    const cartao = document.createElement('div');
    cartao.className = 'cartao-convite';
    const nome = document.createElement('strong');
    nome.textContent = `${convite.nome} · ${convite.email}`;
    const link = document.createElement('a');
    link.href = `${location.origin}/convite/${convite.token}`;
    link.textContent = link.href;
    link.rel = 'noreferrer';
    const copiar = document.createElement('button');
    copiar.type = 'button';
    copiar.textContent = 'COPIAR LINK';
    copiar.addEventListener('click', async () => {
      await navigator.clipboard.writeText(link.href);
      copiar.textContent = 'COPIADO';
    });
    cartao.append(nome, document.createElement('br'), link, document.createElement('br'), copiar);
    lista.append(cartao);
  }
  resultadoConvites.scrollIntoView({ block: 'nearest' });
}

async function atualizarEstado() {
  if (!identificadorEnvelope) return;
  const resposta = await verificarResposta(await fetch(`/api/envelopes/${identificadorEnvelope}`, {
    headers: credenciais(), cache: 'no-store'
  }));
  const estado = await resposta.json();
  empresa.value = estado.nomeEmpresa;
  empresa.dispatchEvent(new Event('input'));
  resultadoConvites.hidden = false;
  document.getElementById('identificador-envelope').textContent = `Documento: ${identificadorEnvelope}`;
  const concluidos = estado.signatarios.filter(item => item.assinadoEm).length;
  document.getElementById('estado-envelope').textContent =
    `${concluidos}/${estado.signatarios.length} signatários concluíram. Prazo: ${new Date(estado.expiraEm).toLocaleString('pt-BR')}.`;
  finalizacao.hidden = concluidos === 0;
  document.getElementById('finalizar-envelope').hidden = concluidos !== estado.signatarios.length || !!estado.finalizadoEm;
  document.getElementById('aviso-finalizacao').textContent = concluidos === estado.signatarios.length
    ? 'Todos assinaram. Finalize com o certificado para proteger o documento.'
    : 'O PDF já contém as assinaturas recebidas. Aguarde os demais signatários para finalizar.';
  const baixar = document.getElementById('baixar-final');
  baixar.hidden = concluidos === 0 && !estado.finalizadoEm;
  baixar.textContent = estado.finalizadoEm ? 'BAIXAR PDF FINAL ↗' : 'BAIXAR PDF ATUAL ↗';
  if (estado.finalizadoEm) {
    document.getElementById('finalizar-envelope').hidden = true;
  }
}

document.getElementById('baixar-final').addEventListener('click', async evento => {
  evento.preventDefault();
  try {
    const respostaPdf = await verificarResposta(await fetch(`/api/envelopes/${identificadorEnvelope}/pdf`, {
      headers: credenciais(), cache: 'no-store'
    }));
    if (enderecoFinal) URL.revokeObjectURL(enderecoFinal);
    enderecoFinal = URL.createObjectURL(await respostaPdf.blob());
    const temporario = document.createElement('a');
    temporario.href = enderecoFinal;
    temporario.download = 'documento-assinado.pdf';
    temporario.click();
  } catch (erro) { mostrarErro(erro.message); }
});

document.getElementById('atualizar-estado').addEventListener('click', () => {
  atualizarEstado().catch(erro => mostrarErro(erro.message));
});
document.getElementById('pagina-final').addEventListener('change', evento => {
  document.getElementById('grupo-imagem-final').hidden = !evento.target.checked;
});
document.getElementById('finalizar-envelope').addEventListener('click', async () => {
  erroPreparacao.hidden = true;
  const botao = document.getElementById('finalizar-envelope');
  try {
    const certificado = document.getElementById('certificado-final').files[0];
    const senha = document.getElementById('senha-final').value;
    if (!certificado || !senha) throw new Error('Informe certificado e senha.');
    const dados = new FormData();
    dados.append('certificado', certificado);
    dados.append('senha', senha);
    dados.append('paginaPersonalizada', String(document.getElementById('pagina-final').checked));
    const imagem = document.getElementById('imagem-final').files[0];
    if (imagem && document.getElementById('pagina-final').checked) dados.append('imagemEmpresa', imagem);
    botao.disabled = true;
    const resposta = await verificarResposta(await fetch(`/api/envelopes/${identificadorEnvelope}/finalizar`, {
      method: 'POST', headers: credenciais(), body: dados, cache: 'no-store'
    }));
    if (enderecoFinal) URL.revokeObjectURL(enderecoFinal);
    enderecoFinal = URL.createObjectURL(await resposta.blob());
    const baixar = document.getElementById('baixar-final');
    baixar.href = enderecoFinal;
    baixar.hidden = false;
    botao.hidden = true;
    document.getElementById('senha-final').value = '';
  } catch (erro) { mostrarErro(erro.message); botao.disabled = false; }
});

if (identificadorEnvelope) {
  resultadoConvites.hidden = false;
  document.getElementById('identificador-envelope').textContent = `Documento: ${identificadorEnvelope}`;
  document.getElementById('estado-envelope').textContent = 'Informe credenciais e atualize o estado.';
}
window.addEventListener('pagehide', () => {
  if (enderecoImagem) URL.revokeObjectURL(enderecoImagem);
  if (enderecoFinal) URL.revokeObjectURL(enderecoFinal);
});
