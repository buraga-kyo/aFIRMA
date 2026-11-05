/** Tabela reutilizável aFIRMA. Contrato público: docs/tabela.md. */
(() => {
  let sequencia = 0;
  const montadas = new WeakMap();
  const padroes = Object.freeze({
    selecao: false,
    acoes: false,
    paginacao: true,
    selecionarColunas: true,
    filtrosHeader: true,
    ordenacao: true,
    redimensionamento: true,
    reordenacao: false,
    filtrosSalvos: false,
    buscaIA: false,
    edicao: false,
    detalhes: false,
    persistencia: false
  });
  const criar = (tag, classe, texto) => {
    const no = document.createElement(tag);
    if (classe) no.className = classe;
    if (texto !== undefined) no.textContent = String(texto ?? '');
    return no;
  };
  const copiar = valor => structuredClone(valor);
  class FirmaTabela {
    constructor(configuracao = {}) {
      this.config = configuracao;
      this.raiz = typeof configuracao.elemento === 'string' ? document.querySelector(configuracao.elemento) : configuracao.elemento;
      // Compatibilidade com o HTML antigo; novas instâncias usam elemento.
      if (!this.raiz && configuracao.containerId) this.raiz = document.getElementById(`${configuracao.containerId}-tabela`)?.closest('.af-tabela, main');
      if (!this.raiz) throw new Error('FirmaTabela: informe um elemento de destino existente.');
      if (montadas.has(this.raiz)) throw new Error('FirmaTabela: o destino já possui uma instância. Use destruir() antes de remontar.');
      this.id = configuracao.containerId || `af-tabela-${++sequencia}`;
      this.colunas = (configuracao.colunas || []).map(itemAtual => ({
        ...itemAtual,
        nome: itemAtual.nome ?? itemAtual.header ?? itemAtual.id
      }));
      const ids = this.colunas.map(itemAtual => itemAtual.id);
      if (!ids.length || new Set(ids).size !== ids.length || ids.some(id => !id || ['selecao', 'acoes'].includes(id))) {
        throw new Error('FirmaTabela: defina colunas com IDs únicos; selecao e acoes são reservados.');
      }
      if (document.getElementById(`${this.id}-tabela`) && configuracao.elemento) throw new Error(`FirmaTabela: prefixo ${this.id} já está em uso.`);
      this.recursos = Object.fromEntries(Object.keys(padroes).map(chave => [chave, configuracao.recursos === false ? false : configuracao.recursos?.[chave] ?? padroes[chave]]));
      if (configuracao.recursos !== false && configuracao.recursos?.acoes === undefined) this.recursos.acoes = Boolean(configuracao.renderAcoes);
      if (this.recursos.edicao && typeof configuracao.salvar !== 'function') throw new Error('FirmaTabela: edição requer o callback salvar(item, alteracoes, contexto).');
      if (configuracao.modo === 'servidor' && this.recursos.buscaIA) throw new Error('FirmaTabela: buscaIA atualmente suporta apenas o modo local.');
      this.textos = {
        colunas: '▤ COLUNAS ▾',
        acoes: 'AÇÕES',
        vazio: 'Nenhum registro encontrado.',
        semResultados: 'Nenhum resultado para os filtros atuais.',
        carregando: 'Carregando dados...',
        busca: 'Descreva sua busca...',
        pagina: ({
          pagina,
          totalPaginas,
          tamanho
        }) => `Página ${pagina} de ${totalPaginas} · ${tamanho} linhas ▾`,
        ...configuracao.textos
      };
      this.eventos = new AbortController();
      this.destruida = false;
      this.versao = 0;
      this.storageId = configuracao.storageId || this.id;
      this.estado = {
        todos: [],
        visiveis: this.colunas.filter(itemAtual => itemAtual.visivel !== false || itemAtual.ocultavel === false).map(itemAtual => itemAtual.id),
        filtros: {},
        ordem: null,
        pagina: 1,
        tamanho: this.tamanhoValido(configuracao.tamanhoPagina ?? 10),
        larguras: Object.fromEntries(this.colunas.map(itemAtual => [itemAtual.id, this.limitarLargura(itemAtual.id, itemAtual.largura ?? 200)])),
        selecionados: new Set(),
        salvos: [],
        busca: [],
        plano: null,
        total: 0,
        carregando: false
      };
      this.estado.larguras.selecao = 34;
      this.estado.larguras.acoes = configuracao.larguraAcoes ?? 96;
      this.restaurar();
      this.raiz.classList.add('af-tabela');
      this.raiz.dataset.tabelaInstancia = this.id;
      if (configuracao.elemento) this.montar();
      this.no = sufixo => this.raiz.querySelector(`[id="${this.id}-${sufixo}"]`);
      for (const sufixo of ['tabela', 'colunas', 'cabecalho', 'corpo']) {
        if (!this.no(sufixo)) throw new Error(`FirmaTabela: elemento ${this.id}-${sufixo} ausente.`);
      }
      montadas.set(this.raiz, this);
      this.vincular();
      if (Array.isArray(configuracao.dados)) this.definirDados(configuracao.dados);else if (configuracao.autoInit !== false) this.render();
    }
    tamanhoValido(valor) {
      if (!Number.isInteger(valor) || valor < 1) throw new Error('FirmaTabela: tamanho da página deve ser inteiro positivo.');
      return valor;
    }
    coluna(id) {
      return this.colunas.find(itemAtual => itemAtual.id === id);
    }
    permitido(recurso, id) {
      return this.recursos[recurso] && (!id || this.coluna(id) && this.coluna(id)[recurso] !== false);
    }
    chave(item) {
      return this.config.chaveLinha ? this.config.chaveLinha(item) : item.id;
    }
    limitarLargura(id, valor) {
      const itemAtual = this.coluna(id);
      return Math.max(itemAtual?.larguraMinima ?? 80, Math.min(itemAtual?.larguraMaxima ?? 600, Number(valor) || 200));
    }
    ouvir(alvo, evento, funcao, opcoes = {}) {
      alvo?.addEventListener(evento, funcao, {
        ...opcoes,
        signal: this.eventos.signal
      });
    }
    botao(rotulo, acao, classe = '') {
      const botaoAtual = criar('button', classe, rotulo);
      botaoAtual.type = 'button';
      botaoAtual.addEventListener('click', acao);
      return botaoAtual;
    }
    parte(tag, sufixo, classe) {
      const no = criar(tag, classe);
      no.id = `${this.id}-${sufixo}`;
      return no;
    }
    slot(destino, nome) {
      const conteudo = this.config.slots?.[nome];
      if (typeof conteudo === 'function') conteudo(destino, this);else if (conteudo instanceof Node) destino.append(conteudo);
    }
    montar() {
      const etiquetas = this.parte('div', 'chips', 'chips-filtros');
      const barraFerramentas = criar('section', 'lista-toolbar');
      barraFerramentas.setAttribute('aria-label', 'Ferramentas da tabela');
      const faixa = criar('div', 'toolbar-faixa'),
        comandos = criar('div', 'toolbar-grupo');
      this.slot(comandos, 'comandos');
      if (this.recursos.selecionarColunas) {
        const botaoAtual = this.parte('button', 'colunas-botao');
        botaoAtual.type = 'button';
        botaoAtual.textContent = this.textos.colunas;
        botaoAtual.setAttribute('aria-expanded', 'false');
        botaoAtual.setAttribute('aria-controls', `${this.id}-menu-colunas`);
        comandos.append(botaoAtual);
      }
      faixa.append(comandos);
      if (this.recursos.buscaIA) {
        const grupo = criar('div', 'toolbar-grupo'),
          formulario = this.parte('form', 'busca-form');
        formulario.className = 'af-busca';
        const entrada = criar('input');
        entrada.placeholder = this.textos.busca;
        entrada.maxLength = 500;
        entrada.setAttribute('aria-label', this.textos.busca);
        const enviar = criar('button', '', '↵');
        enviar.type = 'submit';
        enviar.setAttribute('aria-label', 'Preparar plano de busca');
        formulario.append(entrada, enviar);
        grupo.append(formulario);
        faixa.append(grupo);
      }
      barraFerramentas.append(faixa);
      if (this.recursos.selecionarColunas) {
        const menu = this.parte('div', 'menu-colunas', 'menu toolbar-menu');
        menu.hidden = true;
        barraFerramentas.append(menu);
      }
      if (this.recursos.buscaIA) {
        const menu = this.parte('div', 'plano-busca', 'menu plano-busca');
        menu.hidden = true;
        barraFerramentas.append(menu);
      }
      const painel = criar('section', 'tabela-painel'),
        areaRolavel = this.parte('div', 'scroll', 'tabela-scroll');
      const tabela = this.parte('table', 'tabela');
      tabela.setAttribute('aria-label', this.config.rotulo || 'Tabela de dados');
      const cabecalho = criar('thead');
      cabecalho.append(this.parte('tr', 'cabecalho'));
      tabela.append(this.parte('colgroup', 'colunas'), cabecalho, this.parte('tbody', 'corpo'));
      areaRolavel.append(tabela);
      const status = this.parte('div', 'estado', 'tabela-estado');
      status.setAttribute('role', 'status');
      status.setAttribute('aria-live', 'polite');
      painel.append(areaRolavel, status);
      if (this.recursos.paginacao || this.config.slots?.rodape) {
        const rodape = criar('footer', 'lista-rodape');
        if (this.recursos.paginacao) {
          const botaoAtual = this.parte('button', 'pagina-texto');
          botaoAtual.type = 'button';
          botaoAtual.setAttribute('aria-expanded', 'false');
          botaoAtual.setAttribute('aria-controls', `${this.id}-menu-pagina`);
          const menu = this.parte('div', 'menu-pagina', 'menu menu-pagina');
          menu.hidden = true;
          const paginaAtual = this.parte('nav', 'paginacao');
          paginaAtual.setAttribute('aria-label', 'Paginação');
          rodape.append(botaoAtual, menu, paginaAtual);
        }
        this.slot(rodape, 'rodape');
        painel.append(rodape);
      }
      this.raiz.replaceChildren(etiquetas);
      if (comandos.childNodes.length || this.recursos.buscaIA) this.raiz.append(barraFerramentas);
      this.raiz.append(painel);
    }
    restaurar() {
      if (!this.recursos.persistencia) return;
      try {
        const disposicao = JSON.parse(localStorage.getItem(`${this.storageId}-layout`) || 'null');
        const larguras = disposicao?.larguras || JSON.parse(localStorage.getItem(`${this.storageId}-larguras`) || '{}');
        this.colunas.forEach(itemAtual => {
          if (Number.isFinite(larguras[itemAtual.id])) this.estado.larguras[itemAtual.id] = this.limitarLargura(itemAtual.id, larguras[itemAtual.id]);
        });
        if (Array.isArray(disposicao?.visiveis)) {
          this.estado.visiveis = [...new Set(disposicao.visiveis.filter(id => this.coluna(id)))];
          this.colunas.filter(itemAtual => itemAtual.ocultavel === false).forEach(itemAtual => {
            if (!this.estado.visiveis.includes(itemAtual.id)) this.estado.visiveis.push(itemAtual.id);
          });
        }
        if (this.recursos.filtrosSalvos) this.estado.salvos = JSON.parse(localStorage.getItem(`${this.storageId}-filtros`) || '[]').filter(itemDoRegistro => this.coluna(itemDoRegistro.coluna)).map(itemDoRegistro => ({
          ...itemDoRegistro,
          filtro: itemDoRegistro.filtro ?? itemDoRegistro.filtros?.[itemDoRegistro.coluna]
        }));
      } catch {/* Armazenamento indisponível ou conteúdo antigo inválido não impede a tabela. */}
    }
    persistir() {
      if (!this.recursos.persistencia) return;
      try {
        localStorage.setItem(`${this.storageId}-layout`, JSON.stringify({
          larguras: this.estado.larguras,
          visiveis: this.estado.visiveis
        }));
        localStorage.setItem(`${this.storageId}-larguras`, JSON.stringify(this.estado.larguras));
        if (this.recursos.filtrosSalvos) localStorage.setItem(`${this.storageId}-filtros`, JSON.stringify(this.estado.salvos));
      } catch {/* Navegação continua sem persistência. */}
    }
    valor(item, itemAtual) {
      return itemAtual.valor ? itemAtual.valor(item) : item[itemAtual.id];
    }
    formatado(item, id) {
      const itemAtual = this.coluna(id),
        valor = this.valor(item, itemAtual);
      if (itemAtual.formatar) return itemAtual.formatar(valor, item);
      if (itemAtual.tipo === 'data' && valor) {
        const data = new Date(valor);
        return Number.isNaN(data.getTime()) ? String(valor) : new Intl.DateTimeFormat('pt-BR', {
          dateStyle: 'short',
          timeStyle: 'short'
        }).format(data);
      }
      return valor;
    }
    corresponde(item, filtros) {
      return Object.entries(filtros).every(([id, filtro]) => {
        const itemAtual = this.coluna(id);
        if (!itemAtual || filtro == null) return true;
        const valor = String(this.formatado(item, id) ?? '');
        if (Array.isArray(filtro)) return !filtro.length || filtro.includes(valor);
        if (typeof filtro === 'object') {
          const converter = valorAtual => itemAtual.tipo === 'data' ? new Date(valorAtual).getTime() : Number(valorAtual);
          const atual = converter(this.valor(item, itemAtual));
          const inicio = filtro.from !== '' && filtro.from != null,
            fim = filtro.to !== '' && filtro.to != null;
          // Datas finais incluem o dia inteiro, sem depender do nome do campo.
          const ate = converter(filtro.to) + (itemAtual.tipo === 'data' && /^\d{4}-\d{2}-\d{2}$/.test(filtro.to) ? 86399999 : 0);
          return (!inicio || atual >= converter(filtro.from)) && (!fim || atual <= ate);
        }
        return valor.toLocaleLowerCase('pt-BR').includes(String(filtro).toLocaleLowerCase('pt-BR'));
      });
    }
    dados() {
      if (this.config.modo === 'servidor') return [...this.estado.todos];
      const itens = this.estado.todos.filter(item => this.corresponde(item, this.estado.filtros));
      if (this.estado.ordem) {
        const {
            id,
            dir: direcao
          } = this.estado.ordem,
          itemAtual = this.coluna(id);
        itens.sort((primeiro, segundo) => {
          const converter = item => itemAtual.tipo === 'data' ? new Date(this.valor(item, itemAtual)).getTime() : itemAtual.tipo === 'numero' ? Number(this.valor(item, itemAtual)) : this.formatado(item, id);
          const x = converter(primeiro),
            y = converter(segundo);
          return (typeof x === 'number' ? x - y : String(x ?? '').localeCompare(String(y ?? ''), 'pt-BR', {
            numeric: true
          })) * (direcao === 'asc' ? 1 : -1);
        });
      }
      return itens;
    }
    idsVisiveis() {
      return [...(this.recursos.selecao ? ['selecao'] : []), ...(this.recursos.acoes ? ['acoes'] : []), ...this.estado.visiveis];
    }
    aplicarLarguras() {
      const grupo = this.no('colunas'),
        ids = this.idsVisiveis();
      grupo.replaceChildren();
      ids.forEach(id => {
        const colunaAtual = criar('col');
        colunaAtual.dataset.colunaId = id;
        colunaAtual.style.width = `${this.estado.larguras[id]}px`;
        grupo.append(colunaAtual);
      });
      this.no('tabela').style.width = `${ids.reduce((itemDoRegistro, id) => itemDoRegistro + this.estado.larguras[id], 0)}px`;
    }
    alca(id, nome) {
      const alca = criar('span', 'alca-coluna');
      alca.tabIndex = 0;
      alca.setAttribute('role', 'separator');
      alca.setAttribute('aria-orientation', 'vertical');
      alca.setAttribute('aria-label', `Redimensionar ${nome}`);
      const itemAtual = this.coluna(id);
      alca.setAttribute('aria-valuemin', itemAtual?.larguraMinima ?? 80);
      alca.setAttribute('aria-valuemax', itemAtual?.larguraMaxima ?? 600);
      const mudar = largura => {
        this.estado.larguras[id] = this.limitarLargura(id, largura);
        this.aplicarLarguras();
        alca.setAttribute('aria-valuenow', this.estado.larguras[id]);
      };
      alca.setAttribute('aria-valuenow', this.estado.larguras[id]);
      alca.addEventListener('pointerdown', evento => {
        evento.preventDefault();
        evento.stopPropagation();
        this.fecharMenus();
        alca.setPointerCapture(evento.pointerId);
        const inicio = evento.clientX,
          largura = this.estado.larguras[id];
        const mover = eventoAtual => mudar(largura + eventoAtual.clientX - inicio);
        const parar = () => {
          alca.removeEventListener('pointermove', mover);
          alca.removeEventListener('pointerup', parar);
          alca.removeEventListener('pointercancel', parar);
          this.persistir();
        };
        alca.addEventListener('pointermove', mover);
        alca.addEventListener('pointerup', parar);
        alca.addEventListener('pointercancel', parar);
      });
      alca.addEventListener('keydown', eventoAtual => {
        if (!['ArrowLeft', 'ArrowRight'].includes(eventoAtual.key)) return;
        eventoAtual.preventDefault();
        mudar(this.estado.larguras[id] + (eventoAtual.key === 'ArrowRight' ? 10 : -10));
        this.persistir();
      });
      return alca;
    }
    fecharMenus() {
      this.raiz.querySelectorAll('.menu').forEach(itemDaTela => itemDaTela.hidden = true);
      this.raiz.querySelectorAll('[aria-expanded]').forEach(botaoAtual => botaoAtual.setAttribute('aria-expanded', 'false'));
      if (this.menuHeader) {
        this.menuHeader.ancora.setAttribute('aria-expanded', 'false');
        this.menuHeader.menu.remove();
        this.menuHeader = null;
      }
    }
    posicionar(menu, ancora) {
      if (!menu || menu.hidden) return;
      Object.assign(menu.style, {
        position: 'fixed',
        right: 'auto',
        bottom: 'auto',
        overflowY: 'auto',
        maxWidth: `${innerWidth - 24}px`,
        zIndex: '1000'
      });
      const caixa = ancora.getBoundingClientRect();
      menu.style.left = `${Math.max(12, Math.min(caixa.left, innerWidth - menu.offsetWidth - 12))}px`;
      menu.style.top = `${caixa.bottom + 6}px`;
      menu.style.maxHeight = `${Math.max(60, innerHeight - caixa.bottom - 18)}px`;
    }
    reposicionar() {
      for (const [menu, botao] of [['menu-colunas', 'colunas-botao'], ['menu-pagina', 'pagina-texto']]) this.posicionar(this.no(menu), this.no(botao));
      if (this.menuHeader) this.posicionar(this.menuHeader.menu, this.menuHeader.ancora);
    }
    abrirColunas() {
      const menu = this.no('menu-colunas'),
        botaoAtual = this.no('colunas-botao'),
        aberto = !menu.hidden;
      this.fecharMenus();
      if (aberto) return;
      const rascunho = new Set(this.estado.visiveis),
        busca = criar('input'),
        lista = criar('div');
      busca.placeholder = 'Buscar colunas';
      busca.setAttribute('aria-label', 'Buscar colunas');
      const preencher = () => {
        lista.replaceChildren();
        this.colunas.filter(itemAtual => itemAtual.nome.toLocaleLowerCase().includes(busca.value.toLocaleLowerCase())).forEach(itemAtual => {
          const rotuloAtual = criar('label'),
            marcador = criar('input');
          marcador.type = 'checkbox';
          marcador.checked = rascunho.has(itemAtual.id);
          marcador.disabled = itemAtual.ocultavel === false;
          marcador.addEventListener('change', () => marcador.checked ? rascunho.add(itemAtual.id) : rascunho.delete(itemAtual.id));
          rotuloAtual.append(marcador, criar('span', '', itemAtual.nome));
          lista.append(rotuloAtual);
        });
      };
      busca.addEventListener('input', preencher);
      preencher();
      menu.replaceChildren(busca, lista, this.botao('Selecionar todas', () => {
        this.colunas.forEach(itemAtual => rascunho.add(itemAtual.id));
        preencher();
      }), this.botao('Desmarcar opcionais', () => {
        rascunho.clear();
        this.colunas.filter(itemAtual => itemAtual.ocultavel === false).forEach(itemAtual => rascunho.add(itemAtual.id));
        preencher();
      }), this.botao('Restaurar', () => {
        rascunho.clear();
        this.colunas.filter(itemAtual => itemAtual.visivel !== false || itemAtual.ocultavel === false).forEach(itemAtual => rascunho.add(itemAtual.id));
        preencher();
      }), this.botao('Aplicar', () => this.definirColunas([...rascunho]), 'primaria'));
      menu.hidden = false;
      botaoAtual.setAttribute('aria-expanded', 'true');
      this.posicionar(menu, botaoAtual);
    }
    abrirHeader(itemAtual, ancora) {
      const aberto = this.menuHeader?.ancora === ancora;
      this.fecharMenus();
      if (aberto) return;
      const menu = criar('div', 'menu coluna-menu');
      menu.dataset.tabelaInstancia = this.id;
      menu.setAttribute('role', 'dialog');
      menu.setAttribute('aria-label', `Opções da coluna ${itemAtual.nome}`);
      if (this.permitido('ordenacao', itemAtual.id)) {
        menu.append(this.botao('Ordenar crescente', () => this.ordenar(itemAtual.id, 'asc')), this.botao('Ordenar decrescente', () => this.ordenar(itemAtual.id, 'desc')));
      }
      if (this.permitido('reordenacao', itemAtual.id)) {
        for (const [rotulo, inicio] of [['Mover para o início', true], ['Mover para o fim', false]]) menu.append(this.botao(rotulo, () => {
          const ids = this.estado.visiveis.filter(id => id !== itemAtual.id);
          inicio ? ids.unshift(itemAtual.id) : ids.push(itemAtual.id);
          this.estado.visiveis = ids;
          this.persistir();
          this.render();
        }));
      }
      if (this.permitido('filtrosHeader', itemAtual.id)) {
        menu.append(criar('hr'));
        if (this.recursos.filtrosSalvos) {
          menu.append(criar('p', '', 'FILTROS SALVOS'));
          this.estado.salvos.filter(itemDoRegistro => itemDoRegistro.coluna === itemAtual.id).forEach(itemDoRegistro => menu.append(this.botao(itemDoRegistro.nome, () => this.filtrar(itemAtual.id, copiar(itemDoRegistro.filtro)))));
          menu.append(this.botao('Salvar filtro atual', () => {
            if (!this.estado.filtros[itemAtual.id]) return;
            const nome = prompt('Nome do filtro:');
            if (!nome?.trim()) return;
            this.estado.salvos.push({
              nome: nome.trim().slice(0, 80),
              coluna: itemAtual.id,
              filtro: copiar(this.estado.filtros[itemAtual.id])
            });
            this.persistir();
            this.fecharMenus();
          }));
        }
        if (this.config.modo === 'servidor' || ['numero', 'data'].includes(itemAtual.tipo)) {
          const de = criar('input'),
            ate = criar('input');
          if (['numero', 'data'].includes(itemAtual.tipo)) {
            de.type = ate.type = itemAtual.tipo === 'data' ? 'date' : 'number';
            de.setAttribute('aria-label', 'De');
            ate.setAttribute('aria-label', 'Até');
            de.value = this.estado.filtros[itemAtual.id]?.from ?? '';
            ate.value = this.estado.filtros[itemAtual.id]?.to ?? '';
            menu.append(de, ate, this.botao('Filtrar', () => this.filtrar(itemAtual.id, {
              from: de.value,
              to: ate.value
            })));
          } else {
            de.setAttribute('aria-label', `Filtrar ${itemAtual.nome}`);
            de.placeholder = 'Contém...';
            de.value = typeof this.estado.filtros[itemAtual.id] === 'string' ? this.estado.filtros[itemAtual.id] : '';
            menu.append(de, this.botao('Filtrar', () => this.filtrar(itemAtual.id, de.value)));
          }
        } else {
          const busca = criar('input'),
            lista = criar('div', 'valor-lista');
          busca.placeholder = 'Buscar valores';
          busca.setAttribute('aria-label', `Buscar valores de ${itemAtual.nome}`);
          const selecionados = new Set(Array.isArray(this.estado.filtros[itemAtual.id]) ? this.estado.filtros[itemAtual.id] : []);
          const preencher = () => {
            lista.replaceChildren();
            const contagens = new Map();
            this.estado.todos.forEach(item => {
              const valorAtual = String(this.formatado(item, itemAtual.id) ?? '');
              contagens.set(valorAtual, (contagens.get(valorAtual) || 0) + 1);
            });
            [...contagens].filter(([valorAtual]) => valorAtual.toLocaleLowerCase().includes(busca.value.toLocaleLowerCase())).sort(([primeiro], [segundo]) => primeiro.localeCompare(segundo)).forEach(([valorAtual, valorDoRegistro]) => {
              const rotuloAtual = criar('label'),
                marcador = criar('input');
              marcador.type = 'checkbox';
              marcador.checked = selecionados.has(valorAtual);
              marcador.addEventListener('change', () => marcador.checked ? selecionados.add(valorAtual) : selecionados.delete(valorAtual));
              rotuloAtual.append(marcador, criar('span', '', `${valorAtual} (${valorDoRegistro})`));
              lista.append(rotuloAtual);
            });
          };
          busca.addEventListener('input', preencher);
          preencher();
          menu.append(busca, lista, this.botao('Aplicar filtro', () => this.filtrar(itemAtual.id, [...selecionados])));
        }
        menu.append(this.botao('Limpar filtro desta coluna', () => this.filtrar(itemAtual.id, null)), this.botao('Limpar todos os filtros', () => this.limparFiltros()));
      }
      document.body.append(menu);
      this.menuHeader = {
        menu,
        ancora
      };
      ancora.setAttribute('aria-expanded', 'true');
      this.posicionar(menu, ancora);
    }
    linha(item) {
      const linhaAtual = criar('tr');
      if (this.recursos.selecao) {
        const celula = criar('td', `coluna-selecao${this.config.acoesFixas ? ' coluna-selecao-fixa' : ''}`),
          marcador = criar('input');
        marcador.type = 'checkbox';
        marcador.setAttribute('aria-label', `Selecionar ${this.chave(item)}`);
        marcador.checked = this.estado.selecionados.has(this.chave(item));
        marcador.addEventListener('change', () => {
          marcador.checked ? this.estado.selecionados.add(this.chave(item)) : this.estado.selecionados.delete(this.chave(item));
          this.config.onSelecao?.(this.obterSelecionados(), this);
          this.render();
        });
        celula.append(marcador);
        linhaAtual.append(celula);
      }
      if (this.recursos.acoes) {
        const celula = criar('td', this.config.acoesFixas ? 'coluna-acoes-fixa' : '');
        this.config.renderAcoes?.(item, celula, this.estado, () => this.render(), this.api.bind(this), this);
        linhaAtual.append(celula);
      }
      this.estado.visiveis.forEach(id => {
        const itemAtual = this.coluna(id),
          celula = criar('td');
        celula.title = String(this.formatado(item, id) ?? '');
        if (itemAtual.render) itemAtual.render(item, celula, this);else if (this.config.renderCelula) this.config.renderCelula(item, id, celula, this.estado, () => this.render(), this.api.bind(this), this);else celula.textContent = String(this.formatado(item, id) ?? '');
        if (this.permitido('edicao', id) && itemAtual.editavel !== false && this.config.edicaoInline !== false) {
          celula.classList.add('af-editavel');
          celula.addEventListener('dblclick', eventoAtual => {
            if (!eventoAtual.target.closest('button,a,input,select,textarea') && !window.getSelection()?.toString()) this.editar(item, itemAtual, celula);
          });
        }
        linhaAtual.append(celula);
      });
      linhaAtual.addEventListener('click', eventoAtual => {
        if (eventoAtual.target.closest('.btn-ver-linha') && this.recursos.detalhes) this.detalhes(item);else if (eventoAtual.target.closest('.btn-editar-linha') && this.recursos.edicao) this.detalhes(item, true);else if (!eventoAtual.target.closest('button,a,input,select,textarea') && !window.getSelection()?.toString()) this.config.onRowClick?.(item, this.estado, () => this.render(), this);
      });
      return linhaAtual;
    }
    async salvar(item, alteracoes) {
      try {
        const resultado = await this.config.salvar(item, alteracoes, this);
        if (this.destruida) return false;
        Object.assign(item, resultado || alteracoes);
        this.render();
        this.config.toast?.('Alterações salvas.');
        return true;
      } catch (erro) {
        this.config.toast?.(erro.message, true);
        const status = this.no('estado');
        if (status) status.textContent = erro.message;
        return false;
      }
    }
    editar(item, itemAtual, celula) {
      const entrada = criar('input');
      entrada.type = itemAtual.tipo === 'numero' ? 'number' : 'text';
      entrada.value = this.valor(item, itemAtual) ?? '';
      entrada.setAttribute('aria-label', `Editar ${itemAtual.nome}`);
      celula.replaceChildren(entrada);
      entrada.focus();
      let salvando = false;
      entrada.addEventListener('keydown', async eventoAtual => {
        if (eventoAtual.key === 'Escape') {
          eventoAtual.stopPropagation();
          this.render();
        }
        if (eventoAtual.key === 'Enter' && !salvando) {
          eventoAtual.preventDefault();
          salvando = true;
          entrada.disabled = true;
          const valor = itemAtual.tipo === 'numero' ? Number(entrada.value) : entrada.value;
          if (!(await this.salvar(item, {
            [itemAtual.id]: valor
          }))) {
            salvando = false;
            entrada.disabled = false;
            entrada.focus();
          }
        }
      });
      // Sair da célula cancela: nenhuma gravação implícita ao clicar fora.
      entrada.addEventListener('blur', () => {
        if (!salvando && !this.destruida) this.render();
      });
    }
    detalhes(item, editar = false) {
      if (!editar && !this.recursos.detalhes || editar && !this.recursos.edicao) return;
      this.dialogo?.remove();
      const janela = criar('dialog', 'modal-exportacao af-detalhes');
      janela.dataset.tabelaInstancia = this.id;
      this.dialogo = janela;
      janela.setAttribute('aria-label', editar ? 'Editar registro' : 'Detalhes do registro');
      const topo = criar('header', 'af-detalhes-topo'),
        x = this.botao('×', () => janela.close(), 'af-detalhes-fechar');
      x.setAttribute('aria-label', editar ? 'Fechar edição de registro' : 'Fechar detalhes do registro');
      x.title = 'Fechar';
      topo.append(criar('h2', '', editar ? 'Editar registro' : 'Detalhes do registro'), x);
      janela.append(topo);
      const campos = new Map();
      this.colunas.forEach(itemAtual => {
        if (itemAtual.detalhes === false) return;
        const rotuloAtual = criar('label', 'af-campo', itemAtual.nome);
        if (editar && this.permitido('edicao', itemAtual.id) && itemAtual.editavel !== false) {
          const entrada = criar('input');
          entrada.type = itemAtual.tipo === 'numero' ? 'number' : 'text';
          entrada.value = this.valor(item, itemAtual) ?? '';
          entrada.autofocus = campos.size === 0;
          campos.set(itemAtual.id, entrada);
          rotuloAtual.append(entrada);
        } else rotuloAtual.append(criar('div', '', this.formatado(item, itemAtual.id)));
        janela.append(rotuloAtual);
      });
      const acoes = criar('footer', 'af-detalhes-acoes'),
        fechar = this.botao('Fechar', () => janela.close());
      acoes.append(fechar);
      janela.append(acoes);
      if (editar) acoes.append(this.botao('Salvar alterações', async eventoAtual => {
        eventoAtual.currentTarget.disabled = true;
        const alteracoes = Object.fromEntries([...campos].map(([id, entrada]) => [id, this.coluna(id).tipo === 'numero' ? Number(entrada.value) : entrada.value]));
        if (await this.salvar(item, alteracoes)) janela.close();else eventoAtual.target.disabled = false;
      }, 'primaria'));
      janela.addEventListener('close', () => {
        janela.remove();
        if (this.dialogo === janela) this.dialogo = null;
      });
      document.body.append(janela);
      janela.showModal();
    }
    render() {
      if (this.destruida) return;
      const focoAnterior = this.raiz.contains(document.activeElement) ? document.activeElement : null;
      this.fecharMenus();
      this.aplicarLarguras();
      const itens = this.dados();
      if (this.config.modo !== 'servidor') this.estado.total = itens.length;
      const totalPaginas = this.recursos.paginacao ? Math.max(1, Math.ceil(this.estado.total / this.estado.tamanho)) : 1;
      this.estado.pagina = Math.max(1, Math.min(this.estado.pagina, totalPaginas));
      const cabecalho = this.no('cabecalho');
      cabecalho.replaceChildren();
      if (this.recursos.selecao) {
        const tituloColuna = criar('th', this.config.acoesFixas ? 'coluna-selecao-fixa' : ''),
          marcador = criar('input');
        marcador.type = 'checkbox';
        marcador.setAttribute('aria-label', 'Selecionar os registros carregados e filtrados');
        marcador.title = 'Selecionar tudo no resultado filtrado';
        const selecionados = itens.filter(item => this.estado.selecionados.has(this.chave(item))).length;
        marcador.checked = itens.length > 0 && selecionados === itens.length;
        marcador.indeterminate = selecionados > 0 && selecionados < itens.length;
        marcador.addEventListener('change', () => {
          itens.forEach(item => marcador.checked ? this.estado.selecionados.add(this.chave(item)) : this.estado.selecionados.delete(this.chave(item)));
          this.config.onSelecao?.(this.obterSelecionados(), this);
          this.render();
        });
        tituloColuna.append(marcador);
        cabecalho.append(tituloColuna);
      }
      if (this.recursos.acoes) {
        const tituloColuna = criar('th', this.config.acoesFixas ? 'coluna-acoes-fixa' : '');
        tituloColuna.append(criar('span', 'titulo-coluna titulo-coluna-fixa', this.textos.acoes));
        if (this.recursos.redimensionamento) tituloColuna.append(this.alca('acoes', this.textos.acoes));
        cabecalho.append(tituloColuna);
      }
      this.estado.visiveis.forEach(id => {
        const itemAtual = this.coluna(id),
          tituloColuna = criar('th');
        tituloColuna.scope = 'col';
        tituloColuna.dataset.coluna = id;
        const interativo = ['ordenacao', 'filtrosHeader', 'reordenacao'].some(registro => this.permitido(registro, id));
        const rotulo = `${itemAtual.nome}${this.estado.ordem?.id === id ? this.estado.ordem.dir === 'asc' ? ' ▲' : ' ▼' : ''}`;
        const titulo = interativo ? this.botao(rotulo, () => this.abrirHeader(itemAtual, titulo), 'titulo-coluna') : criar('span', 'titulo-coluna titulo-coluna-fixa', rotulo);
        if (interativo) {
          titulo.setAttribute('aria-label', `Opções da coluna ${itemAtual.nome}`);
          titulo.setAttribute('aria-expanded', 'false');
        }
        if (this.estado.filtros[id] != null) titulo.classList.add('filtrada');
        tituloColuna.append(titulo);
        if (this.permitido('redimensionamento', id)) tituloColuna.append(this.alca(id, itemAtual.nome));
        cabecalho.append(tituloColuna);
      });
      const pagina = this.config.modo === 'servidor' || !this.recursos.paginacao ? itens : itens.slice((this.estado.pagina - 1) * this.estado.tamanho, this.estado.pagina * this.estado.tamanho);
      this.no('corpo').replaceChildren(...pagina.map(item => this.linha(item)));
      const status = this.no('estado');
      if (status) status.textContent = this.estado.carregando ? this.textos.carregando : !this.estado.todos.length ? this.textos.vazio : !itens.length ? this.textos.semResultados : '';
      const textoPortugues = this.no('pagina-texto');
      if (textoPortugues) textoPortugues.textContent = this.textos.pagina({
        pagina: this.estado.pagina,
        totalPaginas,
        tamanho: this.estado.tamanho,
        total: this.estado.total
      });
      const paginaAtual = this.no('paginacao');
      if (paginaAtual && this.recursos.paginacao) {
        const anterior = this.botao('←', () => this.irParaPagina(this.estado.pagina - 1));
        anterior.disabled = this.estado.pagina <= 1;
        const proximo = this.botao('→', () => this.irParaPagina(this.estado.pagina + 1));
        proximo.disabled = this.estado.pagina >= totalPaginas;
        const botoes = [];
        const inicio = Math.max(1, Math.min(this.estado.pagina - 2, totalPaginas - 4));
        for (let paragrafo = inicio; paragrafo <= Math.min(totalPaginas, inicio + 4); paragrafo++) {
          const botaoAtual = this.botao(paragrafo, () => this.irParaPagina(paragrafo), paragrafo === this.estado.pagina ? 'atual' : '');
          if (paragrafo === this.estado.pagina) botaoAtual.setAttribute('aria-current', 'page');
          botoes.push(botaoAtual);
        }
        paginaAtual.replaceChildren(anterior, ...botoes, proximo);
      }
      const etiquetas = this.no('chips');
      if (etiquetas) {
        etiquetas.replaceChildren();
        Object.entries(this.estado.filtros).forEach(([id, filtro]) => {
          const valor = Array.isArray(filtro) ? filtro.join(', ') : typeof filtro === 'object' ? `${filtro.from ?? '…'} a ${filtro.to ?? '…'}` : filtro;
          const etiqueta = criar('span', 'chip', `${this.coluna(id)?.nome}: ${valor}`);
          etiqueta.append(this.botao('✕', () => this.filtrar(id, null), 'icone'));
          etiquetas.append(etiqueta);
        });
      }
      this.config.onRender?.(this);
      // Campos reutilizados no cabeçalho preservam o foco após a atualização.
      if (focoAnterior?.isConnected && document.activeElement === document.body) focoAnterior.focus({preventScroll: true});
    }
    confirmarExclusao(rotulo, origem) {
      if (this.confirmacaoExclusao || this.destruida) return Promise.resolve(false);
      const janela = criar('dialog', 'af-confirmacao-exclusao');
      janela.setAttribute('aria-label', 'Confirmar exclusão');
      const titulo = criar('h2', '', 'Excluir este registro?'),
        texto = criar('p', '', rotulo || 'Excluir registro'),
        ajuda = criar('p', '', 'Deseja realmente excluir? O registro será removido desta listagem.');
      const formulario = criar('form', 'af-confirmacao-acoes');
      formulario.method = 'dialog';
      const cancelar = criar('button', '', 'Cancelar');
      cancelar.type = 'button';
      cancelar.value = 'cancelar';
      cancelar.autofocus = true;
      const excluir = criar('button', 'destrutivo', 'Sim, excluir');
      excluir.type = 'button';
      excluir.value = 'excluir';
      formulario.append(cancelar, excluir);
      janela.append(titulo, texto, ajuda, formulario);
      this.confirmacaoExclusao = janela;
      document.body.append(janela);
      return new Promise(concluir => {
        let finalizada = false;
        const finalizar = sim => {
          if (finalizada) return;
          finalizada = true;
          janela.close(sim ? 'excluir' : 'cancelar');
          janela.remove();
          this.confirmacaoExclusao = null;
          this.cancelarConfirmacaoExclusao = null;
          if (origem?.isConnected) origem.focus();
          concluir(sim);
        };
        cancelar.onclick = () => finalizar(false);
        excluir.onclick = () => finalizar(true);
        this.cancelarConfirmacaoExclusao = () => finalizar(false);
        janela.addEventListener('cancel', eventoAtual => {
          eventoAtual.preventDefault();
          finalizar(false);
        }, {
          once: true
        });
        janela.addEventListener('close', () => finalizar(janela.returnValue === 'excluir'), {
          once: true
        });
        janela.showModal();
      });
    }
    vincular() {
      const confirmados = new WeakSet();
      this.ouvir(this.raiz, 'click', eventoAtual => {
        const botaoAtual = eventoAtual.target.closest('button.destrutivo,button[data-confirmar-exclusao]');
        if (!botaoAtual || !this.raiz.contains(botaoAtual) || botaoAtual.closest('.af-confirmacao-exclusao')) return;
        if (confirmados.has(botaoAtual)) {
          confirmados.delete(botaoAtual);
          return;
        }
        eventoAtual.preventDefault();
        eventoAtual.stopImmediatePropagation();
        this.confirmarExclusao(botaoAtual.getAttribute('aria-label') || botaoAtual.title || botaoAtual.textContent, botaoAtual).then(sim => {
          if (sim && !this.destruida && botaoAtual.isConnected && !botaoAtual.disabled) {
            confirmados.add(botaoAtual);
            botaoAtual.click();
            confirmados.delete(botaoAtual);
          }
        });
      }, {
        capture: true
      });
      this.ouvir(this.no('colunas-botao'), 'click', () => this.abrirColunas());
      this.ouvir(this.no('pagina-texto'), 'click', () => {
        const menu = this.no('menu-pagina'),
          botaoAtual = this.no('pagina-texto'),
          aberto = !menu.hidden;
        this.fecharMenus();
        if (aberto) return;
        const tamanhos = criar('div', 'tamanhos-pagina');
        (this.config.tamanhosPagina || [5, 10, 50, 100]).forEach(valorDoRegistro => tamanhos.append(this.botao(valorDoRegistro, () => this.definirTamanhoPagina(valorDoRegistro))));
        const numero = criar('input');
        numero.type = 'number';
        numero.min = 1;
        numero.max = Math.max(1, Math.ceil(this.estado.total / this.estado.tamanho));
        numero.value = this.estado.pagina;
        numero.setAttribute('aria-label', 'Número da página');
        menu.replaceChildren(criar('p', '', 'LINHAS POR PÁGINA'), tamanhos, criar('p', '', 'IR PARA A PÁGINA'), numero, this.botao('Ir', () => this.irParaPagina(Number(numero.value))));
        menu.hidden = false;
        botaoAtual.setAttribute('aria-expanded', 'true');
        this.posicionar(menu, botaoAtual);
      });
      this.ouvir(document, 'pointerdown', eventoAtual => {
        const pares = [['menu-colunas', 'colunas-botao'], ['menu-pagina', 'pagina-texto']].map(([itemDaTela, botaoAtual]) => ({
          menu: this.no(itemDaTela),
          ancora: this.no(botaoAtual)
        }));
        if (this.menuHeader) pares.push(this.menuHeader);
        pares.forEach(({
          menu,
          ancora
        }) => {
          if (menu && !menu.hidden && !menu.contains(eventoAtual.target) && !ancora?.contains(eventoAtual.target)) {
            menu.hidden = true;
            ancora?.setAttribute('aria-expanded', 'false');
            if (menu === this.menuHeader?.menu) {
              menu.remove();
              this.menuHeader = null;
            }
          }
        });
      });
      this.ouvir(document, 'keydown', eventoAtual => {
        if (eventoAtual.key === 'Escape' && (this.raiz.contains(eventoAtual.target) || this.menuHeader?.menu.contains(eventoAtual.target))) this.fecharMenus();
      });
      this.ouvir(window, 'resize', () => this.reposicionar());
      this.ouvir(window, 'scroll', () => this.reposicionar(), {
        capture: true
      });
      if (this.recursos.buscaIA) {
        const formulario = this.no('busca-form') || document.getElementById(this.config.buscaFormId || this.config.iaFormId);
        this.ouvir(formulario, 'submit', eventoAtual => {
          eventoAtual.preventDefault();
          const consulta = formulario.querySelector('input').value.trim();
          if (consulta) this.buscarIA(consulta);
        });
      }
    }
    atualizar() {
      this.fecharMenus();
      return this.config.modo === 'servidor' ? this.carregar() : this.render();
    }
    filtrar(id, filtro) {
      if (!this.permitido('filtrosHeader', id)) throw new Error(`Filtro desativado para ${id}.`);
      if (filtro == null || filtro === '' || Array.isArray(filtro) && !filtro.length) delete this.estado.filtros[id];else this.estado.filtros[id] = copiar(filtro);
      this.estado.pagina = 1;
      return this.atualizar();
    }
    limparFiltros() {
      this.estado.filtros = {};
      this.estado.busca = [];
      this.estado.pagina = 1;
      return this.atualizar();
    }
    ordenar(id, direcao = 'asc') {
      if (!this.permitido('ordenacao', id) || !['asc', 'desc'].includes(direcao)) throw new Error(`Ordenação inválida ou desativada para ${id}.`);
      this.estado.ordem = {
        id,
        dir: direcao
      };
      this.estado.pagina = 1;
      return this.atualizar();
    }
    definirColunas(ids) {
      if (!this.recursos.selecionarColunas) throw new Error('Seletor de colunas desativado.');
      if (ids.some(id => !this.coluna(id))) throw new Error('Coluna desconhecida.');
      const fixas = this.colunas.filter(itemAtual => itemAtual.ocultavel === false).map(itemAtual => itemAtual.id);
      this.estado.visiveis = [...new Set([...ids, ...fixas])];
      this.persistir();
      this.render();
    }
    irParaPagina(pagina) {
      if (!this.recursos.paginacao) return;
      this.estado.pagina = Math.max(1, Math.min(Math.max(1, Math.ceil(this.estado.total / this.estado.tamanho)), Math.floor(Number(pagina)) || 1));
      return this.atualizar();
    }
    definirTamanhoPagina(tamanho) {
      this.estado.tamanho = this.tamanhoValido(tamanho);
      this.estado.pagina = 1;
      return this.atualizar();
    }
    definirDados(itens) {
      if (this.config.modo === 'servidor') throw new Error('No modo servidor, forneça os dados por carregarDados ou endpoint.');
      if (!Array.isArray(itens)) throw new Error('FirmaTabela: dados deve ser um array.');
      this.validarChaves(itens);
      this.estado.todos = [...itens];
      const chaves = new Set(itens.map(item => this.chave(item)));
      this.estado.selecionados = new Set([...this.estado.selecionados].filter(id => chaves.has(id)));
      this.render();
    }
    limparDados() {
      this.versao++;
      this.requisicao?.abort();
      this.fecharMenus();
      this.dialogo?.close();
      this.estado.todos = [];
      this.estado.total = 0;
      this.estado.pagina = 1;
      this.estado.carregando = false;
      this.estado.filtros = {};
      this.estado.ordem = null;
      this.estado.selecionados.clear();
      this.config.onSelecao?.([], this);
      this.render();
    }
    validarChaves(itens) {
      if (!this.recursos.selecao) return;
      const chaves = itens.map(item => this.chave(item));
      if (chaves.some(id => id == null) || new Set(chaves).size !== chaves.length) throw new Error('FirmaTabela: seleção requer uma chave única por registro.');
    }
    obterSelecionados() {
      return [...this.estado.selecionados];
    }
    limparSelecao() {
      this.estado.selecionados.clear();
      this.config.onSelecao?.([], this);
      this.render();
    }
    getEstado() {
      return this.estado;
    } // Compatibilidade; prefira os métodos públicos para alterações.
    api(caminhos = '', opcoesAtual = {}) {
      return this.requisitar(`${this.config.endpoint || ''}${caminhos}`, opcoesAtual);
    }
    async requisitar(endereco, opcoesAtual = {}) {
      const cabecalhos = new Headers(this.config.credenciais?.() || this.config.headers || {});
      new Headers(opcoesAtual.headers || {}).forEach((valorAtual, chaveAtual) => cabecalhos.set(chaveAtual, valorAtual));
      if (typeof opcoesAtual.body === 'string' && !cabecalhos.has('Content-Type')) cabecalhos.set('Content-Type', 'application/json');
      const resposta = await fetch(endereco, {
        ...opcoesAtual,
        headers: cabecalhos
      });
      if (!resposta.ok) {
        let detalhe = {};
        try {
          detalhe = await resposta.json();
        } catch {/* Resposta sem JSON. */}
        throw new Error(detalhe.mensagem || `Falha na requisição (${resposta.status}).`);
      }
      return resposta.status === 204 ? null : resposta.json();
    }
    async carregar() {
      if (this.destruida) return false;
      const versao = ++this.versao;
      this.requisicao?.abort();
      this.requisicao = new AbortController();
      this.estado.carregando = true;
      this.render();
      try {
        const consulta = {
          pagina: this.estado.pagina,
          tamanho: this.estado.tamanho,
          ordem: copiar(this.estado.ordem),
          filtros: copiar(this.estado.filtros),
          signal: this.requisicao.signal
        };
        let resposta;
        if (this.config.carregarDados) resposta = await this.config.carregarDados(consulta, this);else {
          let caminhos = '';
          if (this.config.modo === 'servidor') {
            const parametrosAtuais = this.config.parametros ? this.config.parametros(consulta) : {
              pagina: consulta.pagina,
              tamanho: consulta.tamanho,
              ordem: JSON.stringify(consulta.ordem),
              filtros: JSON.stringify(consulta.filtros)
            };
            caminhos = `${String(this.config.endpoint).includes('?') ? '&' : '?'}${new URLSearchParams(parametrosAtuais)}`;
          }
          resposta = await this.api(caminhos, {
            signal: consulta.signal
          });
        }
        if (versao !== this.versao || this.destruida) return false;
        if (this.config.mapearResposta) resposta = this.config.mapearResposta(resposta);
        const itens = this.config.modo === 'servidor' ? resposta?.itens : resposta;
        if (!Array.isArray(itens)) throw new Error('Resposta inválida: esperado um array, ou { itens, total } no modo servidor.');
        if (this.config.modo === 'servidor' && (!Number.isInteger(resposta.total) || resposta.total < 0)) throw new Error('Resposta inválida: total deve ser inteiro não negativo.');
        this.validarChaves(itens);
        this.estado.todos = [...itens];
        this.estado.carregando = false;
        if (this.config.modo === 'servidor') {
          this.estado.total = resposta.total;
          const ultima = Math.max(1, Math.ceil(resposta.total / this.estado.tamanho));
          if (this.estado.pagina > ultima) {
            this.estado.pagina = ultima;
            return this.carregar();
          }
        } else {
          const ids = new Set(itens.map(item => this.chave(item)));
          this.estado.selecionados = new Set([...this.estado.selecionados].filter(id => ids.has(id)));
        }
        this.render();
        this.config.onCarregar?.(this);
        return true;
      } catch (erro) {
        if (versao !== this.versao || this.destruida || erro.name === 'AbortError') return false;
        this.estado.carregando = false;
        this.render();
        this.no('estado').textContent = erro.message;
        this.config.toast?.(erro.message, true);
        this.config.onErro?.(erro, this);
        return false;
      }
    }
    async buscarIA(consulta) {
      if (!this.recursos.buscaIA || this.destruida) return;
      const painel = this.no('plano-busca');
      painel.hidden = false;
      painel.replaceChildren(criar('p', '', 'Interpretando...'));
      const versao = this.versaoBusca = (this.versaoBusca || 0) + 1;
      this.requisicaoBusca?.abort();
      this.requisicaoBusca = new AbortController();
      try {
        const plano = this.config.interpretarBusca ? await this.config.interpretarBusca(consulta, this) : await this.requisitar(this.config.buscaEndpoint || this.config.iaEndpoint || `${this.config.endpoint || ''}/plano-busca`, {
          method: 'POST',
          body: JSON.stringify({
            consulta
          }),
          signal: this.requisicaoBusca.signal
        });
        if (this.destruida || versao !== this.versaoBusca) return;
        if (!Array.isArray(plano.steps) || plano.steps.some(eventoAtual => !this.coluna(eventoAtual.column) || !['sort', 'column-filter'].includes(eventoAtual.op) || (eventoAtual.op === 'sort' ? !this.permitido('ordenacao', eventoAtual.column) || !['asc', 'desc'].includes(eventoAtual.dir) : !this.permitido('filtrosHeader', eventoAtual.column) || !['equals', 'contains', 'range'].includes(eventoAtual.type)))) throw new Error('O plano contém operações ou colunas indisponíveis.');
        this.estado.plano = plano;
        const mostrar = () => {
          const lista = criar('ol');
          plano.steps.forEach((eventoAtual, indice) => {
            const item = criar('li', '', eventoAtual.op === 'sort' ? `Ordenar ${this.coluna(eventoAtual.column).nome}: ${eventoAtual.dir}` : `Filtrar ${this.coluna(eventoAtual.column).nome}: ${eventoAtual.value ?? `${eventoAtual.from ?? '…'} a ${eventoAtual.to ?? '…'}`}`);
            item.append(this.botao('✕', () => {
              plano.steps.splice(indice, 1);
              mostrar();
            }, 'icone'));
            lista.append(item);
          });
          painel.replaceChildren(criar('h2', '', 'PLANO DE BUSCA'), criar('p', '', plano.summary || consulta), lista, this.botao('Aplicar', () => {
            plano.steps.forEach(eventoAtual => {
              if (eventoAtual.op === 'sort') this.estado.ordem = {
                id: eventoAtual.column,
                dir: eventoAtual.dir
              };else this.estado.filtros[eventoAtual.column] = eventoAtual.type === 'range' ? {
                from: eventoAtual.from,
                to: eventoAtual.to
              } : eventoAtual.type === 'equals' ? [eventoAtual.value] : eventoAtual.value;
            });
            this.estado.pagina = 1;
            this.estado.plano = null;
            this.render();
          }, 'primaria'), this.botao('Cancelar', () => {
            painel.hidden = true;
            this.estado.plano = null;
          }));
        };
        mostrar();
      } catch (erro) {
        if (!this.destruida && versao === this.versaoBusca) {
          painel.replaceChildren(criar('p', '', erro.message));
          this.config.toast?.(erro.message, true);
        }
      }
    }
    destruir() {
      if (this.destruida) return;
      this.cancelarConfirmacaoExclusao?.();
      this.destruida = true;
      this.versao++;
      this.versaoBusca++;
      this.requisicao?.abort();
      this.requisicaoBusca?.abort();
      this.eventos.abort();
      this.fecharMenus();
      this.dialogo?.remove();
      montadas.delete(this.raiz);
      delete this.raiz.dataset.tabelaInstancia;
      if (this.config.elemento) this.raiz.replaceChildren();
    }
  }
  // Ações básicas compartilham os ícones e as classes do Trator de assinaturas.
  FirmaTabela.botaoAcao = (tipo, rotulo, executar) => {
    const tipos = {
      visualizar: ['👁', 'Visualizar detalhes', 'btn-ver-linha'],
      editar: ['✎', 'Editar registro', 'btn-editar-linha'],
      historico: ['◷', 'Histórico', ''],
      remover: ['✖', 'Excluir registro', 'destrutivo'],
      finalizar: ['✓', 'Finalizar atendimento', ''],
      reativar: ['↻', 'Reativar atendimento', '']
    };
    if (!tipos[tipo]) throw new Error('Ação padrão desconhecida');
    const [icone, titulo, classe] = tipos[tipo],
      botaoAtual = criar('button', 'icone ' + classe, icone);
    botaoAtual.type = 'button';
    botaoAtual.title = rotulo || titulo;
    botaoAtual.setAttribute('aria-label', botaoAtual.title);
    if (executar) botaoAtual.onclick = async eventoAtual => {
      eventoAtual.stopPropagation();
      botaoAtual.disabled = true;
      try {
        await executar(eventoAtual);
      } finally {
        botaoAtual.disabled = false;
      }
    };
    return botaoAtual;
  };
  FirmaTabela.botaoControle = (rotulo, icone, executar) => {
    const botaoAtual = criar('button', 'controle-roca');
    botaoAtual.type = 'button';
    botaoAtual.title = rotulo;
    if (/^Excluir\b/i.test(rotulo)) botaoAtual.setAttribute('data-confirmar-exclusao', '');
    const marca = criar('span', 'controle-roca-icone', icone);
    marca.setAttribute('aria-hidden', 'true');
    botaoAtual.append(marca, criar('span', '', rotulo));
    botaoAtual.onclick = async eventoAtual => {
      eventoAtual.stopPropagation();
      botaoAtual.disabled = true;
      botaoAtual.setAttribute('aria-busy', 'true');
      try {
        await executar(eventoAtual);
      } finally {
        botaoAtual.disabled = false;
        botaoAtual.removeAttribute('aria-busy');
      }
    };
    return botaoAtual;
  };
  FirmaTabela.recursosPadrao = padroes;
  window.FirmaTabela = FirmaTabela;
  window.aFirmaTabela = configuracao => new FirmaTabela(configuracao);
})();
