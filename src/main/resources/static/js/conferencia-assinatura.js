window.assinaturasDigitais = (() => {
  const descricoes = {
    CONFIAVEL: 'Cadeia confirmada pelas autoridades configuradas.',
    NAO_CONFIAVEL: 'Cadeia de confiança não confirmada.',
    NAO_VERIFICADA: 'Cadeia de confiança não verificada. Autoridades não configuradas.',
    NAO_CONSULTADA: 'Revogação não consultada.',
    CONSULTADA_SEM_REVOGACAO: 'Revogação consultada, sem revogação identificada.',
    REVOGADO: 'Certificado revogado.',
    CONSULTA_INDISPONIVEL: 'A consulta de revogação não pôde ser concluída.'
  };

  async function receberJson(resposta) {
    const dados = await resposta.json().catch(() => null);
    if (!resposta.ok) throw new Error(dados?.mensagem || `Operação recusada (${resposta.status}).`);
    return dados;
  }

  function acrescentarLinha(destino, texto) {
    const paragrafo = document.createElement('p');
    paragrafo.textContent = texto;
    destino.append(paragrafo);
  }

  async function consultarCertificado(arquivo, senha, credenciais, destino, finalizacaoEnvelope = false) {
    if (!arquivo) throw new Error('Escolha o certificado para conferir o titular.');
    if (arquivo.size > 2 * 1024 * 1024) throw new Error('O certificado deve ter até 2 MB.');
    const dados = new FormData();
    dados.append('certificado', arquivo);
    dados.append('senha', senha);
    dados.append('finalizacaoEnvelope', String(finalizacaoEnvelope));
    const certificado = await receberJson(await fetch('/api/pdfs/certificado', {
      method: 'POST', headers: credenciais, body: dados, cache: 'no-store'
    }));
    destino.replaceChildren();
    acrescentarLinha(destino, `Titular do certificado: ${certificado.titular}`);
    acrescentarLinha(destino, `Emissor: ${certificado.emissor}`);
    acrescentarLinha(destino, `Válido até ${new Date(certificado.validoAte).toLocaleString('pt-BR')}.`);
    if (new Date(certificado.validoAte).getTime() < Date.now()) {
      acrescentarLinha(destino, 'Certificado vencido. O servidor permite seu uso por configuração explícita; a assinatura não comprova validade atual do certificado.');
    }
    acrescentarLinha(destino, descricoes[certificado.confianca]);
    acrescentarLinha(destino, descricoes[certificado.revogacao]);
    acrescentarLinha(destino, certificado.carimboTempoConfigurado
      ? 'A assinatura solicitará carimbo do tempo à autoridade configurada.'
      : 'Sem carimbo do tempo. A data da assinatura será informada pelo servidor.');
    destino.hidden = false;
    return certificado;
  }

  function mostrarVerificacao(resultado, destino) {
    destino.replaceChildren();
    acrescentarLinha(destino, resultado.integridadeConfirmada
      ? 'Integridade criptográfica confirmada. A assinatura final cobre o PDF completo.'
      : 'Integridade do PDF completo não confirmada. Confira os resultados de cada assinatura.');
    acrescentarLinha(destino, `SHA-256 do arquivo: ${resultado.resumoDocumento}`);
    if (!resultado.assinaturas.length) acrescentarLinha(destino, 'O PDF ainda não possui assinatura criptográfica.');
    for (const assinatura of resultado.assinaturas) {
      acrescentarLinha(destino, `Titular: ${assinatura.titular}`);
      acrescentarLinha(destino, assinatura.integra
        ? `Assinatura íntegra na revisão ${assinatura.revisao}. ${assinatura.cobreDocumentoInteiro
          ? 'Cobre o arquivo completo.' : 'Existem revisões posteriores a esta assinatura.'}`
        : 'Assinatura inválida ou impossível de verificar.');
      acrescentarLinha(destino, descricoes[assinatura.confianca]);
      acrescentarLinha(destino, descricoes[assinatura.revogacao]);
      const estadosCarimbo = {
        CONFIAVEL: `Carimbo do tempo conferido: ${new Date(assinatura.horarioCarimbo).toLocaleString('pt-BR')}.`,
        AUSENTE: 'Sem carimbo do tempo de autoridade.',
        INVALIDO: 'Carimbo do tempo inválido.',
        CONFIANCA_NAO_CONFIRMADA: 'Confiança no carimbo do tempo não confirmada.',
        NAO_VERIFICADO: 'Carimbo do tempo não verificado.'
      };
      acrescentarLinha(destino, estadosCarimbo[assinatura.carimboTempo]);
    }
    acrescentarLinha(destino, 'A integridade do arquivo, a confiança no certificado e a identidade dos convidados são verificações diferentes. A confiança é avaliada na data desta consulta.');
    destino.hidden = false;
  }

  async function conferirArquivo(arquivo, credenciais, destino) {
    const dados = new FormData();
    dados.append('pdf', arquivo, 'documento-assinado.pdf');
    const resultado = await receberJson(await fetch('/api/pdfs/verificar', {
      method: 'POST', headers: credenciais, body: dados, cache: 'no-store'
    }));
    mostrarVerificacao(resultado, destino);
    return resultado;
  }

  async function conferirEndereco(endereco, credenciais, destino) {
    const resultado = await receberJson(await fetch(endereco, { headers: credenciais, cache: 'no-store' }));
    mostrarVerificacao(resultado, destino);
    return resultado;
  }

  return { consultarCertificado, conferirArquivo, conferirEndereco };
})();
