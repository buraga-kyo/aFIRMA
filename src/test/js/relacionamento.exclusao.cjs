// Executar em uma instância isolada; cria apenas contas e cadastros sintéticos.
const assert = require('node:assert/strict'),
  arquivosEmDisco = require('node:fs');
const base = process.env.FIRMA_TEST_BASE_URL || 'http://127.0.0.1:18089';
(async () => {
  const marca = Date.now(),
    senha = 'SenhaExclusao2026!';
  async function chamar(item, metodo = 'GET', dados, token) {
    const resposta = await fetch(base + item, {
      method: metodo,
      headers: {
        ...(dados ? {
          'Content-Type': 'application/json'
        } : {}),
        ...(token ? {
          Authorization: 'Bearer ' + token
        } : {})
      },
      body: dados ? JSON.stringify(dados) : undefined
    });
    const itemAtual = await resposta.text();
    assert.ok(resposta.ok, resposta.status + ' ' + item + ' ' + itemAtual);
    return itemAtual ? JSON.parse(itemAtual) : {};
  }
  const email = 'exclusao-' + marca + '@example.test';
  await chamar('/q9zePu74UYPUaDqUyay4fIjwNV', 'POST', {
    username: 'Teste de exclusão',
    email,
    password: senha
  });
  const token = (await chamar('/api/firma/login', 'POST', {
    email,
    password: senha
  })).token;
  const api = (paragrafo, itemDoRegistro = 'GET', dadosAtual) => chamar('/api/firma/' + paragrafo, itemDoRegistro, dadosAtual, token);
  await api('contexto');
  const canal = await api('registros/canais', 'POST', {
    CanalNome: 'Canal para excluir',
    CanalNumeroCelular: '5511999900001',
    CanalProvedorAPI: 'Baileys',
    CanalStatusUso: true
  });
  const inativo = await api('registros/canais', 'POST', {
    CanalNome: 'Canal somente inativo',
    CanalNumeroCelular: '5511999900002',
    CanalProvedorAPI: 'Baileys',
    CanalStatusUso: false
  });
  const setor = await api('registros/setores', 'POST', {
    SetorNome: 'Suporte',
    SetorStatus: true,
    CanalId: canal.id
  });
  const contato = await api('registros/contatos', 'POST', {
    ContatoNome: 'Contato histórico',
    ContatoNumero: '5511999900003'
  });
  const conversa = await api('conversas', 'POST', {
    CanalId: canal.id,
    ContatoId: contato.id,
    SetorId: setor.id
  });
  const {
    chromium
  } = require('playwright');
  const botao = await chromium.launch({
    executablePath: '/usr/bin/google-chrome',
    headless: true,
    args: ['--no-sandbox']
  });
  try {
    const paragrafo = await botao.newPage({
      viewport: {
        width: 1512,
        height: 1000
      }
    });
    arquivosEmDisco.mkdirSync('artifacts/firma-footer-assinatura', {
      recursive: true
    });
    const erros = [];
    paragrafo.on('pageerror', evento => erros.push(evento.message));
    await paragrafo.goto(base);
    await paragrafo.evaluate(itemAtual => localStorage.setItem('assineai-token', itemAtual), token);
    await paragrafo.goto(base + '/talkative/trator');
    await paragrafo.waitForSelector('#relacionamento-tabela tbody tr');
    const linha = paragrafo.locator('#relacionamento-tabela tbody tr').filter({
      hasText: 'Canal para excluir'
    });
    await linha.getByRole('button', {
      name: 'Excluir Canal para excluir',
      exact: true
    }).click();
    await paragrafo.screenshot({
      path: 'artifacts/firma-footer-assinatura/confirmar-exclusao.png'
    });
    await paragrafo.keyboard.press('Escape');
    assert.equal(await linha.count(), 1);
    await linha.getByRole('button', {
      name: 'Excluir Canal para excluir',
      exact: true
    }).click();
    await paragrafo.getByRole('dialog', {
      name: 'Confirmar exclusão',
      exact: true
    }).getByRole('button', {
      name: 'Cancelar',
      exact: true
    }).click();
    assert.equal(await linha.count(), 1);
    await linha.getByRole('button', {
      name: 'Excluir Canal para excluir',
      exact: true
    }).click();
    await paragrafo.getByRole('dialog', {
      name: 'Confirmar exclusão',
      exact: true
    }).getByRole('button', {
      name: 'Sim, excluir',
      exact: true
    }).click();
    await paragrafo.getByText('Registro excluído.', {
      exact: true
    }).waitFor();
    assert.equal(await linha.count(), 0);
    await paragrafo.reload();
    await paragrafo.waitForSelector('#relacionamento-tabela tbody tr');
    assert.equal(await paragrafo.locator('#relacionamento-tabela tbody tr').filter({
      hasText: 'Canal para excluir'
    }).count(), 0);
    assert.equal(await paragrafo.locator('#relacionamento-tabela tbody tr').filter({
      hasText: 'Canal somente inativo'
    }).count(), 1);
    assert.ok(await paragrafo.locator('.rodape-fixo-trator #lista-conectar').isVisible());
    assert.equal(await paragrafo.locator('#lista-token').getAttribute('type'), 'password');
    assert.equal(await paragrafo.locator('#firma-email,#firma-senha,#trator-conta').count(), 0);
    await paragrafo.screenshot({
      path: 'artifacts/firma-footer-assinatura/rodape-desktop.png'
    });
    await paragrafo.locator('#lista-token').fill('token-invalido');
    await paragrafo.locator('#lista-conectar').click();
    await paragrafo.getByText('Token inválido ou expirado. Informe um JWT válido.', {
      exact: true
    }).waitFor();
    assert.equal(await paragrafo.evaluate(() => localStorage.getItem('assineai-token')), token);
    await paragrafo.locator('#lista-token').fill(token);
    await paragrafo.setViewportSize({
      width: 390,
      height: 844
    });
    await paragrafo.screenshot({
      path: 'artifacts/firma-footer-assinatura/rodape-celular.png',
      fullPage: true
    });
    assert.equal(await paragrafo.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
    assert.deepEqual(erros, []);
  } finally {
    await botao.close();
  }
  const lista = await api('registros/canais');
  assert.equal(lista.total, 1);
  assert.equal(lista.itens[0].id, inativo.id);
  assert.equal((await api('registros/canais/' + canal.id)).CanalStatusUso, false);
  assert.equal((await api('conversas/' + conversa.id)).id, conversa.id);
  assert.equal((await api('registros/canais/' + canal.id, 'DELETE')).excluido, true);
  const novo = await api('registros/canais', 'POST', {
    CanalNome: 'Canal recriado',
    CanalNumeroCelular: '(11) 99990-0001',
    CanalProvedorAPI: 'Baileys',
    CanalStatusUso: true
  });
  assert.notEqual(novo.id, canal.id);
  assert.notEqual(novo.CanalGUID, canal.CanalGUID);
  assert.equal(novo.CanalNumeroCelular, '5511999900001');
  assert.equal((await api('conversas/' + conversa.id)).CanalId, canal.id);
  const duplicado = await fetch(base + '/api/firma/registros/canais', {
    method: 'POST',
    headers: {
      Authorization: 'Bearer ' + token,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      CanalNome: 'Duplicado',
      CanalNumeroCelular: '5511999900001',
      CanalProvedorAPI: 'Baileys'
    })
  });
  assert.equal(duplicado.status, 400);
  const reativacao = await fetch(base + '/api/firma/registros/canais/' + canal.id, {
    method: 'PUT',
    headers: {
      Authorization: 'Bearer ' + token,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      CanalStatusUso: true
    })
  });
  assert.equal(reativacao.status, 410);
  const email2 = 'exclusao-outra-' + marca + '@example.test';
  await chamar('/q9zePu74UYPUaDqUyay4fIjwNV', 'POST', {
    username: 'Outra empresa',
    email: email2,
    password: senha
  });
  const outro = (await chamar('/api/firma/login', 'POST', {
    email: email2,
    password: senha
  })).token;
  const negado = await fetch(base + '/api/firma/registros/canais/' + canal.id, {
    method: 'DELETE',
    headers: {
      Authorization: 'Bearer ' + outro
    }
  });
  assert.equal(negado.status, 404);
  const tag = await api('registros/tags', 'POST', {
    TagNome: 'Excluir etiqueta',
    TagStatus: true,
    SetorId: setor.id
  });
  assert.equal((await api('registros/tags/' + tag.id, 'DELETE')).excluido, true);
  assert.equal((await api('registros/tags')).total, 0);
  const fisico = await api('registros/contatos', 'POST', {
    ContatoNome: 'Sem vínculos',
    ContatoNumero: '5511999900004'
  });
  assert.equal((await api('registros/contatos/' + fisico.id, 'DELETE')).excluido, true);
  assert.ok(!(await api('registros/contatos')).itens.some(item => item.id === fisico.id));
  console.log('OK: modal, cancelamento/Escape, exclusão persistente, número reutilizado com novo canal, histórico preservado, duplicidade recusada, rodapé de segurança, layout móvel e isolamento de empresa.');
})().catch(evento => {
  console.error(evento);
  process.exitCode = 1;
});
