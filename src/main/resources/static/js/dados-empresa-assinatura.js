window.dadosEmpresaAssinatura = (() => {
  function cnpj(informado) {
    const texto = String(informado || '').trim().toUpperCase();
    if (!texto) return '';
    if (!/^(?:[A-Z0-9]{12}[0-9]{2}|[A-Z0-9]{2}\.[A-Z0-9]{3}\.[A-Z0-9]{3}\/[A-Z0-9]{4}-[0-9]{2})$/.test(texto)) throw new Error('CNPJ inválido: confira o formato.');
    const numero = texto.replace(/[./-]/g, '');
    if (new Set(numero).size === 1) throw new Error('CNPJ inválido.');
    for (let tamanho = 12; tamanho <= 13; tamanho++) {
      let soma = 0, peso = 2;
      for (let indice = tamanho - 1; indice >= 0; indice--) {
        soma += (numero.charCodeAt(indice) - 48) * peso; peso = peso === 9 ? 2 : peso + 1;
      }
      const resto = soma % 11, digito = resto < 2 ? 0 : 11 - resto;
      if (Number(numero[tamanho]) !== digito) throw new Error('CNPJ inválido: confira os dígitos verificadores.');
    }
    return numero;
  }
  function ligarCnpj(campo) {
    campo.maxLength = 18; campo.autocapitalize = 'characters'; campo.spellcheck = false;
    campo.addEventListener('paste', evento => {
      if (/[^A-Za-z0-9./\-\s]/.test(evento.clipboardData?.getData('text') || '')) {
        evento.preventDefault(); campo.setCustomValidity('CNPJ aceita somente letras, números e os separadores do documento.'); campo.reportValidity();
      }
    });
    campo.addEventListener('input', () => {
      const inicio = campo.selectionStart, anterior = campo.value;
      const caracteres = anterior.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 14);
      let formatado = caracteres.slice(0, 2);
      if (caracteres.length > 2) formatado += '.' + caracteres.slice(2, 5);
      if (caracteres.length > 5) formatado += '.' + caracteres.slice(5, 8);
      if (caracteres.length > 8) formatado += '/' + caracteres.slice(8, 12);
      if (caracteres.length > 12) formatado += '-' + caracteres.slice(12);
      campo.value = formatado;
      if (document.activeElement === campo && inicio !== null) {
        const quantidade = anterior.slice(0, inicio).replace(/[^A-Za-z0-9]/g, '').length;
        let posicao = 0, encontrados = 0;
        while (posicao < formatado.length && encontrados < quantidade) { if (/[A-Z0-9]/.test(formatado[posicao])) encontrados++; posicao++; }
        campo.setSelectionRange(posicao, posicao);
      }
      try { cnpj(campo.value); campo.setCustomValidity(''); }
      catch (falha) { campo.setCustomValidity(falha.message); }
    });
  }
  return { cnpj, ligarCnpj };
})();
