const conviteToken = location.pathname.split("/").filter(Boolean).at(-1);
const mensagemErro = document.getElementById("erro");
const visualizador = document.getElementById("visualizador");
const imagemPagina = document.getElementById("imagem-pagina");
const camadaCampos = document.getElementById("camada-campos");
let temporizadorReenvioCodigo = null;
let prazoReenvioCodigo = 0;
let envioCodigoDisponivel = false;
let codigoJaEnviado = false;

function atualizarContadorReenvio() {
  const segundosRestantes = Math.max(0, Math.ceil((prazoReenvioCodigo - Date.now()) / 1000));
  const botaoEnviarCodigo = document.getElementById("solicitar-codigo");
  botaoEnviarCodigo.disabled = !envioCodigoDisponivel || segundosRestantes > 0 || botaoEnviarCodigo.hasAttribute("aria-busy");
  botaoEnviarCodigo.textContent = codigoJaEnviado ? "REENVIAR CÓDIGO" : "ENVIAR CÓDIGO";
  document.getElementById("contador-reenvio").hidden = segundosRestantes === 0;
  document.getElementById("tempo-reenvio").textContent = `${String(Math.floor(segundosRestantes / 60)).padStart(2, "0")}:${String(segundosRestantes % 60).padStart(2, "0")}`;
  document.getElementById("progresso-reenvio").value = Math.min(60, segundosRestantes);
  if (!segundosRestantes) clearInterval(temporizadorReenvioCodigo);
}

function sincronizarReenvioCodigo(acessoConvite) {
  clearInterval(temporizadorReenvioCodigo);
  envioCodigoDisponivel = acessoConvite.envioDisponivel;
  const segundosParaReenvio = acessoConvite.segundosParaReenvio || 0;
  if (segundosParaReenvio > 0) codigoJaEnviado = true;
  prazoReenvioCodigo = Date.now() + segundosParaReenvio * 1000;
  atualizarContadorReenvio();
  if (segundosParaReenvio > 0) temporizadorReenvioCodigo = setInterval(atualizarContadorReenvio, 250);
}

let dadosConvite = null;
let acessoConfirmado = false;
let temporizadorSessao = null;

function bloquearDocumento() {
  acessoConfirmado = false;
  documentoDisponivelParaAceite = false;
  document.getElementById("conteudo-autenticado").hidden = true;
  document.getElementById("autenticacao-convite").hidden = false;
  document.getElementById("codigo-acesso").value = "";
  atualizarPaginaDocumentoAssinado(false);
  atualizarPassosConvite(0);
  if (temporizadorSessao) clearTimeout(temporizadorSessao);
  if (transmissao) {
    transmissao.getTracks().forEach((trilha) => trilha.stop());
    transmissao = null;
  }
  for (const endereco of [
    enderecoImagem,
    enderecoDocumento,
    enderecoFinal,
    enderecoSelfie,
  ])
    if (endereco) URL.revokeObjectURL(endereco);
  enderecoImagem = enderecoDocumento = enderecoFinal = enderecoSelfie = null;
  document.getElementById("baixar-original").removeAttribute("href");
  document.getElementById("arquivo-selfie").value = "";
  selfieCapturada = null;
  envioPendente = null;
}
let paginaAtual = 1;
let totalPaginas = 1;
let enderecoImagem = null;
let enderecoDocumento = null;
let enderecoFinal = null;
let selfieCapturada = null;
let transmissao = null;
let enderecoSelfie = null;
let carregandoDocumento = false;
let carregandoPagina = false;
let enviandoAssinatura = false;
let envioPendente = null;
let documentoDisponivelParaAceite = false;
const paginasLidas = new Set();

function atualizarLeitura() {
  document.getElementById("leitura-paginas").textContent =
    `${paginasLidas.size} de ${totalPaginas} páginas abertas nesta versão.`;
  document.getElementById("aceite").disabled =
    !documentoDisponivelParaAceite || paginasLidas.size !== totalPaginas;
  document.getElementById("enviar-assinatura").disabled =
    enviandoAssinatura ||
    !documentoDisponivelParaAceite ||
    paginasLidas.size !== totalPaginas;
}

function mostrarSelfie(imagem) {
  if (enderecoSelfie) URL.revokeObjectURL(enderecoSelfie);
  enderecoSelfie = imagem ? URL.createObjectURL(imagem) : null;
  const previa = document.getElementById("previa-selfie");
  if (enderecoSelfie) previa.src = enderecoSelfie;
  else previa.removeAttribute("src");
  previa.hidden = !imagem;
}
const desenhos = new Map();
function erro(mensagem) {
  mensagemErro.textContent = mensagem;
  mensagemErro.hidden = false;
  mensagemErro.scrollIntoView({
    block: "nearest",
  });
}
async function verificar(resposta) {
  if (resposta.ok) return resposta;
  if (resposta.status === 401) bloquearDocumento();
  const dados = await resposta.json().catch(() => null);
  throw new Error(dados?.mensagem || `Operação recusada (${resposta.status}).`);
}
function prepararDesenho(id) {
  const tela = document.getElementById(id);
  const contexto = tela.getContext("2d");
  contexto.fillStyle = "white";
  contexto.fillRect(0, 0, tela.width, tela.height);
  contexto.strokeStyle = "#0E2117";
  contexto.lineWidth = 4;
  contexto.lineCap = "round";
  contexto.lineJoin = "round";
  desenhos.set(id, false);
  let desenhando = false;
  function ponto(evento) {
    const caixa = tela.getBoundingClientRect();
    return {
      x: ((evento.clientX - caixa.left) * tela.width) / caixa.width,
      y: ((evento.clientY - caixa.top) * tela.height) / caixa.height,
    };
  }
  tela.addEventListener("pointerdown", (evento) => {
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
  tela.addEventListener("pointermove", (evento) => {
    if (!desenhando) return;
    const atual = ponto(evento);
    contexto.lineTo(atual.x, atual.y);
    contexto.stroke();
  });
  tela.addEventListener("pointerup", () => {
    desenhando = false;
  });
  tela.addEventListener("pointercancel", () => {
    desenhando = false;
  });
}
for (const id of ["desenho-assinatura", "desenho-rubrica"]) prepararDesenho(id);
for (const botao of document.querySelectorAll("[data-limpar]")) {
  botao.addEventListener("click", () => {
    const id = botao.dataset.limpar;
    const tela = document.getElementById(id);
    const contexto = tela.getContext("2d");
    contexto.fillStyle = "white";
    contexto.fillRect(0, 0, tela.width, tela.height);
    desenhos.set(id, false);
  });
}
function blobDesenho(id) {
  return new Promise((resolver) =>
    document.getElementById(id).toBlob(resolver, "image/png"),
  );
}
async function mostrarPagina() {
  const paginaSolicitada = paginaAtual;
  const resposta = await verificar(
    await fetch(
      `/api/convites/${conviteToken}/paginas/${paginaSolicitada}?resumoDocumento=${dadosConvite.resumoDocumento}`,
      {
        cache: "no-store",
      },
    ),
  );
  const novoEndereco = URL.createObjectURL(await resposta.blob());
  await new Promise((resolver, rejeitar) => {
    imagemPagina.onload = resolver;
    imagemPagina.onerror = rejeitar;
    imagemPagina.src = novoEndereco;
  });
  if (enderecoImagem) URL.revokeObjectURL(enderecoImagem);
  enderecoImagem = novoEndereco;
  visualizador.hidden = false;
  paginasLidas.add(paginaSolicitada);
  atualizarLeitura();
  document.getElementById("numero-pagina").textContent =
    `PÁGINA ${paginaAtual} / ${totalPaginas}`;
  camadaCampos.replaceChildren();
  for (const campo of dadosConvite.campos.filter(
    (item) => item.pagina === paginaAtual,
  )) {
    const caixa = document.createElement("div");
    caixa.className = "campo-posicionado";
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
  const resposta = await verificar(
    await fetch(
      `/api/convites/${conviteToken}/pdf?resumoDocumento=${dadosConvite.resumoDocumento}`,
      { cache: "no-store" },
    ),
  );
  const novoEndereco = URL.createObjectURL(await resposta.blob());
  if (enderecoDocumento) URL.revokeObjectURL(enderecoDocumento);
  enderecoDocumento = novoEndereco;
  document.getElementById("baixar-original").href = enderecoDocumento;
  document.getElementById("baixar-original").download =
    dadosConvite.nomeDocumento;
}

async function navegarPagina(deslocamento) {
  if (carregandoDocumento || carregandoPagina || enviandoAssinatura) return;
  const proximaPagina = paginaAtual + deslocamento;
  if (proximaPagina < 1 || proximaPagina > totalPaginas) return;
  carregandoPagina = true;
  paginaAtual = proximaPagina;
  try {
    await mostrarPagina();
  } catch (falha) {
    documentoDisponivelParaAceite = false;
    document.getElementById("aceite").checked = false;
    atualizarLeitura();
    erro(falha.message);
  } finally {
    carregandoPagina = false;
  }
}
document
  .getElementById("pagina-anterior")
  .addEventListener("click", () => navegarPagina(-1));
document
  .getElementById("pagina-seguinte")
  .addEventListener("click", () => navegarPagina(1));

document.getElementById("abrir-camera").addEventListener("click", async () => {
  try {
    transmissao = await navigator.mediaDevices.getUserMedia({
      video: {
        facingMode: "user",
      },
      audio: false,
    });
    const video = document.getElementById("video-selfie");
    video.srcObject = transmissao;
    video.hidden = false;
    document.getElementById("capturar-selfie").hidden = false;
  } catch {
    erro("Câmera indisponível. Escolha arquivo de imagem.");
  }
});
document
  .getElementById("capturar-selfie")
  .addEventListener("click", async () => {
    const video = document.getElementById("video-selfie");
    if (!video.videoWidth) return erro("Aguarde a câmera carregar.");
    const tela = document.createElement("canvas");
    const escala = Math.min(1, 1280 / video.videoWidth);
    tela.width = Math.round(video.videoWidth * escala);
    tela.height = Math.round(video.videoHeight * escala);
    tela.getContext("2d").drawImage(video, 0, 0, tela.width, tela.height);
    selfieCapturada = await new Promise((resolver) =>
      tela.toBlob(resolver, "image/jpeg", 0.85),
    );
    mostrarSelfie(selfieCapturada);
    transmissao.getTracks().forEach((trilha) => trilha.stop());
    transmissao = null;
    video.hidden = true;
    document.getElementById("capturar-selfie").hidden = true;
  });
document.getElementById("arquivo-selfie").addEventListener("change", () => {
  selfieCapturada = null;
  const imagem = document.getElementById("arquivo-selfie").files[0];
  mostrarSelfie(imagem);
});
document
  .getElementById("enviar-assinatura")
  .addEventListener("click", async () => {
    mensagemErro.hidden = true;
    const botao = document.getElementById("enviar-assinatura");
    if (
      botao.disabled ||
      carregandoDocumento ||
      carregandoPagina ||
      enviandoAssinatura
    )
      return;
    enviandoAssinatura = true;
    botao.disabled = true;
    try {
      if (!documentoDisponivelParaAceite || paginasLidas.size !== totalPaginas)
        throw new Error("Abra todas as páginas antes de confirmar.");
      if (!document.getElementById("aceite").checked)
        throw new Error("Confirme leitura e aceite do documento.");
      const tipos = tiposSolicitados();
      if (tipoObrigatorio("ASSINATURA") && !desenhos.get("desenho-assinatura"))
        throw new Error("Desenhe sua assinatura.");
      if (tipoObrigatorio("RUBRICA") && !desenhos.get("desenho-rubrica"))
        throw new Error("Desenhe sua rubrica.");
      const selfie =
        selfieCapturada || document.getElementById("arquivo-selfie").files[0];
      if (tipoObrigatorio("SELFIE") && !selfie)
        throw new Error("Envie sua selfie.");
      const dados = envioPendente || new FormData();
      if (!envioPendente) {
        const coleta = await coletarDadosAssinatura(`/api/convites/${conviteToken}/coleta`);
        dados.append("localizacao", JSON.stringify(coleta.localizacao));
        dados.append("ipNavegador", coleta.ipNavegador);
        dados.append("aceite", "true");
        dados.append("resumoDocumento", dadosConvite.resumoDocumento);
        dados.append("textoAceite", dadosConvite.textoAceite);
        const valores = {};
        for (const input of document.querySelectorAll(
          "[data-campo-preenchimento]",
        )) {
          if (!input.reportValidity())
            throw Error("Preencha os campos obrigatórios.");
          if (input.type === "file") {
            if (input.files[0])
              dados.append(
                "anexo_" + input.dataset.campoPreenchimento,
                input.files[0],
              );
          } else
            valores[input.dataset.campoPreenchimento] =
              input.type === "checkbox" ? String(input.checked) : input.value;
        }
        dados.append("valores", JSON.stringify(valores));
        if (tipos.has("ASSINATURA") && desenhos.get("desenho-assinatura"))
          dados.append(
            "assinatura",
            await blobDesenho("desenho-assinatura"),
            "assinatura.png",
          );
        if (tipos.has("RUBRICA") && desenhos.get("desenho-rubrica"))
          dados.append(
            "rubrica",
            await blobDesenho("desenho-rubrica"),
            "rubrica.png",
          );
        dados.append(
          "cienciaInclusaoImagem",
          String(document.getElementById("ciencia-inclusao-imagem").checked),
        );
        if (tipos.has("SELFIE") && selfie)
          dados.append("selfie", selfie, selfie.name || "selfie.jpg");
        envioPendente = dados;
      }
      const respostaEnvio = await fetch(
        `/api/convites/${conviteToken}/assinar`,
        {
          method: "POST",
          body: dados,
          cache: "no-store",
        },
      );
      if (!respostaEnvio.ok) envioPendente = null;
      await verificar(respostaEnvio);
      envioPendente = null;
      dadosConvite.assinado = true;
      atualizarPaginaDocumentoAssinado(true);
      document.getElementById("resultado").focus();
      document.getElementById("resultado").scrollIntoView({ block: "nearest" });
      try {
        await carregar();
      } catch {
        erro(
          "Sua assinatura foi registrada. Atualize a leitura para consultar o documento.",
        );
      }
    } catch (falha) {
      erro(
        falha instanceof TypeError
          ? "A conexão falhou. Tente novamente para consultar o resultado do mesmo envio."
          : falha.message,
      );
    } finally {
      enviandoAssinatura = false;
      atualizarLeitura();
    }
  });
function tipoObrigatorio(tipo) {
  return (
    dadosConvite.campos.some(
      (c) => c.tipo === tipo && c.obrigatorio !== false,
    ) ||
    (tipo === "ASSINATURA" &&
      dadosConvite.formaDeAutenticacao?.includes("tela")) ||
    (tipo === "SELFIE" && dadosConvite.formaDeAutenticacao?.includes("selfie"))
  );
}
function prepararPreenchimento() {
  const lista = document.getElementById("campos-preenchimento");
  if (!lista) return;
  lista.replaceChildren();
  const tipos = {
    TEXTO: "text",
    DATA: "date",
    SELECAO: "checkbox",
    NUMERO: "number",
    ANEXO: "file",
  };
  for (const campo of dadosConvite.campos.filter((c) => tipos[c.tipo])) {
    const label = document.createElement("label"),
      input = document.createElement("input");
    label.textContent = `${campo.nome || campo.tipo} · página ${campo.pagina}`;
    input.type = tipos[campo.tipo];
    input.required = campo.obrigatorio !== false;
    input.maxLength = 2000;
    input.dataset.campoPreenchimento = campo.id;
    if (input.type === "file") {
      input.accept = "application/pdf,image/png,image/jpeg";
    } else if (input.type === "checkbox")
      input.checked = campo.valor === "true";
    else input.value = campo.valor || "";
    input.disabled = dadosConvite.assinado;
    label.append(input);
    lista.append(label);
  }
  document.getElementById("preenchimento-convite").hidden =
    !lista.children.length;
  if (lista.children.length) {
    const proximo = document.createElement("button");
    proximo.type = "button";
    proximo.textContent = "Próximo campo pendente";
    proximo.onclick = () => {
      const entradas = [...lista.querySelectorAll("input")];
      const campo = entradas.find(
        (i) =>
          !i.disabled &&
          (i.type === "checkbox"
            ? !i.checked
            : i.type === "file"
              ? !i.files.length
              : !i.value),
      );
      campo?.focus();
      campo?.scrollIntoView({ block: "center", behavior: "smooth" });
    };
    lista.after(proximo);
  }
}
function tiposSolicitados() {
  const tipos = new Set(dadosConvite.campos.map((campo) => campo.tipo));
  if (dadosConvite.formaDeAutenticacao?.includes("tela"))
    tipos.add("ASSINATURA");
  if (dadosConvite.formaDeAutenticacao?.includes("selfie")) tipos.add("SELFIE");
  return tipos;
}
function prepararAutenticacao() {
  prepararPreenchimento();
  const painel = document.getElementById("codigos-autenticacao");
  painel.replaceChildren();
  for (const canal of dadosConvite.formaDeAutenticacao || []) {
    if (canal !== "whatsapp") continue;
    const grupo = document.createElement("div");
    grupo.className = "codigo-convite";
    const rotulo = document.createElement("label");
    rotulo.textContent = `CÓDIGO POR ${canal === "email" ? "E-MAIL" : "WHATSAPP"}`;
    const entrada = document.createElement("input");
    entrada.inputMode = "numeric";
    entrada.maxLength = 6;
    entrada.autocomplete = "one-time-code";
    entrada.id = `codigo-${canal}`;
    rotulo.htmlFor = entrada.id;
    const enviar = document.createElement("button");
    enviar.type = "button";
    enviar.textContent = "ENVIAR CÓDIGO";
    const validar = document.createElement("button");
    validar.type = "button";
    validar.textContent = "VALIDAR CÓDIGO";
    const estado = document.createElement("p");
    estado.setAttribute("role", "status");
    enviar.addEventListener("click", async () => {
      enviar.disabled = true;
      try {
        await verificar(
          await fetch(`/api/convites/${conviteToken}/codigos/${canal}`, {
            method: "POST",
          }),
        );
        estado.textContent =
          "Código enviado. Validade de 10 minutos; aguarde um minuto antes de reenviar.";
      } catch (falha) {
        erro(falha.message);
      } finally {
        enviar.disabled = false;
      }
    });
    validar.addEventListener("click", async () => {
      if (!/^\d{6}$/.test(entrada.value))
        return erro("Digite o código de seis números.");
      validar.disabled = true;
      try {
        await verificar(
          await fetch(
            `/api/convites/${conviteToken}/codigos/${canal}/verificar`,
            {
              method: "POST",
              headers: {
                "Content-Type": "application/json",
              },
              body: JSON.stringify({
                codigo: entrada.value,
              }),
            },
          ),
        );
        estado.textContent =
          "Código validado. Conclua a assinatura antes de expirar.";
        entrada.disabled = true;
      } catch {
        erro(
          "Código inválido, expirado ou limite de tentativas atingido. Solicite um novo código.",
        );
        validar.disabled = false;
      }
    });
    grupo.append(rotulo, enviar, entrada, validar, estado);
    painel.append(grupo);
  }
}
function prepararDocumentos() {
  const painel = document.getElementById("documentos-convite");
  painel.replaceChildren();
  for (const documento of dadosConvite.documentos || []) {
    const botao = document.createElement("button");
    botao.type = "button";
    botao.className = "botao-carteira";
    botao.textContent = `${documento.nome} · ${documento.paginaInicio === documento.paginaFim ? `página ${documento.paginaInicio}` : `páginas ${documento.paginaInicio} a ${documento.paginaFim}`}`;
    botao.addEventListener("click", () => {
      paginaAtual = documento.paginaInicio;
      mostrarPagina().catch((falha) => erro(falha.message));
    });
    painel.append(botao);
  }
}
async function carregar() {
  const jaEstavaAssinado = !!dadosConvite?.assinado;
  const resposta = await verificar(
    await fetch(`/api/convites/${conviteToken}`, {
      cache: "no-store",
    }),
  );
  const recebido = await resposta.json();
  if (!acessoConfirmado) return;
  dadosConvite = recebido;
  atualizarPaginaDocumentoAssinado(dadosConvite.assinado);
  atualizarPassosConvite(dadosConvite.assinado ? 3 : 1);
  if (dadosConvite.assinado) {
    // O convite já foi cumprido. Reabrir a página mantém a confirmação e o download.
    documentoDisponivelParaAceite = false;
    return;
  }
  document.getElementById("marca-empresa").firstChild.textContent =
    dadosConvite.nomeEmpresa;
  document.getElementById("rodape-empresa").textContent =
    dadosConvite.nomeEmpresa;
  document.getElementById("saudacao").textContent =
    `${dadosConvite.nomeSignatario}, leia ${dadosConvite.nomeDocumento} antes de assinar.`;
  for (const aviso of document.querySelectorAll("[data-aviso-privacidade]")) {
    aviso.textContent = dadosConvite.avisoPrivacidade;
    aviso.dataset.avisoPreservado = "true";
  }
  totalPaginas = dadosConvite.paginas;
  paginaAtual = Math.min(paginaAtual, totalPaginas);
  if (dadosConvite.assinado && !jaEstavaAssinado) {
    const ultimaPaginaOriginal = Math.max(0, ...(dadosConvite.documentos || []).map(documento => documento.paginaFim));
    paginaAtual = Math.min(totalPaginas, ultimaPaginaOriginal + 1);
  }
  paginasLidas.clear();
  documentoDisponivelParaAceite = false;
  document.getElementById("aceite").checked = false;
  document.getElementById("texto-aceite").textContent =
    dadosConvite.textoAceite;
  document.getElementById("identificacao-convite").textContent =
    `Nome e e-mail informados: ${dadosConvite.nomeSignatario} (${dadosConvite.emailSignatario}). ${
      dadosConvite.papel === "REPRESENTANTE"
        ? `Atuação declarada: representante de ${dadosConvite.representado}. Fundamento: ${dadosConvite.fundamentoRepresentacao}.`
        : "Atuação declarada: em nome próprio."
    }`;
  document.getElementById("versao-documento").textContent =
    `SHA-256 da versão apresentada: ${dadosConvite.resumoDocumento}`;
  atualizarLeitura();
  prepararAutenticacao();
  prepararDocumentos();
  const tipos = tiposSolicitados();
  document.getElementById("grupo-assinatura").hidden = !tipos.has("ASSINATURA");
  document.getElementById("grupo-rubrica").hidden = !tipos.has("RUBRICA");
  document.getElementById("grupo-selfie").hidden = !tipos.has("SELFIE");
  document.getElementById("area-assinatura").hidden =
    dadosConvite.assinado ||
    dadosConvite.finalizado ||
    !!dadosConvite.recusadoEm;
  document.getElementById("recusa-convite").hidden =
    dadosConvite.assinado ||
    dadosConvite.finalizado ||
    !!dadosConvite.recusadoEm;
  if (dadosConvite.recusadoEm)
    document.getElementById("saudacao").textContent =
      "Sua recusa foi registrada. Procure a empresa para tratar de uma nova versão.";
  document.getElementById("resultado").hidden = !dadosConvite.assinado;
  document.getElementById('baixar-final').hidden = !dadosConvite.assinado;
  document.getElementById('baixar-final').textContent = dadosConvite.finalizado ? 'BAIXAR PDF FINAL' : 'BAIXAR PDF ASSINADO (PARCIAL)';
  if (dadosConvite.assinado) document.getElementById('estado-final').textContent = dadosConvite.finalizado
    ? 'PDF finalizado e disponível para baixar.'
    : 'Sua assinatura foi registrada. Baixe o PDF parcial com as assinaturas recebidas e as demais pendentes.';
  await mostrarPagina();
  await atualizarDocumento();
  documentoDisponivelParaAceite = true;
  atualizarLeitura();
  if (dadosConvite.finalizado) {
    await assinaturasDigitais.conferirEndereco(
      `/api/convites/${conviteToken}/verificacao`,
      {},
      document.getElementById("verificacao-assinatura"),
    );
  }
}
document
  .getElementById("baixar-final")
  .addEventListener("click", async (evento) => {
    evento.preventDefault();
    try {
      const resposta = await verificar(
        await fetch(`/api/convites/${conviteToken}/final`, {
          cache: "no-store",
        }),
      );
      if (enderecoFinal) URL.revokeObjectURL(enderecoFinal);
      enderecoFinal = URL.createObjectURL(await resposta.blob());
      const link = document.createElement("a");
      link.href = enderecoFinal;
      link.download = dadosConvite.finalizado ? "documento-assinado.pdf" : "documento-parcial.pdf";
      link.click();
    } catch (falha) {
      erro(falha.message);
    }
  });

async function atualizarConvite() {
  if (
    !acessoConfirmado ||
    carregandoDocumento ||
    carregandoPagina ||
    enviandoAssinatura
  )
    return;
  carregandoDocumento = true;
  documentoDisponivelParaAceite = false;
  envioPendente = null;
  document.getElementById("aceite").checked = false;
  atualizarLeitura();
  mensagemErro.hidden = true;
  try {
    await carregar();
  } catch (falha) {
    erro(falha.message);
  } finally {
    carregandoDocumento = false;
  }
}
if (conviteToken === "convite") {
  document.getElementById("marca-empresa").firstChild.textContent = "aFIRMA";
  document.getElementById("rodape-empresa").textContent = "aFIRMA";
  document.getElementById("destino-codigo").textContent = "Abra o link completo recebido por e-mail para identificar a empresa e acessar seu documento.";
  document.getElementById("solicitar-codigo").disabled = true;
  document.getElementById("confirmar-codigo").disabled = true;
  document.getElementById("codigo-acesso").disabled = true;
} else consultarAcesso().catch((falha) => erro(falha.message));
window.addEventListener("pagehide", () => {
  if (enderecoImagem) URL.revokeObjectURL(enderecoImagem);
  if (enderecoDocumento) URL.revokeObjectURL(enderecoDocumento);
  if (enderecoFinal) URL.revokeObjectURL(enderecoFinal);
  if (enderecoSelfie) URL.revokeObjectURL(enderecoSelfie);
  if (transmissao) transmissao.getTracks().forEach((trilha) => trilha.stop());
  bloquearDocumento();
});
window.addEventListener("pageshow", (evento) => {
  if (evento.persisted) consultarAcesso().catch((falha) => erro(falha.message));
});

async function consultarAcesso() {
  const resposta = await verificar(
    await fetch(`/api/convites/${conviteToken}/acesso`, { cache: "no-store" }),
  );
  const acesso = await resposta.json();
  atualizarEmpresaConvite(acesso.nomeEmpresa);
  for (const aviso of document.querySelectorAll("[data-aviso-privacidade]")) {
    aviso.textContent = acesso.avisoPrivacidade;
    aviso.dataset.avisoPreservado = "true";
  }
  document.getElementById("destino-codigo").textContent =
    `E-mail cadastrado: ${acesso.emailMascarado}.`;
  sincronizarReenvioCodigo(acesso);
  if (!acesso.envioDisponivel)
    document.getElementById("estado-codigo").textContent =
      "O envio ainda não foi configurado pela empresa responsável.";
  if (!acesso.autenticado) {
    bloquearDocumento();
    return;
  }
  acessoConfirmado = true;
  document.getElementById("autenticacao-convite").hidden = true;
  document.getElementById("conteudo-autenticado").hidden = false;
  document.getElementById("estado-sessao").textContent =
    `Acesso ao e-mail confirmado. Sessão válida até ${new Date(acesso.expiraEm).toLocaleTimeString("pt-BR")}.`;
  temporizadorSessao = setTimeout(
    () => {
      bloquearDocumento();
      erro("Sua sessão expirou. Confirme outro código para continuar.");
    },
    Math.max(0, new Date(acesso.expiraEm).getTime() - Date.now()),
  );
  await atualizarConvite();
}
document
  .getElementById("solicitar-codigo")
  .addEventListener("click", async (evento) => {
    const botaoEnviarCodigo = evento.currentTarget;
    if (botaoEnviarCodigo.disabled) return;
    botaoEnviarCodigo.disabled = true;
    mensagemErro.hidden = true;
    botaoEnviarCodigo.setAttribute("aria-busy", "true");
    // Uso o mesmo trator da FIRMA enquanto o e-mail está sendo enviado.
    const identificadorCarregamentoEmail = FirmaMensagens.mostrar(
      "Enviando o código para seu e-mail...",
      { tipo: "processando" },
    );
    try {
      const respostaEnvioCodigo = await verificar(
        await fetch(`/api/convites/${conviteToken}/codigo`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: "{}",
          cache: "no-store",
        }),
      );
      codigoJaEnviado = true;
      sincronizarReenvioCodigo(await respostaEnvioCodigo.json());
      document.getElementById("estado-codigo").textContent =
        "Código enviado. Confira seu e-mail. Ele vale por 10 minutos. Aguarde um minuto antes de solicitar outro.";
      document.getElementById("codigo-acesso").focus();
    } catch (falha) {
      erro(falha.message);
      // Se outra aba enviou o código, recupero o prazo que a API está cobrando.
      try {
        const respostaAcessoConvite = await verificar(await fetch(`/api/convites/${conviteToken}/acesso`, { cache: "no-store" }));
        sincronizarReenvioCodigo(await respostaAcessoConvite.json());
      } catch {
        envioCodigoDisponivel = false;
      }
    } finally {
      FirmaMensagens.fechar(identificadorCarregamentoEmail);
      botaoEnviarCodigo.removeAttribute("aria-busy");
      atualizarContadorReenvio();
    }
  });
document
  .getElementById("confirmar-codigo")
  .addEventListener("click", async (evento) => {
    const botao = evento.currentTarget;
    if (botao.disabled) return;
    botao.disabled = true;
    mensagemErro.hidden = true;
    try {
      const codigo = document.getElementById("codigo-acesso").value.trim();
      if (!/^[0-9]{8}$/.test(codigo))
        throw new Error("Informe os oito dígitos recebidos por e-mail.");
      await verificar(
        await fetch(`/api/convites/${conviteToken}/autenticar`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ codigo }),
          cache: "no-store",
        }),
      );
      document.getElementById("codigo-acesso").value = "";
      await consultarAcesso();
    } catch (falha) {
      erro(falha.message);
    } finally {
      botao.disabled = false;
    }
  });
document
  .getElementById("encerrar-sessao")
  .addEventListener("click", async () => {
    try {
      await verificar(
        await fetch(`/api/convites/${conviteToken}/sair`, {
          method: "POST",
          cache: "no-store",
        }),
      );
      bloquearDocumento();
    } catch (falha) {
      erro(falha.message);
    }
  });
document
  .getElementById("recusar-convite")
  .addEventListener("click", async (evento) => {
    const botao = evento.currentTarget;
    if (botao.disabled) return;
    botao.disabled = true;
    try {
      await verificar(
        await fetch(`/api/convites/${conviteToken}/recusar`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            motivo: document.getElementById("motivo-recusa").value,
          }),
          cache: "no-store",
        }),
      );
      await atualizarConvite();
    } catch (falha) {
      erro(falha.message);
    } finally {
      botao.disabled = false;
    }
  });

let consultandoAndamento = false;
async function acompanharConviteAssinado() {
  if (!acessoConfirmado || !dadosConvite?.assinado || dadosConvite.finalizado || document.hidden
      || consultandoAndamento || carregandoDocumento || carregandoPagina || enviandoAssinatura) return;
  consultandoAndamento = true;
  try {
    const resposta = await verificar(await fetch(`/api/convites/${conviteToken}`, { cache: 'no-store' }));
    const estado = await resposta.json();
    if (estado.resumoDocumento !== dadosConvite.resumoDocumento || estado.finalizado !== dadosConvite.finalizado)
      await atualizarConvite();
  } catch (falha) { erro(falha.message); }
  finally { consultandoAndamento = false; }
}
let acompanhamentoConvite = setInterval(acompanharConviteAssinado, 10000);
window.addEventListener('pagehide', () => clearInterval(acompanhamentoConvite));
window.addEventListener('pageshow', evento => {
  if (evento.persisted) acompanhamentoConvite = setInterval(acompanharConviteAssinado, 10000);
});
document.addEventListener('visibilitychange', () => { if (!document.hidden) acompanharConviteAssinado(); });

// O nome vem do envelope deste convite, inclusive antes de confirmar o e-mail.
function atualizarEmpresaConvite(nomeEmpresa) {
  document.getElementById("marca-empresa").firstChild.textContent = nomeEmpresa;
  document.getElementById("rodape-empresa").textContent = nomeEmpresa;
}

function atualizarPassosConvite(indiceEtapaAtual) {
  document.querySelectorAll(".passos-convite li").forEach((passoConvite, indicePasso) => {
    passoConvite.classList.toggle("concluido", indicePasso < indiceEtapaAtual);
    passoConvite.classList.toggle("ativo", indicePasso === indiceEtapaAtual);
    passoConvite.classList.toggle("inativo", indicePasso > indiceEtapaAtual);
    if (indicePasso === indiceEtapaAtual) passoConvite.setAttribute("aria-current", "step");
    else passoConvite.removeAttribute("aria-current");
  });
}

// A leitura continua livre; o indicador só acompanha a parte que está na tela.
const observadorEtapasConvite = new IntersectionObserver((entradasVisiveis) => {
  if (!acessoConfirmado || dadosConvite?.assinado) return;
  for (const entradaVisivel of entradasVisiveis) {
    if (entradaVisivel.isIntersecting) atualizarPassosConvite(entradaVisivel.target.id === "area-assinatura" ? 2 : 1);
  }
}, { threshold: 0.15 });
observadorEtapasConvite.observe(document.getElementById("area-assinatura"));
observadorEtapasConvite.observe(document.getElementById("documentos-convite").closest("section"));

function atualizarPaginaDocumentoAssinado(documentoJaAssinado) {
  document.body.classList.toggle("documento-assinado", documentoJaAssinado);
  document.title = documentoJaAssinado ? "Documento assinado com sucesso" : "Convite para assinar";
  document.querySelector(".topo-selo").textContent = documentoJaAssinado ? "DOCUMENTO ASSINADO" : "CONVITE PARA ASSINAR";
  document.querySelector(".passos-convite").hidden = documentoJaAssinado;
  document.getElementById("resultado").hidden = !documentoJaAssinado;
  document.getElementById("encerrar-sessao").hidden = documentoJaAssinado;
  for (const identificadorPainel of ["documento-para-assinar", "saudacao", "identificacao-convite", "versao-documento", "leitura-paginas"]) {
    document.getElementById(identificadorPainel).hidden = documentoJaAssinado;
  }
  if (!documentoJaAssinado) return;
  document.getElementById("area-assinatura").hidden = true;
  document.getElementById("recusa-convite").hidden = true;
  document.getElementById("verificacao-assinatura").hidden = true;
  document.getElementById("documento-assinado-nome").textContent = dadosConvite.nomeDocumento;
  document.getElementById("estado-final").textContent = dadosConvite.finalizado
    ? "Sua assinatura foi concluída e o documento está finalizado. Baixe seu PDF assinado abaixo."
    : "Sua assinatura foi concluída. O documento ainda aguarda a conclusão das demais assinaturas. Você já pode baixar o PDF com as assinaturas recebidas e acompanhar a finalização por este link.";
  const botaoBaixarDocumento = document.getElementById("baixar-final");
  botaoBaixarDocumento.hidden = false;
  botaoBaixarDocumento.textContent = dadosConvite.finalizado ? "BAIXAR DOCUMENTO ASSINADO ↗" : "BAIXAR PDF ASSINADO (PARCIAL) ↗";
}
