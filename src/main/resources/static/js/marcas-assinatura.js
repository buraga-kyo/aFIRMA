/* As três formas de criar uma marca compartilham a mesma prévia PNG transparente. */
window.FirmaMarcas = (() => {
  const fontes = { 'Firma Great Vibes': 'Clássica', 'Firma Dancing Script': 'Cursiva' };
  function canvas(largura, altura) {
    const tela = document.createElement('canvas');
    tela.width = largura;
    tela.height = altura;
    return tela;
  }

  function preparar(id, informar) {
    const previa = document.getElementById(id);
    const grupo = previa.closest('.grupo-desenho');
    const nome = id.endsWith('rubrica') ? 'rubrica' : 'assinatura';
    const prefixo = `marca-${nome}`;
    const manual = canvas(800, 240);
    manual.className = 'assinatura-desenho marca-manual';
    manual.setAttribute('aria-label', `Desenhe sua ${nome} com o dedo ou mouse`);
    const painel = document.createElement('div');
    painel.className = 'editor-marca';
    painel.innerHTML = `
      <div class="marca-modos" role="group" aria-label="Como criar sua ${nome}">
        <button type="button" data-modo="desenhar" aria-pressed="true">DESENHAR</button>
        <button type="button" data-modo="digitar" aria-pressed="false">DIGITAR</button>
        <button type="button" data-modo="imagem" aria-pressed="false">ENVIAR IMAGEM</button>
      </div>
      <div data-painel="desenhar"><p>Use o dedo ou o mouse no quadro abaixo.</p></div>
      <div data-painel="digitar" hidden>
        <label for="${prefixo}-texto">${nome === 'rubrica' ? 'INICIAIS OU RUBRICA' : 'NOME PARA ASSINAR'}</label>
        <input id="${prefixo}-texto" type="text" maxlength="120" autocomplete="off" placeholder="${nome === 'rubrica' ? 'Suas iniciais' : 'Seu nome'}">
        <label for="${prefixo}-fonte">ESTILO DA LETRA</label>
        <select id="${prefixo}-fonte">${Object.entries(fontes).map(([valor, titulo]) => `<option value="${valor}">${titulo}</option>`).join('')}</select>
      </div>
      <div data-painel="imagem" hidden>
        <label for="${prefixo}-arquivo">IMAGEM DA ${nome.toUpperCase()}</label>
        <input id="${prefixo}-arquivo" type="file" accept="image/png,image/jpeg">
        <p>Envie PNG ou JPEG de até 10 MB. Prefira uma foto bem iluminada.</p>
        <div data-ajustes hidden>
          <label class="marca-checkbox"><input type="checkbox" id="${prefixo}-fundo" checked> REMOVER FUNDO BRANCO</label>
          <label for="${prefixo}-intensidade">INTENSIDADE DA REMOÇÃO</label>
          <input id="${prefixo}-intensidade" type="range" min="0" max="100" value="35">
          <p>Aumente aos poucos e confira se o traço continua inteiro.</p>
          <p>Arraste a área marcada para escolher o trecho. Puxe as bordas ou os cantos para recortar. Amplie para ajustar os detalhes.</p>
          <div class="marca-recorte-janela" aria-label="Editor visual de recorte">
            <div class="marca-recorte-espaco"><div class="marca-recorte-area">
              <canvas class="marca-recorte-imagem" aria-label="Imagem original para recortar"></canvas>
              <div class="marca-recorte-selecao" tabindex="0" role="group" aria-label="Área de recorte. Arraste ou use as setas para mover.">
                <span class="marca-recorte-grade" aria-hidden="true"></span>
                ${[['n', 'superior'], ['s', 'inferior'], ['w', 'esquerda'], ['e', 'direita'],
                  ['nw', 'superior esquerdo'], ['ne', 'superior direito'], ['sw', 'inferior esquerdo'], ['se', 'inferior direito']]
                  .map(([alca, titulo]) => `<button type="button" data-alca="${alca}" aria-label="Ajustar recorte: ${titulo}. Use as setas."></button>`).join('')}
              </div>
            </div></div>
          </div>
          <div class="marca-recorte-ferramentas">
            <label for="${prefixo}-zoom">AMPLIAR</label>
            <input id="${prefixo}-zoom" type="range" min="1" max="4" step="0.1" value="1">
            <button type="button" data-reiniciar-recorte>RESTAURAR RECORTE</button>
          </div>
        </div>
      </div>
      <p class="marca-estado" role="status" aria-live="polite"></p>
      <p class="marca-previa-titulo">PRÉVIA DA ${nome.toUpperCase()}</p>`;
    grupo.querySelector('p')?.remove();
    grupo.insertBefore(painel, previa);
    painel.querySelector('[data-painel="desenhar"]').append(manual);
    previa.className = 'assinatura-desenho marca-previa';
    previa.setAttribute('aria-label', `Prévia da ${nome} que será inserida no PDF`);
    const obter = sufixo => painel.querySelector(`#${prefixo}-${sufixo}`);
    const estado = painel.querySelector('.marca-estado');
    const contexto = manual.getContext('2d');
    contexto.strokeStyle = '#0E2117';
    contexto.lineWidth = 4;
    contexto.lineCap = 'round';
    contexto.lineJoin = 'round';
    let modo = 'desenhar', imagem, desenhando = false, versao = 0, carga = 0;
    let corte = { x: 0, y: 0, w: 1, h: 1 };
    const janela = painel.querySelector('.marca-recorte-janela');
    const area = painel.querySelector('.marca-recorte-area');
    const selecao = painel.querySelector('.marca-recorte-selecao');
    const limitar = (valor, minimo, maximo) => Math.max(minimo, Math.min(maximo, valor));
    function mostrarCorte() {
      selecao.style.left = `${corte.x * 100}%`;
      selecao.style.top = `${corte.y * 100}%`;
      selecao.style.width = `${corte.w * 100}%`;
      selecao.style.height = `${corte.h * 100}%`;
    }
    function dimensionarCorte() {
      if (!imagem || !janela.clientWidth) return;
      const escala = Math.min((janela.clientWidth - 32) / imagem.width, 268 / imagem.height);
      const zoom = Number(obter('zoom').value);
      area.style.width = `${Math.max(1, imagem.width * escala * zoom)}px`;
      area.style.height = `${Math.max(1, imagem.height * escala * zoom)}px`;
      mostrarCorte();
    }
    function ajustarCorte(inicio, alca, dx, dy) {
      if (!alca) {
        corte.x = limitar(inicio.x + dx, 0, 1 - inicio.w);
        corte.y = limitar(inicio.y + dy, 0, 1 - inicio.h);
      } else {
        const minW = Math.min(.1, 24 / area.clientWidth), minH = Math.min(.1, 24 / area.clientHeight);
        let esquerda = inicio.x, direita = inicio.x + inicio.w;
        let topo = inicio.y, base = inicio.y + inicio.h;
        if (alca.includes('w')) esquerda = limitar(inicio.x + dx, 0, direita - minW);
        if (alca.includes('e')) direita = limitar(direita + dx, esquerda + minW, 1);
        if (alca.includes('n')) topo = limitar(inicio.y + dy, 0, base - minH);
        if (alca.includes('s')) base = limitar(base + dy, topo + minH, 1);
        corte = { x: esquerda, y: topo, w: direita - esquerda, h: base - topo };
      }
      mostrarCorte();
      atualizar();
    }
    selecao.addEventListener('pointerdown', evento => {
      if (evento.button !== 0) return;
      evento.preventDefault(); evento.stopPropagation();
      const alvo = evento.target.closest('[data-alca]') || selecao;
      alvo.focus({ preventScroll: true });
      selecao.setPointerCapture(evento.pointerId);
      const inicio = { ...corte }, caixa = area.getBoundingClientRect();
      const x = evento.clientX, y = evento.clientY, alca = alvo.dataset.alca;
      const mover = e => ajustarCorte(inicio, alca, (e.clientX - x) / caixa.width, (e.clientY - y) / caixa.height);
      const terminar = () => {
        selecao.removeEventListener('pointermove', mover);
        for (const tipo of ['pointerup', 'pointercancel', 'lostpointercapture']) selecao.removeEventListener(tipo, terminar);
      };
      selecao.addEventListener('pointermove', mover);
      for (const tipo of ['pointerup', 'pointercancel', 'lostpointercapture']) selecao.addEventListener(tipo, terminar);
    });
    selecao.addEventListener('keydown', evento => {
      const direcoes = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] };
      if (!direcoes[evento.key]) return;
      evento.preventDefault(); evento.stopPropagation();
      const passo = evento.shiftKey ? .05 : .01, [dx, dy] = direcoes[evento.key];
      ajustarCorte({ ...corte }, evento.target.dataset.alca, dx * passo, dy * passo);
    });
    // No espaço fora da seleção, arrastar desloca a imagem ampliada.
    janela.addEventListener('pointerdown', evento => {
      if (evento.button !== 0 || evento.target.closest('.marca-recorte-selecao')) return;
      evento.preventDefault();
      janela.setPointerCapture(evento.pointerId);
      const x = evento.clientX, y = evento.clientY, esquerda = janela.scrollLeft, topo = janela.scrollTop;
      const mover = e => { janela.scrollLeft = esquerda - (e.clientX - x); janela.scrollTop = topo - (e.clientY - y); };
      const terminar = () => {
        janela.removeEventListener('pointermove', mover);
        for (const tipo of ['pointerup', 'pointercancel', 'lostpointercapture']) janela.removeEventListener(tipo, terminar);
      };
      janela.addEventListener('pointermove', mover);
      for (const tipo of ['pointerup', 'pointercancel', 'lostpointercapture']) janela.addEventListener(tipo, terminar);
    });
    obter('zoom').addEventListener('input', () => {
      const anteriorW = area.clientWidth, anteriorH = area.clientHeight;
      const cx = (janela.scrollLeft + janela.clientWidth / 2) / Math.max(janela.clientWidth, anteriorW);
      const cy = (janela.scrollTop + janela.clientHeight / 2) / Math.max(janela.clientHeight, anteriorH);
      dimensionarCorte();
      janela.scrollLeft = cx * Math.max(janela.clientWidth, area.clientWidth) - janela.clientWidth / 2;
      janela.scrollTop = cy * Math.max(janela.clientHeight, area.clientHeight) - janela.clientHeight / 2;
    });
    painel.querySelector('[data-reiniciar-recorte]').addEventListener('click', () => {
      corte = { x: 0, y: 0, w: 1, h: 1 };
      obter('zoom').value = '1';
      dimensionarCorte(); atualizar();
    });
    new ResizeObserver(dimensionarCorte).observe(janela);
    const limparPrevia = () => {
      previa.getContext('2d').clearRect(0, 0, previa.width, previa.height);
      informar(false);
    };
    function recortar(tela) {
      const ctx = tela.getContext('2d');
      const { data } = ctx.getImageData(0, 0, tela.width, tela.height);
      let x0 = tela.width, y0 = tela.height, x1 = -1, y1 = -1;
      for (let y = 0; y < tela.height; y++) for (let x = 0; x < tela.width; x++) {
        if (data[(y * tela.width + x) * 4 + 3] <= 8) continue;
        x0 = Math.min(x0, x); y0 = Math.min(y0, y);
        x1 = Math.max(x1, x); y1 = Math.max(y1, y);
      }
      if (x1 < x0) return false;
      previa.width = x1 - x0 + 1 + 8;
      previa.height = y1 - y0 + 1 + 8;
      previa.getContext('2d').drawImage(tela, x0, y0, x1 - x0 + 1, y1 - y0 + 1,
        4, 4, x1 - x0 + 1, y1 - y0 + 1);
      return true;
    }
    async function atualizar() {
      const atual = ++versao;
      limparPrevia();
      estado.textContent = '';
      try {
        let tela = manual;
        if (modo === 'digitar') {
          const texto = obter('texto').value.trim();
          if (!texto) return;
          const fonte = obter('fonte').value;
          estado.textContent = 'Preparando a letra…';
          const carregadas = await document.fonts.load(`100px "${fonte}"`, texto);
          if (atual !== versao) return;
          if (!carregadas.length) throw Error('Não foi possível carregar a letra. Tente novamente ou escolha outra forma.');
          tela = canvas(1200, 320);
          const ctx = tela.getContext('2d');
          let tamanho = 120, medida;
          do {
            ctx.font = `${tamanho}px "${fonte}"`;
            medida = ctx.measureText(texto);
            if (Math.max(medida.width, medida.actualBoundingBoxLeft + medida.actualBoundingBoxRight) <= 1120) break;
            tamanho -= 2;
          } while (tamanho > 8);
          ctx.fillStyle = '#0E2117';
          ctx.fillText(texto, 40 + Math.max(0, medida.actualBoundingBoxLeft),
            (320 + medida.actualBoundingBoxAscent - medida.actualBoundingBoxDescent) / 2);
        } else if (modo === 'imagem') {
          if (!imagem) return;
          const w = imagem.width * corte.w;
          const h = imagem.height * corte.h;
          const escala = Math.min(1, 1200 / w, 400 / h);
          tela = canvas(Math.max(1, Math.round(w * escala)), Math.max(1, Math.round(h * escala)));
          const ctx = tela.getContext('2d');
          ctx.drawImage(imagem, imagem.width * corte.x, imagem.height * corte.y,
            w, h, 0, 0, tela.width, tela.height);
          if (obter('fundo').checked) {
            const pixels = ctx.getImageData(0, 0, tela.width, tela.height);
            const limite = 255 - Number(obter('intensidade').value) * 1.4;
            for (let i = 0; i < pixels.data.length; i += 4) {
              const branco = Math.min(pixels.data[i], pixels.data[i + 1], pixels.data[i + 2]);
              if (branco >= limite) pixels.data[i + 3] *= branco === 255 ? 0 : (255 - branco) / (255 - limite);
            }
            ctx.putImageData(pixels, 0, 0);
          }
        }
        if (atual !== versao) return;
        if (!recortar(tela)) {
          estado.textContent = modo === 'imagem' ? 'Não há traços visíveis. Reduza a intensidade ou ajuste o recorte.' : '';
          return;
        }
        informar(true);
        estado.textContent = 'Confira a prévia antes de confirmar. O quadriculado indica transparência.';
      } catch (falha) {
        if (atual === versao) {
          limparPrevia();
          estado.textContent = falha.message || 'Não foi possível preparar a marca.';
        }
      }
    }
    for (const botao of painel.querySelectorAll('[data-modo]')) {
      botao.addEventListener('click', () => {
        desenhando = false;
        modo = botao.dataset.modo;
        for (const aba of painel.querySelectorAll('[data-modo]')) aba.setAttribute('aria-pressed', String(aba === botao));
        for (const area of painel.querySelectorAll('[data-painel]')) area.hidden = area.dataset.painel !== modo;
        if (modo === 'imagem') dimensionarCorte();
        atualizar();
      });
    }
    for (const input of painel.querySelectorAll('input:not([type="file"]):not([id$="-zoom"]), select')) input.addEventListener('input', atualizar);
    obter('fundo').addEventListener('change', () => { obter('intensidade').disabled = !obter('fundo').checked; });
    obter('arquivo').addEventListener('change', async () => {
      const atual = ++carga;
      ++versao;
      imagem = null;
      painel.querySelector('[data-ajustes]').hidden = true;
      limparPrevia();
      const arquivo = obter('arquivo').files[0];
      if (!arquivo) return;
      estado.textContent = 'Carregando imagem…';
      try {
        if (!['image/png', 'image/jpeg'].includes(arquivo.type)) throw Error('Envie uma imagem PNG ou JPEG.');
        if (arquivo.size > 10 * 1024 * 1024) throw Error('A imagem deve ter até 10 MB.');
        const bitmap = await createImageBitmap(arquivo);
        if (atual !== carga) { bitmap.close(); return; }
        if (bitmap.width * bitmap.height > 20000000) { bitmap.close(); throw Error('Reduza a imagem para até 20 megapixels.'); }
        // Mantém uma cópia limitada para que os ajustes sejam rápidos no celular.
        const escala = Math.min(1, 1800 / bitmap.width, 1800 / bitmap.height);
        imagem = canvas(Math.max(1, Math.round(bitmap.width * escala)), Math.max(1, Math.round(bitmap.height * escala)));
        imagem.getContext('2d').drawImage(bitmap, 0, 0, imagem.width, imagem.height);
        bitmap.close();
        corte = { x: 0, y: 0, w: 1, h: 1 };
        obter('zoom').value = '1';
        const original = painel.querySelector('.marca-recorte-imagem');
        original.width = imagem.width; original.height = imagem.height;
        original.getContext('2d').drawImage(imagem, 0, 0);
        painel.querySelector('[data-ajustes]').hidden = false;
        dimensionarCorte();
        atualizar();
      } catch (falha) {
        if (atual === carga) {
          if (modo === 'imagem') { limparPrevia(); estado.textContent = falha.message; }
        }
      }
    });
    const ponto = evento => {
      const caixa = manual.getBoundingClientRect();
      return { x: (evento.clientX - caixa.left) * manual.width / caixa.width,
        y: (evento.clientY - caixa.top) * manual.height / caixa.height };
    };
    manual.addEventListener('pointerdown', evento => {
      if (evento.button !== 0) return;
      evento.preventDefault();
      desenhando = true;
      informar(false);
      manual.setPointerCapture(evento.pointerId);
      const p = ponto(evento);
      contexto.beginPath(); contexto.moveTo(p.x, p.y); contexto.lineTo(p.x + .1, p.y + .1); contexto.stroke();
    });
    manual.addEventListener('pointermove', evento => {
      if (!desenhando) return;
      const p = ponto(evento); contexto.lineTo(p.x, p.y); contexto.stroke();
    });
    for (const tipo of ['pointerup', 'pointercancel', 'lostpointercapture']) manual.addEventListener(tipo, () => {
      if (!desenhando) return;
      desenhando = false;
      atualizar();
    });
    grupo.querySelector('[data-limpar]').addEventListener('click', () => {
      ++carga; ++versao;
      desenhando = false;
      if (modo === 'desenhar') contexto.clearRect(0, 0, manual.width, manual.height);
      if (modo === 'digitar') obter('texto').value = '';
      if (modo === 'imagem') {
        imagem = null;
        obter('arquivo').value = '';
        painel.querySelector('[data-ajustes]').hidden = true;
      }
      atualizar();
    });
    informar(false);
  }
  return { preparar };
})();
