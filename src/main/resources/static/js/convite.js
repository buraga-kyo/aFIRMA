const conviteToken = location.pathname.split('/').filter(Boolean).at(-1);
const mensagemErro = document.getElementById('erro');
const visualizador = document.getElementById('visualizador');
const imagemPagina = document.getElementById('imagem-pagina');
const camadaCampos = document.getElementById('camada-campos');
let dadosConvite = null;
let paginaAtual = 1;
let totalPaginas = 1;
let enderecoImagem = null;
let enderecoDocumento = null;
let enderecoFinal = null;
let selfieCapturada = null;
let transmissao = null;
const desenhos = new Map();

function erro(mensagem) {
  mensagemErro.textContent = mensagem;
  mensagemErro.hidden = false;
  mensagemErro.scrollIntoView({ block: 'nearest' });
}

async function verificar(resposta) {
  if (resposta.ok) return resposta;
  const dados = await resposta.json().catch(() => null);
  throw new Error(dados?.mensagem || `Operação recusada (${resposta.status}).`);
}

function prepararDesenho(id) {
  const tela = document.getElementById(id);
  const contexto = tela.getContext('2d');
  contexto.fillStyle = 'white';
  contexto.fillRect(0, 0, tela.width, tela.height);
  contexto.strokeStyle = '#0E2117';
  contexto.lineWidth = 4;
  contexto.lineCap = 'round';
  contexto.lineJoin = 'round';
  desenhos.set(id, false);
  let desenhando = false;
  function ponto(evento) {
    const caixa = tela.getBoundingClientRect();
    return { x: (evento.clientX - caixa.left) * tela.width / caixa.width,
      y: (evento.clientY - caixa.top) * tela.height / caixa.height };
  }
  tela.addEventListener('pointerdown', evento => {
    evento.preventDefault();
    desenhando = true;
    tela.setPointerCapture(evento.pointerId);
    const inicio = ponto(evento);
    contexto.beginPath();
    contexto.moveTo(inicio.x, inicio.y);
    contexto.lineTo(inicio.x + 0.1, inicio.y + 0.1);
    contexto.stroke();
    desenhos.set(id, true);
  });
  tela.addEventListener('pointermove', evento => {
    if (!desenhando) return;
    const atual = ponto(evento);
    contexto.lineTo(atual.x, atual.y);
    contexto.stroke();
  });
  tela.addEventListener('pointerup', () => { desenhando = false; });
  tela.addEventListener('pointercancel', () => { desenhando = false; });
}

for (const id of ['desenho-assinatura', 'desenho-rubrica']) prepararDesenho(id);
for (const botao of document.querySelectorAll('[data-limpar]')) {
  botao.addEventListener('click', () => {
    const id = botao.dataset.limpar;
    const tela = document.getElementById(id);
    const contexto = tela.getContext('2d');
    contexto.fillStyle = 'white';
    contexto.fillRect(0, 0, tela.width, tela.height);
    desenhos.set(id, false);
  });
}

function blobDesenho(id) {
  return new Promise(resolver => document.getElementById(id).toBlob(resolver, 'image/png'));
}

async function mostrarPagina() {
  const resposta = await verificar(await fetch(`/api/convites/${conviteToken}/paginas/${paginaAtual}`, {
    cache: 'no-store'
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
  camadaCampos.replaceChildren();
  for (const campo of dadosConvite.campos.filter(item => item.pagina === paginaAtual)) {
    const caixa = document.createElement('div');
    caixa.className = 'campo-posicionado';
    caixa.dataset.tipo = campo.tipo;
    caixa.style.left = `${campo.posicaoX * 100}%`;
    caixa.style.top = `${campo.posicaoY * 100}%`;
    caixa.style.width = `${campo.largura * 100}%`;
    caixa.style.height = `${campo.altura * 100}%`;
    caixa.textContent = `${campo.tipo.toLowerCase()} · ${dadosConvite.nomeSignatario}`;
    camadaCampos.append(caixa);
  }
}

async function atualizarDocumento() {
  const resposta = await verificar(await fetch(`/api/convites/${conviteToken}/pdf`, { cache: 'no-store' }));
  const novoEndereco = URL.createObjectURL(await resposta.blob());
  if (enderecoDocumento) URL.revokeObjectURL(enderecoDocumento);
  enderecoDocumento = novoEndereco;
  document.getElementById('baixar-original').href = enderecoDocumento;
  document.getElementById('baixar-original').download = dadosConvite.nomeDocumento;
}

document.getElementById('pagina-anterior').addEventListener('click', () => {
  if (paginaAtual > 1) { paginaAtual--; mostrarPagina().catch(falha => erro(falha.message)); }
});
document.getElementById('pagina-seguinte').addEventListener('click', () => {
  if (paginaAtual < totalPaginas) { paginaAtual++; mostrarPagina().catch(falha => erro(falha.message)); }
});

document.getElementById('abrir-camera').addEventListener('click', async () => {
  try {
    transmissao = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'user' }, audio: false });
    const video = document.getElementById('video-selfie');
    video.srcObject = transmissao;
    video.hidden = false;
    document.getElementById('capturar-selfie').hidden = false;
  } catch { erro('Câmera indisponível. Escolha arquivo de imagem.'); }
});
document.getElementById('capturar-selfie').addEventListener('click', async () => {
  const video = document.getElementById('video-selfie');
  if (!video.videoWidth) return erro('Aguarde a câmera carregar.');
  const tela = document.createElement('canvas');
  const escala = Math.min(1, 1280 / video.videoWidth);
  tela.width = Math.round(video.videoWidth * escala);
  tela.height = Math.round(video.videoHeight * escala);
  tela.getContext('2d').drawImage(video, 0, 0, tela.width, tela.height);
  selfieCapturada = await new Promise(resolver => tela.toBlob(resolver, 'image/jpeg', 0.85));
  const previa = document.getElementById('previa-selfie');
  previa.src = URL.createObjectURL(selfieCapturada);
  previa.hidden = false;
  transmissao.getTracks().forEach(trilha => trilha.stop());
  transmissao = null;
  video.hidden = true;
  document.getElementById('capturar-selfie').hidden = true;
});
document.getElementById('arquivo-selfie').addEventListener('change', () => {
  selfieCapturada = null;
  const imagem = document.getElementById('arquivo-selfie').files[0];
  const previa = document.getElementById('previa-selfie');
  if (imagem) { previa.src = URL.createObjectURL(imagem); previa.hidden = false; }
  else previa.hidden = true;
});

document.getElementById('enviar-assinatura').addEventListener('click', async () => {
  mensagemErro.hidden = true;
  const botao = document.getElementById('enviar-assinatura');
  try {
    if (!document.getElementById('aceite').checked) throw new Error('Confirme leitura e aceite do documento.');
    const tipos = new Set(dadosConvite.campos.map(campo => campo.tipo));
    if (tipos.has('ASSINATURA') && !desenhos.get('desenho-assinatura')) throw new Error('Desenhe sua assinatura.');
    if (tipos.has('RUBRICA') && !desenhos.get('desenho-rubrica')) throw new Error('Desenhe sua rubrica.');
    const selfie = selfieCapturada || document.getElementById('arquivo-selfie').files[0];
    if (tipos.has('SELFIE') && !selfie) throw new Error('Envie sua selfie.');
    const dados = new FormData();
    dados.append('aceite', 'true');
    if (tipos.has('ASSINATURA')) dados.append('assinatura', await blobDesenho('desenho-assinatura'), 'assinatura.png');
    if (tipos.has('RUBRICA')) dados.append('rubrica', await blobDesenho('desenho-rubrica'), 'rubrica.png');
    if (tipos.has('SELFIE')) dados.append('selfie', selfie, selfie.name || 'selfie.jpg');
    botao.disabled = true;
    await verificar(await fetch(`/api/convites/${conviteToken}/assinar`, {
      method: 'POST', body: dados, cache: 'no-store'
    }));
    document.getElementById('area-assinatura').hidden = true;
    document.getElementById('resultado').hidden = false;
    document.getElementById('resultado').scrollIntoView({ block: 'nearest' });
    await Promise.all([mostrarPagina(), atualizarDocumento()]);
  } catch (falha) { erro(falha.message); botao.disabled = false; }
});

async function carregar() {
  const resposta = await verificar(await fetch(`/api/convites/${conviteToken}`, { cache: 'no-store' }));
  dadosConvite = await resposta.json();
  document.getElementById('marca-empresa').firstChild.textContent = dadosConvite.nomeEmpresa;
  document.getElementById('rodape-empresa').textContent = dadosConvite.nomeEmpresa;
  document.getElementById('saudacao').textContent = `${dadosConvite.nomeSignatario}, leia ${dadosConvite.nomeDocumento} antes de assinar.`;
  totalPaginas = dadosConvite.paginas;
  const tipos = new Set(dadosConvite.campos.map(campo => campo.tipo));
  document.getElementById('grupo-assinatura').hidden = !tipos.has('ASSINATURA');
  document.getElementById('grupo-rubrica').hidden = !tipos.has('RUBRICA');
  document.getElementById('grupo-selfie').hidden = !tipos.has('SELFIE');
  document.getElementById('area-assinatura').hidden = dadosConvite.assinado;
  document.getElementById('resultado').hidden = !dadosConvite.assinado;
  if (dadosConvite.finalizado) {
    document.getElementById('estado-final').textContent = 'PDF finalizado e disponível para baixar.';
    document.getElementById('baixar-final').hidden = false;
  }
  await mostrarPagina();
  await atualizarDocumento();
}

document.getElementById('baixar-final').addEventListener('click', async evento => {
  evento.preventDefault();
  try {
    const resposta = await verificar(await fetch(`/api/convites/${conviteToken}/final`, { cache: 'no-store' }));
    if (enderecoFinal) URL.revokeObjectURL(enderecoFinal);
    enderecoFinal = URL.createObjectURL(await resposta.blob());
    const link = document.createElement('a');
    link.href = enderecoFinal;
    link.download = 'documento-assinado.pdf';
    link.click();
  } catch (falha) { erro(falha.message); }
});

carregar().catch(falha => erro(falha.message));
window.addEventListener('pagehide', () => {
  if (enderecoImagem) URL.revokeObjectURL(enderecoImagem);
  if (enderecoDocumento) URL.revokeObjectURL(enderecoDocumento);
  if (enderecoFinal) URL.revokeObjectURL(enderecoFinal);
  if (transmissao) transmissao.getTracks().forEach(trilha => trilha.stop());
});
