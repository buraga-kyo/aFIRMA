const {
  chromium
} = require('playwright');
const arquivosEmDisco = require('node:fs'),
  caminhos = require('node:path'),
  assert = require('node:assert/strict');
const base = process.env.FIRMA_TEST_BASE_URL || 'http://127.0.0.1:18089',
  out = process.env.FIRMA_TEST_ARTIFACTS || '/tmp/firma-relacionamento-browser';
arquivosEmDisco.mkdirSync(out, {
  recursive: true
});
(async () => {
  const navegador = await chromium.launch({
    executablePath: process.env.CHROME_BIN || '/usr/bin/google-chrome',
    headless: true,
    args: ['--no-sandbox']
  });
  try {
    const pagina = await navegador.newPage({
      viewport: {
        width: 1512,
        height: 1000
      }
    });
    pagina.setDefaultTimeout(25000);
    const erros = [];
    pagina.on('pageerror', evento => erros.push(evento.stack || evento.message));
    const email = 'relacionamento-' + Date.now() + '@example.test',
      senha = 'SenhaTeste2026!';
    let registro = await pagina.request.post(base + '/q9zePu74UYPUaDqUyay4fIjwNV', {
      data: {
        username: 'Horizonte',
        email,
        password: senha
      }
    });
    assert.ok(registro.ok(), registro.status() + " " + (await registro.text()));
    await pagina.goto(base + '/');
    assert.equal(await pagina.locator('.firma-sidebar').count(), 0);
    assert.equal(await pagina.locator('.modulos .modulo').count(), 3);
    assert.equal(await pagina.locator('body').evaluate(evento => getComputedStyle(evento).backgroundColor), 'rgb(14, 33, 23)');
    await pagina.screenshot({
      path: caminhos.join(out, 'inicio-firma.png'),
      fullPage: true
    });
    await pagina.locator('.modulo-talk').click();
    await pagina.waitForURL(base + '/chat');
    await pagina.goto(base + '/talkative/trator');
    assert.ok(await pagina.locator('#trator-montagem').count());
    assert.equal(await pagina.locator('body').evaluate(evento => getComputedStyle(evento).backgroundColor), 'rgb(14, 33, 23)');
    assert.equal(await pagina.locator('iframe').count(), 0);
    const acesso = await (await pagina.request.post(base + '/api/firma/login', {
      data: {
        email,
        password: senha
      }
    })).json();
    await pagina.fill('#lista-token', acesso.token);
    await pagina.locator('#lista-conectar').click();
    await pagina.waitForFunction(() => document.body.classList.contains('firma-autenticado'));
    let token = await pagina.evaluate(() => localStorage.getItem('assineai-token'));
    const requisicao = pagina.request;
    async function api(item, metodo = 'GET', data, itemAtual = token) {
      const respostaAtual = await requisicao.fetch(base + '/api/firma/' + item, {
        method: metodo,
        headers: {
          Authorization: `Bearer ${itemAtual}`
        },
        ...(data ? {
          data
        } : {})
      });
      if (!respostaAtual.ok()) throw Error(metodo + ' ' + item + ': ' + respostaAtual.status() + ' ' + (await respostaAtual.text()));
      return respostaAtual.text().then(itemAtual => itemAtual ? JSON.parse(itemAtual) : {});
    }
    await pagina.goto(base + '/assineai/trator');
    assert.ok(await pagina.locator('#tabela-montagem').count());
    assert.equal(await pagina.locator('.firma-sidebar').count(), 0);
    await pagina.screenshot({
      path: caminhos.join(out, 'trator-firma.png'),
      fullPage: true
    });
    for (const rota of ['/empresa', '/advai', '/assinatura', '/preparar-assinaturas']) {
      await pagina.goto(base + rota);
      assert.equal(await pagina.locator('.firma-sidebar').count(), 0);
      assert.equal(await pagina.locator('body').evaluate(evento => getComputedStyle(evento).backgroundColor), 'rgb(14, 33, 23)');
    }
    async function conectarPagina() {
      if (await pagina.locator('#lista-token').count()) {
        await pagina.fill('#lista-token', token);
        await pagina.click('#lista-conectar');
        await pagina.waitForFunction(() => window.FirmaContexto);
      }
    }
    const contexto = await api('contexto');
    assert.ok(contexto.modulos.find(modulo => modulo.slug === 'setores').campos.find(item => item.nome === 'CanalId').referencia === 'Canal');
    const canal = await api('registros/canais', 'POST', {
      CanalNome: 'Atendimento principal',
      CanalNumeroCelular: '5511999911100',
      CanalStatusUso: true,
      CanalProvedorAPI: 'Baileys'
    });
    const setor = await api('registros/setores', 'POST', {
      SetorNome: 'Relacionamento',
      SetorStatus: true,
      CanalId: canal.id
    });
    const contato = await api('registros/contatos', 'POST', {
      ContatoNome: 'Marina Costa',
      ContatoNumero: '5511999922200',
      ContatoEmail: 'marina@example.test'
    });
    const contato2 = await api('registros/contatos', 'POST', {
      ContatoNome: 'João Almeida',
      ContatoNumero: '5511999922201',
      ContatoEmail: 'joao@example.test'
    });
    const perfis = (await api('registros/perfis')).itens;
    assert.equal(perfis.length, 3);
    const usuario = await api('registros/usuarios', 'POST', {
      UsuarioPrimeiroNome: 'Ana',
      UsuarioUltimoNome: 'Silva',
      UsuarioEmail: 'agente-' + Date.now() + '@example.test',
      UsuarioSenha: senha,
      UsuarioBloqueado: false,
      PerfilId: perfis.find(paragrafo => paragrafo.PerfilNome === 'Atendente').id
    });
    assert.ok(!('UsuarioSenha' in usuario));
    const atendente = await api('registros/atendentes', 'POST', {
      AtendenteNome: 'Ana Silva',
      AtendenteStatus: true,
      AtendenteStatusChat: 'DISPONIVEL',
      AtendenteUsuarioId: usuario.id
    });
    await api('registros/atendentes-setores', 'POST', {
      AtendenteId: atendente.id,
      SetorId: setor.id
    });
    const conversa = await api('conversas', 'POST', {
      CanalId: canal.id,
      ContatoId: contato.id,
      SetorId: setor.id
    });
    await api('conversas/' + conversa.id + '/acao', 'POST', {
      acao: 'assumir',
      AtendenteId: atendente.id
    });
    if (process.env.FIRMA_TEST_POSTGRES_CONTAINER) {
      const sql = `INSERT INTO public.chat_mensagem(atendimento_id,canal_id,empresa_id,identificador_externo,sentido,estado,texto) SELECT v.origem_id::uuid,ca.origem_id::uuid,cl.origem_id::bigint,'entrada-teste-'||gen_random_uuid()::text,'ENTRADA','RECEBIDA','Olá! Gostaria de confirmar os documentos para assinatura.' FROM firma_relacionamento_vinculo v JOIN firma."Atendimento" a ON a.id=v.destino_id JOIN firma_relacionamento_vinculo ca ON ca.destino='Canal' AND ca.destino_id=a."CanalId" JOIN firma_relacionamento_vinculo cl ON cl.destino='Cliente' AND cl.destino_id=a."ClienteId" WHERE v.destino='Atendimento' AND v.destino_id=${Number(conversa.id)};`;
      require('node:child_process').execFileSync('docker', ['exec', '-i', process.env.FIRMA_TEST_POSTGRES_CONTAINER, 'psql', '-X', '-v', 'ON_ERROR_STOP=1', '-U', process.env.FIRMA_TEST_POSTGRES_USER || 'firma_relacionamento', '-d', process.env.FIRMA_TEST_POSTGRES_DB || 'firma_relacionamento'], {
        input: sql,
        stdio: ['pipe', 'pipe', 'pipe']
      });
    }
    await pagina.goto(base + '/sistema?modulo=contatos');
    await conectarPagina();
    await pagina.waitForSelector('#relacionamento-tabela tbody tr');
    assert.ok((await pagina.locator('#relacionamento-tabela').innerText()).includes('Marina Costa'));
    await pagina.screenshot({
      path: caminhos.join(out, 'contatos.png'),
      fullPage: true
    });
    await pagina.locator('#relacionamento-novo').click();
    await pagina.fill('[name=ContatoNome]', 'Contato pelo formulário');
    await pagina.fill('[name=ContatoNumero]', '5511999922202');
    await pagina.locator('#relacionamento-form [type=submit]').click();
    await pagina.waitForSelector('#relacionamento-editar', {
      state: 'hidden'
    });
    assert.ok((await api('registros/contatos')).itens.some(item => item.ContatoNome === 'Contato pelo formulário'));
    await pagina.goto(base + '/chat');
    await conectarPagina();
    await pagina.locator('[data-conversa-id="' + conversa.id + '"] [data-conversa]').dblclick();
    await pagina.waitForFunction(() => document.querySelector('#chat-nome').textContent === 'Marina Costa' && document.querySelector('#chat-setor').value !== '');
    await pagina.screenshot({
      path: caminhos.join(out, 'conversas.png'),
      fullPage: true
    });
    assert.equal(await pagina.locator('iframe').count(), 0);
    if (process.env.FIRMA_TEST_POSTGRES_CONTAINER) assert.ok((await pagina.locator('#chat-mensagens').innerText()).includes('Gostaria de confirmar'));
    // O envio falha de forma explícita quando não há transporte configurado; não inventa confirmação.
    const envio = await requisicao.post(base + `/api/firma/conversas/${conversa.id}/mensagens`, {
      headers: {
        Authorization: `Bearer ${token}`
      },
      data: {
        texto: 'Mensagem local de teste'
      }
    });
    assert.ok((process.env.FIRMA_TEST_BRIDGE_MODE === 'simulado' ? [200] : [503, 502]).includes(envio.status()), await envio.text());
    const empresa2 = 'outra-' + Date.now() + '@example.test';
    await requisicao.post(base + '/q9zePu74UYPUaDqUyay4fIjwNV', {
      data: {
        username: 'Outra',
        email: empresa2,
        password: senha
      }
    });
    const login2 = await (await requisicao.post(base + '/api/firma/login', {
      data: {
        email: empresa2,
        password: senha
      }
    })).json();
    const isolado = await api('registros/contatos', 'GET', undefined, login2.token);
    assert.equal(isolado.total, 0);
    const intrusao = await requisicao.post(base + '/api/firma/registros/setores', {
      headers: {
        Authorization: `Bearer ${login2.token}`
      },
      data: {
        SetorNome: 'Indevido',
        CanalId: canal.id
      }
    });
    assert.equal(intrusao.status(), 404);
    const tentativa = await requisicao.post(base + '/api/firma/registros/contatos', {
      headers: {
        Authorization: `Bearer ${token}`
      },
      data: {
        ContatoNome: 'Fora do escopo',
        ClienteId: 123456
      }
    });
    assert.equal(tentativa.status(), 400);

    // Regras da origem conectadas à operação, com chamadas externas desativadas.
    assert.ok(!contexto.modulos.some(modulo => modulo.slug === 'planos'));
    const duplicado = await requisicao.post(base + '/api/firma/registros/contatos', {
      headers: {
        Authorization: `Bearer ${token}`
      },
      data: {
        ContatoNome: 'Duplicado',
        ContatoNumero: '(11) 99992-2200'
      }
    });
    assert.equal(duplicado.status(), 400);
    const vinculoDuplicado = await requisicao.post(base + '/api/firma/registros/atendentes-setores', {
      headers: {
        Authorization: `Bearer ${token}`
      },
      data: {
        AtendenteId: atendente.id,
        SetorId: setor.id
      }
    });
    assert.equal(vinculoDuplicado.status(), 400);
    const mesma = await api('conversas', 'POST', {
      CanalId: canal.id,
      ContatoId: contato.id,
      SetorId: setor.id
    });
    assert.equal(mesma.id, conversa.id);
    const tag = await api('registros/tags', 'POST', {
      TagNome: 'Prioritário ' + Date.now(),
      TagStatus: true,
      SetorId: setor.id
    });
    await api('conversas/' + conversa.id + '/tags', 'PUT', [tag.id, tag.id]);
    assert.equal((await api('conversas/' + conversa.id)).tags.length, 1);
    const lista = await api('registros/listas', 'POST', {
      ListaContatoNome: 'Clientes ' + Date.now(),
      ListaContatoStatus: true,
      ListaContatoContatos: [{
        telefone: '(11) 99992-2200',
        nome: 'Marina'
      }, {
        telefone: '5511999922200',
        nome: 'Duplicado'
      }, {
        telefone: 'inválido',
        nome: 'Sem telefone'
      }]
    });
    const disparo = await api('registros/disparos', 'POST', {
      CanalId: canal.id,
      SetorId: setor.id,
      ListaContatoId: lista.id,
      DisparoMassaConteudo: 'Olá, {{nome}}. {{Saudação}}!',
      DisparoMassaColunaDestinatario: 'telefone',
      DisparoMassaDataHoraInicio: new Date(Date.now() + 86400000).toISOString()
    });
    assert.equal(disparo.DisparoMassaQuantidadeMensagens, 1);
    assert.equal(disparo.DisparoMassaMensagens[0].Mensagem, 'Olá, Marina. {{Saudação}}!');
    const cancelado = await api('disparos/' + disparo.id + '/cancelar', 'POST');
    assert.equal(cancelado.DisparoMassaStatus, 'Cancelado');
    assert.equal(cancelado.DisparoMassaQuantidadeCancelado, 1);
    const horarioInvalido = await requisicao.post(base + '/api/firma/registros/disparos', {
      headers: {
        Authorization: `Bearer ${token}`
      },
      data: {
        CanalId: canal.id,
        ListaContatoId: lista.id,
        DisparoMassaConteudo: 'Teste',
        DisparoMassaHoraReinicio: '18:00',
        DisparoMassaUltimoHorario: '08:00'
      }
    });
    assert.equal(horarioInvalido.status(), 400);
    const modelo = await api('registros/templates', 'POST', {
      MensagemTemplateNome: 'aviso_' + Date.now(),
      MensagemTemplateCategoria: 'UTILITY',
      MensagemTemplateCorpo: 'Seu contrato está pronto.',
      CanalId: canal.id,
      SetorId: setor.id
    });
    assert.equal(modelo.MensagemTemplateStatusMeta, 'RASCUNHO');
    const ia = await api('registros/ia', 'POST', {
      InteligenciaArtificialNome: 'Assistente',
      InteligenciaArtificialStatus: true,
      InteligenciaArtificialProvedor: 'ChatGPT_CompletionsAPI',
      InteligenciaArtificialModelo: 'modelo-de-teste',
      InteligenciaArtificialTextoPrompt: 'Ajude com os contratos.',
      InteligenciaArtificialProvedorAPIKey: 'APP_IA_TOKEN_TESTE',
      InteligenciaArtificialAtivador: 'manual-chat',
      CanalId: canal.id,
      SetorId: setor.id
    });
    assert.ok(!('InteligenciaArtificialProvedorAPIKey' in ia));
    const iaDuplicada = await requisicao.post(base + '/api/firma/registros/ia', {
      headers: {
        Authorization: `Bearer ${token}`
      },
      data: {
        InteligenciaArtificialNome: 'Outra',
        InteligenciaArtificialStatus: true,
        InteligenciaArtificialProvedor: 'DeepSeek',
        InteligenciaArtificialModelo: 'modelo',
        InteligenciaArtificialTextoPrompt: 'Teste',
        InteligenciaArtificialAtivador: 'manual-chat',
        CanalId: canal.id,
        SetorId: setor.id
      }
    });
    assert.equal(iaDuplicada.status(), 400);
    const fluxo = {
      nodes: [{
        id: 'start',
        type: 'start',
        data: null
      }, {
        id: 'pergunta',
        type: 'tarefa',
        data: {
          pergunta: 'Qual seu nome?',
          variavel: 'nome',
          resposta: 'Obrigado, &NOME!'
        }
      }, {
        id: 'end',
        type: 'end',
        data: null
      }],
      edges: [{
        source: 'start',
        target: 'pergunta'
      }, {
        source: 'pergunta',
        target: 'end'
      }]
    };
    const bot = await api('registros/bots', 'POST', {
      ChatbotDefinicaoNome: 'Boas-vindas',
      ChatbotDefinicaoStatus: false,
      ChatbotDefinicaoProvedor: 'firma',
      ChatbotDefinicaoFuncionamento: {
        Integral: true
      },
      ChatbotDefinicaoFlow: fluxo,
      CanalId: canal.id
    });
    assert.deepEqual(bot.ChatbotDefinicaoFlow, fluxo);
    assert.deepEqual(await api('bots/' + bot.id + '/sessoes'), []);
    const webhook = await api('registros/webhooks', 'POST', {
      WebhookURL: 'https://example.test/eventos',
      WebhookEvento: 'mensagem_recebida',
      WebhookTodosCanais: false,
      CanalId: canal.id
    });
    assert.deepEqual(await api('webhooks/' + webhook.id + '/entregas'), []);
    const chave = await api('registros/chaves', 'POST', {
      ChaveAPIDescricao: 'Integração de teste'
    });
    assert.match(chave.chaveGerada, /^[a-f0-9-]{36}$/i);
    assert.ok(!(await api('registros/chaves')).itens.some(chaveAtual => 'ChaveAPIValor' in chaveAtual));
    const apiCanais = await requisicao.get(base + '/api/firma/v1/canais', {
      headers: {
        Authorization: 'Bearer ' + chave.chaveGerada
      }
    });
    assert.ok(apiCanais.ok());
    assert.ok((await apiCanais.json()).itens.some(item => item.id === canal.id));
    const pedidoApi = {
      CanalId: canal.id,
      Destinatario: '5511999922200',
      texto: 'Envio local sem transporte'
    };
    const cabecalhoApi = {
      Authorization: 'Bearer ' + chave.chaveGerada,
      'Idempotency-Key': 'envio-teste-' + Date.now()
    };
    const envioApi = await requisicao.post(base + '/api/firma/v1/mensagens', {
      headers: cabecalhoApi,
      data: pedidoApi
    });
    assert.ok(envioApi.ok(), await envioApi.text());
    const envioRegistrado = await envioApi.json();
    assert.ok((process.env.FIRMA_TEST_BRIDGE_MODE === 'simulado' ? ['ENVIADO'] : ['FALHOU', 'INCERTO']).includes(envioRegistrado.estado));
    const repetido = await (await requisicao.post(base + '/api/firma/v1/mensagens', {
      headers: cabecalhoApi,
      data: pedidoApi
    })).json();
    assert.equal(repetido.id, envioRegistrado.id);
    await api('registros/chaves/' + chave.id, 'DELETE');
    assert.equal((await requisicao.get(base + '/api/firma/v1/canais', {
      headers: {
        Authorization: 'Bearer ' + chave.chaveGerada
      }
    })).status(), 401);
    const arquivo = await requisicao.post(base + '/api/firma/arquivos', {
      headers: {
        Authorization: `Bearer ${token}`
      },
      multipart: {
        arquivo: {
          name: 'teste.txt',
          mimeType: 'text/plain',
          buffer: Buffer.from('Arquivo da FIRMA')
        }
      }
    });
    assert.ok(arquivo.ok(), await arquivo.text());
    const botaoArquivo = await arquivo.json();
    const baixado = await requisicao.get(base + '/api/firma/arquivos/' + botaoArquivo.id, {
      headers: {
        Authorization: `Bearer ${token}`
      }
    });
    assert.equal(await baixado.text(), 'Arquivo da FIRMA');
    assert.equal((await requisicao.get(base + '/api/firma/arquivos/' + botaoArquivo.id, {
      headers: {
        Authorization: `Bearer ${login2.token}`
      }
    })).status(), 404);
    const indicadores = await api('indicadores');
    assert.ok(indicadores.cards.total >= 1);
    const indicadores2 = await api('indicadores', 'GET', undefined, login2.token);
    assert.equal(indicadores2.cards.total, 0);
    await pagina.goto(base + '/sistema?modulo=indicadores');
    await conectarPagina();
    await pagina.waitForSelector('#indicadores-cards article');
    await pagina.screenshot({
      path: caminhos.join(out, 'indicadores.png'),
      fullPage: true
    });
    await pagina.goto(base + '/sistema?modulo=disparos');
    await conectarPagina();
    await pagina.waitForSelector('#relacionamento-tabela tbody tr');
    assert.ok((await pagina.locator('#relacionamento-tabela').innerText()).includes('Cancelado'));
    await pagina.screenshot({
      path: caminhos.join(out, 'disparos.png'),
      fullPage: true
    });
    const loginAgente = await (await requisicao.post(base + '/api/firma/login', {
      data: {
        email: usuario.UsuarioEmail,
        password: senha
      }
    })).json();
    assert.ok(loginAgente.token);
    const perfilAgente = await api('contexto', 'GET', undefined, loginAgente.token);
    assert.ok(!perfilAgente.modulos.some(modulo => modulo.slug === 'perfis'));
    const bloqueado = await requisicao.post(base + '/api/firma/registros/perfis', {
      headers: {
        Authorization: `Bearer ${loginAgente.token}`
      },
      data: {
        PerfilNome: 'Administrador indevido'
      }
    });
    assert.equal(bloqueado.status(), 403);
    const perfilProtegido = await requisicao.put(base + '/api/assineai/empresa', {
      headers: {
        Authorization: `Bearer ${loginAgente.token}`
      },
      data: {
        EmpresaNome: 'Mudança indevida'
      }
    });
    assert.equal(perfilProtegido.status(), 403);
    const conversasAgente = await api('registros/atendimentos', 'GET', undefined, loginAgente.token);
    assert.ok(conversasAgente.itens.some(registroAtual => registroAtual.id === conversa.id));
    const sessoes = await api('registros/sessoes');
    assert.ok(sessoes.itens.length > 0);
    assert.ok(sessoes.itens.every(itemDoRegistro => !('SessaoJWT' in itemDoRegistro)));
    const logout = await requisicao.post(base + '/api/firma/logout', {
      headers: {
        Authorization: `Bearer ${loginAgente.token}`
      }
    });
    assert.ok(logout.ok());
    const revogado = await requisicao.get(base + '/api/firma/contexto', {
      headers: {
        Authorization: `Bearer ${loginAgente.token}`
      }
    });
    assert.equal(revogado.status(), 401);
    await api('conversas/' + conversa.id + '/acao', 'POST', {
      acao: 'finalizar'
    });
    const encerrado = await api('conversas/' + conversa.id);
    assert.equal(encerrado.AtendimentoStatus, 'finalizado');
    assert.ok(encerrado.AtendimentoDuracao >= 0);
    assert.ok(encerrado.acoes.some(registroAtual => registroAtual.AtendimentoAcaoTipo === 'Finalizar'));
    await api('conversas/' + conversa.id + '/acao', 'POST', {
      acao: 'reativar'
    });
    assert.equal((await api('conversas/' + conversa.id)).AtendimentoStatus, 'pendente');
    await pagina.goto(base + '/');
    await pagina.locator('.modulo-adv').click();
    await pagina.waitForURL(base + '/advai');
    await pagina.goto(base + '/');
    await pagina.locator('.modulo-assine').click();
    await pagina.waitForURL(base + '/assineai/trator');
    await pagina.setViewportSize({
      width: 390,
      height: 844
    });
    await pagina.goto(base + '/');
    await pagina.screenshot({
      path: caminhos.join(out, 'inicio-celular.png'),
      fullPage: true
    });
    await pagina.locator('.modulo-talk').click();
    await pagina.waitForURL(base + '/chat');
    await pagina.goto(base + '/talkative/trator');
    await pagina.waitForSelector('.trator-faixa-abas');
    await pagina.screenshot({
      path: caminhos.join(out, 'menu-celular.png'),
      fullPage: true
    });
    assert.deepEqual(erros, []);
    console.log('OK: Home FIRMA com módulos, identidade original, TalkAtive isolado, navegação nativa, cadastros, FKs, conversa, isolamento, perfis, login de usuário e revogação de sessão.');
  } finally {
    await navegador.close();
  }
})().catch(evento => {
  console.error(evento);
  process.exit(1);
});
