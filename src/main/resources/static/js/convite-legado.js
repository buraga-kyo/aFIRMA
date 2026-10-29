const conviteToken = location.pathname.split("/").filter(Boolean).at(-1);
const mensagemErro = document.getElementById("erro");
const visualizador = document.getElementById("visualizador");
const imagemPagina = document.getElementById("imagem-pagina");
const camadaCampos = document.getElementById("camada-campos");
let dadosConvite = null;
let paginaAtual = 1;
let totalPaginas = 1;
let enderecoImagem = null;
let enderecoDocumento = null;
let enderecoFinal = null;
let selfieCapturada = null;
let transmissao = null;
const desenhos = new Map();
let documentos = [];
let documentoAtual = null;
let localizacao = null;
let carregandoPagina = false;
const canaisValidados = new Set();

function erro(mensagem) {
  mensagemErro.textContent = mensagem;
  mensagemErro.hidden = false;
  mensagemErro.scrollIntoView({ block: "nearest" });
}

async function verificar(resposta) {
  if (resposta.ok) return resposta;
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
  if (carregandoPagina) return;
  carregandoPagina = true;
  document.getElementById("documento-convite").disabled = true;
  document.getElementById("pagina-anterior").disabled = true;
  document.getElementById("pagina-seguinte").disabled = true;
  try {
    const resposta = await verificar(
      await fetch(
        `/api/assineai/convites/${conviteToken}/documentos/${documentoAtual.DocumentoId}/paginas/${paginaAtual}`,
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
    document.getElementById("numero-pagina").textContent =
      `PÁGINA ${paginaAtual} / ${totalPaginas}`;
    camadaCampos.replaceChildren();
    for (const campo of documentoAtual.campos.filter(
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
  } finally {
    carregandoPagina = false;
    document.getElementById("documento-convite").disabled = false;
    document.getElementById("pagina-anterior").disabled = paginaAtual <= 1;
    document.getElementById("pagina-seguinte").disabled =
      paginaAtual >= totalPaginas;
  }
}

async function atualizarDocumento() {
  const idSolicitado = documentoAtual.DocumentoId;
  const resposta = await verificar(
    await fetch(
      `/api/assineai/convites/${conviteToken}/documentos/${documentoAtual.DocumentoId}/pdf`,
      { cache: "no-store" },
    ),
  );
  const novoEndereco = URL.createObjectURL(await resposta.blob());
  if (idSolicitado !== documentoAtual.DocumentoId) {
    URL.revokeObjectURL(novoEndereco);
    return;
  }
  if (enderecoDocumento) URL.revokeObjectURL(enderecoDocumento);
  enderecoDocumento = novoEndereco;
  document.getElementById("baixar-original").href = enderecoDocumento;
  document.getElementById("baixar-original").download =
    documentoAtual.DocumentoTitulo + ".pdf";
  document.getElementById("baixar-fonte").href =
    `/api/assineai/convites/${conviteToken}/documentos/${documentoAtual.DocumentoId}/pdf?original=true`;
  document.getElementById("ver-autenticidade").href =
    `/web-panels/autenticidade-documento/${documentoAtual.DocumentoToken}`;
}

document.getElementById("pagina-anterior").addEventListener("click", () => {
  if (!carregandoPagina && paginaAtual > 1) {
    paginaAtual--;
    mostrarPagina().catch((falha) => erro(falha.message));
  }
});
document.getElementById("pagina-seguinte").addEventListener("click", () => {
  if (!carregandoPagina && paginaAtual < totalPaginas) {
    paginaAtual++;
    mostrarPagina().catch((falha) => erro(falha.message));
  }
});

document.getElementById("abrir-camera").addEventListener("click", async () => {
  try {
    transmissao = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: "user" },
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
    const previa = document.getElementById("previa-selfie");
    previa.src = URL.createObjectURL(selfieCapturada);
    previa.hidden = false;
    transmissao.getTracks().forEach((trilha) => trilha.stop());
    transmissao = null;
    video.hidden = true;
    document.getElementById("capturar-selfie").hidden = true;
  });
document.getElementById("arquivo-selfie").addEventListener("change", () => {
  selfieCapturada = null;
  const imagem = document.getElementById("arquivo-selfie").files[0];
  const previa = document.getElementById("previa-selfie");
  if (imagem) {
    previa.src = URL.createObjectURL(imagem);
    previa.hidden = false;
  } else previa.hidden = true;
});

document
  .getElementById("enviar-assinatura")
  .addEventListener("click", async () => {
    mensagemErro.hidden = true;
    const botao = document.getElementById("enviar-assinatura");
    try {
      if (!document.getElementById("aceite").checked)
        throw new Error("Confirme leitura e aceite do documento.");
      const tipos = tiposSolicitados();
      if (tipos.has("ASSINATURA") && !desenhos.get("desenho-assinatura"))
        throw new Error("Desenhe sua assinatura.");
      if (tipos.has("RUBRICA") && !desenhos.get("desenho-rubrica"))
        throw new Error("Desenhe sua rubrica.");
      const selfie =
        selfieCapturada || document.getElementById("arquivo-selfie").files[0];
      if (tipos.has("SELFIE") && !selfie) throw new Error("Envie sua selfie.");
      if (
        (dadosConvite.formaDeAutenticacao || []).some((c) =>
          ["email", "whatsapp"].includes(c),
        ) &&
        !canaisValidados.size
      )
        throw new Error("Valide um código por e-mail ou WhatsApp.");
      const dados = { aceite: true, userLocation: localizacao };
      if (tipos.has("ASSINATURA"))
        dados.SignatarioAssinaturaEscritaBase64 = document
          .getElementById("desenho-assinatura")
          .toDataURL("image/png");
      if (tipos.has("RUBRICA"))
        dados.rubrica = document
          .getElementById("desenho-rubrica")
          .toDataURL("image/png");
      if (tipos.has("SELFIE"))
        dados.selfie = await new Promise((resolve, reject) => {
          const leitor = new FileReader();
          leitor.onload = () => resolve(leitor.result);
          leitor.onerror = reject;
          leitor.readAsDataURL(selfie);
        });
      botao.disabled = true;
      const respostaAssinada = await verificar(
        await fetch(`/api/assineai/convites/${conviteToken}/assinar`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(dados),
          cache: "no-store",
        }),
      );
      const atualizados = await respostaAssinada.json();
      for (const atual of atualizados) {
        const documento = documentos.find(
          (d) => d.DocumentoId === atual.DocumentoId,
        );
        if (documento && atual.paginas) documento.paginas = atual.paginas;
      }
      totalPaginas = documentoAtual.paginas;
      document.getElementById("area-assinatura").hidden = true;
      document.getElementById("resultado").hidden = false;
      document.getElementById("resultado").scrollIntoView({ block: "nearest" });
      document.getElementById("estado-final").textContent =
        "Assinatura registrada. Baixe cada documento no seletor acima. A situação do certificado fica disponível na página de autenticidade.";
      await mostrarPagina();
      await atualizarDocumento();
    } catch (falha) {
      erro(falha.message);
      botao.disabled = false;
    }
  });

function tiposSolicitados() {
  const tipos = new Set(
    documentos.flatMap((d) => d.campos || []).map((campo) => campo.tipo),
  );
  if (dadosConvite.formaDeAutenticacao?.includes("tela"))
    tipos.add("ASSINATURA");
  if (dadosConvite.formaDeAutenticacao?.includes("selfie")) tipos.add("SELFIE");
  return tipos;
}

function prepararAutenticacao() {
  const painel = document.getElementById("codigos-autenticacao");
  painel.replaceChildren();
  if (
    (dadosConvite.formaDeAutenticacao || []).some((c) =>
      ["email", "whatsapp"].includes(c),
    )
  ) {
    const dica = document.createElement("p");
    dica.textContent = "Valide um dos canais escolhidos: e-mail ou WhatsApp.";
    painel.append(dica);
  }
  for (const canal of dadosConvite.formaDeAutenticacao || []) {
    if (!["email", "whatsapp"].includes(canal)) continue;
    const grupo = document.createElement("div");
    grupo.className = "codigo-convite";
    const label = document.createElement("label");
    label.textContent = `CÓDIGO POR ${canal === "email" ? "E-MAIL" : "WHATSAPP"}`;
    const entrada = document.createElement("input");
    entrada.inputMode = "numeric";
    entrada.maxLength = 6;
    entrada.autocomplete = "one-time-code";
    entrada.id = `codigo-${canal}`;
    label.htmlFor = entrada.id;
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
          await fetch(
            `/api/assineai/convites/${conviteToken}/codigos/${canal}`,
            { method: "POST" },
          ),
        );
        entrada.disabled = false;
        entrada.value = "";
        validar.disabled = false;
        canaisValidados.delete(canal);
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
            `/api/assineai/convites/${conviteToken}/codigos/${canal}/verificar`,
            {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({ codigo: entrada.value }),
            },
          ),
        );
        canaisValidados.add(canal);
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
    grupo.append(label, enviar, entrada, validar, estado);
    painel.append(grupo);
  }
}

function prepararDocumentos() {
  const seletor = document.getElementById("documento-convite");
  seletor.replaceChildren(
    ...documentos.map((d) => {
      const opcao = document.createElement("option");
      opcao.value = d.DocumentoId;
      opcao.textContent = d.DocumentoTitulo;
      return opcao;
    }),
  );
  seletor.addEventListener("change", async () => {
    documentoAtual = documentos.find(
      (d) => String(d.DocumentoId) === seletor.value,
    );
    paginaAtual = 1;
    totalPaginas = documentoAtual.paginas;
    try {
      await mostrarPagina();
      await atualizarDocumento();
    } catch (falha) {
      erro(falha.message);
    }
  });
}

document
  .getElementById("compartilhar-localizacao")
  .addEventListener("click", () => {
    const estado = document.getElementById("estado-localizacao");
    if (!navigator.geolocation) {
      estado.textContent = "Localização indisponível. Você pode continuar.";
      return;
    }
    estado.textContent = "Aguardando localização...";
    navigator.geolocation.getCurrentPosition(
      (posicao) => {
        localizacao = {
          latitude: posicao.coords.latitude,
          longitude: posicao.coords.longitude,
          accuracy: posicao.coords.accuracy,
        };
        estado.textContent =
          "Localização adicionada ao registro da assinatura.";
      },
      () => {
        localizacao = null;
        estado.textContent =
          "Localização não compartilhada. Você pode continuar.";
      },
      { timeout: 10000, maximumAge: 0 },
    );
  });

async function carregar() {
  const resposta = await verificar(
    await fetch(`/api/assineai/convites/${conviteToken}`, {
      cache: "no-store",
    }),
  );
  const retorno = await resposta.json();
  documentos = retorno.Documentos;
  documentoAtual = documentos[0];
  dadosConvite = {
    nomeEmpresa: retorno.empresa,
    nomeSignatario: retorno.Signatario.SignatarioNome,
    assinado: retorno.Signatario.SignatarioAssinou,
    formaDeAutenticacao: retorno.Signatario.SignatarioFormaDeAutenticacao || [],
    paginas: documentoAtual.paginas,
    nomeDocumento: documentoAtual.DocumentoTitulo,
    finalizado: false,
  };
  document.body.dataset.tema = String(retorno.tema)
    .toLowerCase()
    .includes("caderno")
    ? "caderno"
    : "firma";
  document.getElementById("marca-empresa").firstChild.textContent =
    dadosConvite.nomeEmpresa;
  document.getElementById("rodape-empresa").textContent =
    dadosConvite.nomeEmpresa;
  document.getElementById("saudacao").textContent =
    `${dadosConvite.nomeSignatario}, leia ${dadosConvite.nomeDocumento} antes de assinar.`;
  totalPaginas = dadosConvite.paginas;
  prepararAutenticacao();
  prepararDocumentos();
  const tipos = tiposSolicitados();
  document.getElementById("grupo-assinatura").hidden = !tipos.has("ASSINATURA");
  document.getElementById("grupo-rubrica").hidden = !tipos.has("RUBRICA");
  document.getElementById("grupo-selfie").hidden = !tipos.has("SELFIE");
  document.getElementById("area-assinatura").hidden = dadosConvite.assinado;
  document.getElementById("resultado").hidden = !dadosConvite.assinado;
  if (dadosConvite.finalizado) {
    document.getElementById("estado-final").textContent =
      "PDF finalizado e disponível para baixar.";
    document.getElementById("baixar-final").hidden = false;
  }
  await mostrarPagina();
  await atualizarDocumento();
}

carregar().catch((falha) => erro(falha.message));
window.addEventListener("pagehide", () => {
  if (enderecoImagem) URL.revokeObjectURL(enderecoImagem);
  if (enderecoDocumento) URL.revokeObjectURL(enderecoDocumento);
  if (enderecoFinal) URL.revokeObjectURL(enderecoFinal);
  if (transmissao) transmissao.getTracks().forEach((trilha) => trilha.stop());
});
