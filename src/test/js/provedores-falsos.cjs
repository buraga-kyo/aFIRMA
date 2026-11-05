// Contratos SGA/Asaas/hControl em loopback. Nenhuma conexão externa.
const servidorHttp = require('node:http'),
  calls = [];
servidorHttp.createServer(async (requisicao, resposta) => {
  const endereco = new URL(requisicao.url, 'http://127.0.0.1');
  let raw = '';
  for await (const paragrafo of requisicao) raw += paragrafo;
  let botao = {};
  try {
    botao = raw ? JSON.parse(raw) : {};
  } catch {}
  calls.push({
    path: endereco.pathname,
    query: Object.fromEntries(endereco.searchParams),
    body: botao,
    auth: requisicao.headers.authorization,
    access: requisicao.headers.access_token
  });
  resposta.setHeader('Content-Type', 'application/json');
  let dados;
  if (endereco.pathname === '/_chamadas') dados = calls;else if (endereco.pathname === '/payments') dados = Number(endereco.searchParams.get('offset') || 0) === 0 ? {
    data: [{
      id: 'p1',
      customer: 'c1',
      value: 100
    }, {
      id: 'p2',
      customer: 'c2',
      value: 200
    }],
    hasMore: true
  } : {
    data: [{
      id: 'p3',
      customer: 'c1',
      value: 300
    }],
    hasMore: false
  };else if (endereco.pathname === '/customers') dados = {
    data: [{
      id: 'c1',
      name: 'Cliente de teste'
    }],
    hasMore: false
  };else if (endereco.pathname === '/listar/situacao-boleto/ativo') dados = [{
    codigo_situacaoboleto: 1,
    descricao: 'Aberto'
  }];else if (endereco.pathname === '/listar/boleto/periodo') dados = Number(botao.inicio_paginacao || 0) === 0 ? [{
    codigo: 1,
    nome: 'Ana',
    valor: 100
  }, {
    codigo: 2,
    nome: 'Bruno',
    valor: 200
  }] : [];else if (endereco.pathname === '/Account/Token') dados = {
    data: {
      token: 'token-hcontrol-de-teste-sem-valor-real'
    }
  };else if (endereco.pathname === '/Financeiro/ObterBoletosEmAbertoPorCpfCnpj' || endereco.pathname === '/Financeiro/ObterBoletosEmAberto') dados = {
    data: [{
      id: 55,
      nomeFantasia: 'Cliente hControl',
      valor: 99
    }]
  };else if (endereco.pathname === '/Financeiro/ImprimirBoleto') dados = {
    data: {
      link: 'https://example.test/boleto',
      linhaDigitavel: '0000'
    }
  };else if (endereco.pathname === '/ObterClientes') dados = {
    data: [{
      id: 55,
      nome: 'Cliente hControl'
    }]
  };else {
    resposta.statusCode = 404;
    dados = {
      erro: 'Rota não simulada'
    };
  }
  resposta.end(JSON.stringify(dados));
}).listen(18094, '127.0.0.1', () => console.log('Provedores simulados locais em 18094'));
