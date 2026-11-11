const formulario = document.getElementById("formulario-assinatura");
const entradaPdf = document.getElementById("pdf");
const zonaPdf = document.getElementById("zona-pdf");
const nomePdf = document.getElementById("nome-pdf");
const entradaCertificado = document.getElementById("certificado");
const removerPdf = document.getElementById("remover-pdf");
const removerCertificado = document.getElementById("remover-certificado");
const estadoPdf = document.getElementById("estado-pdf");
const estadoCertificado = document.getElementById("estado-certificado");
const botaoAssinar = document.getElementById("botao-assinar");
const resultado = document.getElementById("resultado");
const baixar = document.getElementById("baixar");
const caixaErro = document.getElementById("erro");
let enderecoArquivo = null;
let enderecoRevisao = null;
let identificadorAssinaturaDireta = null;
let identificadorPedidoDireto = crypto.randomUUID();
let certificadoConferido = false;

function invalidarConferencia() {
  certificadoConferido = false;
  document.getElementById("dados-certificado").hidden = true;
  document.getElementById("confirmar-revisao").checked = false;
}

function atualizarRevisaoPdf() {
  if (enderecoRevisao) URL.revokeObjectURL(enderecoRevisao);
  const arquivo = entradaPdf.files[0];
  enderecoRevisao = arquivo ? URL.createObjectURL(arquivo) : null;
  const link = document.getElementById("revisar-pdf");
  link.hidden = !arquivo;
  if (enderecoRevisao) link.href = enderecoRevisao;
  else link.removeAttribute("href");
  document.getElementById("confirmar-revisao").checked = false;
}

for (const entrada of [entradaCertificado, document.getElementById("senha")]) {
  entrada.addEventListener("input", invalidarConferencia);
  entrada.addEventListener("change", () => {
    invalidarConferencia();
    identificadorPedidoDireto = crypto.randomUUID();
  });
}

document
  .getElementById("conferir-certificado")
  .addEventListener("click", async (evento) => {
    const botao = evento.currentTarget;
    if (botao.disabled) return;
    botao.disabled = true;
    caixaErro.hidden = true;
    try {
      const arquivo = entradaCertificado.files[0];
      const senha = document.getElementById("senha").value;
      await assinaturasDigitais.consultarCertificado(
        arquivo,
        senha,
        {
          "X-API-Key": document.getElementById("chave-api").value.trim(),
          Authorization: `Bearer ${document.getElementById("token").value.trim()}`,
        },
        document.getElementById("dados-certificado"),
      );
      certificadoConferido =
        arquivo === entradaCertificado.files[0] &&
        senha === document.getElementById("senha").value;
      if (!certificadoConferido) invalidarConferencia();
    } catch (falha) {
      invalidarConferencia();
      caixaErro.textContent = falha.message;
      caixaErro.hidden = false;
    } finally {
      botao.disabled = false;
    }
  });
const nomeEmpresa = document.getElementById("nome-empresa");
function adicionarPersonalizacao(dados) {
  // O servidor escolhe o modelo da empresa autenticada, sem aceitar uma versão avulsa.
  dados.append("paginaPersonalizada", "true");
}
function mostrarNomePdf() {
  const arquivo = entradaPdf.files[0];
  identificadorPedidoDireto = crypto.randomUUID();
  atualizarRevisaoPdf();
  nomePdf.textContent = arquivo?.name || "Clique para escolher seu PDF";
  removerPdf.hidden = !arquivo;
  estadoPdf.textContent = arquivo
    ? "PDF selecionado."
    : "Nenhum PDF selecionado.";
}
function limparResultado() {
  resultado.hidden = true;
  document.getElementById("verificacao-assinatura").hidden = true;
  baixar.removeAttribute("href");
  if (enderecoArquivo) {
    URL.revokeObjectURL(enderecoArquivo);
    enderecoArquivo = null;
  }
}
entradaPdf.addEventListener("change", () => {
  mostrarNomePdf();
  limparResultado();
});
entradaCertificado.addEventListener("change", () => {
  const arquivo = entradaCertificado.files[0];
  removerCertificado.hidden = !arquivo;
  estadoCertificado.textContent = arquivo
    ? `Certificado selecionado: ${arquivo.name}.`
    : "Nenhum certificado selecionado.";
  limparResultado();
});
removerPdf.addEventListener("click", () => {
  entradaPdf.value = "";
  mostrarNomePdf();
  estadoPdf.textContent = "PDF removido da seleção. Escolha outro arquivo.";
  limparResultado();
});
removerCertificado.addEventListener("click", () => {
  entradaCertificado.value = "";
  invalidarConferencia();
  removerCertificado.hidden = true;
  estadoCertificado.textContent =
    "Certificado removido da seleção. Escolha outro arquivo.";
  limparResultado();
});
for (const botao of document.querySelectorAll(".botao-olho")) {
  botao.addEventListener("click", () => {
    const entrada = document.getElementById(botao.dataset.alvo);
    const visivel = entrada.type === "password";
    entrada.type = visivel ? "text" : "password";
    botao.setAttribute("aria-pressed", String(visivel));
    botao.setAttribute(
      "aria-label",
      `${visivel ? "Ocultar" : "Mostrar"} ${botao.dataset.alvo === "senha" ? "senha do certificado" : botao.dataset.alvo === "token" ? "token JWT" : "chave da API"}`,
    );
  });
}
for (const evento of ["dragenter", "dragover"]) {
  zonaPdf.addEventListener(evento, (ocorrencia) => {
    ocorrencia.preventDefault();
    zonaPdf.classList.add("arrastando");
  });
}
for (const evento of ["dragleave", "drop"]) {
  zonaPdf.addEventListener(evento, (ocorrencia) => {
    ocorrencia.preventDefault();
    zonaPdf.classList.remove("arrastando");
  });
}
zonaPdf.addEventListener("drop", (ocorrencia) => {
  const arquivo = ocorrencia.dataTransfer.files[0];
  if (
    arquivo?.type === "application/pdf" ||
    arquivo?.name.toLowerCase().endsWith(".pdf")
  ) {
    const arquivos = new DataTransfer();
    arquivos.items.add(arquivo);
    entradaPdf.files = arquivos.files;
    mostrarNomePdf();
    limparResultado();
  } else {
    caixaErro.textContent = "Escolha um arquivo PDF.";
    caixaErro.hidden = false;
  }
});
formulario.addEventListener("submit", async (ocorrencia) => {
  ocorrencia.preventDefault();
  if (botaoAssinar.disabled) return;
  caixaErro.hidden = true;
  limparResultado();
  const arquivo = entradaPdf.files[0];
  if (
    !arquivo ||
    arquivo.size > 20 * 1024 * 1024 ||
    !(
      arquivo.type === "application/pdf" ||
      arquivo.name.toLowerCase().endsWith(".pdf")
    )
  ) {
    caixaErro.textContent = "Escolha um PDF de até 20 MB.";
    caixaErro.hidden = false;
    return;
  }

  if (
    !certificadoConferido ||
    !document.getElementById("confirmar-revisao").checked
  ) {
    caixaErro.textContent =
      "Confira o certificado e confirme a revisão do documento antes de assinar.";
    caixaErro.hidden = false;
    return;
  }
  botaoAssinar.disabled = true;
  botaoAssinar.setAttribute("aria-busy", "true");
  botaoAssinar.querySelector("span").textContent = "ASSINANDO DOCUMENTO...";
  try {
    const dados = new FormData(formulario);
    dados.append("idRequisicao", identificadorPedidoDireto);
    const formalidades = await politicaAssinatura.formalidades();
    dados.append("tipoDocumento", formalidades.tipoDocumento);
    dados.append("declaracaoFormalidades", formalidades.declaracaoFormalidades);
    dados.append("declaracaoPoderes", await politicaAssinatura.poderes());
    adicionarPersonalizacao(dados);
    const chave = document.getElementById("chave-api").value.trim();
    const token = document.getElementById("token").value.trim();
    const resposta = await fetch(formulario.action, {
      method: "POST",
      headers: {
        "X-API-Key": chave,
        Authorization: `Bearer ${token}`,
      },
      body: dados,
      cache: "no-store",
    });
    if (!resposta.ok) {
      const mensagens = {
        400: "Confira PDF, certificado e senha. Tente novamente.",
        401: "Chave da API ou token inválido.",
        403: "Token sem permissão para assinar PDF.",
        413: "Arquivo acima do limite permitido.",
        415: "O arquivo enviado não é um PDF válido.",
        429: "Muitas tentativas. Aguarde um minuto.",
        500: "Falha interna ao assinar. Consulte os logs do servidor.",
        503: "Serviço indisponível. Tente novamente em instantes.",
      };
      let mensagem =
        mensagens[resposta.status] ||
        "Não foi possível assinar o documento. Tente novamente.";
      if (
        [400, 413, 415, 500, 503].includes(resposta.status) &&
        resposta.headers.get("content-type")?.includes("application/json")
      ) {
        const detalhe = await resposta.json().catch(() => null);
        if (typeof detalhe?.mensagem === "string" && detalhe.mensagem.trim())
          mensagem = detalhe.mensagem;
      }
      throw new Error(mensagem);
    }

    identificadorAssinaturaDireta = resposta.headers.get(
      "X-Envelope-Assinatura",
    );
    document.getElementById("evidencias-diretas").hidden =
      !identificadorAssinaturaDireta;
    const arquivoAssinado = await resposta.blob();
    if (!arquivoAssinado.size)
      throw new Error("O documento assinado veio vazio. Tente novamente.");
    enderecoArquivo = URL.createObjectURL(arquivoAssinado);
    baixar.href = enderecoArquivo;
    resultado.hidden = false;
    resultado.scrollIntoView({ block: "nearest" });
    document.getElementById("senha").value = "";
    entradaCertificado.value = "";
    removerCertificado.hidden = true;
    estadoCertificado.textContent =
      "Certificado removido da seleção após a assinatura.";
    invalidarConferencia();
    try {
      await assinaturasDigitais.conferirEndereco(
        `/api/envelopes/${identificadorAssinaturaDireta}/verificacao`,
        { "X-API-Key": chave, Authorization: `Bearer ${token}` },
        document.getElementById("verificacao-assinatura"),
      );
    } catch {
      caixaErro.textContent =
        "O PDF foi assinado e está disponível. A consulta detalhada da verificação falhou.";
      caixaErro.hidden = false;
    }
  } catch (erro) {
    caixaErro.textContent =
      erro instanceof TypeError
        ? "Falha de conexão. Tente novamente."
        : erro.message;
    caixaErro.hidden = false;
  } finally {
    botaoAssinar.disabled = false;
    botaoAssinar.removeAttribute("aria-busy");
    botaoAssinar.querySelector("span").textContent = "ASSINAR DOCUMENTO";
  }
});
window.addEventListener("pagehide", () => {
  if (enderecoArquivo) URL.revokeObjectURL(enderecoArquivo);
  if (enderecoRevisao) URL.revokeObjectURL(enderecoRevisao);
  document.getElementById("senha").value = "";
  document.getElementById("chave-api").value = "";
  document.getElementById("token").value = "";
  entradaCertificado.value = "";
});
async function mostrarPadraoDaEmpresa() {
  const token = document.getElementById("token").value.trim();
  if (!token) return;
  const estado = document.getElementById("estado-modelo-pagina");
  try {
    const resposta = await fetch("/api/assineai/empresa", {
      headers: { Authorization: `Bearer ${token}` },
      cache: "no-store",
    });
    if (!resposta.ok)
      throw Error("Confira seu acesso para consultar o padrão da empresa.");
    const empresa = await resposta.json();
    if (token !== document.getElementById("token").value.trim()) return;
    const biblioteca = empresa.EmpresaTemplates;
    const modelo = biblioteca?.modelos?.find(
      (item) => item.id === biblioteca.ativo,
    );
    nomeEmpresa.value = empresa.EmpresaNome || "";
    estado.textContent =
      "Padrão da empresa: " +
      (modelo?.nome || "daFIRMA / Relatório de assinaturas") +
      ". Aplicado automaticamente ao assinar.";
  } catch (erro) {
    if (token === document.getElementById("token").value.trim())
      estado.textContent = erro.message;
  }
}
const tokenDaTela = document.getElementById("token");
if (!tokenDaTela.value)
  tokenDaTela.value = localStorage.getItem("assineai-token") || "";
tokenDaTela.addEventListener("change", mostrarPadraoDaEmpresa);
mostrarPadraoDaEmpresa();

document
  .getElementById("baixar-evidencias-diretas")
  .addEventListener("click", async (evento) => {
    const botao = evento.currentTarget;
    if (!identificadorAssinaturaDireta || botao.disabled) return;
    botao.disabled = true;
    try {
      const resposta = await fetch(
        `/api/envelopes/${identificadorAssinaturaDireta}/evidencias/pacote`,
        {
          headers: {
            "X-API-Key": document.getElementById("chave-api").value.trim(),
            Authorization: `Bearer ${document.getElementById("token").value.trim()}`,
          },
          cache: "no-store",
        },
      );
      if (!resposta.ok)
        throw new Error(
          "Não foi possível baixar as evidências. Confira suas credenciais.",
        );
      const endereco = URL.createObjectURL(await resposta.blob());
      const link = document.createElement("a");
      link.href = endereco;
      link.download = `evidencias-${identificadorAssinaturaDireta}.zip`;
      link.click();
      setTimeout(() => URL.revokeObjectURL(endereco), 1000);
    } catch (falha) {
      caixaErro.textContent = falha.message;
      caixaErro.hidden = false;
    } finally {
      botao.disabled = false;
    }
  });

document.addEventListener('firma:acesso-conectado', mostrarPadraoDaEmpresa);

document.addEventListener('firma:desconectado', () => {
  invalidarConferencia();
  document.getElementById('senha').value = '';
  entradaCertificado.value = '';
});
