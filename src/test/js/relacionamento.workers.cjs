const assert = require('node:assert/strict');
const base = process.env.FIRMA_TEST_BASE_URL || 'http://127.0.0.1:18089',
  ponte = process.env.FIRMA_TEST_BRIDGE_URL || 'http://127.0.0.1:18091',
  chaveMensagem = process.env.FIRMA_TEST_BRIDGE_KEY || 'firma-ponte-local-de-testes-2026-chave';
async function chamar(endereco, metodo = 'GET', data, token) {
  const respostaAtual = await fetch(endereco, {
    method: metodo,
    headers: {
      ...(token ? {
        Authorization: 'Bearer ' + token
      } : {}),
      ...(data ? {
        'Content-Type': 'application/json'
      } : {})
    },
    ...(data ? {
      body: JSON.stringify(data)
    } : {})
  });
  const texto = await respostaAtual.text();
  if (!respostaAtual.ok) throw Error(respostaAtual.status + ' ' + endereco + ' ' + texto);
  return texto ? JSON.parse(texto) : {};
}
async function aguardar(executarAcao, predicado) {
  for (let indice = 0; indice < 100; indice++) {
    const registro = await executarAcao();
    if (predicado(registro)) return registro;
    await new Promise(registro => setTimeout(registro, 200));
  }
  throw Error('Tempo excedido aguardando o executor');
}
(async () => {
  const marcador = Date.now().toString(),
    email = 'workers-' + marcador + '@example.test',
    senha = 'TesteWorkers2026!';
  await chamar(base + '/q9zePu74UYPUaDqUyay4fIjwNV', 'POST', {
    username: 'Empresa dos executores',
    email,
    password: senha
  });
  const token = (await chamar(base + '/api/firma/login', 'POST', {
    email,
    password: senha
  })).token;
  const api = (paragrafo, item = 'GET', dados) => chamar(base + '/api/firma/' + paragrafo, item, dados, token);
  await api('contexto');
  const canal = await api('registros/canais', 'POST', {
    CanalNome: 'Canal simulado',
    CanalProvedorAPI: 'Baileys',
    CanalStatusUso: true,
    CanalNumeroCelular: '5511999900011'
  });
  const setor = await api('registros/setores', 'POST', {
    SetorNome: 'Suporte',
    SetorStatus: true,
    CanalId: canal.id
  });
  const lista = await api('registros/listas', 'POST', {
    ListaContatoNome: 'Lista operacional',
    ListaContatoContatos: [{
      telefone: '5511999900012',
      nome: 'Ana'
    }, {
      telefone: '5511999900013',
      nome: 'Bruno'
    }, {
      telefone: '(11) 99990-0012',
      nome: 'Ana duplicada'
    }]
  });
  const campanha = await api('registros/disparos', 'POST', {
    CanalId: canal.id,
    SetorId: setor.id,
    ListaContatoId: lista.id,
    DisparoMassaConteudo: 'Campanha ' + marcador + ' {{nome}}',
    DisparoMassaColunaDestinatario: 'telefone',
    DisparoMassaMensagensPorDisparos: 1,
    DisparoMassaIntervaloEntreDisparos: 1,
    DisparoMassaDesejaGerarAtendimento: true
  });
  assert.equal(campanha.DisparoMassaQuantidadeMensagens, 2);
  const final = await aguardar(() => api('registros/disparos/' + campanha.id), dados => dados.DisparoMassaStatus === 'Concluído');
  assert.equal(final.DisparoMassaQuantidadeEnviado, 2);
  assert.equal(final.DisparoMassaQuantidadeFalha, 0);
  const enviados = await chamar(ponte + '/_envios');
  const mensagens = enviados.filter(item => item.texto?.startsWith('Campanha ' + marcador + ' '));
  assert.equal(mensagens.length, 2);
  assert.ok(mensagens[1].instante - mensagens[0].instante >= 900);
  const limite = await api('registros/disparos', 'POST', {
    CanalId: canal.id,
    ListaContatoId: lista.id,
    DisparoMassaConteudo: 'Limite operacional {{nome}}',
    DisparoMassaMaxMensagemDia: 1
  });
  await aguardar(() => api('registros/disparos/' + limite.id), dados => dados.DisparoMassaQuantidadeEnviado === 1);
  await new Promise(registro => setTimeout(registro, 1800));
  const pausado = await api('registros/disparos/' + limite.id);
  assert.equal(pausado.DisparoMassaQuantidadePendente, 1);
  assert.equal(pausado.DisparoMassaQuantidadeEnviado, 1);
  const cancelado = await api('disparos/' + limite.id + '/cancelar', 'POST');
  assert.equal(cancelado.DisparoMassaQuantidadeCancelado, 1);
  const unica = await api('registros/listas', 'POST', {
    ListaContatoNome: 'Uma pessoa',
    ListaContatoContatos: [{
      telefone: '5511999900014',
      nome: 'Carla'
    }]
  });
  const falha = await api('registros/disparos', 'POST', {
    CanalId: canal.id,
    ListaContatoId: unica.id,
    DisparoMassaConteudo: 'falhar uma vez ' + marcador
  });
  await aguardar(() => api('registros/disparos/' + falha.id), dados => dados.DisparoMassaQuantidadeFalha === 1);
  await api('disparos/' + falha.id + '/repetir-falhas', 'POST');
  const recuperado = await aguardar(() => api('registros/disparos/' + falha.id), dados => dados.DisparoMassaQuantidadeEnviado === 1);
  assert.equal(recuperado.DisparoMassaQuantidadeFalha, 0);
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
  const bot = await api('registros/bots', 'POST', {
    ChatbotDefinicaoNome: 'Bot operacional',
    CanalId: canal.id,
    ChatbotDefinicaoProvedor: 'firma',
    ChatbotDefinicaoStatus: true,
    ChatbotDefinicaoFuncionamento: {
      Integral: true
    },
    ChatbotDefinicaoFlow: fluxo
  });
  async function entrada(id, texto) {
    const respostaAtual = await fetch(base + '/api/chat/interno/eventos', {
      method: 'POST',
      headers: {
        'X-Chat-Bridge-Key': chaveMensagem,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        tipo: 'mensagem',
        canalId: canal.CanalGUID,
        identificador: id,
        telefone: '5511999900015',
        nome: 'Visitante',
        texto
      })
    });
    assert.ok(respostaAtual.ok, await respostaAtual.text());
  }
  await entrada('entrada-bot-1-' + marcador, 'Oi');
  let sessoes = await api('bots/' + bot.id + '/sessoes');
  assert.ok(sessoes.some(itemAtual => itemAtual.estado === 'ATIVA'));
  await entrada('entrada-bot-2-' + marcador, 'Diana');
  sessoes = await api('bots/' + bot.id + '/sessoes');
  assert.ok(sessoes.some(itemAtual => itemAtual.estado === 'CONCLUIDA'));
  const respostas = await chamar(ponte + '/_envios');
  assert.ok(respostas.some(item => item.texto === 'Qual seu nome?'));
  assert.ok(respostas.some(item => item.texto === 'Olá, Diana!'));
  const contatos = await api('registros/contatos?busca=5511999900015'),
    contato = contatos.itens[0];
  const atendimentos = await api('registros/atendimentos?busca=5511999900015');
  assert.ok(contato && atendimentos.itens.length === 1);
  const registroAtual = await api('conversas/' + atendimentos.itens[0].id);
  assert.ok(registroAtual.mensagens.some(mensagem => mensagem.MensagemTexto === 'Olá, Diana!'));
  const quantidade = registroAtual.mensagens.length;
  await entrada('entrada-bot-2-' + marcador, 'Diana');
  assert.equal((await api('conversas/' + registroAtual.id)).mensagens.length, quantidade);
  const recebida = registroAtual.mensagens.find(mensagem => mensagem.MensagemTexto === 'Diana');
  assert.ok(recebida);
  async function atualizar(campos) {
    const respostaAtual = await fetch(base + '/api/chat/interno/eventos', {
      method: 'POST',
      headers: {
        'X-Chat-Bridge-Key': chaveMensagem,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        tipo: 'atualizacao',
        canalId: canal.CanalGUID,
        identificador: 'entrada-bot-2-' + marcador,
        ...campos
      })
    });
    assert.ok(respostaAtual.ok, await respostaAtual.text());
  }
  await atualizar({
    ack: 3,
    editada: true,
    texto: 'Diana editou',
    reacao: {
      Numero: '5511999900015',
      Reacao: '👍'
    }
  });
  let atual = (await api('conversas/' + registroAtual.id)).mensagens.find(item => item.id === recebida.id);
  assert.equal(atual.MensagemACK, 3);
  assert.equal(atual.MensagemTexto, 'Diana editou');
  assert.equal(atual.MensagemEditada, true);
  assert.equal(atual.MensagemReacao[0].Reacao, '👍');
  await atualizar({
    deletada: true
  });
  assert.equal((await api('conversas/' + registroAtual.id)).mensagens.find(item => item.id === recebida.id).MensagemDeletada, true);
  const fora = await fetch(base + '/api/chat/interno/eventos', {
    method: 'POST',
    headers: {
      'X-Chat-Bridge-Key': chaveMensagem,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      tipo: 'mensagem',
      canalId: canal.CanalGUID,
      identificador: 'fora-' + marcador,
      telefone: '5511999900015',
      fromMe: true,
      texto: 'Enviada por outro dispositivo',
      raw: {
        key: {
          id: 'fora-' + marcador,
          remoteJid: '5511999900015@s.whatsapp.net',
          fromMe: true
        },
        message: {
          conversation: 'Enviada por outro dispositivo'
        }
      }
    })
  });
  assert.ok(fora.ok, await fora.text());
  assert.ok((await api('conversas/' + registroAtual.id)).mensagens.some(mensagem => mensagem.MensagemTexto === 'Enviada por outro dispositivo'));
  const chave = await api('registros/chaves', 'POST', {
    ChaveAPIDescricao: 'API de compatibilidade'
  });
  const cabecalhos = {
    Authorization: 'Bearer ' + chave.chaveGerada
  };
  const canaisOrigem = await (await fetch(base + '/API/v1/Canal/Listar?Status=ativo', {
    headers: cabecalhos
  })).json();
  assert.equal(canaisOrigem.Erro, false);
  assert.ok(canaisOrigem.Canais.some(itemDoRegistro => itemDoRegistro.GUID === canal.CanalGUID));
  const setoresOrigem = await (await fetch(base + '/API/v1/Setor/Listar?Canal=' + canal.CanalGUID, {
    headers: cabecalhos
  })).json();
  assert.equal(setoresOrigem.Setores.length, 1);
  const respostaApi = await fetch(base + '/API/v1/Mensagem/Extraoficial/Enviar', {
    method: 'POST',
    headers: {
      ...cabecalhos,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      Canal: canal.CanalGUID,
      Destinatario: '5511999900016',
      Mensagem: {
        Tipo: 'Texto',
        Texto: 'Mensagem da API original'
      },
      AbrirAtendimento: true,
      Atendimento: {
        Setor: setor.id
      }
    })
  });
  const respostaTexto = await respostaApi.text();
  assert.ok(respostaApi.ok, respostaTexto);
  const resultadoApi = JSON.parse(respostaTexto);
  assert.equal(resultadoApi.Erro, false);
  assert.ok(resultadoApi.MensagemId);
  assert.ok(resultadoApi.AtendimentoId);
  const operador = (await api('registros/atendimentos?SetorId=' + setor.id)).itens;
  assert.ok(operador.length > 0);
  assert.ok(operador.every(registroAtual => registroAtual.SetorId === setor.id));
  if (process.env.FIRMA_TEST_PROVEDORES_URL) {
    await api('registros/integracoes', 'POST', {
      IntegracaoNome: 'Asaas',
      IntegracaoStatus: true,
      IntegracaoDados: [{
        campo: 'Token',
        valor: 'token-asaas-falso'
      }]
    });
    const cobrancas = await api('integracoes/Asaas/cobrancas', 'POST', {
      status: 'PENDING'
    });
    assert.equal(cobrancas.length, 3);
    await api('registros/integracoes', 'POST', {
      IntegracaoNome: 'SGA',
      IntegracaoStatus: true,
      IntegracaoDados: [{
        campo: 'Token',
        valor: 'token-sga-falso'
      }]
    });
    const boletos = await api('integracoes/SGA/boletos', 'POST', {
      codigo_situacao_boleto: 1
    });
    assert.equal(boletos.length, 2);
    await api('registros/integracoes', 'POST', {
      IntegracaoNome: 'hControl',
      IntegracaoStatus: true,
      IntegracaoDados: [{
        campo: 'clientId',
        valor: 'cliente-falso'
      }, {
        campo: 'secretId',
        valor: 'segredo-falso'
      }]
    });
    const hc = await api('integracoes/hControl/boletos', 'POST', {
      cpfCnpj: '123.456.789-00'
    });
    assert.equal(hc[0].id, 55);
    const impresso = await api('integracoes/hControl/imprimir', 'POST', {
      parcelaId: 55
    });
    assert.equal(impresso.link, 'https://example.test/boleto');
    const chamadas = await chamar(process.env.FIRMA_TEST_PROVEDORES_URL + '/_chamadas');
    assert.ok(chamadas.some(itemDoRegistro => itemDoRegistro.path === '/payments' && itemDoRegistro.query.offset === '2'));
    assert.ok(chamadas.some(itemDoRegistro => itemDoRegistro.path === '/listar/boleto/periodo' && itemDoRegistro.body.inicio_paginacao === 1));
    assert.ok(chamadas.some(itemDoRegistro => itemDoRegistro.path === '/Financeiro/ObterBoletosEmAbertoPorCpfCnpj' && itemDoRegistro.query.cpfCnpjCliente === '12345678900'));
  }
  if (process.env.FIRMA_TEST_SMTP_URL) {
    const perfis = (await api('registros/perfis')).itens,
      mail = 'reset-' + marcador + '@example.test';
    await api('registros/usuarios', 'POST', {
      UsuarioPrimeiroNome: 'Usuário',
      UsuarioUltimoNome: 'Recuperação',
      UsuarioEmail: mail,
      UsuarioSenha: 'SenhaAnterior2026!',
      PerfilId: perfis.find(paragrafo => paragrafo.PerfilNome === 'Atendente').id
    });
    const acesso = await chamar(base + '/api/firma/login', 'POST', {
      email: mail,
      password: 'SenhaAnterior2026!'
    });
    await chamar(base + '/api/firma/recuperar-senha', 'POST', {
      email: mail
    });
    const mensagens = await chamar(process.env.FIRMA_TEST_SMTP_URL);
    const carta = mensagens.filter(item => item.includes(mail)).at(-1);
    assert.ok(carta);
    const codigo = carta.match(/\b([0-9]{6})\b/)[1];
    const incorreto = codigo === '000000' ? '000001' : '000000';
    const invalido = await fetch(base + '/api/firma/redefinir-senha', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        email: mail,
        codigo: incorreto,
        senha: 'SenhaNova2026!'
      })
    });
    assert.equal(invalido.status, 400);
    await chamar(base + '/api/firma/redefinir-senha', 'POST', {
      email: mail,
      codigo,
      senha: 'SenhaNova2026!'
    });
    assert.equal((await fetch(base + '/api/firma/contexto', {
      headers: {
        Authorization: 'Bearer ' + acesso.token
      }
    })).status, 401);
    const novo = await chamar(base + '/api/firma/login', 'POST', {
      email: mail,
      password: 'SenhaNova2026!'
    });
    assert.ok(novo.token);
    await chamar(base + '/api/firma/conta/senha', 'PUT', {
      atual: 'SenhaNova2026!',
      nova: 'OutraSenha2026!'
    }, novo.token);
    assert.equal((await fetch(base + '/api/firma/contexto', {
      headers: {
        Authorization: 'Bearer ' + novo.token
      }
    })).status, 401);
    assert.ok((await chamar(base + '/api/firma/login', 'POST', {
      email: mail,
      password: 'OutraSenha2026!'
    })).token);
  }
  console.log('OK: fila real com transporte simulado, lotes/intervalos, limite diário operacional, cancelamento, falha/retomada, bot da origem, callbacks, ACK, edição, exclusão, reações, mensagens externas, API original e deduplicação.');
})().catch(evento => {
  console.error(evento);
  process.exitCode = 1;
});
