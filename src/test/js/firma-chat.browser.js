(async () => {
  const resultados = [],
    assert = (ok, mensagem) => {
      if (!ok) throw Error(mensagem);
    },
    esperar = () => new Promise(registro => setTimeout(registro, 40));
  async function teste(nome, executarAcao) {
    try {
      await executarAcao();
      resultados.push({
        nome,
        ok: true
      });
    } catch (evento) {
      resultados.push({
        nome,
        ok: false,
        erro: evento.stack
      });
    }
  }
  await esperar();
  await esperar();
  await teste('Token salvo não conecta nem carrega registros automaticamente', async () => {
    assert(!window.FirmaAcesso && !window.FirmaContexto, 'Conexão automática indevida');
    assert(!window.chamadasFirmaChat.length, 'API chamada antes de Conectar');
    assert(!document.querySelector('[data-conversa-id]'), 'Conversa carregada sem conexão');
    assert(document.getElementById('chat-nova').disabled, 'Nova conversa liberada sem conexão');
    document.getElementById('lista-conectar').click();
    await esperar();
    await esperar();
    assert(window.FirmaAcesso && document.body.classList.contains('firma-autenticado'), 'Conexão explícita falhou');
  });
  await teste('Abas pertencem à barra do trator e usam o desenho das assinaturas', async () => {
    assert(getComputedStyle(document.body).backgroundColor === 'rgb(14, 33, 23)', 'Fundo da FIRMA perdido');
    assert(getComputedStyle(document.querySelector('.firma-sidebar')).display === 'none', 'Menu lateral visível');
    const navegacao = document.querySelector('.firma-chat-navegacao'),
      barra = document.querySelector('#chat-tabela .toolbar-grupo');
    assert(barra.contains(navegacao), 'Abas fora do trator');
    assert(barra.contains(document.getElementById('chat-nova')), 'Novo fora da barra');
    assert(document.querySelector('.lista-rodape').contains(document.getElementById('chat-expandir')), 'Expandir fora do cabeçalho da lista');
    assert(!document.querySelector('.firma-chat-heading'), 'Cabeçalho separado do trator');
    assert(document.querySelector('#chat-tabela .lista-rodape .titulo-trator'), 'Título fora do rodapé');
    assert(document.querySelector('.rodape-fixo-trator #lista-conectar'), 'Acesso sem o rodapé padrão');
    const grupos = document.querySelectorAll('#firma-chat-grupos .trator-aba');
    assert(grupos.length === 1 && grupos[0].textContent === 'TELAS', 'As telas não foram agrupadas em uma única aba');
    assert(barra.lastElementChild.classList.contains('trator-abas-finais') && barra.lastElementChild.contains(grupos[0]), 'Telas não é a última aba');
    assert([...grupos].every(botao => barra.contains(botao)), 'Grupos fora da barra');
    assert(getComputedStyle(grupos[0], '::before').maskImage.includes('svg'), 'Curva das abas de assinatura ausente');
    grupos[0].click();
    await esperar();
    const menu = document.getElementById(grupos[0].getAttribute('aria-controls'));
    assert(menu.matches(':popover-open') && menu.querySelectorAll('a').length >= 3 && menu.querySelectorAll('h3').length >= 3, 'Menu sem todas as telas e seus grupos');
    assert(grupos[0].getAttribute('aria-expanded') === 'true', 'Estado da aba incorreto');
    const item = menu.querySelector('a');
    assert(getComputedStyle(item).color === 'rgb(242, 247, 236)', 'Texto do menu sem contraste');
    item.focus();
    assert(getComputedStyle(item).backgroundColor === 'rgb(201, 255, 61)' && getComputedStyle(item).color === 'rgb(14, 33, 23)', 'Foco do menu sem contraste');
    menu.hidePopover();
  });
  await teste('Zapiou preserva as alturas e os verdes do AssineAI e reúne as telas', () => {
    const faixa = document.querySelector('.trator-faixa-abas'),
      filtro = document.querySelector('.trator-burro-fixo'),
      marca = faixa.querySelector('.trator-marca'),
      aba = faixa.querySelector('[href="/chat"]');
    assert(aba.textContent === 'Zapiou' && document.title.includes('Zapiou'), 'Nome do módulo incorreto');
    assert(getComputedStyle(marca).getPropertyValue('--cor-aba').trim() === '#3B2A1A', 'Marca sem cor de terra');
    assert(aba.getBoundingClientRect().height === 56 && marca.getBoundingClientRect().height === 50, 'Alturas diferentes das abas do AssineAI');
    assert(getComputedStyle(faixa.querySelector('[href="/assineai/trator"]')).getPropertyValue('--cor-aba').includes('color-mix'), 'Verde do módulo substituído');
    assert(document.getElementById('chat-nova').textContent === '+ Zaps', 'Comando de novo atendimento incorreto');
    const colunas = document.getElementById('firma-chat-colunas-botao');
    assert(getComputedStyle(colunas).getPropertyValue('--cor-aba').trim() === '#d2bc8e', 'Colunas sem cor de papel');
    assert(aba.getBoundingClientRect().top < filtro.getBoundingClientRect().top, 'Filtro esconde o topo das fichas');
    assert(getComputedStyle(filtro).position === 'absolute' && getComputedStyle(filtro.querySelector('.af-busca')).minHeight === '40px', 'Filtro diferente da estrutura compartilhada');
    const inicio = faixa.scrollLeft;
    faixa.dispatchEvent(new KeyboardEvent('keydown', {key:'End', bubbles:true}));
    assert(faixa.scrollWidth <= faixa.clientWidth || faixa.scrollLeft > inicio, 'Abas não deslizam por trás do filtro');
    assert(aba.getBoundingClientRect().top < filtro.getBoundingClientRect().top, 'Rolagem escondeu o topo');
    faixa.dispatchEvent(new KeyboardEvent('keydown', {key:'Home', bubbles:true}));
  });
  await teste('Busca fica oculta na tabela', () => {
    const campo = document.getElementById('chat-busca');
    assert(campo.closest('#chat-ferramentas[hidden]'), 'Busca visível fora do chat expandido');
    assert(!document.querySelector('#chat-tabela #chat-busca'), 'Busca dentro da tabela');
  });
  await teste('Conversas e última mensagem aparecem no trator', () => {
    assert(document.querySelectorAll('#chat-tabela tbody tr[data-conversa-id]').length === 4, 'Conversas ausentes');
    assert(document.querySelector('#chat-tabela').textContent.includes('Olá, Marina'), 'Prévia ausente');
    assert(document.querySelectorAll('#chat-tabela th').length >= 6, 'Cabeçalhos ausentes');
  });
  await teste('Todos os recursos do trator de assinaturas estão habilitados', () => {
    for (const [nome, valor] of Object.entries(FirmaTrator.recursos)) assert(tabelaFirmaChatTeste.recursos[nome] === valor, 'Recurso divergente: ' + nome);
    assert(tabelaFirmaChatTeste.estado.tamanho === 10, 'Tamanho inicial difere da assinatura');
    assert(document.querySelector('th .titulo-coluna-fixa').textContent === 'AÇÕES', 'Coluna Ações ausente');
    assert(document.querySelectorAll('[data-conversa-id="1"] .acoes-padrao .icone').length === 4, 'Ações incompletas');
  });
  await teste('Selecionar tudo cobre páginas e mostra contador; checkbox por linha funciona', () => {
    const itemAtual = tabelaFirmaChatTeste;
    itemAtual.definirTamanhoPagina(2);
    document.querySelector('#chat-tabela thead input[type=checkbox]').click();
    assert(itemAtual.obterSelecionados().length === 4, 'Selecionar tudo só marcou a página atual');
    assert(document.querySelector('.trator-selecao').textContent === '4 selecionados', 'Contador ausente');
    document.querySelector('[data-conversa-id="1"] input[type=checkbox]').click();
    assert(itemAtual.obterSelecionados().length === 3 && document.querySelector('thead input[type=checkbox]').indeterminate, 'Seleção parcial incorreta');
    itemAtual.limparSelecao();
    itemAtual.definirTamanhoPagina(30);
  });
  await teste('Seletor de colunas tem selecionar todas, remover opcionais e aplicação persistida', () => {
    const itemAtual = tabelaFirmaChatTeste;
    itemAtual.no('colunas-botao').click();
    let menu = itemAtual.no('menu-colunas');
    [...menu.querySelectorAll('button')].find(botao => botao.textContent === 'Selecionar todas').click();
    [...menu.querySelectorAll('button')].find(botao => botao.textContent === 'Aplicar').click();
    assert(itemAtual.estado.visiveis.includes('telefone') && itemAtual.estado.visiveis.includes('email'), 'Selecionar todas falhou');
    assert(JSON.parse(localStorage.getItem(itemAtual.storageId + '-layout')).visiveis.includes('email'), 'Layout não persistiu');
    itemAtual.no('colunas-botao').click();
    menu = itemAtual.no('menu-colunas');
    [...menu.querySelectorAll('button')].find(botao => botao.textContent === 'Desmarcar opcionais').click();
    assert(menu.querySelector('input[type=checkbox]:disabled').checked, 'Contato obrigatório foi desmarcado');
    [...menu.querySelectorAll('button')].find(botao => botao.textContent === 'Restaurar').click();
    [...menu.querySelectorAll('button')].find(botao => botao.textContent === 'Aplicar').click();
  });
  await teste('Filtro salvo no cabeçalho é persistido e pode ser reaplicado', () => {
    const itemAtual = tabelaFirmaChatTeste,
      originalPrompt = window.prompt;
    window.prompt = () => 'Pendentes do Zapiou';
    try {
      itemAtual.filtrar('status', ['Pendente']);
      [...document.querySelectorAll('thead .titulo-coluna')].find(botao => botao.textContent === 'ATENDIMENTOS').click();
      [...document.querySelectorAll('.coluna-menu button')].find(botao => botao.textContent === 'Salvar filtro atual').click();
      assert(JSON.parse(localStorage.getItem(itemAtual.storageId + '-filtros')).some(campoAtual => campoAtual.nome === 'Pendentes do Zapiou'), 'Filtro salvo não persistiu');
      itemAtual.limparFiltros();
      [...document.querySelectorAll('thead .titulo-coluna')].find(botao => botao.textContent === 'ATENDIMENTOS').click();
      [...document.querySelectorAll('.coluna-menu button')].find(botao => botao.textContent === 'Pendentes do Zapiou').click();
      assert(itemAtual.dados().length === 1 && itemAtual.dados()[0].status === 'Pendente', 'Filtro salvo não aplicou');
      itemAtual.limparFiltros();
    } finally {
      window.prompt = originalPrompt;
    }
  });
  await teste('Largura segue os mesmos três modos do trator de assinaturas', () => {
    const conteudoPrincipal = document.querySelector('main'),
      botao = document.getElementById('alternar-largura');
    assert(conteudoPrincipal.dataset.largura === 'total', 'Modo inicial incorreto');
    botao.click();
    assert(conteudoPrincipal.dataset.largura === 'largo', 'Modo 80% ausente');
    botao.click();
    assert(conteudoPrincipal.dataset.largura === 'centro', 'Modo centro ausente');
    botao.click();
    assert(conteudoPrincipal.dataset.largura === 'total', 'Ciclo incompleto');
  });
  await teste('Visualizar, editar contato e histórico executam as ações correspondentes', async () => {
    const acoes = () => document.querySelector('[data-conversa-id="1"] .acoes-padrao');
    acoes().querySelector('.btn-ver-linha').click();
    await esperar();
    assert(document.querySelector('dialog.af-detalhes')?.open, 'Visualizar não abre detalhes');
    assert([...document.querySelectorAll('dialog.af-detalhes button')].some(botao => botao.textContent === 'ABRIR CONVERSA ↧'), 'Detalhes sem acesso ao chat');
    tabelaFirmaChatTeste.dialogo.close();
    acoes().querySelector('.btn-editar-linha').click();
    await esperar();
    const janela = tabelaFirmaChatTeste.dialogo;
    assert(janela.querySelectorAll('input').length === 3, 'Campos de contato incorretos');
    janela.querySelector('input').value = 'Marina Souza';
    [...janela.querySelectorAll('button')].find(botao => botao.textContent === 'Salvar alterações').click();
    await esperar();
    assert(chamadasDetalhadas.some(itemDoRegistro => itemDoRegistro.metodo === 'PUT' && itemDoRegistro.url.endsWith('registros/contatos/1') && itemDoRegistro.dados.ContatoNome === 'Marina Souza'), 'Contato não foi salvo');
    assert(document.querySelector('[data-conversa-id="1"] .firma-chat-contato').textContent.includes('Marina Souza'), 'Contato não atualizou');
    acoes().querySelector('[aria-label^="Histórico"]').click();
    await esperar();
    assert(document.getElementById('chat-historico-dialog').open && document.getElementById('chat-historico-conteudo').textContent.includes('PROTO-1'), 'Histórico não abriu');
    document.getElementById('chat-historico-dialog').close();
  });
  await teste('Finalizar e reativar usam as operações do atendimento', async () => {
    document.querySelector('[data-conversa-id="1"] [aria-label="Finalizar atendimento"]').click();
    await esperar();
    await esperar();
    assert(document.querySelector('[data-conversa-id="1"] [aria-label="Reativar atendimento"]'), 'Finalizar não atualizou ações');
    document.querySelector('[data-conversa-id="1"] [aria-label="Reativar atendimento"]').click();
    await esperar();
    await esperar();
    assert(document.getElementById('chat-nova-dialog').open, 'Reabertura não abriu formulário');
    document.getElementById('chat-novo-atendente').value = '1';
    document.getElementById('chat-novo-texto').value = 'Retomar atendimento';
    document.getElementById('chat-nova-form').dispatchEvent(new Event('submit', {
      bubbles: true,
      cancelable: true
    }));
    await esperar();
    await esperar();
    assert(document.querySelector('[data-conversa-id="1"] [aria-label="Finalizar atendimento"]'), 'Reativar não atualizou ações');
  });
  await teste('Busca do Burro de Carga mostra a prévia antes de aplicar', async () => {
    const itemAtual = tabelaFirmaChatTeste;
    await itemAtual.buscarIA('Conversas pendentes');
    assert(itemAtual.dados().length === 4, 'Busca aplicou sem revisão');
    const painel = itemAtual.no('plano-busca');
    assert(painel.textContent.includes('Conversas pendentes'), 'Prévia ausente');
    [...painel.querySelectorAll('button')].find(botao => botao.textContent === 'Aplicar').click();
    assert(itemAtual.dados().length === 1 && itemAtual.dados()[0].id === 1, 'Plano não aplicou');
    itemAtual.limparFiltros();
  });
  await teste('Exportação usa a seleção e gera um arquivo CSV real', async () => {
    document.querySelector('[data-conversa-id="1"] input[type=checkbox]').click();
    document.getElementById('exportar-dados').click();
    assert(document.getElementById('chat-exportar-resumo').textContent.includes('1 conversa(s) selecionada'), 'Exportação ignorou seleção');
    document.getElementById('chat-exportar-concordo').click();
    document.querySelector('[data-chat-formato=csv]').click();
    assert(arquivosExportados.length === 1, 'Arquivo não gerado');
    const valorCsv = await arquivosExportados[0].text();
    assert(valorCsv.includes('CONTATO') && valorCsv.includes('Marina Souza') && !valorCsv.includes('João'), 'CSV não respeitou os registros selecionados');
    tabelaFirmaChatTeste.limparSelecao();
  });
  await teste('Importação solicita histórico somente depois de escolher o canal', async () => {
    document.getElementById('importar-dados').click();
    await esperar();
    assert(document.getElementById('chat-importar-dialog').open, 'Importação não abriu');
    document.getElementById('chat-importar-canal').value = '1';
    document.getElementById('chat-importar-form').dispatchEvent(new Event('submit', {
      bubbles: true,
      cancelable: true
    }));
    await esperar();
    await esperar();
    assert(chamadasDetalhadas.some(itemDoRegistro => itemDoRegistro.metodo === 'POST' && itemDoRegistro.url.endsWith('canais/1/importar')), 'Importação não solicitada');
  });
  await teste('Contato tem foto, nome, telefone e fallback para foto indisponível', () => {
    const linha = document.querySelector('[data-conversa-id="1"]'),
      semFoto = document.querySelector('[data-conversa-id="2"]');
    assert(linha.querySelector('.firma-chat-contato-avatar img'), 'Foto do contato ausente');
    assert(linha.querySelector('.firma-chat-contato-identidade').textContent.includes('5511999999999'), 'Telefone ausente');
    assert(!semFoto.querySelector('.firma-chat-contato-avatar img') && semFoto.querySelector('.firma-chat-contato-avatar').textContent === 'J', 'Fallback sem foto incorreto');
    linha.querySelector('.firma-chat-contato-avatar img').dispatchEvent(new Event('error'));
    assert(!linha.querySelector('.firma-chat-contato-avatar img') && linha.querySelector('.firma-chat-contato-avatar').textContent === 'MS', 'Fallback de imagem quebrada incorreto');
  });
  await teste('Duplo clique abre a conversa abaixo da linha', async () => {
    document.querySelector('[data-conversa-id="1"]').dispatchEvent(new MouseEvent('dblclick', {
      bubbles: true
    }));
    await esperar();
    await esperar();
    assert(document.querySelector('.firma-chat-linha-chat #chat-conversa'), 'Conversa fora da linha');
    assert(document.querySelectorAll('.firma-chat-linha-chat').length === 1, 'Linhas expandidas duplicadas');
    assert(document.querySelector('#chat-mensagens').textContent.includes('Histórico completo'), 'Histórico ausente');
    assert(!document.getElementById('chat-envio').hidden, 'Compositor ausente');
  });
  await teste('Expandir e recolher preserva o rascunho e o histórico', () => {
    document.getElementById('chat-texto').value = 'Meu rascunho';
    document.getElementById('chat-expandir').click();
    assert(document.body.classList.contains('firma-chat-integral'), 'Modo integral ausente');
    assert(document.getElementById('chat-painel').parentElement.tagName === 'MAIN', 'Painel fora do chat integral');
    assert(getComputedStyle(document.querySelector('#chat-tabela .tabela-scroll')).display === 'none', 'Tabela visível no modo integral');
    assert(document.querySelector('.lista-rodape').contains(document.getElementById('chat-expandir')), 'Comando de retorno fora da lista lateral');
    assert(getComputedStyle(document.getElementById('chat-lista')).display !== 'none', 'Lista de pessoas ausente');
    const busca = document.getElementById('chat-busca');
    assert(busca.closest('.relacionamento-inbox'), 'Busca ausente no chat expandido');
    busca.focus();
    tabelaFirmaChatTeste.render();
    assert(document.activeElement === busca, 'Atualização perdeu o foco da busca');
    busca.value = 'Marina';
    document.getElementById('chat-expandir').click();
    assert(document.querySelector('.firma-chat-linha-chat #chat-painel'), 'Não voltou à linha');
    assert(document.getElementById('chat-texto').value === 'Meu rascunho', 'Rascunho perdido');
    assert(document.getElementById('chat-busca').closest('#chat-ferramentas[hidden]'), 'Busca permaneceu visível ao recolher');
    assert(!document.getElementById('chat-busca').value, 'Busca oculta manteve o filtro');
  });
  await teste('X do chat fica na área visível mesmo com rolagem horizontal e fecha sem finalizar', async () => {
    const areaRolavel = document.querySelector('#chat-tabela .tabela-scroll'),
      botao = document.getElementById('chat-recolher');
    botao.scrollIntoView({
      block: 'center'
    });
    areaRolavel.scrollLeft = 0;
    await esperar();
    const area = areaRolavel.getBoundingClientRect(),
      retangulo = botao.getBoundingClientRect();
    assert(retangulo.width >= 40 && retangulo.left >= area.left && retangulo.right <= area.right + 1, 'X fora da largura visível');
    areaRolavel.scrollLeft = 120;
    await esperar();
    const movido = botao.getBoundingClientRect();
    assert(movido.left >= area.left && movido.right <= area.right + 1, 'Rolagem horizontal esconde o X');
    const operacoes = chamadasDetalhadas.filter(itemDoRegistro => itemDoRegistro.url.endsWith('/acao')).length;
    botao.click();
    assert(!document.querySelector('.firma-chat-linha-chat'), 'X não recolheu a linha');
    assert(chamadasDetalhadas.filter(itemDoRegistro => itemDoRegistro.url.endsWith('/acao')).length === operacoes, 'Fechar finalizou o atendimento');
    document.getElementById('chat-atualizar').click();
    await esperar();
    await esperar();
    assert(!document.querySelector('.firma-chat-linha-chat'), 'Atualização reabriu conversa fechada');
    areaRolavel.scrollLeft = 0;
  });
  await teste('X também fecha o chat integral e retorna ao trator', async () => {
    document.querySelector('[data-conversa-id="1"]').dispatchEvent(new MouseEvent('dblclick', {
      bubbles: true
    }));
    await esperar();
    await esperar();
    document.getElementById('chat-expandir').click();
    assert(document.body.classList.contains('firma-chat-integral'), 'Chat integral não abriu');
    document.getElementById('chat-recolher').click();
    assert(!document.body.classList.contains('firma-chat-integral') && !document.querySelector('.firma-chat-linha-chat'), 'X não voltou ao trator');
    assert(getComputedStyle(document.querySelector('#chat-tabela .tabela-painel')).display !== 'none', 'Trator permaneceu oculto');
  });
  await teste('Atendimentos filtra pelo cabeçalho comum do trator, sem filas soltas', async () => {
    assert(!document.querySelector('[data-chat-estado],.firma-chat-filtros,.chat-filtros'), 'Filtros soltos acima da tabela');
    const header = [...document.querySelectorAll('#chat-tabela th button')].find(botao => botao.textContent === 'ATENDIMENTOS');
    assert(header, 'Coluna Atendimentos ausente');
    header.click();
    const menu = document.querySelector('.coluna-menu'),
      labels = [...menu.querySelectorAll('.valor-lista label')];
    assert(labels.length === 4, 'Filas ausentes do filtro');
    const finalizado = labels.find(lista => lista.textContent.includes('Finalizados'));
    finalizado.querySelector('input').click();
    [...menu.querySelectorAll('button')].find(botao => botao.textContent === 'Aplicar filtro').click();
    await esperar();
    assert(document.querySelectorAll('#chat-tabela tbody tr[data-conversa-id]').length === 1 && document.querySelector('[data-conversa-id="2"]'), 'Filtro de atendimento não aplicado');
    assert(document.querySelectorAll('#chat-lista button').length === 1 && document.querySelector('#chat-lista').textContent.includes('João'), 'Lista lateral ignorou os filtros');
  });
  await teste('Alterar token limpa registros; token inválido mantém acesso bloqueado', async () => {
    const campo = document.getElementById('lista-token');
    campo.value = 'invalido';
    campo.dispatchEvent(new Event('input', {
      bubbles: true
    }));
    assert(!window.FirmaAcesso && !document.querySelector('[data-conversa-id]'), 'Conexão anterior permaneceu ativa');
    const consultas = window.chamadasFirmaChat.filter(endereco => endereco.includes('registros/atendimentos')).length;
    document.getElementById('lista-conectar').click();
    await esperar();
    await esperar();
    assert(!window.FirmaAcesso && !document.querySelector('[data-conversa-id]'), 'Token inválido carregou dados');
    assert(window.chamadasFirmaChat.filter(endereco => endereco.includes('registros/atendimentos')).length === consultas, 'Atendimentos consultados com token inválido');
  });
  await teste('Reconectar restaura registros sem duplicar o trator', async () => {
    document.getElementById('lista-token').value = 'teste';
    document.getElementById('lista-conectar').click();
    await esperar();
    await esperar();
    assert(window.FirmaAcesso && document.querySelectorAll('[data-conversa-id]').length === 4, 'Reconexão falhou');
    assert(document.querySelectorAll('#chat-tabela .lista-toolbar').length === 1 && document.querySelectorAll('.firma-chat-navegacao').length === 1, 'Reconexão duplicou o trator');
  });
  await teste('Página executa sem erros JavaScript', () => assert(!window.errosFirmaChat.length, window.errosFirmaChat.join('\n')));
  const out = document.createElement('pre');
  out.id = 'resultado-testes';
  out.textContent = JSON.stringify(resultados);
  document.body.append(out);
})();
