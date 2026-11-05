/** Estrutura compartilhada pelos tratores daFIRMA. */
(() => {
  const recursos = Object.freeze({
    selecao: true,
    acoes: true,
    paginacao: true,
    selecionarColunas: true,
    filtrosHeader: true,
    ordenacao: true,
    redimensionamento: true,
    reordenacao: true,
    filtrosSalvos: true,
    buscaIA: true,
    edicao: true,
    detalhes: true,
    persistencia: true
  });
  // As abas deslizam atrás do filtro em todos os módulos.
  function configuracaoBusca(instancia) {
    return typeof instancia.config.buscarNoServidor === 'function';
  }
  function prepararFaixa(instancia) {
    const faixa = instancia.raiz.querySelector('.toolbar-faixa>.toolbar-grupo');
    if (faixa.dataset.arrastavel) return;
    // Buscas paginadas usam o mesmo filtro visual, com execução no servidor.
    if (!instancia.no('busca-form') && configuracaoBusca(instancia)) {
      const grupo = document.createElement('div'),
        formulario = document.createElement('form'),
        entrada = document.createElement('input'),
        enviar = document.createElement('button');
      grupo.className = 'toolbar-grupo';
      formulario.className = 'af-busca';
      formulario.id = instancia.id + '-busca-form';
      entrada.type = 'search';
      entrada.maxLength = 200;
      entrada.placeholder = instancia.textos.busca;
      entrada.setAttribute('aria-label', instancia.textos.busca);
      enviar.type = 'submit';
      enviar.textContent = '↵';
      enviar.setAttribute('aria-label', 'Buscar nos registros');
      formulario.append(entrada, enviar);
      grupo.append(formulario);
      faixa.parentElement.append(grupo);
      instancia.ouvir(formulario, 'submit', async evento => {
        evento.preventDefault();
        if (enviar.disabled) return;
        enviar.disabled = true;
        instancia.estado.pagina = 1;
        try {
          await instancia.config.buscarNoServidor(entrada.value.trim(), instancia);
        } catch (erro) {
          instancia.config.toast?.(erro.message);
        } finally {
          enviar.disabled = false;
        }
      });
    }
    faixa.dataset.arrastavel = 'true';
    faixa.classList.add('trator-faixa-abas');
    faixa.tabIndex = 0;
    faixa.setAttribute('role', 'region');
    faixa.setAttribute('aria-label', 'Abas do trator. Arraste para os lados ou use as setas.');
    instancia.raiz.classList.add('trator-compartilhado');
    const busca = instancia.no('busca-form')?.parentElement;
    busca?.classList.add('trator-burro-fixo');
    if (instancia.config.slots?.abasFinais) {
      const finais = document.createElement('nav');
      finais.className = 'trator-firma trator-abas-finais';
      finais.setAttribute('aria-label', 'Outras telas');
      instancia.slot(finais, 'abasFinais');
      faixa.append(finais);
    }
    let gesto = null,
      bloquearClique = false;
    const fecharMenus = () => {
      faixa.querySelectorAll('[aria-controls]').forEach(botao => {
        const menu = document.getElementById(botao.getAttribute('aria-controls'));
        if (menu?.matches(':popover-open')) menu.hidePopover();
      });
      instancia.fecharMenus();
    };
    instancia.ouvir(faixa, 'pointerdown', evento => {
      bloquearClique = false;
      if (evento.pointerType === 'touch' || evento.button !== 0 || evento.target.closest('input,textarea,select,label')) return;
      gesto = {
        id: evento.pointerId,
        x: evento.clientX,
        y: evento.clientY,
        inicio: faixa.scrollLeft,
        arrastou: false
      };
    });
    instancia.ouvir(faixa, 'pointermove', evento => {
      if (!gesto || evento.pointerId !== gesto.id) return;
      const distanciaHorizontal = evento.clientX - gesto.x,
        distanciaVertical = evento.clientY - gesto.y;
      if (!gesto.arrastou) {
        if (Math.abs(distanciaHorizontal) < 6 || Math.abs(distanciaHorizontal) < Math.abs(distanciaVertical)) return;
        gesto.arrastou = true;
        bloquearClique = true;
        faixa.setPointerCapture(evento.pointerId);
        faixa.classList.add('arrastando');
        fecharMenus();
      }
      evento.preventDefault();
      faixa.scrollLeft = gesto.inicio - distanciaHorizontal;
    });
    const encerrar = evento => {
      if (!gesto || evento.pointerId !== gesto.id) return;
      gesto = null;
      faixa.classList.remove('arrastando');
    };
    instancia.ouvir(faixa, 'pointerup', encerrar);
    instancia.ouvir(faixa, 'pointercancel', encerrar);
    instancia.ouvir(faixa, 'lostpointercapture', encerrar);
    instancia.ouvir(window, 'pointerup', encerrar);
    instancia.ouvir(faixa, 'click', evento => {
      if (bloquearClique) {
        evento.preventDefault();
        evento.stopImmediatePropagation();
        bloquearClique = false;
      }
    }, {capture: true});
    instancia.ouvir(faixa, 'dragstart', evento => evento.preventDefault());
    instancia.ouvir(faixa, 'scroll', fecharMenus, {
      passive: true
    });
    instancia.ouvir(faixa, 'keydown', evento => {
      if (evento.target !== faixa) return;
      if (evento.key === 'ArrowRight' || evento.key === 'ArrowLeft') {
        evento.preventDefault();
        faixa.scrollLeft += evento.key === 'ArrowRight' ? 180 : -180;
      } else if (evento.key === 'Home' || evento.key === 'End') {
        evento.preventDefault();
        faixa.scrollLeft = evento.key === 'Home' ? 0 : faixa.scrollWidth;
      }
    });
    instancia.ouvir(faixa, 'focusin', evento => {
      if (evento.target === faixa) return;
      const registro = evento.target.getBoundingClientRect(),
        limite = busca ? busca.getBoundingClientRect().left - 16 : faixa.getBoundingClientRect().right,
        inicio = faixa.getBoundingClientRect().left;
      if (registro.right > limite) faixa.scrollLeft += registro.right - limite;else if (registro.left < inicio) faixa.scrollLeft += registro.left - inicio;
    });
  }
  function montar(configuracao) {
    const rodape = configuracao.slots?.rodape;
    return new window.FirmaTabela({
      tamanhoPagina: 10,
      tamanhosPagina: [5, 10, 50, 100],
      larguraAcoes: 176,
      ...configuracao,
      textos: {
        busca: 'FALAR COM O BURRO DE CARGA...',
        pagina: ({
          pagina,
          totalPaginas,
          tamanho
        }) => `ÁREA ${pagina} de ${totalPaginas} do TERRENO → ${tamanho} Sementes ▾`,
        ...configuracao.textos
      },
      recursos: {
        ...recursos,
        ...configuracao.recursos
      },
      slots: {
        ...configuracao.slots,
        rodape: (destino, instancia) => {
          if (typeof rodape === 'function') rodape(destino, instancia);else if (rodape instanceof Node) destino.append(rodape);
          const selecao = document.createElement('span');
          selecao.className = 'trator-selecao';
          selecao.hidden = true;
          selecao.setAttribute('aria-live', 'polite');
          destino.insertBefore(selecao, destino.querySelector('.titulo-trator'));
        }
      },
      onRender: instancia => {
        prepararFaixa(instancia);
        const selo = instancia.raiz.querySelector('.trator-selecao');
        if (selo) {
          const valor = instancia.obterSelecionados().length;
          selo.hidden = !valor;
          selo.textContent = valor + ' selecionado' + (valor === 1 ? '' : 's');
        }
        configuracao.onRender?.(instancia);
      }
    });
  }
  function configurarLargura(conteudoPrincipal, botao, storageId) {
    const modos = [['total', 'TELA INTEIRA'], ['largo', '80% DA TELA'], ['centro', 'CENTRO']];
    const aplicar = modo => {
      const escolhido = modos.find(item => item[0] === modo) || modos[0];
      conteudoPrincipal.dataset.largura = escolhido[0];
      botao.textContent = '▤ ' + escolhido[1];
      try {
        localStorage.setItem(storageId, escolhido[0]);
      } catch {}
    };
    botao.onclick = () => {
      const indice = modos.findIndex(item => item[0] === conteudoPrincipal.dataset.largura);
      aplicar(modos[(indice + 1) % modos.length][0]);
    };
    let salvo;
    try {
      salvo = localStorage.getItem(storageId);
    } catch {}
    aplicar(salvo || 'total');
  }
  window.FirmaTrator = Object.freeze({
    recursos,
    montar,
    configurarLargura
  });
})();
