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
    return token ? { ...(chave ? { 'X-API-Key': chave } : {}), Authorization: `Bearer ${token}` } : {};
  }

  let versaoHistorico = 0;
  const modalHistorico = $('modal-historico-assinatura');
  modalHistorico.addEventListener('close', () => { versaoHistorico++; });
  async function abrirHistorico(item) {
    const versao = ++versaoHistorico;
    const painel = $('conteudo-historico-assinatura');
    painel.textContent = 'Carregando documentos e histórico...';
    modalHistorico.showModal();
    const adicionar = (pai, tag, texto) => {
      const elemento = document.createElement(tag); elemento.textContent = texto; pai.append(elemento); return elemento;
    };
    const data = valor => new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'medium' }).format(new Date(valor));
    try {
      const resposta = await fetch(item.legado ? `/api/assineai/assinaturas/${item.id}` : `/api/envelopes/${item.id}`, { headers: getCredenciais(), cache: 'no-store' });
      if (!resposta.ok) throw new Error(`Não foi possível consultar o histórico (${resposta.status}).`);
      const dados = await resposta.json();
      if (versao !== versaoHistorico || !modalHistorico.open) return;
      painel.replaceChildren();
      adicionar(painel, 'h3', dados.nome || dados.nomeDocumento);
      adicionar(painel, 'p', `${dados.status} · Responsável: ${dados.responsavel || 'Não informado'} · Documentos com assinaturas concluídas: ${dados.quantidadeDocumentoAssinado || '0'}`);
      adicionar(painel, 'h3', 'DOCUMENTOS');
      for (const documento of dados.documentos || []) {
        adicionar(painel, 'p', documento.paginaInicio ? `${documento.titulo} · páginas ${documento.paginaInicio} a ${documento.paginaFim}` : documento.titulo);
        if (item.legado) {
          const baixar = adicionar(painel, 'button', 'BAIXAR DOCUMENTO'); baixar.type = 'button';
          baixar.addEventListener('click', async () => {
            try { const resposta = await fetch(`/api/assineai/assinaturas/${item.id}/documentos/${documento.identificador}/pdf`, { headers: getCredenciais() });
              if (!resposta.ok) throw new Error(`Documento indisponível (${resposta.status}).`);
              const url = URL.createObjectURL(await resposta.blob()); const link = document.createElement('a'); link.href = url; link.download = documento.titulo + '.pdf'; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
            } catch (erro) { toast(erro.message, true); }
          });
          if (documento.autenticidade) { const link = adicionar(painel, 'a', ' VER AUTENTICIDADE'); link.href = documento.autenticidade; link.target = '_blank'; link.rel = 'noreferrer'; }
        }
      }
      if (!dados.documentos?.length) adicionar(painel, 'p', 'Registro anterior sem documentos individualizados.');
      if (item.legado) {
        const formulario = document.createElement('form'); formulario.className = 'campos';
        const titulo = adicionar(formulario, 'h3', 'CERTIFICAR DOCUMENTOS INDIVIDUAIS');
        const labelArquivo = adicionar(formulario, 'label', 'CERTIFICADO PFX / P12'); const arquivo = document.createElement('input'); arquivo.type = 'file'; arquivo.accept = '.pfx,.p12'; arquivo.required = true; labelArquivo.append(arquivo);
        const labelSenha = adicionar(formulario, 'label', 'SENHA'); const senha = document.createElement('input'); senha.type = 'password'; senha.required = true; senha.autocomplete = 'off'; labelSenha.append(senha);
        const enviar = adicionar(formulario, 'button', 'CERTIFICAR CADA PDF'); enviar.type = 'submit';
        formulario.addEventListener('submit', async evento => {
          evento.preventDefault(); enviar.disabled = true;
          try { const corpo = new FormData(); corpo.append('certificado', arquivo.files[0]); corpo.append('senha', senha.value);
            const resposta = await fetch(`/api/assineai/assinaturas/${item.id}/certificar`, { method: 'POST', headers: getCredenciais(), body: corpo });
            if (!resposta.ok) { const erro = await resposta.json().catch(() => ({})); throw new Error(erro.mensagem || 'Não foi possível certificar os documentos.'); }
            senha.value = ''; toast('Todos os PDFs foram certificados individualmente.'); modalHistorico.close(); tabela.carregar();
          } catch (erro) { toast(erro.message, true); } finally { enviar.disabled = false; }
        }); painel.append(formulario);
      }
      for (const pessoa of dados.signatarios || []) {
        const secao = adicionar(painel, 'section', '');
        adicionar(secao, 'h3', pessoa.nome);
        adicionar(secao, 'p', `${pessoa.situacao || 'Pendente'} · Acessos ao link: ${pessoa.acessos || '0'}`);
        if (pessoa.assinadoEm) adicionar(secao, 'p', `Assinatura confirmada em ${data(pessoa.assinadoEm)}`);
        if (pessoa.autenticadoVia) adicionar(secao, 'p', `Métodos registrados: ${pessoa.autenticadoVia.split(',').map(m => ({ tela: 'Assinatura na tela', rubrica: 'Rubrica', selfie: 'Selfie', email: 'Código por e-mail', whatsapp: 'Código por WhatsApp' })[m] || m).join(', ')}`);
        const lista = adicionar(secao, 'ol', '');
        for (const evento of pessoa.historicos || []) {
          const linha = adicionar(lista, 'li', `${data(evento.criadoEm)} · ${evento.acao.replaceAll('_', ' ')}`);
          if (evento.enderecoIp) adicionar(linha, 'p', `IP: ${evento.enderecoIp}`);
          if (evento.dispositivo) adicionar(linha, 'p', `Dispositivo: ${evento.dispositivo}`);
        }
        if (!pessoa.historicos?.length) adicionar(secao, 'p', 'Não há eventos registrados.');
      }
    } catch (erro) {
      if (versao === versaoHistorico && modalHistorico.open) painel.textContent = erro.message;
    }
  }

  async function conectar() {
    const chave = $('lista-chave').value.trim();
    const token = $('lista-token').value.trim();
    if (!token || $('fonte-assinaturas').value === 'firma' && !chave) {
      toast('Informe o token. Para registros anteriores da Firma, informe também a chave da API.', true);
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

  const tabela = new window.FirmaTabela({
    elemento: '#tabela-montagem',
    rotulo: 'Documentos para assinatura',
    recursos: {
      selecao: true, acoes: true, paginacao: true, selecionarColunas: true,
      filtrosHeader: true, ordenacao: true, redimensionamento: true, reordenacao: true,
      filtrosSalvos: true, buscaIA: true, edicao: true, detalhes: true, persistencia: true
    },
    slots: {
      comandos: $('tabela-comandos').content.cloneNode(true),
      rodape: $('tabela-rodape').content.cloneNode(true)
    },
    textos: {
      busca: 'FALAR COM O BURRO DE CARGA...',
      pagina: ({ pagina, totalPaginas, tamanho }) => `ÁREA ${pagina} de ${totalPaginas} do TERRENO → ${tamanho} Sementes ▾`
    },
    salvar: (item, alteracoes, instancia) => instancia.api(`/${item.id}`, { method: 'PATCH', body: JSON.stringify({ titulo: alteracoes.titulo }) }),
    containerId: 'tabela',
    get endpoint() { return $('fonte-assinaturas').value === 'legado' ? '/api/assineai/assinaturas' : '/api/envelopes/documentos'; },
    storageId: 'assineai-documentos',
    get buscaEndpoint() { return $('fonte-assinaturas').value === 'legado' ? '/api/assineai/plano-busca' : '/api/envelopes/documentos/plano-busca'; },
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
    ].map(coluna => ({ ...coluna, editavel: coluna.id === 'titulo' })),
    renderAcoes: (item, acoesTd, estado, render, api) => {
      const btnEdit = document.createElement('button');
      btnEdit.textContent = '✎';
      btnEdit.title = 'Editar título do documento';
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

      const btnHistorico = document.createElement('button');
      btnHistorico.type = 'button'; btnHistorico.className = 'icone'; btnHistorico.textContent = '◷'; btnHistorico.title = 'HISTÓRICO';
      btnHistorico.setAttribute('aria-label', `Histórico de ${item.titulo}`);
      btnHistorico.onclick = evento => { evento.stopPropagation(); abrirHistorico(item); };
      acoesTd.classList.add('acoes-assinatura');
      acoesTd.append(btnView, btnEdit, btnHistorico, btnDel);
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
            if (item.legado) { await abrirHistorico(item); return; }
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
        else if (st.includes('progresso') || st.includes('andamento') || st.includes('certificado')) statusClass = 'status-progresso';
        else if (st.includes('assinado') || st.includes('concluido') || st.includes('finalizada')) statusClass = 'status-assinado';
        
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

  $('fonte-assinaturas').addEventListener('change', () => { tabela.limparSelecao(); tabela.carregar(); });

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
