(async () => {
  const painel = document.getElementById("prova-documento");
  try {
    const token = location.pathname.split("/").filter(Boolean).at(-1);
    const rota = location.pathname.includes('/autenticidade-assinatura/') ? 'autenticidade-envelope' : 'autenticidade';
    const resposta = await fetch(`/api/assineai/${rota}/${encodeURIComponent(token)}`, {
      cache: "no-store"
    });
    if (!resposta.ok) throw new Error("Documento não encontrado ou indisponível.");
    const dados = await resposta.json();
    painel.replaceChildren();
    for (const texto of [dados.DocumentoTitulo, dados.originalValido ? "Integridade do original preservado conferida." : "Não foi possível confirmar a integridade do original.", `Documento: ${dados.DocumentoToken}`, `SHA-256 do original: ${dados.DocumentoHashDoPDFOriginal}`, dados.certificado ? "PDF certificado disponível no convite do assinante." : "Certificação digital ainda não concluída."]) {
      const paragrafo = document.createElement("p");
      paragrafo.textContent = texto;
      paragrafo.style.overflowWrap = "anywhere";
      painel.append(paragrafo);
    }
  } catch (erro) {
    painel.textContent = erro.message;
  }
})();
