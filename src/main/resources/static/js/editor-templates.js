(() => {
  const porId = id => document.getElementById(id),
    clonar = valorAtual => JSON.parse(JSON.stringify(valorAtual));
  const rotulos = {
    cabecalho: 'Identidade e título',
    documento: 'Dados do documento',
    signatarios: 'Dados dos signatários',
    integridade: 'Integridade e emissão'
  };
  let biblioteca,
    selecionado,
    catalogo,
    pendente = false,
    espera,
    versao = 0,
    enderecoPrevia;
  const historico = [],
    futuro = [],
    enderecos = [];
  const atual = () => biblioteca.modelos.find(item => item.id === selecionado);
  const estado = elemento => porId('template-estado').textContent = elemento;
  async function api(caminho, dados) {
    const resposta = await fetch('/api/assineai/empresa/templates' + caminho, {
      method: dados ? 'POST' : 'GET',
      cache: 'no-store',
      headers: {
        Authorization: `Bearer ${porId('empresa-token').value.trim()}`,
        ...(dados ? {
          'Content-Type': 'application/json'
        } : {})
      },
      ...(dados ? {
        body: JSON.stringify(dados)
      } : {})
    });
    if (!resposta.ok) {
      const evento = await resposta.json().catch(() => ({}));
      throw Error(evento.mensagem || `Não foi possível renderizar a folha (${resposta.status}).`);
    }
    return resposta;
  }
  function marcar() {
    pendente = true;
    estado('Alterações em edição. Salve para aplicar às próximas gerações do PDF.');
  }
  function guardar() {
    historico.push(clonar(atual()));
    if (historico.length > 60) historico.shift();
    futuro.length = 0;
  }
  function lista() {
    const seletor = porId('template-selecionado');
    seletor.replaceChildren();
    for (const item of biblioteca.modelos) {
      const elementoAtual = document.createElement('option');
      elementoAtual.value = item.id;
      elementoAtual.textContent = item.nome + (item.id === biblioteca.ativo ? ' · PADRÃO' : '');
      seletor.append(elementoAtual);
    }
    seletor.value = selecionado;
    porId('template-ativo').textContent = 'Padrão da empresa: ' + biblioteca.modelos.find(item => item.id === biblioteca.ativo).nome;
    document.querySelectorAll('.registro-card').forEach(botaoAtual => botaoAtual.setAttribute('aria-pressed', String(botaoAtual.dataset.design === atual().design)));
  }
  function preencher() {
    lista();
    document.querySelectorAll('[data-modelo]').forEach(evento => evento.value = atual()[evento.dataset.modelo] ?? (evento.dataset.modelo === 'opacidade' ? 0.08 : ''));
    document.querySelectorAll('[data-cor]').forEach(evento => evento.value = atual().cores[evento.dataset.cor]);
    document.querySelector('[data-modelo="tamanho"]').min = atual().design === 'assineai' ? 7 : 8;
    porId('template-canvas').style.aspectRatio = atual().design === 'assineai' ? '595 / 840' : '595 / 842';
    medidas();
    alcas();
    agendar();
  }
  function alturaMinima(area) {
    return area === 'signatarios' ? 180 : area === 'documento' ? (atual().design === 'assineai' ? 70 : 130) : area === 'cabecalho' ? (atual().design === 'assineai' ? 35 : atual().design === 'classico' ? 40 : 110) : 60;
  }
  function medidas() {
    const elementoDoRegistro = atual().areas[porId('template-area').value];
    document.querySelectorAll('[data-area]').forEach(evento => {
      evento.value = elementoDoRegistro[evento.dataset.area];
      if (evento.dataset.area === 'h') evento.min = alturaMinima(porId('template-area').value);
    });
  }
  function agendar() {
    clearTimeout(espera);
    espera = setTimeout(() => previa().catch(evento => estado(evento.message)), 650);
  }
  async function previa() {
    if (window.EditorDocuseal?.documentoAberto()) return;
    const minhaVersao = ++versao;
    const elementoDaTela = await api('/previa', {
      modelo: clonar(atual()),
      empresa: porId('perfil-nome').value.trim() || 'Sua empresa',
      formato: 'png'
    });
    const endereco = URL.createObjectURL(await elementoDaTela.blob());
    if (minhaVersao !== versao) {
      URL.revokeObjectURL(endereco);
      return;
    }
    if (enderecoPrevia) URL.revokeObjectURL(enderecoPrevia);
    enderecoPrevia = endereco;
    if (window.EditorDocuseal?.documentoAberto()) { URL.revokeObjectURL(endereco); return; }
    porId('template-previa').src = endereco;
    alcas();
  }
  function alcas() {
    const camada = porId('template-alcas');
    camada.replaceChildren();
    camada.hidden = !porId('template-editar-posicoes').checked || !!window.EditorDocuseal?.documentoAberto();
    for (const [id, registro] of Object.entries(atual().areas)) {
      const conteiner = document.createElement('div');
      conteiner.className = 'registro-alca' + (id === porId('template-area').value ? ' selecionada' : '');
      conteiner.dataset.bloco = id;
      conteiner.tabIndex = 0;
      conteiner.setAttribute('role', 'button');
      conteiner.setAttribute('aria-label', `Mover ${rotulos[id]}. Use as setas do teclado.`);
      const rotulo = document.createElement('span');
      rotulo.textContent = rotulos[id];
      const redimensionarAtual = document.createElement('i');
      conteiner.append(rotulo, redimensionarAtual);
      posicionar(conteiner, registro);
      camada.append(conteiner);
      conteiner.onpointerdown = evento => {
        evento.preventDefault();
        porId('template-area').value = id;
        camada.querySelectorAll('.registro-alca').forEach(no => no.classList.toggle('selecionada', no === conteiner));
        medidas();
        guardar();
        const inicio = {
            ...registro
          },
          x = evento.clientX,
          y = evento.clientY,
          escala = porId('template-canvas').getBoundingClientRect().width / 595,
          redimensionar = evento.target === redimensionarAtual;
        conteiner.setPointerCapture(evento.pointerId);
        conteiner.onpointermove = eventoAtual => {
          const distanciaHorizontal = (eventoAtual.clientX - x) / escala,
            distanciaVertical = (eventoAtual.clientY - y) / escala;
          if (redimensionar) {
            registro.w = Math.round(Math.max(400, Math.min(575 - registro.x, inicio.w + distanciaHorizontal)));
            registro.h = Math.round(Math.max(alturaMinima(id), Math.min(815 - registro.y, inicio.h + distanciaVertical)));
          } else {
            registro.x = Math.round(Math.max(20, Math.min(575 - registro.w, inicio.x + distanciaHorizontal)));
            registro.y = Math.round(Math.max(20, Math.min(815 - registro.h, inicio.y + distanciaVertical)));
          }
          posicionar(conteiner, registro);
          medidas();
        };
        conteiner.onpointerup = () => {
          conteiner.onpointermove = null;
          const sobreposto = Object.entries(atual().areas).some(([chave, outra]) => chave !== id && registro.x < outra.x + outra.w && outra.x < registro.x + registro.w && registro.y < outra.y + outra.h && outra.y < registro.y + registro.h);
          if (sobreposto) {
            Object.assign(registro, inicio);
            posicionar(conteiner, registro);
            medidas();
            estado('Este espaço pertence a outro bloco. Escolha uma área livre na folha.');
            return;
          }
          marcar();
          agendar();
        };
        conteiner.onpointercancel = conteiner.onpointerup;
      };
      conteiner.onkeydown = evento => {
        if (!['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(evento.key)) return;
        evento.preventDefault();
        guardar();
        const inicio = {...registro};
        const valorDoRegistro = evento.shiftKey ? 10 : 1;
        registro.x = Math.max(20, Math.min(575 - registro.w, registro.x + (evento.key === 'ArrowRight' ? valorDoRegistro : evento.key === 'ArrowLeft' ? -valorDoRegistro : 0)));
        registro.y = Math.max(20, Math.min(815 - registro.h, registro.y + (evento.key === 'ArrowDown' ? valorDoRegistro : evento.key === 'ArrowUp' ? -valorDoRegistro : 0)));
        if (Object.entries(atual().areas).some(([chave, outra]) => chave !== id && registro.x < outra.x + outra.w && outra.x < registro.x + registro.w && registro.y < outra.y + outra.h && outra.y < registro.y + registro.h)) {
          Object.assign(registro, inicio);
          estado('Escolha uma área livre: os blocos de evidências não podem se sobrepor.');
          return;
        }
        porId('template-area').value = id;
        medidas();
        posicionar(conteiner, registro);
        marcar();
        agendar();
      };
    }
  }
  function posicionar(elementoLocal, registro) {
    elementoLocal.style.left = `${registro.x / 595 * 100}%`;
    elementoLocal.style.top = `${registro.y / (atual().design === 'assineai' ? 840 : 842) * 100}%`;
    elementoLocal.style.width = `${registro.w / 595 * 100}%`;
    elementoLocal.style.height = `${registro.h / (atual().design === 'assineai' ? 840 : 842) * 100}%`;
  }
  async function galeria() {
    const area = porId('modelos-galeria');
    area.replaceChildren();
    for (const item of catalogo) {
      const botao = document.createElement('button');
      botao.type = 'button';
      botao.className = 'registro-card';
      botao.dataset.design = item.design;
      const amostra = document.createElement('div');
      amostra.className = 'registro-thumb';
      amostra.style.background = item.cores.secundaria;
      const imagemAtual = document.createElement('img');
      imagemAtual.alt = `Folha de assinaturas: ${item.nome}`;
      amostra.append(imagemAtual);
      const nome = document.createElement('strong');
      nome.textContent = item.nome.split(' / ')[0];
      const descricaoAtual = document.createElement('p');
      descricaoAtual.textContent = item.descricao;
      botao.append(amostra, nome, descricaoAtual);
      area.append(botao);
      botao.onclick = () => {
        let modelo = biblioteca.modelos.find(valorAtual => valorAtual.id === item.id);
        if (!modelo) {
          if (biblioteca.modelos.length >= 24) return estado('Limite de 24 versões. Exclua uma versão antes de adicionar outra.');
          modelo = clonar(item);
          biblioteca.modelos.push(modelo);
        }
        selecionado = modelo.id;
        historico.length = 0;
        futuro.length = 0;
        preencher();
        estado('Modelo selecionado para edição. Defina como padrão e salve para usar no PDF.');
      };
      try {
        const registroAtual = await api('/previa', {
          modelo: item,
          empresa: 'Sua empresa',
          formato: 'png'
        });
        const endereco = URL.createObjectURL(await registroAtual.blob());
        enderecos.push(endereco);
        imagemAtual.src = endereco;
      } catch (evento) {
        imagemAtual.alt = evento.message;
      }
    }
    lista();
  }
  document.querySelectorAll('[data-modelo],[data-cor],[data-area]').forEach(elementoLocal => elementoLocal.oninput = () => {
    if (!biblioteca) return;
    guardar();
    if (elementoLocal.dataset.modelo) atual()[elementoLocal.dataset.modelo] = elementoLocal.type === 'number' ? Number(elementoLocal.value) : elementoLocal.value;else if (elementoLocal.dataset.cor) atual().cores[elementoLocal.dataset.cor] = elementoLocal.value;else atual().areas[porId('template-area').value][elementoLocal.dataset.area] = Number(elementoLocal.value);
    marcar();
    lista();
    alcas();
    agendar();
  });
  porId('template-selecionado').onchange = evento => {
    selecionado = evento.target.value;
    historico.length = 0;
    futuro.length = 0;
    preencher();
  };
  porId('template-area').onchange = () => {
    medidas();
    alcas();
  };
  porId('template-editar-posicoes').onchange = alcas;
  porId('perfil-nome').addEventListener('input', () => biblioteca && agendar());
  porId('template-ativar').onclick = () => {
    biblioteca.ativo = selecionado;
    lista();
    marcar();
  };
  porId('template-duplicar').onclick = () => {
    if (biblioteca.modelos.length >= 24) return estado('Limite de 24 versões.');
    const item = clonar(atual());
    item.id = crypto.randomUUID();
    item.nome = (item.nome.split(' / ')[0] + ' · Minha versão').slice(0, 120);
    biblioteca.modelos.push(item);
    selecionado = item.id;
    preencher();
    marcar();
  };
  porId('template-zero').onclick = () => {
    if (biblioteca.modelos.length >= 24) return estado('Limite de 24 versões.');
    const item = clonar(catalogo.find(modelo => modelo.id === 'atlas') || catalogo[0]);
    item.id = crypto.randomUUID();
    item.design = 'livre';
    item.nome = 'Minha composição';
    item.descricao = 'Identidade criada do zero';
    item.cores = {
      fundo: '#ffffff',
      papel: '#ffffff',
      tinta: '#243128',
      destaque: '#294332',
      secundaria: '#dce2dc'
    };
    biblioteca.modelos.push(item);
    selecionado = item.id;
    preencher();
    marcar();
  };
  porId('template-excluir').onclick = () => {
    if (selecionado === biblioteca.ativo) return estado('Defina outro modelo como padrão antes de excluir este.');
    biblioteca.modelos = biblioteca.modelos.filter(item => item.id !== selecionado);
    selecionado = biblioteca.ativo;
    preencher();
    marcar();
  };
  porId('template-desfazer').onclick = () => {
    if (!historico.length) return;
    futuro.push(clonar(atual()));
    biblioteca.modelos[biblioteca.modelos.indexOf(atual())] = historico.pop();
    preencher();
    marcar();
  };
  porId('template-refazer').onclick = () => {
    if (!futuro.length) return;
    historico.push(clonar(atual()));
    biblioteca.modelos[biblioteca.modelos.indexOf(atual())] = futuro.pop();
    preencher();
    marcar();
  };
  porId('template-logo').onchange = async evento => {
    const campo = evento.target.files[0];
    if (!campo) return;
    if (campo.size > 500000 || !['image/png', 'image/jpeg'].includes(campo.type)) {
      estado('Use um logotipo PNG ou JPEG de até 500 KB.');
      evento.target.value = '';
      return;
    }
    const id = selecionado;
    try {
      const valor = await new Promise((respostaAtual, rejeitar) => {
        const registroAtual = new FileReader();
        registroAtual.onload = () => respostaAtual(registroAtual.result);
        registroAtual.onerror = rejeitar;
        registroAtual.readAsDataURL(campo);
      });
      if (id !== selecionado) return;
      guardar();
      atual().logo = valor;
      marcar();
      agendar();
    } catch {
      estado('Não foi possível ler o logotipo.');
    }
    evento.target.value = '';
  };
  porId('template-fundo-imagem').onchange = async evento => {
    const campo = evento.target.files[0];
    if (!campo) return;
    if (campo.size > 500000 || !['image/png', 'image/jpeg'].includes(campo.type)) {
      estado('Use PNG ou JPEG de até 500 KB.');
      return;
    }
    const id = selecionado;
    try {
      const imagem = await new Promise((respostaAtual, rejeitar) => {
        const registroAtual = new FileReader();
        registroAtual.onload = () => respostaAtual(registroAtual.result);
        registroAtual.onerror = rejeitar;
        registroAtual.readAsDataURL(campo);
      });
      if (id !== selecionado) return;
      guardar();
      atual().fundoImagem = imagem;
      atual().opacidade ??= 0.08;
      marcar();
      agendar();
    } catch {
      estado('Não foi possível ler a imagem de fundo.');
    }
    evento.target.value = '';
  };
  porId('template-remover-fundo').onclick = () => {
    guardar();
    atual().fundoImagem = '';
    marcar();
    agendar();
  };
  porId('template-remover-logo').onclick = () => {
    guardar();
    atual().logo = '';
    marcar();
    agendar();
  };
  porId('template-atualizar').onclick = () => {
    clearTimeout(espera);
    previa().catch(evento => estado(evento.message));
  };
  function baixar(blob, nome) {
    const enderecoAtual = URL.createObjectURL(blob),
      linkDownload = document.createElement('a');
    linkDownload.href = enderecoAtual;
    linkDownload.download = nome;
    linkDownload.click();
    setTimeout(() => URL.revokeObjectURL(enderecoAtual), 1000);
  }
  porId('template-baixar').onclick = async () => {
    try {
      const elementoDaTela = await api('/previa', {
        modelo: atual(),
        empresa: porId('perfil-nome').value.trim() || 'Sua empresa',
        formato: 'pdf'
      });
      baixar(await elementoDaTela.blob(), 'amostra-pagina-assinaturas.pdf');
    } catch (evento) {
      estado(evento.message);
    }
  };
  porId('template-exportar').onclick = () => baixar(new Blob([JSON.stringify(biblioteca, null, 2)], {
    type: 'application/json'
  }), 'modelos-pagina-assinaturas.json');
  porId('template-importar').onchange = async evento => {
    try {
      const campo = evento.target.files[0];
      if (!campo) return;
      if (campo.size > 4000000) throw Error('A biblioteca deve ter até 4 MB.');
      const dados = JSON.parse(await campo.text());
      const registroAtual = await api('/validar', dados);
      biblioteca = await registroAtual.json();
      selecionado = biblioteca.ativo;
      preencher();
      marcar();
    } catch (evento) {
      estado(evento.message);
    }
    evento.target.value = '';
  };
  window.addEventListener('beforeunload', evento => {
    if (pendente) {
      evento.preventDefault();
      evento.returnValue = '';
    }
  });
  window.EditorTemplates = {
    async carregar(valor) {
      catalogo = await (await api('')).json();
      biblioteca = clonar(valor?.versao === 2 ? valor : {
        versao: 2,
        ativo: catalogo[0].id,
        modelos: catalogo
      });
      selecionado = biblioteca.ativo;
      pendente = false;
      porId('editor-templates').hidden = false;
      preencher();
      if (!porId('modelos-galeria').children.length) galeria().catch(evento => estado(evento.message));
    },
    dados: () => clonar(biblioteca),
    salvo() {
      pendente = false;
      estado('Modelo salvo. A página será usada nas próximas gerações do PDF.');
    }
  };
})();
