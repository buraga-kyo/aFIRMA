/* Estado de edição no navegador; processamento e modelos na API da FIRMA. */
(() => {
  const id = (nome) => document.getElementById(nome);
  if (!id('studio-campos')) return;
  const camada = id('studio-campos'),
    canvas = id('template-canvas');
  const tipos = {
    signature: 'Assinatura',
    initials: 'Rubrica',
    text: 'Texto',
    date: 'Data',
    checkbox: 'Seleção',
    number: 'Número',
    file: 'Anexo',
  };
  let pdf,
    paginas = [],
    pagina = 1,
    campos = [],
    selecionado,
    versaoPrevia = 0,
    url,
    ocupado = false;
  const passado = [],
    futuro = [];
  const status = (mensagem) => (id('studio-status').textContent = mensagem);
  let modeloId, modeloVersao, modoArea, copiado;
  let versaoCarga = 0;
  const LIMITE_HISTORICO = 20;

  // File é imutável e pode ser compartilhado; campos e páginas precisam de cópias
  // para que o próximo arraste não altere os estados já guardados no histórico.
  const capturarEstado = () => ({
    pdf,
    paginas: structuredClone(paginas),
    pagina,
    campos: structuredClone(campos),
  });
  const guardar = () => {
    passado.push(capturarEstado());
    if (passado.length > LIMITE_HISTORICO) passado.shift();
    futuro.length = 0;
  };
  const restaurar = async (estado) => {
    pdf = estado.pdf;
    paginas = estado.paginas;
    pagina = estado.pagina;
    campos = estado.campos;
    selecionar(null);
    if (pdf) await mostrar();
  };
  function historico(refazer = false) {
    const origem = refazer ? futuro : passado,
      destino = refazer ? passado : futuro;
    if (!origem.length) return;
    destino.push(capturarEstado());
    return restaurar(origem.pop());
  }
  const atual = () => campos.find((c) => c.id === selecionado);
  const limitar = (v, min, max) => Math.max(min, Math.min(max, v));
  async function pedir(caminho, corpo, metodo = 'POST') {
    const credencial = id('empresa-token').value.trim();
    const resposta = await fetch('/api/assineai/editor/' + caminho, {
      method: metodo,
      headers: { Authorization: `Bearer ${id('empresa-token').value.trim()}` },
      body: corpo,
    });
    // Uma resposta iniciada com outra credencial não pode atualizar esta sessão.
    if (credencial !== id('empresa-token').value.trim())
      throw Error('Acesso alterado. Reabra o documento na sessão atual.');
    if (!resposta.ok) {
      if (resposta.status === 429)
        throw Error('A bancada recebeu muitas operações. Aguarde um minuto e tente novamente.');
      const erro = await resposta.json().catch(() => ({}));
      throw Error(erro.detail || erro.mensagem || 'Não foi possível preparar o documento.');
    }
    return resposta;
  }
  const dadosPdf = () => {
    const dados = new FormData();
    dados.append('pdf', pdf);
    return dados;
  };
  function propriedades() {
    const campo = atual();
    id('studio-campo').hidden = !campo;
    if (!campo) return;
    id('studio-campo-nome').value = campo.name;
    id('studio-campo-papel').value = campo.role;
    id('studio-campo-obrigatorio').checked = campo.required;
  }
  function selecionar(chave) {
    selecionado = chave;
    desenhar();
    propriedades();
  }
  function posicionar(no, campo) {
    for (const [atributo, chave] of Object.entries({
      left: 'x',
      top: 'y',
      width: 'w',
      height: 'h',
    }))
      no.style[atributo] = `${campo[chave] * 100}%`;
  }
  function desenhar() {
    id('trator-documentos').classList.toggle('studio-documento-aberto', !!pdf);
    const rotulo = document.querySelector('.registro-toolbar>span');
    const identificacao = document.querySelector('.documentos-identificacao small');
    if (identificacao)
      identificacao.textContent = pdf
        ? 'PDF · ' + paginas.length + ' PÁGINA(S)'
        : 'A4 · PADRÃO DA FIRMA';
    if (rotulo)
      rotulo.textContent = pdf
        ? 'DOCUMENTO EM EDIÇÃO · ' + pdf.name
        : 'PRÉVIA DA FOLHA ADICIONADA AO PDF';
    camada.replaceChildren();
    camada.hidden = !pdf;
    id('template-alcas').hidden = !!pdf || !id('template-editar-posicoes').checked;
    for (const campo of campos.filter((c) => c.page === pagina)) {
      const no = document.createElement('div'),
        titulo = document.createElement('span'),
        alca = document.createElement('i');
      no.className = 'studio-campo' + (campo.id === selecionado ? ' selecionado' : '');
      no.dataset.tipo = campo.type;
      no.tabIndex = 0;
      no.setAttribute('role', 'button');
      no.setAttribute(
        'aria-label',
        `${campo.name}, ${campo.role}. Arraste ou use as setas para mover.`,
      );
      titulo.textContent = campo.type === 'checkbox' ? '☑' : campo.name;
      alca.setAttribute('aria-hidden', 'true');
      no.append(titulo, alca);
      posicionar(no, campo);
      camada.append(no);
      no.onfocus = () => {
        selecionado = campo.id;
        propriedades();
      };
      no.onpointerdown = (evento) => {
        if (modoArea || ocupado) return;
        evento.preventDefault();
        evento.stopPropagation();
        selecionado = campo.id;
        propriedades();
        camada
          .querySelectorAll('.studio-campo')
          .forEach((item) => item.classList.toggle('selecionado', item === no));
        no.focus();
        guardar();
        const inicio = { ...campo },
          retangulo = canvas.getBoundingClientRect(),
          x = evento.clientX,
          y = evento.clientY;
        const redimensionar = evento.target === alca;
        no.setPointerCapture(evento.pointerId);
        no.onpointermove = (movimento) => {
          const dx = (movimento.clientX - x) / retangulo.width,
            dy = (movimento.clientY - y) / retangulo.height;
          if (redimensionar) {
            campo.w = limitar(
              inicio.w + dx,
              ['signature', 'initials'].includes(campo.type) ? 0.05 : 0.025,
              1 - campo.x,
            );
            campo.h = limitar(
              inicio.h + dy,
              ['signature', 'initials'].includes(campo.type) ? 0.03 : 0.015,
              1 - campo.y,
            );
          } else {
            campo.x = limitar(inicio.x + dx, 0, 1 - campo.w);
            campo.y = limitar(inicio.y + dy, 0, 1 - campo.h);
          }
          posicionar(no, campo);
        };
        no.onpointerup = () => {
          no.onpointermove = null;
        };
        no.onpointercancel = () => {
          Object.assign(campo, inicio);
          posicionar(no, campo);
          no.onpointermove = null;
        };
      };
      no.onkeydown = (evento) => {
        if (evento.key === 'Delete' || evento.key === 'Backspace') {
          evento.preventDefault();
          excluir();
          return;
        }
        if (!evento.key.startsWith('Arrow')) return;
        evento.preventDefault();
        guardar();
        const passo = evento.shiftKey ? 10 : 1,
          tamanho = paginas[pagina - 1];
        campo.x = limitar(
          campo.x +
            (evento.key === 'ArrowRight' ? passo : evento.key === 'ArrowLeft' ? -passo : 0) /
              tamanho.width,
          0,
          1 - campo.w,
        );
        campo.y = limitar(
          campo.y +
            (evento.key === 'ArrowDown' ? passo : evento.key === 'ArrowUp' ? -passo : 0) /
              tamanho.height,
          0,
          1 - campo.h,
        );
        posicionar(no, campo);
      };
    }
  }
  async function mostrar() {
    const minha = ++versaoPrevia,
      arquivo = pdf,
      numero = pagina;
    const form = new FormData();
    form.append('pdf', arquivo);
    form.append('pagina', numero);
    status('Carregando página…');
    try {
      const resposta = await pedir('previa', form),
        blob = await resposta.blob();
      // A renderização pode terminar fora de ordem ao navegar ou trocar de PDF.
      // Só a requisição mais recente pode substituir a imagem da página.
      if (minha !== versaoPrevia || arquivo !== pdf) return;
      if (url) URL.revokeObjectURL(url);
      url = URL.createObjectURL(blob);
      id('template-previa').src = url;
      const tamanho = paginas[numero - 1];
      canvas.style.aspectRatio = `${tamanho.width}/${tamanho.height}`;
      window.dispatchEvent(new Event('firma:editor-pagina'));
      id('studio-pagina').textContent = `Página ${numero} de ${paginas.length}`;
      id('studio-pagina-anterior').disabled = numero === 1;
      id('studio-pagina-proxima').disabled = numero === paginas.length;
      desenhar();
      status('Arraste um campo para o documento. Ctrl/Cmd+Z desfaz a edição dos campos.');
    } catch (erro) {
      if (minha === versaoPrevia) status(erro.message);
    }
  }
  function adicionar(
    tipo,
    x = 0.1,
    y = Math.min(0.85, 0.1 + campos.filter((c) => c.page === pagina).length * 0.09),
  ) {
    if (ocupado) return;
    if (!pdf) return status('Escolha primeiro o PDF que será preenchido no DocuSeal.');
    guardar();
    if (campos.length >= 200) return status('Limite de 200 campos.');
    const campo = {
      id: crypto.randomUUID(),
      name: `${tipos[tipo]} ${campos.length + 1}`,
      type: tipo,
      role: 'Signatário',
      required: true,
      page: pagina,
      x: limitar(x, 0, 0.75),
      y: limitar(y, 0, 0.93),
      w: tipo === 'checkbox' ? 0.035 : 0.25,
      h: tipo === 'signature' ? 0.065 : 0.035,
    };
    campos.push(campo);
    selecionar(campo.id);
  }
  document.querySelectorAll('[data-campo-tipo]').forEach((botao) => {
    botao.onclick = () => adicionar(botao.dataset.campoTipo);
    botao.ondragstart = (evento) =>
      evento.dataTransfer.setData('application/x-firma-campo', botao.dataset.campoTipo);
  });
  canvas.addEventListener('dragover', (evento) => {
    if (pdf) {
      evento.preventDefault();
      evento.dataTransfer.dropEffect = 'copy';
    }
  });
  canvas.addEventListener('drop', (evento) => {
    const tipo = evento.dataTransfer.getData('application/x-firma-campo');
    if (!tipos[tipo]) return;
    evento.preventDefault();
    const r = canvas.getBoundingClientRect();
    adicionar(tipo, (evento.clientX - r.left) / r.width, (evento.clientY - r.top) / r.height);
  });
  function excluir() {
    if (!atual()) return;
    guardar();
    campos = campos.filter((c) => c.id !== selecionado);
    selecionar(null);
  }
  id('studio-campo-excluir').onclick = excluir;
  for (const [nome, chave] of [
    ['nome', 'name'],
    ['papel', 'role'],
    ['obrigatorio', 'required'],
  ]) {
    id('studio-campo-' + nome).onchange = (evento) => {
      if (!atual()) return;
      guardar();
      atual()[chave] =
        evento.target.type === 'checkbox' ? evento.target.checked : evento.target.value.trim();
      desenhar();
    };
  }
  document.addEventListener('keydown', (evento) => {
    if (
      !pdf ||
      /INPUT|TEXTAREA|SELECT/.test(evento.target.tagName) ||
      !(evento.ctrlKey || evento.metaKey)
    )
      return;
    const tecla = evento.key.toLowerCase();
    if (tecla === 'z') {
      evento.preventDefault();
      historico(evento.shiftKey);
    }
    if (tecla === 'c' && atual()) {
      evento.preventDefault();
      copiado = structuredClone(atual());
      status('Campo copiado.');
    }
    if (tecla === 'v' && copiado) {
      evento.preventDefault();
      colar();
    }
    if (tecla === 'd' && atual()) {
      evento.preventDefault();
      copiado = structuredClone(atual());
      colar();
    }
  });
  id('studio-pdf').onchange = async (evento) => {
    const arquivo = evento.target.files[0];
    if (!arquivo) return;
    ++versaoCarga;
    const minha = ++versaoPrevia;
    if (arquivo.size > 20000000) return status('Escolha um PDF de até 20 MB.');
    const form = new FormData();
    form.append('pdf', arquivo);
    try {
      const dados = await (await pedir('paginas', form)).json();
      if (minha !== versaoPrevia) return;
      modeloId = null;
      modeloVersao = null;
      id('studio-modelo-nome').value = arquivo.name.replace(/\.pdf$/i, '');
      pdf = arquivo;
      paginas = dados.paginas;
      pagina = 1;
      campos = [];
      passado.length = futuro.length = 0;
      selecionar(null);
      id('studio-docuseal').disabled = false;
      await mostrar();
    } catch (erro) {
      status(erro.message);
    }
  };
  id('studio-pagina-anterior').onclick = () => {
    if (pdf && pagina > 1) {
      pagina--;
      selecionar(null);
      mostrar();
    }
  };
  id('studio-pagina-proxima').onclick = () => {
    if (pdf && pagina < paginas.length) {
      pagina++;
      selecionar(null);
      mostrar();
    }
  };
  id('studio-evidencias').onclick = () => {
    ++versaoPrevia;
    pdf = null;
    campos = [];
    selecionar(null);
    canvas.style.aspectRatio = '';
    id('studio-docuseal').disabled = true;
    id('studio-pdf').value = '';
    id('studio-pagina').textContent = 'Página 1';
    modoArea = null;
    camada.classList.remove('desenhando');
    passado.length = futuro.length = 0;
    id('template-atualizar').click();
    status('Editando a folha de evidências da FIRMA.');
  };
  id('studio-exportar').onclick = async () => {
    if (!pdf) return status('Escolha um PDF e adicione os campos primeiro.');
    const resumo = await crypto.subtle.digest('SHA-256', await pdf.arrayBuffer());
    const sha256 = Array.from(new Uint8Array(resumo), (b) => b.toString(16).padStart(2, '0')).join(
      '',
    );
    const endereco = URL.createObjectURL(
      new Blob([JSON.stringify({ versao: 1, sha256, campos }, null, 2)], {
        type: 'application/json',
      }),
    );
    const link = document.createElement('a');
    link.href = endereco;
    link.download = 'campos-docuseal.json';
    link.click();
    setTimeout(() => URL.revokeObjectURL(endereco), 1000);
  };
  id('studio-importar').onchange = async (evento) => {
    try {
      const arquivo = evento.target.files[0];
      if (!arquivo) return;
      if (!pdf) throw Error('Escolha o PDF original antes de restaurar os campos.');
      if (arquivo.size > 200000) throw Error('Mapa de campos muito grande.');
      const documento = pdf,
        dados = JSON.parse(await arquivo.text());
      const resumo = await crypto.subtle.digest('SHA-256', await documento.arrayBuffer());
      const sha256 = Array.from(new Uint8Array(resumo), (b) =>
        b.toString(16).padStart(2, '0'),
      ).join('');
      if (pdf !== documento) return;
      if (dados.versao !== 1 || dados.sha256 !== sha256)
        throw Error('Este mapa pertence a outro PDF. Selecione o documento original.');
      if (
        !Array.isArray(dados.campos) ||
        dados.campos.length > 200 ||
        dados.campos.some(
          (c) =>
            !tipos[c.type] ||
            typeof c.name !== 'string' ||
            typeof c.role !== 'string' ||
            typeof c.required !== 'boolean' ||
            !Number.isInteger(c.page) ||
            c.page < 1 ||
            c.page > paginas.length ||
            !['x', 'y', 'w', 'h'].every((k) => Number.isFinite(c[k])) ||
            c.x < 0 ||
            c.y < 0 ||
            c.w <= 0 ||
            c.h <= 0 ||
            c.x + c.w > 1.00000001 ||
            c.y + c.h > 1.00000001,
        )
      )
        throw Error('Mapa de campos inválido.');
      guardar();
      campos = dados.campos.map((c) => ({ ...c, id: crypto.randomUUID() }));
      selecionar(null);
      status('Campos restaurados no PDF original.');
    } catch (erro) {
      status(erro.message);
    } finally {
      evento.target.value = '';
    }
  };
  id('studio-docuseal').onclick = async () => {
    if (!pdf || ocupado) return;
    ocupado = true;
    id('studio-docuseal').disabled = true;
    const form = new FormData();
    form.append('pdf', pdf);
    form.append('campos', JSON.stringify(campos));
    status('Criando modelo no DocuSeal…');
    try {
      const resposta = await fetch('/api/assineai/docuseal/templates', {
        method: 'POST',
        headers: { Authorization: `Bearer ${id('empresa-token').value.trim()}` },
        body: form,
      });
      if (!resposta.ok) {
        const erro = await resposta.json();
        throw Error(erro.detail || erro.mensagem || 'Conector externo indisponível.');
      }
      const resultado = await resposta.json();
      status(
        `Modelo ${resultado.id} criado no DocuSeal. Os convites serão preparados no DocuSeal.`,
      );
    } catch (erro) {
      status(erro.message);
    } finally {
      ocupado = false;
      id('studio-docuseal').disabled = !pdf;
    }
  };
  id('empresa-token').addEventListener('input', () => {
    ++versaoPrevia;
    ++versaoCarga;
    modeloId = null;
    modeloVersao = null;
    pdf = null;
    campos = [];
    passado.length = futuro.length = 0;
    desenhar();
    propriedades();
    id('studio-pdf').value = '';
    id('studio-docuseal').disabled = true;
  });
  function colar() {
    if (!pdf || !copiado || campos.length >= 200) return;
    guardar();
    const campo = {
      ...structuredClone(copiado),
      id: crypto.randomUUID(),
      page: pagina,
      name: (copiado.name.slice(0, 65) + ' · cópia ' + (campos.length + 1)).slice(0, 80),
    };
    campo.x = limitar(campo.x + 0.02, 0, 1 - campo.w);
    campo.y = limitar(campo.y + 0.02, 0, 1 - campo.h);
    campos.push(campo);
    selecionar(campo.id);
  }
  function baixar(blob, nome) {
    const endereco = URL.createObjectURL(blob),
      link = document.createElement('a');
    link.href = endereco;
    link.download = nome;
    link.click();
    setTimeout(() => URL.revokeObjectURL(endereco), 1000);
  }
  // Serializa as operações que alteram PDF ou modelo. O finally libera a bancada
  // também quando o servidor recusa o pedido ou a conexão falha.
  async function acao(tarefa) {
    if (ocupado) return;
    ocupado = true;
    id('trator-documentos').classList.add('studio-ocupado');
    id('trator-documentos').setAttribute('aria-busy', 'true');
    try {
      await tarefa();
    } catch (erro) {
      status(erro.message);
    } finally {
      ocupado = false;
      id('trator-documentos').classList.remove('studio-ocupado');
      id('trator-documentos').removeAttribute('aria-busy');
    }
  }
  function ligar(nome, tarefa) {
    id(nome).onclick = () => acao(tarefa);
  }
  const exigir = () => {
    if (!pdf) throw Error('Abra um PDF para editar.');
  };
  ligar('studio-desfazer', () => historico());
  ligar('studio-refazer', () => historico(true));
  ligar('studio-duplicar', () => {
    if (atual()) {
      copiado = structuredClone(atual());
      colar();
    }
  });
  ligar('studio-alinhar', () => {
    if (atual()) {
      guardar();
      atual().x = (1 - atual().w) / 2;
      desenhar();
    }
  });
  ligar('studio-baixar-pdf', () => {
    exigir();
    baixar(pdf, pdf.name);
  });
  ligar('studio-detectar', async () => {
    exigir();
    const arquivo = pdf;
    const dados = await (await pedir('detectar', dadosPdf())).json();
    if (arquivo !== pdf) return;
    const novos = dados.campos.filter(
      (c) => !campos.some((atual) => atual.page === c.page && atual.name === c.name),
    );
    if (campos.length + novos.length > 200)
      throw Error('A detecção ultrapassa o limite de 200 campos.');
    guardar();
    campos.push(...novos);
    selecionar(null);
    status(`${novos.length} campos encontrados e adicionados.`);
  });
  function plano() {
    return paginas.map((_, i) => ({ origem: i + 1, rotacao: 0 }));
  }
  function sobrepoe(campo, area) {
    return (
      campo.x < area.x + area.w &&
      area.x < campo.x + campo.w &&
      campo.y < area.y + area.h &&
      area.y < campo.y + campo.h
    );
  }

  function transformarCampo(original, destino, numeroPagina, paginaRepetida) {
    const campo = { ...original, id: crypto.randomUUID(), page: numeroPagina };
    if (destino.ocultacoes?.some((area) => sobrepoe(campo, area))) return null;

    // As posições usam frações da página exibida, com origem no topo esquerdo.
    // Primeiro aplicamos o recorte nesse espaço; depois giramos os campos junto do PDF.
    if (destino.recorte) {
      const recorte = destino.recorte;
      const foraDoRecorte =
        campo.x < recorte.x ||
        campo.y < recorte.y ||
        campo.x + campo.w > recorte.x + recorte.w ||
        campo.y + campo.h > recorte.y + recorte.h;
      if (foraDoRecorte) return null;
      campo.x = (campo.x - recorte.x) / recorte.w;
      campo.y = (campo.y - recorte.y) / recorte.h;
      campo.w /= recorte.w;
      campo.h /= recorte.h;
    }

    // Um quarto de volta troca os eixos e desconta a altura da nova posição X.
    for (let volta = 0; volta < destino.rotacao / 90; volta++) {
      const { x, w } = campo;
      campo.x = 1 - campo.y - campo.h;
      campo.y = x;
      campo.w = campo.h;
      campo.h = w;
    }
    if (paginaRepetida) {
      campo.name = (campo.name.slice(0, 64) + ' · página ' + numeroPagina).slice(0, 80);
    }
    return campo;
  }

  function remapearCampos(originais, planoPaginas) {
    const novos = [];
    planoPaginas.forEach((destino, indice) => {
      const paginaRepetida =
        planoPaginas.filter((item) => item.origem === destino.origem).length > 1;
      for (const original of originais.filter((campo) => campo.page === destino.origem)) {
        const campo = transformarCampo(original, destino, indice + 1, paginaRepetida);
        if (campo) novos.push(campo);
      }
    });
    return novos;
  }

  async function editar(planoPaginas, novaPagina = pagina) {
    exigir();
    const arquivo = pdf,
      form = dadosPdf();
    form.append('operacao', JSON.stringify({ paginas: planoPaginas }));
    status('Processando páginas na FIRMA…');
    const blob = await (await pedir('editar', form)).blob();
    if (pdf !== arquivo) return;
    const novo = new File([blob], arquivo.name, { type: 'application/pdf' }),
      info = new FormData();
    info.append('pdf', novo);
    const metadados = await (await pedir('paginas', info)).json();
    if (pdf !== arquivo) return;
    const novos = remapearCampos(campos, planoPaginas);
    if (novos.length > 200) throw Error('A operação excede o limite de 200 campos.');
    // O estado só muda depois de receber o PDF e suas medidas. Uma falha anterior
    // mantém o documento atual e não cria uma entrada de histórico incompleta.
    guardar();
    pdf = novo;
    paginas = metadados.paginas;
    campos = novos;
    pagina = limitar(novaPagina, 1, paginas.length);
    selecionar(null);
    await mostrar();
    status(
      'Páginas atualizadas. Campos fora do recorte ou sobre áreas ocultadas foram removidos. Desfazer restaura a edição.',
    );
  }
  ligar('studio-girar', () => {
    const p = plano();
    exigir();
    p[pagina - 1].rotacao = 90;
    return editar(p);
  });
  ligar('studio-mover-antes', () => {
    exigir();
    if (pagina === 1) return;
    const p = plano();
    [p[pagina - 1], p[pagina - 2]] = [p[pagina - 2], p[pagina - 1]];
    return editar(p, pagina - 1);
  });
  ligar('studio-mover-depois', () => {
    exigir();
    if (pagina === paginas.length) return;
    const p = plano();
    [p[pagina - 1], p[pagina]] = [p[pagina], p[pagina - 1]];
    return editar(p, pagina + 1);
  });
  ligar('studio-duplicar-pagina', () => {
    exigir();
    const p = plano();
    p.splice(pagina, 0, { origem: pagina, rotacao: 0 });
    return editar(p, pagina + 1);
  });
  ligar('studio-excluir-pagina', () => {
    exigir();
    if (paginas.length === 1) throw Error('Preserve pelo menos uma página.');
    const p = plano();
    p.splice(pagina - 1, 1);
    return editar(p);
  });
  for (const [botao, modo] of [
    ['studio-recortar', 'recorte'],
    ['studio-ocultar', 'ocultacoes'],
  ])
    ligar(botao, () => {
      exigir();
      modoArea = modo;
      camada.classList.add('desenhando');
      status(
        modo === 'recorte'
          ? 'Desenhe a área que deseja manter. Escape cancela.'
          : 'Desenhe a área que deseja remover definitivamente do PDF. Escape cancela.',
      );
      id(botao).closest('details').open = false;
    });
  camada.addEventListener(
    'pointerdown',
    (evento) => {
      if (!modoArea || !pdf || ocupado) return;
      evento.preventDefault();
      evento.stopPropagation();
      const modo = modoArea,
        r = canvas.getBoundingClientRect(),
        inicio = {
          x: limitar((evento.clientX - r.left) / r.width, 0, 1),
          y: limitar((evento.clientY - r.top) / r.height, 0, 1),
        },
        no = document.createElement('div');
      no.className = 'studio-selecao';
      camada.append(no);
      let caixa;
      const movimento = (e) => {
        const x = limitar((e.clientX - r.left) / r.width, 0, 1),
          y = limitar((e.clientY - r.top) / r.height, 0, 1);
        caixa = {
          x: Math.min(x, inicio.x),
          y: Math.min(y, inicio.y),
          w: Math.abs(x - inicio.x),
          h: Math.abs(y - inicio.y),
        };
        posicionar(no, caixa);
      };
      camada.setPointerCapture(evento.pointerId);
      camada.addEventListener('pointermove', movimento);
      const terminar = (cancelar) => {
        camada.removeEventListener('pointermove', movimento);
        camada.onpointerup = camada.onpointercancel = null;
        no.remove();
        camada.classList.remove('desenhando');
        modoArea = null;
        if (cancelar || !caixa || caixa.w < 0.01 || caixa.h < 0.01) return;
        const p = plano();
        p[pagina - 1][modo] = modo === 'recorte' ? caixa : [caixa];
        acao(() => editar(p));
      };
      camada.onpointerup = () => terminar(false);
      camada.onpointercancel = () => terminar(true);
    },
    true,
  );
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      modoArea = null;
      camada.classList.remove('desenhando');
      camada.onpointercancel?.();
    }
  });
  id('studio-adicionar-pdf').onchange = (evento) =>
    acao(async () => {
      exigir();
      const arquivos = [...evento.target.files];
      if (!arquivos.length) return;
      const anterior = pdf,
        form = new FormData();
      form.append('pdfs', pdf);
      arquivos.forEach((a) => form.append('pdfs', a));
      const blob = await (await pedir('combinar', form)).blob();
      if (pdf !== anterior) return;
      const novo = new File([blob], pdf.name, { type: 'application/pdf' }),
        dados = new FormData();
      dados.append('pdf', novo);
      const info = await (await pedir('paginas', dados)).json();
      if (pdf !== anterior) return;
      guardar();
      pdf = novo;
      paginas = info.paginas;
      await mostrar();
      evento.target.value = '';
      status('PDFs adicionados ao final do documento.');
    });
  async function listar() {
    const itens = await (await pedir('modelos', undefined, 'GET')).json(),
      seletor = id('studio-modelos');
    seletor.replaceChildren(new Option('Selecione um modelo', ''));
    itens.forEach((m) => seletor.add(new Option(`${m.nome} · v${m.versao}`, m.id)));
    seletor.value = modeloId || '';
  }
  async function versoes() {
    if (!modeloId) return;
    const itens = await (await pedir(`modelos/${modeloId}/versoes`, undefined, 'GET')).json(),
      seletor = id('studio-versoes');
    seletor.replaceChildren();
    itens.forEach((v) => seletor.add(new Option(`v${v.versao} · ${v.nome}`, v.versao)));
    seletor.value = modeloVersao;
  }
  async function salvar(copia = false) {
    exigir();
    const form = dadosPdf();
    form.append('nome', id('studio-modelo-nome').value);
    form.append('campos', JSON.stringify(campos));
    if (modeloId && !copia) {
      form.append('id', modeloId);
      form.append('versao', modeloVersao);
    }
    const arquivo = pdf,
      resultado = await (await pedir('modelos', form)).json();
    if (pdf !== arquivo) return;
    modeloId = resultado.id;
    modeloVersao = resultado.versao;
    await listar();
    await versoes();
    status(`Modelo salvo na FIRMA. Versão ${modeloVersao}.`);
  }
  async function abrir(chave, versao) {
    // Cada abertura invalida as anteriores, inclusive quando os metadados chegam antes do PDF.
    const parametros = versao ? '?versao=' + encodeURIComponent(versao) : '',
      minha = ++versaoCarga;
    const dados = await (await pedir('modelos/' + chave + parametros, undefined, 'GET')).json();
    const blob = await (
      await pedir('modelos/' + chave + '/pdf' + parametros, undefined, 'GET')
    ).blob();
    if (minha !== versaoCarga) return;
    const novo = new File([blob], dados.nome + '.pdf', { type: 'application/pdf' }),
      form = new FormData();
    form.append('pdf', novo);
    const info = await (await pedir('paginas', form)).json();
    if (minha !== versaoCarga) return;
    pdf = novo;
    campos = dados.campos;
    paginas = info.paginas;
    pagina = 1;
    modeloId = chave;
    modeloVersao = dados.versao;
    passado.length = futuro.length = 0;
    id('studio-modelo-nome').value = dados.nome;
    id('studio-docuseal').disabled = false;
    selecionar(null);
    await versoes();
    await mostrar();
    status(`Modelo aberto. Versão ${modeloVersao}.`);
  }
  ligar('studio-salvar', () => salvar());
  ligar('studio-salvar-copia', () => salvar(true));
  ligar('studio-listar', listar);
  id('studio-modelos').onchange = (evento) => {
    if (evento.target.value) acao(() => abrir(evento.target.value));
  };
  ligar('studio-restaurar-versao', () => {
    if (modeloId && id('studio-versoes').value) return abrir(modeloId, id('studio-versoes').value);
  });
  ligar('studio-preparar-convites', () => {
    exigir();
    if (!campos.length) throw Error('Adicione os campos dos participantes.');
    id('studio-participantes').hidden = false;
    const lista = id('studio-pessoas');
    lista.replaceChildren();
    for (const papel of new Set(campos.map((c) => c.role))) {
      const grupo = document.createElement('div');
      grupo.dataset.papel = papel;
      const titulo = document.createElement('strong');
      titulo.textContent = papel;
      grupo.append(titulo);
      for (const [tipo, nome] of [
        ['text', 'Nome'],
        ['email', 'E-mail'],
      ]) {
        const label = document.createElement('label'),
          input = document.createElement('input');
        label.textContent = nome;
        input.type = tipo;
        input.dataset.pessoa = tipo;
        input.maxLength = tipo === 'email' ? 254 : 120;
        label.append(input);
        grupo.append(label);
      }
      lista.append(grupo);
    }
  });
  ligar('studio-criar-convites', async () => {
    exigir();
    const form = dadosPdf();
    form.append('campos', JSON.stringify(campos));
    form.append('nome', id('studio-modelo-nome').value || pdf.name);
    const pessoas = [...id('studio-pessoas').children].map((grupo) => ({
      papel: grupo.dataset.papel,
      nome: grupo.querySelector('[data-pessoa=text]').value,
      email: grupo.querySelector('[data-pessoa=email]').value,
    }));
    form.append('participantes', JSON.stringify(pessoas));
    const resultado = await (await pedir('convites', form)).json();
    const lista = id('studio-convites');
    lista.replaceChildren();
    for (const convite of resultado.convites) {
      const link = document.createElement('a');
      link.textContent = convite.nome + ' · Abrir convite';
      link.href = '/convite/' + encodeURIComponent(convite.token);
      link.target = '_blank';
      link.rel = 'noopener';
      lista.append(link);
    }
    await listarEnvelopes();
    id('studio-envelopes').value = resultado.identificador;
    await andamento();
    status('Convites criados na FIRMA. Compartilhe os links com os participantes.');
  });

  async function listarEnvelopes() {
    const dados = await (await pedir('envelopes', undefined, 'GET')).json(),
      seletor = id('studio-envelopes'),
      atual = seletor.value;
    seletor.replaceChildren(new Option('Selecione um documento', ''));
    dados.forEach((e) =>
      seletor.add(new Option(e.nomeDocumento + ' · ' + (e.status || 'Aguardando'), e.id)),
    );
    seletor.value = atual;
  }
  async function andamento() {
    const chave = id('studio-envelopes').value;
    if (!chave) throw Error('Selecione o documento para acompanhar.');
    const dados = await (await pedir('envelopes/' + chave, undefined, 'GET')).json(),
      painel = id('studio-andamento');
    painel.replaceChildren();
    for (const pessoa of dados.signatarios) {
      const linha = document.createElement('p');
      linha.textContent = pessoa.nome + ': ' + (pessoa.assinadoEm ? 'Assinado' : 'Aguardando');
      painel.append(linha);
      for (const anexo of pessoa.anexos || []) {
        const botao = document.createElement('button');
        botao.type = 'button';
        botao.textContent = 'Baixar anexo · ' + anexo.nome;
        botao.onclick = () =>
          acao(async () =>
            baixar(
              await (
                await pedir(`envelopes/${chave}/anexos/${anexo.campo}`, undefined, 'GET')
              ).blob(),
              anexo.nome,
            ),
          );
        painel.append(botao);
      }
      for (const evento of pessoa.historicos || []) {
        const item = document.createElement('small');
        item.textContent = evento.acao + ' · ' + evento.criadoEm;
        painel.append(item, document.createElement('br'));
      }
    }
    id('studio-acompanhamento').open = true;
  }
  id('studio-envelopes').onchange = () => acao(andamento);
  ligar('studio-atualizar-envelope', async () => {
    await listarEnvelopes();
    await andamento();
  });
  ligar('studio-baixar-envelope', async () => {
    const chave = id('studio-envelopes').value;
    if (!chave) throw Error('Selecione um documento.');
    baixar(
      await (await pedir('envelopes/' + chave + '/pdf', undefined, 'GET')).blob(),
      'documento-preenchido.pdf',
    );
  });
  ligar('studio-certificar', async () => {
    const chave = id('studio-envelopes').value,
      arquivo = id('studio-certificado').files[0],
      senha = id('studio-certificado-senha').value;
    if (!chave || !arquivo || !senha)
      throw Error('Selecione o documento, o certificado e informe a senha.');
    const form = new FormData();
    form.append('certificado', arquivo);
    form.append('senha', senha);
    try {
      baixar(
        await (await pedir('envelopes/' + chave + '/finalizar', form)).blob(),
        'documento-assinado.pdf',
      );
      await andamento();
      status('Documento finalizado com certificado digital.');
    } finally {
      id('studio-certificado-senha').value = '';
    }
  });
  window.addEventListener('firma:editor-carregado', () =>
    acao(async () => {
      await listar();
      await listarEnvelopes();
    }),
  );
  window.EditorDocumentosFirma = window.EditorDocuseal = { documentoAberto: () => !!pdf };
})();
