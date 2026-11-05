(() => {
  const porId = id => document.getElementById(id);
  function aviso(mensagemAtual, erro = false) {
    const caixa = porId('lista-toast');
    if (caixa) {
      caixa.dataset.firmaTipo = erro ? 'erro' : 'sucesso';
      caixa.textContent = mensagemAtual;
      caixa.hidden = !mensagemAtual;
    }
  }
  function obterCredenciais() {
    const chave = porId('lista-chave').value.trim();
    const token = porId('lista-token').value.trim();
    return token ? {
      ...(chave ? {
        'X-API-Key': chave
      } : {}),
      Authorization: `Bearer ${token}`
    } : {};
  }
  let carregandoRegistros = false;
  let versaoHistorico = 0;
  const janelaHistorico = porId('modal-historico-assinatura');
  janelaHistorico.addEventListener('close', () => {
    versaoHistorico++;
  });
  async function abrirHistorico(item) {
    const versao = ++versaoHistorico;
    const painel = porId('conteudo-historico-assinatura');
    painel.textContent = 'Carregando documentos e histórico...';
    janelaHistorico.showModal();
    const adicionar = (pai, tag, texto) => {
      const elemento = document.createElement(tag);
      elemento.textContent = texto;
      pai.append(elemento);
      return elemento;
    };
    const data = valor => new Intl.DateTimeFormat('pt-BR', {
      dateStyle: 'short',
      timeStyle: 'medium'
    }).format(new Date(valor));
    try {
      const resposta = await fetch(item.legado ? `/api/assineai/assinaturas/${item.id}` : `/api/envelopes/${item.id}`, {
        headers: obterCredenciais(),
        cache: 'no-store'
      });
      if (!resposta.ok) throw new Error(`Não foi possível consultar o histórico (${resposta.status}).`);
      const dados = await resposta.json();
      if (versao !== versaoHistorico || !janelaHistorico.open) return;
      painel.replaceChildren();
      adicionar(painel, 'h3', dados.nome || dados.nomeDocumento);
      adicionar(painel, 'p', `${dados.status} · Responsável: ${dados.responsavel || 'Não informado'} · Documentos com assinaturas concluídas: ${dados.quantidadeDocumentoAssinado || '0'}`);
      adicionar(painel, 'h3', 'DOCUMENTOS');
      for (const documento of dados.documentos || []) {
        adicionar(painel, 'p', documento.paginaInicio ? `${documento.titulo} · páginas ${documento.paginaInicio} a ${documento.paginaFim}` : documento.titulo);
        if (item.legado) {
          const baixar = adicionar(painel, 'button', 'BAIXAR DOCUMENTO');
          baixar.type = 'button';
          baixar.addEventListener('click', async () => {
            try {
              const resposta = await fetch(`/api/assineai/assinaturas/${item.id}/documentos/${documento.identificador}/pdf`, {
                headers: obterCredenciais()
              });
              if (!resposta.ok) throw new Error(`Documento indisponível (${resposta.status}).`);
              const endereco = URL.createObjectURL(await resposta.blob());
              const link = document.createElement('a');
              link.href = endereco;
              link.download = documento.titulo + '.pdf';
              link.click();
              setTimeout(() => URL.revokeObjectURL(endereco), 1000);
            } catch (erro) {
              aviso(erro.message, true);
            }
          });
          if (documento.autenticidade) {
            const link = adicionar(painel, 'a', ' VER AUTENTICIDADE');
            link.href = documento.autenticidade;
            link.target = '_blank';
            link.rel = 'noreferrer';
          }
        }
      }
      if (!dados.documentos?.length) adicionar(painel, 'p', 'Registro anterior sem documentos individualizados.');
      if (item.legado) {
        const formulario = document.createElement('form');
        formulario.className = 'campos';
        const titulo = adicionar(formulario, 'h3', 'CERTIFICAR DOCUMENTOS INDIVIDUAIS');
        const rotuloArquivo = adicionar(formulario, 'label', 'CERTIFICADO PFX / P12');
        const arquivo = document.createElement('input');
        arquivo.type = 'file';
        arquivo.accept = '.pfx,.p12';
        arquivo.required = true;
        rotuloArquivo.append(arquivo);
        const rotuloSenha = adicionar(formulario, 'label', 'SENHA');
        const senha = document.createElement('input');
        senha.type = 'password';
        senha.required = true;
        senha.autocomplete = 'off';
        rotuloSenha.append(senha);
        const enviar = adicionar(formulario, 'button', 'CERTIFICAR CADA PDF');
        enviar.type = 'submit';
        formulario.addEventListener('submit', async evento => {
          evento.preventDefault();
          enviar.disabled = true;
          try {
            const corpo = new FormData();
            corpo.append('certificado', arquivo.files[0]);
            corpo.append('senha', senha.value);
            const resposta = await fetch(`/api/assineai/assinaturas/${item.id}/certificar`, {
              method: 'POST',
              headers: obterCredenciais(),
              body: corpo
            });
            if (!resposta.ok) {
              const erro = await resposta.json().catch(() => ({}));
              throw new Error(erro.mensagem || 'Não foi possível certificar os documentos.');
            }
            senha.value = '';
            aviso('Todos os PDFs foram certificados individualmente.');
            janelaHistorico.close();
            tabela.carregar();
          } catch (erro) {
            aviso(erro.message, true);
          } finally {
            enviar.disabled = false;
          }
        });
        painel.append(formulario);
      }
      for (const pessoa of dados.signatarios || []) {
        const secao = adicionar(painel, 'section', '');
        adicionar(secao, 'h3', pessoa.nome);
        adicionar(secao, 'p', `${pessoa.situacao || 'Pendente'} · Acessos ao link: ${pessoa.acessos || '0'}`);
        if (pessoa.assinadoEm) adicionar(secao, 'p', `Assinatura confirmada em ${data(pessoa.assinadoEm)}`);
        if (pessoa.autenticadoVia) adicionar(secao, 'p', `Métodos registrados: ${pessoa.autenticadoVia.split(',').map(itemAtual => ({
          tela: 'Assinatura na tela',
          rubrica: 'Rubrica',
          selfie: 'Selfie',
          email: 'Código por e-mail',
          whatsapp: 'Código por WhatsApp'
        })[itemAtual] || itemAtual).join(', ')}`);
        const lista = adicionar(secao, 'ol', '');
        for (const evento of pessoa.historicos || []) {
          const linha = adicionar(lista, 'li', `${data(evento.criadoEm)} · ${evento.acao.replaceAll('_', ' ')}`);
          if (evento.enderecoIp) adicionar(linha, 'p', `IP: ${evento.enderecoIp}`);
          if (evento.dispositivo) adicionar(linha, 'p', `Dispositivo: ${evento.dispositivo}`);
        }
        if (!pessoa.historicos?.length) adicionar(secao, 'p', 'Não há eventos registrados.');
      }
    } catch (erro) {
      if (versao === versaoHistorico && janelaHistorico.open) painel.textContent = erro.message;
    }
  }
  async function conectar() {
    const chave = porId('lista-chave').value.trim();
    const token = porId('lista-token').value.trim();
    if (!token || porId('fonte-assinaturas').value === 'firma' && !chave) {
      aviso('Informe o token. Para registros anteriores da Firma, informe também a chave da API.', true);
      return;
    }
    aviso('Conectando...');
    localStorage.setItem('assineai-chave', chave);
    localStorage.setItem('assineai-token', token);
    await tabela.carregar();
    try {
      const salvas = JSON.parse(localStorage.getItem('assineai-documentos-larguras') || '{}');
      if (!Number.isFinite(salvas['signatarios'])) {
        const estado = tabela.getEstado();
        let larguraMaxima = 200;
        estado.todos.forEach(item => {
          if (item.signatarios && Array.isArray(item.signatarios)) {
            const larguraAtual = item.signatarios.reduce((acumulado, assinatura) => acumulado + (assinatura.nome || assinatura).length * 7 + 30, 0) + 30;
            if (larguraAtual > larguraMaxima) larguraMaxima = larguraAtual;
          }
        });
        if (larguraMaxima > 200) {
          estado.larguras['signatarios'] = Math.min(600, larguraMaxima);
          tabela.render();
        }
      }
    } catch {}
  }
  porId('lista-conectar').addEventListener('click', conectar);
  const tabela = window.FirmaTrator.montar({
    elemento: '#tabela-montagem',
    rotulo: 'Documentos para assinatura',
    slots: {
      comandos: porId('tabela-comandos').content.cloneNode(true),
      rodape: porId('tabela-rodape').content.cloneNode(true)
    },
    textos: {
      busca: 'FALAR COM O BURRO DE CARGA...'
    },
    salvar: (item, alteracoes, instancia) => instancia.api(`/${item.id}`, {
      method: 'PATCH',
      body: JSON.stringify({
        titulo: alteracoes.titulo
      })
    }),
    containerId: 'tabela',
    get endpoint() {
      return porId('fonte-assinaturas').value === 'legado' ? '/api/assineai/assinaturas' : '/api/envelopes/documentos';
    },
    storageId: 'assineai-documentos',
    get buscaEndpoint() {
      return porId('fonte-assinaturas').value === 'legado' ? '/api/assineai/plano-busca' : '/api/envelopes/documentos/plano-busca';
    },
    credenciais: obterCredenciais,
    toast: aviso,
    onRender: instancia => {
      const carregando = instancia.getEstado().carregando;
      if (carregando && !carregandoRegistros) {
        const caixa = porId('lista-toast');
        caixa.dataset.firmaTipo = 'info';
        caixa.textContent = 'Carregando registros...';
        caixa.hidden = false;
      }
      carregandoRegistros = carregando;
    },
    onCarregar: () => aviso(''),
    colunas: [{
      id: 'id',
      nome: 'ID',
      tipo: 'texto',
      largura: 200
    }, {
      id: 'titulo',
      nome: 'TÍTULO',
      tipo: 'texto',
      largura: 200
    }, {
      id: 'data',
      nome: 'DATA',
      tipo: 'data',
      largura: 200
    }, {
      id: 'responsavel',
      nome: 'RESPONSÁVEL',
      tipo: 'texto',
      largura: 200
    }, {
      id: 'status',
      nome: 'STATUS',
      tipo: 'enum',
      largura: 200
    }, {
      id: 'quantidadeAssinaturas',
      nome: 'ASSINATURAS',
      tipo: 'numero',
      largura: 200
    }, {
      id: 'signatarios',
      nome: 'SIGNATÁRIOS',
      tipo: 'texto',
      largura: 200,
      formatar: linkDownload => Array.isArray(linkDownload) ? linkDownload.map(linkDownloadAtual => linkDownloadAtual.nome || linkDownloadAtual).join(', ') : linkDownload
    }].map(coluna => ({
      ...coluna,
      editavel: coluna.id === 'titulo'
    })),
    renderAcoes: (item, acoesTd, estado, desenhar, api) => {
      const botaoEditar = FirmaTabela.botaoAcao('editar', 'Editar título do documento');
      const botaoVisualizar = FirmaTabela.botaoAcao('visualizar', 'Visualizar detalhes');
      const botaoExcluir = FirmaTabela.botaoAcao('remover', `Excluir ${item.titulo}`, () => {
        return api(`/${item.id}`, {
          method: 'DELETE'
        }).then(() => tabela.carregar()).catch(eventoAtual => aviso(eventoAtual.message, true));
      });
      const botaoHistorico = FirmaTabela.botaoAcao('historico', `Histórico de ${item.titulo}`, () => abrirHistorico(item));
      acoesTd.classList.add('acoes-assinatura', 'acoes-padrao');
      acoesTd.append(botaoVisualizar, botaoEditar, botaoHistorico, botaoExcluir);
    },
    renderCelula: (item, idColuna, celula, estado, desenhar, api) => {
      if (idColuna === 'titulo') {
        const linkDownloadDoRegistro = document.createElement('a');
        linkDownloadDoRegistro.href = '#';
        linkDownloadDoRegistro.title = item.titulo;
        linkDownloadDoRegistro.textContent = item.titulo;
        linkDownloadDoRegistro.onclick = async linkDownloadDaTela => {
          linkDownloadDaTela.preventDefault();
          try {
            if (item.legado) {
              await abrirHistorico(item);
              return;
            }
            const resposta = await fetch(`/api/envelopes/${item.id}/pdf`, {
              headers: obterCredenciais()
            });
            if (!resposta.ok) throw new Error(`Erro ${resposta.status} ao baixar PDF`);
            const endereco = URL.createObjectURL(await resposta.blob());
            const link = document.createElement('a');
            link.href = endereco;
            link.download = item.titulo.toLowerCase().endsWith('.pdf') ? item.titulo : `${item.titulo}.pdf`;
            link.click();
            setTimeout(() => URL.revokeObjectURL(endereco), 1000);
          } catch (erro) {
            aviso(erro.message, true);
          }
        };
        celula.append(linkDownloadDoRegistro);
      } else if (idColuna === 'status') {
        const span = document.createElement('span');
        const situacaoAtual = (item.status || '').toLowerCase();
        let statusClass = '';
        if (situacaoAtual.includes('pendente')) statusClass = 'status-pendente';else if (situacaoAtual.includes('progresso') || situacaoAtual.includes('andamento') || situacaoAtual.includes('certificado')) statusClass = 'status-progresso';else if (situacaoAtual.includes('assinado') || situacaoAtual.includes('concluido') || situacaoAtual.includes('finalizada')) statusClass = 'status-assinado';
        span.className = `chip ${statusClass}`;
        span.textContent = item.status;
        celula.append(span);
      } else if (idColuna === 'signatarios' && item.signatarios && Array.isArray(item.signatarios)) {
        item.signatarios.forEach(assinatura => {
          const linkDownloadDoRegistro = document.createElement('a');
          linkDownloadDoRegistro.href = '#';
          linkDownloadDoRegistro.className = 'badge-roca-neon';
          linkDownloadDoRegistro.textContent = assinatura.nome || assinatura;
          celula.append(linkDownloadDoRegistro);
        });
      } else if (idColuna === 'data') {
        celula.textContent = item.data ? new Intl.DateTimeFormat('pt-BR', {
          dateStyle: 'short',
          timeStyle: 'short'
        }).format(new Date(item.data)) : '';
      } else {
        const valor = item[idColuna];
        if (typeof valor === 'string' && valor.includes(',')) {
          valor.split(',').forEach(linkDownload => {
            const linkDownloadDoRegistro = document.createElement('a');
            linkDownloadDoRegistro.href = '#';
            linkDownloadDoRegistro.className = 'badge-roca-neon';
            linkDownloadDoRegistro.textContent = linkDownload.trim();
            celula.append(linkDownloadDoRegistro);
          });
        } else {
          celula.textContent = valor ?? '';
        }
      }
    }
  });
  porId('fonte-assinaturas').addEventListener('change', () => {
    tabela.limparSelecao();
    tabela.carregar();
  });
  FirmaTrator.configurarLargura(porId('pagina-documentos'), porId('alternar-largura'), 'assineai-largura-documentos-v2');
  const janelaExportacao = porId('modal-exportacao');
  const botaoExportar = porId('exportar-dados');
  const mensagemExportacao = porId('export-msg');
  const quantidadeExportacao = porId('export-qtd');
  const confirmacaoExportacao = porId('export-concordo');
  const botoesExportacao = document.querySelectorAll('#botoes-formato-export button');
  botaoExportar.addEventListener('click', () => {
    const estado = tabela.getEstado();
    const temFiltro = Object.keys(estado.filtros || {}).length > 0;
    const quantidade = tabela.dados().length;
    if (temFiltro) {
      mensagemExportacao.textContent = 'Filtros estão aplicados. Confirme a quantidade de registros a serem exportados.';
    } else {
      mensagemExportacao.textContent = 'Não há filtros aplicados. Tem certeza que deseja exportar todos os registros?';
    }
    quantidadeExportacao.textContent = quantidade;
    confirmacaoExportacao.checked = false;
    botoesExportacao.forEach(botao => botao.disabled = true);
    janelaExportacao.showModal();
  });
  confirmacaoExportacao.addEventListener('change', eventoAtual => {
    botoesExportacao.forEach(botao => botao.disabled = !eventoAtual.target.checked);
  });
  porId('btn-cancelar-export').addEventListener('click', () => {
    janelaExportacao.close();
  });
  botoesExportacao.forEach(botao => botao.addEventListener('click', eventoAtual => {
    const formato = eventoAtual.target.dataset.formato;
    if (formato === 'pdf') {
      window.print();
    } else {
      aviso(`Exportação para ${formato.toUpperCase()} não implementada`);
    }
    janelaExportacao.close();
  }));
  const janelaImportacao = porId('modal-importacao');
  porId('importar-dados').addEventListener('click', () => janelaImportacao.showModal());
  porId('btn-cancelar-import').addEventListener('click', () => janelaImportacao.close());
  const inputImport = porId('input-arquivo-importacao');
  const areaArrastar = inputImport.parentElement;
  areaArrastar.addEventListener('dragover', eventoAtual => {
    eventoAtual.preventDefault();
    areaArrastar.style.borderColor = 'var(--primaria, #007bff)';
  });
  areaArrastar.addEventListener('dragleave', eventoAtual => {
    eventoAtual.preventDefault();
    areaArrastar.style.borderColor = 'var(--borda)';
  });
  areaArrastar.addEventListener('drop', eventoAtual => {
    eventoAtual.preventDefault();
    areaArrastar.style.borderColor = 'var(--borda)';
    if (eventoAtual.dataTransfer.files && eventoAtual.dataTransfer.files.length > 0) {
      inputImport.files = eventoAtual.dataTransfer.files;
      aviso(`Arquivo ${eventoAtual.dataTransfer.files[0].name} selecionado. Importação não implementada.`);
      janelaImportacao.close();
    }
  });
  inputImport.addEventListener('change', () => {
    if (inputImport.files && inputImport.files.length > 0) {
      aviso(`Arquivo ${inputImport.files[0].name} selecionado. Importação não implementada.`);
      janelaImportacao.close();
    }
  });
  const avisoInicial = porId('lista-toast');
  avisoInicial.dataset.firmaTipo = 'info';
  avisoInicial.textContent = 'Conecte o token no rodapé para carregar os registros.';
  avisoInicial.hidden = false;

  // preenchimento de credenciais salvas ou dados do perfil de teste local
  const chaveGuardada = localStorage.getItem('assineai-chave');
  const tokenGuardado = localStorage.getItem('assineai-token');
  if (chaveGuardada) porId('lista-chave').value = chaveGuardada;
  if (tokenGuardado) porId('lista-token').value = tokenGuardado;
  const botaoTeste = porId('usar-credenciais-teste');
  if (document.body.dataset.tokenTeste && document.body.dataset.chaveTeste) {
    if (botaoTeste) {
      botaoTeste.hidden = false;
      botaoTeste.addEventListener('click', () => {
        porId('lista-chave').value = document.body.dataset.chaveTeste;
        porId('lista-token').value = document.body.dataset.tokenTeste;
        conectar();
      });
    }
    // se estiver vazio no ambiente de teste, preenche automaticamente
    if (!porId('lista-chave').value && !porId('lista-token').value) {
      porId('lista-chave').value = document.body.dataset.chaveTeste;
      porId('lista-token').value = document.body.dataset.tokenTeste;
    }
  }
})();
