const empresa = document.getElementById("empresa");
const arquivoPdf = document.getElementById("arquivo-pdf");
const chaveApi = document.getElementById("chave-api");
const token = document.getElementById("token");
const listaSignatarios = document.getElementById("lista-signatarios");
const seletorSignatario = document.getElementById("signatario-atual");
const seletorTipo = document.getElementById("tipo-campo");
const visualizador = document.getElementById("visualizador");
const imagemPagina = document.getElementById("imagem-pagina");
const camadaCampos = document.getElementById("camada-campos");
const erroPreparacao = document.getElementById("erro-preparacao");
const resultadoConvites = document.getElementById("resultado-convites");
const finalizacao = document.getElementById("finalizacao");
let enderecoFinal = null;
const nomeTipos = {
  ASSINATURA: "Assinatura",
  RUBRICA: "Rubrica",
  SELFIE: "Selfie",
};
let empresaParticipante = false;
const signatarios = [
  {
    id: crypto.randomUUID(),
    nome: "",
    email: "",
    cpf: "",
    rg: "",
    whatsapp: "",
    qualificacao: "",
    formaDeAutenticacao: ["tela"],
    formaDeNotificacao: [],
    campos: [],
  },
];
let paginaAtual = 1;
let totalPaginas = 0;
let enderecoImagem = null;
let identificadorEnvelope = new URLSearchParams(location.search).get(
  "envelope",
);

let carregandoPdf = false;
let versaoPdf = 0;
const documentos = [
  {
    id: crypto.randomUUID(),
    nome: "",
    arquivo: null,
    paginas: 0,
    pagina: 1,
  },
];
let documentoAtual = documentos[0];
let empresaCarregada = false;
let consultandoEstado = false;
let acompanhamento;
let recursos = {
  entregaDisponivel: false,
  usuario: "",
};
let convitesGerados = [];
const nomeDocumento = document.getElementById("nome-documento");
const rotulosMetodos = {
  tela: "Assinatura na tela",
  email: "Código por e-mail",
  whatsapp: "Código por WhatsApp",
  selfie: "Selfie",
};
async function verificarCanais() {
  const resposta = await verificarResposta(
    await fetch("/api/assineai/recursos", {
      headers: credenciais(),
      cache: "no-store",
    }),
  );
  recursos = await resposta.json();
  if (recursos.empresa) {
    empresa.value = recursos.empresa.EmpresaNome || "";
    document.getElementById("empresa-cnpj").value =
      recursos.empresa.EmpresaCNPJ || "";
    document.getElementById("empresa-email").value =
      recursos.empresa.EmpresaEmail || "";
    document.getElementById("tema-assinatura").value = String(
      recursos.empresa.EmpresaTemaTelaAssinatura || "firma",
    )
      .toLowerCase()
      .includes("caderno")
      ? "Caderno"
      : /solida|sólida/i.test(recursos.empresa.EmpresaTemaTelaAssinatura || "")
        ? "Cor Solida"
        : "firma";
  }
  document.getElementById('dados-responsavel-pendentes').hidden = !empresaParticipante || !recursos.empresa?.EmpresaAssinaturaAutomatica
    || ['EmpresaCNPJ', 'EmpresaResponsavelNome', 'EmpresaResponsavelCPF', 'EmpresaResponsavelEmail', 'EmpresaResponsavelCelular']
      .every(campo => String(recursos.empresa[campo] || '').trim());
  document.getElementById("disponibilidade-canais").textContent =
    recursos.entregaDisponivel
      ? "Canais disponíveis: e-mail e WhatsApp."
      : "Entrega não configurada. Compartilhe os links manualmente; assinatura na tela e selfie estão disponíveis.";
  const nomeCadastrado = empresa.value.trim();
  empresaCarregada = !!nomeCadastrado
    && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(document.getElementById('empresa-email').value.trim());
  document.getElementById('cadastro-empresa-pendente').hidden = empresaCarregada;
  const navegacao = document.querySelector('.modulos-wizard');
  navegacao.hidden = !empresaCarregada;
  navegacao.style.display = empresaCarregada ? '' : 'none';
  if (empresaCarregada && !identificadorEnvelope) document.getElementById('step-1-signatarios').hidden = false;
  atualizarSignatarios();
  if (empresaCarregada && identificadorEnvelope) await atualizarEstado();
}

document.getElementById('completar-empresa').addEventListener('submit', async evento => {
  evento.preventDefault();
  const botao = evento.currentTarget.querySelector('button[type="submit"]');
  botao.disabled = true;
  try {
    await verificarResposta(await fetch('/api/assineai/empresa', {
      method: 'PUT', headers: { ...credenciais(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ EmpresaNome: empresa.value.trim(),
        EmpresaEmail: document.getElementById('empresa-email').value.trim(),
        EmpresaCNPJ: document.getElementById('empresa-cnpj').value.trim(),
        EmpresaTemaTelaAssinatura: document.getElementById('tema-assinatura').value })
    }));
    await verificarCanais();
  } catch (falha) { mostrarErro(falha.message); }
  finally { botao.disabled = false; }
});
function atualizarDocumentos() {
  const lista = document.getElementById("lista-documentos");
  lista.replaceChildren();
  lista.hidden = documentos.length === 1;
  const posicaoAtual = documentos.indexOf(documentoAtual) + 1;
  document.getElementById('documento-em-edicao').textContent = documentoAtual.arquivo
    ? `Documento ${posicaoAtual}: ${documentoAtual.arquivo.name}`
    : `Documento ${posicaoAtual} · escolha o PDF abaixo.`;
  for (const [indice, documento] of documentos.entries()) {
    const linha = document.createElement("div");
    linha.className = "documento-wizard";
    const editar = document.createElement("button");
    editar.type = "button";
    editar.textContent = `${indice + 1}. ${documento.nome || `Documento ${indice + 1}`} · ${documento.paginas ? `${documento.paginas} pág.` : documento.arquivo ? "PDF selecionado" : "Escolha o PDF"}`;
    editar.setAttribute("aria-pressed", String(documento === documentoAtual));
    editar.disabled = carregandoPdf;
    editar.addEventListener("click", () => {
      if (documento !== documentoAtual) selecionarDocumento(documento);
    });
    const remover = document.createElement("button");
    remover.type = "button";
    remover.textContent = "REMOVER";
    remover.hidden = documentos.length === 1;
    remover.disabled = carregandoPdf;
    remover.title = carregandoPdf ? "Aguarde a abertura do PDF" : "Remover este documento";
    remover.setAttribute(
      "aria-label",
      `Remover documento ${documento.nome || indice + 1}`,
    );
    remover.addEventListener("click", () => {
      documentos.splice(documentos.indexOf(documento), 1);
      for (const pessoa of signatarios)
        pessoa.campos = pessoa.campos.filter(
          (campo) => campo.documento !== documento.id,
        );
      if (documento === documentoAtual) selecionarDocumento(documentos[0]);
      atualizarDocumentos();
      atualizarSignatarios();
      desenharCampos();
    });
    linha.append(editar, remover);
    lista.append(linha);
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
  document.getElementById("header-posicionamento").hidden = true;
  document.getElementById("etapa-posicionamento").hidden = true;
  atualizarDocumentos();
  atualizarControlesPdf();
  if (!totalPaginas) return;
  carregandoPdf = true;
  atualizarControlesPdf();
  try {
    await mostrarPagina(paginaAtual, versao);
  } catch (erro) {
    if (versao === versaoPdf) mostrarErro(erro.message);
  } finally {
    if (versao === versaoPdf) {
      carregandoPdf = false;
      atualizarControlesPdf();
      atualizarDocumentos();
    }
  }
}
nomeDocumento.addEventListener("input", () => {
  documentoAtual.nome = nomeDocumento.value;
  atualizarDocumentos();
});
document.getElementById("adicionar-documento").addEventListener("click", () => {
  if (carregandoPdf) return;
  if (documentos.length >= 10)
    return mostrarErro("Limite de 10 documentos por assinatura.");
  const documento = {
    id: crypto.randomUUID(),
    nome: "",
    arquivo: null,
    paginas: 0,
    pagina: 1,
  };
  documentos.push(documento);
  selecionarDocumento(documento);
  nomeDocumento.focus();
});
function validarDadosPessoais() {
  for (const pessoa of signatarios) {
    pessoa.cpf = dadosPessoaisSignatario.cpf(pessoa.cpf);
    pessoa.rg = dadosPessoaisSignatario.rg(pessoa.rg);
    pessoa.whatsapp = dadosPessoaisSignatario.whatsapp(pessoa.whatsapp);
  }
}
function validarPreparacao() {
  validarDadosPessoais();
  if (!empresaCarregada) throw new Error("Complete os dados da empresa antes de continuar.");
  if (!empresa.value.trim()) throw new Error("Informe o nome da empresa.");
  if (!signatarios.length && !empresaParticipante)
    throw new Error("Adicione pelo menos um signatário.");
  if (
    documentos.some(
      (dadosAtuais) =>
        !dadosAtuais.nome.trim() ||
        !dadosAtuais.arquivo ||
        !dadosAtuais.paginas,
    )
  )
    throw new Error("Dê um nome e abra o PDF de cada documento.");
  if (
    signatarios.reduce(
      (valorAtual, itemAtual) => valorAtual + itemAtual.campos.length,
      0,
    ) > 100
  )
    throw new Error("Limite de 100 campos por assinatura.");
}
function mostrarRevisao() {
  const painel = document.getElementById("revisao-assinatura");
  painel.replaceChildren();
  const titulo = document.createElement("h2");
  titulo.textContent = "CONFIRA ANTES DE FINALIZAR";
  painel.append(titulo);
  if (empresaParticipante) {
    const linhaEmpresa = document.createElement('p');
    linhaEmpresa.textContent = `Empresa participante: ${recursos.empresa.EmpresaNome} · Responsável: ${recursos.empresa.EmpresaResponsavelNome}.`;
    painel.append(linhaEmpresa);
  }
  for (const documento of documentos) {
    const linha = document.createElement("p");
    linha.textContent = `${documento.nome} · ${documento.paginas} página(s) · ${signatarios.reduce((valorAtual, itemAtual) => valorAtual + itemAtual.campos.filter((itemDoRegistro) => itemDoRegistro.documento === documento.id).length, 0)} campo(s)`;
    painel.append(linha);
  }
  for (const pessoa of signatarios) {
    const linha = document.createElement("p");
    linha.textContent = `${pessoa.nome} · ${pessoa.qualificacao || "Signatário"} · Autenticação: ${pessoa.formaDeAutenticacao.map((registro) => rotulosMetodos[registro]).join(", ") || "Marcas posicionadas no PDF"} · Notificação: ${pessoa.formaDeNotificacao.join(", ") || "Link manual"}`;
    painel.append(linha);
  }
}
let criacaoConcluida = false;
let assinaturaEmpresaRegistrada = false;
let identificadorPedidoCriacao = crypto.randomUUID();
let formularioCriacaoPendente = null;

if (new URLSearchParams(location.search).get("origem") === "advai") {
  const transferencia = sessionStorage.getItem("advai-proposta-pdf");
  sessionStorage.removeItem("advai-proposta-pdf");
  if (transferencia) {
    try {
      const dados = JSON.parse(transferencia);
      const bytes = Uint8Array.from(
        atob(dados.conteudo.split(",")[1]),
        (letra) => letra.charCodeAt(0),
      );
      const arquivos = new DataTransfer();
      arquivos.items.add(
        new File([bytes], "proposta.pdf", {
          type: "application/pdf",
        }),
      );
      arquivoPdf.files = arquivos.files;
      documentoAtual.arquivo = arquivos.files[0];
      documentoAtual.nome = "Proposta";
      nomeDocumento.value = "Proposta";
      empresa.value = dados.empresa;
      empresa.dispatchEvent(new Event("input"));
    } catch {
      mostrarErro(
        "Não foi possível carregar a proposta. Selecione o PDF manualmente.",
      );
    }
  }
}
empresa.addEventListener("input", () => {
  document.querySelector(".marca").firstChild.textContent =
    empresa.value.trim() || "aFIRMA.";
});
function mostrarErro(mensagem) {
  erroPreparacao.textContent = mensagem;
  erroPreparacao.hidden = false;
  erroPreparacao.scrollIntoView({
    block: "nearest",
  });
}
function credenciais() {
  if (!token.value.trim())
    throw new Error("Entre com a conta da empresa ou informe o token JWT.");
  return {
    ...(chaveApi.value.trim()
      ? {
          "X-API-Key": chaveApi.value.trim(),
        }
      : {}),
    Authorization: `Bearer ${token.value.trim()}`,
  };
}
async function verificarResposta(resposta) {
  if (resposta.ok) return resposta;
  const dados = await resposta.json().catch(() => null);
  throw new Error(dados?.mensagem || `Operação recusada (${resposta.status}).`);
}

// Wizard Logic
const s1 = document.getElementById("step-1-signatarios");
const s2 = document.getElementById("step-2-documentos");
const s3 = document.getElementById("step-3-finalizar");
const b1 = document.getElementById("btn-step-1");
const b2 = document.getElementById("btn-step-2");
const b3 = document.getElementById("btn-step-3");
function changeStep(inicioAtual, fim, botaoPaginaAnterior, botaoProximaPagina) {
  inicioAtual.hidden = true;
  fim.hidden = false;
  botaoPaginaAnterior.classList.remove("ativo");
  botaoPaginaAnterior.classList.add("inativo");
  botaoProximaPagina.classList.remove("inativo");
  botaoProximaPagina.classList.add("ativo");
  for (const botao of [b1, b2, b3]) {
    if (botao === botaoProximaPagina)
      botao.setAttribute("aria-current", "step");
    else botao.removeAttribute("aria-current");
  }
  fim.scrollIntoView({
    block: "start",
    behavior: "smooth",
  });
}
document.getElementById("ir-step-2").addEventListener("click", () => {
  erroPreparacao.hidden = true;
  for (const campo of listaSignatarios.querySelectorAll('[data-dado-pessoal]')) {
    if (!campo.reportValidity()) return;
  }
  try { validarDadosPessoais(); } catch (falha) { return mostrarErro(falha.message); }
  if (!signatarios.length && !empresaParticipante)
    return mostrarErro("Adicione pelo menos um signatário.");
  if (signatarios.some((itemAtual) => !itemAtual.nome.trim()))
    return mostrarErro("Preencha o nome de todos os signatários.");
  if (
    signatarios.some(
      (itemAtual) =>
        itemAtual.email.trim() &&
        !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(itemAtual.email.trim()),
    )
  )
    return mostrarErro("Informe um e-mail válido ou deixe o campo vazio.");
  if (
    signatarios.some(
      (itemAtual) =>
        [
          ...itemAtual.formaDeAutenticacao,
          ...itemAtual.formaDeNotificacao,
        ].includes("email") && !itemAtual.email.trim(),
    )
  )
    return mostrarErro("Informe e-mail para os canais selecionados.");
  if (
    signatarios.some(
      (itemAtual) =>
        [
          ...itemAtual.formaDeAutenticacao,
          ...itemAtual.formaDeNotificacao,
        ].includes("whatsapp") &&
        !itemAtual.whatsapp.trim(),
    )
  )
    return mostrarErro("Informe WhatsApp com DDD para os canais selecionados.");
  if (
    !recursos.entregaDisponivel &&
    signatarios.some(
      (itemAtual) =>
        itemAtual.formaDeNotificacao.length ||
        itemAtual.formaDeAutenticacao.some((registro) =>
          ["email", "whatsapp"].includes(registro),
        ),
    )
  )
    return mostrarErro(
      "Verifique a disponibilidade dos canais com as credenciais atuais.",
    );
  changeStep(s1, s2, b1, b2);
});
document.getElementById("voltar-step-1").addEventListener("click", () => {
  changeStep(s2, s1, b2, b1);
});
document.getElementById("ir-step-3").addEventListener("click", () => {
  erroPreparacao.hidden = true;
  try {
    validarPreparacao();
  } catch (erro) {
    return mostrarErro(erro.message);
  }
  mostrarRevisao();
  changeStep(s2, s3, b2, b3);
});
document.getElementById("voltar-step-2").addEventListener("click", () => {
  changeStep(s3, s2, b3, b2);
});
function atualizarSeletor() {
  const selecionado = seletorSignatario.value;
  seletorSignatario.replaceChildren(
    ...signatarios.map((item, indice) => {
      const opcao = document.createElement("option");
      opcao.value = item.id;
      opcao.textContent = `${indice + 1}. ${item.nome.trim() || "Signatário"} · ${item.campos.length} campo(s)`;
      return opcao;
    }),
  );
  if (signatarios.some((item) => item.id === selecionado))
    seletorSignatario.value = selecionado;
  atualizarResumo();
}
function atualizarResumo() {
  seletorSignatario.disabled = !signatarios.length;
  const pendentes = signatarios.filter((item) => !item.campos.length);
  document.getElementById("resumo-campos").textContent = !signatarios.length && empresaParticipante
    ? 'A empresa assina com o certificado cadastrado. Não é necessário posicionar uma imagem de assinatura.'
    : pendentes.length
    ? `Falta posicionar: ${pendentes.map((item) => item.nome.trim() || "Signatário").join(", ")}.`
    : `Tudo pronto: ${signatarios.reduce((total, item) => total + item.campos.length, 0)} campo(s) para ${signatarios.length} signatário(s).`;
}
function atualizarSignatarios() {
  listaSignatarios.replaceChildren();
  document.getElementById('adicionar-eu').disabled = empresaParticipante;
  if (empresaParticipante) {
    const cadastro = recursos.empresa;
    const cartao = document.createElement('section'); cartao.className = 'cartao-signatario cartao-novo-assinante';
    const titulo = document.createElement('h3'); titulo.textContent = cadastro.EmpresaNome;
    const responsavel = document.createElement('p');
    responsavel.textContent = `Responsável: ${cadastro.EmpresaResponsavelNome} · CPF: ${cadastro.EmpresaResponsavelCPF}${cadastro.EmpresaResponsavelRG ? ` · RG: ${cadastro.EmpresaResponsavelRG}` : ''} · CNPJ: ${cadastro.EmpresaCNPJ}`;
    const contato = document.createElement('p'); contato.textContent = `${cadastro.EmpresaResponsavelEmail} · ${cadastro.EmpresaResponsavelCelular}`;
    const metodo = document.createElement('p'); metodo.textContent = cadastro.EmpresaAssinaturaAutomatica
      ? 'A empresa assina com o certificado cadastrado ao gerar os convites.' : 'A empresa participa e assina manualmente com o certificado cadastrado.';
    const remover = document.createElement('button'); remover.type = 'button'; remover.className = 'acao secundario'; remover.textContent = 'REMOVER MINHA PARTICIPAÇÃO';
    remover.addEventListener('click', () => { empresaParticipante = false; atualizarSignatarios(); });
    cartao.append(titulo, responsavel, contato, metodo, remover); listaSignatarios.append(cartao);
  }
  for (const signatario of signatarios) {
    const cartao = document.createElement("div");
    cartao.className = "cartao-signatario cartao-novo-assinante";

    // Nome
    const nome = document.createElement("div");
    nome.className = "campo";
    nome.innerHTML = "<label>NOME *</label>";
    const entradaNome = document.createElement("input");
    entradaNome.maxLength = 255;
    entradaNome.value = signatario.nome;
    entradaNome.placeholder = "Nome do signatário";
    entradaNome.addEventListener("input", () => {
      signatario.nome = entradaNome.value;
      atualizarSeletor();
      desenharCampos();
    });
    nome.append(entradaNome);

    // Email
    const email = document.createElement("div");
    email.className = "campo";
    email.innerHTML = "<label>E-MAIL (OPCIONAL)</label>";
    const entradaEmail = document.createElement("input");
    entradaEmail.type = "email";
    entradaEmail.maxLength = 254;
    entradaEmail.value = signatario.email;
    entradaEmail.placeholder = "pessoa@empresa.com";
    entradaEmail.addEventListener("input", () => {
      signatario.email = entradaEmail.value;
    });
    email.append(entradaEmail);

    // CPF
    const divCpf = document.createElement("div");
    divCpf.className = "campo";
    divCpf.innerHTML = "<label>CPF (OPCIONAL)</label>";
    const entradaCpf = document.createElement("input");
    entradaCpf.placeholder = "000.000.000-00";
    entradaCpf.value = signatario.cpf || "";
    entradaCpf.addEventListener("input", () => {
      signatario.cpf = entradaCpf.value;
    });
    divCpf.append(entradaCpf);

    // RG
    const divRg = document.createElement("div");
    divRg.className = "campo";
    divRg.innerHTML = "<label>RG</label>";
    const entradaRg = document.createElement("input");
    entradaRg.placeholder = "Número conforme o documento";
    entradaRg.value = signatario.rg || "";
    entradaRg.addEventListener("input", () => {
      signatario.rg = entradaRg.value;
    });
    divRg.append(entradaRg);

    // Whatsapp
    const divZap = document.createElement("div");
    divZap.className = "campo";
    divZap.innerHTML = "<label>WHATSAPP (OPCIONAL)</label>";
    const entradaZap = document.createElement("input");
    entradaZap.placeholder = "(11) 99999-9999 ou + código do país";
    entradaZap.value = signatario.whatsapp || "";
    entradaZap.addEventListener("input", () => {
      signatario.whatsapp = entradaZap.value;
    });
    divZap.append(entradaZap);

    // Qualificação
    const divQualificacao = document.createElement("div");
    divQualificacao.className = "campo";
    divQualificacao.innerHTML = "<label>VAI ASSINAR COMO?</label>";
    const entradaQualificacao = document.createElement("input");
    entradaQualificacao.placeholder = "Associado, Empresa, Motorista...";
    entradaQualificacao.maxLength = 255;

    entradaQualificacao.value = signatario.qualificacao || "";
    entradaQualificacao.addEventListener("input", () => {
      signatario.qualificacao = entradaQualificacao.value;
    });
    divQualificacao.append(entradaQualificacao);
    prepararListaQualificacaoFirma(divQualificacao, entradaQualificacao, signatario.id);

    // Área de rodapé do cartão (Quantidade e Remover) - Ocupando a linha inteira no grid
    const rodapeCartao = document.createElement("div");
    rodapeCartao.className = "cartao-rodape-acoes";
    const representacao = document.createElement("div");
    representacao.className = "campo campo-largo";
    const rotuloPapel = document.createElement("label");
    rotuloPapel.textContent = "ATUAÇÃO DO PARTICIPANTE";
    const papel = document.createElement("select");
    for (const [valor, texto] of [
      ["EM_NOME_PROPRIO", "Em nome próprio"],
      ["REPRESENTANTE", "Em nome de outra pessoa ou empresa"],
    ]) {
      const opcao = document.createElement("option");
      opcao.value = valor;
      opcao.textContent = texto;
      papel.append(opcao);
    }
    papel.value = signatario.papel || "EM_NOME_PROPRIO";
    const representado = document.createElement("input");
    representado.placeholder = "Pessoa ou empresa representada";
    representado.maxLength = 254;
    representado.value = signatario.representado || "";
    const fundamento = document.createElement("textarea");
    fundamento.placeholder = "Fundamento dos poderes de representação";
    fundamento.maxLength = 1000;
    fundamento.value = signatario.fundamentoRepresentacao || "";
    function atualizarRepresentacao() {
      signatario.papel = papel.value;
      representado.hidden = fundamento.hidden = papel.value !== "REPRESENTANTE";
      if (papel.value !== "REPRESENTANTE") {
        signatario.representado = signatario.fundamentoRepresentacao = "";
        representado.value = fundamento.value = "";
      }
    }
    papel.addEventListener("change", atualizarRepresentacao);
    representado.addEventListener("input", () => {
      signatario.representado = representado.value;
    });
    fundamento.addEventListener("input", () => {
      signatario.fundamentoRepresentacao = fundamento.value;
    });
    atualizarRepresentacao();
    representacao.append(rotuloPapel, papel, representado, fundamento);
    prepararListaAtuacaoFirma(representacao, rotuloPapel, papel, signatario.id);
    const quantidade = document.createElement("p");
    quantidade.textContent = `${signatario.campos.length} campo(s) posicionado(s)`;
    const remover = document.createElement("button");
    remover.type = "button";
    remover.className = "acao secundario";
    remover.textContent = "REMOVER SIGNATÁRIO";
    remover.addEventListener("click", () => {
      signatarios.splice(signatarios.indexOf(signatario), 1);
      atualizarSignatarios();
      desenharCampos();
    });
    rodapeCartao.append(quantidade, remover);
    const escolhas = document.createElement("div");
    escolhas.className = "escolhas-signatario";
    for (const [propriedade, legenda, metodos] of [
      [
        "formaDeAutenticacao",
        "FORMA DE AUTENTICAÇÃO",
        [
          ["tela", "Assinatura na tela"],
          ["email", "Código por e-mail"],
          ["whatsapp", "Código por WhatsApp"],
          ["selfie", "Selfie"],
        ],
      ],
      [
        "formaDeNotificacao",
        "FORMA DE NOTIFICAÇÃO",
        [
          ["email", "E-mail"],
          ["whatsapp", "WhatsApp"],
        ],
      ],
    ]) {
      const grupo = document.createElement("fieldset");
      const titulo = document.createElement("legend");
      titulo.textContent = legenda;
      grupo.append(titulo);
      for (const [valor, rotulo] of metodos) {
        const rotuloAtual = document.createElement("label");
        const entrada = document.createElement("input");
        entrada.type = "checkbox";
        entrada.value = valor;
        entrada.checked = signatario[propriedade].includes(valor);
        if (["email", "whatsapp"].includes(valor)) {
          entrada.dataset.canalEntrega = "";
          entrada.disabled = !recursos.entregaDisponivel && !entrada.checked;
        }
        entrada.addEventListener("change", () => {
          signatario[propriedade] = entrada.checked
            ? [...signatario[propriedade], valor]
            : signatario[propriedade].filter((item) => item !== valor);
        });
        rotuloAtual.append(entrada, document.createTextNode(rotulo));
        grupo.append(rotuloAtual);
      }
      escolhas.append(grupo);
    }
    for (const [grupo, entrada, sufixo] of [
      [nome, entradaNome, "nome"],
      [email, entradaEmail, "email"],
      [divCpf, entradaCpf, "cpf"],
      [divRg, entradaRg, "rg"],
      [divZap, entradaZap, "whatsapp"],
      [divQualificacao, entradaQualificacao, "qualificacao"],
    ]) {
      entrada.id = `${signatario.id}-${sufixo}`;
      grupo.querySelector("label").htmlFor = entrada.id;
    }
    dadosPessoaisSignatario.ligar(entradaCpf, 'cpf', valor => { signatario.cpf = valor; });
    dadosPessoaisSignatario.ligar(entradaRg, 'rg', valor => { signatario.rg = valor; });
    dadosPessoaisSignatario.ligar(entradaZap, 'whatsapp', valor => { signatario.whatsapp = valor; });
    cartao.append(
      nome,
      email,
      divCpf,
      divRg,
      divZap,
      divQualificacao,
      representacao,
      escolhas,
      rodapeCartao,
    );
    listaSignatarios.append(cartao);
  }
  atualizarSeletor();
}
document
  .getElementById("adicionar-signatario")
  .addEventListener("click", () => {
    if (signatarios.length >= 20)
      return mostrarErro("Limite de 20 signatários.");
    const signatario = {
      id: crypto.randomUUID(),
      nome: "",
      email: "",
      cpf: "",
      rg: "",
      whatsapp: "",
      qualificacao: "",
      formaDeAutenticacao: ["tela"],
      formaDeNotificacao: [],
      campos: [],
      papel: "EM_NOME_PROPRIO",
      representado: "",
      fundamentoRepresentacao: "",
    };
    signatarios.push(signatario);
    atualizarSignatarios();
    seletorSignatario.value = signatario.id;
  });
function atualizarControlesPdf() {
  document.getElementById("abrir-pdf").disabled = carregandoPdf;
  document.getElementById("pagina-anterior").disabled =
    carregandoPdf || paginaAtual <= 1;
  document.getElementById("pagina-seguinte").disabled =
    carregandoPdf || paginaAtual >= totalPaginas;
  document.getElementById("adicionar-campo").disabled =
    carregandoPdf || !totalPaginas;
  document.getElementById("ir-step-3").disabled = carregandoPdf;
  document.getElementById("adicionar-documento").disabled = carregandoPdf;
  atualizarDocumentos();
  visualizador.inert = carregandoPdf;
  visualizador.setAttribute("aria-busy", String(carregandoPdf));
  document.getElementById("estado-pdf").hidden = !carregandoPdf;
  document.getElementById("estado-pdf").textContent =
    "Carregando página do documento…";
}
async function mostrarPagina(pagina = paginaAtual, versao = versaoPdf) {
  const dados = new FormData();
  dados.append("pdf", arquivoPdf.files[0]);
  dados.append("pagina", String(pagina));
  const resposta = await verificarResposta(
    await fetch("/api/assineai/previsualizar", {
      method: "POST",
      headers: credenciais(),
      body: dados,
      cache: "no-store",
    }),
  );
  const novoEndereco = URL.createObjectURL(await resposta.blob());
  const imagem = new Image();
  try {
    imagem.src = novoEndereco;
    await imagem.decode();
    if (versao !== versaoPdf) {
      URL.revokeObjectURL(novoEndereco);
      return;
    }
    imagemPagina.src = novoEndereco;
    if (enderecoImagem) URL.revokeObjectURL(enderecoImagem);
    enderecoImagem = novoEndereco;
  } catch {
    URL.revokeObjectURL(novoEndereco);
    throw new Error(
      "Não foi possível exibir a página do PDF. Tente novamente.",
    );
  }
  paginaAtual = pagina;
  documentoAtual.pagina = pagina;
  visualizador.hidden = false;
  document.getElementById("header-posicionamento").hidden = false;
  document.getElementById("etapa-posicionamento").hidden = false;
  document.getElementById("numero-pagina").textContent =
    `PÁGINA ${paginaAtual} / ${totalPaginas}`;
  desenharCampos();
}
document.getElementById("abrir-pdf").addEventListener("click", async () => {
  if (carregandoPdf) return;
  erroPreparacao.hidden = true;
  const versao = versaoPdf;
  carregandoPdf = true;
  atualizarControlesPdf();
  try {
    if (!arquivoPdf.files[0]) throw new Error("Escolha um PDF.");
    const dados = new FormData();
    dados.append("pdf", arquivoPdf.files[0]);
    const resposta = await verificarResposta(
      await fetch("/api/assineai/paginas", {
        method: "POST",
        headers: credenciais(),
        body: dados,
        cache: "no-store",
      }),
    );
    const quantidade = (await resposta.json()).paginas;
    if (versao !== versaoPdf) return;
    if (!Number.isInteger(quantidade) || quantidade < 1)
      throw new Error("O PDF não contém páginas válidas.");
    totalPaginas = quantidade;
    documentoAtual.paginas = quantidade;
    await mostrarPagina(1, versao);
  } catch (erro) {
    if (versao === versaoPdf) mostrarErro(erro.message);
  } finally {
    if (versao === versaoPdf) {
      carregandoPdf = false;
      atualizarControlesPdf();
      atualizarDocumentos();
    }
  }
});
arquivoPdf.addEventListener("change", () => {
  versaoPdf++;
  carregandoPdf = false;
  totalPaginas = 0;
  paginaAtual = 1;
  visualizador.hidden = true;
  if (enderecoImagem) URL.revokeObjectURL(enderecoImagem);
  enderecoImagem = null;
  document.getElementById("header-posicionamento").hidden = true;
  document.getElementById("etapa-posicionamento").hidden = true;
  document.getElementById("numero-pagina").textContent =
    "Abra o PDF para começar.";
  documentoAtual.arquivo = arquivoPdf.files[0] || null;
  documentoAtual.paginas = 0;
  documentoAtual.pagina = 1;
  if (!documentoAtual.nome && documentoAtual.arquivo) {
    documentoAtual.nome = documentoAtual.arquivo.name
      .replace(/\.pdf$/i, "")
      .slice(0, 255);
    nomeDocumento.value = documentoAtual.nome;
  }
  for (const signatario of signatarios)
    signatario.campos = signatario.campos.filter(
      (campo) => campo.documento !== documentoAtual.id,
    );
  atualizarDocumentos();
  if (!token.value) token.value = localStorage.getItem("assineai-token") || "";
  if (!chaveApi.value)
    chaveApi.value = localStorage.getItem("assineai-chave") || "";
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
  try {
    await mostrarPagina(pagina, versao);
  } catch (erro) {
    if (versao === versaoPdf) mostrarErro(erro.message);
  } finally {
    if (versao === versaoPdf) {
      carregandoPdf = false;
      atualizarControlesPdf();
      atualizarDocumentos();
    }
  }
}
document
  .getElementById("pagina-anterior")
  .addEventListener("click", () => navegarPagina(-1));
document
  .getElementById("pagina-seguinte")
  .addEventListener("click", () => navegarPagina(1));
function adicionarCampo(x, y) {
  if (carregandoPdf || visualizador.hidden) return;
  const signatario = signatarios.find(
    (item) => item.id === seletorSignatario.value,
  );
  if (!signatario) return mostrarErro("Adicione e selecione um signatário.");
  if (
    signatarios.reduce(
      (valorAtual, itemAtual) => valorAtual + itemAtual.campos.length,
      0,
    ) >= 100
  )
    return mostrarErro("Limite de 100 campos por assinatura.");
  const tipo = seletorTipo.value;
  const largura = tipo === "SELFIE" ? 0.2 : tipo === "RUBRICA" ? 0.18 : 0.3;
  const altura = tipo === "SELFIE" ? 0.2 : tipo === "RUBRICA" ? 0.07 : 0.1;
  signatario.campos.push({
    id: crypto.randomUUID(),
    tipo,
    pagina: paginaAtual,
    rotacao: 0,
    documento: documentoAtual.id,
    posicaoX: Math.max(0, Math.min(1 - largura, x - largura / 2)),
    posicaoY: Math.max(0, Math.min(1 - altura, y - altura / 2)),
    largura,
    altura,
  });
  atualizarSignatarios();
  seletorSignatario.value = signatario.id;
  desenharCampos();
  camadaCampos.lastElementChild?.focus({
    preventScroll: true,
  });
}
camadaCampos.addEventListener("click", (evento) => {
  if (evento.target !== camadaCampos) return;
  const retangulo = camadaCampos.getBoundingClientRect();
  adicionarCampo(
    (evento.clientX - retangulo.left) / retangulo.width,
    (evento.clientY - retangulo.top) / retangulo.height,
  );
});
document
  .getElementById("adicionar-campo")
  .addEventListener("click", () => adicionarCampo(0.5, 0.5));
seletorSignatario.addEventListener("change", desenharCampos);
function desenharCampos() {
  camadaCampos.replaceChildren();
  for (const signatario of signatarios) {
    for (const campo of signatario.campos.filter(
      (item) =>
        item.pagina === paginaAtual && item.documento === documentoAtual.id,
    )) {
      const caixa = document.createElement("div");
      caixa.className = "campo-posicionado";
      caixa.dataset.campo = campo.id;
      caixa.dataset.tipo = campo.tipo;
      caixa.tabIndex = 0;
      caixa.setAttribute(
        "aria-label",
        `${nomeTipos[campo.tipo]} de ${signatario.nome}, página ${campo.pagina}, rotação ${campo.rotacao || 0} graus. Use as setas para mover.`,
      );
      caixa.classList.toggle(
        "campo-ativo",
        signatario.id === seletorSignatario.value,
      );
      caixa.style.left = `${campo.posicaoX * 100}%`;
      caixa.style.top = `${campo.posicaoY * 100}%`;
      caixa.style.width = `${campo.largura * 100}%`;
      caixa.style.height = `${campo.altura * 100}%`;
      const texto = document.createElement("span");
      texto.style.transform = `rotate(${campo.rotacao || 0}deg)`;
      texto.textContent = `${nomeTipos[campo.tipo]} · ${signatario.nome.trim() || "Signatário"}`;
      const remover = document.createElement("button");
      remover.type = "button";
      remover.setAttribute("aria-label", "Remover campo");
      remover.textContent = "×";
      remover.addEventListener("click", (evento) => {
        evento.stopPropagation();
        signatario.campos.splice(signatario.campos.indexOf(campo), 1);
        atualizarSignatarios();
        desenharCampos();
      });
      const alca = document.createElement("span");
      alca.className = "alca-redimensionar";
      alca.setAttribute("aria-hidden", "true");
      const controles = document.createElement("div");
      controles.className = "controles-campo";
      if (campo.posicaoX > .5) {
        controles.style.left = "auto";
        controles.style.right = "0";
      }
      for (const [titulo, acao] of [
        ["Girar 90°", () => {
          const pagina = camadaCampos.getBoundingClientRect();
          const largura = campo.altura * pagina.height / pagina.width;
          const altura = campo.largura * pagina.width / pagina.height;
          if (largura > 1 || altura > 1)
            return mostrarErro("Reduza o campo antes de girar para que ele caiba na página.");
          campo.posicaoX = Math.max(0, Math.min(1 - largura, campo.posicaoX + (campo.largura - largura) / 2));
          campo.posicaoY = Math.max(0, Math.min(1 - altura, campo.posicaoY + (campo.altura - altura) / 2));
          campo.largura = largura;
          campo.altura = altura;
          campo.rotacao = ((campo.rotacao || 0) + 90) % 360;
          desenharCampos();
          camadaCampos.querySelector(`[data-campo="${campo.id}"]`)?.focus({ preventScroll: true });
        }],
        ["Duplicar esse campo em todas as páginas", () => {
          const paginas = Array.from({ length: totalPaginas }, (_, indice) => indice + 1)
            .filter(pagina => pagina !== campo.pagina && !signatario.campos.some(outro =>
              outro.documento === campo.documento && outro.pagina === pagina && outro.tipo === campo.tipo &&
              outro.posicaoX === campo.posicaoX && outro.posicaoY === campo.posicaoY &&
              outro.largura === campo.largura && outro.altura === campo.altura &&
              (outro.rotacao || 0) === (campo.rotacao || 0)));
          const quantidade = paginas.length;
          const total = signatarios.reduce((soma, pessoa) => soma + pessoa.campos.length, 0);
          if (total + quantidade > 100)
            return mostrarErro("A duplicação ultrapassa o limite de 100 campos por assinatura.");
          for (const pagina of paginas)
            signatario.campos.push({ ...campo, id: crypto.randomUUID(), pagina });
          atualizarSignatarios();
          desenharCampos();
        }],
      ]) {
        if (titulo === "Girar 90°" && !["ASSINATURA", "RUBRICA"].includes(campo.tipo)) continue;
        const botao = document.createElement("button");
        botao.type = "button";
        botao.textContent = titulo;
        botao.addEventListener("click", (evento) => {
          evento.stopPropagation();
          acao();
        });
        controles.append(botao);
      }
      caixa.append(texto, remover, alca, controles);
      const atualizarGeometria = () => {
        caixa.style.left = `${campo.posicaoX * 100}%`;
        caixa.style.top = `${campo.posicaoY * 100}%`;
        caixa.style.width = `${campo.largura * 100}%`;
        caixa.style.height = `${campo.altura * 100}%`;
      };
      caixa.addEventListener("keydown", (evento) => {
        if (evento.target !== caixa) return;
        const direcoes = {
          ArrowLeft: [-1, 0],
          ArrowRight: [1, 0],
          ArrowUp: [0, -1],
          ArrowDown: [0, 1],
        };
        const direcao = direcoes[evento.key];
        if (!direcao) return;
        evento.preventDefault();
        const passo = evento.shiftKey ? 0.02 : 0.002;
        campo.posicaoX = Math.max(
          0,
          Math.min(1 - campo.largura, campo.posicaoX + direcao[0] * passo),
        );
        campo.posicaoY = Math.max(
          0,
          Math.min(1 - campo.altura, campo.posicaoY + direcao[1] * passo),
        );
        atualizarGeometria();
      });
      caixa.addEventListener("pointerdown", (evento) => {
        if (evento.target.closest("button") || evento.button !== 0) return;
        evento.preventDefault();
        caixa.focus({
          preventScroll: true,
        });
        caixa.setPointerCapture(evento.pointerId);
        const redimensionar = evento.target === alca;
        const inicio = {
          x: evento.clientX,
          y: evento.clientY,
          ...campo,
        };
        const retangulo = camadaCampos.getBoundingClientRect();
        const mover = (movimento) => {
          const distanciaHorizontal =
            (movimento.clientX - inicio.x) / retangulo.width;
          const distanciaVertical =
            (movimento.clientY - inicio.y) / retangulo.height;
          if (redimensionar) {
            campo.largura = Math.max(
              0.08,
              Math.min(
                1 - campo.posicaoX,
                inicio.largura + distanciaHorizontal,
              ),
            );
            campo.altura = Math.max(
              0.04,
              Math.min(1 - campo.posicaoY, inicio.altura + distanciaVertical),
            );
          } else {
            campo.posicaoX = Math.max(
              0,
              Math.min(
                1 - campo.largura,
                inicio.posicaoX + distanciaHorizontal,
              ),
            );
            campo.posicaoY = Math.max(
              0,
              Math.min(1 - campo.altura, inicio.posicaoY + distanciaVertical),
            );
          }
          atualizarGeometria();
        };
        const terminar = () => {
          caixa.removeEventListener("pointermove", mover);
          caixa.removeEventListener("pointerup", terminar);
          caixa.removeEventListener("pointercancel", terminar);
          caixa.removeEventListener("lostpointercapture", terminar);
        };
        caixa.addEventListener("pointermove", mover);
        caixa.addEventListener("pointerup", terminar);
        caixa.addEventListener("pointercancel", terminar);
        caixa.addEventListener("lostpointercapture", terminar);
      });
      camadaCampos.append(caixa);
    }
  }
}
document
  .getElementById("criar-envelope")
  .addEventListener("click", async () => {
    const botao = document.getElementById("criar-envelope");
    if (botao.disabled || criacaoConcluida) return;

    erroPreparacao.hidden = true;
    botao.disabled = true;
    try {
      await verificarCanais();
      validarPreparacao();
      const nomeAssinatura = document
        .getElementById("nome-assinatura")
        .value.trim();
      if (!nomeAssinatura) throw new Error("Informe o nome da assinatura.");
      if (
        signatarios.some(
          (pessoa) => !pessoa.nome.trim() || !pessoa.email.trim(),
        )
      )
        throw new Error("Informe nome e e-mail de todos os participantes.");
      const formalidades = await politicaAssinatura.formalidades();
      FirmaMensagens.mostrar('Preparando os documentos e os convites.', { id: 'assinatura-andamento', tipo: 'processando' });
      if (!formularioCriacaoPendente) {
        const dados = new FormData();
        if (empresaParticipante && recursos.empresa?.EmpresaAssinaturaAutomatica) {
          const campos = ['EmpresaCNPJ', 'EmpresaResponsavelNome', 'EmpresaResponsavelCPF', 'EmpresaResponsavelEmail', 'EmpresaResponsavelCelular'];
          if (campos.some(campo => !String(recursos.empresa[campo] || '').trim()))
            throw new Error('Complete os dados do responsável na tela da empresa antes de gerar os convites.');
          FirmaMensagens.mostrar('Autorize a localização para registrar o acesso de quem está criando os convites.', { id: 'assinatura-andamento', tipo: 'processando' });
          await adicionarColetaEmpresa(dados);
          FirmaMensagens.mostrar('A empresa está assinando os documentos. Em seguida, os convites serão gerados.', { id: 'assinatura-andamento', tipo: 'processando' });
        }
        for (const documento of documentos)
          dados.append("pdfs", documento.arquivo);
        dados.append(
          "dados",
          new Blob(
            [
              JSON.stringify({
                nomeEmpresa: empresa.value.trim(),
                empresaParticipante,
                nomeAssinatura,
                ...formalidades,
                idRequisicao: identificadorPedidoCriacao,
                documentos: documentos.map((documento) => ({
                  nome: documento.nome.trim(),
                })),
                signatarios: signatarios.map((pessoa) => ({
                  nome: pessoa.nome.trim(),
                  email: pessoa.email.trim(),
                  cpf: pessoa.cpf || null,
                  rg: pessoa.rg || null,
                  whatsapp: pessoa.whatsapp || null,
                  qualificacao: pessoa.qualificacao || null,
                  formaDeAutenticacao: pessoa.formaDeAutenticacao,
                  formaDeNotificacao: pessoa.formaDeNotificacao,
                  papel: pessoa.papel || "EM_NOME_PROPRIO",
                  representado: pessoa.representado || "",
                  fundamentoRepresentacao: pessoa.fundamentoRepresentacao || "",
                  campos: pessoa.campos.map(({ id, documento, ...campo }) => ({
                    ...campo,
                    rotacao: campo.rotacao || 0,
                    documento: documentos.findIndex(
                      (item) => item.id === documento,
                    ),
                  })),
                })),
              }),
            ],
            { type: "application/json" },
          ),
        );
        formularioCriacaoPendente = dados;
      }
      const resposta = await fetch("/api/envelopes/lote", {
        method: "POST",
        headers: credenciais(),
        body: formularioCriacaoPendente,
        cache: "no-store",
      });
      if (!resposta.ok && resposta.status >= 400 && resposta.status < 500) {
        formularioCriacaoPendente = null;
        identificadorPedidoCriacao = crypto.randomUUID();
      }
      await verificarResposta(resposta);
      const criado = await resposta.json();
      formularioCriacaoPendente = null;
      identificadorEnvelope = criado.identificador;
      convitesGerados = criado.convites;
      criacaoConcluida = true;
      history.replaceState(null, "", `?envelope=${identificadorEnvelope}`);
      mostrarConvites(criado.convites);
      await atualizarEstado();
      if (signatarios.some((pessoa) => pessoa.formaDeNotificacao.length)) {
        document.getElementById("enviar-notificacoes").hidden = false;
        FirmaMensagens.mostrar('Convites gerados. Enviando as notificações.', { id: 'assinatura-andamento', tipo: 'processando' });
        await enviarNotificacoes();
      }
    } catch (falha) {
      mostrarErro(falha.message);
    } finally {
      FirmaMensagens.fechar('assinatura-andamento');
      botao.disabled = criacaoConcluida;
    }
  });
function mostrarConvites(convites) {
  resultadoConvites.hidden = false;
  resultadoConvites.querySelector('h2').textContent = convites.length ? 'Convites gerados.' : 'Documento criado.';
  const lista = document.getElementById("lista-convites");
  lista.replaceChildren();
  for (const convite of convites) {
    const cartao = document.createElement("div");
    cartao.className = "cartao-convite";
    const nome = document.createElement("strong");
    nome.textContent = `${convite.nome}${convite.email ? ` · ${convite.email}` : ""}`;
    const link = document.createElement("a");
    link.href = `${location.origin}/convite/${convite.token}`;
    link.textContent = link.href;
    link.rel = "noreferrer";
    const copiar = document.createElement("button");
    copiar.type = "button";
    copiar.textContent = "COPIAR LINK";
    copiar.addEventListener("click", async () => {
      try {
        await navigator.clipboard.writeText(link.href);
        copiar.textContent = "COPIADO";
      } catch {
        mostrarErro(
          "Não foi possível copiar. Selecione e copie o link exibido.",
        );
      }
    });
    cartao.append(
      nome,
      document.createElement("br"),
      link,
      document.createElement("br"),
      copiar,
    );
    lista.append(cartao);
  }
  resultadoConvites.scrollIntoView({ block: "nearest" });
}

async function atualizarEstado() {
  if (!identificadorEnvelope) return;
  const resposta = await verificarResposta(
    await fetch(`/api/envelopes/${identificadorEnvelope}`, {
      headers: credenciais(),
      cache: "no-store",
    }),
  );
  let estado = await resposta.json();
  if (estado.assinaturaAutomatica && !estado.empresaAssinouEm && !estado.finalizadoEm && !estado.canceladoEm) {
    await verificarResposta(await fetch(`/api/envelopes/${identificadorEnvelope}/sincronizar-empresa`, {
      method: 'POST', headers: credenciais(), cache: 'no-store'
    }));
    estado = await (await verificarResposta(await fetch(`/api/envelopes/${identificadorEnvelope}`, {
      headers: credenciais(), cache: 'no-store'
    }))).json();
  }
  if ((estado.assinaturaAutomatica || estado.empresaAssinouEm) && !estado.finalizadoEm && !estado.canceladoEm
      && (estado.signatarios.length || estado.empresaParticipante) && estado.signatarios.every(pessoa => pessoa.assinadoEm)) {
    // Conclui também documentos que receberam o último aceite antes desta correção.
    await verificarResposta(await fetch(`/api/envelopes/${identificadorEnvelope}/concluir-automaticamente`, {
      method: 'POST', headers: credenciais(), cache: 'no-store'
    }));
    estado = await (await verificarResposta(await fetch(`/api/envelopes/${identificadorEnvelope}`, {
      headers: credenciais(), cache: 'no-store'
    }))).json();
  }
  assinaturaEmpresaRegistrada = !!estado.empresaAssinouEm;
  if (estado.finalizadoEm || estado.canceladoEm) clearInterval(acompanhamento);
  empresa.value = estado.nomeEmpresa;
  empresa.dispatchEvent(new Event("input"));
  resultadoConvites.hidden = false;
  document.getElementById("identificador-envelope").textContent =
    `Documento: ${identificadorEnvelope}`;
  const listaEstados = document.getElementById("estado-signatarios");
  listaEstados.replaceChildren();
  for (const signatario of estado.signatarios) {
    const item = document.createElement("li");
    item.textContent = `${signatario.nome} (${signatario.email}): ${
      signatario.assinadoEm
        ? `aceite registrado em ${new Date(signatario.assinadoEm).toLocaleString("pt-BR")}`
        : "aguardando aceite"
    }.`;
    if (
      !signatario.assinadoEm &&
      !signatario.recusadoEm &&
      !estado.finalizadoEm &&
      !estado.canceladoEm
    ) {
      const substituir = document.createElement("button");
      substituir.type = "button";
      substituir.className = "botao-carteira";
      substituir.textContent = "SUBSTITUIR LINK";
      substituir.addEventListener("click", async () => {
        substituir.disabled = true;
        try {
          const resposta = await verificarResposta(
            await fetch(
              `/api/envelopes/${identificadorEnvelope}/participantes/${signatario.identificador}/substituir-convite`,
              {
                method: "POST",
                headers: {
                  ...credenciais(),
                  "Content-Type": "application/json",
                },
                body: JSON.stringify({
                  motivo: document.getElementById("motivo-controle").value,
                }),
                cache: "no-store",
              },
            ),
          );
          const convite = await resposta.json();
          mostrarConvites([
            {
              nome: signatario.nome,
              email: signatario.email,
              token: convite.token,
            },
          ]);
        } catch (falha) {
          mostrarErro(falha.message);
        } finally {
          substituir.disabled = false;
        }
      });
      item.append(document.createElement("br"), substituir);
    }
    if (signatario.recusadoEm) item.append(" Recusa registrada.");
    listaEstados.append(item);
  }
  const concluidos = estado.signatarios.filter(
    (item) => item.assinadoEm,
  ).length;
  document.getElementById("estado-envelope").textContent = estado.canceladoEm
    ? `Envelope cancelado em ${new Date(estado.canceladoEm).toLocaleString("pt-BR")}. As evidências foram preservadas.`
    : `${concluidos + (estado.empresaParticipante && estado.empresaAssinouEm ? 1 : 0)}/${estado.signatarios.length + (estado.empresaParticipante ? 1 : 0)} signatários concluíram. Prazo: ${new Date(estado.expiraEm).toLocaleString("pt-BR")}.`;
  document.getElementById("gestao-evidencias").hidden = false;
  document.getElementById("prazo-preservacao").textContent =
    estado.reterEvidenciasAte
      ? `Preservação declarada até ${new Date(estado.reterEvidenciasAte).toLocaleDateString("pt-BR")}.`
      : "Documento anterior à política atual de preservação.";
  document.getElementById("cancelar-envelope").hidden =
    !!estado.finalizadoEm || !!estado.canceladoEm;
  finalizacao.hidden = !!estado.canceladoEm;
  document.getElementById('controles-assinatura-manual').hidden = !estado.empresaParticipante || estado.assinaturaAutomatica || !!estado.finalizadoEm;
  document.getElementById('titulo-finalizacao').textContent = estado.finalizadoEm
    ? 'DOCUMENTO CONCLUÍDO' : !estado.empresaParticipante ? 'ACOMPANHAMENTO DO DOCUMENTO' : estado.assinaturaAutomatica ? 'ASSINATURA AUTOMÁTICA DA EMPRESA' : 'ASSINATURA MANUAL DA EMPRESA';
  const estadoEmpresa = document.createElement('li');
  estadoEmpresa.textContent = estado.empresaAssinouEm ? `Empresa: assinou em ${new Date(estado.empresaAssinouEm).toLocaleString('pt-BR')}.` : 'Empresa: assinatura pendente.';
  if (estado.empresaParticipante) listaEstados.prepend(estadoEmpresa);
  document.getElementById('assinar-pela-empresa').hidden = !!estado.empresaAssinouEm || !!estado.finalizadoEm;
  document.getElementById("finalizar-envelope").hidden =
    concluidos !== estado.signatarios.length || !!estado.finalizadoEm;
  document.getElementById("aviso-finalizacao").textContent =
    !estado.empresaParticipante
      ? "O documento é protegido pelo certificado cadastrado e será concluído automaticamente após os aceites."
      : estado.assinaturaAutomatica
      ? "A empresa já assinou na criação. O documento será concluído automaticamente após o último participante assinar."
      : estado.empresaAssinouEm
        ? "A empresa já assinou. O documento será concluído automaticamente após os demais aceites."
        : concluidos === estado.signatarios.length
        ? "Todos os participantes assinaram. A conclusão usa o certificado cadastrado na empresa."
        : "O PDF já contém as assinaturas recebidas. A empresa está no modo manual.";
  const baixar = document.getElementById("baixar-final");
  baixar.hidden = false;
  baixar.textContent = estado.finalizadoEm
    ? "BAIXAR PDF FINAL ↗"
    : "BAIXAR PDF PARCIAL ↗";
  if (estado.finalizadoEm) {
    document.getElementById("finalizar-envelope").hidden = true;
    document.getElementById("aviso-finalizacao").textContent =
      `PDF finalizado em ${new Date(estado.finalizadoEm).toLocaleString("pt-BR")}.`;
    const verificacao = await assinaturasDigitais.conferirEndereco(
      `/api/envelopes/${identificadorEnvelope}/verificacao`,
      credenciais(),
      document.getElementById("verificacao-assinatura"),
    );
    document.getElementById("resumo-validacao").value =
      verificacao.resumoDocumento;
  }
}

document
  .getElementById("baixar-final")
  .addEventListener("click", async (evento) => {
    evento.preventDefault();
    try {
      const respostaPdf = await verificarResposta(
        await fetch(`/api/envelopes/${identificadorEnvelope}/pdf`, {
          headers: credenciais(),
          cache: "no-store",
        }),
      );
      if (enderecoFinal) URL.revokeObjectURL(enderecoFinal);
      enderecoFinal = URL.createObjectURL(await respostaPdf.blob());
      const temporario = document.createElement("a");
      temporario.href = enderecoFinal;
      temporario.download = "documento-assinado.pdf";
      temporario.click();
    } catch (erro) {
      mostrarErro(erro.message);
    }
  });

document
  .getElementById("finalizar-envelope")
  .addEventListener("click", async () => {
    erroPreparacao.hidden = true;
    const botao = document.getElementById("finalizar-envelope");
    if (botao.disabled) return;
    try {
      FirmaMensagens.mostrar('Concluindo o documento com a assinatura da empresa.', { id: 'assinatura-andamento', tipo: 'processando' });
      if (!document.getElementById("confirmar-finalizacao").checked)
        throw new Error("Confirme a revisão da versão atual do documento.");
      const dados = new FormData();
      if (!assinaturaEmpresaRegistrada) await adicionarColetaEmpresa(dados);
      dados.append("declaracaoPoderes", await politicaAssinatura.poderes());
      botao.disabled = true;
      const resposta = await verificarResposta(
        await fetch(`/api/envelopes/${identificadorEnvelope}/finalizar`, {
          method: "POST",
          headers: credenciais(),
          body: dados,
          cache: "no-store",
        }),
      );
      if (enderecoFinal) URL.revokeObjectURL(enderecoFinal);
      enderecoFinal = URL.createObjectURL(await resposta.blob());
      const baixar = document.getElementById("baixar-final");
      baixar.href = enderecoFinal;
      baixar.hidden = false;
      botao.hidden = true;
      try {
        await atualizarEstado();
      } catch {
        mostrarErro(
          "O PDF foi finalizado. Atualize o estado para consultar a verificação.",
        );
      }
    } catch (erro) {
      mostrarErro(erro.message);
      botao.disabled = false;
    } finally { FirmaMensagens.fechar('assinatura-andamento'); }
  });
window.addEventListener("pagehide", () => {
  if (enderecoImagem) URL.revokeObjectURL(enderecoImagem);
  if (enderecoFinal) URL.revokeObjectURL(enderecoFinal);
  chaveApi.value = "";
  token.value = "";
  formularioCriacaoPendente = null;
});
document.getElementById("adicionar-eu").addEventListener("click", async () => {
  try {
    await verificarCanais();
    const cadastro = recursos.empresa;
    const campos = ['EmpresaNome', 'EmpresaCNPJ', 'EmpresaResponsavelNome', 'EmpresaResponsavelCPF', 'EmpresaResponsavelEmail', 'EmpresaResponsavelCelular'];
    if (!cadastro || campos.some(campo => !String(cadastro[campo] || '').trim()))
      throw new Error('Complete os dados da empresa e do responsável na tela da empresa para se adicionar como assinante.');
    empresaParticipante = true;
    if (signatarios.length === 1 && !signatarios[0].nome.trim() && !signatarios[0].email.trim() && !signatarios[0].campos.length) signatarios.splice(0, 1);
    atualizarSignatarios();
  } catch (erro) { mostrarErro(erro.message); }
});
async function enviarNotificacoes() {
  const botao = document.getElementById("enviar-notificacoes");
  const estado = document.getElementById("estado-notificacoes");
  botao.disabled = true;
  estado.textContent = "Enviando notificações…";
  try {
    const resposta = await verificarResposta(
      await fetch(`/api/envelopes/${identificadorEnvelope}/notificar`, {
        method: "POST",
        headers: {
          ...credenciais(),
          "Content-Type": "application/json",
        },
        body: JSON.stringify(
          convitesGerados.map((respostaAtual) => respostaAtual.token),
        ),
      }),
    );
    const resultados = await resposta.json();
    estado.textContent = resultados
      .map(
        (registroAtual) =>
          `${registroAtual.nome} (${registroAtual.canal}): ${registroAtual.enviado ? "enviado" : "falha na entrega; compartilhe o link manualmente"}`,
      )
      .join(" · ");
    botao.hidden = resultados.every((registroAtual) => registroAtual.enviado);
  } catch (erro) {
    estado.textContent = `Convites criados. Não foi possível enviar notificações: ${erro.message} Compartilhe os links ou tente novamente.`;
  } finally {
    botao.disabled = false;
  }
}
document
  .getElementById("enviar-notificacoes")
  .addEventListener("click", enviarNotificacoes);

// Renderiza o signatário inicial
document.addEventListener("DOMContentLoaded", () => {
  if (!token.value) token.value = localStorage.getItem("assineai-token") || "";
  if (!chaveApi.value)
    chaveApi.value = localStorage.getItem("assineai-chave") || "";
  atualizarSignatarios();
  atualizarControlesPdf();
});

document
  .getElementById("baixar-evidencias")
  .addEventListener("click", async (evento) => {
    const botao = evento.currentTarget;
    if (botao.disabled) return;
    botao.disabled = true;
    try {
      const resposta = await verificarResposta(
        await fetch(
          `/api/envelopes/${identificadorEnvelope}/evidencias/pacote`,
          { headers: credenciais(), cache: "no-store" },
        ),
      );
      const endereco = URL.createObjectURL(await resposta.blob());
      const link = document.createElement("a");
      link.href = endereco;
      link.download = `evidencias-${identificadorEnvelope}.zip`;
      link.click();
      setTimeout(() => URL.revokeObjectURL(endereco), 1000);
    } catch (falha) {
      mostrarErro(falha.message);
    } finally {
      botao.disabled = false;
    }
  });
document
  .getElementById("cancelar-envelope")
  .addEventListener("click", async (evento) => {
    const botao = evento.currentTarget;
    if (botao.disabled) return;
    botao.disabled = true;
    try {
      await verificarResposta(
        await fetch(`/api/envelopes/${identificadorEnvelope}/cancelar`, {
          method: "POST",
          headers: { ...credenciais(), "Content-Type": "application/json" },
          body: JSON.stringify({
            motivo: document.getElementById("motivo-controle").value,
          }),
          cache: "no-store",
        }),
      );
      await atualizarEstado();
    } catch (falha) {
      mostrarErro(falha.message);
    } finally {
      botao.disabled = false;
    }
  });
document
  .getElementById("anexar-relatorio")
  .addEventListener("click", async (evento) => {
    const botao = evento.currentTarget;
    if (botao.disabled) return;
    botao.disabled = true;
    try {
      const arquivo = document.getElementById("relatorio-externo").files[0];
      if (!arquivo)
        throw new Error("Selecione o relatório original da consulta externa.");
      const dados = new FormData();
      dados.append("relatorio", arquivo);
      dados.append(
        "resumoDocumento",
        document.getElementById("resumo-validacao").value,
      );
      dados.append(
        "referenciaConsulta",
        document.getElementById("referencia-validacao").value,
      );
      dados.append(
        "resultadoDeclarado",
        document.getElementById("resultado-validacao").value,
      );
      await verificarResposta(
        await fetch(
          `/api/envelopes/${identificadorEnvelope}/relatorios-externos`,
          {
            method: "POST",
            headers: credenciais(),
            body: dados,
            cache: "no-store",
          },
        ),
      );
      document.getElementById("estado-relatorio").textContent =
        "Relatório preservado como evidência declarada pelo responsável. Não houve aprovação automática pelo aFIRMA.";
    } catch (falha) {
      mostrarErro(falha.message);
    } finally {
      botao.disabled = false;
    }
  });

document.addEventListener('firma:acesso-conectado', () => verificarCanais().catch(falha => mostrarErro(falha.message)));

document.addEventListener('firma:desconectado', () => {
  document.getElementById('confirmar-finalizacao').checked = false;
});

async function acompanharAssinaturas() {
  if (!identificadorEnvelope || !empresaCarregada || document.hidden || consultandoEstado || !acessoFirma.ativa()) return;
  consultandoEstado = true;
  try { await atualizarEstado(); }
  catch (falha) { mostrarErro(falha.message); }
  finally { consultandoEstado = false; }
}
acompanhamento = setInterval(acompanharAssinaturas, 10000);
window.addEventListener('pagehide', () => clearInterval(acompanhamento));
window.addEventListener('pageshow', evento => {
  if (evento.persisted) acompanhamento = setInterval(acompanharAssinaturas, 10000);
});
document.addEventListener('visibilitychange', () => { if (!document.hidden) acompanharAssinaturas(); });

document.getElementById('assinar-pela-empresa').addEventListener('click', async evento => {
  const botao = evento.currentTarget;
  if (botao.disabled) return;
  botao.disabled = true;
  try {
    FirmaMensagens.mostrar('Registrando a assinatura da empresa. Autorize a localização quando o navegador pedir.', { id: 'assinatura-andamento', tipo: 'processando' });
    const dados = new FormData();
    await adicionarColetaEmpresa(dados);
    dados.append('declaracaoPoderes', await politicaAssinatura.poderes());
    await verificarResposta(await fetch(`/api/envelopes/${identificadorEnvelope}/assinar-empresa`, {
      method: 'POST', headers: credenciais(), body: dados, cache: 'no-store'
    }));
    await atualizarEstado();
  } catch (falha) { mostrarErro(falha.message); }
  finally { botao.disabled = false; FirmaMensagens.fechar('assinatura-andamento'); }
});

async function adicionarColetaEmpresa(dados) {
  const coleta = await coletarDadosAssinatura('/api/envelopes/coleta-empresa');
  dados.append('localizacaoEmpresa', JSON.stringify(coleta.localizacao));
  dados.append('ipEmpresa', coleta.ipNavegador);
}

// A lista é nossa para manter a cara da FIRMA sem impedir uma qualificação digitada.
function prepararListaQualificacaoFirma(grupoQualificacao, entradaQualificacao, identificadorSignatario) {
  const identificadorLista = `qualificacao-firma-${identificadorSignatario}`;
  const opcoesQualificacao = [...document.querySelectorAll("#qualificacoes-signatario option")].map(opcaoQualificacao => opcaoQualificacao.value);
  const listaQualificacao = document.createElement("ul");
  listaQualificacao.id = identificadorLista;
  listaQualificacao.className = "lista-qualificacao-firma";
  listaQualificacao.setAttribute("role", "listbox");
  listaQualificacao.setAttribute("aria-label", "Vai assinar como?");
  listaQualificacao.hidden = true;
  grupoQualificacao.classList.add("qualificacao-firma");
  const caixaEntradaQualificacao = document.createElement("div");
  caixaEntradaQualificacao.className = "entrada-qualificacao-firma";
  entradaQualificacao.before(caixaEntradaQualificacao);
  caixaEntradaQualificacao.append(entradaQualificacao);
  const setaQualificacao = document.createElement("span");
  setaQualificacao.className = "seta-qualificacao-firma";
  setaQualificacao.textContent = "⌄";
  setaQualificacao.setAttribute("aria-hidden", "true");
  caixaEntradaQualificacao.append(setaQualificacao);
  entradaQualificacao.id = `${identificadorLista}-entrada`;
  grupoQualificacao.querySelector("label").htmlFor = entradaQualificacao.id;
  entradaQualificacao.setAttribute("role", "combobox");
  entradaQualificacao.setAttribute("aria-autocomplete", "list");
  entradaQualificacao.setAttribute("aria-controls", identificadorLista);
  entradaQualificacao.setAttribute("aria-expanded", "false");
  entradaQualificacao.autocomplete = "off";
  let indiceOpcaoAtiva = -1;

  function fecharListaQualificacao() {
    listaQualificacao.hidden = true;
    entradaQualificacao.setAttribute("aria-expanded", "false");
    entradaQualificacao.removeAttribute("aria-activedescendant");
    indiceOpcaoAtiva = -1;
  }

  function destacarOpcaoQualificacao() {
    [...listaQualificacao.children].forEach((opcaoQualificacao, indiceOpcao) => {
      opcaoQualificacao.setAttribute("aria-selected", String(indiceOpcao === indiceOpcaoAtiva));
    });
    const opcaoAtiva = listaQualificacao.children[indiceOpcaoAtiva];
    if (opcaoAtiva) {
      entradaQualificacao.setAttribute("aria-activedescendant", opcaoAtiva.id);
      opcaoAtiva.scrollIntoView({ block: "nearest" });
    }
  }

  function selecionarQualificacao(valorQualificacao) {
    entradaQualificacao.value = valorQualificacao;
    entradaQualificacao.dispatchEvent(new Event("input", { bubbles: true }));
    fecharListaQualificacao();
  }

  function abrirListaQualificacao() {
    listaQualificacao.replaceChildren();
    indiceOpcaoAtiva = -1;
    entradaQualificacao.removeAttribute("aria-activedescendant");
    const filtroQualificacao = entradaQualificacao.value.trim().toLocaleLowerCase("pt-BR");
    for (const valorQualificacao of opcoesQualificacao.filter(valorOpcao => valorOpcao.toLocaleLowerCase("pt-BR").includes(filtroQualificacao))) {
      const opcaoQualificacao = document.createElement("li");
      opcaoQualificacao.id = `${identificadorLista}-${listaQualificacao.children.length}`;
      opcaoQualificacao.setAttribute("role", "option");
      opcaoQualificacao.setAttribute("aria-selected", "false");
      opcaoQualificacao.textContent = valorQualificacao;
      opcaoQualificacao.addEventListener("pointerdown", eventoOpcao => eventoOpcao.preventDefault());
      opcaoQualificacao.addEventListener("click", () => selecionarQualificacao(valorQualificacao));
      listaQualificacao.append(opcaoQualificacao);
    }
    listaQualificacao.hidden = listaQualificacao.children.length === 0;
    entradaQualificacao.setAttribute("aria-expanded", String(!listaQualificacao.hidden));
  }

  entradaQualificacao.addEventListener("focus", abrirListaQualificacao);
  entradaQualificacao.addEventListener("click", abrirListaQualificacao);
  entradaQualificacao.addEventListener("input", abrirListaQualificacao);
  entradaQualificacao.addEventListener("blur", fecharListaQualificacao);
  entradaQualificacao.addEventListener("keydown", eventoTeclado => {
    if (eventoTeclado.key === "Escape" || eventoTeclado.key === "Tab") return fecharListaQualificacao();
    if (eventoTeclado.key === "Enter" && !listaQualificacao.hidden && indiceOpcaoAtiva >= 0) {
      eventoTeclado.preventDefault();
      return selecionarQualificacao(listaQualificacao.children[indiceOpcaoAtiva].textContent);
    }
    if (!["ArrowDown", "ArrowUp"].includes(eventoTeclado.key)) return;
    eventoTeclado.preventDefault();
    if (listaQualificacao.hidden) abrirListaQualificacao();
    const quantidadeOpcoes = listaQualificacao.children.length;
    if (!quantidadeOpcoes) return;
    indiceOpcaoAtiva = indiceOpcaoAtiva < 0
      ? (eventoTeclado.key === "ArrowDown" ? 0 : quantidadeOpcoes - 1)
      : (indiceOpcaoAtiva + (eventoTeclado.key === "ArrowDown" ? 1 : -1) + quantidadeOpcoes) % quantidadeOpcoes;
    destacarOpcaoQualificacao();
  });
  caixaEntradaQualificacao.append(listaQualificacao);
}

// Mantenho o select como fonte do valor e uso a lista da FIRMA na tela.
function prepararListaAtuacaoFirma(grupoAtuacao, rotuloAtuacao, seletorAtuacao, identificadorSignatario) {
  const caixaAtuacao = document.createElement("div");
  caixaAtuacao.className = "qualificacao-firma";
  const botaoAtuacao = document.createElement("button");
  botaoAtuacao.type = "button";
  botaoAtuacao.className = "seletor-atuacao-firma";
  botaoAtuacao.id = `atuacao-firma-${identificadorSignatario}`;
  rotuloAtuacao.htmlFor = botaoAtuacao.id;
  const listaAtuacao = document.createElement("ul");
  listaAtuacao.id = `${botaoAtuacao.id}-opcoes`;
  listaAtuacao.className = "lista-qualificacao-firma";
  listaAtuacao.setAttribute("role", "listbox");
  listaAtuacao.setAttribute("aria-label", "Atuação do participante");
  listaAtuacao.hidden = true;
  botaoAtuacao.setAttribute("role", "combobox");
  botaoAtuacao.setAttribute("aria-haspopup", "listbox");
  botaoAtuacao.setAttribute("aria-controls", listaAtuacao.id);
  botaoAtuacao.setAttribute("aria-expanded", "false");
  seletorAtuacao.hidden = true;
  let indiceAtuacaoAtiva = seletorAtuacao.selectedIndex;

  function fecharListaAtuacao() {
    listaAtuacao.hidden = true;
    botaoAtuacao.setAttribute("aria-expanded", "false");
    botaoAtuacao.removeAttribute("aria-activedescendant");
  }

  function atualizarAtuacaoSelecionada() {
    botaoAtuacao.textContent = seletorAtuacao.selectedOptions[0].textContent;
    [...listaAtuacao.children].forEach((opcaoAtuacao, indiceOpcao) => {
      opcaoAtuacao.setAttribute("aria-selected", String(indiceOpcao === seletorAtuacao.selectedIndex));
      opcaoAtuacao.classList.toggle("opcao-em-foco", indiceOpcao === indiceAtuacaoAtiva);
    });
    if (!listaAtuacao.hidden) botaoAtuacao.setAttribute("aria-activedescendant", listaAtuacao.children[indiceAtuacaoAtiva].id);
  }

  function selecionarAtuacao(indiceOpcao) {
    seletorAtuacao.selectedIndex = indiceOpcao;
    indiceAtuacaoAtiva = indiceOpcao;
    seletorAtuacao.dispatchEvent(new Event("change", { bubbles: true }));
    atualizarAtuacaoSelecionada();
    fecharListaAtuacao();
  }

  [...seletorAtuacao.options].forEach((opcaoOriginal, indiceOpcao) => {
    const opcaoAtuacao = document.createElement("li");
    opcaoAtuacao.id = `${listaAtuacao.id}-${indiceOpcao}`;
    opcaoAtuacao.setAttribute("role", "option");
    opcaoAtuacao.textContent = opcaoOriginal.textContent;
    opcaoAtuacao.addEventListener("pointerdown", eventoOpcao => eventoOpcao.preventDefault());
    opcaoAtuacao.addEventListener("click", () => selecionarAtuacao(indiceOpcao));
    listaAtuacao.append(opcaoAtuacao);
  });

  function abrirListaAtuacao() {
    listaAtuacao.hidden = false;
    indiceAtuacaoAtiva = seletorAtuacao.selectedIndex;
    botaoAtuacao.setAttribute("aria-expanded", "true");
    atualizarAtuacaoSelecionada();
  }

  botaoAtuacao.addEventListener("click", () => listaAtuacao.hidden ? abrirListaAtuacao() : fecharListaAtuacao());
  botaoAtuacao.addEventListener("blur", fecharListaAtuacao);
  botaoAtuacao.addEventListener("keydown", eventoTeclado => {
    if (["Escape", "Tab"].includes(eventoTeclado.key)) return fecharListaAtuacao();
    if (["Enter", " "].includes(eventoTeclado.key)) {
      eventoTeclado.preventDefault();
      return listaAtuacao.hidden ? abrirListaAtuacao() : selecionarAtuacao(indiceAtuacaoAtiva);
    }
    if (!["ArrowDown", "ArrowUp", "Home", "End"].includes(eventoTeclado.key)) return;
    eventoTeclado.preventDefault();
    if (listaAtuacao.hidden) abrirListaAtuacao();
    else if (eventoTeclado.key === "Home") indiceAtuacaoAtiva = 0;
    else if (eventoTeclado.key === "End") indiceAtuacaoAtiva = seletorAtuacao.options.length - 1;
    else indiceAtuacaoAtiva = (indiceAtuacaoAtiva + (eventoTeclado.key === "ArrowDown" ? 1 : -1) + seletorAtuacao.options.length) % seletorAtuacao.options.length;
    atualizarAtuacaoSelecionada();
  });
  seletorAtuacao.addEventListener("change", atualizarAtuacaoSelecionada);
  caixaAtuacao.append(botaoAtuacao, listaAtuacao);
  grupoAtuacao.insertBefore(caixaAtuacao, seletorAtuacao);
  atualizarAtuacaoSelecionada();
}
