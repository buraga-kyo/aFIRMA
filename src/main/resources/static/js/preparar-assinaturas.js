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
const signatarios = [{ id: crypto.randomUUID(), nome: '', email: '', cpf: '', rg: '', whatsapp: '', qualificacao: '', campos: [] }];
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

  to.scrollIntoView({ block: 'start', behavior: 'smooth' });
}

document.getElementById('ir-step-2').addEventListener('click', () => {
  erroPreparacao.hidden = true;
  if (!signatarios.length) return mostrarErro('Adicione pelo menos um signatário.');
  if (signatarios.some(s => !s.nome.trim() || !s.email.trim())) return mostrarErro('Preencha nome e e-mail de todos os signatários.');
  changeStep(s1, s2, b1, b2);
});

document.getElementById('voltar-step-1').addEventListener('click', () => {
  changeStep(s2, s1, b2, b1);
});

document.getElementById('ir-step-3').addEventListener('click', () => {
  erroPreparacao.hidden = true;
  if (!empresa.value.trim() || !arquivoPdf.files[0] || !totalPaginas) {
    return mostrarErro('Informe empresa, abra o PDF e posicione os campos.');
  }
  if (signatarios.some(s => s.campos.length === 0)) {
    return mostrarErro('Cada signatário precisa ter pelo menos um campo posicionado.');
  }
  changeStep(s2, s3, b2, b3);
});

document.getElementById('voltar-step-2').addEventListener('click', () => {
  changeStep(s3, s2, b3, b2);
});

function atualizarSignatarios() {
  listaSignatarios.replaceChildren();
  seletorSignatario.replaceChildren();
  for (const signatario of signatarios) {
    const cartao = document.createElement('div');
    cartao.className = 'cartao-signatario cartao-novo-assinante';
    
    // Nome
    const nome = document.createElement('div');
    nome.className = 'campo';
    nome.innerHTML = '<label>NOME</label>';
    const entradaNome = document.createElement('input');
    entradaNome.maxLength = 120;
    entradaNome.value = signatario.nome;
    entradaNome.placeholder = 'Nome do signatário';
    entradaNome.addEventListener('input', () => { signatario.nome = entradaNome.value; atualizarSeletor(); desenharCampos(); });
    nome.append(entradaNome);
    
    // Email
    const email = document.createElement('div');
    email.className = 'campo';
    email.innerHTML = '<label>E-MAIL PARA IDENTIFICAÇÃO</label>';
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
    divCpf.innerHTML = '<label>CPF (Para autenticação)</label>';
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
    divZap.innerHTML = '<label>WHATSAPP (Para notificação)</label>';
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
    entradaQualificacao.placeholder = 'Testemunha, Avalista...';
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

    cartao.append(nome, email, divCpf, divRg, divZap, divQualificacao, rodapeCartao);
    listaSignatarios.append(cartao);
  }
  atualizarSeletor();
}

document.getElementById('adicionar-signatario').addEventListener('click', () => {
  if (signatarios.length >= 20) return mostrarErro('Limite de 20 signatários.');
  const signatario = { id: crypto.randomUUID(), nome: '', email: '', cpf: '', rg: '', whatsapp: '', qualificacao: '', campos: [] };
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
  const nomeAssinatura = document.getElementById('nome-assinatura');
  const loading = document.getElementById('loading-finalizacao');
  const painelConvites = document.getElementById('resultado-convites');
  
  try {
    if (!nomeAssinatura.value.trim()) {
      throw new Error('Informe o nome da assinatura.');
    }
    botao.hidden = true;
    document.getElementById('voltar-step-2').hidden = true;
    loading.hidden = false;

    const dados = new FormData();
    dados.append('pdf', arquivoPdf.files[0]);
    dados.append('dados', new Blob([JSON.stringify({ 
      nomeEmpresa: empresa.value.trim(),
      nomeAssinatura: nomeAssinatura.value.trim(),
      signatarios: signatarios.map(item => ({ 
        nome: item.nome.trim(), 
        email: item.email.trim(),
        cpf: item.cpf ? item.cpf.trim() : null,
        rg: item.rg ? item.rg.trim() : null,
        whatsapp: item.whatsapp ? item.whatsapp.trim() : null,
        qualificacao: item.qualificacao ? item.qualificacao.trim() : null,
        campos: item.campos.map(({ id, ...campo }) => campo) 
      })) 
    })], { type: 'application/json' }));
    
    const resposta = await verificarResposta(await fetch('/api/envelopes', {
      method: 'POST', headers: credenciais(), body: dados, cache: 'no-store'
    }));
    const criado = await resposta.json();
    identificadorEnvelope = criado.identificador;
    
    loading.hidden = true;
    mostrarConvites(criado.convites);
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

window.addEventListener('pagehide', () => {
  if (enderecoImagem) URL.revokeObjectURL(enderecoImagem);
  if (enderecoFinal) URL.revokeObjectURL(enderecoFinal);
});

// Renderiza o signatário inicial
document.addEventListener('DOMContentLoaded', () => {
  atualizarSignatarios();
});
