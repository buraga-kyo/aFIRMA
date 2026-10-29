(async () => {
  const painel = document.getElementById("prova-documento");
  try {
    const token = location.pathname.split("/").filter(Boolean).at(-1);
    const resposta = await fetch(
      `/api/assineai/autenticidade/${encodeURIComponent(token)}`,
      { cache: "no-store" },
    );
    if (!resposta.ok)
      throw new Error("Documento não encontrado ou indisponível.");
    const dados = await resposta.json();
    painel.replaceChildren();
    for (const texto of [
      dados.DocumentoTitulo,
      dados.originalValido
        ? "Integridade do original confirmada pela prova criptográfica."
        : "Não foi possível confirmar a integridade do original.",
      `Documento: ${dados.DocumentoToken}`,
      `SHA-256 do original: ${dados.DocumentoHashDoPDFOriginal}`,
      dados.certificado
        ? "PDF certificado disponível no convite do assinante."
        : "Certificação digital ainda não concluída.",
    ]) {
      const p = document.createElement("p");
      p.textContent = texto;
      p.style.overflowWrap = "anywhere";
      painel.append(p);
    }
  } catch (erro) {
    painel.textContent = erro.message;
  }
})();
