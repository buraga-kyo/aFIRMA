const formulario = document.getElementById('formulario-proposta');
const catalogo = document.getElementById('catalogo');
const escolhidos = document.getElementById('servicos-escolhidos');
const previa = document.getElementById('previa-documento');
const erro = document.getElementById('erro-advai');
const servicos = [];

function valor(id) { return document.getElementById(id).value.trim(); }
function paragrafo(pai, texto, tag = 'p') {
  const elemento = document.createElement(tag);
  elemento.textContent = texto;
  pai.append(elemento);
  return elemento;
}
function mostrarErro(mensagem) {
  erro.textContent = mensagem;
  erro.hidden = false;
  erro.scrollIntoView({ block: 'nearest' });
}
function atualizarPrevia() {
  const empresa = valor('empresa') || 'SUA EMPRESA';
  document.getElementById('rodape-empresa').textContent = empresa;
  previa.replaceChildren();
  paragrafo(previa, empresa, 'h2');
  paragrafo(previa, 'PROPOSTA DE SERVIÇOS JURÍDICOS');
  paragrafo(previa, `Cliente: ${valor('cliente') || '—'} · ${valor('email') || '—'}`);
  paragrafo(previa, valor('titulo') || 'Proposta', 'h3');
  paragrafo(previa, valor('introducao'));
  paragrafo(previa, 'SERVIÇOS', 'h3');
  for (const servico of servicos) {
    paragrafo(previa, servico.titulo, 'h3');
    paragrafo(previa, servico.descricao);
  }
  paragrafo(previa, 'ESCOPO E ENTREGAS', 'h3');
  paragrafo(previa, valor('escopo'));
  paragrafo(previa, 'HONORÁRIOS', 'h3');
  const valorTotal = Number(valor('valor'));
  const desconto = Number(valor('desconto'));
  if (Number.isFinite(valorTotal) && valorTotal > 0) {
    const moeda = numero => numero.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
    paragrafo(previa, `Valor: ${moeda(valorTotal)}`);
    if (desconto > 0 && desconto <= 100) paragrafo(previa, `À vista: ${moeda(valorTotal * (1 - desconto / 100))} (${desconto}% de desconto).`);
  }
  paragrafo(previa, 'PRAZO', 'h3');
  paragrafo(previa, valor('prazo'));
  paragrafo(previa, 'CONDIÇÕES', 'h3');
  paragrafo(previa, valor('condicoes'));
}

function atualizarServicos() {
  escolhidos.replaceChildren();
  for (const servico of servicos) {
    const cartao = document.createElement('div');
    cartao.className = 'advai-servico';
    const titulo = document.createElement('input');
    titulo.value = servico.titulo;
    titulo.maxLength = 160;
    titulo.setAttribute('aria-label', 'Título do serviço');
    titulo.addEventListener('input', () => { servico.titulo = titulo.value; atualizarPrevia(); });
    const descricao = document.createElement('textarea');
    descricao.value = servico.descricao;
    descricao.maxLength = 2000;
    descricao.rows = 3;
    descricao.setAttribute('aria-label', 'Descrição do serviço');
    descricao.addEventListener('input', () => { servico.descricao = descricao.value; atualizarPrevia(); });
    const remover = document.createElement('button');
    remover.type = 'button';
    remover.textContent = 'REMOVER';
    remover.addEventListener('click', () => { servicos.splice(servicos.indexOf(servico), 1); atualizarServicos(); });
    cartao.append(titulo, descricao, remover);
    escolhidos.append(cartao);
  }
  atualizarPrevia();
}

function adicionarServico(titulo, descricao, origem = null) {
  if (servicos.length >= 30) { mostrarErro('Limite de 30 serviços por proposta.'); return false; }
  servicos.push({ titulo, descricao, origem });
  atualizarServicos();
  return true;
}

document.getElementById('adicionar-servico').addEventListener('click', () => {
  const titulo = valor('novo-servico');
  const descricao = valor('nova-descricao');
  if (!titulo || !descricao) return mostrarErro('Preencha título e descrição do serviço.');
  adicionarServico(titulo, descricao);
  document.getElementById('novo-servico').value = '';
  document.getElementById('nova-descricao').value = '';
});

formulario.addEventListener('input', atualizarPrevia);
formulario.addEventListener('submit', evento => evento.preventDefault());

function dadosProposta() {
  if (!formulario.reportValidity()) throw new Error('Preencha os campos obrigatórios.');
  if (!servicos.length) throw new Error('Escolha pelo menos um serviço.');
  if (servicos.some(servico => !servico.titulo.trim() || !servico.descricao.trim())) {
    throw new Error('Revise título e descrição dos serviços.');
  }
  return {
    empresa: valor('empresa'), cliente: valor('cliente'), email: valor('email'),
    titulo: valor('titulo'), introducao: valor('introducao'), escopo: valor('escopo'),
    valor: Number(valor('valor')), desconto: Number(valor('desconto')),
    prazo: valor('prazo'), condicoes: valor('condicoes'),
    servicos: servicos.map(({ titulo, descricao }) => ({ titulo: titulo.trim(), descricao: descricao.trim() }))
  };
}

async function gerar(formato, assinatura = false) {
  erro.hidden = true;
  const botao = document.getElementById(assinatura ? 'preparar-assinatura' : `baixar-${formato}`);
  try {
    const dados = dadosProposta();
    botao.disabled = true;
    const resposta = await fetch(`/api/advai/propostas/${formato}`, {
      method: 'POST', cache: 'no-store',
      headers: { 'Content-Type': 'application/json', 'X-API-Key': valor('chave-api'),
        Authorization: `Bearer ${valor('token')}` },
      body: JSON.stringify(dados)
    });
    if (!resposta.ok) {
      const detalhe = await resposta.json().catch(() => null);
      throw new Error(detalhe?.mensagem || `Não foi possível gerar a proposta (${resposta.status}).`);
    }
    const arquivo = await resposta.blob();
    if (assinatura) {
      const leitor = new FileReader();
      const conteudo = await new Promise((resolver, rejeitar) => {
        leitor.onload = () => resolver(leitor.result);
        leitor.onerror = rejeitar;
        leitor.readAsDataURL(arquivo);
      });
      sessionStorage.setItem('advai-proposta-pdf', JSON.stringify({ empresa: dados.empresa, conteudo }));
      location.href = '/preparar-assinaturas?origem=advai';
      return;
    }
    const endereco = URL.createObjectURL(arquivo);
    const link = document.createElement('a');
    link.href = endereco;
    link.download = `proposta.${formato}`;
    link.click();
    setTimeout(() => URL.revokeObjectURL(endereco), 60000);
  } catch (falha) { mostrarErro(falha.message); }
  finally { botao.disabled = false; }
}
for (const formato of ['pdf', 'docx']) {
  document.getElementById(`baixar-${formato}`).addEventListener('click', () => gerar(formato));
}
document.getElementById('preparar-assinatura').addEventListener('click', () => gerar('pdf', true));

async function carregarCatalogo() {
  try {
    const resposta = await fetch('/api/advai/catalogo', {
      cache: 'no-store', headers: { 'X-API-Key': valor('chave-api'), Authorization: `Bearer ${valor('token')}` }
    });
    if (!resposta.ok) throw new Error('Informe a chave da API e o token JWT para carregar o catálogo.');
    catalogo.replaceChildren();
    for (const area of await resposta.json()) {
      const grupo = document.createElement('div');
      grupo.className = 'advai-area';
      paragrafo(grupo, area.titulo, 'strong');
      for (const servico of area.servicos) {
        const opcao = document.createElement('label');
        opcao.className = 'advai-opcao';
        const caixa = document.createElement('input');
        caixa.type = 'checkbox';
        caixa.addEventListener('change', () => {
          if (caixa.checked && !adicionarServico(servico.titulo, servico.descricao, servico.titulo)) caixa.checked = false;
          else {
            const indice = servicos.findIndex(item => item.origem === servico.titulo ||
              item.titulo === servico.titulo);
            if (indice >= 0) servicos.splice(indice, 1);
            atualizarServicos();
          }
        });
        const texto = document.createElement('span');
        paragrafo(texto, servico.titulo, 'b');
        paragrafo(texto, servico.descricao, 'small');
        opcao.append(caixa, texto);
        grupo.append(opcao);
      }
      catalogo.append(grupo);
    }
  } catch (falha) { mostrarErro(falha.message); }
}

let esperaCatalogo;
for (const id of ['chave-api', 'token']) {
  document.getElementById(id).addEventListener('input', () => {
    clearTimeout(esperaCatalogo);
    if (valor('chave-api') && valor('token')) esperaCatalogo = setTimeout(carregarCatalogo, 500);
  });
}
atualizarPrevia();
