(() => {
  const $ = id => document.getElementById(id);
  
  function toast(msg, erro = false) {
    const box = $('lista-toast');
    if (box) {
      box.replaceChildren(document.createTextNode(msg));
      box.style.color = erro ? 'var(--accent)' : '';
      if (!erro) {
        box.append(Object.assign(document.createElement('button'), { type: 'button', textContent: '✕', onclick: () => box.hidden = true }));
        setTimeout(() => box.hidden = true, 4500);
      }
      box.hidden = false;
    }
  }

  function getCredenciais() {
    const chave = $('lista-chave').value.trim();
    const token = $('lista-token').value.trim();
    return (chave && token) ? { 'X-API-Key': chave, Authorization: `Bearer ${token}` } : {};
  }

  async function conectar() {
    const chave = $('lista-chave').value.trim();
    const token = $('lista-token').value.trim();
    if (!chave || !token) {
      toast('Informe chave e token.', true);
      return;
    }
    toast('Conectando...');
    localStorage.setItem('assineai-chave', chave);
    localStorage.setItem('assineai-token', token);
    await tabela.carregar();
    
    try {
      const salvas = JSON.parse(localStorage.getItem('assineai-documentos-larguras') || '{}');
      if (!Number.isFinite(salvas['signatarios'])) {
        const estado = tabela.getEstado();
        let maxWidth = 200;
        estado.todos.forEach(item => {
          if (item.signatarios && Array.isArray(item.signatarios)) {
            const width = item.signatarios.reduce((acc, sig) => acc + (sig.nome || sig).length * 7 + 30, 0) + 30;
            if (width > maxWidth) maxWidth = width;
          }
        });
        if (maxWidth > 200) {
          estado.larguras['signatarios'] = Math.min(600, maxWidth);
          tabela.render();
        }
      }
    } catch {}
  }

  $('lista-conectar').addEventListener('click', conectar);

  const tabela = window.aFirmaTabela({
    containerId: 'tabela',
    endpoint: '/api/envelopes/documentos',
    storageId: 'assineai-documentos',
    buscaFormId: 'busca-form',
    buscaEndpoint: '/api/envelopes/documentos/plano-busca',
    credenciais: getCredenciais,
    toast: toast,
    colunas: [
      { id: 'id', nome: 'ID', tipo: 'texto', largura: 200 },
      { id: 'titulo', nome: 'TÍTULO', tipo: 'texto', largura: 200 },
      { id: 'data', nome: 'DATA', tipo: 'data', largura: 200 },
      { id: 'responsavel', nome: 'RESPONSÁVEL', tipo: 'texto', largura: 200 },
      { id: 'status', nome: 'STATUS', tipo: 'enum', largura: 200 },
      { id: 'quantidadeAssinaturas', nome: 'ASSINATURAS', tipo: 'numero', largura: 200 },
      { id: 'signatarios', nome: 'SIGNATÁRIOS', tipo: 'texto', largura: 200, formatar: (v) => Array.isArray(v) ? v.map(s => s.nome || s).join(', ') : v }
    ],
    renderAcoes: (item, acoesTd, estado, render, api) => {
      const btnEdit = document.createElement('button');
      btnEdit.textContent = '✎';
      btnEdit.title = 'Editar in-line (Duplo clique na célula) ou Expandir';
      btnEdit.className = 'icone btn-editar-linha';
      
      const btnView = document.createElement('button');
      btnView.textContent = '👁';
      btnView.title = 'Visualizar detalhes';
      btnView.className = 'icone btn-ver-linha';

      const btnDel = document.createElement('button');
      btnDel.textContent = '✖';
      btnDel.className = 'icone destrutivo';
      btnDel.onclick = () => {
        if (confirm(`Excluir documento ${item.titulo}?`)) {
          api(`/${item.id}`, { method: 'DELETE' }).then(() => tabela.carregar()).catch(e => toast(e.message, true));
        }
      };

      acoesTd.append(btnView, btnEdit, btnDel);
    },
    renderCelula: (item, idColuna, td, estado, render, api) => {
      if (idColuna === 'titulo') {
        const a = document.createElement('a');
        a.href = '#';
        a.title = item.titulo;
        a.textContent = item.titulo;
        a.onclick = async e => {
          e.preventDefault();
          try {
            const resposta = await fetch(`/api/envelopes/${item.id}/pdf`, { headers: getCredenciais() });
            if (!resposta.ok) throw new Error(`Erro ${resposta.status} ao baixar PDF`);
            const url = URL.createObjectURL(await resposta.blob());
            const link = document.createElement('a');
            link.href = url;
            link.download = item.titulo.toLowerCase().endsWith('.pdf') ? item.titulo : `${item.titulo}.pdf`;
            link.click();
            setTimeout(() => URL.revokeObjectURL(url), 1000);
          } catch(erro) { toast(erro.message, true); }
        };
        td.append(a);
      } else if (idColuna === 'status') {
        const span = document.createElement('span');
        const st = (item.status || '').toLowerCase();
        let statusClass = '';
        if (st.includes('pendente')) statusClass = 'status-pendente';
        else if (st.includes('progresso') || st.includes('andamento')) statusClass = 'status-progresso';
        else if (st.includes('assinado') || st.includes('concluido')) statusClass = 'status-assinado';
        
        span.className = `chip ${statusClass}`;
        span.textContent = item.status;
        td.append(span);
      } else if (idColuna === 'signatarios' && item.signatarios && Array.isArray(item.signatarios)) {
        item.signatarios.forEach(sig => {
          const a = document.createElement('a');
          a.href = '#';
          a.className = 'badge-roca-neon';
          a.textContent = sig.nome || sig;
          td.append(a);
        });
      } else if (idColuna === 'data') {
        td.textContent = item.data ? new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(item.data)) : '';
      } else {
        const valor = item[idColuna];
        if (typeof valor === 'string' && valor.includes(',')) {
          valor.split(',').forEach(v => {
            const a = document.createElement('a');
            a.href = '#';
            a.className = 'badge-roca-neon';
            a.textContent = v.trim();
            td.append(a);
          });
        } else {
          td.textContent = valor ?? '';
        }
      }
    }
  });

  const modosLargura = [['total', 'TELA INTEIRA'], ['largo', '80% DA TELA'], ['centro', 'CENTRO']];
  function aplicarModoLargura(modo) {
    const atual = modosLargura.findIndex(([id]) => id === modo);
    const indice = atual < 0 ? 0 : atual;
    $('pagina-documentos').dataset.largura = modosLargura[indice][0];
    $('alternar-largura').textContent = `▤ ${modosLargura[indice][1]}`;
    localStorage.setItem('assineai-largura-documentos-v2', modosLargura[indice][0]);
  }
  
  $('alternar-largura').addEventListener('click', () => {
    const atual = modosLargura.findIndex(([id]) => id === $('pagina-documentos').dataset.largura);
    aplicarModoLargura(modosLargura[(atual + 1) % 3][0]);
  });
  
  aplicarModoLargura(localStorage.getItem('assineai-largura-documentos-v2') || 'total');

  const modalExport = $('modal-exportacao');
  const btnExport = $('exportar-dados');
  const msgExport = $('export-msg');
  const qtdExport = $('export-qtd');
  const chkExport = $('export-concordo');
  const botoesExport = document.querySelectorAll('#botoes-formato-export button');

  btnExport.addEventListener('click', () => {
    const estado = tabela.getEstado();
    const temFiltro = Object.keys(estado.filtros || {}).length > 0;
    const qtd = tabela.dados().length;
    
    if (temFiltro) {
      msgExport.textContent = 'Filtros estão aplicados. Confirme a quantidade de registros a serem exportados.';
    } else {
      msgExport.textContent = 'Não há filtros aplicados. Tem certeza que deseja exportar todos os registros?';
    }
    
    qtdExport.textContent = qtd;
    chkExport.checked = false;
    botoesExport.forEach(b => b.disabled = true);
    modalExport.showModal();
  });

  chkExport.addEventListener('change', (e) => {
    botoesExport.forEach(b => b.disabled = !e.target.checked);
  });

  $('btn-cancelar-export').addEventListener('click', () => {
    modalExport.close();
  });

  botoesExport.forEach(b => b.addEventListener('click', (e) => {
    const formato = e.target.dataset.formato;
    if (formato === 'pdf') {
      window.print();
    } else {
      toast(`Exportação para ${formato.toUpperCase()} não implementada`);
    }
    modalExport.close();
  }));

  const modalImport = $('modal-importacao');
  $('importar-dados').addEventListener('click', () => modalImport.showModal());
  $('btn-cancelar-import').addEventListener('click', () => modalImport.close());
  
  const inputImport = $('input-arquivo-importacao');
  const zonaDrag = inputImport.parentElement;
  
  zonaDrag.addEventListener('dragover', (e) => {
    e.preventDefault();
    zonaDrag.style.borderColor = 'var(--primaria, #007bff)';
  });
  
  zonaDrag.addEventListener('dragleave', (e) => {
    e.preventDefault();
    zonaDrag.style.borderColor = 'var(--borda)';
  });
  
  zonaDrag.addEventListener('drop', (e) => {
    e.preventDefault();
    zonaDrag.style.borderColor = 'var(--borda)';
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      inputImport.files = e.dataTransfer.files;
      toast(`Arquivo ${e.dataTransfer.files[0].name} selecionado. Importação não implementada.`);
      modalImport.close();
    }
  });

  inputImport.addEventListener('change', () => {
    if (inputImport.files && inputImport.files.length > 0) {
      toast(`Arquivo ${inputImport.files[0].name} selecionado. Importação não implementada.`);
      modalImport.close();
    }
  });

  // preenchimento de credenciais salvas ou dados do perfil de teste local
  const chaveGuardada = localStorage.getItem('assineai-chave');
  const tokenGuardado = localStorage.getItem('assineai-token');
  if (chaveGuardada) $('lista-chave').value = chaveGuardada;
  if (tokenGuardado) $('lista-token').value = tokenGuardado;

  const btnTeste = $('usar-credenciais-teste');
  if (document.body.dataset.tokenTeste && document.body.dataset.chaveTeste) {
    if (btnTeste) {
      btnTeste.hidden = false;
      btnTeste.addEventListener('click', () => {
        $('lista-chave').value = document.body.dataset.chaveTeste;
        $('lista-token').value = document.body.dataset.tokenTeste;
        conectar();
      });
    }
    // se estiver vazio no ambiente de teste, preenche automaticamente
    if (!$('lista-chave').value && !$('lista-token').value) {
      $('lista-chave').value = document.body.dataset.chaveTeste;
      $('lista-token').value = document.body.dataset.tokenTeste;
    }
  }

})();
