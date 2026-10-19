const $ = seletor => document.querySelector(seletor);
let token = '';
let chave = '';
let selecionado = null;
let fila = [];

function aviso(texto) { $('#aviso').textContent = texto || ''; }

async function api(caminho, metodo = 'GET', dados) {
  const resposta = await fetch('/api/chat' + caminho, {
    method: metodo,
    headers: { Authorization: `Bearer ${token}`, 'X-API-Key': chave,
      ...(dados ? { 'Content-Type': 'application/json' } : {}) },
    body: dados ? JSON.stringify(dados) : undefined
  });
  if (!resposta.ok) {
    const erro = await resposta.json().catch(() => ({}));
    throw new Error(erro.mensagem || `Erro HTTP ${resposta.status}`);
  }
  const texto = await resposta.text();
  return texto ? JSON.parse(texto) : null;
}

function elemento(tag, texto, classe) {
  const item = document.createElement(tag);
  item.textContent = texto;
  if (classe) item.className = classe;
  return item;
}

async function atualizarFila() {
  fila = await api('/atendimentos');
  const lista = $('#fila');
  lista.replaceChildren();
  for (const atendimento of fila) {
    const item = elemento('li', '');
    const botao = elemento('button', atendimento.contato_nome || atendimento.telefone);
    botao.addEventListener('click', () => abrir(atendimento.id));
    item.append(botao, elemento('small', `${atendimento.telefone} · ${atendimento.estado}`));
    lista.append(item);
  }
  if (!fila.length) lista.append(elemento('li', 'Sem atendimentos abertos.'));
  if (selecionado && !fila.some(item => item.id === selecionado)) {
    selecionado = null;
    $('#titulo-conversa').textContent = 'Selecione atendimento';
    $('#mensagens').replaceChildren();
  }
}

async function atualizarCanaisDisponiveis() {
  const canais = await api('/canais');
  const seletor = $('#form-novo-atendimento select[name="canal"]');
  seletor.replaceChildren();
  for (const canal of canais) {
    const opcao = elemento('option', `${canal.nome} · ${canal.telefone} · ${canal.conexao}`);
    opcao.value = canal.id;
    seletor.append(opcao);
  }
}

async function abrir(id) {
  selecionado = id;
  const atendimento = fila.find(item => item.id === id);
  $('#titulo-conversa').textContent = atendimento?.contato_nome || atendimento?.telefone || 'Atendimento';
  $('#estado').textContent = atendimento?.estado || '';
  $('#assumir').disabled = atendimento?.estado !== 'PENDENTE';
  $('#transferir').disabled = !atendimento;
  $('#finalizar').disabled = !atendimento;
  $('#texto').disabled = atendimento?.estado !== 'EM_ANDAMENTO';
  $('#enviar').disabled = atendimento?.estado !== 'EM_ANDAMENTO';
  const mensagens = await api(`/atendimentos/${id}/mensagens`);
  const painel = $('#mensagens');
  painel.replaceChildren();
  for (const mensagem of mensagens.reverse()) {
    const bolha = elemento('div', mensagem.texto, `bolha ${mensagem.sentido === 'SAIDA' ? 'saida' : 'entrada'}`);
    bolha.append(elemento('small', `${mensagem.estado} · ${new Date(mensagem.criado_em).toLocaleString('pt-BR')}`));
    painel.append(bolha);
  }
  painel.scrollTop = painel.scrollHeight;
}

async function atualizarGestao() {
  try {
    const [canais, atendentes, setores] = await Promise.all([
      api('/admin/canais'), api('/admin/atendentes'), api('/admin/setores')
    ]);
    $('#gestao').hidden = false;
    const listaCanais = $('#canais');
    listaCanais.replaceChildren();
    for (const canal of canais) {
      const item = elemento('li', `${canal.nome} · ${canal.telefone} · ${canal.conexao}`);
      const conectar = elemento('button', 'Conectar / QR');
      conectar.addEventListener('click', async () => {
        try {
          await api(`/admin/canais/${canal.id}/conectar`, 'POST');
          aviso('Canal conectando. Aguarde QR.');
          setTimeout(async () => {
            try {
              const imagem = await api(`/admin/canais/${canal.id}/qr`);
              const qr = document.createElement('img');
              qr.src = imagem.qr;
              qr.alt = `QR do canal ${canal.nome}`;
              $('#qr-imagem').replaceChildren(qr);
              $('#qr').hidden = false;
            } catch (erro) { aviso(erro.message); }
          }, 2500);
        } catch (erro) { aviso(erro.message); }
      });
      item.append(conectar);
      listaCanais.append(item);
    }
    const listaAtendentes = $('#atendentes');
    listaAtendentes.replaceChildren(...atendentes.map(a => elemento('li', `${a.id} · ${a.nome} · ${a.disponibilidade}`)));
    const listaSetores = $('#setores');
    listaSetores.replaceChildren(...setores.map(s => elemento('li', `${s.id} · ${s.nome}`)));
  } catch (erro) {
    if (erro.message.includes('403')) $('#gestao').hidden = true;
    else aviso(erro.message);
  }
}

async function executar(operacao) {
  try { await operacao(); aviso('Operação concluída.'); }
  catch (erro) { aviso(erro.message); }
}

$('#entrar').addEventListener('click', () => executar(async () => {
  token = $('#token').value.trim();
  chave = $('#chave').value.trim();
  if (!token || !chave) throw new Error('Informe token e chave de API.');
  await atualizarFila();
  await atualizarCanaisDisponiveis();
  $('#aplicacao').hidden = false;
  await atualizarGestao();
}));

if (document.body.dataset.tokenTeste) {
  $('#teste').hidden = false;
  $('#teste').addEventListener('click', () => {
    $('#token').value = document.body.dataset.tokenTeste;
    $('#chave').value = document.body.dataset.chaveTeste;
    $('#entrar').click();
  });
}

$('#atualizar').addEventListener('click', () => executar(atualizarFila));
$('#assumir').addEventListener('click', () => executar(async () => {
  await api(`/atendimentos/${selecionado}/assumir`, 'POST');
  await atualizarFila(); await abrir(selecionado);
}));
$('#finalizar').addEventListener('click', () => executar(async () => {
  await api(`/atendimentos/${selecionado}/finalizar`, 'POST');
  await atualizarFila();
}));
$('#transferir').addEventListener('click', () => executar(async () => {
  const setor = prompt('ID do setor de destino (vazio = fila geral):');
  if (setor === null) return;
  const atendente = prompt('ID do atendente de destino (vazio = fila):');
  if (atendente === null) return;
  await api(`/atendimentos/${selecionado}/transferir`, 'POST', {
    setorId: setor ? Number(setor) : null, atendenteId: atendente ? Number(atendente) : null
  });
  await atualizarFila();
}));
$('#form-mensagem').addEventListener('submit', evento => {
  evento.preventDefault();
  executar(async () => {
    await api(`/atendimentos/${selecionado}/mensagens`, 'POST', { texto: $('#texto').value });
    $('#texto').value = '';
    await abrir(selecionado);
  });
});
$('#form-novo-atendimento').addEventListener('submit', evento => {
  evento.preventDefault();
  executar(async () => {
    const form = evento.target;
    const criado = await api('/atendimentos', 'POST', {
      canalId: form.elements.namedItem('canal').value,
      telefone: form.elements.namedItem('telefone').value,
      nome: form.elements.namedItem('nome').value,
      texto: form.elements.namedItem('texto').value
    });
    form.reset();
    await atualizarFila();
    await abrir(criado.id);
  });
});
$('#form-setor').addEventListener('submit', evento => {
  evento.preventDefault();
  executar(async () => {
    await api('/admin/setores', 'POST', { nome: evento.target.elements.namedItem('nome').value });
    evento.target.reset(); await atualizarGestao();
  });
});
$('#form-atendente').addEventListener('submit', evento => {
  evento.preventDefault();
  executar(async () => {
    const form = evento.target;
    const setor = form.elements.namedItem('setor').value;
    await api('/admin/atendentes', 'POST', {
      identidade: form.elements.namedItem('identidade').value,
      nome: form.elements.namedItem('nome').value,
      setores: setor ? [Number(setor)] : []
    });
    form.reset(); await atualizarGestao();
  });
});
$('#form-canal').addEventListener('submit', evento => {
  evento.preventDefault();
  executar(async () => {
    const form = evento.target;
    const setor = form.elements.namedItem('setor').value;
    await api('/admin/canais', 'POST', {
      nome: form.elements.namedItem('nome').value,
      telefone: form.elements.namedItem('telefone').value,
      setorPadraoId: setor ? Number(setor) : null
    });
    form.reset(); await atualizarGestao();
    await atualizarCanaisDisponiveis();
  });
});
setInterval(() => {
  if (!token || document.hidden) return;
  atualizarFila().then(() => selecionado && abrir(selecionado)).catch(erro => aviso(erro.message));
}, 10000);
