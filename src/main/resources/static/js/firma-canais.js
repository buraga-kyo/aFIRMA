(() => {
  const porId = id => document.getElementById(id);
  let modulo,
    canais = [],
    atual = null,
    versao = 0,
    versaoQR = 0,
    espera,
    tokenAtivo = '';
  const rotulos = {
    CanalNome: 'NOME DO CANAL',
    CanalNumeroCelular: 'NÚMERO DO WHATSAPP',
    CanalStatusUso: 'CANAL ATIVO',
    CanalProvedorAPI: 'CONEXÃO',
    CanalMetaNumeroIdentificador: 'IDENTIFICADOR DO NÚMERO NA META',
    CanalMetaWAID: 'CONTA WHATSAPP BUSINESS (WABA ID)',
    CanalMsgEncerramentoAtendimento: 'MENSAGEM AO FINALIZAR ATENDIMENTO',
    CanalVisualizaMenuBOTChat: 'MOSTRAR ATENDIMENTOS DE BOTS',
    CanalFinalizaAtendimentoPorTempoInatividade: 'FINALIZAR POR INATIVIDADE',
    CanalAtendimentoTempoInatividade: 'TEMPO SEM RESPOSTA (MINUTOS)',
    CanalMsgFinalizarTempoInatividade: 'MENSAGEM AO FINALIZAR POR INATIVIDADE'
  };
  const estados = {
    CONECTANDO: 'Conectando. Aguarde o QR.',
    QR_PENDENTE: 'Escaneie o QR no WhatsApp.',
    CONECTADO: 'WhatsApp conectado.',
    DESCONECTADO: 'WhatsApp desconectado.'
  };
  function aviso(texto, erro = false) {
    const no = porId('mensagem-empresa');
    no.dataset.firmaTipo = erro ? 'erro' : 'info';
    no.textContent = texto;
    no.hidden = !texto;
  }
  async function api(caminho, metodo = 'GET', dados) {
    const token = tokenAtivo;
    if (!token || token !== porId('empresa-token').value.trim()) throw Error('Carregue a FIRMA antes de configurar o canal.');
    const resposta = await fetch('/api/firma/' + caminho, {
      method: metodo,
      cache: 'no-store',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
      ...(dados ? {
        body: JSON.stringify(dados)
      } : {})
    });
    if (token !== tokenAtivo || token !== porId('empresa-token').value.trim()) throw Error('O acesso mudou. Carregue a FIRMA novamente.');
    const dadosAtuais = await resposta.json().catch(() => ({}));
    if (!resposta.ok) throw Error(dadosAtuais.mensagem || dadosAtuais.message || `Operação recusada (${resposta.status}).`);
    return dadosAtuais;
  }
  function pararQR() {
    versaoQR++;
    clearTimeout(espera);
    porId('firma-canal-painel').hidden = true;
    porId('firma-canal-qr').hidden = true;
    porId('firma-canal-qr').removeAttribute('src');
  }
  function permissoes() {
    porId('firma-canal-novo').hidden = !modulo?.criavel;
    porId('firma-canal-salvar').hidden = atual ? !modulo?.editavel : !modulo?.criavel;
  }
  function preencher(registro) {
    pararQR();
    atual = registro;
    permissoes();
    const campos = porId('firma-canal-campos');
    campos.replaceChildren();
    const pode = registro ? modulo.editavel : modulo.criavel;
    for (const item of modulo.campos) {
      if (['CanalStatus', 'CanalNomePastaWhatsApp'].includes(item.nome)) continue;
      const grupo = document.createElement('div');
      grupo.className = 'campo';
      grupo.dataset.campo = item.nome;
      const rotulo = document.createElement('label');
      rotulo.textContent = rotulos[item.nome] || item.nome.replace(/^Canal/, '').replace(/([a-z])([A-Z])/g, '$1 $2').toUpperCase();
      let elemento;
      if (item.nome === 'CanalProvedorAPI') {
        elemento = document.createElement('select');
        elemento.add(new Option('WhatsApp com QR (Baileys)', 'Baileys'));
        elemento.add(new Option('WhatsApp Business (Meta)', 'Meta'));
        if (registro?.[item.nome] && !['Baileys', 'Meta'].includes(registro[item.nome])) elemento.add(new Option(registro[item.nome], registro[item.nome]));
      } else if (['TEXT', 'JSON', 'JSONB'].includes(item.tipo)) {
        elemento = document.createElement('textarea');
        grupo.classList.add('campo-largo');
      } else {
        elemento = document.createElement('input');
        elemento.type = item.tipo === 'BOOLEAN' ? 'checkbox' : item.tipo === 'PASSWORD' ? 'password' : item.tipo === 'INTEGER' ? 'number' : 'text';
        if (item.tipo.startsWith('VARCHAR')) elemento.maxLength = Number(item.tipo.match(/\d+/)[0]);
        if (elemento.type === 'password') {
          elemento.autocomplete = 'new-password';
          elemento.placeholder = registro ? 'Deixe vazio para manter' : '';
        }
        if (elemento.type === 'number') {
          elemento.step = '1';
          elemento.min = '0';
        }
      }
      elemento.id = 'firma-' + item.nome;
      elemento.name = item.nome;
      elemento.dataset.tipo = item.tipo;
      elemento.disabled = !pode;
      rotulo.htmlFor = elemento.id;
      const valor = registro?.[item.nome] ?? {
        CanalProvedorAPI: 'Baileys',
        CanalStatusUso: true
      }[item.nome];
      if (elemento.type === 'checkbox') {
        elemento.checked = valor === true;
        grupo.classList.add('campo-checkbox');
      } else if (valor != null) elemento.value = ['JSON', 'JSONB'].includes(item.tipo) ? JSON.stringify(valor, null, 2) : String(valor);
      elemento.required = Boolean(item.obrigatorio) && elemento.type !== 'checkbox' && !(elemento.type === 'password' && registro);
      grupo.append(rotulo, elemento);
      campos.append(grupo);
    }
    const ajustarCampos = () => {
      const meta = porId('firma-CanalProvedorAPI')?.value === 'Meta';
      const inatividade = porId('firma-CanalFinalizaAtendimentoPorTempoInatividade')?.checked;
      for (const grupo of campos.children) {
        const nome = grupo.dataset.campo;
        grupo.hidden = nome.startsWith('CanalMeta') ? !meta : ['CanalAtendimentoTempoInatividade', 'CanalMsgFinalizarTempoInatividade'].includes(nome) ? !inatividade : false;
      }
    };
    campos.onchange = ajustarCampos;
    ajustarCampos();
    porId('firma-canal-form').hidden = false;
    porId('firma-canal-cancelar').hidden = !pode;
    porId('firma-canal-conexao').hidden = !registro;
    porId('firma-canal-estado').textContent = registro ? estados[registro.CanalStatus] || registro.CanalStatus || 'Consulte a conexão do canal.' : '';
    const acoes = porId('firma-canal-acoes');
    acoes.replaceChildren();
    if (registro) for (const [acao, titulo] of [['conectar', 'CONECTAR / QR'], ['estado', 'CONSULTAR CONEXÃO'], ['importar', 'IMPORTAR CONVERSAS'], ['desconectar', 'DESCONECTAR']]) {
      if (!modulo.acoes.includes(acao) || registro.CanalProvedorAPI === 'Meta' && acao !== 'estado') continue;
      const botao = document.createElement('button');
      botao.type = 'button';
      botao.className = 'botao-carteira';
      botao.textContent = titulo;
      botao.onclick = async () => {
        const id = registro.id;
        botao.disabled = true;
        try {
          if (acao === 'conectar') {
            await api(`canais/${id}/conectar`, 'POST');
            if (atual?.id === id) acompanhar(id);
          } else if (acao === 'importar') {
            await api(`canais/${id}/importar`, 'POST');
            aviso('Histórico solicitado. O WhatsApp define quais mensagens ficam disponíveis.');
          } else {
            const dadosAtuais = await api(`canais/${id}/${acao}`, 'POST');
            if (atual?.id === id) {
              if (acao === 'desconectar') pararQR();
              porId('firma-canal-estado').textContent = dadosAtuais.erro || estados[dadosAtuais.estado] || dadosAtuais.estado || 'Canal desconectado.';
            }
          }
        } catch (evento) {
          aviso(evento.message, true);
        } finally {
          botao.disabled = false;
        }
      };
      acoes.append(botao);
    }
  }
  async function acompanhar(id) {
    pararQR();
    const valorAtual = versaoQR;
    porId('firma-canal-painel').hidden = false;
    const inicio = Date.now();
    async function consultar() {
      try {
        const dadosAtuais = await api(`canais/${id}/conexao`, 'POST');
        if (valorAtual !== versaoQR || atual?.id !== id) return;
        porId('firma-canal-estado').textContent = dadosAtuais.erro || estados[dadosAtuais.estado] || dadosAtuais.estado;
        porId('firma-canal-qr').hidden = !dadosAtuais.qr;
        if (dadosAtuais.qr) porId('firma-canal-qr').src = dadosAtuais.qr;
        if (dadosAtuais.estado === 'CONECTADO') {
          porId('firma-canal-painel').hidden = true;
          const cabecalho = await api(`canais/${id}/historico-status`, 'POST');
          if (valorAtual === versaoQR) aviso(`${cabecalho.conversas || 0} conversas e ${cabecalho.mensagens || 0} mensagens no histórico.`);
          return;
        }
        if (dadosAtuais.estado === 'DESCONECTADO' || Date.now() - inicio > 120000) return;
        espera = setTimeout(consultar, 2000);
      } catch (evento) {
        if (valorAtual === versaoQR) aviso(evento.message, true);
      }
    }
    consultar();
  }
  async function listar(selecionado) {
    const valorAtual = ++versao;
    pararQR();
    const lista = [];
    let pagina = 1,
      total = 0;
    do {
      const dadosAtuais = await api(`registros/canais?tamanho=100&pagina=${pagina++}`);
      if (valorAtual !== versao) return;
      lista.push(...dadosAtuais.itens);
      total = dadosAtuais.total;
      if (!dadosAtuais.itens.length) break;
    } while (lista.length < total);
    canais = lista;
    const seletor = porId('firma-canal');
    seletor.replaceChildren(new Option(canais.length ? 'Selecione o canal' : 'Nenhum canal cadastrado', ''));
    canais.forEach(registroAtual => seletor.add(new Option(registroAtual.CanalNome || registroAtual.CanalNumeroCelular || `Canal ${registroAtual.id}`, registroAtual.id)));
    const registro = canais.find(registroAtual => registroAtual.id === selecionado) || canais[0];
    if (registro) {
      seletor.value = registro.id;
      preencher(registro);
    } else if (modulo.criavel) preencher(null);else {
      atual = null;
      porId('firma-canal-form').hidden = true;
      porId('firma-canal-conexao').hidden = true;
    }
  }
  async function carregar() {
    versao++;
    pararQR();
    atual = null;
    modulo = null;
    porId('firma-canais').hidden = true;
    tokenAtivo = porId('empresa-token').value.trim();
    const token = tokenAtivo;
    try {
      const item = await api('contexto');
      if (token !== tokenAtivo) return;
      modulo = item.modulos.find(moduloAtual => moduloAtual.slug === 'canais');
      if (!modulo) return;
      porId('firma-canais').hidden = false;
      permissoes();
      await listar();
    } catch (evento) {
      aviso(evento.message, true);
    }
  }
  porId('firma-canal').onchange = () => {
    const registro = canais.find(elementoAtual => String(elementoAtual.id) === porId('firma-canal').value);
    if (registro) preencher(registro);else {
      pararQR();
      atual = null;
      porId('firma-canal-form').hidden = true;
      porId('firma-canal-conexao').hidden = true;
    }
  };
  porId('firma-canal-novo').onclick = () => {
    porId('firma-canal').value = '';
    preencher(null);
    porId('firma-CanalNome')?.focus();
  };
  porId('firma-canal-atualizar').onclick = () => listar(atual?.id).catch(evento => aviso(evento.message, true));
  porId('firma-canal-cancelar').onclick = () => {
    const registro = canais.find(elementoAtual => String(elementoAtual.id) === porId('firma-canal').value);
    if (registro) preencher(registro);else if (canais.length) {
      porId('firma-canal').value = canais[0].id;
      preencher(canais[0]);
    } else {
      pararQR();
      porId('firma-canal-form').hidden = true;
    }
  };
  porId('firma-canal-fechar').onclick = pararQR;
  porId('firma-canal-form').onsubmit = async evento => {
    evento.preventDefault();
    const elementoDoRegistro = porId('firma-canal-salvar'),
      valorAtual = versao,
      id = atual?.id;
    elementoDoRegistro.disabled = true;
    try {
      const dados = {};
      for (const elemento of evento.target.querySelectorAll('[name]')) {
        if (elemento.disabled || elemento.type === 'password' && !elemento.value) continue;
        dados[elemento.name] = elemento.type === 'checkbox' ? elemento.checked : elemento.value === '' ? null : elemento.type === 'number' ? Number(elemento.value) : ['JSON', 'JSONB'].includes(elemento.dataset.tipo) ? JSON.parse(elemento.value) : elemento.value;
      }
      const salvo = await api('registros/canais' + (id ? '/' + id : ''), id ? 'PUT' : 'POST', dados);
      if (valorAtual !== versao) return;
      await listar(salvo.id);
      aviso('Canal salvo.');
    } catch (evento) {
      if (valorAtual === versao) aviso(evento.message, true);
    } finally {
      elementoDoRegistro.disabled = false;
    }
  };
  porId('empresa-token').addEventListener('input', () => {
    tokenAtivo = '';
    versao++;
    pararQR();
    porId('firma-canais').hidden = true;
  });
  window.addEventListener('pagehide', pararQR);
  window.FirmaCanais = Object.freeze({
    carregar
  });
})();
