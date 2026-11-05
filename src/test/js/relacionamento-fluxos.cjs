// Executar somente contra aplicação isolada e ponte simulada.
const assert = require('node:assert/strict');
const {
  chromium
} = require('playwright');
const base = process.env.FIRMA_TEST_BASE_URL || 'http://127.0.0.1:18089';
async function chamar(caminho, metodo = 'GET', dados, token, status = 200) {
  const respostaAtual = await fetch(base + caminho, {
    signal: AbortSignal.timeout(15000),
    method: metodo,
    headers: {
      ...(token ? {
        Authorization: 'Bearer ' + token
      } : {}),
      ...(dados ? {
        'Content-Type': 'application/json'
      } : {})
    },
    ...(dados ? {
      body: JSON.stringify(dados)
    } : {})
  });
  const bruto = await respostaAtual.text();
  assert.equal(respostaAtual.status, status, `${metodo} ${caminho}: ${bruto}`);
  return bruto ? JSON.parse(bruto) : null;
}
(async () => {
  const marca = Date.now(),
    email = `fluxos-${marca}@example.test`,
    senha = 'FirmaTeste2026!';
  await chamar('/q9zePu74UYPUaDqUyay4fIjwNV', 'POST', {
    username: 'Firma teste',
    email,
    password: senha
  }, undefined, 201);
  const token = (await chamar('/api/firma/login', 'POST', {
    email,
    password: senha
  })).token;
  const api = (item, itemAtual = 'GET', dadosAtual, itemDoRegistro = 200) => chamar('/api/firma/' + item, itemAtual, dadosAtual, token, itemDoRegistro);
  await chamar('/api/firma/contexto', 'GET', undefined, undefined, 401);
  await api('contexto');
  const integracao = await api('registros/integracoes', 'POST', {
    IntegracaoNome: 'Teste local',
    IntegracaoDados: {
      token: 'segredo-sintetico'
    },
    IntegracaoStatus: true
  });
  assert.ok(!('IntegracaoDados' in integracao));
  assert.ok(!('IntegracaoDados' in (await api('registros/integracoes/' + integracao.id))));
  assert.ok((await api('registros/integracoes')).itens.every(indice => !('IntegracaoDados' in indice)));
  await api('registros/integracoes/' + integracao.id, 'PUT', {
    IntegracaoNome: 'Teste local atualizado'
  });
  const canal = await api('registros/canais', 'POST', {
    CanalNome: 'Canal dos fluxos',
    CanalNumeroCelular: '5511999966600',
    CanalProvedorAPI: 'Baileys',
    CanalStatusUso: true
  });
  const setor = await api('registros/setores', 'POST', {
    SetorNome: 'Comercial',
    CanalId: canal.id,
    SetorStatus: true
  });
  const contato = await api('registros/contatos', 'POST', {
    ContatoNome: 'Marina dos fluxos',
    ContatoNumero: '5511999966601'
  });
  const atendente = await api('registros/atendentes', 'POST', {
    AtendenteNome: 'Ana',
    AtendenteStatus: true
  });
  await api('registros/atendentes-setores', 'POST', {
    AtendenteId: atendente.id,
    SetorId: setor.id
  });
  const conversa = await api('conversas', 'POST', {
    CanalId: canal.id,
    ContatoId: contato.id,
    SetorId: setor.id,
    AtendenteId: atendente.id,
    texto: 'Primeira mensagem ' + marca
  });
  assert.equal(conversa.AtendimentoStatus, 'emandamento');
  assert.equal(conversa.AtendenteId, atendente.id);
  let estado = await api('conversas/' + conversa.id);
  assert.ok(estado.mensagens.some(mensagem => mensagem.MensagemTexto === 'Primeira mensagem ' + marca));
  await api('conversas/' + conversa.id + '/acao', 'POST', {
    acao: 'finalizar'
  });
  await api('conversas/' + conversa.id + '/acao', 'POST', {
    acao: 'reativar',
    AtendenteId: atendente.id,
    texto: 'Retomada ' + marca
  });
  estado = await api('conversas/' + conversa.id);
  assert.equal(estado.AtendimentoStatus, 'emandamento');
  assert.equal(estado.AtendenteId, atendente.id);
  assert.ok(estado.mensagens.some(mensagem => mensagem.MensagemTexto === 'Retomada ' + marca));
  const entrada = {
    tipo: 'mensagem',
    canalId: canal.CanalGUID,
    identificador: 'recebida-' + marca,
    telefone: contato.ContatoNumero,
    nome: 'Marina',
    texto: 'Mensagem para leitura'
  };
  const receber = await fetch(base + '/api/chat/interno/eventos', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Chat-Bridge-Key': 'firma-ponte-local-de-testes-2026-chave'
    },
    body: JSON.stringify(entrada)
  });
  assert.ok(receber.ok, await receber.text());
  await api('conversas/' + conversa.id + '/acao', 'POST', {
    acao: 'lidas'
  });
  estado = await api('conversas/' + conversa.id);
  assert.equal(estado.contato.ContatoNome, 'Marina dos fluxos');
  assert.equal(estado.AtendimentoQtdMensagensNaoLidas, 0);
  assert.equal(estado.mensagens.find(mensagem => mensagem.MensagemTexto === 'Mensagem para leitura').MensagemACK, 3);
  // A preferência do canal também alcança bots internos, sem esconder o histórico finalizado.
  const canalBot = await api('registros/canais', 'POST', {
    CanalNome: 'Canal de automação',
    CanalNumeroCelular: '5511999966610',
    CanalProvedorAPI: 'Baileys',
    CanalStatusUso: true,
    CanalVisualizaMenuBOTChat: false
  });
  const fluxo = {
    nodes: [{
      id: 'start',
      type: 'start',
      data: null
    }, {
      id: 'nome',
      type: 'tarefa',
      data: {
        pergunta: 'Qual seu nome?',
        variavel: 'NOME',
        resposta: 'Olá, &NOME!'
      }
    }, {
      id: 'end',
      type: 'end',
      data: null
    }],
    edges: [{
      source: 'start',
      target: 'nome'
    }, {
      source: 'nome',
      target: 'end'
    }]
  };
  await api('registros/bots', 'POST', {
    ChatbotDefinicaoNome: 'Recepção',
    CanalId: canalBot.id,
    ChatbotDefinicaoProvedor: 'firma',
    ChatbotDefinicaoStatus: true,
    ChatbotDefinicaoFuncionamento: {
      Integral: true
    },
    ChatbotDefinicaoFlow: fluxo
  });
  async function mensagemBot(sufixo, texto) {
    const respostaAtual = await fetch(base + '/api/chat/interno/eventos', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Chat-Bridge-Key': 'firma-ponte-local-de-testes-2026-chave'
      },
      body: JSON.stringify({
        tipo: 'mensagem',
        canalId: canalBot.CanalGUID,
        identificador: 'bot-' + marca + '-' + sufixo,
        telefone: '5511999966611',
        nome: 'Visitante',
        texto
      })
    });
    assert.ok(respostaAtual.ok, await respostaAtual.text());
  }
  await mensagemBot('inicio', 'Olá');
  assert.equal((await api('registros/atendimentos?busca=5511999966611')).total, 0);
  await api('registros/canais/' + canalBot.id, 'PUT', {
    CanalVisualizaMenuBOTChat: true
  });
  const botsVisiveis = await api('registros/atendimentos?estado=bot&busca=5511999966611');
  assert.equal(botsVisiveis.total, 1);
  assert.equal(botsVisiveis.itens[0].AtendimentoEmBot, true);
  await api('registros/canais/' + canalBot.id, 'PUT', {
    CanalVisualizaMenuBOTChat: false
  });
  await mensagemBot('resposta', 'Marina');
  assert.equal((await api('registros/atendimentos?busca=5511999966611')).total, 1);
  const outroEmail = `isolamento-${marca}@example.test`;
  await chamar('/q9zePu74UYPUaDqUyay4fIjwNV', 'POST', {
    username: 'Outra firma',
    email: outroEmail,
    password: senha
  }, undefined, 201);
  const outroToken = (await chamar('/api/firma/login', 'POST', {
    email: outroEmail,
    password: senha
  })).token;
  await chamar('/api/firma/contexto', 'GET', undefined, outroToken);
  await chamar('/api/firma/conversas/' + conversa.id, 'GET', undefined, outroToken, 404);
  await chamar('/api/firma/conversas/' + conversa.id + '/acao', 'POST', {
    acao: 'lidas'
  }, outroToken, 404);
  // Um envio inicial recusado não deixa atendimento aberto sem a mensagem solicitada.
  const contatoFalha = await api('registros/contatos', 'POST', {
    ContatoNome: 'Falha controlada',
    ContatoNumero: '5511999966602'
  });
  await api('conversas', 'POST', {
    CanalId: canal.id,
    ContatoId: contatoFalha.id,
    SetorId: setor.id,
    AtendenteId: atendente.id,
    texto: 'falhar uma vez ' + marca
  }, 502);
  assert.equal((await api('registros/atendimentos?busca=5511999966602')).total, 0);
  const navegador = await chromium.launch({
    executablePath: '/usr/bin/google-chrome',
    headless: true,
    args: ['--no-sandbox']
  });
  try {
    const pagina = await navegador.newPage({
        viewport: {
          width: 1440,
          height: 1000
        }
      }),
      erros = [];
    pagina.on('pageerror', evento => erros.push(evento.message));
    await pagina.goto(base + '/chat');
    await pagina.fill('#lista-token', token);
    await pagina.click('#lista-conectar');
    await pagina.waitForFunction(() => window.FirmaContexto);
    await pagina.locator('[data-conversa-id="' + conversa.id + '"] [data-conversa]').dblclick();
    await pagina.waitForFunction(() => document.getElementById('chat-nome').textContent === 'Marina dos fluxos');
    await pagina.click('#chat-nova');
    assert.equal(await pagina.locator('#chat-novo-atendente').count(), 1);
    assert.equal(await pagina.locator('#chat-novo-template').count(), 1);
    await pagina.click('#chat-nova-fechar');
    await pagina.setViewportSize({
      width: 390,
      height: 844
    });
    await pagina.screenshot({
      path: '/tmp/firma-fluxos-celular.png',
      fullPage: true
    });
    assert.deepEqual(erros, []);
  } finally {
    await navegador.close();
  }
  await api('logout', 'POST');
  await chamar('/api/firma/contexto', 'GET', undefined, token, 401);
  console.log('OK: início atribuído com envio, reabertura com envio, leitura no provedor, isolamento, autenticação, revogação e tela móvel.');
})().catch(evento => {
  console.error(evento);
  process.exit(1);
});
