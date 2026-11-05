(() => {
  const porId = id => document.getElementById(id),
    slug = document.body.dataset.modulo;
  let contexto,
    modulo,
    tabela,
    idAtual = null;
  const registrosGuardados = new Map();
  let termoBusca = '';
  function slotsDoTrator() {
    return {
      comandos: destino => destino.append(document.querySelector('.firma-chat-navegacao'), porId('trator-comandos').content.cloneNode(true)),
      abasFinais: destino => destino.append(porId('firma-chat-grupos')),
      rodape: porId('trator-rodape').content.cloneNode(true)
    };
  }
  tabela = FirmaTrator.montar({
    elemento: '#trator-montagem',
    colunas: [{id: 'cadastro', nome: 'REGISTROS'}],
    dados: [],
    recursos: {buscaIA: false, edicao: false},
    buscarNoServidor: () => {},
    slots: slotsDoTrator(),
    textos: {vazio: 'Conecte o token no rodapé para carregar os registros.'}
  });
  tabela.raiz.querySelectorAll('input,select,button').forEach(controle => controle.disabled = true);
  const nomeCampo = item => ({
    ContatoNumero: 'Telefone',
    AtendenteUsuarioId: 'Usuário',
    UsuarioNomeCompleto: 'Nome completo',
    ClienteCpfCnpj: 'CPF / CNPJ',
    CanalGUID: 'Identificador do canal',
    MensagemRapidaGUID: 'Identificador',
    TagGUID: 'Identificador',
    MensagemTemplateCorpo: 'Conteúdo da mensagem',
    ContatoJidWhatsApp: 'Identificador do WhatsApp'
  })[item] || item.replace(/Id$/, '').replace(new RegExp('^' + (modulo?.tabela || '') + '(?=[A-Z])'), '').replace(/([a-zà-ú])([A-Z])/g, '$1 $2').replace(/([A-Z])([A-Z][a-z])/g, '$1 $2').replace(/^./, itemAtual => itemAtual.toUpperCase());
  async function api(caminho, metodo = 'GET', dados) {
    const acesso = window.FirmaAcesso;
    if (!acesso) throw Error('Conecte um token válido no rodapé.');
    const resposta = await fetch('/api/firma/' + caminho, {
      method: metodo,
      headers: {
        Authorization: `Bearer ${acesso.token}`,
        ...(dados ? {
          'Content-Type': 'application/json'
        } : {})
      },
      ...(dados ? {
        body: JSON.stringify(dados)
      } : {}),
      cache: 'no-store'
    });
    if (acesso !== window.FirmaAcesso) throw Error('A conexão foi alterada.');
    if (resposta.status === 401) document.dispatchEvent(new CustomEvent('firma:desconectado'));
    const dadosAtuais = await resposta.json().catch(() => ({}));
    if (!resposta.ok) throw Error(dadosAtuais.mensagem || dadosAtuais.message || `Operação recusada (${resposta.status}).`);
    return dadosAtuais;
  }
  function aviso(texto) {
    porId('relacionamento-aviso').textContent = texto;
  }
  const descricao = registroAtual => registroAtual.UsuarioNomeCompleto || registroAtual[Object.keys(registroAtual).find(chave => chave.endsWith('Nome'))] || registroAtual.ContatoNumero || registroAtual.UsuarioEmail || registroAtual.PermissaoNome || registroAtual.MensagemRapidaTexto || `Registro ${registroAtual.id}`;
  async function opcoes(slug) {
    if (!registrosGuardados.has(slug)) registrosGuardados.set(slug, (await api('registros/' + slug + '?tamanho=100')).itens);
    return registrosGuardados.get(slug);
  }
  async function carregar() {
    if (tabela) await tabela.carregar();
  }
  const rotulos = {
    conectar: 'Conectar / QR',
    qr: 'QR code',
    estado: 'Estado',
    desconectar: 'Desconectar',
    importar: 'Importar conversas',
    'execucoes-ia': 'Execuções',
    publicar: 'Publicar na Meta',
    'excluir-meta': 'Excluir na Meta',
    sincronizar: 'Atualizar status',
    detalhes: 'Detalhes',
    cancelar: 'Cancelar',
    'repetir-falhas': 'Repetir falhas',
    execucoes: 'Execuções',
    entregas: 'Entregas',
    consultar: 'Consultar',
    revogar: 'Revogar',
    remover: 'Excluir registro'
  };
  function montar() {
    const referencias = {};
    const acoesControle = (modulo.acoes || []).filter(acaoPermitida => !['qr', 'remover'].includes(acaoPermitida));
    const controlesDisponiveis = registroAtual => acoesControle.filter(acaoPermitida => !(slug === 'canais' && registroAtual.CanalProvedorAPI === 'Meta' && ['conectar', 'desconectar', 'importar'].includes(acaoPermitida)));
    const executar = async (acao, registroAtual) => {
      try {
        await operar(acao, registroAtual);
      } catch (evento) {
        aviso(evento.message);
      }
    };
    const iconesControle = {
      conectar: '⌁',
      estado: '◉',
      desconectar: '⏻',
      importar: '↓',
      publicar: '↗',
      sincronizar: '↻',
      'excluir-meta': '✖',
      cancelar: '■',
      'repetir-falhas': '↻',
      detalhes: '▤',
      execucoes: '◷',
      'execucoes-ia': '◷',
      entregas: '↗',
      consultar: '⌕',
      revogar: '⏻'
    };
    tabela = FirmaTrator.montar({
      elemento: '#trator-montagem',
      containerId: 'relacionamento',
      storageId: 'trator-' + contexto.clienteId + '-' + contexto.usuarioId + '-' + slug,
      modo: 'servidor',
      rotulo: modulo.titulo,
      recursos: {
        buscaIA: false,
        edicao: false
      },
      slots: slotsDoTrator(),
      buscarNoServidor: consulta => {
        termoBusca = consulta;
        return carregar();
      },
      colunas: [...(acoesControle.length ? [{
        id: 'controles',
        nome: 'CONTROLES',
        detalhes: false,
        largura: 390,
        larguraMinima: 240,
        editavel: false,
        ocultavel: false,
        filtrosHeader: false,
        ordenacao: false,
        reordenacao: false,
        formatar: () => '',
        render: (registroAtual, celula) => {
          celula.classList.add('coluna-controles');
          const grupoAtual = document.createElement('div');
          grupoAtual.className = 'trator-controles';
          grupoAtual.setAttribute('role', 'group');
          grupoAtual.setAttribute('aria-label', 'Controles de ' + descricao(registroAtual));
          for (const acao of controlesDisponiveis(registroAtual)) grupoAtual.append(FirmaTabela.botaoControle(rotulos[acao] || acao, iconesControle[acao] || '↗', () => executar(acao, registroAtual)));
          if (!grupoAtual.childNodes.length) grupoAtual.textContent = 'Sem ações';
          celula.append(grupoAtual);
        }
      }] : []), ...['id', ...modulo.colunas].map(item => ({
        id: item,
        nome: item === 'id' ? 'ID' : nomeCampo(item).toUpperCase(),
        tipo: item === 'id' ? 'numero' : 'texto',
        largura: item === 'id' ? 90 : 220,
        formatar: valor => referencias[item]?.get(valor) || (valor == null ? 'Não informado' : typeof valor === 'boolean' ? valor ? 'Ativo' : 'Inativo' : typeof valor === 'object' ? JSON.stringify(valor) : String(valor))
      }))],
      carregarDados: async consulta => {
        const elementoAtual = await api('registros/' + slug + '?' + new URLSearchParams({
          pagina: consulta.pagina,
          tamanho: consulta.tamanho,
          busca: termoBusca,
          ordem: JSON.stringify(consulta.ordem),
          filtros: JSON.stringify(consulta.filtros)
        }));
        for (const item of modulo.campos) {
          if (modulo.colunas.includes(item.nome) && item.moduloReferencia && !referencias[item.nome]) try {
            referencias[item.nome] = new Map((await opcoes(item.moduloReferencia)).map(registroAtual => [registroAtual.id, descricao(registroAtual)]));
          } catch {}
        }
        porId('relacionamento-total').textContent = elementoAtual.total + ' registros';
        return elementoAtual;
      },
      toast: aviso,
      renderAcoes: (registroAtual, celula) => {
        celula.classList.add('acoes-padrao');
        celula.append(FirmaTabela.botaoAcao('visualizar', 'Visualizar ' + descricao(registroAtual)));
        if (modulo.editavel) celula.append(FirmaTabela.botaoAcao('editar', 'Editar ' + descricao(registroAtual), async () => {
          try {
            await editar(registroAtual);
          } catch (evento) {
            aviso(evento.message);
          }
        }));
        if ((modulo.acoes || []).includes('remover')) celula.append(FirmaTabela.botaoAcao('remover', 'Excluir ' + descricao(registroAtual), () => executar('remover', registroAtual)));
      }
    });
    porId('relacionamento-novo').onclick = () => editar(null).catch(evento => aviso(evento.message));
    porId('relacionamento-atualizar').onclick = () => {
      registrosGuardados.clear();
      carregar();
    };
    FirmaTrator.configurarLargura(porId('trator-talkative'), porId('alternar-largura'), 'firma-cadastros-largura-v1');
    porId('relacionamento-titulo').textContent = modulo.titulo;
    porId('relacionamento-novo').hidden = !modulo.criavel || slug === 'cliente';
    porId('relacionamento-novo').textContent = '+ ' + (slug === 'canais' ? 'Canal' : 'Novo registro');
    aviso('');
    carregar();
  }
  async function editar(registro) {
    idAtual = registro?.id || null;
    porId('relacionamento-form-titulo').textContent = (idAtual ? 'Editar ' : 'Novo registro · ') + modulo.titulo;
    porId('relacionamento-form-grupo').textContent = modulo.grupo;
    porId('relacionamento-form').querySelector('[type=submit]').textContent = slug === 'disparos' ? 'Agendar disparo' : slug === 'chaves' && !idAtual ? 'Gerar chave' : 'Salvar alterações';
    porId('relacionamento-form-erro').textContent = '';
    const campos = porId('relacionamento-campos');
    campos.replaceChildren();
    for (const item of modulo.campos) {
      const rotuloAtual = document.createElement('label');
      rotuloAtual.textContent = nomeCampo(item.nome);
      let elementoDoRegistro;
      const referencia = item.moduloReferencia || (item.nome === 'AtendenteUsuarioId' ? 'usuarios' : null);
      if (item.nome === 'CanalProvedorAPI') {
        elementoDoRegistro = document.createElement('select');
        elementoDoRegistro.add(new Option('WhatsApp · QR code (Baileys)', 'Baileys'));
        elementoDoRegistro.add(new Option('WhatsApp Business · Meta', 'Meta'));
      } else if (referencia) {
        elementoDoRegistro = document.createElement('select');
        elementoDoRegistro.add(new Option('Selecione…', ''));
        for (const registroAtual of await opcoes(referencia)) elementoDoRegistro.add(new Option(descricao(registroAtual), registroAtual.id));
        if (registro?.[item.nome] && !Array.from(elementoDoRegistro.options).some(opcao => opcao.value === String(registro[item.nome]))) {
          const atual = await api('registros/' + referencia + '/' + registro[item.nome]);
          elementoDoRegistro.add(new Option(descricao(atual), atual.id));
        }
      } else if (item.tipo === 'JSONB' || item.tipo === 'JSON' || item.tipo === 'TEXT') {
        elementoDoRegistro = document.createElement('textarea');
        if (item.tipo === 'JSONB' || item.tipo === 'JSON') elementoDoRegistro.placeholder = 'Configuração em JSON';
      } else {
        elementoDoRegistro = document.createElement('input');
        elementoDoRegistro.type = item.tipo === 'BOOLEAN' ? 'checkbox' : item.tipo === 'PASSWORD' ? 'password' : item.tipo === 'INTEGER' || item.tipo.includes('NUMERIC') || item.tipo.includes('FLOAT') || item.tipo.includes('DOUBLE') ? 'number' : item.tipo.includes('TIMESTAMP') ? 'datetime-local' : /Email/.test(item.nome) ? 'email' : 'text';
        if (elementoDoRegistro.type === 'number') elementoDoRegistro.step = item.tipo === 'INTEGER' ? '1' : 'any';
        if (item.tipo.startsWith('VARCHAR')) elementoDoRegistro.maxLength = Number(item.tipo.match(/\d+/)[0]);
      }
      elementoDoRegistro.name = item.nome;
      elementoDoRegistro.dataset.tipo = item.tipo;
      if (item.sensivel) {
        elementoDoRegistro.dataset.sensivel = "true";
        if (idAtual) elementoDoRegistro.placeholder = "Deixe em branco para manter a configuração atual";
      }
      if (item.tipo === 'PASSWORD') elementoDoRegistro.autocomplete = 'new-password';
      const valoresIniciais = {
        DisparoMassaMensagensPorDisparos: 1,
        DisparoMassaIntervaloEntreDisparos: 5,
        DisparoMassaHoraReinicio: '00:00',
        DisparoMassaUltimoHorario: '23:59',
        DisparoMassaDiasEnvio: '0,1,2,3,4,5,6',
        DisparoMassaColunaDestinatario: 'telefone',
        ChatbotDefinicaoProvedor: 'firma',
        ChatbotDefinicaoFuncionamento: {
          Integral: true
        },
        InteligenciaArtificialAtivador: 'manual-chat',
        CanalProvedorAPI: 'Baileys'
      };
      const valor = registro?.[item.nome] ?? (!idAtual ? valoresIniciais[item.nome] : undefined);
      if (elementoDoRegistro.type === 'checkbox') elementoDoRegistro.checked = valor == null ? !/Bloqueado|Deletada|Editada/.test(item.nome) : valor === true;else if (valor != null) elementoDoRegistro.value = item.tipo === 'JSONB' || item.tipo === 'JSON' ? JSON.stringify(valor, null, 2) : item.tipo.includes('TIMESTAMP') ? new Date(valor).toISOString().slice(0, 16) : String(valor);
      if (item.obrigatorio && !(idAtual && item.sensivel)) elementoDoRegistro.required = true;
      rotuloAtual.append(elementoDoRegistro);
      if (referencia) {
        const busca = document.createElement('input');
        busca.type = 'search';
        busca.placeholder = 'Buscar ' + nomeCampo(item.nome).toLowerCase();
        busca.setAttribute('aria-label', busca.placeholder);
        let espera,
          versao = 0;
        busca.oninput = () => {
          clearTimeout(espera);
          espera = setTimeout(async () => {
            const valor = ++versao;
            try {
              const dadosAtuais = await api('registros/' + referencia + '?tamanho=100&busca=' + encodeURIComponent(busca.value));
              if (valor !== versao) return;
              const id = elementoDoRegistro.value,
                anterior = elementoDoRegistro.selectedOptions[0]?.textContent;
              elementoDoRegistro.replaceChildren(new Option('Selecione…', ''));
              dadosAtuais.itens.forEach(registroAtual => elementoDoRegistro.add(new Option(descricao(registroAtual), registroAtual.id)));
              if (id && !dadosAtuais.itens.some(registroAtual => String(registroAtual.id) === id)) elementoDoRegistro.add(new Option(anterior, id));
              elementoDoRegistro.value = id;
            } catch (evento) {
              porId('relacionamento-form-erro').textContent = evento.message;
            }
          }, 300);
        };
        rotuloAtual.append(busca);
      }
      campos.append(rotuloAtual);
    }
    porId('relacionamento-editar').showModal();
  }
  porId('relacionamento-form').onsubmit = async evento => {
    evento.preventDefault();
    const botao = evento.target.querySelector('[type=submit]');
    botao.disabled = true;
    try {
      const dados = {};
      for (const elementoDoRegistro of porId('relacionamento-campos').querySelectorAll('[name]')) {
        if ((elementoDoRegistro.dataset.tipo === 'PASSWORD' || elementoDoRegistro.dataset.sensivel === 'true') && !elementoDoRegistro.value) continue;
        dados[elementoDoRegistro.name] = elementoDoRegistro.type === 'checkbox' ? elementoDoRegistro.checked : elementoDoRegistro.value === '' ? null : elementoDoRegistro.dataset.tipo === 'JSONB' || elementoDoRegistro.dataset.tipo === 'JSON' ? JSON.parse(elementoDoRegistro.value) : elementoDoRegistro.type === 'number' || elementoDoRegistro.tagName === 'SELECT' && elementoDoRegistro.name !== 'CanalProvedorAPI' ? Number(elementoDoRegistro.value) : elementoDoRegistro.dataset.tipo.includes('TIMESTAMP') ? new Date(elementoDoRegistro.value).toISOString() : elementoDoRegistro.value;
      }
      const resultado = await api(`registros/${slug}` + (idAtual ? '/' + idAtual : ''), idAtual ? 'PUT' : 'POST', dados);
      if (resultado.chaveGerada) mostrar('Chave criada · guarde agora', resultado.chaveGerada.toString());
      const conectarNovo = slug === 'canais' && !idAtual && resultado.CanalProvedorAPI !== 'Meta';
      registrosGuardados.clear();
      porId('relacionamento-editar').close();
      await carregar();
      if (conectarNovo) abrirCanal(resultado, 'conectar');
    } catch (erro) {
      porId('relacionamento-form-erro').textContent = erro.message;
    } finally {
      botao.disabled = false;
    }
  };
  function mostrar(titulo, dados) {
    porId('relacionamento-resultado-titulo').textContent = titulo;
    const conteudo = porId('relacionamento-resultado-conteudo');
    conteudo.replaceChildren();
    if (dados?.qr) {
      const imagem = document.createElement('img');
      imagem.alt = 'QR code para conectar o WhatsApp';
      imagem.src = dados.qr;
      imagem.style.maxWidth = '100%';
      conteudo.append(imagem);
    } else {
      const textoFormatado = document.createElement('pre');
      textoFormatado.textContent = typeof dados === 'string' ? dados : JSON.stringify(dados, null, 2);
      conteudo.append(textoFormatado);
    }
    porId('relacionamento-resultado').showModal();
  }
  async function operar(acao, registroAtual) {
    if (slug === 'canais' && ['conectar', 'qr', 'importar'].includes(acao)) {
      await abrirCanal(registroAtual, acao);
      return;
    }
    if (acao === 'detalhes') {
      mostrar('Detalhes do disparo', await api('registros/disparos/' + registroAtual.id));
      return;
    }
    if (acao === 'execucoes-ia') {
      mostrar('Execuções da IA', await api('ia/' + registroAtual.id + '/execucoes'));
      return;
    }
    if (acao === 'execucoes') {
      mostrar('Execuções do bot', await api('bots/' + registroAtual.id + '/sessoes'));
      return;
    }
    if (acao === 'entregas') {
      mostrar('Entregas do webhook', await api('webhooks/' + registroAtual.id + '/entregas'));
      return;
    }
    if (acao === 'consultar') {
      porId('relacionamento-integracao-nome').value = registroAtual.IntegracaoNome;
      porId('relacionamento-integracao').showModal();
      return;
    }
    let resultado;
    if (acao === 'remover') {
      resultado = await api('registros/' + slug + '/' + registroAtual.id, 'DELETE');
      if (resultado.excluido !== true) throw Error('A exclusão não foi confirmada pelo servidor.');
      registrosGuardados.clear();
      const atualizada = await tabela.carregar();
      aviso(atualizada ? 'Registro excluído.' : 'Registro excluído. Não foi possível atualizar a tabela; clique em Atualizar.');
      return;
    } else if (acao === 'revogar') resultado = await api('sessoes/' + registroAtual.id, 'DELETE');else resultado = await api(slug + '/' + registroAtual.id + '/' + acao, 'POST');
    if (resultado?.qr || acao === 'estado') mostrar('Canal', resultado);
    await carregar();
    aviso('Operação concluída.');
  }
  porId('relacionamento-resultado-fechar').onclick = () => porId('relacionamento-resultado').close();
  porId('relacionamento-integracao-fechar').onclick = () => porId('relacionamento-integracao').close();
  porId('relacionamento-integracao-form').onsubmit = async evento => {
    evento.preventDefault();
    try {
      const nome = porId('relacionamento-integracao-nome').value,
        acao = porId('relacionamento-integracao-acao').value,
        filtros = JSON.parse(porId('relacionamento-integracao-filtros').value || '{}');
      mostrar('Resultado da consulta', await api('integracoes/' + encodeURIComponent(nome) + '/' + acao, 'POST', filtros));
    } catch (evento) {
      aviso(evento.message);
    }
  };
  porId('relacionamento-fechar').onclick = porId('relacionamento-cancelar').onclick = () => porId('relacionamento-editar').close();
  function iniciar(item) {
    if (tabela) {
      porId('trator-montagem').before(document.querySelector('.firma-chat-navegacao'), porId('firma-chat-grupos'));
      tabela.destruir();
      tabela = null;
    }
    registrosGuardados.clear();
    contexto = item;
    termoBusca = "";
    modulo = item.modulos.find(moduloAtual => moduloAtual.slug === slug);
    if (!modulo) {
      aviso('Seu perfil não tem acesso a este cadastro.');
      return;
    }
    document.title = modulo.titulo + ' · Trator da FIRMA';
    montar();
  }
  let canalAtual,
    versaoCanal = 0,
    esperaCanal;
  const estados = {
    CONECTANDO: 'Conectando ao WhatsApp… aguardando o QR code.',
    QR_PENDENTE: 'QR disponível. Escaneie com seu WhatsApp.',
    CONECTADO: 'WhatsApp conectado!',
    DESCONECTADO: 'Canal desconectado. Clique em tentar novamente.'
  };
  async function abrirCanal(registroAtual, acao) {
    canalAtual = registroAtual;
    const versao = ++versaoCanal;
    clearTimeout(esperaCanal);
    porId('canal-conexao-titulo').textContent = registroAtual.CanalNome || 'Canal WhatsApp';
    porId('canal-conexao-qr').hidden = true;
    porId('canal-importacao').hidden = true;
    porId('canal-tentar').hidden = true;
    porId('canal-conexao-progresso').hidden = false;
    porId('canal-conexao-status').textContent = acao === 'importar' ? 'Consultando histórico do número…' : 'Preparando conexão…';
    if (!porId('canal-conexao').open) porId('canal-conexao').showModal();
    try {
      if (acao === 'conectar') await api('canais/' + registroAtual.id + '/conectar', 'POST');
      if (acao === 'importar') await api('canais/' + registroAtual.id + '/importar', 'POST');
      if (versao === versaoCanal) acompanharCanal(versao, Date.now());
    } catch (evento) {
      if (versao !== versaoCanal) return;
      porId('canal-conexao-status').textContent = evento.message;
      porId('canal-conexao-progresso').hidden = true;
      porId('canal-tentar').hidden = false;
    }
  }
  async function acompanharCanal(versao, inicio) {
    if (versao !== versaoCanal || !porId('canal-conexao').open) return;
    try {
      const dadosAtuais = await api('canais/' + canalAtual.id + '/conexao', 'POST');
      if (versao !== versaoCanal) return;
      porId('canal-conexao-status').textContent = dadosAtuais.erro || estados[dadosAtuais.estado] || dadosAtuais.estado;
      porId('canal-conexao-progresso').hidden = ['CONECTADO', 'DESCONECTADO'].includes(dadosAtuais.estado);
      porId('canal-tentar').hidden = dadosAtuais.estado !== 'DESCONECTADO';
      porId('canal-conexao-qr').hidden = !dadosAtuais.qr;
      if (dadosAtuais.qr) {
        porId('canal-conexao-qr').src = dadosAtuais.qr;
        inicio = Date.now();
      }
      porId('canal-conexao-ajuda').hidden = dadosAtuais.estado === 'CONECTADO';
      if (dadosAtuais.estado === 'CONECTADO') {
        porId('canal-importacao').hidden = false;
        const cabecalho = await api('canais/' + canalAtual.id + '/historico-status', 'POST');
        if (versao !== versaoCanal) return;
        const paragrafo = cabecalho.ponte || {};
        porId('canal-importacao-status').textContent = `${cabecalho.conversas || 0} conversas · ${cabecalho.mensagens || 0} mensagens · ${cabecalho.duplicadas || 0} já existentes · ${cabecalho.ignoradas || 0} itens não suportados. ` + (paragrafo.progresso != null ? 'Sincronização: ' + paragrafo.progresso + '%. ' : '') + (paragrafo.mensagem || 'Aguardando o histórico disponibilizado pelo WhatsApp.') + (paragrafo.falhas ? ' Há ' + paragrafo.falhas + ' lote(s) aguardando nova tentativa.' : '');
      }
      if (Date.now() - inicio > 50000 && dadosAtuais.estado === 'CONECTANDO') {
        porId('canal-conexao-status').textContent = 'O WhatsApp está demorando a gerar o QR. Tente novamente.';
        porId('canal-tentar').hidden = false;
        porId('canal-conexao-progresso').hidden = true;
        return;
      }
      esperaCanal = setTimeout(() => acompanharCanal(versao, inicio), 2000);
    } catch (evento) {
      if (versao !== versaoCanal) return;
      porId('canal-conexao-status').textContent = evento.message;
      porId('canal-conexao-progresso').hidden = true;
      porId('canal-tentar').hidden = false;
    }
  }
  porId('canal-conexao-fechar').onclick = () => porId('canal-conexao').close();
  porId('canal-conexao').addEventListener('close', () => {
    versaoCanal++;
    clearTimeout(esperaCanal);
    carregar();
  });
  porId('canal-tentar').onclick = () => abrirCanal(canalAtual, 'conectar');
  porId('canal-importar').onclick = async () => {
    const elementoDaTela = porId('canal-importar');
    elementoDaTela.disabled = true;
    porId('canal-importacao-status').textContent = 'Solicitando mais histórico ao WhatsApp…';
    try {
      await api('canais/' + canalAtual.id + '/importar', 'POST');
    } catch (evento) {
      porId('canal-importacao-status').textContent = evento.message;
    } finally {
      elementoDaTela.disabled = false;
    }
  };
  document.addEventListener('firma:desconectado', () => {
    contexto = null;
    registrosGuardados.clear();
    tabela?.limparDados();
    tabela?.raiz.querySelectorAll('input,select,button').forEach(controle => controle.disabled = true);
    porId('relacionamento-total')?.replaceChildren();
    document.querySelectorAll('dialog[open]').forEach(dadosAtuais => dadosAtuais.close());
    aviso('Conecte o token no rodapé para carregar os dados.');
  });
  document.addEventListener('firma:contexto', evento => iniciar(evento.detail));
  if (window.FirmaContexto) iniciar(window.FirmaContexto);
})();
