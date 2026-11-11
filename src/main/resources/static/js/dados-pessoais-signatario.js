window.dadosPessoaisSignatario = (() => {
  const ddds = new Set('11 12 13 14 15 16 17 18 19 21 22 24 27 28 31 32 33 34 35 37 38 41 42 43 44 45 46 47 48 49 51 53 54 55 61 62 63 64 65 66 67 68 69 71 73 74 75 77 79 81 82 83 84 85 86 87 88 89 91 92 93 94 95 96 97 98 99'.split(' '));
  const erro = (campo, motivo) => { throw new Error(`${campo} inválido: ${motivo}`); };
  function entrada(valor, limite, campo) {
    valor = valor || '';
    if (valor.length > limite || /[\u0000-\u001f\u007f]/.test(valor)) erro(campo, 'tamanho ou caracteres inválidos');
    return valor.replace(/^ +| +$/g, '');
  }
  function cpf(valor) {
    const texto = entrada(valor, 14, 'CPF');
    if (!texto) return '';
    if (!/^(?:[0-9]{11}|[0-9]{3}\.[0-9]{3}\.[0-9]{3}-[0-9]{2})$/.test(texto)) erro('CPF', 'use 11 dígitos');
    const numeros = texto.replace(/[.-]/g, '');
    if (new Set(numeros).size === 1) erro('CPF', 'dígitos verificadores incorretos');
    for (let quantidade = 9; quantidade <= 10; quantidade++) {
      let soma = 0;
      for (let indice = 0; indice < quantidade; indice++) soma += Number(numeros[indice]) * (quantidade + 1 - indice);
      let digito = 11 - soma % 11;
      if (digito >= 10) digito = 0;
      if (digito !== Number(numeros[quantidade])) erro('CPF', 'dígitos verificadores incorretos');
    }
    return numeros;
  }
  function rg(valor) {
    const texto = entrada(valor, 24, 'RG');
    if (!texto) return '';
    if (!/^[A-Za-z0-9. -]+$/.test(texto)) erro('RG', 'use apenas o número, seu prefixo e os separadores do documento');
    const numero = texto.toUpperCase().replace(/[. -]/g, '');
    if (!/^[A-Z]{0,2}[0-9]{5,12}X?$/.test(numero) || /^[A-Z]{0,2}0+X?$/.test(numero)) erro('RG', 'informe de 5 a 12 dígitos, com prefixo quando existir e X apenas no final');
    return numero;
  }
  function whatsapp(valor) {
    const texto = entrada(valor, 32, 'WhatsApp');
    if (!texto) return '';
    if (!/^\+?[0-9 ()-]+$/.test(texto) || !/[0-9]/.test(texto)) erro('WhatsApp', 'use DDD e número; para outro país, comece com + e o código do país');
    if (texto.includes('(') && !/^\+?[0-9 ]*\([0-9]{2}\)[0-9 -]+$/.test(texto)) erro('WhatsApp', 'parênteses devem envolver somente o DDD');
    if (!texto.includes('(') && texto.includes(')')) erro('WhatsApp', 'DDD malformado');
    let numeros = texto.replace(/[+ ()-]/g, '');
    const internacional = texto.startsWith('+');
    if (internacional && !numeros.startsWith('55')) {
      if (!/^[1-9][0-9]{7,14}$/.test(numeros)) erro('WhatsApp', 'o formato internacional deve ter entre 8 e 15 dígitos');
      return '+' + numeros;
    }
    if ((internacional || numeros.length > 11) && numeros.startsWith('55')) numeros = numeros.slice(2);
    if (!/^[0-9]{10,11}$/.test(numeros) || !ddds.has(numeros.slice(0, 2))) erro('WhatsApp', 'informe um DDD brasileiro válido e o número completo');
    if (!/^(?:[2-5][0-9]{7}|9[0-9]{8})$/.test(numeros.slice(2))) erro('WhatsApp', 'celular deve começar com 9 e ter nove dígitos; fixo deve ter oito');
    return '+55' + numeros;
  }
  const validadores = { cpf, rg, whatsapp };
  const caracteres = { cpf: /^[0-9. -]*$/, rg: /^[A-Za-z0-9. -]*$/, whatsapp: /^[+0-9 ()-]*$/ };
  const limites = { cpf: 14, rg: 24, whatsapp: 32 };
  function pontuarCpf(numeros) {
    return numeros.replace(/^([0-9]{3})([0-9])/, '$1.$2').replace(/^([0-9]{3}\.[0-9]{3})([0-9])/, '$1.$2').replace(/([0-9]{3}\.[0-9]{3}\.[0-9]{3})([0-9])/, '$1-$2');
  }
  function formatar(tipo, valor) {
    if (!caracteres[tipo].test(valor) || valor.length > limites[tipo]) return valor;
    if (tipo === 'cpf') {
      const numeros = valor.replace(/[. -]/g, '');
      return numeros.length <= 11 ? pontuarCpf(numeros) : valor;
    }
    if (tipo === 'rg') {
      const numero = valor.toUpperCase().replace(/[. -]/g, '');
      if (/^[0-9]{9}X?$/.test(numero) && numero.length === 9) return numero.replace(/^(.{2})(.{3})(.{3})(.)$/, '$1.$2.$3-$4');
      if (/^[0-9]{8}X$/.test(numero)) return numero.replace(/^(.{2})(.{3})(.{3})(.)$/, '$1.$2.$3-$4');
      if (/^[A-Z]{1,2}[0-9]/.test(numero)) return numero.replace(/^([A-Z]{1,2})([0-9]+)/, (_, prefixo, digitos) => prefixo + '-' + digitos.replace(/\B(?=(\d{3})+(?!\d))/g, '.'));
      if (/^[0-9]{11}$/.test(numero)) {
        try { cpf(numero); return pontuarCpf(numero); } catch { /* RG antigo não tem regra nacional de dígito. */ }
      }
      if (/^[0-9]+X?$/.test(numero)) {
        const digitos = numero.replace(/X$/, '');
        return digitos.replace(/\B(?=(\d{3})+(?!\d))/g, '.') + (numero.endsWith('X') ? '-X' : '');
      }
      return numero;
    }
    let numeros = valor.replace(/[+ ()-]/g, '');
    const comPais = valor.trim().startsWith('+') || numeros.length > 11;
    if (comPais && !numeros.startsWith('55')) return valor.trim().startsWith('+') ? '+' + numeros : valor;
    if (comPais) numeros = numeros.slice(2);
    if (numeros.length > 11) return valor;
    let resultado = comPais ? '+55 ' : '';
    if (!numeros) return resultado.trimEnd();
    resultado += '(' + numeros.slice(0, 2);
    if (numeros.length >= 2) resultado += ') ';
    const assinante = numeros.slice(2);
    const corte = assinante.startsWith('9') ? 5 : 4;
    resultado += assinante.slice(0, corte);
    if (assinante.length > corte) resultado += '-' + assinante.slice(corte);
    return resultado;
  }
  function ligar(campo, tipo, atualizar) {
    campo.dataset.dadoPessoal = tipo;
    campo.maxLength = limites[tipo];
    campo.inputMode = tipo === 'rg' ? 'text' : 'tel';
    campo.spellcheck = false;
    if (tipo === 'rg') campo.autocapitalize = 'characters';
    campo.value = formatar(tipo, campo.value);
    const mensagem = document.createElement('span');
    mensagem.id = campo.id + '-erro'; mensagem.className = 'erro-validacao-pessoal'; mensagem.hidden = true;
    mensagem.setAttribute('aria-live', 'polite'); campo.setAttribute('aria-describedby', mensagem.id); campo.after(mensagem);
    let exibirErro = false;
    function conferir() {
      try {
        const valor = validadores[tipo](campo.value);
        campo.setCustomValidity(''); campo.removeAttribute('aria-invalid'); mensagem.hidden = true;
        atualizar(valor);
      } catch (erro) {
        campo.setCustomValidity(erro.message); campo.setAttribute('aria-invalid', 'true');
        mensagem.textContent = erro.message; mensagem.hidden = !exibirErro; atualizar(campo.value);
      }
    }
    campo.addEventListener('paste', evento => {
      const texto = evento.clipboardData?.getData('text') || '';
      const proposto = campo.value.slice(0, campo.selectionStart) + texto + campo.value.slice(campo.selectionEnd);
      if (!caracteres[tipo].test(texto) || proposto.length > limites[tipo]) {
        evento.preventDefault(); exibirErro = true;
        campo.setCustomValidity('O conteúdo colado contém caracteres inválidos ou excede o tamanho do campo.');
        mensagem.textContent = campo.validationMessage; mensagem.hidden = false; campo.setAttribute('aria-invalid', 'true');
      }
    });
    campo.addEventListener('input', () => {
      const posicao = campo.selectionStart ?? campo.value.length;
      const significativos = (campo.value.slice(0, posicao).match(/[A-Za-z0-9+]/g) || []).length;
      campo.value = formatar(tipo, campo.value);
      let novaPosicao = 0, quantidade = 0;
      while (novaPosicao < campo.value.length && quantidade < significativos) {
        if (/[A-Za-z0-9+]/.test(campo.value[novaPosicao])) quantidade++;
        novaPosicao++;
      }
      campo.setSelectionRange(novaPosicao, novaPosicao); conferir();
    });
    campo.addEventListener('beforeinput', evento => {
      if (evento.inputType === 'insertText' && evento.data && !caracteres[tipo].test(evento.data)) {
        evento.preventDefault(); exibirErro = true;
        campo.setCustomValidity('Esse caractere não é permitido neste campo.');
        campo.setAttribute('aria-invalid', 'true'); mensagem.textContent = campo.validationMessage; mensagem.hidden = false;
        return;
      }
      if (!['deleteContentBackward', 'deleteContentForward'].includes(evento.inputType)
          || campo.selectionStart !== campo.selectionEnd) return;
      const posicao = campo.selectionStart;
      if (evento.inputType === 'deleteContentBackward') {
        if (!posicao || /[A-Za-z0-9+]/.test(campo.value[posicao - 1])) return;
        let inicio = posicao - 1;
        while (inicio > 0 && !/[A-Za-z0-9+]/.test(campo.value[inicio])) inicio--;
        evento.preventDefault(); campo.setRangeText('', inicio, posicao, 'start');
      } else {
        if (posicao >= campo.value.length || /[A-Za-z0-9+]/.test(campo.value[posicao])) return;
        let fim = posicao;
        while (fim < campo.value.length && !/[A-Za-z0-9+]/.test(campo.value[fim])) fim++;
        evento.preventDefault(); campo.setRangeText('', posicao, Math.min(fim + 1, campo.value.length), 'start');
      }
      campo.dispatchEvent(new Event('input', { bubbles: true }));
    });
    campo.addEventListener('blur', () => { exibirErro = true; conferir(); });
    campo.addEventListener('invalid', () => { exibirErro = true; mensagem.textContent = campo.validationMessage; mensagem.hidden = false; });
    conferir();
  }
  return { cpf, rg, whatsapp, ligar };
})();
