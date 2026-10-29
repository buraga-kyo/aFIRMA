/** Tabela reutilizável aFIRMA. Contrato público: docs/tabela.md. */
(() => {
  let sequencia = 0;
  const montadas = new WeakMap();
  const padroes = Object.freeze({
    selecao: false, acoes: false, paginacao: true, selecionarColunas: true,
    filtrosHeader: true, ordenacao: true, redimensionamento: true,
    reordenacao: false, filtrosSalvos: false, buscaIA: false,
    edicao: false, detalhes: false, persistencia: false
  });
  const criar = (tag, classe, texto) => {
    const no = document.createElement(tag);
    if (classe) no.className = classe;
    if (texto !== undefined) no.textContent = String(texto ?? '');
    return no;
  };
  const copiar = valor => structuredClone(valor);

  class FirmaTabela {
    constructor(config = {}) {
      this.config = config;
      this.raiz = typeof config.elemento === 'string' ? document.querySelector(config.elemento) : config.elemento;
      // Compatibilidade com o HTML antigo; novas instâncias usam elemento.
      if (!this.raiz && config.containerId) this.raiz = document.getElementById(`${config.containerId}-tabela`)?.closest('.af-tabela, main');
      if (!this.raiz) throw new Error('FirmaTabela: informe um elemento de destino existente.');
      if (montadas.has(this.raiz)) throw new Error('FirmaTabela: o destino já possui uma instância. Use destruir() antes de remontar.');
      this.id = config.containerId || `af-tabela-${++sequencia}`;
      this.colunas = (config.colunas || []).map(c => ({ ...c, nome: c.nome ?? c.header ?? c.id }));
      const ids = this.colunas.map(c => c.id);
      if (!ids.length || new Set(ids).size !== ids.length || ids.some(id => !id || ['selecao', 'acoes'].includes(id))) {
        throw new Error('FirmaTabela: defina colunas com IDs únicos; selecao e acoes são reservados.');
      }
      if (document.getElementById(`${this.id}-tabela`) && config.elemento) throw new Error(`FirmaTabela: prefixo ${this.id} já está em uso.`);
      this.recursos = Object.fromEntries(Object.keys(padroes).map(chave => [chave, config.recursos === false ? false : (config.recursos?.[chave] ?? padroes[chave])]));
      if (config.recursos !== false && config.recursos?.acoes === undefined) this.recursos.acoes = Boolean(config.renderAcoes);
      if (this.recursos.edicao && typeof config.salvar !== 'function') throw new Error('FirmaTabela: edição requer o callback salvar(item, alteracoes, contexto).');
      if (config.modo === 'servidor' && this.recursos.buscaIA) throw new Error('FirmaTabela: buscaIA atualmente suporta apenas o modo local.');
      this.textos = {
        colunas: '▤ COLUNAS ▾', acoes: 'AÇÕES', vazio: 'Nenhum registro encontrado.',
        semResultados: 'Nenhum resultado para os filtros atuais.', carregando: 'Carregando dados...',
        busca: 'Descreva sua busca...', pagina: ({ pagina, totalPaginas, tamanho }) => `Página ${pagina} de ${totalPaginas} · ${tamanho} linhas ▾`,
        ...config.textos
      };
      this.eventos = new AbortController();
      this.destruida = false;
      this.versao = 0;
      this.storageId = config.storageId || this.id;
      this.estado = {
        todos: [], visiveis: this.colunas.filter(c => c.visivel !== false || c.ocultavel === false).map(c => c.id),
        filtros: {}, ordem: null, pagina: 1, tamanho: this.tamanhoValido(config.tamanhoPagina ?? 10),
        larguras: Object.fromEntries(this.colunas.map(c => [c.id, this.limitarLargura(c.id, c.largura ?? 200)])),
        selecionados: new Set(), salvos: [], busca: [], plano: null, total: 0, carregando: false
      };
      this.estado.larguras.selecao = 34;
      this.estado.larguras.acoes = config.larguraAcoes ?? 96;
      this.restaurar();
      this.raiz.classList.add('af-tabela');
      this.raiz.dataset.tabelaInstancia = this.id;
      if (config.elemento) this.montar();
      this.no = sufixo => this.raiz.querySelector(`[id="${this.id}-${sufixo}"]`);
      for (const sufixo of ['tabela', 'colunas', 'cabecalho', 'corpo']) {
        if (!this.no(sufixo)) throw new Error(`FirmaTabela: elemento ${this.id}-${sufixo} ausente.`);
      }
      montadas.set(this.raiz, this);
      this.vincular();
      if (Array.isArray(config.dados)) this.definirDados(config.dados);
      else if (config.autoInit !== false) this.render();
    }

    tamanhoValido(valor) {
      if (!Number.isInteger(valor) || valor < 1) throw new Error('FirmaTabela: tamanho da página deve ser inteiro positivo.');
      return valor;
    }
    coluna(id) { return this.colunas.find(c => c.id === id); }
    permitido(recurso, id) { return this.recursos[recurso] && (!id || (this.coluna(id) && this.coluna(id)[recurso] !== false)); }
    chave(item) { return this.config.chaveLinha ? this.config.chaveLinha(item) : item.id; }
    limitarLargura(id, valor) {
      const c = this.coluna(id);
      return Math.max(c?.larguraMinima ?? 80, Math.min(c?.larguraMaxima ?? 600, Number(valor) || 200));
    }
    ouvir(alvo, evento, funcao, opcoes = {}) {
      alvo?.addEventListener(evento, funcao, { ...opcoes, signal: this.eventos.signal });
    }
    botao(rotulo, acao, classe = '') {
      const b = criar('button', classe, rotulo); b.type = 'button'; b.addEventListener('click', acao); return b;
    }
    parte(tag, sufixo, classe) { const no = criar(tag, classe); no.id = `${this.id}-${sufixo}`; return no; }
    slot(destino, nome) {
      const conteudo = this.config.slots?.[nome];
      if (typeof conteudo === 'function') conteudo(destino, this);
      else if (conteudo instanceof Node) destino.append(conteudo);
    }
    montar() {
      const chips = this.parte('div', 'chips', 'chips-filtros');
      const toolbar = criar('section', 'lista-toolbar'); toolbar.setAttribute('aria-label', 'Ferramentas da tabela');
      const faixa = criar('div', 'toolbar-faixa'), comandos = criar('div', 'toolbar-grupo');
      this.slot(comandos, 'comandos');
      if (this.recursos.selecionarColunas) {
        const b = this.parte('button', 'colunas-botao'); b.type = 'button'; b.textContent = this.textos.colunas;
        b.setAttribute('aria-expanded', 'false'); b.setAttribute('aria-controls', `${this.id}-menu-colunas`); comandos.append(b);
      }
      faixa.append(comandos);
      if (this.recursos.buscaIA) {
        const grupo = criar('div', 'toolbar-grupo'), form = this.parte('form', 'busca-form'); form.className = 'af-busca';
        const input = criar('input'); input.placeholder = this.textos.busca; input.maxLength = 500; input.setAttribute('aria-label', this.textos.busca);
        const enviar = criar('button', '', '↵'); enviar.type = 'submit'; enviar.setAttribute('aria-label', 'Preparar plano de busca');
        form.append(input, enviar); grupo.append(form); faixa.append(grupo);
      }
      toolbar.append(faixa);
      if (this.recursos.selecionarColunas) {
        const menu = this.parte('div', 'menu-colunas', 'menu toolbar-menu'); menu.hidden = true; toolbar.append(menu);
      }
      if (this.recursos.buscaIA) { const menu = this.parte('div', 'plano-busca', 'menu plano-busca'); menu.hidden = true; toolbar.append(menu); }
      const painel = criar('section', 'tabela-painel'), scroll = this.parte('div', 'scroll', 'tabela-scroll');
      const tabela = this.parte('table', 'tabela'); tabela.setAttribute('aria-label', this.config.rotulo || 'Tabela de dados');
      const head = criar('thead'); head.append(this.parte('tr', 'cabecalho'));
      tabela.append(this.parte('colgroup', 'colunas'), head, this.parte('tbody', 'corpo')); scroll.append(tabela);
      const status = this.parte('div', 'estado', 'tabela-estado'); status.setAttribute('role', 'status'); status.setAttribute('aria-live', 'polite');
      painel.append(scroll, status);
      if (this.recursos.paginacao || this.config.slots?.rodape) {
        const rodape = criar('footer', 'lista-rodape');
        if (this.recursos.paginacao) {
          const b = this.parte('button', 'pagina-texto'); b.type = 'button'; b.setAttribute('aria-expanded', 'false'); b.setAttribute('aria-controls', `${this.id}-menu-pagina`);
          const menu = this.parte('div', 'menu-pagina', 'menu menu-pagina'); menu.hidden = true;
          const pag = this.parte('nav', 'paginacao'); pag.setAttribute('aria-label', 'Paginação'); rodape.append(b, menu, pag);
        }
        this.slot(rodape, 'rodape'); painel.append(rodape);
      }
      this.raiz.replaceChildren(chips);
      if (comandos.childNodes.length || this.recursos.buscaIA) this.raiz.append(toolbar);
      this.raiz.append(painel);
    }

    restaurar() {
      if (!this.recursos.persistencia) return;
      try {
        const layout = JSON.parse(localStorage.getItem(`${this.storageId}-layout`) || 'null');
        const larguras = layout?.larguras || JSON.parse(localStorage.getItem(`${this.storageId}-larguras`) || '{}');
        this.colunas.forEach(c => { if (Number.isFinite(larguras[c.id])) this.estado.larguras[c.id] = this.limitarLargura(c.id, larguras[c.id]); });
        if (Array.isArray(layout?.visiveis)) {
          this.estado.visiveis = [...new Set(layout.visiveis.filter(id => this.coluna(id)))];
          this.colunas.filter(c => c.ocultavel === false).forEach(c => { if (!this.estado.visiveis.includes(c.id)) this.estado.visiveis.push(c.id); });
        }
        if (this.recursos.filtrosSalvos) this.estado.salvos = JSON.parse(localStorage.getItem(`${this.storageId}-filtros`) || '[]').filter(s => this.coluna(s.coluna)).map(s => ({ ...s, filtro: s.filtro ?? s.filtros?.[s.coluna] }));
      } catch { /* Armazenamento indisponível ou conteúdo antigo inválido não impede a tabela. */ }
    }
    persistir() {
      if (!this.recursos.persistencia) return;
      try {
        localStorage.setItem(`${this.storageId}-layout`, JSON.stringify({ larguras: this.estado.larguras, visiveis: this.estado.visiveis }));
        localStorage.setItem(`${this.storageId}-larguras`, JSON.stringify(this.estado.larguras));
        if (this.recursos.filtrosSalvos) localStorage.setItem(`${this.storageId}-filtros`, JSON.stringify(this.estado.salvos));
      } catch { /* Navegação continua sem persistência. */ }
    }
    valor(item, c) { return c.valor ? c.valor(item) : item[c.id]; }
    formatado(item, id) {
      const c = this.coluna(id), valor = this.valor(item, c);
      if (c.formatar) return c.formatar(valor, item);
      if (c.tipo === 'data' && valor) {
        const data = new Date(valor);
        return Number.isNaN(data.getTime()) ? String(valor) : new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(data);
      }
      return valor;
    }
    corresponde(item, filtros) {
      return Object.entries(filtros).every(([id, filtro]) => {
        const c = this.coluna(id);
        if (!c || filtro == null) return true;
        const valor = String(this.formatado(item, id) ?? '');
        if (Array.isArray(filtro)) return !filtro.length || filtro.includes(valor);
        if (typeof filtro === 'object') {
          const converter = v => c.tipo === 'data' ? new Date(v).getTime() : Number(v);
          const atual = converter(this.valor(item, c));
          const inicio = filtro.from !== '' && filtro.from != null, fim = filtro.to !== '' && filtro.to != null;
          // Datas finais incluem o dia inteiro, sem depender do nome do campo.
          const ate = converter(filtro.to) + (c.tipo === 'data' && /^\d{4}-\d{2}-\d{2}$/.test(filtro.to) ? 86399999 : 0);
          return (!inicio || atual >= converter(filtro.from)) && (!fim || atual <= ate);
        }
        return valor.toLocaleLowerCase('pt-BR').includes(String(filtro).toLocaleLowerCase('pt-BR'));
      });
    }
    dados() {
      if (this.config.modo === 'servidor') return [...this.estado.todos];
      const itens = this.estado.todos.filter(item => this.corresponde(item, this.estado.filtros));
      if (this.estado.ordem) {
        const { id, dir } = this.estado.ordem, c = this.coluna(id);
        itens.sort((a, b) => {
          const converter = item => c.tipo === 'data' ? new Date(this.valor(item, c)).getTime() : c.tipo === 'numero' ? Number(this.valor(item, c)) : this.formatado(item, id);
          const x = converter(a), y = converter(b);
          return (typeof x === 'number' ? x - y : String(x ?? '').localeCompare(String(y ?? ''), 'pt-BR', { numeric: true })) * (dir === 'asc' ? 1 : -1);
        });
      }
      return itens;
    }
    idsVisiveis() { return [...(this.recursos.selecao ? ['selecao'] : []), ...(this.recursos.acoes ? ['acoes'] : []), ...this.estado.visiveis]; }
    aplicarLarguras() {
      const grupo = this.no('colunas'), ids = this.idsVisiveis(); grupo.replaceChildren();
      ids.forEach(id => { const col = criar('col'); col.dataset.colunaId = id; col.style.width = `${this.estado.larguras[id]}px`; grupo.append(col); });
      this.no('tabela').style.width = `${ids.reduce((s, id) => s + this.estado.larguras[id], 0)}px`;
    }
    alca(id, nome) {
      const alca = criar('span', 'alca-coluna'); alca.tabIndex = 0;
      alca.setAttribute('role', 'separator'); alca.setAttribute('aria-orientation', 'vertical'); alca.setAttribute('aria-label', `Redimensionar ${nome}`);
      const c = this.coluna(id); alca.setAttribute('aria-valuemin', c?.larguraMinima ?? 80); alca.setAttribute('aria-valuemax', c?.larguraMaxima ?? 600);
      const mudar = largura => {
        this.estado.larguras[id] = this.limitarLargura(id, largura); this.aplicarLarguras();
        alca.setAttribute('aria-valuenow', this.estado.larguras[id]);
      };
      alca.setAttribute('aria-valuenow', this.estado.larguras[id]);
      alca.addEventListener('pointerdown', evento => {
        evento.preventDefault(); evento.stopPropagation(); this.fecharMenus(); alca.setPointerCapture(evento.pointerId);
        const inicio = evento.clientX, largura = this.estado.larguras[id];
        const mover = e => mudar(largura + e.clientX - inicio);
        const parar = () => { alca.removeEventListener('pointermove', mover); alca.removeEventListener('pointerup', parar); alca.removeEventListener('pointercancel', parar); this.persistir(); };
        alca.addEventListener('pointermove', mover); alca.addEventListener('pointerup', parar); alca.addEventListener('pointercancel', parar);
      });
      alca.addEventListener('keydown', e => {
        if (!['ArrowLeft', 'ArrowRight'].includes(e.key)) return;
        e.preventDefault(); mudar(this.estado.larguras[id] + (e.key === 'ArrowRight' ? 10 : -10)); this.persistir();
      });
      return alca;
    }

    fecharMenus() {
      this.raiz.querySelectorAll('.menu').forEach(m => m.hidden = true);
      this.raiz.querySelectorAll('[aria-expanded]').forEach(b => b.setAttribute('aria-expanded', 'false'));
      if (this.menuHeader) { this.menuHeader.ancora.setAttribute('aria-expanded', 'false'); this.menuHeader.menu.remove(); this.menuHeader = null; }
    }
    posicionar(menu, ancora) {
      if (!menu || menu.hidden) return;
      Object.assign(menu.style, { position: 'fixed', right: 'auto', bottom: 'auto', overflowY: 'auto', maxWidth: `${innerWidth - 24}px`, zIndex: '1000' });
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
      const menu = this.no('menu-colunas'), b = this.no('colunas-botao'), aberto = !menu.hidden;
      this.fecharMenus(); if (aberto) return;
      const rascunho = new Set(this.estado.visiveis), busca = criar('input'), lista = criar('div'); busca.placeholder = 'Buscar colunas'; busca.setAttribute('aria-label', 'Buscar colunas');
      const preencher = () => {
        lista.replaceChildren(); this.colunas.filter(c => c.nome.toLocaleLowerCase().includes(busca.value.toLocaleLowerCase())).forEach(c => {
          const label = criar('label'), check = criar('input'); check.type = 'checkbox'; check.checked = rascunho.has(c.id); check.disabled = c.ocultavel === false;
          check.addEventListener('change', () => check.checked ? rascunho.add(c.id) : rascunho.delete(c.id)); label.append(check, criar('span', '', c.nome)); lista.append(label);
        });
      };
      busca.addEventListener('input', preencher); preencher();
      menu.replaceChildren(busca, lista, this.botao('Restaurar', () => { rascunho.clear(); this.colunas.filter(c => c.visivel !== false || c.ocultavel === false).forEach(c => rascunho.add(c.id)); preencher(); }), this.botao('Aplicar', () => this.definirColunas([...rascunho]), 'primaria'));
      menu.hidden = false; b.setAttribute('aria-expanded', 'true'); this.posicionar(menu, b);
    }
    abrirHeader(c, ancora) {
      const aberto = this.menuHeader?.ancora === ancora; this.fecharMenus(); if (aberto) return;
      const menu = criar('div', 'menu coluna-menu'); menu.dataset.tabelaInstancia = this.id;
      menu.setAttribute('role', 'dialog'); menu.setAttribute('aria-label', `Opções da coluna ${c.nome}`);
      if (this.permitido('ordenacao', c.id)) {
        menu.append(this.botao('Ordenar crescente', () => this.ordenar(c.id, 'asc')), this.botao('Ordenar decrescente', () => this.ordenar(c.id, 'desc')));
      }
      if (this.permitido('reordenacao', c.id)) {
        for (const [rotulo, inicio] of [['Mover para o início', true], ['Mover para o fim', false]]) menu.append(this.botao(rotulo, () => {
          const ids = this.estado.visiveis.filter(id => id !== c.id); inicio ? ids.unshift(c.id) : ids.push(c.id); this.estado.visiveis = ids; this.persistir(); this.render();
        }));
      }
      if (this.permitido('filtrosHeader', c.id)) {
        menu.append(criar('hr'));
        if (this.recursos.filtrosSalvos) {
          menu.append(criar('p', '', 'FILTROS SALVOS'));
          this.estado.salvos.filter(s => s.coluna === c.id).forEach(s => menu.append(this.botao(s.nome, () => this.filtrar(c.id, copiar(s.filtro)))));
          menu.append(this.botao('Salvar filtro atual', () => {
            if (!this.estado.filtros[c.id]) return;
            const nome = prompt('Nome do filtro:'); if (!nome?.trim()) return;
            this.estado.salvos.push({ nome: nome.trim().slice(0, 80), coluna: c.id, filtro: copiar(this.estado.filtros[c.id]) }); this.persistir(); this.fecharMenus();
          }));
        }
        if (this.config.modo === 'servidor' || ['numero', 'data'].includes(c.tipo)) {
          const de = criar('input'), ate = criar('input');
          if (['numero', 'data'].includes(c.tipo)) {
            de.type = ate.type = c.tipo === 'data' ? 'date' : 'number'; de.setAttribute('aria-label', 'De'); ate.setAttribute('aria-label', 'Até');
            de.value = this.estado.filtros[c.id]?.from ?? ''; ate.value = this.estado.filtros[c.id]?.to ?? '';
            menu.append(de, ate, this.botao('Filtrar', () => this.filtrar(c.id, { from: de.value, to: ate.value })));
          } else {
            de.setAttribute('aria-label', `Filtrar ${c.nome}`); de.placeholder = 'Contém...'; de.value = typeof this.estado.filtros[c.id] === 'string' ? this.estado.filtros[c.id] : '';
            menu.append(de, this.botao('Filtrar', () => this.filtrar(c.id, de.value)));
          }
        } else {
          const busca = criar('input'), lista = criar('div', 'valor-lista'); busca.placeholder = 'Buscar valores'; busca.setAttribute('aria-label', `Buscar valores de ${c.nome}`);
          const selecionados = new Set(Array.isArray(this.estado.filtros[c.id]) ? this.estado.filtros[c.id] : []);
          const preencher = () => {
            lista.replaceChildren(); const contagens = new Map();
            this.estado.todos.forEach(item => { const v = String(this.formatado(item, c.id) ?? ''); contagens.set(v, (contagens.get(v) || 0) + 1); });
            [...contagens].filter(([v]) => v.toLocaleLowerCase().includes(busca.value.toLocaleLowerCase())).sort(([a], [b]) => a.localeCompare(b)).forEach(([v, n]) => {
              const label = criar('label'), check = criar('input'); check.type = 'checkbox'; check.checked = selecionados.has(v);
              check.addEventListener('change', () => check.checked ? selecionados.add(v) : selecionados.delete(v)); label.append(check, criar('span', '', `${v} (${n})`)); lista.append(label);
            });
          };
          busca.addEventListener('input', preencher); preencher(); menu.append(busca, lista, this.botao('Aplicar filtro', () => this.filtrar(c.id, [...selecionados])));
        }
        menu.append(this.botao('Limpar filtro desta coluna', () => this.filtrar(c.id, null)), this.botao('Limpar todos os filtros', () => this.limparFiltros()));
      }
      document.body.append(menu); this.menuHeader = { menu, ancora }; ancora.setAttribute('aria-expanded', 'true'); this.posicionar(menu, ancora);
    }

    linha(item) {
      const tr = criar('tr');
      if (this.recursos.selecao) {
        const td = criar('td', `coluna-selecao${this.config.acoesFixas ? ' coluna-selecao-fixa' : ''}`), check = criar('input'); check.type = 'checkbox';
        check.setAttribute('aria-label', `Selecionar ${this.chave(item)}`); check.checked = this.estado.selecionados.has(this.chave(item));
        check.addEventListener('change', () => { check.checked ? this.estado.selecionados.add(this.chave(item)) : this.estado.selecionados.delete(this.chave(item)); this.config.onSelecao?.(this.obterSelecionados(), this); this.render(); }); td.append(check); tr.append(td);
      }
      if (this.recursos.acoes) {
        const td = criar('td', this.config.acoesFixas ? 'coluna-acoes-fixa' : '');
        this.config.renderAcoes?.(item, td, this.estado, () => this.render(), this.api.bind(this), this); tr.append(td);
      }
      this.estado.visiveis.forEach(id => {
        const c = this.coluna(id), td = criar('td'); td.title = String(this.formatado(item, id) ?? '');
        if (c.render) c.render(item, td, this);
        else if (this.config.renderCelula) this.config.renderCelula(item, id, td, this.estado, () => this.render(), this.api.bind(this), this);
        else td.textContent = String(this.formatado(item, id) ?? '');
        if (this.permitido('edicao', id) && c.editavel !== false) {
          td.classList.add('af-editavel'); td.addEventListener('dblclick', e => {
            if (!e.target.closest('button,a,input,select,textarea') && !window.getSelection()?.toString()) this.editar(item, c, td);
          });
        }
        tr.append(td);
      });
      tr.addEventListener('click', e => {
        if (e.target.closest('.btn-ver-linha') && this.recursos.detalhes) this.detalhes(item);
        else if (e.target.closest('.btn-editar-linha') && this.recursos.edicao) this.detalhes(item, true);
        else if (!e.target.closest('button,a,input,select,textarea') && !window.getSelection()?.toString()) this.config.onRowClick?.(item, this.estado, () => this.render(), this);
      });
      return tr;
    }
    async salvar(item, alteracoes) {
      try {
        const resultado = await this.config.salvar(item, alteracoes, this);
        if (this.destruida) return false;
        Object.assign(item, resultado || alteracoes); this.render(); this.config.toast?.('Alterações salvas.'); return true;
      } catch (erro) { this.config.toast?.(erro.message, true); const status = this.no('estado'); if (status) status.textContent = erro.message; return false; }
    }
    editar(item, c, td) {
      const input = criar('input'); input.type = c.tipo === 'numero' ? 'number' : 'text'; input.value = this.valor(item, c) ?? '';
      input.setAttribute('aria-label', `Editar ${c.nome}`); td.replaceChildren(input); input.focus();
      let salvando = false;
      input.addEventListener('keydown', async e => {
        if (e.key === 'Escape') { e.stopPropagation(); this.render(); }
        if (e.key === 'Enter' && !salvando) {
          e.preventDefault(); salvando = true; input.disabled = true;
          const valor = c.tipo === 'numero' ? Number(input.value) : input.value;
          if (!await this.salvar(item, { [c.id]: valor })) { salvando = false; input.disabled = false; input.focus(); }
        }
      });
      // Sair da célula cancela: nenhuma gravação implícita ao clicar fora.
      input.addEventListener('blur', () => { if (!salvando && !this.destruida) this.render(); });
    }
    detalhes(item, editar = false) {
      if ((!editar && !this.recursos.detalhes) || (editar && !this.recursos.edicao)) return;
      this.dialogo?.remove(); const dialog = criar('dialog', 'modal-exportacao af-detalhes'); dialog.dataset.tabelaInstancia = this.id; this.dialogo = dialog;
      dialog.setAttribute('aria-label', editar ? 'Editar registro' : 'Detalhes do registro');
      dialog.append(criar('h2', '', editar ? 'Editar registro' : 'Detalhes do registro'));
      const campos = new Map();
      this.colunas.forEach(c => {
        const label = criar('label', 'af-campo', c.nome);
        if (editar && this.permitido('edicao', c.id) && c.editavel !== false) {
          const input = criar('input'); input.type = c.tipo === 'numero' ? 'number' : 'text'; input.value = this.valor(item, c) ?? ''; campos.set(c.id, input); label.append(input);
        } else label.append(criar('div', '', this.formatado(item, c.id)));
        dialog.append(label);
      });
      const fechar = this.botao('Fechar', () => dialog.close()); dialog.append(fechar);
      if (editar) dialog.append(this.botao('Salvar alterações', async e => {
        e.currentTarget.disabled = true;
        const alteracoes = Object.fromEntries([...campos].map(([id, input]) => [id, this.coluna(id).tipo === 'numero' ? Number(input.value) : input.value]));
        if (await this.salvar(item, alteracoes)) dialog.close(); else e.target.disabled = false;
      }, 'primaria'));
      dialog.addEventListener('close', () => { dialog.remove(); if (this.dialogo === dialog) this.dialogo = null; }); document.body.append(dialog); dialog.showModal();
    }

    render() {
      if (this.destruida) return;
      this.fecharMenus(); this.aplicarLarguras();
      const itens = this.dados();
      if (this.config.modo !== 'servidor') this.estado.total = itens.length;
      const totalPaginas = this.recursos.paginacao ? Math.max(1, Math.ceil(this.estado.total / this.estado.tamanho)) : 1;
      this.estado.pagina = Math.max(1, Math.min(this.estado.pagina, totalPaginas));
      const head = this.no('cabecalho'); head.replaceChildren();
      if (this.recursos.selecao) {
        const th = criar('th', this.config.acoesFixas ? 'coluna-selecao-fixa' : ''), check = criar('input'); check.type = 'checkbox'; check.setAttribute('aria-label', 'Selecionar os registros carregados e filtrados');
        const selecionados = itens.filter(item => this.estado.selecionados.has(this.chave(item))).length;
        check.checked = itens.length > 0 && selecionados === itens.length; check.indeterminate = selecionados > 0 && selecionados < itens.length;
        check.addEventListener('change', () => { itens.forEach(item => check.checked ? this.estado.selecionados.add(this.chave(item)) : this.estado.selecionados.delete(this.chave(item))); this.config.onSelecao?.(this.obterSelecionados(), this); this.render(); }); th.append(check); head.append(th);
      }
      if (this.recursos.acoes) {
        const th = criar('th', this.config.acoesFixas ? 'coluna-acoes-fixa' : ''); th.append(criar('span', 'titulo-coluna titulo-coluna-fixa', this.textos.acoes));
        if (this.recursos.redimensionamento) th.append(this.alca('acoes', this.textos.acoes)); head.append(th);
      }
      this.estado.visiveis.forEach(id => {
        const c = this.coluna(id), th = criar('th'); th.scope = 'col';
        const interativo = ['ordenacao', 'filtrosHeader', 'reordenacao'].some(r => this.permitido(r, id));
        const rotulo = `${c.nome}${this.estado.ordem?.id === id ? (this.estado.ordem.dir === 'asc' ? ' ▲' : ' ▼') : ''}`;
        const titulo = interativo ? this.botao(rotulo, () => this.abrirHeader(c, titulo), 'titulo-coluna') : criar('span', 'titulo-coluna titulo-coluna-fixa', rotulo);
        if (interativo) { titulo.setAttribute('aria-label', `Opções da coluna ${c.nome}`); titulo.setAttribute('aria-expanded', 'false'); }
        if (this.estado.filtros[id] != null) titulo.classList.add('filtrada');
        th.append(titulo); if (this.permitido('redimensionamento', id)) th.append(this.alca(id, c.nome)); head.append(th);
      });
      const pagina = this.config.modo === 'servidor' || !this.recursos.paginacao ? itens : itens.slice((this.estado.pagina - 1) * this.estado.tamanho, this.estado.pagina * this.estado.tamanho);
      this.no('corpo').replaceChildren(...pagina.map(item => this.linha(item)));
      const status = this.no('estado'); if (status) status.textContent = this.estado.carregando ? this.textos.carregando : !this.estado.todos.length ? this.textos.vazio : !itens.length ? this.textos.semResultados : '';
      const pt = this.no('pagina-texto'); if (pt) pt.textContent = this.textos.pagina({ pagina: this.estado.pagina, totalPaginas, tamanho: this.estado.tamanho, total: this.estado.total });
      const pag = this.no('paginacao');
      if (pag && this.recursos.paginacao) {
        const anterior = this.botao('←', () => this.irParaPagina(this.estado.pagina - 1)); anterior.disabled = this.estado.pagina <= 1;
        const proximo = this.botao('→', () => this.irParaPagina(this.estado.pagina + 1)); proximo.disabled = this.estado.pagina >= totalPaginas;
        const botoes = []; const inicio = Math.max(1, Math.min(this.estado.pagina - 2, totalPaginas - 4));
        for (let p = inicio; p <= Math.min(totalPaginas, inicio + 4); p++) {
          const b = this.botao(p, () => this.irParaPagina(p), p === this.estado.pagina ? 'atual' : ''); if (p === this.estado.pagina) b.setAttribute('aria-current', 'page'); botoes.push(b);
        }
        pag.replaceChildren(anterior, ...botoes, proximo);
      }
      const chips = this.no('chips');
      if (chips) {
        chips.replaceChildren(); Object.entries(this.estado.filtros).forEach(([id, filtro]) => {
          const valor = Array.isArray(filtro) ? filtro.join(', ') : typeof filtro === 'object' ? `${filtro.from ?? '…'} a ${filtro.to ?? '…'}` : filtro;
          const chip = criar('span', 'chip', `${this.coluna(id)?.nome}: ${valor}`); chip.append(this.botao('✕', () => this.filtrar(id, null), 'icone')); chips.append(chip);
        });
      }
      this.config.onRender?.(this);
    }

    vincular() {
      this.ouvir(this.no('colunas-botao'), 'click', () => this.abrirColunas());
      this.ouvir(this.no('pagina-texto'), 'click', () => {
        const menu = this.no('menu-pagina'), b = this.no('pagina-texto'), aberto = !menu.hidden; this.fecharMenus(); if (aberto) return;
        const tamanhos = criar('div', 'tamanhos-pagina');
        (this.config.tamanhosPagina || [5, 10, 50, 100]).forEach(n => tamanhos.append(this.botao(n, () => this.definirTamanhoPagina(n))));
        const numero = criar('input'); numero.type = 'number'; numero.min = 1; numero.max = Math.max(1, Math.ceil(this.estado.total / this.estado.tamanho)); numero.value = this.estado.pagina; numero.setAttribute('aria-label', 'Número da página');
        menu.replaceChildren(criar('p', '', 'LINHAS POR PÁGINA'), tamanhos, criar('p', '', 'IR PARA A PÁGINA'), numero, this.botao('Ir', () => this.irParaPagina(Number(numero.value))));
        menu.hidden = false; b.setAttribute('aria-expanded', 'true'); this.posicionar(menu, b);
      });
      this.ouvir(document, 'pointerdown', e => {
        const pares = [['menu-colunas', 'colunas-botao'], ['menu-pagina', 'pagina-texto']].map(([m, b]) => ({ menu: this.no(m), ancora: this.no(b) }));
        if (this.menuHeader) pares.push(this.menuHeader);
        pares.forEach(({ menu, ancora }) => { if (menu && !menu.hidden && !menu.contains(e.target) && !ancora?.contains(e.target)) { menu.hidden = true; ancora?.setAttribute('aria-expanded', 'false'); if (menu === this.menuHeader?.menu) { menu.remove(); this.menuHeader = null; } } });
      });
      this.ouvir(document, 'keydown', e => {
        if (e.key === 'Escape' && (this.raiz.contains(e.target) || this.menuHeader?.menu.contains(e.target))) this.fecharMenus();
      });
      this.ouvir(window, 'resize', () => this.reposicionar()); this.ouvir(window, 'scroll', () => this.reposicionar(), { capture: true });
      if (this.recursos.buscaIA) {
        const form = this.no('busca-form') || document.getElementById(this.config.buscaFormId || this.config.iaFormId);
        this.ouvir(form, 'submit', e => { e.preventDefault(); const consulta = form.querySelector('input').value.trim(); if (consulta) this.buscarIA(consulta); });
      }
    }
    atualizar() { this.fecharMenus(); return this.config.modo === 'servidor' ? this.carregar() : this.render(); }
    filtrar(id, filtro) {
      if (!this.permitido('filtrosHeader', id)) throw new Error(`Filtro desativado para ${id}.`);
      if (filtro == null || filtro === '' || (Array.isArray(filtro) && !filtro.length)) delete this.estado.filtros[id]; else this.estado.filtros[id] = copiar(filtro);
      this.estado.pagina = 1; return this.atualizar();
    }
    limparFiltros() { this.estado.filtros = {}; this.estado.busca = []; this.estado.pagina = 1; return this.atualizar(); }
    ordenar(id, dir = 'asc') {
      if (!this.permitido('ordenacao', id) || !['asc', 'desc'].includes(dir)) throw new Error(`Ordenação inválida ou desativada para ${id}.`);
      this.estado.ordem = { id, dir }; this.estado.pagina = 1; return this.atualizar();
    }
    definirColunas(ids) {
      if (!this.recursos.selecionarColunas) throw new Error('Seletor de colunas desativado.');
      if (ids.some(id => !this.coluna(id))) throw new Error('Coluna desconhecida.');
      const fixas = this.colunas.filter(c => c.ocultavel === false).map(c => c.id);
      this.estado.visiveis = [...new Set([...ids, ...fixas])]; this.persistir(); this.render();
    }
    irParaPagina(pagina) {
      if (!this.recursos.paginacao) return;
      this.estado.pagina = Math.max(1, Math.min(Math.max(1, Math.ceil(this.estado.total / this.estado.tamanho)), Math.floor(Number(pagina)) || 1)); return this.atualizar();
    }
    definirTamanhoPagina(tamanho) { this.estado.tamanho = this.tamanhoValido(tamanho); this.estado.pagina = 1; return this.atualizar(); }
    definirDados(itens) {
      if (this.config.modo === 'servidor') throw new Error('No modo servidor, forneça os dados por carregarDados ou endpoint.');
      if (!Array.isArray(itens)) throw new Error('FirmaTabela: dados deve ser um array.');
      this.validarChaves(itens); this.estado.todos = [...itens];
      const chaves = new Set(itens.map(item => this.chave(item))); this.estado.selecionados = new Set([...this.estado.selecionados].filter(id => chaves.has(id)));
      this.render();
    }
    validarChaves(itens) {
      if (!this.recursos.selecao) return;
      const chaves = itens.map(item => this.chave(item));
      if (chaves.some(id => id == null) || new Set(chaves).size !== chaves.length) throw new Error('FirmaTabela: seleção requer uma chave única por registro.');
    }
    obterSelecionados() { return [...this.estado.selecionados]; }
    limparSelecao() { this.estado.selecionados.clear(); this.config.onSelecao?.([], this); this.render(); }
    getEstado() { return this.estado; } // Compatibilidade; prefira os métodos públicos para alterações.
    api(path = '', options = {}) { return this.requisitar(`${this.config.endpoint || ''}${path}`, options); }
    async requisitar(url, options = {}) {
      const headers = new Headers(this.config.credenciais?.() || this.config.headers || {});
      new Headers(options.headers || {}).forEach((v, k) => headers.set(k, v));
      if (typeof options.body === 'string' && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
      const resposta = await fetch(url, { ...options, headers });
      if (!resposta.ok) {
        let detalhe = {}; try { detalhe = await resposta.json(); } catch { /* Resposta sem JSON. */ }
        throw new Error(detalhe.mensagem || `Falha na requisição (${resposta.status}).`);
      }
      return resposta.status === 204 ? null : resposta.json();
    }
    async carregar() {
      if (this.destruida) return false;
      const versao = ++this.versao; this.requisicao?.abort(); this.requisicao = new AbortController();
      this.estado.carregando = true; this.render();
      try {
        const consulta = { pagina: this.estado.pagina, tamanho: this.estado.tamanho, ordem: copiar(this.estado.ordem), filtros: copiar(this.estado.filtros), signal: this.requisicao.signal };
        let resposta;
        if (this.config.carregarDados) resposta = await this.config.carregarDados(consulta, this);
        else {
          let path = '';
          if (this.config.modo === 'servidor') {
            const params = this.config.parametros ? this.config.parametros(consulta) : { pagina: consulta.pagina, tamanho: consulta.tamanho, ordem: JSON.stringify(consulta.ordem), filtros: JSON.stringify(consulta.filtros) };
            path = `${String(this.config.endpoint).includes('?') ? '&' : '?'}${new URLSearchParams(params)}`;
          }
          resposta = await this.api(path, { signal: consulta.signal });
        }
        if (versao !== this.versao || this.destruida) return false;
        if (this.config.mapearResposta) resposta = this.config.mapearResposta(resposta);
        const itens = this.config.modo === 'servidor' ? resposta?.itens : resposta;
        if (!Array.isArray(itens)) throw new Error('Resposta inválida: esperado um array, ou { itens, total } no modo servidor.');
        if (this.config.modo === 'servidor' && (!Number.isInteger(resposta.total) || resposta.total < 0)) throw new Error('Resposta inválida: total deve ser inteiro não negativo.');
        this.validarChaves(itens); this.estado.todos = [...itens]; this.estado.carregando = false;
        if (this.config.modo === 'servidor') {
          this.estado.total = resposta.total;
          const ultima = Math.max(1, Math.ceil(resposta.total / this.estado.tamanho));
          if (this.estado.pagina > ultima) { this.estado.pagina = ultima; return this.carregar(); }
        } else {
          const ids = new Set(itens.map(item => this.chave(item))); this.estado.selecionados = new Set([...this.estado.selecionados].filter(id => ids.has(id)));
        }
        this.render(); this.config.onCarregar?.(this); return true;
      } catch (erro) {
        if (versao !== this.versao || this.destruida || erro.name === 'AbortError') return false;
        this.estado.carregando = false; this.render(); this.no('estado').textContent = erro.message; this.config.toast?.(erro.message, true); this.config.onErro?.(erro, this); return false;
      }
    }
    async buscarIA(consulta) {
      if (!this.recursos.buscaIA || this.destruida) return;
      const painel = this.no('plano-busca'); painel.hidden = false; painel.replaceChildren(criar('p', '', 'Interpretando...'));
      const versao = this.versaoBusca = (this.versaoBusca || 0) + 1;
      this.requisicaoBusca?.abort(); this.requisicaoBusca = new AbortController();
      try {
        const plano = this.config.interpretarBusca ? await this.config.interpretarBusca(consulta, this) : await this.requisitar(this.config.buscaEndpoint || this.config.iaEndpoint || `${this.config.endpoint || ''}/plano-busca`, { method: 'POST', body: JSON.stringify({ consulta }), signal: this.requisicaoBusca.signal });
        if (this.destruida || versao !== this.versaoBusca) return;
        if (!Array.isArray(plano.steps) || plano.steps.some(e => !this.coluna(e.column) || !['sort', 'column-filter'].includes(e.op) || (e.op === 'sort' ? !this.permitido('ordenacao', e.column) || !['asc', 'desc'].includes(e.dir) : !this.permitido('filtrosHeader', e.column) || !['equals', 'contains', 'range'].includes(e.type)))) throw new Error('O plano contém operações ou colunas indisponíveis.');
        this.estado.plano = plano;
        const mostrar = () => {
          const lista = criar('ol'); plano.steps.forEach((e, i) => {
            const item = criar('li', '', e.op === 'sort' ? `Ordenar ${this.coluna(e.column).nome}: ${e.dir}` : `Filtrar ${this.coluna(e.column).nome}: ${e.value ?? `${e.from ?? '…'} a ${e.to ?? '…'}`}`);
            item.append(this.botao('✕', () => { plano.steps.splice(i, 1); mostrar(); }, 'icone')); lista.append(item);
          });
          painel.replaceChildren(criar('h2', '', 'PLANO DE BUSCA'), criar('p', '', plano.summary || consulta), lista, this.botao('Aplicar', () => {
            plano.steps.forEach(e => { if (e.op === 'sort') this.estado.ordem = { id: e.column, dir: e.dir }; else this.estado.filtros[e.column] = e.type === 'range' ? { from: e.from, to: e.to } : e.type === 'equals' ? [e.value] : e.value; });
            this.estado.pagina = 1; this.estado.plano = null; this.render();
          }, 'primaria'), this.botao('Cancelar', () => { painel.hidden = true; this.estado.plano = null; }));
        }; mostrar();
      } catch (erro) { if (!this.destruida && versao === this.versaoBusca) painel.replaceChildren(criar('p', '', erro.message)); }
    }
    destruir() {
      if (this.destruida) return;
      this.destruida = true; this.versao++; this.versaoBusca++; this.requisicao?.abort(); this.requisicaoBusca?.abort(); this.eventos.abort(); this.fecharMenus(); this.dialogo?.remove();
      montadas.delete(this.raiz); delete this.raiz.dataset.tabelaInstancia;
      if (this.config.elemento) this.raiz.replaceChildren();
    }
  }
  FirmaTabela.recursosPadrao = padroes;
  window.FirmaTabela = FirmaTabela;
  window.aFirmaTabela = config => new FirmaTabela(config);
})();
