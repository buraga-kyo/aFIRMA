(() => {
  const porId = id => document.getElementById(id);
  let reabertura = null;
  let versaoLista = 0;
  let resposta = null,
    conversaAtual = null,
    contexto,
    atual = null,
    todos = [],
    cadastrosGuardados = {},
    espera,
    versao = 0,
    temMaisMensagens = false;
  async function api(caminho, metodo = 'GET', dados) {
    const acesso = window.FirmaAcesso;
    if (!contexto || !acesso) throw Error('Conecte um token válido no rodapé.');
    const respostaAtual = await fetch('/api/firma/' + caminho, {
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
    if (acesso !== window.FirmaAcesso) throw Error('A conexão foi alterada. Conecte novamente.');
    const dadosAtuais = await respostaAtual.json().catch(() => ({}));
    if (!respostaAtual.ok) {
      if (respostaAtual.status === 401) document.dispatchEvent(new CustomEvent('firma:desconectado'));
      throw Error(dadosAtuais.mensagem || dadosAtuais.message || `Operação recusada (${respostaAtual.status}).`);
    }
    return dadosAtuais;
  }
  async function registros(cadastro) {
    if (cadastrosGuardados[cadastro]) return cadastrosGuardados[cadastro];
    const itens = [];
    let pagina = 1,
      total = 0;
    do {
      const dadosAtuais = await api('registros/' + cadastro + '?' + new URLSearchParams({
        tamanho: 100,
        pagina
      }));
      itens.push(...dadosAtuais.itens);
      total = dadosAtuais.total;
      pagina++;
      if (!dadosAtuais.itens.length) break;
    } while (itens.length < total);
    return cadastrosGuardados[cadastro] = itens;
  }
  function nome(registro) {
    return registro.ContatoNome || registro.ContatoNumero || registro.CanalNome || registro.SetorNome || registro.AtendenteNome || `Registro ${registro.id}`;
  }
  function opcoes(elementoAtual, itens, selecionado) {
    elementoAtual.replaceChildren(new Option('Selecione…', ''));
    for (const registro of itens) elementoAtual.add(new Option(nome(registro), registro.id));
    if (selecionado) elementoAtual.value = selecionado;
  }
  function aviso(texto, erro = false) {
    porId('chat-aviso').dataset.firmaTipo = erro ? 'erro' : 'info';
    porId('chat-aviso').textContent = texto;
  }
  function podeEditarContato() {
    return Boolean(contexto?.modulos.find(modulo => modulo.slug === 'contatos')?.editavel);
  }
  async function salvarContato(item, alteracoes) {
    if (!podeEditarContato()) throw Error('Seu perfil não permite editar contatos.');
    const mapa = {
        contato: 'ContatoNome',
        telefone: 'ContatoNumero',
        email: 'ContatoEmail'
      },
      dados = {};
    for (const [campo, valor] of Object.entries(alteracoes)) if (mapa[campo]) dados[mapa[campo]] = String(valor).trim();
    if ('ContatoNome' in dados && !dados.ContatoNome) throw Error('Informe o nome do contato.');
    const contato = await api('registros/contatos/' + item.ContatoId, 'PUT', dados);
    todos = todos.map(registroAtual => registroAtual.ContatoId === item.ContatoId ? {
      ...registroAtual,
      ...dados,
      ContatoNome: contato.ContatoNome ?? dados.ContatoNome ?? registroAtual.ContatoNome,
      ContatoNumero: contato.ContatoNumero ?? dados.ContatoNumero ?? registroAtual.ContatoNumero,
      ContatoEmail: contato.ContatoEmail ?? dados.ContatoEmail ?? registroAtual.ContatoEmail
    } : registroAtual);
    await desenhar();
    return {
      ...alteracoes
    };
  }
  function desenharAcoes(item, celula) {
    celula.classList.add('acoes-padrao');
    const executar = executarAcao => async () => {
      try {
        await executarAcao();
      } catch (evento) {
        aviso(evento.message, true);
      }
    };
    celula.append(FirmaTabela.botaoAcao('visualizar', 'Visualizar ' + item.contato, executar(() => {
      tabela.detalhes(item);
      const abrirChat = document.createElement('button');
      abrirChat.type = 'button';
      abrirChat.textContent = 'ABRIR CONVERSA ↧';
      abrirChat.onclick = () => {
        tabela.dialogo?.close();
        aberta = true;
        abrir(item.id).catch(evento => aviso(evento.message, true));
      };
      tabela.dialogo.querySelector('.af-detalhes-acoes').append(abrirChat);
    })));
    const editar = FirmaTabela.botaoAcao('editar', 'Editar contato ' + item.contato);
    editar.disabled = !podeEditarContato();
    celula.append(editar);
    celula.append(FirmaTabela.botaoAcao('historico', 'Histórico de ' + item.contato, executar(() => historico(item.id))));
    const finalizado = item.AtendimentoStatus === 'finalizado';
    celula.append(FirmaTabela.botaoAcao(finalizado ? 'reativar' : 'finalizar', finalizado ? 'Reativar atendimento' : 'Finalizar atendimento', executar(async () => {
      if (finalizado) {
        await prepararInicio(item.id);
        return;
      }
      await api('conversas/' + item.id + '/acao', 'POST', {
        acao: 'finalizar'
      });
      await listar();
      if (atual === item.id) await abrir(item.id);
      aviso(finalizado ? 'Atendimento reativado.' : 'Atendimento finalizado.');
    })));
  }
  async function historico(id) {
    const itens = await api('conversas/' + id + '/historico');
    porId('chat-historico-conteudo').replaceChildren();
    for (const registroAtual of itens) {
      const botaoAtual = document.createElement('button');
      botaoAtual.type = 'button';
      botaoAtual.textContent = (registroAtual.AtendimentoProtocolo || registroAtual.id) + ' · ' + registroAtual.AtendimentoStatus;
      botaoAtual.onclick = () => {
        porId('chat-historico-dialog').close();
        aberta = true;
        abrir(registroAtual.id).catch(evento => aviso(evento.message, true));
      };
      porId('chat-historico-conteudo').append(botaoAtual);
    }
    porId('chat-historico-dialog').showModal();
  }
  async function listar() {
    if (!contexto || !window.FirmaAcesso) {
      porId('chat-aviso').textContent = 'Conecte o token no rodapé para carregar as conversas.';
      return;
    }
    const requisicao = ++versaoLista,
      busca = integral ? porId('chat-busca').value : '';
    try {
      const resultado = [];
      let pagina = 1,
        total;
      do {
        const dadosAtuais = await api('registros/atendimentos?' + new URLSearchParams({
          tamanho: 100,
          pagina,
          busca
        }));
        if (requisicao !== versaoLista) return;
        total = dadosAtuais.total;
        resultado.push(...dadosAtuais.itens);
        pagina++;
        if (!dadosAtuais.itens.length) break;
      } while (resultado.length < total);
      todos = resultado;
      await desenhar();
      porId('chat-aviso').textContent = '';
    } catch (evento) {
      if (requisicao === versaoLista) porId('chat-aviso').textContent = evento.message;
    }
  }
  function avatar(contato) {
    const trecho = document.createElement('span');
    trecho.className = 'firma-chat-contato-avatar';
    trecho.textContent = nome(contato).trim().split(/\s+/).slice(0, 2).map(paragrafo => paragrafo[0]).join('').toLocaleUpperCase('pt-BR');
    if (contato.ContatoAvatar) {
      try {
        const endereco = new URL(contato.ContatoAvatar, location.href);
        if (['https:', 'http:'].includes(endereco.protocol) || /^data:image\/(png|jpeg|webp|gif);base64,/i.test(contato.ContatoAvatar)) {
          const imagem = document.createElement('img');
          imagem.alt = 'Foto de ' + nome(contato);
          imagem.loading = 'lazy';
          imagem.referrerPolicy = 'no-referrer';
          imagem.src = endereco.href;
          imagem.onerror = () => imagem.remove();
          trecho.append(imagem);
        }
      } catch {}
    }
    return trecho;
  }
  function contatoCelula(registroAtual, celula) {
    celula.dataset.conversa = registroAtual.id;
    const pessoa = document.createElement('div'),
      dados = document.createElement('span'),
      titulo = document.createElement('strong'),
      telefone = document.createElement('small');
    pessoa.className = 'firma-chat-contato';
    dados.className = 'firma-chat-contato-identidade';
    titulo.textContent = registroAtual.contato;
    telefone.textContent = registroAtual.ContatoNumero || 'Telefone não informado';
    dados.append(titulo, telefone);
    pessoa.append(avatar(registroAtual), dados);
    celula.append(pessoa);
  }
  let tabela,
    integral = false,
    aberta = false,
    larguraConfigurada = false,
    observadorChat;
  const conteudoPrincipal = document.querySelector('main.trator-relacionamento'),
    estacionamento = porId('chat-estacionamento'),
    buscaConversas = porId('chat-busca').closest('label'),
    painel = document.createElement('div');
  painel.id = 'chat-painel';
  painel.className = 'relacionamento-chat';
  painel.append(document.querySelector('.relacionamento-chat-body'), porId('chat-detalhes'));
  estacionamento.append(painel);
  function estacionar() {
    estacionamento.append(painel);
  }
  function ajustarLarguraChat() {
    const areaRolavel = porId('chat-tabela').querySelector('.tabela-scroll');
    if (areaRolavel && !integral) painel.style.setProperty('--firma-chat-chat-largura', areaRolavel.clientWidth + 'px');
  }
  function encaixar() {
    // A busca pertence somente à lista lateral do chat expandido.
    const destinoBusca = integral ? document.querySelector('.relacionamento-inbox') : porId('chat-ferramentas');
    if (buscaConversas.parentElement !== destinoBusca) destinoBusca.prepend(buscaConversas);
    if (!integral && porId('chat-busca').value) {
      porId('chat-busca').value = '';
      clearTimeout(espera);
      if (contexto) listar();
    }
    estacionar();
    document.querySelectorAll('.firma-chat-linha-chat').forEach(linhaAtual => linhaAtual.remove());
    if (integral) {
      conteudoPrincipal.append(painel);
      return;
    }
    if (!aberta || !atual) {
      estacionar();
      return;
    }
    const linha = document.querySelector('[data-conversa-id="' + atual + '"]');
    if (!linha) {
      estacionar();
      return;
    }
    const linhaAtual = document.createElement('tr'),
      celula = document.createElement('td');
    linhaAtual.className = 'firma-chat-linha-chat';
    celula.colSpan = linha.children.length;
    celula.append(painel);
    linhaAtual.append(celula);
    linha.after(linhaAtual);
    ajustarLarguraChat();
  }
  porId('chat-recolher').onclick = () => {
    const id = atual;
    versao++;
    atual = null;
    conversaAtual = null;
    aberta = false;
    integral = false;
    document.body.classList.remove('firma-chat-integral');
    porId('chat-expandir').textContent = 'EXPANDIR CHAT ↗';
    porId('chat-conversa').hidden = true;
    porId('chat-vazio').hidden = false;
    encaixar();
    document.querySelectorAll('.relacionamento-chat-item.ativo').forEach(botaoAtual => botaoAtual.classList.remove('ativo'));
    document.querySelector('[data-conversa-id="' + id + '"] .btn-ver-linha')?.focus({
      preventScroll: true
    });
  };
  porId('chat-expandir').onclick = () => {
    integral = !integral;
    document.body.classList.toggle('firma-chat-integral', integral);
    porId('chat-expandir').textContent = integral ? 'VOLTAR AO TRATOR ↙' : 'EXPANDIR CHAT ↗';
    encaixar();
  };
  async function desenhar() {
    const itens = todos.map(registroAtual => ({
      ...registroAtual,
      contato: nome(registroAtual),
      ultima: registroAtual.UltimaMensagem || 'Sem mensagem',
      telefone: registroAtual.ContatoNumero || '',
      email: registroAtual.ContatoEmail || '',
      status: registroAtual.AtendimentoStatus === 'finalizado' ? 'Finalizados' : registroAtual.AtendimentoEmBot || registroAtual.AtendimentoBOTExterno ? 'Bots' : registroAtual.AtendimentoStatus === 'emandamento' ? 'Em andamento' : 'Pendente',
      canal: registroAtual.CanalNome || 'Sem canal',
      naoLidas: registroAtual.AtendimentoQtdMensagensNaoLidas || 0
    }));
    if (!tabela) tabela = FirmaTrator.montar({
      elemento: '#chat-tabela',
      rotulo: 'Zapiou',
      dados: [],
      slots: {
        abasFinais: destino => destino.append(porId('firma-chat-grupos')),
        comandos: destino => {
          destino.append(document.querySelector('.firma-chat-navegacao'), porId('chat-nova'), porId('exportar-dados'), porId('importar-dados'));
          // As abas guardam navegação e comandos; a busca fica no cabeçalho da lista.
        },
        rodape: destino => destino.append(porId('chat-rodape').content.cloneNode(true), porId('chat-expandir'), porId('chat-atualizar'))
      },
      edicaoInline: false,
      containerId: 'firma-chat',
      storageId: 'firma-chat-desconectado',
      toast: aviso,
      interpretarBusca: consulta => api('plano-busca', 'POST', {
        consulta
      }),
      salvar: salvarContato,
      renderAcoes: desenharAcoes,
      colunas: [{
        id: 'contato',
        nome: 'CONTATO',
        largura: 300,
        larguraMinima: 220,
        ocultavel: false,
        render: contatoCelula,
        editavel: false
      }, {
        id: 'ultima',
        nome: 'ÚLTIMA MSG',
        largura: 360
      }, {
        id: 'status',
        nome: 'ATENDIMENTOS',
        tipo: 'enum',
        largura: 180
      }, {
        id: 'canal',
        nome: 'CANAL',
        largura: 190
      }, {
        id: 'naoLidas',
        nome: 'NÃO LIDAS',
        tipo: 'numero',
        largura: 130
      }, {
        id: 'updatedAt',
        nome: 'ATUALIZAÇÃO',
        tipo: 'data',
        largura: 190
      }, {
        id: 'telefone',
        nome: 'TELEFONE',
        visivel: false
      }, {
        id: 'email',
        nome: 'E-MAIL',
        visivel: false
      }].map(itemDoRegistro => ({
        ...itemDoRegistro,
        editavel: false
      })),
      onRender: instancia => {
        desenharLista(instancia.dados());
        document.querySelectorAll('[data-conversa]').forEach(celula => {
          const linhaAtual = celula.closest('tr');
          linhaAtual.dataset.conversaId = celula.dataset.conversa;
          linhaAtual.ondblclick = evento => {
            if (evento.target.closest('button,a,input,select')) return;
            aberta = true;
            abrir(Number(celula.dataset.conversa)).catch(evento => porId('chat-aviso').textContent = evento.message);
          };
        });
        encaixar();
      },
      textos: {
        busca: 'FALAR COM O BURRO DE CARGA...',
        vazio: 'Nenhuma conversa nesta fila. Dê dois cliques em um registro para abrir o chat.'
      }
    });
    if (!observadorChat && window.ResizeObserver) {
      observadorChat = new ResizeObserver(ajustarLarguraChat);
      observadorChat.observe(porId('chat-tabela').querySelector('.tabela-scroll'));
    }
    if (!larguraConfigurada) {
      FirmaTrator.configurarLargura(conteudoPrincipal, porId('alternar-largura'), 'firma-chat-largura-v1');
      larguraConfigurada = true;
    }
    for (const itemDoRegistro of tabela.colunas) itemDoRegistro.editavel = ['contato', 'telefone', 'email'].includes(itemDoRegistro.id) && podeEditarContato();
    estacionar();
    tabela.definirDados(itens);
    encaixar();
  }
  function desenharLista(itens) {
    // A coluna lateral usa o mesmo resultado filtrado que a tabela.
    porId('chat-lista').replaceChildren();
    for (const registroAtual of itens) {
      const botaoAtual = document.createElement('button');
      botaoAtual.type = 'button';
      botaoAtual.className = 'relacionamento-chat-item' + (atual === registroAtual.id ? ' ativo' : '');
      const tituloEmDestaque = document.createElement('strong'),
        small = document.createElement('small');
      tituloEmDestaque.textContent = registroAtual.contato;
      small.textContent = registroAtual.ultima;
      const info = document.createElement('span');
      info.append(tituloEmDestaque, small);
      botaoAtual.append(avatar(registroAtual), info);
      botaoAtual.onclick = () => {
        aberta = true;
        abrir(registroAtual.id).catch(evento => porId('chat-aviso').textContent = evento.message);
      };
      porId('chat-lista').append(botaoAtual);
    }
  }
  async function abrir(id, anteriores = null) {
    const valorAtual = ++versao;
    const registroAtual = await api('conversas/' + id);
    if (valorAtual !== versao) return;
    const painel = porId('chat-mensagens'),
      posicao = painel.scrollTop,
      altura = painel.scrollHeight,
      noFim = painel.scrollHeight - painel.scrollTop - painel.clientHeight < 60;
    if (atual === id && conversaAtual) {
      const mapa = new Map(conversaAtual.mensagens.map(itemAtual => [itemAtual.id, itemAtual]));
      for (const mensagemAtual of [...(anteriores || []), ...registroAtual.mensagens]) mapa.set(mensagemAtual.id, mensagemAtual);
      registroAtual.mensagens = [...mapa.values()].sort((x, y) => new Date(y.MensagemDataHoraWhatsApp || y.createdAt) - new Date(x.MensagemDataHoraWhatsApp || x.createdAt) || y.id - x.id);
    } else temMaisMensagens = registroAtual.mensagens.length === 100;
    if (anteriores) temMaisMensagens = anteriores.length === 100;
    if (atual !== id) {
      resposta = null;
      porId('chat-resposta').hidden = true;
    }
    atual = id;
    aberta = true;
    conversaAtual = registroAtual;
    porId('chat-vazio').hidden = true;
    porId('chat-conversa').hidden = false;
    porId('chat-nome').textContent = nome(registroAtual.contato);
    porId('chat-avatar').replaceChildren(avatar(registroAtual.contato));
    porId('chat-protocolo').textContent = `${registroAtual.AtendimentoProtocolo || ''} · ${registroAtual.canal.CanalNome || ''}`;
    porId('chat-mensagens').replaceChildren();
    if (temMaisMensagens) {
      const mais = document.createElement('button');
      mais.id = 'chat-mensagens-anteriores';
      mais.type = 'button';
      mais.textContent = 'Carregar mensagens anteriores';
      mais.onclick = async () => {
        mais.disabled = true;
        try {
          const dadosAtuais = await api('conversas/' + id + '/mensagens?antes=' + registroAtual.mensagens.at(-1).id);
          if (atual === id) await abrir(id, dadosAtuais);
        } catch (evento) {
          porId('chat-aviso').textContent = evento.message;
        } finally {
          mais.disabled = false;
        }
      };
      porId('chat-mensagens').append(mais);
    }
    for (const mensagemAtual of [...registroAtual.mensagens].reverse()) {
      const conteiner = document.createElement('div');
      conteiner.className = 'relacionamento-bolha' + (mensagemAtual.MensagemFromMe ? ' saida' : '');
      conteiner.textContent = mensagemAtual.MensagemDeletada ? 'Mensagem excluída' : mensagemAtual.MensagemTexto || (mensagemAtual.ArquivoId ? 'Arquivo anexado' : 'Mensagem sem texto');
      const detalhe = document.createElement('small');
      detalhe.textContent = new Date(mensagemAtual.MensagemDataHoraWhatsApp || mensagemAtual.createdAt).toLocaleString('pt-BR') + (mensagemAtual.MensagemEditada ? ' · editada' : '') + (mensagemAtual.MensagemFromMe ? ' · ' + ({
        0: 'Pendente',
        1: 'Enviada',
        2: 'Entregue',
        3: 'Lida',
        4: 'Reproduzida',
        '-1': 'Falha'
      }[mensagemAtual.MensagemACK] || 'Enviada') : '');
      if (mensagemAtual.MensagemReacao?.length) {
        const reacoes = document.createElement('small');
        reacoes.textContent = mensagemAtual.MensagemReacao.map(registro => registro.Reacao).filter(Boolean).join(' ');
        conteiner.append(reacoes);
      }
      conteiner.append(detalhe);
      if (mensagemAtual.MensagemRespostaId) {
        const detalheAtual = document.createElement('small');
        detalheAtual.textContent = 'Em resposta à mensagem #' + mensagemAtual.MensagemRespostaId;
        conteiner.prepend(detalheAtual);
      }
      if (mensagemAtual.ArquivoId) {
        const botaoDoRegistro = document.createElement('button');
        botaoDoRegistro.type = 'button';
        botaoDoRegistro.textContent = 'Baixar anexo ↧';
        botaoDoRegistro.onclick = () => baixar(mensagemAtual.ArquivoId).catch(evento => porId('chat-aviso').textContent = evento.message);
        conteiner.append(botaoDoRegistro);
      }
      if (!mensagemAtual.MensagemDeletada) {
        const responder = document.createElement('button');
        responder.type = 'button';
        responder.textContent = 'Responder';
        responder.onclick = () => {
          resposta = mensagemAtual.id;
          porId('chat-resposta').hidden = false;
          porId('chat-resposta').querySelector('span').textContent = (mensagemAtual.MensagemTexto || 'Arquivo').slice(0, 100);
          porId('chat-texto').focus();
        };
        conteiner.append(responder);
        if (mensagemAtual.MensagemFromMe) {
          const remover = document.createElement('button');
          remover.type = 'button';
          remover.textContent = 'Apagar';
          remover.onclick = async () => {
            try {
              await api('conversas/' + id + '/mensagens/' + mensagemAtual.id + '/remover', 'POST');
              await abrir(id);
            } catch (evento) {
              porId('chat-aviso').textContent = evento.message;
            }
          };
          conteiner.append(remover);
        }
      }
      porId('chat-mensagens').append(conteiner);
    }
    if (!registroAtual.mensagens.length) {
      const paragrafo = document.createElement('p');
      paragrafo.textContent = 'O histórico deste atendimento começa aqui.';
      paragrafo.style.cssText = 'text-align:center;color:#a0aec0;font-size:11px';
      porId('chat-mensagens').append(paragrafo);
    }
    porId('chat-mensagens').scrollTop = anteriores ? posicao + painel.scrollHeight - altura : noFim ? painel.scrollHeight : posicao;
    porId('chat-contato-dados').replaceChildren();
    for (const [rotulo, valor] of [['Nome', registroAtual.contato.ContatoNome], ['Telefone', registroAtual.contato.ContatoNumero], ['E-mail', registroAtual.contato.ContatoEmail], ['Canal', registroAtual.canal.CanalNome], ['Situação', registroAtual.AtendimentoStatus]]) {
      const tituloDado = document.createElement('dt'),
        descricaoDado = document.createElement('dd');
      tituloDado.textContent = rotulo;
      descricaoDado.textContent = {
        pendente: 'Pendente',
        emandamento: 'Em atendimento',
        finalizado: 'Finalizado'
      }[valor] || valor || 'Não informado';
      porId('chat-contato-dados').append(tituloDado, descricaoDado);
    }
    opcoes(porId('chat-atendente'), await registros('atendentes'), registroAtual.AtendenteId);
    opcoes(porId('chat-setor'), (await registros('setores')).filter(itemDaTela => !itemDaTela.CanalId || itemDaTela.CanalId === registroAtual.CanalId), registroAtual.SetorId);
    porId('chat-atividades').replaceChildren();
    for (const acao of registroAtual.acoes) {
      const paragrafo = document.createElement('p');
      paragrafo.textContent = acao.AtendimentoAcaoTipo + ' · ' + new Date(acao.createdAt).toLocaleString('pt-BR');
      porId('chat-atividades').append(paragrafo);
    }
    porId('chat-reativar').hidden = registroAtual.AtendimentoStatus !== 'finalizado';
    porId('chat-assumir').hidden = registroAtual.AtendimentoStatus === 'finalizado';
    try {
      const tags = (await registros('tags')).filter(itemLocal => itemLocal.TagStatus !== false && (!itemLocal.SetorId || itemLocal.SetorId === registroAtual.SetorId));
      opcoes(porId('chat-tags'), tags.map(itemLocal => ({
        ...itemLocal,
        ContatoNome: itemLocal.TagNome
      })));
      for (const opcao of porId('chat-tags').options) opcao.selected = (registroAtual.tags || []).some(itemLocal => String(itemLocal.id) === opcao.value);
    } catch {}
    try {
      opcoes(porId('chat-resposta-rapida'), (await registros('respostas')).filter(itemAtual => !itemAtual.SetorId || itemAtual.SetorId === registroAtual.SetorId).map(itemAtual => ({
        ...itemAtual,
        ContatoNome: itemAtual.MensagemRapidaTexto.slice(0, 60)
      })));
    } catch {}
    try {
      opcoes(porId('chat-ia'), (await registros('ia')).filter(ia => ia.InteligenciaArtificialStatus !== false && (!ia.CanalId || ia.CanalId === registroAtual.CanalId) && (!ia.SetorId || ia.SetorId === registroAtual.SetorId)).map(ia => ({
        ...ia,
        ContatoNome: ia.InteligenciaArtificialNome
      })));
    } catch {}
    if (registroAtual.AtendimentoQtdMensagensNaoLidas) await api('conversas/' + id + '/acao', 'POST', {
      acao: 'lidas'
    });
    porId('chat-envio').hidden = registroAtual.AtendimentoStatus === 'finalizado';
    porId('chat-finalizar').disabled = registroAtual.AtendimentoStatus === 'finalizado';
    await desenhar();
  }
  async function acao(acao, extra = {}) {
    if (!atual) return;
    try {
      await api(`conversas/${atual}/acao`, 'POST', {
        acao,
        ...extra
      });
      cadastrosGuardados = {};
      await listar();
      await abrir(atual);
    } catch (evento) {
      porId('chat-aviso').textContent = evento.message;
    }
  }
  porId('chat-assumir').onclick = () => acao('assumir', {
    AtendenteId: Number(porId('chat-atendente').value)
  });
  porId('chat-transferir').onclick = () => acao('transferir', {
    SetorId: Number(porId('chat-setor').value)
  });
  porId('chat-finalizar').onclick = () => acao('finalizar');
  porId('chat-envio').onsubmit = async evento => {
    evento.preventDefault();
    if (!atual) return;
    const botaoAtual = evento.target.querySelector('[type=submit]');
    botaoAtual.disabled = true;
    porId('chat-estado-envio').textContent = 'Enviando…';
    try {
      await api(`conversas/${atual}/mensagens`, 'POST', {
        texto: porId('chat-texto').value,
        ...(resposta ? {
          MensagemRespostaId: resposta
        } : {})
      });
      porId('chat-texto').value = '';
      resposta = null;
      porId('chat-resposta').hidden = true;
      porId('chat-estado-envio').textContent = 'Mensagem enviada';
      await abrir(atual);
    } catch (erro) {
      porId('chat-estado-envio').textContent = erro.message;
    } finally {
      botaoAtual.disabled = false;
    }
  };
  porId('chat-resposta-cancelar').onclick = () => {
    resposta = null;
    porId('chat-resposta').hidden = true;
  };
  porId('chat-reativar').onclick = () => prepararInicio(atual).catch(evento => aviso(evento.message, true));
  porId('chat-avaliar').onclick = () => acao('avaliar', {
    nota: Number(porId('chat-nota').value)
  });
  porId('chat-tags-salvar').onclick = async () => {
    try {
      await api('conversas/' + atual + '/tags', 'PUT', [...porId('chat-tags').selectedOptions].map(opcao => Number(opcao.value)).filter(Boolean));
      await abrir(atual);
    } catch (evento) {
      porId('chat-aviso').textContent = evento.message;
    }
  };
  porId('chat-resposta-rapida').onchange = async () => {
    const elementoDaTela = (await registros('respostas')).find(elementoDoRegistro => elementoDoRegistro.id === Number(porId('chat-resposta-rapida').value));
    if (elementoDaTela) porId('chat-texto').value = elementoDaTela.MensagemRapidaTexto;
  };
  porId('chat-sugerir').onclick = async () => {
    if (!atual || !porId('chat-ia').value) return;
    const elementoLocal = porId('chat-sugerir');
    elementoLocal.disabled = true;
    try {
      const elementoDaTela = await api('conversas/' + atual + '/ia/' + porId('chat-ia').value, 'POST');
      porId('chat-texto').value = elementoDaTela.texto;
    } catch (evento) {
      porId('chat-estado-envio').textContent = evento.message;
    } finally {
      elementoLocal.disabled = false;
    }
  };
  porId('chat-anexo').onchange = async () => {
    const arquivo = porId('chat-anexo').files[0];
    if (!arquivo || !atual) return;
    const formulario = new FormData();
    formulario.append('arquivo', arquivo);
    formulario.append('legenda', porId('chat-texto').value);
    try {
      const respostaAtual = await fetch('/api/firma/conversas/' + atual + '/arquivos', {
        method: 'POST',
        headers: {
          Authorization: 'Bearer ' + window.FirmaAcesso.token
        },
        body: formulario
      });
      if (!respostaAtual.ok) {
        const evento = await respostaAtual.json();
        throw Error(evento.mensagem || 'Falha no envio');
      }
      await abrir(atual);
    } catch (evento) {
      porId('chat-estado-envio').textContent = evento.message;
    } finally {
      porId('chat-anexo').value = '';
    }
  };
  async function baixar(id) {
    const respostaAtual = await fetch('/api/firma/arquivos/' + id, {
      headers: {
        Authorization: 'Bearer ' + window.FirmaAcesso.token
      }
    });
    if (!respostaAtual.ok) throw Error('Não foi possível baixar o arquivo');
    const endereco = URL.createObjectURL(await respostaAtual.blob()),
      linkDownload = document.createElement('a');
    linkDownload.href = endereco;
    const cabecalhoArquivo = respostaAtual.headers.get('Content-Disposition') || '';
    linkDownload.download = decodeURIComponent(cabecalhoArquivo.match(/filename\*=UTF-8''([^;]+)/i)?.[1] || cabecalhoArquivo.match(/filename="([^"]+)"/)?.[1] || 'anexo');
    linkDownload.click();
    setTimeout(() => URL.revokeObjectURL(endereco), 10000);
  }
  porId('chat-historico-fechar').onclick = () => porId('chat-historico-dialog').close();
  porId('chat-historico').onclick = () => historico(atual).catch(evento => aviso(evento.message, true));
  porId('chat-busca').oninput = () => {
    clearTimeout(espera);
    espera = setTimeout(() => listar(), 300);
  };
  porId('chat-atualizar').onclick = () => listar();
  async function atualizarInicio() {
    const canal = Number(porId('chat-novo-canal').value),
      setor = Number(porId('chat-novo-setor').value);
    opcoes(porId('chat-novo-template'), (await registros('templates')).filter(itemAtual => itemAtual.MensagemTemplateStatusUso !== false && itemAtual.MensagemTemplateStatusMeta === 'APPROVED' && (!itemAtual.CanalId || itemAtual.CanalId === canal) && (!itemAtual.SetorId || itemAtual.SetorId === setor)).map(itemAtual => ({
      ...itemAtual,
      ContatoNome: itemAtual.MensagemTemplateNome
    })));
    porId('chat-novo-template').options[0].textContent = 'Mensagem livre';
    porId('chat-novo-variaveis').replaceChildren();
  }
  async function prepararInicio(id = null) {
    reabertura = id;
    const registroAtual = id ? await api('conversas/' + id) : null;
    porId('chat-nova-form').reset();
    porId('chat-nova-erro').textContent = '';
    porId('chat-nova-dialog').querySelector('h2').textContent = id ? 'Reabrir conversa' : 'Nova conversa';
    opcoes(porId('chat-novo-canal'), await registros('canais'), registroAtual?.CanalId);
    opcoes(porId('chat-novo-contato'), await registros('contatos'), registroAtual?.ContatoId);
    opcoes(porId('chat-novo-setor'), (await registros('setores')).filter(itemDaTela => !itemDaTela.CanalId || itemDaTela.CanalId === Number(porId('chat-novo-canal').value)), registroAtual?.SetorId);
    opcoes(porId('chat-novo-atendente'), (await registros('atendentes')).filter(atendente => atendente.AtendenteStatus !== false), registroAtual?.AtendenteId);
    for (const campo of ['canal', 'contato', 'setor']) porId('chat-novo-' + campo).disabled = Boolean(id);
    await atualizarInicio();
    porId('chat-nova-dialog').showModal();
  }
  porId('chat-nova').onclick = () => prepararInicio().catch(evento => aviso(evento.message, true));
  porId('chat-nova-fechar').onclick = () => porId('chat-nova-dialog').close();
  porId('chat-novo-canal').onchange = async () => {
    try {
      opcoes(porId('chat-novo-setor'), (await registros('setores')).filter(itemDaTela => !itemDaTela.CanalId || itemDaTela.CanalId === Number(porId('chat-novo-canal').value)));
      await atualizarInicio();
    } catch (evento) {
      porId('chat-nova-erro').textContent = evento.message;
    }
  };
  porId('chat-novo-setor').onchange = () => atualizarInicio().catch(evento => porId('chat-nova-erro').textContent = evento.message);
  porId('chat-novo-template').onchange = async () => {
    const modelo = (await registros('templates')).find(elementoDoRegistro => elementoDoRegistro.id === Number(porId('chat-novo-template').value));
    porId('chat-novo-variaveis').replaceChildren();
    const indices = [...new Set([...(modelo?.MensagemTemplateCorpo || '').matchAll(/\{\{([0-9]+)}}/g)].map(itemAtual => Number(itemAtual[1])))].sort((primeiro, segundo) => primeiro - segundo);
    for (const indice of indices) {
      const rotuloAtual = document.createElement('label'),
        entrada = document.createElement('input');
      rotuloAtual.textContent = 'Variável ' + indice;
      entrada.required = true;
      entrada.maxLength = 4096;
      entrada.dataset.variavel = indice;
      rotuloAtual.append(entrada);
      porId('chat-novo-variaveis').append(rotuloAtual);
    }
  };
  porId('chat-nova-form').onsubmit = async evento => {
    evento.preventDefault();
    const botao = evento.target.querySelector('[type=submit]');
    botao.disabled = true;
    try {
      const texto = porId('chat-novo-texto').value,
        modelo = Number(porId('chat-novo-template').value),
        arquivo = porId('chat-novo-anexo').files[0];
      if (!texto.trim() && !modelo && !arquivo) throw Error('Informe uma mensagem inicial, template ou anexo.');
      const pedido = {
        AtendenteId: Number(porId('chat-novo-atendente').value),
        texto
      };
      if (modelo) {
        pedido.MensagemTemplateId = modelo;
        pedido.variaveis = [...porId('chat-novo-variaveis').querySelectorAll('input')].map(indiceAtual => indiceAtual.value);
      }
      if (arquivo) {
        const formulario = new FormData();
        formulario.append('arquivo', arquivo);
        const resposta = await fetch('/api/firma/arquivos', {
          method: 'POST',
          headers: {
            Authorization: 'Bearer ' + window.FirmaAcesso.token
          },
          body: formulario
        });
        const dados = await resposta.json();
        if (!resposta.ok) throw Error(dados.mensagem || dados.message || 'Não foi possível guardar o anexo.');
        pedido.ArquivoId = dados.id;
      }
      const elemento5 = reabertura ? await api('conversas/' + reabertura + '/acao', 'POST', {
        ...pedido,
        acao: 'reativar'
      }) : await api('conversas', 'POST', {
        ...pedido,
        CanalId: Number(porId('chat-novo-canal').value),
        ContatoId: Number(porId('chat-novo-contato').value),
        SetorId: porId('chat-novo-setor').value ? Number(porId('chat-novo-setor').value) : null
      });
      porId('chat-nova-dialog').close();
      await listar();
      await abrir(elemento5.id);
    } catch (erro) {
      porId('chat-nova-erro').textContent = erro.message;
    } finally {
      botao.disabled = false;
    }
  };
  desenhar();
  let exportacao = [];
  function linhasExportacao() {
    const selecionados = new Set(tabela.obterSelecionados());
    return selecionados.size ? tabela.getEstado().todos.filter(registroAtual => selecionados.has(registroAtual.id)) : tabela.dados();
  }
  porId('exportar-dados').onclick = () => {
    exportacao = linhasExportacao().map(registroAtual => ({
      ...registroAtual
    }));
    if (!exportacao.length) {
      aviso('Nenhuma conversa para exportar.');
      return;
    }
    const valorDoRegistro = tabela.obterSelecionados().length;
    porId('chat-exportar-resumo').textContent = exportacao.length + ' conversa(s) ' + (valorDoRegistro ? 'selecionada(s)' : 'no resultado dos filtros') + '.';
    porId('chat-exportar-concordo').checked = false;
    document.querySelectorAll('[data-chat-formato]').forEach(botaoAtual => botaoAtual.disabled = true);
    porId('chat-exportar-dialog').showModal();
  };
  porId('chat-exportar-concordo').onchange = evento => document.querySelectorAll('[data-chat-formato]').forEach(botaoAtual => botaoAtual.disabled = !evento.target.checked);
  document.querySelectorAll('[data-fechar-dialog]').forEach(botaoAtual => botaoAtual.onclick = () => botaoAtual.closest('dialog').close());
  document.querySelectorAll('[data-chat-formato]').forEach(botaoAtual => botaoAtual.onclick = () => {
    if (!window.FirmaAcesso || !porId('chat-exportar-concordo').checked) return;
    const campos = tabela.colunas.filter(itemDoRegistro => tabela.estado.visiveis.includes(itemDoRegistro.id)),
      linhas = exportacao.map(registroAtual => Object.fromEntries(campos.map(itemDoRegistro => [itemDoRegistro.nome, tabela.formatado(registroAtual, itemDoRegistro.id) ?? ''])));
    if (botaoAtual.dataset.chatFormato === 'pdf') {
      const secao = document.createElement('section');
      secao.className = 'firma-chat-impressao';
      const titulo = document.createElement('h1');
      titulo.textContent = 'Zapiou';
      const table = document.createElement('table'),
        cabecalhoTabela = document.createElement('thead'),
        linhaAtual = document.createElement('tr');
      for (const itemDoRegistro of campos) {
        const tituloColuna = document.createElement('th');
        tituloColuna.textContent = itemDoRegistro.nome;
        linhaAtual.append(tituloColuna);
      }
      cabecalhoTabela.append(linhaAtual);
      table.append(cabecalhoTabela);
      const corpoTabela = document.createElement('tbody');
      for (const registroAtual of linhas) {
        const linhaDoRegistro = document.createElement('tr');
        for (const itemDoRegistro of campos) {
          const celula = document.createElement('td');
          celula.textContent = registroAtual[itemDoRegistro.nome];
          linhaDoRegistro.append(celula);
        }
        corpoTabela.append(linhaDoRegistro);
      }
      table.append(corpoTabela);
      secao.append(titulo, table);
      document.body.append(secao);
      try {
        window.print();
      } finally {
        secao.remove();
      }
    } else {
      const valorCsv = valorAtual => '"' + String(valorAtual).replace(/^[=+@\-\t\r]/, "'$&").replaceAll('"', '""') + '"';
      const conteudo = botaoAtual.dataset.chatFormato === 'json' ? JSON.stringify(linhas, null, 2) : '\uFEFF' + [campos.map(itemDoRegistro => valorCsv(itemDoRegistro.nome)).join(';'), ...linhas.map(registroAtual => campos.map(itemDoRegistro => valorCsv(registroAtual[itemDoRegistro.nome])).join(';'))].join('\r\n');
      const endereco = URL.createObjectURL(new Blob([conteudo], {
          type: botaoAtual.dataset.chatFormato === 'json' ? 'application/json;charset=utf-8' : 'text/csv;charset=utf-8'
        })),
        link = document.createElement('a');
      link.href = endereco;
      link.download = 'firma-chat-conversas.' + botaoAtual.dataset.chatFormato;
      link.click();
      setTimeout(() => URL.revokeObjectURL(endereco), 10000);
    }
    porId('chat-exportar-dialog').close();
  });
  porId('importar-dados').onclick = async () => {
    try {
      const canais = (await registros('canais')).filter(itemDoRegistro => itemDoRegistro.CanalProvedorAPI === 'Baileys');
      if (!canais.length) {
        aviso('Cadastre e conecte um canal WhatsApp para importar conversas.');
        return;
      }
      opcoes(porId('chat-importar-canal'), canais);
      porId('chat-importar-dialog').showModal();
    } catch (evento) {
      aviso(evento.message, true);
    }
  };
  porId('chat-importar-form').onsubmit = async evento => {
    evento.preventDefault();
    const botaoAtual = evento.target.querySelector('[type=submit]');
    botaoAtual.disabled = true;
    try {
      await api('canais/' + porId('chat-importar-canal').value + '/importar', 'POST');
      porId('chat-importar-dialog').close();
      await listar();
      aviso('Importação solicitada. As conversas serão atualizadas durante a sincronização.');
    } catch (erro) {
      aviso(erro.message, true);
    } finally {
      botaoAtual.disabled = false;
    }
  };
  function acessoAtivo(ativo) {
    for (const id of ['chat-nova', 'chat-atualizar', 'chat-expandir', 'chat-busca', 'exportar-dados', 'importar-dados']) porId(id).disabled = !ativo;
    if (ativo) porId('importar-dados').disabled = !contexto?.modulos.find(modulo => modulo.slug === 'canais')?.acoes?.includes('importar');
  }
  acessoAtivo(false);
  async function iniciar(itemDoRegistro) {
    contexto = itemDoRegistro;
    cadastrosGuardados = {};
    tabela.storageId = 'firma-chat-' + itemDoRegistro.clienteId + '-' + itemDoRegistro.usuarioId;
    tabela.restaurar();
    porId('chat-aviso').textContent = '';
    acessoAtivo(true);
    await listar();
  }
  document.addEventListener('firma:contexto', evento => iniciar(evento.detail));
  document.addEventListener('firma:desconectado', () => {
    versaoLista++;
    versao++;
    contexto = null;
    exportacao = [];
    document.querySelectorAll('dialog[open]').forEach(dadosAtuais => dadosAtuais.close());
    atual = null;
    conversaAtual = null;
    resposta = null;
    todos = [];
    cadastrosGuardados = {};
    aberta = false;
    integral = false;
    document.body.classList.remove('firma-chat-integral');
    porId('chat-expandir').textContent = 'EXPANDIR CHAT ↗';
    porId('chat-texto').value = '';
    porId('chat-conversa').hidden = true;
    porId('chat-vazio').hidden = false;
    acessoAtivo(false);
    tabela?.limparDados();
    if (tabela) {
      tabela.estado.salvos = [];
      tabela.storageId = 'firma-chat-desconectado';
    }
    desenhar();
    porId('chat-aviso').textContent = 'Conecte o token no rodapé para carregar as conversas.';
  });
  // Atualização em segundo plano preserva rascunhos e somente consulta dados.
  const atualizacaoPeriodica = setInterval(() => {
    if (contexto && !document.hidden) {
      cadastrosGuardados = {};
      listar().then(() => atual && abrir(atual)).catch(() => {});
    }
  }, 15000);
  window.addEventListener('pagehide', () => {
    clearInterval(atualizacaoPeriodica);
    observadorChat?.disconnect();
  });
})();
