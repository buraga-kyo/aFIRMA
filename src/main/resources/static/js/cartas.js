(() => {
  const porId = id => document.getElementById(id);
  const pagina = porId('pagina-documentos');
  const painel = porId('modelos-email');
  const endereco = '/api/assineai/modelos-email';
  const data = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
  let precisaRecarregar = false;
  let retorno;
  let geracao = 0;

  function aviso(texto, erro = false) {
    const origem = porId('lista-toast');
    origem.dataset.firmaTipo = erro ? 'erro' : 'sucesso';
    origem.textContent = texto;
    origem.hidden = !texto;
  }

  function abrir(id, origem, acao, momento = 'verificacao') {
    aviso('');
    retorno = origem;
    pagina.classList.add('cartas-editando');
    window.CartasEditor.abrir(id, acao, momento);
  }

  function voltar() {
    window.CartasEditor.fechar();
    pagina.classList.remove('cartas-editando');
    if (precisaRecarregar) {
      precisaRecarregar = false;
      tabela.carregar().then(() => porId('cartas-novo').focus());
    } else {
      retorno?.focus();
    }
  }

  const tabela = FirmaTrator.montar({
    elemento: '#tabela-montagem',
    containerId: 'tabela',
    storageId: 'assineai-cartas',
    rotulo: 'Templates de e-mail',
    recursos: { edicao: false, detalhes: false },
    endpoint: endereco,
    buscaEndpoint: endereco + '/plano-busca',
    larguraAcoes: 216,
    slots: {
      abasFinais: porId('tabela-telas').content.cloneNode(true),
      comandos: porId('tabela-comandos').content.cloneNode(true),
      rodape: porId('tabela-rodape').content.cloneNode(true)
    },
    toast: aviso,
    colunas: [
      { id: 'id', nome: 'ID', tipo: 'numero', largura: 90 },
      { id: 'titulo', nome: 'TÍTULO', tipo: 'texto', largura: 275 },
      { id: 'etapa', nome: 'USADO EM', tipo: 'enum', largura: 205 },
      { id: 'status', nome: 'STATUS', tipo: 'enum', largura: 155 },
      { id: 'data', nome: 'ÚLTIMA ALTERAÇÃO', tipo: 'data', largura: 190,
        formatar: valor => valor ? data.format(new Date(valor)) : '' }
    ],
    renderCelula(item, coluna, celula) {
      if (coluna === 'titulo') {
        const link = document.createElement('a');
        link.href = '#';
        link.textContent = item.titulo;
        link.addEventListener('click', evento => {
          evento.preventDefault();
          abrir(item.id, link, undefined, item.momento);
        });
        celula.append(link);
      } else if (coluna === 'status') {
        const selo = document.createElement('span');
        const classe = { Ativo: 'status-assinado', Rascunho: 'status-pendente', Inativo: 'status-progresso' };
        selo.className = 'chip ' + (classe[item.status] || '');
        selo.textContent = item.status;
        celula.append(selo);
      } else {
        celula.textContent = coluna === 'data' && item.data ? data.format(new Date(item.data)) : item[coluna] ?? '';
      }
    },
    renderAcoes(item, destino, estado, desenhar, api, instancia) {
      const acao = (tipo, rotulo, abrirComo) => FirmaTabela.botaoAcao(tipo, rotulo + item.titulo,
        evento => abrir(item.id, evento.currentTarget, abrirComo, item.momento));
      const duplicar = document.createElement('button');
      duplicar.type = 'button';
      duplicar.className = 'icone';
      duplicar.textContent = '⧉';
      duplicar.title = 'Duplicar ' + item.titulo;
      duplicar.setAttribute('aria-label', duplicar.title);
      duplicar.addEventListener('click', async evento => {
        evento.stopPropagation();
        duplicar.disabled = true;
        const atual = geracao;
        try {
          const copia = await instancia.requisitar(endereco + '/' + item.momento + '/' + item.id + '/duplicar', { method: 'POST' });
          if (atual !== geracao) return;
          precisaRecarregar = true;
          abrir(copia.id, duplicar, undefined, copia.momento);
        } catch (erro) {
          if (atual === geracao) aviso(erro.message, true);
        } finally {
          duplicar.disabled = false;
        }
      });
      const excluir = FirmaTabela.botaoAcao('remover', 'Excluir ' + item.titulo, async () => {
        try {
          await instancia.requisitar(endereco + '/' + item.momento + '/' + item.id, { method: 'DELETE' });
          await instancia.carregar();
          aviso('Template excluído.');
        } catch (erro) {
          aviso(erro.message, true);
        }
      });
      excluir.disabled = item.status === 'Ativo';
      if (excluir.disabled) excluir.title = 'Publique outro template para excluir o ativo.';
      destino.classList.add('acoes-assinatura', 'acoes-padrao', 'acoes-cartas');
      destino.append(acao('visualizar', 'Prévia de ', 'previa'), acao('editar', 'Editar '),
        duplicar, acao('historico', 'Histórico de ', 'historico'), excluir);
    },
    onRender(instancia) {
      const area = instancia.raiz.querySelector('.tabela-painel');
      if (painel.parentElement !== area) area.prepend(painel);
    }
  });

  painel.addEventListener('cartas:atualizado', () => { precisaRecarregar = true; });
  painel.addEventListener('cartas:voltar', voltar);
  porId('cartas-novo').addEventListener('click', evento => abrir(null, evento.currentTarget));
  tabela.no('busca-form').addEventListener('submit', () => { if (!painel.hidden) voltar(); }, true);
  document.addEventListener('firma:acesso-conectado', () => {
    geracao++;
    tabela.carregar();
  });
  document.addEventListener('firma:desconectado', () => {
    geracao++;
    precisaRecarregar = false;
    window.CartasEditor.fechar();
    pagina.classList.remove('cartas-editando');
    tabela.limparDados();
  });
  FirmaTrator.configurarLargura(pagina, porId('alternar-largura'), 'assineai-largura-cartas');
})();
