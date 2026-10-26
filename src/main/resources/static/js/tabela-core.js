/**
 * Tabela Genérica aFIRMA (tabela-core.js)
 * Componente reutilizável para listagem, filtros, redimensionamento, paginação e busca.
 */
window.aFirmaTabela = function(config) {
  const $ = id => document.getElementById(id);
  const colunas = config.colunas;
  const padrao = colunas.map(c => c.id);
  
  const largura_padrao = 200;
  const largurasPadrao = { selecao: 34, acoes: 96 };
  colunas.forEach(c => largurasPadrao[c.id] = largura_padrao);
  
  const estado = {
    todos: [], visiveis: [...padrao], rascunho: [...padrao], filtros: {}, ordem: null, pagina: 1,
    tamanho: 10, larguras: { ...largurasPadrao }, aviso: null, plano: null, busca: [], salvos: [], selecionados: new Set()
  };
  
  let menuColunaAberto = null;
  const storageId = config.storageId || 'tabela-generica';
  
  try {
    const salvas = JSON.parse(localStorage.getItem(`${storageId}-larguras`) || '{}');
    Object.keys(largurasPadrao).forEach(id => {
      if (Number.isFinite(salvas[id])) estado.larguras[id] = Math.max(id === 'selecao' ? 34 : id === 'acoes' ? 86 : 80, Math.min(600, salvas[id]));
    });
  } catch {}

  const data = valor => valor ? new Intl.DateTimeFormat('pt-BR',{dateStyle:'short',timeStyle:'short'}).format(new Date(valor)) : '-';
  const texto = (elemento, valor) => { elemento.textContent = String(valor ?? ''); return elemento; };
  const el = (tag, classe, valor) => { const node = document.createElement(tag); if(classe) node.className = classe; if(valor !== undefined) texto(node, valor); return node; };
  const botao = (rotulo, funcao, classe='') => { const b = el('button', classe, rotulo); b.type = 'button'; b.addEventListener('click', funcao); return b; };
  const valorFormatado = (item, id) => {
    const col = colunas.find(c => c.id === id);
    if (!col) return item[id];
    if (col.formatar) return col.formatar(item[id], item);
    if (col.tipo === 'data') return data(item[id]);
    return item[id];
  };

  async function api(path, options={}) {
    const headers = { ...config.credenciais(), ...options.headers };
    if (options.body && typeof options.body === 'string') {
      headers['Content-Type'] = 'application/json';
    }
    const resposta = await fetch(`${config.endpoint}${path}`, { ...options, headers });
    if (!resposta.ok) {
      let detalhe = {};
      try { detalhe = await resposta.json(); } catch {}
      throw new Error(resposta.status === 401 || resposta.status === 403 ? 'Credenciais inválidas ou sem permissão.' : detalhe.mensagem || `Erro ${resposta.status} na operação.`);
    }
    return (resposta.status === 204 || options.method === 'DELETE') ? null : resposta.json();
  }

  function fecharMenus() {
    document.querySelectorAll('.menu').forEach(m => m.hidden = true);
    document.querySelectorAll('[aria-expanded]').forEach(b => b.setAttribute('aria-expanded', 'false'));
  }

  function posicionarMenuColunas() {
    const menu = $(`${config.containerId}-menu-colunas`);
    if (menu.hidden) return;
    const ancora = $(`${config.containerId}-colunas-botao`).getBoundingClientRect(), largura = menu.offsetWidth;
    menu.style.left = `${Math.max(12, Math.min(ancora.left, innerWidth - largura - 12))}px`;
    menu.style.top = `${ancora.bottom + 6}px`;
    menu.style.maxHeight = `${Math.max(120, innerHeight - ancora.bottom - 18)}px`;
  }

  function fecharMenuColuna() {
    if (!menuColunaAberto) return;
    menuColunaAberto.ancora.setAttribute('aria-expanded', 'false');
    menuColunaAberto.menu.remove();
    menuColunaAberto = null;
  }

  function posicionarMenuColuna() {
    if (!menuColunaAberto) return;
    const { menu, ancora } = menuColunaAberto, caixa = ancora.getBoundingClientRect();
    if (caixa.bottom < 0 || caixa.top > innerHeight) { fecharMenuColuna(); return; }
    const largura = Math.min(280, innerWidth - 24), abaixo = innerHeight - caixa.bottom - 18, acima = caixa.top - 18;
    menu.style.width = `${largura}px`;
    menu.style.left = `${Math.max(12, Math.min(caixa.left, innerWidth - largura - 12))}px`;
    if (abaixo < Math.min(menu.scrollHeight, 320) && acima > abaixo) {
      const altura = Math.min(menu.scrollHeight, acima);
      menu.style.top = `${Math.max(12, caixa.top - altura - 6)}px`;
      menu.style.maxHeight = `${Math.max(80, acima)}px`;
    } else {
      menu.style.top = `${caixa.bottom + 6}px`;
      menu.style.maxHeight = `${Math.max(80, abaixo)}px`;
    }
  }

  function corresponde(item, filtros) {
    return Object.entries(filtros).every(([id, f]) => {
      if (!f) return true;
      const atual = String(valorFormatado(item, id) ?? '');
      if (Array.isArray(f)) return !f.length || f.includes(atual);
      if (f.from !== undefined || f.to !== undefined) {
        const n = id === 'data' ? new Date(item[id]).getTime() : Number(item[id]);
        const de = id === 'data' ? new Date(f.from).getTime() : Number(f.from);
        const ate = id === 'data' ? new Date(f.to).getTime() : Number(f.to);
        return (!f.from || n >= de) && (!f.to || n <= ate);
      }
      return atual.toLocaleLowerCase('pt-BR').includes(String(f).toLocaleLowerCase('pt-BR'));
    });
  }

  function dados() {
    let itens = estado.todos.filter(item => corresponde(item, estado.filtros));
    if (estado.ordem) {
      const { id, dir } = estado.ordem;
      itens.sort((a, b) => {
        let x = id === 'data' ? new Date(a[id]).getTime() : valorFormatado(a, id);
        let y = id === 'data' ? new Date(b[id]).getTime() : valorFormatado(b, id);
        return (typeof x === 'number' ? x - y : String(x).localeCompare(String(y), 'pt-BR', { numeric: true })) * (dir === 'asc' ? 1 : -1);
      });
    }
    return itens;
  }

  function aplicarLarguras() {
    const ids = ['selecao', 'acoes', ...estado.visiveis], grupo = $(`${config.containerId}-colunas`);
    grupo.replaceChildren();
    ids.forEach(id => {
      const coluna = el('col');
      coluna.dataset.colunaId = id;
      coluna.style.width = `${estado.larguras[id]}px`;
      grupo.append(coluna);
    });
    $(`${config.containerId}-tabela`).style.width = `${ids.reduce((s, id) => s + estado.larguras[id], 0)}px`;
  }

  function alcaColuna(id, nome) {
    const alca = el('span', 'alca-coluna');
    alca.tabIndex = 0;
    alca.setAttribute('role', 'separator');
    alca.setAttribute('aria-orientation', 'vertical');
    alca.setAttribute('aria-label', `Redimensionar ${nome}`);
    alca.setAttribute('aria-valuemin', '80');
    alca.setAttribute('aria-valuemax', '600');
    const mudar = largura => {
      estado.larguras[id] = Math.max(id === 'acoes' ? 86 : 80, Math.min(600, largura));
      const coluna = [...$(`${config.containerId}-colunas`).children].find(c => c.dataset.colunaId === id);
      if (coluna) coluna.style.width = `${estado.larguras[id]}px`;
      $(`${config.containerId}-tabela`).style.width = `${['selecao', 'acoes', ...estado.visiveis].reduce((s, chave) => s + estado.larguras[chave], 0)}px`;
      alca.setAttribute('aria-valuenow', String(estado.larguras[id]));
    };
    alca.setAttribute('aria-valuenow', String(estado.larguras[id]));
    alca.addEventListener('pointerdown', evento => {
      evento.preventDefault(); evento.stopPropagation(); fecharMenuColuna();
      alca.setPointerCapture(evento.pointerId);
      const inicio = evento.clientX, largura = estado.larguras[id];
      const mover = e => mudar(largura + e.clientX - inicio);
      const parar = () => {
        alca.removeEventListener('pointermove', mover);
        alca.removeEventListener('pointerup', parar);
        alca.removeEventListener('pointercancel', parar);
        localStorage.setItem(`${storageId}-larguras`, JSON.stringify(estado.larguras));
      };
      alca.addEventListener('pointermove', mover);
      alca.addEventListener('pointerup', parar);
      alca.addEventListener('pointercancel', parar);
    });
    alca.addEventListener('keydown', evento => {
      if (evento.key !== 'ArrowLeft' && evento.key !== 'ArrowRight') return;
      evento.preventDefault();
      mudar(estado.larguras[id] + (evento.key === 'ArrowRight' ? 10 : -10));
      localStorage.setItem(`${storageId}-larguras`, JSON.stringify(estado.larguras));
    });
    return alca;
  }

  function menuColuna(col, ancora) {
    if (menuColunaAberto?.ancora === ancora) { fecharMenuColuna(); return; }
    fecharMenuColuna();
    const menu = el('div', 'menu coluna-menu');
    menu.setAttribute('role', 'dialog');
    menu.setAttribute('aria-label', `Opções da coluna ${col.nome}`);
    const ordenar = (dir, label) => menu.append(botao(label, () => { estado.ordem = { id: col.id, dir }; render(); }));
    ordenar('asc', col.tipo === 'numero' ? 'Menor → maior' : col.tipo === 'data' ? 'Antigo → recente' : 'A → Z');
    ordenar('desc', col.tipo === 'numero' ? 'Maior → menor' : col.tipo === 'data' ? 'Recente → antigo' : 'Z → A');
    menu.append(el('hr'));
    menu.append(botao('◧ Fix to the left', () => fixar(col.id, true)), botao('◨ Fix to the right', () => fixar(col.id, false)), el('hr'));
    menu.append(el('p', '', 'FILTROS SALVOS'));
    const salvos = estado.salvos.filter(s => s.coluna === col.id);
    if (!salvos.length) menu.append(el('p', '', '(Nenhum filtro salvo)'));
    salvos.forEach(s => {
      const label = el('label'), check = el('input');
      check.type = 'checkbox';
      check.checked = Boolean(s.ativo);
      check.addEventListener('change', () => {
        s.ativo = check.checked;
        estado.filtros[col.id] = check.checked ? structuredClone(s.filtros[col.id]) : undefined;
        persistirSalvos(); render();
      });
      label.append(check, el('span', '', `● ${s.nome}`));
      label.style.color = s.cor;
      menu.append(label);
    });
    menu.append(botao('▣ Salvar filtro atual', () => salvarFiltro(col.id)), el('hr'));
    
    if (['numero', 'data'].includes(col.tipo)) {
      const de = el('input'), ate = el('input');
      de.type = col.tipo === 'data' ? 'date' : 'number'; ate.type = de.type;
      de.placeholder = 'From'; ate.placeholder = 'To';
      const f = estado.filtros[col.id] || {}; de.value = f.from || ''; ate.value = f.to || '';
      menu.append(de, ate, botao('⌕ SEARCH', () => { estado.filtros[col.id] = { from: de.value, to: ate.value }; estado.pagina = 1; menu.remove(); render(); }));
    } else {
      const busca = el('input'); busca.placeholder = '⌕ Buscar valores'; menu.append(busca);
      const lista = el('div', 'valor-lista'); menu.append(lista);
      const preencher = () => {
        lista.replaceChildren();
        const contagens = new Map();
        estado.todos.forEach(item => {
          const v = String(valorFormatado(item, col.id) ?? '');
          contagens.set(v, (contagens.get(v) || 0) + 1);
        });
        [...contagens].filter(([v]) => v.toLowerCase().includes(busca.value.toLowerCase())).sort((a, b) => a[0].localeCompare(b[0])).forEach(([v, n]) => {
          const label = el('label'), check = el('input'); check.type = 'checkbox';
          check.checked = (estado.filtros[col.id] || []).includes(v);
          check.addEventListener('change', () => {
            const s = new Set(estado.filtros[col.id] || []);
            check.checked ? s.add(v) : s.delete(v);
            estado.filtros[col.id] = [...s]; estado.pagina = 1; render();
          });
          label.append(check, el('span', '', `${v} (${n})`)); lista.append(label);
        });
      };
      busca.addEventListener('input', preencher); preencher();
    }
    menu.append(el('hr'), botao('⊘ Limpar todos os filtros', () => { estado.filtros = {}; estado.busca = []; estado.pagina = 1; estado.salvos.forEach(s => s.ativo = false); persistirSalvos(); render(); }));
    document.body.append(menu); menuColunaAberto = { menu, ancora }; ancora.setAttribute('aria-expanded', 'true'); posicionarMenuColuna();
  }

  function fixar(id, esquerda) {
    estado.visiveis = estado.visiveis.filter(x => x !== id);
    esquerda ? estado.visiveis.unshift(id) : estado.visiveis.push(id);
    render();
  }

  function persistirSalvos() { localStorage.setItem(`${storageId}-filtros`, JSON.stringify(estado.salvos)); }
  
  function salvarFiltro(coluna) {
    if (!estado.filtros[coluna]) { config.toast?.('Crie um filtro nesta coluna antes de salvar.'); return; }
    const nome = prompt('Nome do filtro:'); if (!nome?.trim()) return;
    const cores = ['#C9FF3D', '#E8792B', '#A9C2AC'];
    estado.salvos.push({ nome: nome.trim().slice(0, 40), coluna, cor: cores[estado.salvos.length % cores.length], filtros: { [coluna]: structuredClone(estado.filtros[coluna]) }, ativo: true });
    persistirSalvos(); render();
  }

  function menuColunas() {
    const menu = $(`${config.containerId}-menu-colunas`); menu.replaceChildren();
    const busca = el('input'); busca.placeholder = '⌕ Buscar colunas'; menu.append(busca);
    const grupo = el('div'); menu.append(grupo);
    function preencher() {
      grupo.replaceChildren();
      colunas.filter(c => c.nome.toLowerCase().includes(busca.value.toLowerCase())).forEach(c => {
        const label = el('label'), check = el('input'); check.type = 'checkbox';
        check.checked = estado.rascunho.includes(c.id);
        check.addEventListener('change', () => { estado.rascunho = check.checked ? [...estado.rascunho, c.id] : estado.rascunho.filter(x => x !== c.id); });
        label.append(check, el('span', '', c.nome)); grupo.append(label);
      });
    }
    busca.addEventListener('input', preencher); preencher();
    menu.append(el('hr'), botao('↺', () => { estado.rascunho = [...padrao]; preencher(); }), botao('✓ UPDATE', () => { estado.visiveis = padrao.filter(id => estado.rascunho.includes(id)); fecharMenus(); render(); }, 'primaria'));
  }

  function linhaDefault(item) {
    const tr = el('tr');
    
    const selecao = el('td', 'coluna-selecao');
    if (config.acoesFixas) selecao.classList.add('coluna-selecao-fixa');
    const checkbox = el('input'); checkbox.type = 'checkbox';
    checkbox.checked = estado.selecionados.has(item.id);
    checkbox.addEventListener('change', () => {
      if (checkbox.checked) estado.selecionados.add(item.id); else estado.selecionados.delete(item.id);
      render();
    });
    selecao.append(checkbox); tr.append(selecao);
    
    const acoes = el('td');
    if (config.acoesFixas) acoes.classList.add('coluna-acoes-fixa');
    if (config.renderAcoes) {
      config.renderAcoes(item, acoes, estado, render, api);
    }
    tr.append(acoes);
    
    estado.visiveis.forEach(id => {
      const td = el('td'); 
      td.title = String(valorFormatado(item, id) ?? '');
      td.style.cursor = 'pointer'; // Indicador de edição in-line
      
      if (config.renderCelula) {
        config.renderCelula(item, id, td, estado, render, api);
      } else {
        let texto_solto = String(valorFormatado(item, id) ?? '-');
        if (texto_solto.includes(',')) {
          let pedacos = texto_solto.split(',');
          pedacos.forEach(pedaco => {
            let bagulhinho = document.createElement('a');
            bagulhinho.href = '#';
            bagulhinho.className = 'badge-roca-neon';
            bagulhinho.textContent = pedaco.trim();
            td.append(bagulhinho);
          });
        } else {
          td.textContent = texto_solto;
        }
      }
      
      td.addEventListener('dblclick', e => {
        if (e.target.closest('button,a,input') || window.getSelection()?.toString()) return;
        abrirEdicaoInline(item, id, td);
      });
      tr.append(td);
    });
    
    tr.addEventListener('click', e => {
      if (e.target.closest('.btn-ver-linha')) abrirPainelExpansao(item, tr, false);
      else if (e.target.closest('.btn-editar-linha')) abrirPainelExpansao(item, tr, true);
      else if (!e.target.closest('button,a,input') && !window.getSelection()?.toString() && config.onRowClick) config.onRowClick(item, estado, render);
    });
    return tr;
  }
  
  function abrirEdicaoInline(item, colId, td) {
    const valorOriginal = item[colId];
    const input = document.createElement('input');
    input.type = 'text';
    input.value = valorOriginal || '';
    input.className = 'input-inline';
    
    td.replaceChildren(input);
    input.focus();
    
    const confirmar = async () => {
      const novoValor = input.value.trim();
      if (novoValor === String(valorOriginal || '')) {
        render(); // desfaz se não mudou
        return;
      }
      abrirModalConfirmacao(item, { [colId]: novoValor }, () => render(), () => setTimeout(() => input.focus(), 10));
    };
    
    input.addEventListener('keydown', e => {
      if (e.key === 'Enter') { e.preventDefault(); confirmar(); }
      if (e.key === 'Escape') render();
    });
    input.addEventListener('focusout', () => { setTimeout(() => { if (!document.getElementById('modal-confirmacao-salvar')?.open) confirmar(); }, 150); });
  }

  function abrirPainelExpansao(item, tr, editMode = false) {
    let painel = document.getElementById('painel-expansao-flutuante');
    if (!painel) {
      painel = document.createElement('div');
      painel.id = 'painel-expansao-flutuante';
      painel.className = 'painel-expansao-overlay';
      document.body.append(painel);
    }
    
    // Limpar eventos antigos se houver
    if (painel._removerClickFora) {
      document.removeEventListener('click', painel._removerClickFora);
    }
    
    const trRect = tr.getBoundingClientRect();
    painel.style.top = `${trRect.bottom + window.scrollY}px`;
    painel.style.left = `${Math.max(20, trRect.left)}px`;
    painel.style.width = `${Math.min(window.innerWidth - 40, trRect.width)}px`;
    painel.style.display = 'block';
    
    painel.innerHTML = `
      <div class="painel-header">
        <h3>${editMode ? 'Editar' : 'Visualizar'} Detalhes</h3>
        <button class="icone fechar-painel">✕</button>
      </div>
      <div class="painel-corpo"></div>
      <div class="painel-footer" ${editMode ? '' : 'hidden'}>
        <button class="btn-cancelar-painel">Cancelar</button>
        <button class="primaria btn-salvar-painel">Salvar Alterações</button>
      </div>
    `;
    
    const corpo = painel.querySelector('.painel-corpo');
    const valoresEditados = {};
    
    colunas.forEach(col => {
      const formGroup = document.createElement('div');
      formGroup.className = 'form-grupo';
      const lbl = document.createElement('label');
      lbl.textContent = col.nome;
      formGroup.append(lbl);
      
      const valorAtual = item[col.id];
      
      if (editMode && col.id !== 'id') {
        let valorEditavel = valorAtual;
        if (Array.isArray(valorAtual)) {
            valorEditavel = valorAtual.map(x => (x && typeof x === 'object') ? (x.nome || x.email || JSON.stringify(x)) : x).join(', ');
        } else if (valorAtual && typeof valorAtual === 'object') {
            valorEditavel = valorAtual.nome || valorAtual.titulo || JSON.stringify(valorAtual);
        }
        
        if (col.tipo === 'enum' || col.id === 'status') {
            const sel = document.createElement('select');
            sel.className = 'input-inline';
            const opcoes = [...new Set(estado.todos.map(x => x[col.id]).filter(Boolean))];
            opcoes.forEach(op => {
                const opt = document.createElement('option');
                opt.value = op;
                opt.textContent = op;
                if (op === valorAtual) opt.selected = true;
                sel.append(opt);
            });
            sel.onchange = e => valoresEditados[col.id] = e.target.value;
            formGroup.append(sel);
        } else {
            const inp = document.createElement('input');
            inp.type = col.tipo === 'data' ? 'datetime-local' : 'text';
            inp.value = valorEditavel || '';
            inp.oninput = e => valoresEditados[col.id] = e.target.value;
            formGroup.append(inp);
        }
      } else {
        const span = document.createElement('div');
        span.className = 'valor-leitura';
        
        if (col.id === 'signatarios' && Array.isArray(item.signatarios)) {
          span.innerHTML = item.signatarios.map(s => `<span class="badge-roca-neon">${s.nome || s}</span>`).join(' ');
        } else {
          span.textContent = valorFormatado(item, col.id) || '-';
        }
        formGroup.append(span);
      }
      corpo.append(formGroup);
    });
    
    painel.querySelector('.fechar-painel').onclick = () => painel.style.display = 'none';
    if (editMode) {
      painel.querySelector('.btn-cancelar-painel').onclick = () => painel.style.display = 'none';
      painel.querySelector('.btn-salvar-painel').onclick = () => {
        painel.style.display = 'none';
        abrirModalConfirmacao(item, valoresEditados, () => render(), () => abrirPainelExpansao(item, tr, true));
      };
    }
    
    const clickFora = e => {
      if (painel.style.display !== 'none' && !painel.contains(e.target) && !e.target.closest('.btn-ver-linha, .btn-editar-linha') && !document.getElementById('modal-confirmacao-salvar')?.contains(e.target)) {
        painel.style.display = 'none';
        document.removeEventListener('click', clickFora);
        painel._removerClickFora = null;
      }
    };
    painel._removerClickFora = clickFora;
    setTimeout(() => document.addEventListener('click', clickFora), 50);
  }

  function abrirModalConfirmacao(itemOriginal, valoresNovos, onConfirm, onCancel) {
    let dialog = document.getElementById('modal-confirmacao-salvar');
    if (!dialog) {
      dialog = document.createElement('dialog');
      dialog.id = 'modal-confirmacao-salvar';
      dialog.className = 'modal-exportacao'; // reaproveita estilo de modal
      document.body.append(dialog);
    }
    
    dialog.innerHTML = `
      <div class="modal-conteudo">
        <h2>Confirmar Edição</h2>
        <p>Você tem certeza que deseja salvar estas alterações?</p>
        <div class="modal-acoes">
          <button id="btn-cancelar-edicao" type="button">Cancelar</button>
          <button id="btn-voltar-edicao" type="button">Voltar Edição</button>
          <button id="btn-salvar-edicao" type="button" class="primaria">Salvar</button>
        </div>
      </div>
    `;
    
    dialog.showModal();
    
    dialog.querySelector('#btn-cancelar-edicao').onclick = () => { dialog.close(); onConfirm(); }; // onConfirm reconstrói a linha cancelando o input
    dialog.querySelector('#btn-voltar-edicao').onclick = () => { dialog.close(); if(onCancel) onCancel(); };
    dialog.querySelector('#btn-salvar-edicao').onclick = async () => {
      try {
        const mudancas = { ...itemOriginal, ...valoresNovos };
        // Chamada real seria: await api(`/${itemOriginal.id}`, { method: 'PUT', body: JSON.stringify(mudancas) });
        // Simulação de salvamento local:
        Object.assign(itemOriginal, valoresNovos);
        if (config.toast) config.toast('Alterações salvas com sucesso!');
        dialog.close();
        onConfirm();
      } catch(e) {
        if (config.toast) config.toast('Erro ao salvar: ' + e.message, true);
        dialog.close();
        if(onCancel) onCancel();
      }
    };
  }

  function render() {
    fecharMenuColuna();
    const chips = $(`${config.containerId}-chips`);
    if (chips) {
      chips.replaceChildren();
      Object.entries(estado.filtros).forEach(([id, f]) => {
        if (!f || (Array.isArray(f) && !f.length)) return;
        const chip = el('span', 'chip', `${colunas.find(c => c.id === id)?.nome}: ${Array.isArray(f) ? f.join(', ') : typeof f === 'object' ? `${f.from || '…'}–${f.to || '…'}` : f}`);
        chip.append(botao('✕', () => { delete estado.filtros[id]; estado.pagina = 1; render(); }, 'icone'));
        chips.append(chip);
      });
      if (estado.busca.length) {
        const chip = el('span', 'chip', `⌁ ${estado.busca.map(x => x.texto).join('; ')}`);
        chip.append(botao('✕', () => { estado.busca.forEach(x => x.desfazer()); estado.busca = []; render(); }, 'icone'));
        chips.append(chip);
      }
    }
    
    aplicarLarguras();
    const head = $(`${config.containerId}-cabecalho`); head.replaceChildren();
    
    const todosItens = dados();
    const selecaoTitulo = el('th');
    if (config.acoesFixas) selecaoTitulo.classList.add('coluna-selecao-fixa');
    const selecaoDiv = el('div', 'titulo-selecao');
    const checkboxGeral = el('input');
    checkboxGeral.type = 'checkbox';
    checkboxGeral.checked = todosItens.length > 0 && estado.selecionados.size === todosItens.length && todosItens.every(i => estado.selecionados.has(i.id));
    checkboxGeral.addEventListener('change', () => {
      if (checkboxGeral.checked) todosItens.forEach(i => estado.selecionados.add(i.id));
      else estado.selecionados.clear();
      render();
    });
    selecaoDiv.append(checkboxGeral); selecaoTitulo.append(selecaoDiv); head.append(selecaoTitulo);
    
    const acoesTitulo = el('th');
    if (config.acoesFixas) acoesTitulo.classList.add('coluna-acoes-fixa');
    acoesTitulo.append(el('span', 'titulo-coluna titulo-coluna-fixa', 'AÇÕES'), alcaColuna('acoes', 'ações')); head.append(acoesTitulo);
    estado.visiveis.forEach(id => {
      const c = colunas.find(x => x.id === id), th = el('th');
      const titulo = botao(`${c.nome}${estado.ordem?.id === id ? (estado.ordem.dir === 'asc' ? ' ▲' : ' ▼') : ''}`, () => menuColuna(c, titulo), 'titulo-coluna');
      titulo.setAttribute('aria-label', `Opções da coluna ${c.nome}`);
      titulo.setAttribute('aria-expanded', 'false');
      if (estado.filtros[id]) titulo.classList.add('filtrada');
      th.append(titulo, alcaColuna(id, c.nome)); head.append(th);
    });
    
    const total = Math.max(1, Math.ceil(todosItens.length / estado.tamanho));
    estado.pagina = Math.min(estado.pagina, total);
    
    const corpo = $(`${config.containerId}-corpo`); corpo.replaceChildren();
    todosItens.slice((estado.pagina - 1) * estado.tamanho, estado.pagina * estado.tamanho).forEach(item => corpo.append(linhaDefault(item)));
    
    const textStatus = !config.credenciais() ? 'Conecte para carregar os dados.' : !estado.todos.length ? 'Nenhum registro encontrado.' : !todosItens.length ? 'Nenhum resultado para os filtros atuais. Use ⊘ para limpar.' : '';
    const stEl = $(`${config.containerId}-estado`); if (stEl) stEl.textContent = textStatus;
    
    const pt = $(`${config.containerId}-pagina-texto`);
    if (pt) {
      pt.textContent = `PAGE ${estado.pagina} OF ${total} · ${estado.tamanho} LINHAS ▾`;
      const pMenu = $(`${config.containerId}-menu-pagina`); if(pMenu) pMenu.hidden = true;
      pt.setAttribute('aria-expanded', 'false');
    }
    
    const pag = $(`${config.containerId}-paginacao`);
    if (pag) {
      pag.replaceChildren();
      const anterior = botao('←', () => { estado.pagina--; render(); }); anterior.disabled = estado.pagina === 1; pag.append(anterior);
      let inicio = Math.max(1, Math.min(estado.pagina - 2, total - 4));
      for (let p = inicio; p <= Math.min(total, inicio + 4); p++) pag.append(botao(String(p), () => { estado.pagina = p; render(); }, p === estado.pagina ? 'atual' : ''));
      const proximo = botao('→', () => { estado.pagina++; render(); }); proximo.disabled = estado.pagina === total; pag.append(proximo);
    }
  }

  // Comportamentos de UI
  document.addEventListener('pointerdown', e => {
    if (menuColunaAberto && !menuColunaAberto.menu.contains(e.target) && !menuColunaAberto.ancora.contains(e.target)) fecharMenuColuna();
    const pm = $(`${config.containerId}-menu-pagina`), pt = $(`${config.containerId}-pagina-texto`);
    if (pm && !pm.hidden && !pm.contains(e.target) && !pt.contains(e.target)) { pm.hidden = true; pt.setAttribute('aria-expanded', 'false'); }
  });
  window.addEventListener('resize', () => { posicionarMenuColuna(); posicionarMenuColunas(); });
  
  let larguraAnterior = 0;
  const observadorResize = new ResizeObserver(entradas => {
    for (const entrada of entradas) {
      const novaLargura = entrada.contentRect.width;
      if (larguraAnterior && novaLargura !== larguraAnterior && novaLargura > 0) {
        const fator = novaLargura / larguraAnterior;
        ['acoes', ...estado.visiveis].forEach(id => {
          estado.larguras[id] = Math.max(id === 'acoes' ? 86 : 80, Math.min(600, estado.larguras[id] * fator));
        });
        localStorage.setItem(`${storageId}-larguras`, JSON.stringify(estado.larguras));
        aplicarLarguras();
      }
      larguraAnterior = novaLargura;
    }
  });
  const tabelaScroll = $(`${config.containerId}-scroll`);
  if (tabelaScroll) observadorResize.observe(tabelaScroll);
  window.addEventListener('scroll', () => { posicionarMenuColuna(); posicionarMenuColunas(); }, true);
  document.addEventListener('keydown', e => {
    if (e.key === 'Escape') {
      if (estado.editando) { estado.editando = null; render(); }
      fecharMenuColuna();
      const pm = $(`${config.containerId}-menu-pagina`), pt = $(`${config.containerId}-pagina-texto`);
      if(pm) pm.hidden = true; if(pt) pt.setAttribute('aria-expanded', 'false');
      fecharMenus();
    }
  });

  const pt = $(`${config.containerId}-pagina-texto`);
  if (pt) {
    pt.addEventListener('click', () => {
      const menu = $(`${config.containerId}-menu-pagina`), aberto = !menu.hidden; menu.replaceChildren();
      if (aberto) { menu.hidden = true; pt.setAttribute('aria-expanded', 'false'); return; }
      menu.append(el('p', '', 'LINHAS POR PÁGINA'));
      const tamanhos = el('div', 'tamanhos-pagina'); [5, 10, 50, 100].forEach(n => tamanhos.append(botao(String(n), () => { estado.tamanho = n; estado.pagina = 1; render(); }, n === estado.tamanho ? 'atual' : ''))); menu.append(tamanhos);
      menu.append(el('hr'), el('p', '', 'IR PARA A PÁGINA'));
      const salto = el('div', 'salto-pagina'), numero = el('input'); numero.type = 'number'; numero.min = '1'; numero.max = String(Math.max(1, Math.ceil(dados().length / estado.tamanho))); numero.value = String(estado.pagina); numero.setAttribute('aria-label', 'Número da página'); salto.append(numero, botao('IR', () => { estado.pagina = Math.min(Number(numero.max), Math.max(1, Number(numero.value) || 1)); render(); })); menu.append(salto);
      menu.hidden = false; pt.setAttribute('aria-expanded', 'true'); numero.addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); salto.querySelector('button').click(); } });
    });
  }

  const btnColunas = $(`${config.containerId}-colunas-botao`);
  if (btnColunas) btnColunas.addEventListener('click', () => { estado.rascunho = [...estado.visiveis]; menuColunas(); abrir(`${config.containerId}-menu-colunas`, `${config.containerId}-colunas-botao`); });

  // Busca Plana (Se ativo)
  if (config.buscaFormId) {
    function estimar(etapas){
      return estado.todos.filter(item=>etapas.every(e=>{
        if(e.op!=='column-filter')return true;
        const v=String(valorFormatado(item,e.column)??'').toLowerCase();
        return e.type==='equals'?v===e.value.toLowerCase():e.type==='contains'?v.includes(e.value.toLowerCase()):(!e.from||v>=e.from)&&(!e.to||v<=e.to);
      })).length;
    }
    function aplicarPlano(consulta) {
      if(!estado.plano)return; estado.busca.forEach(x=>x.desfazer()); estado.busca=[];
      const anteriores=new Map(),aplicados=new Map(),ordemAnterior=estado.ordem?{...estado.ordem}:null;
      estado.plano.steps.forEach(e=>{
        if(e.op==='sort')estado.ordem={id:e.column,dir:e.dir};
        else {
          if(!anteriores.has(e.column))anteriores.set(e.column,structuredClone(estado.filtros[e.column]??null));
          const novo=e.type==='range'?{from:e.from,to:e.to}:e.type==='equals'?[e.value]:e.value;
          estado.filtros[e.column]=novo;aplicados.set(e.column,structuredClone(novo));
        }
      });
      const ordemAplicada=estado.ordem?{...estado.ordem}:null;
      estado.busca=[{texto:consulta,desfazer:()=>{
        anteriores.forEach((f,id)=>{if(JSON.stringify(estado.filtros[id])===JSON.stringify(aplicados.get(id))){if(f===null)delete estado.filtros[id];else estado.filtros[id]=f;}});
        if(JSON.stringify(estado.ordem)===JSON.stringify(ordemAplicada))estado.ordem=ordemAnterior;
      }}];
      estado.pagina=1; $(`${config.containerId}-plano-busca`).hidden=true; estado.plano=null; render();
    }
    function mostrarPlano(consulta) {
      const painel = $(`${config.containerId}-plano-busca`); painel.style.borderColor='var(--border)';
      painel.replaceChildren(el('h2','', 'PLANO DE BUSCA'),el('p','',estado.plano.summary||consulta));
      const lista=el('ol');
      estado.plano.steps.forEach((etapa,i)=>{
        const item=el('li');
        const descricao=etapa.op==='sort'?`Ordenar ${etapa.column} ${etapa.dir}`:`Filtrar ${etapa.column}: ${etapa.type==='range'?`${etapa.from||'…'} até ${etapa.to||'…'}`:etapa.value}`;
        item.append(el('span','plano-numero',`${i+1}. `),el('span','',descricao),botao('✕',()=>{estado.plano.steps.splice(i,1);mostrarPlano(consulta);},'icone'));
        lista.append(item);
      });
      painel.append(lista,el('p','',`≈ ${estimar(estado.plano.steps)} resultados`),botao('✓ APLICAR',()=>aplicarPlano(consulta),'primaria'),botao('✕ CANCELAR',()=>{painel.hidden=true;estado.plano=null}));
    }
    $(config.buscaFormId).addEventListener('submit', async e => {
      e.preventDefault(); const input = $(config.buscaFormId).querySelector('input'); const consulta = input.value.trim(); if (!consulta) return;
      const painel = $(`${config.containerId}-plano-busca`); painel.hidden = false; painel.replaceChildren(el('p','', 'Interpretando…'));
      try { const plano = await api(config.buscaEndpoint || '/plano-busca', { method: 'POST', body: JSON.stringify({ consulta }) }); estado.plano = plano; mostrarPlano(consulta); }
      catch (erro) { painel.replaceChildren(el('p','',erro.message), el('p','', 'Tente detalhar mais a busca.')); painel.style.borderColor='var(--accent)'; setTimeout(() => { painel.hidden = true; painel.style.borderColor = ''; }, 2000); }
    });
  }

  // Métodos expostos
  const inst = {
    carregar: async () => {
      try {
        estado.salvos = JSON.parse(localStorage.getItem(`${storageId}-filtros`) || '[]');
      } catch { estado.salvos = []; }
      const stEl = $(`${config.containerId}-estado`);
      if (stEl) stEl.textContent = 'Carregando dados…';
      try {
        estado.todos = await api('');
        if (config.toast) config.toast(`${estado.todos.length} registro(s) carregado(s).`);
        render();
      } catch (e) {
        if (stEl) stEl.textContent = e.message;
        if (config.toast) config.toast(e.message, true);
      }
    },
    dados: dados,
    obterSelecionados: () => [...estado.selecionados],
    getEstado: () => estado,
    render: render
  };
  
  if (config.autoInit !== false) render();
  return inst;
};
