// Banco e transporte devem ser isolados; não conecta contas reais de WhatsApp.
const assert = require('node:assert/strict'),
  criptografia = require('node:crypto'),
  arquivosEmDisco = require('node:fs'),
  caminhos = require('node:path');
const base = process.env.FIRMA_TEST_BASE_URL || 'http://127.0.0.1:18089',
  chaveMensagem = process.env.FIRMA_TEST_BRIDGE_KEY || 'firma-ponte-local-de-testes-2026-chave';
async function pedido(paragrafo, metodo = 'GET', data, token) {
  const resposta = await fetch(base + paragrafo, {
    method: metodo,
    headers: {
      ...(token ? {
        Authorization: 'Bearer ' + token
      } : {}),
      ...(data ? {
        'Content-Type': 'application/json'
      } : {})
    },
    body: data ? JSON.stringify(data) : undefined
  });
  const texto = await resposta.text();
  assert.ok(resposta.ok, resposta.status + ' ' + paragrafo + ' ' + texto);
  return texto ? JSON.parse(texto) : {};
}
(async () => {
  const marca = Date.now(),
    email = 'historico-' + marca + '@example.test',
    password = 'TesteHistorico2026!';
  await pedido('/q9zePu74UYPUaDqUyay4fIjwNV', 'POST', {
    username: 'Empresa de histórico',
    email,
    password
  });
  const token = (await pedido('/api/firma/login', 'POST', {
    email,
    password
  })).token;
  const api = (paragrafo, item = 'GET', dados) => pedido('/api/firma/' + paragrafo, item, dados, token);
  const contexto = await api('contexto');
  const canal = await api('registros/canais', 'POST', {
    CanalNome: 'WhatsApp histórico',
    CanalNumeroCelular: '5511999977000',
    CanalStatusUso: true,
    CanalProvedorAPI: 'Baileys'
  });
  await api('registros/setores', 'POST', {
    SetorNome: 'Relacionamento',
    SetorStatus: true,
    CanalId: canal.id
  });
  await api('registros/ia', 'POST', {
    InteligenciaArtificialNome: 'IA que não deve responder ao histórico',
    InteligenciaArtificialStatus: true,
    InteligenciaArtificialProvedor: 'ChatGPT_CompletionsAPI',
    InteligenciaArtificialModelo: 'teste',
    InteligenciaArtificialTextoPrompt: 'Teste',
    InteligenciaArtificialProvedorAPIKey: 'APP_IA_TOKEN_TESTE',
    InteligenciaArtificialAtivador: 'todas-mensagens',
    CanalId: canal.id
  });
  const webhook = await api('registros/webhooks', 'POST', {
    WebhookURL: 'https://example.test/eventos',
    WebhookEvento: 'mensagem_recebida',
    WebhookTodosCanais: false,
    CanalId: canal.id
  });
  const lote = {
    lote: criptografia.randomUUID(),
    conversas: [{
      telefone: '5511999977001',
      nome: 'Marina do histórico'
    }],
    mensagens: Array.from({
      length: 50
    }, (_, indice) => ({
      id: 'historico-' + marca + '-' + indice,
      telefone: '5511999977001',
      nome: 'Marina',
      texto: 'Mensagem antiga ' + indice,
      timestamp: 1700000000 + indice,
      fromMe: indice % 2 === 0,
      tipo: 'Texto'
    }))
  };
  async function importar(raw) {
    const resposta = await fetch(base + '/api/chat/interno/eventos', {
      method: 'POST',
      headers: {
        'X-Chat-Bridge-Key': chaveMensagem,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        tipo: 'historico',
        canalId: canal.CanalGUID,
        raw
      })
    });
    assert.ok(resposta.ok, await resposta.text());
  }
  await Promise.all([importar(lote), importar(lote)]);
  await importar({
    ...lote,
    lote: criptografia.randomUUID()
  });
  for (let parte = 1; parte < 3; parte++) await importar({
    lote: criptografia.randomUUID(),
    conversas: [],
    mensagens: Array.from({
      length: parte === 1 ? 50 : 5
    }, (_, indice) => ({
      id: 'historico-' + marca + '-' + (parte * 50 + indice),
      telefone: '5511999977001',
      texto: 'Mensagem antiga ' + (parte * 50 + indice),
      timestamp: 1700000000 + parte * 50 + indice,
      fromMe: false,
      tipo: 'Texto'
    }))
  });
  const cabecalho = await api('canais/' + canal.id + '/historico-status', 'POST');
  assert.equal(cabecalho.conversas, 1);
  assert.equal(cabecalho.mensagens, 105);
  assert.equal(cabecalho.duplicadas, 50);
  const lista = (await api('registros/atendimentos')).itens;
  assert.equal(lista.length, 1);
  const registro = await api('conversas/' + lista[0].id);
  assert.equal(registro.contato.ContatoNome, 'Marina do histórico');
  assert.equal(registro.AtendimentoQtdMensagensNaoLidas, 0);
  assert.equal(registro.mensagens.length, 100);
  assert.equal(registro.mensagens[0].MensagemTexto, 'Mensagem antiga 104');
  assert.equal(registro.MensagemId, registro.mensagens[0].id);
  const antigas = await api('conversas/' + registro.id + '/mensagens?antes=' + registro.mensagens.at(-1).id);
  assert.equal(antigas.length, 5);
  assert.equal(antigas.at(-1).MensagemTexto, 'Mensagem antiga 0');
  assert.equal(Date.parse(antigas.at(-1).MensagemDataHoraWhatsApp), 1700000000000);
  // Lote fora de ordem não substitui a mensagem mais recente nem incrementa não lidas.
  await importar({
    lote: criptografia.randomUUID(),
    conversas: [],
    mensagens: [{
      id: 'antiquissima-' + marca,
      telefone: '5511999977001',
      texto: 'Muito anterior',
      timestamp: 1600000000,
      fromMe: false,
      tipo: 'Texto'
    }]
  });
  assert.equal((await api('conversas/' + registro.id)).MensagemId, registro.MensagemId);
  assert.deepEqual(await api('webhooks/' + webhook.id + '/entregas'), []);
  if (process.env.FIRMA_TEST_POSTGRES_CONTAINER) {
    const sql = `SELECT count(*) FROM firma_relacionamento_ia_execucao e JOIN firma."Mensagem" m ON m.id=e.mensagem_id WHERE m."CanalId"=${Number(canal.id)}; SELECT count(*) FROM chat_webhook_entrega WHERE empresa_id=(SELECT origem_id::bigint FROM firma_relacionamento_vinculo WHERE destino='Cliente' AND destino_id=${Number(contexto.clienteId)});`;
    const resultado = require('node:child_process').execFileSync('docker', ['exec', '-i', process.env.FIRMA_TEST_POSTGRES_CONTAINER, 'psql', '-X', '-At', '-v', 'ON_ERROR_STOP=1', '-U', 'firma_relacionamento', '-d', 'firma_relacionamento'], {
      input: sql,
      encoding: 'utf8'
    });
    assert.equal(resultado.trim(), '0\n0');
  }
  const email2 = 'outro-historico-' + marca + '@example.test';
  await pedido('/q9zePu74UYPUaDqUyay4fIjwNV', 'POST', {
    username: 'Outra empresa',
    email: email2,
    password
  });
  const outro = (await pedido('/api/firma/login', 'POST', {
    email: email2,
    password
  })).token;
  await pedido('/api/firma/contexto', 'GET', undefined, outro);
  const negado = await fetch(base + '/api/firma/canais/' + canal.id + '/historico-status', {
    method: 'POST',
    headers: {
      Authorization: 'Bearer ' + outro
    }
  });
  assert.equal(negado.status, 404);
  const semChave = await fetch(base + '/api/chat/interno/eventos', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      tipo: 'historico',
      canalId: canal.CanalGUID,
      raw: lote
    })
  });
  assert.equal(semChave.status, 401);
  // Filtros/ordenação são feitos no servidor sobre todos os registros, com escopo preservado.
  const filtro = await api('registros/contatos?' + new URLSearchParams({
    filtros: JSON.stringify({
      ContatoNome: 'Marina do histórico'
    }),
    ordem: JSON.stringify({
      id: 'ContatoNome',
      dir: 'asc'
    })
  }));
  assert.equal(filtro.total, 1);
  const out = process.env.FIRMA_TEST_ARTIFACTS || '/tmp/firma-trator-historico';
  arquivosEmDisco.mkdirSync(out, {
    recursive: true
  });
  const {
    chromium
  } = require('playwright');
  const navegador = await chromium.launch({
    executablePath: '/usr/bin/google-chrome',
    headless: true,
    args: ['--no-sandbox']
  });
  try {
    const pagina = await navegador.newPage({
        viewport: {
          width: 1512,
          height: 1000
        }
      }),
      erros = [];
    pagina.on('pageerror', evento => erros.push(evento.message));
    await pagina.goto(base);
    await pagina.evaluate(itemAtual => localStorage.setItem('assineai-token', itemAtual), token);
    await pagina.goto(base + '/talkative/trator');
    await pagina.fill('#lista-token', token);
    await pagina.click('#lista-conectar');
    await pagina.waitForFunction(() => window.FirmaContexto);
    await pagina.waitForSelector('#relacionamento-tabela tbody tr');
    assert.ok(await pagina.locator('[data-tabela-instancia=relacionamento]').count());
    await pagina.screenshot({
      path: caminhos.join(out, 'trator-canais.png')
    });
    await pagina.setViewportSize({
      width: 390,
      height: 844
    });
    await pagina.screenshot({
      path: caminhos.join(out, 'trator-canais-celular.png'),
      fullPage: true
    });
    assert.equal(await pagina.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
    await pagina.setViewportSize({
      width: 1512,
      height: 1000
    });
    await pagina.getByRole('button', {
      name: 'Conectar / QR',
      exact: true
    }).click();
    await pagina.getByText('Conectando ao WhatsApp… aguardando o QR code.', {
      exact: true
    }).last().waitFor();
    assert.ok(await pagina.locator('#canal-conexao-progresso').isVisible());
    await pagina.locator('#canal-conexao-qr').waitFor({
      state: 'visible'
    });
    await pagina.screenshot({
      path: caminhos.join(out, 'qr-feedback.png')
    });
    await pagina.getByText('WhatsApp conectado!', {
      exact: true
    }).last().waitFor();
    await pagina.getByText(/1 conversas · 106 mensagens/).last().waitFor();
    await pagina.locator('#canal-importar').click();
    await pagina.screenshot({
      path: caminhos.join(out, 'importacao.png')
    });
    await pagina.locator('#canal-conexao-fechar').click();
    await pagina.route('**/api/firma/canais/' + canal.id + '/conexao', registroAtual => registroAtual.fulfill({
      status: 502,
      contentType: 'application/json',
      body: JSON.stringify({
        mensagem: 'Ponte indisponível no teste'
      })
    }));
    await pagina.getByRole('button', {
      name: 'Conectar / QR',
      exact: true
    }).click();
    await pagina.getByText('Ponte indisponível no teste', {
      exact: true
    }).last().waitFor();
    assert.ok(await pagina.locator('#canal-tentar').isVisible());
    await pagina.unroute('**/api/firma/canais/' + canal.id + '/conexao');
    await pagina.locator('#canal-tentar').click();
    await pagina.getByText('WhatsApp conectado!', {
      exact: true
    }).last().waitFor();
    await pagina.locator('#canal-conexao-fechar').click();
    await pagina.getByRole('link', {
      name: 'ABRIR CHAT ↗',
      exact: true
    }).click();
    await pagina.fill('#lista-token', token);
    await pagina.click('#lista-conectar');
    await pagina.waitForFunction(() => window.FirmaContexto);
    await pagina.locator('[data-conversa-id="' + registro.id + '"] [data-conversa]').dblclick();
    await pagina.locator('#chat-mensagens-anteriores').click();
    await pagina.locator('.relacionamento-bolha').filter({
      hasText: 'Muito anterior'
    }).waitFor();
    assert.equal(await pagina.locator('.relacionamento-bolha').count(), 106);
    await pagina.screenshot({
      path: caminhos.join(out, 'historico-no-chat.png')
    });
    assert.deepEqual(erros, []);
  } finally {
    await navegador.close();
  }
  console.log('OK: importação idempotente e concorrente, 106 mensagens com datas originais, paginação, última mensagem preservada, nenhuma automação/webhook, isolamento de empresa, Trator, feedback do QR e histórico no chat.');
})().catch(evento => {
  console.error(evento);
  process.exitCode = 1;
});
