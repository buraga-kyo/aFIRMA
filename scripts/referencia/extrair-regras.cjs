// Executa apenas a função pura de compilação da origem, sem carregar conectores ou credenciais.
const arquivosEmDisco = require('node:fs'),
  caminhos = require('node:path'),
  vm = require('node:vm'),
  criptografia = require('node:crypto');
const raiz = process.env.FIRMA_REFERENCIA_ROOT || '/home/aluno/autentica/zapio';
const origem = caminhos.join(raiz, 'model/src/consultas/TabelaChatbotDefinicao.js'),
  fonte = arquivosEmDisco.readFileSync(origem, 'utf8');
const trecho = fonte.slice(fonte.indexOf('async function GerarChatbotDefinicaoEMetadata('), fonte.indexOf('exports.RecuperarDadosMensagemTemplatePorId') > 0 ? fonte.indexOf('exports.RecuperarDadosMensagemTemplatePorId') : fonte.length);
const contexto = {
  exports: {},
  console: {
    log() {
      throw Error('A compilação da origem falhou');
    }
  },
  URLSearchParams
};
vm.createContext(contexto);
vm.runInContext(trecho, contexto);
const tarefa = {
  id: 'pergunta',
  type: 'tarefa',
  data: {
    pergunta: 'Seu nome?',
    variavel: 'nome',
    resposta: 'Olá, &NOME!',
    acoes: [{
      nome: 'transferir_tarefa',
      valor: 'end',
      condicao: ''
    }]
  }
};
const cenarios = [{
  nodes: [{
    id: 'start',
    type: 'start',
    data: null
  }, tarefa, {
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
}, {
  nodes: [{
    id: 'transferir',
    type: 'script',
    data: {
      tipo: 'transferir_setor',
      setor: {
        id: 4
      },
      destino_transferir: 'gerar'
    }
  }, {
    id: 'gerar',
    type: 'script',
    data: {
      tipo: 'gerar_atendimento'
    }
  }],
  edges: [{
    source: 'start',
    target: 'transferir'
  }, {
    source: 'gerar',
    target: 'end'
  }]
}, {
  nodes: [{
    id: 'rest',
    type: 'integracao',
    data: {
      tipo: 'rest',
      variavel: 'retorno',
      restData: {
        url: 'https://example.test/clientes',
        metodo: 'POST',
        enviarHeaders: true,
        headers: [{
          chave: 'Authorization',
          valor: 'Bearer teste'
        }],
        enviarQueryParameters: true,
        queryParameters: [{
          nome: 'nome',
          valor: 'Maria da Silva'
        }],
        body: '{}'
      },
      acoes: [{
        nome: 'transferir_tarefa',
        valor: 'end',
        condicao: ''
      }]
    }
  }],
  edges: [{
    source: 'start',
    target: 'rest'
  }, {
    source: 'rest',
    target: 'end'
  }]
}, {
  nodes: [{
    id: 'hcontrol',
    type: 'integracao',
    data: {
      tipo: 'hControl',
      hControlData: {
        clientId: 'cliente-de-teste',
        secretId: 'segredo-falso'
      },
      variavel: 'boletos'
    }
  }],
  edges: [{
    source: 'start',
    target: 'hcontrol'
  }, {
    source: 'hcontrol',
    target: 'end'
  }]
}];
(async () => {
  const casos = [];
  for (const fluxo of cenarios) casos.push({
    fluxo,
    esperado: await contexto.GerarChatbotDefinicaoEMetadata(fluxo)
  });
  const saida = caminhos.resolve('/tmp/firma-referencias/compilador.json');
  arquivosEmDisco.mkdirSync(caminhos.dirname(saida), {
    recursive: true
  });
  arquivosEmDisco.writeFileSync(saida, JSON.stringify({
    sha256: criptografia.createHash('sha256').update(fonte).digest('hex'),
    casos
  }, null, 2) + '\n');
  console.log('4 vetores calculados pelo compilador original.');
})().catch(evento => {
  console.error(evento);
  process.exitCode = 1;
});
