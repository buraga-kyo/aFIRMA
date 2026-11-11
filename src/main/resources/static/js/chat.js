const porId = seletor => document.querySelector(seletor);
let token = '';
let chave = '';
let selecionado = null;
let fila = [];
function aviso(texto) {
  porId('#aviso').textContent = texto || '';
}
async function api(caminho, metodo = 'GET', dados) {
  const resposta = await fetch('/api/chat' + caminho, {
    method: metodo,
    headers: {
      Authorization: `Bearer ${token}`,
      'X-API-Key': chave,
      ...(dados ? {
        'Content-Type': 'application/json'
      } : {})
    },
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
  const lista = porId('#fila');
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
    porId('#titulo-conversa').textContent = 'Selecione atendimento';
    porId('#mensagens').replaceChildren();
  }
}
async function atualizarCanaisDisponiveis() {
  const canais = await api('/canais');
  const seletor = porId('#form-novo-atendimento select[name="canal"]');
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
  porId('#titulo-conversa').textContent = atendimento?.contato_nome || atendimento?.telefone || 'Atendimento';
  porId('#estado').textContent = atendimento?.estado || '';
  porId('#assumir').disabled = atendimento?.estado !== 'PENDENTE';
  porId('#transferir').disabled = !atendimento;
  porId('#finalizar').disabled = !atendimento;
  porId('#texto').disabled = atendimento?.estado !== 'EM_ANDAMENTO';
  porId('#enviar').disabled = atendimento?.estado !== 'EM_ANDAMENTO';
  const mensagens = await api(`/atendimentos/${id}/mensagens`);
  const painel = porId('#mensagens');
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
    const [canais, atendentes, setores] = await Promise.all([api('/admin/canais'), api('/admin/atendentes'), api('/admin/setores')]);
    porId('#gestao').hidden = false;
    const listaCanais = porId('#canais');
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
              porId('#qr-imagem').replaceChildren(qr);
              porId('#qr').hidden = false;
            } catch (erro) {
              aviso(erro.message);
            }
          }, 2500);
        } catch (erro) {
          aviso(erro.message);
        }
      });
      item.append(conectar);
      listaCanais.append(item);
    }
    const listaAtendentes = porId('#atendentes');
    listaAtendentes.replaceChildren(...atendentes.map(registro => elemento('li', `${registro.id} · ${registro.nome} · ${registro.disponibilidade}`)));
    const listaSetores = porId('#setores');
    listaSetores.replaceChildren(...setores.map(itemAtual => elemento('li', `${itemAtual.id} · ${itemAtual.nome}`)));
  } catch (erro) {
    if (erro.message.includes('403')) porId('#gestao').hidden = true;else aviso(erro.message);
  }
}
async function executar(operacao) {
  try {
    await operacao();
    aviso('Operação concluída.');
  } catch (erro) {
    aviso(erro.message);
  }
}
porId('#entrar').addEventListener('click', () => executar(async () => {
  token = porId('#token').value.trim();
  chave = porId('#chave').value.trim();
  if (!token || !chave) throw new Error('Informe token e chave de API.');
  await atualizarFila();
  await atualizarCanaisDisponiveis();
  porId('#aplicacao').hidden = false;
  await atualizarGestao();
}));
if (document.body.dataset.tokenTeste) {
  porId('#teste').hidden = false;
  porId('#teste').addEventListener('click', () => {
    porId('#token').value = document.body.dataset.tokenTeste;
    porId('#chave').value = document.body.dataset.chaveTeste;
    porId('#entrar').click();
  });
}
porId('#atualizar').addEventListener('click', () => executar(atualizarFila));
porId('#assumir').addEventListener('click', () => executar(async () => {
  await api(`/atendimentos/${selecionado}/assumir`, 'POST');
  await atualizarFila();
  await abrir(selecionado);
}));
porId('#finalizar').addEventListener('click', () => executar(async () => {
  await api(`/atendimentos/${selecionado}/finalizar`, 'POST');
  await atualizarFila();
}));
porId('#transferir').addEventListener('click', () => executar(async () => {
  const setor = prompt('ID do setor de destino (vazio = fila geral):');
  if (setor === null) return;
  const atendente = prompt('ID do atendente de destino (vazio = fila):');
  if (atendente === null) return;
  await api(`/atendimentos/${selecionado}/transferir`, 'POST', {
    setorId: setor ? Number(setor) : null,
    atendenteId: atendente ? Number(atendente) : null
  });
  await atualizarFila();
}));
porId('#form-mensagem').addEventListener('submit', evento => {
  evento.preventDefault();
  executar(async () => {
    await api(`/atendimentos/${selecionado}/mensagens`, 'POST', {
      texto: porId('#texto').value
    });
    porId('#texto').value = '';
    await abrir(selecionado);
  });
});
porId('#form-novo-atendimento').addEventListener('submit', evento => {
  evento.preventDefault();
  executar(async () => {
    const formulario = evento.target;
    const criado = await api('/atendimentos', 'POST', {
      canalId: formulario.elements.namedItem('canal').value,
      telefone: formulario.elements.namedItem('telefone').value,
      nome: formulario.elements.namedItem('nome').value,
      texto: formulario.elements.namedItem('texto').value
    });
    formulario.reset();
    await atualizarFila();
    await abrir(criado.id);
  });
});
porId('#form-setor').addEventListener('submit', evento => {
  evento.preventDefault();
  executar(async () => {
    await api('/admin/setores', 'POST', {
      nome: evento.target.elements.namedItem('nome').value
    });
    evento.target.reset();
    await atualizarGestao();
  });
});
porId('#form-atendente').addEventListener('submit', evento => {
  evento.preventDefault();
  executar(async () => {
    const formulario = evento.target;
    const setor = formulario.elements.namedItem('setor').value;
    await api('/admin/atendentes', 'POST', {
      identidade: formulario.elements.namedItem('identidade').value,
      nome: formulario.elements.namedItem('nome').value,
      setores: setor ? [Number(setor)] : []
    });
    formulario.reset();
    await atualizarGestao();
  });
});
porId('#form-canal').addEventListener('submit', evento => {
  evento.preventDefault();
  executar(async () => {
    const formulario = evento.target;
    const setor = formulario.elements.namedItem('setor').value;
    await api('/admin/canais', 'POST', {
      nome: formulario.elements.namedItem('nome').value,
      telefone: formulario.elements.namedItem('telefone').value,
      setorPadraoId: setor ? Number(setor) : null
    });
    formulario.reset();
    await atualizarGestao();
    await atualizarCanaisDisponiveis();
  });
});
setInterval(() => {
  if (!token || document.hidden) return;
  atualizarFila().then(() => selecionado && abrir(selecionado)).catch(erro => aviso(erro.message));
}, 10000);

document.addEventListener('firma:acesso-conectado', () => porId('#entrar').click());
document.addEventListener('firma:desconectado', () => {
  token = ''; chave = ''; porId('#aplicacao').hidden = true;
});
