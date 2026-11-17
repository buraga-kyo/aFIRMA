(() => {
  const porId = id => document.getElementById(id);
  const PROTOCOLO = 'afirma.nostr.aceite.v1';
  const TEXTO_ACEITE = 'Li este PDF e concordo com o seu conteúdo. Autorizo registrar meu aceite com esta chave Nostr e com o papel informado.';
  let chave, versao = 0, versaoVerificacao = 0, enderecos = [];
  function mensagem(id, texto, erro = false) {
    const no = porId(id);
    no.textContent = texto;
    no.classList.toggle('inventor-erro', erro);
  }
  for (const botao of document.querySelectorAll('[data-inventor]')) botao.addEventListener('click', () => {
    const ethereum = botao.dataset.inventor === 'ethereum';
    porId('area-nostr').hidden = ethereum;
    porId('inventor-ethereum').hidden = !ethereum;
    for (const aba of document.querySelectorAll('[data-inventor]')) aba.setAttribute('aria-pressed', String(aba === botao));
  });
  let publicacaoAutomatica = false, provaParaPublicar;
  const basePublicacao = '/api/assineai/nostr';
  async function apiPublicacao(caminho, dados, metodo = 'GET', binario = false) {
    const token = localStorage.getItem('assineai-token') || '';
    const r = await fetch(basePublicacao + caminho, { method: metodo, headers: { Authorization: `Bearer ${token}`, ...(dados ? { 'Content-Type': 'application/json' } : {}) }, ...(dados ? { body: JSON.stringify(dados) } : {}), cache: 'no-store' });
    if (!r.ok) { const e = await r.json().catch(() => ({})); throw Error(e.mensagem || e.detail || `Publicação indisponível (${r.status}).`); }
    return binario ? r.blob() : r.json();
  }
  async function baixarPublicacao(id, sufixo, nome) {
    try {
      const blob = await apiPublicacao(`/publicacoes/${id}/${sufixo}`, null, 'GET', true);
      const url = URL.createObjectURL(blob), link = document.createElement('a');
      link.href = url; link.download = nome; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (erro) { mensagem('nostr-publicacao-estado', erro.message, true); }
  }
  async function carregarPublicacoes() {
    try {
      const config = await apiPublicacao('/estado'); publicacaoAutomatica = config.ativa && config.servicoConfigurado;
      mensagem('nostr-publicacao-config', publicacaoAutomatica ? `Publicação automática ativa. Perfil: ${config.chavePublica}` : 'Cadastre e autorize o perfil da FIRMA para publicar automaticamente os próximos aceites.');
      const lista = await apiPublicacao('/publicacoes');
      const painel = porId('nostr-publicacoes-lista'); painel.replaceChildren();
      for (const item of lista) {
        const caixa = document.createElement('article'); caixa.className = 'inventor-resultado';
        const resumo = document.createElement('p'); resumo.className = 'inventor-resumo';
        const rotulos = { PENDENTE: 'Pendente', PARCIAL: 'Publicado em parte dos relays', PUBLICADO: 'Aceito por pelo menos dois relays', SUSPENSA: 'Suspensa por troca de identidade', AGUARDANDO_NO_BITCOIN: 'Aguardando verificação no Bitcoin Core', CONFIRMADA: 'Anterioridade confirmada', NAO_APLICAVEL: 'Não se aplica ao perfil' };
        resumo.textContent = `${item.tipo === 'PERFIL' ? 'Perfil da empresa' : 'Comprovante de aceite'}\nPublicação: ${rotulos[item.publicacao_estado] || item.publicacao_estado}\nAnterioridade: ${rotulos[item.anterioridade_estado] || item.anterioridade_estado}${item.automacao_ativa ? '' : '\nAutomação pausada'}${item.evento_id ? '\nEvento: ' + item.evento_id : ''}${item.bloco ? '\nBloco Bitcoin: ' + item.bloco : ''}${item.erro_codigo ? '\nO serviço tentará novamente. Confira o assinador e a conexão.' : ''}`;
        caixa.append(resumo);
        for (const relay of item.relays || []) {
          const linha = document.createElement('p'); linha.textContent = `${relay.aceito ? 'Aceito' : 'Pendente'}: ${relay.url}`; caixa.append(linha);
        }
        const acoes = [['pacote', 'BAIXAR PACOTE PRIVADO', 'prova-publicacao.json']];
        if (item.evento_id) acoes.push(['evento', 'BAIXAR EVENTO PÚBLICO', 'evento-nostr.json']);
        if (item.tem_ots) acoes.push(['ots', 'BAIXAR CARIMBO OTS', 'evento-nostr.json.ots']);
        for (const [sufixo, texto, nome] of acoes) {
          const botao = document.createElement('button'); botao.type = 'button'; botao.className = 'botao-carteira'; botao.textContent = texto;
          botao.addEventListener('click', () => baixarPublicacao(item.id, sufixo, nome)); caixa.append(botao);
        }
        const tentar = document.createElement('button'); tentar.type = 'button'; tentar.className = 'botao-carteira'; tentar.textContent = 'TENTAR NOVAMENTE';
        tentar.addEventListener('click', async () => { try { await apiPublicacao(`/publicacoes/${item.id}/tentar`, {}, 'POST'); mensagem('nostr-publicacao-estado', 'Nova tentativa agendada.'); } catch (erro) { mensagem('nostr-publicacao-estado', erro.message, true); } }); caixa.append(tentar);
        painel.append(caixa);
      }
    } catch (erro) { publicacaoAutomatica = false; mensagem('nostr-publicacao-config', erro.message, true); }
  }
  async function publicarAceite(evento) {
    provaParaPublicar = evento;
    try {
      await apiPublicacao('/publicacoes', { evento }, 'POST');
      porId('nostr-publicacao-reenviar').hidden = true;
      mensagem('nostr-publicacao-estado', 'Aceite registrado na fila. A publicação e a anterioridade são acompanhadas separadamente abaixo.');
      await carregarPublicacoes();
    } catch (erro) { porId('nostr-publicacao-reenviar').hidden = false; mensagem('nostr-publicacao-estado', `Sua prova local está preservada. ${erro.message}`, true); }
  }
  porId('nostr-publicacoes-atualizar').addEventListener('click', carregarPublicacoes);
  porId('nostr-publicacao-reenviar').addEventListener('click', () => { if (provaParaPublicar) publicarAceite(provaParaPublicar); });
  carregarPublicacoes();
  function limpar() {
    ++versao;
    for (const endereco of enderecos) URL.revokeObjectURL(endereco);
    enderecos = [];
    for (const id of ['nostr-baixar-prova', 'nostr-baixar-pdf']) porId(id).removeAttribute('href');
    porId('nostr-resultado').hidden = true;
    mensagem('nostr-estado', '');
  }
  function extensao() {
    if (!window.nostr?.getPublicKey || !window.nostr?.signEvent)
      throw Error('Use uma extensão Nostr compatível com NIP-07 para conectar e assinar.');
    return window.nostr;
  }
  function validarChave(valor) {
    if (typeof valor !== 'string' || !/^[0-9a-f]{64}$/.test(valor)) throw Error('Chave pública Nostr inválida.');
    return valor;
  }
  async function lerPdf(arquivo) {
    if (!arquivo || arquivo.size < 5 || arquivo.size > 20 * 1024 * 1024) throw Error('Escolha um PDF de até 20 MB.');
    const bytes = await arquivo.arrayBuffer();
    if (new TextDecoder().decode(bytes.slice(0, 5)) !== '%PDF-') throw Error('O arquivo não tem um cabeçalho PDF válido.');
    const hash = Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', bytes)), b => b.toString(16).padStart(2, '0')).join('');
    return { hash, tamanho: arquivo.size, nome: arquivo.name };
  }
  function conferirEvento(bruto) {
    // Uma cópia JSON impede que um marcador de cache vindo da extensão pule a verificação.
    const evento = JSON.parse(JSON.stringify(bruto));
    if (!evento || evento.kind !== 1 || typeof evento.content !== 'string' || evento.content.length > 32768 ||
        !Number.isSafeInteger(evento.created_at) || evento.created_at < 0 || !/^[0-9a-f]{64}$/.test(evento.id) ||
        !/^[0-9a-f]{128}$/.test(evento.sig)) throw Error('Formato da prova Nostr inválido.');
    validarChave(evento.pubkey);
    if (FirmaNostrCrypto.getEventHash(evento) !== evento.id || !FirmaNostrCrypto.verifyEvent(evento))
      throw Error('Assinatura Nostr inválida ou prova alterada.');
    const aceite = JSON.parse(evento.content);
    if (!aceite || typeof aceite !== 'object' || aceite.protocolo !== PROTOCOLO || aceite.acao !== 'aceitar-documento' || aceite.algoritmo !== 'SHA-256' ||
        !/^[0-9a-f]{64}$/.test(aceite.hashPdf) || !/^[0-9a-f]{64}$/.test(aceite.desafio) ||
        typeof aceite.papel !== 'string' || !aceite.papel.trim() || aceite.papel.length > 80 ||
        aceite.textoAceite !== TEXTO_ACEITE || !Number.isSafeInteger(aceite.tamanhoPdf) || aceite.tamanhoPdf < 5)
      throw Error('Esta prova não contém um aceite compatível com a aFIRMA.');
    return { evento, aceite };
  }
  porId('nostr-conectar').addEventListener('click', async evento => {
    const botao = evento.currentTarget;
    botao.disabled = true;
    limpar();
    const atual = versao;
    try {
      const publica = validarChave(await extensao().getPublicKey());
      if (atual !== versao) return;
      chave = publica;
      mensagem('nostr-chave', `Chave conectada: ${chave}`);
    } catch (erro) { if (atual === versao) mensagem('nostr-estado', erro.message, true); }
    finally { botao.disabled = false; }
  });
  porId('nostr-pdf').addEventListener('change', async () => {
    limpar();
    porId('nostr-aceite').checked = false;
    const atual = versao;
    try {
      const pdf = await lerPdf(porId('nostr-pdf').files[0]);
      if (atual === versao) mensagem('nostr-documento', `${pdf.nome}\nSHA-256: ${pdf.hash}`);
    } catch (erro) { if (atual === versao) mensagem('nostr-documento', erro.message, true); }
  });
  porId('nostr-papel').addEventListener('input', () => { limpar(); porId('nostr-aceite').checked = false; });
  porId('nostr-aceite').addEventListener('change', limpar);
  porId('nostr-assinar').addEventListener('click', async eventoClique => {
    const botao = eventoClique.currentTarget;
    botao.disabled = true;
    limpar();
    const atual = versao;
    const acessoEmpresa = localStorage.getItem('assineai-token') || '';
    try {
      if (!porId('nostr-aceite').checked) throw Error('Confirme a leitura e o aceite do PDF.');
      const papel = porId('nostr-papel').value.trim();
      if (!papel || papel.length > 80) throw Error('Informe seu papel no documento.');
      if (!chave) throw Error('Conecte sua chave Nostr antes de assinar.');
      const provedor = extensao(), esperada = chave, arquivo = porId('nostr-pdf').files[0];
      const pdf = await lerPdf(arquivo);
      if (atual !== versao) throw Error('Documento ou aceite alterado. Confira os dados e assine novamente.');
      if (await provedor.getPublicKey() !== esperada) throw Error('A chave da extensão mudou. Conecte novamente.');
      if (atual !== versao) throw Error('Documento ou aceite alterado. Confira os dados e assine novamente.');
      const desafio = Array.from(crypto.getRandomValues(new Uint8Array(32)), b => b.toString(16).padStart(2, '0')).join('');
      const pedido = { kind: 1, created_at: Math.floor(Date.now() / 1000), tags: [['client', 'aFIRMA']], content: JSON.stringify({
        protocolo: PROTOCOLO, acao: 'aceitar-documento', algoritmo: 'SHA-256',
        hashPdf: pdf.hash, tamanhoPdf: pdf.tamanho, nomePdf: pdf.nome,
        identificador: crypto.randomUUID(), desafio, papel, textoAceite: TEXTO_ACEITE,
      }) };
      mensagem('nostr-estado', 'Confira o aceite e autorize a assinatura na extensão Nostr.');
      // A extensão recebe uma cópia; o pedido original é preservado para conferir a resposta.
      const assinado = await provedor.signEvent(JSON.parse(JSON.stringify(pedido)));
      const { evento } = conferirEvento(assinado);
      const chaveDepois = await provedor.getPublicKey();
      if (atual !== versao || chave !== esperada || chaveDepois !== esperada)
        throw Error('Chave, documento ou aceite alterado. Assine novamente.');
      if (evento.pubkey !== esperada || evento.content !== pedido.content || evento.kind !== pedido.kind ||
          evento.created_at !== pedido.created_at || JSON.stringify(evento.tags) !== JSON.stringify(pedido.tags))
        throw Error('A extensão devolveu uma assinatura de outro pedido.');
      const prova = new Blob([JSON.stringify({ formato: PROTOCOLO, evento }, null, 2)], { type: 'application/json' });
      const urlProva = URL.createObjectURL(prova), urlPdf = URL.createObjectURL(arquivo);
      enderecos = [urlProva, urlPdf];
      porId('nostr-baixar-prova').href = urlProva;
      porId('nostr-baixar-prova').download = `${arquivo.name}.nostr.json`;
      porId('nostr-baixar-pdf').href = urlPdf;
      porId('nostr-baixar-pdf').download = arquivo.name;
      porId('nostr-resultado').hidden = false;
      mensagem('nostr-estado', `Aceite assinado e conferido. Evento: ${evento.id}`);
      provaParaPublicar = evento;
      try {
        const config = await apiPublicacao('/estado');
        if ((localStorage.getItem('assineai-token') || '') !== acessoEmpresa) throw Error('A empresa conectada mudou. Confira o acesso antes de publicar.');
        if (config.ativa) await publicarAceite(evento);
      } catch (erroPublicacao) {
        porId('nostr-publicacao-reenviar').hidden = false;
        mensagem('nostr-publicacao-estado', `A prova local está pronta. ${erroPublicacao.message}`, true);
      }
    } catch (erro) { if (atual === versao) mensagem('nostr-estado', erro.message || 'Não foi possível assinar o aceite.', true); }
    finally { botao.disabled = false; }
  });
  for (const id of ['nostr-verificar-pdf', 'nostr-verificar-prova', 'nostr-chave-esperada', 'nostr-chave-firma-esperada']) porId(id).addEventListener('input', () => {
    ++versaoVerificacao; mensagem('nostr-verificacao', '');
  });
  porId('nostr-verificar').addEventListener('click', async eventoClique => {
    const botao = eventoClique.currentTarget, atual = ++versaoVerificacao;
    botao.disabled = true;
    mensagem('nostr-verificacao', 'Conferindo arquivo e assinatura…');
    try {
      const pdf = await lerPdf(porId('nostr-verificar-pdf').files[0]);
      const arquivo = porId('nostr-verificar-prova').files[0];
      if (!arquivo || arquivo.size > 128 * 1024) throw Error('Escolha uma prova JSON de até 128 KB.');
      const pacote = JSON.parse(await arquivo.text());
      const publicacao = pacote.formato === 'afirma.nostr.publicacao.v1';
      if (!publicacao && pacote.formato !== PROTOCOLO) throw Error('Formato de prova não reconhecido.');
      const { evento, aceite } = conferirEvento(publicacao ? pacote.pedidoPrivado?.aceite : pacote.evento);
      let chaveFirma;
      if (publicacao) {
        const publico = JSON.parse(pacote.eventoCanonico || 'null');
        if (!publico || publico.kind !== 1 || FirmaNostrCrypto.getEventHash(publico) !== publico.id || !FirmaNostrCrypto.verifyEvent(publico)) throw Error('Assinatura pública da FIRMA inválida.');
        const sal = pacote.pedidoPrivado?.sal;
        if (!/^[0-9a-f]{64}$/.test(sal)) throw Error('O pacote privado não contém o sal da publicação.');
        const prefixo = new TextEncoder().encode('afirma.nostr.comprovante.v1' + String.fromCharCode(0));
        const bytes = new Uint8Array(prefixo.length + 64); bytes.set(prefixo);
        bytes.set(Uint8Array.from(sal.match(/../g), h => parseInt(h, 16)), prefixo.length);
        bytes.set(Uint8Array.from(evento.id.match(/../g), h => parseInt(h, 16)), prefixo.length + 32);
        const compromisso = Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', bytes)), b => b.toString(16).padStart(2, '0')).join('');
        const registro = JSON.parse(publico.content);
        if (registro.protocolo !== 'afirma.nostr.comprovante.v1' || registro.acao !== 'registrar-aceite' || registro.algoritmo !== 'SHA-256' || registro.compromisso !== compromisso) throw Error('O comprovante público não corresponde ao aceite privado.');
        chaveFirma = publico.pubkey;
      }
      if (aceite.hashPdf !== pdf.hash || aceite.tamanhoPdf !== pdf.tamanho) throw Error('O PDF não corresponde ao documento aceito nesta prova.');
      const firmaEsperada = porId('nostr-chave-firma-esperada').value.trim().toLowerCase();
      if (firmaEsperada && (!chaveFirma || validarChave(firmaEsperada) !== chaveFirma)) throw Error('A publicação não corresponde à chave esperada da FIRMA.');
      const esperada = porId('nostr-chave-esperada').value.trim().toLowerCase();
      if (esperada && validarChave(esperada) !== evento.pubkey) throw Error('A prova foi assinada por outra chave pública.');
      if (atual !== versaoVerificacao) return;
      mensagem('nostr-verificacao', `Assinatura criptográfica válida. O PDF corresponde ao aceite.\nChave: ${evento.pubkey}\nPapel declarado: ${aceite.papel}${chaveFirma ? '\nCompromisso público conferido. Chave da FIRMA: ' + chaveFirma + '\nConfira a anterioridade com o arquivo do evento público e sua prova .ots; o estado escrito no pacote não substitui essa verificação.' : ''}\nData declarada pelo assinante: ${new Date(evento.created_at * 1000).toISOString()}\nA identidade civil não foi confirmada por esta conferência.`);
    } catch (erro) { if (atual === versaoVerificacao) mensagem('nostr-verificacao', erro.message || 'Prova inválida.', true); }
    finally { botao.disabled = false; }
  });
  fetch('/api/assinaturas/politica', { cache: 'no-store' }).then(async resposta => {
    if (!resposta.ok) throw Error('Não foi possível consultar a disponibilidade do Ethereum.');
    const politica = await resposta.json();
    porId('area-blockchain').hidden = !politica.carteiraExperimental;
    mensagem('ethereum-politica', politica.carteiraExperimental ? 'Operações Ethereum disponíveis.' : 'Novas assinaturas Ethereum estão desativadas nesta instalação. A conferência de provas existentes continua disponível.');
  }).catch(erro => mensagem('ethereum-politica', erro.message, true));
  let verificacaoEthereum = 0;
  porId('ethereum-verificar-pdf').addEventListener('change', () => { ++verificacaoEthereum; mensagem('ethereum-verificacao', ''); });
  porId('ethereum-verificar').addEventListener('click', async eventoClique => {
    const atual = ++verificacaoEthereum;
    const botao = eventoClique.currentTarget;
    botao.disabled = true;
    try {
      const arquivo = porId('ethereum-verificar-pdf').files[0];
      if (!arquivo || arquivo.size > 20 * 1024 * 1024) throw Error('Escolha um PDF de até 20 MB.');
      const dados = new FormData(); dados.append('pdf', arquivo);
      const resposta = await fetch('/api/blockchain/verificar', { method: 'POST', body: dados, cache: 'no-store' });
      const prova = await resposta.json();
      if (!resposta.ok) throw Error(prova.mensagem || prova.detail || 'Não foi possível conferir a prova Ethereum.');
      if (atual !== verificacaoEthereum) return;
      mensagem('ethereum-verificacao', `Prova conferida.\nCarteira: ${prova.carteira}\nTransação: ${prova.transacao}`);
    } catch (erro) { if (atual === verificacaoEthereum) mensagem('ethereum-verificacao', erro.message, true); }
    finally { botao.disabled = false; }
  });
  window.addEventListener('pagehide', limpar);
})();
