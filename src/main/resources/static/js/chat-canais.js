const buscar = seletor => document.querySelector(seletor);
let token = '';
let chave = '';
let canalQr = null;
let canaisAtuais = [];
const selecaoCanais = new Set();

function avisar(mensagem) { buscar('#aviso').textContent = mensagem || ''; }

async function requisitar(caminho, metodo = 'GET', dados) {
  const resposta = await fetch(`/api/chat${caminho}`, {
    method: metodo,
    headers: {
      Authorization: `Bearer ${token}`,
      'X-API-Key': chave,
      ...(dados ? { 'Content-Type': 'application/json' } : {})
    },
    body: dados ? JSON.stringify(dados) : undefined
  });
  if (!resposta.ok) {
    const erro = await resposta.json().catch(() => ({}));
    throw new Error(erro.message || erro.mensagem || `Erro HTTP ${resposta.status}`);
  }
  const texto = await resposta.text();
  return texto ? JSON.parse(texto) : null;
}

function botao(rotulo, acao, secundario = false) {
  const elemento = document.createElement('button');
  elemento.type = 'button';
  elemento.textContent = rotulo;
  if (secundario) elemento.className = 'secundario';
  elemento.addEventListener('click', async () => {
    elemento.disabled = true;
    try { await acao(); } catch (erro) { avisar(erro.message); }
    finally { elemento.disabled = false; }
  });
  return elemento;
}

async function mostrarQr() {
  if (!canalQr) return;
  const imagem = buscar('#imagem-qr');
  const estado = buscar('#estado-qr');
  try {
    const resultado = await requisitar(`/admin/canais/${canalQr}/qr`);
    if (!resultado.qr || !resultado.qr.startsWith('data:image/')) throw new Error('QR ainda indisponível. Atualize em alguns segundos.');
    imagem.src = resultado.qr;
    imagem.hidden = false;
    estado.textContent = 'Escaneie o QR no WhatsApp.';
  } catch (erro) {
    imagem.hidden = true;
    estado.textContent = erro.message;
  }
}

function desenharCanais() {
  const corpo = buscar('#canais tbody');
  const busca = buscar('#busca-canais').value.trim().toLocaleLowerCase('pt-BR');
  const canais = canaisAtuais.filter(canal => !busca ||
    [canal.nome, canal.telefone, canal.conexao, canal.provedor,
      canal.ativo ? 'ativo' : 'inativo'].some(valor => String(valor || '').toLocaleLowerCase('pt-BR').includes(busca)));
  corpo.replaceChildren();
  for (const canal of canais) {
    const linha = document.createElement('tr');
    if (selecaoCanais.has(canal.id)) linha.className = 'selecionada';
    const selecionar = document.createElement('td');
    selecionar.className = 'selecao';
    const caixa = document.createElement('input');
    caixa.type = 'checkbox';
    caixa.checked = selecaoCanais.has(canal.id);
    caixa.setAttribute('aria-label', `Selecionar ${canal.nome}`);
    caixa.addEventListener('change', () => {
      if (caixa.checked) selecaoCanais.add(canal.id); else selecaoCanais.delete(canal.id);
      linha.classList.toggle('selecionada', caixa.checked);
      atualizarSelecaoCanais();
    });
    selecionar.append(caixa);
    linha.append(selecionar);
    const nome = document.createElement('td');
    const titulo = document.createElement('strong');
    titulo.textContent = canal.nome;
    const detalhe = document.createElement('small');
    detalhe.textContent = canal.provedor === 'META' ? 'Canal oficial' : 'Canal conectado por QR';
    nome.append(titulo, detalhe);
    linha.append(nome);
    const telefone = document.createElement('td');
    telefone.textContent = canal.telefone;
    linha.append(telefone);
    for (const [texto, positivo] of [[canal.conexao, canal.conexao === 'CONECTADO'],
      [canal.ativo ? 'Ativo' : 'Inativo', canal.ativo]]) {
      const celula = document.createElement('td');
      const estado = document.createElement('span');
      estado.className = `estado ${positivo ? 'positivo' : 'alerta'}`;
      estado.textContent = texto;
      celula.append(estado);
      linha.append(celula);
    }
    const acoes = document.createElement('td');
    const conjunto = document.createElement('div');
    conjunto.className = 'acoes-linha';
    if (canal.provedor !== 'META') {
      conjunto.append(botao('Conectar / QR', async () => {
        await requisitar(`/admin/canais/${canal.id}/conectar`, 'POST');
        canalQr = canal.id;
        buscar('#painel-qr').hidden = false;
        buscar('#estado-qr').textContent = 'Conectando. Aguarde QR.';
        await mostrarQr();
        await carregar();
      }));
      conjunto.append(botao('Desconectar', async () => {
        await requisitar(`/admin/canais/${canal.id}/desconectar`, 'POST');
        await carregar();
      }, true));
    }
    const excluir = botao('Excluir', async () => {
      if (!window.confirm(`Excluir o canal ${canal.nome}? O histórico será preservado.`)) return;
      await requisitar(`/admin/canais/${canal.id}`, 'DELETE');
      if (canalQr === canal.id) { canalQr = null; buscar('#painel-qr').hidden = true; }
      await carregar();
      avisar('Canal excluído. Histórico preservado.');
    }, true);
    conjunto.append(excluir);
    acoes.append(conjunto);
    linha.append(acoes);
    corpo.append(linha);
  }
  if (!canais.length) {
    const linha = document.createElement('tr');
    const vazio = document.createElement('td');
    vazio.colSpan = 6;
    vazio.className = 'vazio-tabela';
    vazio.textContent = busca ? 'Nenhum canal corresponde à busca.' : 'Nenhum canal cadastrado.';
    linha.append(vazio);
    corpo.append(linha);
  }
  buscar('#resumo-canais').textContent = `${canais.length} de ${canaisAtuais.length} canal(is)`;
  atualizarSelecaoCanais();
}

function atualizarSelecaoCanais() {
  buscar('#selecionados-canais').textContent = selecaoCanais.size ? `${selecaoCanais.size} selecionado(s)` : '';
  buscar('#exportar-canais').disabled = selecaoCanais.size === 0;
  const caixas = [...buscar('#canais tbody').querySelectorAll('input[type=checkbox]')];
  const todos = buscar('#selecionar-todos-canais');
  todos.checked = caixas.length > 0 && caixas.every(caixa => caixa.checked);
  todos.indeterminate = caixas.some(caixa => caixa.checked) && !todos.checked;
}

async function carregar() {
  const [canais, setores] = await Promise.all([
    requisitar('/admin/canais'), requisitar('/admin/setores')
  ]);
  canaisAtuais = canais;
  selecaoCanais.clear();
  desenharCanais();
  const seletor = buscar('#form-canal select[name="setor"]');
  seletor.replaceChildren(new Option('Sem setor padrão', ''));
  for (const setor of setores.filter(item => item.ativo)) {
    seletor.add(new Option(setor.nome, setor.id));
  }
  buscar('#gestao').hidden = false;
  buscar('#acesso').hidden = true;
  avisar('');
}

buscar('#form-acesso').addEventListener('submit', async evento => {
  evento.preventDefault();
  const formulario = evento.currentTarget;
  token = formulario.elements.namedItem('token').value.trim();
  chave = formulario.elements.namedItem('chave').value.trim();
  try { await carregar(); } catch (erro) { avisar(erro.message); }
});

buscar('#form-canal').addEventListener('submit', async evento => {
  evento.preventDefault();
  const formulario = evento.currentTarget;
  const setor = formulario.elements.namedItem('setor').value;
  try {
    await requisitar('/admin/canais', 'POST', {
      nome: formulario.elements.namedItem('nome').value.trim(),
      telefone: formulario.elements.namedItem('telefone').value.trim(),
      setorPadraoId: setor ? Number(setor) : null
    });
    formulario.reset();
    buscar('#painel-cadastro-canal').hidden = true;
    await carregar();
    avisar('Canal criado na aFIRMA. Conecte para obter o QR.');
  } catch (erro) { avisar(erro.message); }
});

buscar('#atualizar').addEventListener('click', () => carregar().catch(erro => avisar(erro.message)));
buscar('#atualizar-qr').addEventListener('click', mostrarQr);
buscar('#busca-canais').addEventListener('input', desenharCanais);
buscar('#selecionar-todos-canais').addEventListener('change', evento => {
  const busca = buscar('#busca-canais').value.trim().toLocaleLowerCase('pt-BR');
  for (const canal of canaisAtuais.filter(item => !busca ||
    [item.nome,item.telefone,item.conexao,item.provedor].some(valor => String(valor || '').toLocaleLowerCase('pt-BR').includes(busca)))) {
    if (evento.target.checked) selecaoCanais.add(canal.id); else selecaoCanais.delete(canal.id);
  }
  desenharCanais();
});
buscar('#novo-canal').addEventListener('click', () => {
  buscar('#painel-cadastro-canal').hidden = false;
  buscar('#painel-cadastro-canal').scrollIntoView({behavior:'smooth',block:'start'});
});
buscar('#fechar-canal').addEventListener('click', () => { buscar('#painel-cadastro-canal').hidden = true; });
buscar('#exportar-canais').addEventListener('click', () => {
  const registros = canaisAtuais.filter(canal => selecaoCanais.has(canal.id));
  if (!registros.length) return;
  const escapar = valor => '"' + String(valor ?? '').replaceAll('"','""') + '"';
  const conteudo = ['Canal;Telefone;Conexão;Situação',
    ...registros.map(canal => [canal.nome,canal.telefone,canal.conexao,canal.ativo ? 'Ativo' : 'Inativo']
      .map(escapar).join(';'))].join('\n');
  const endereco = URL.createObjectURL(new Blob(['\ufeff',conteudo],{type:'text/csv;charset=utf-8'}));
  const link = document.createElement('a');
  link.href = endereco;
  link.download = 'firma-canais.csv';
  link.click();
  setTimeout(() => URL.revokeObjectURL(endereco),1000);
});

if (document.body.dataset.tokenTeste && document.body.dataset.chaveTeste) {
  const teste = buscar('#teste');
  teste.hidden = false;
  teste.addEventListener('click', () => {
    buscar('#form-acesso input[name="token"]').value = document.body.dataset.tokenTeste;
    buscar('#form-acesso input[name="chave"]').value = document.body.dataset.chaveTeste;
    buscar('#form-acesso').requestSubmit();
  });
}
