// Verifica as telas reais com APIs interceptadas, sem alterar o banco ou conectar o WhatsApp.
const {
  chromium
} = require('playwright');
const assert = require('node:assert/strict');
const arquivosEmDisco = require('node:fs');
const base = process.env.FIRMA_TEST_BASE_URL || 'http://127.0.0.1:8080';
(async () => {
  const navegador = await chromium.launch({
    headless: true,
    executablePath: process.env.CHROME_BIN || '/usr/bin/google-chrome',
    args: ['--no-sandbox']
  });
  try {
    const leitura = await navegador.newPage();
    await leitura.goto(base + '/assineai/trator');
    const token = await leitura.getAttribute('body', 'data-token-teste');
    assert(token, 'Execute no perfil local-test para obter o catálogo por leitura autenticada.');
    const cabecalhos = {
      Authorization: 'Bearer ' + token
    };
    const contextoR = await leitura.request.get(base + '/api/firma/contexto', {
      headers: cabecalhos
    });
    assert.equal(contextoR.status(), 200);
    const contexto = await contextoR.json();
    const modelosR = await leitura.request.get(base + '/api/assineai/empresa/templates', {
      headers: cabecalhos
    });
    assert.equal(modelosR.status(), 200);
    const modelos = await modelosR.json();
    await leitura.close();
    for (const largura of [1440, 500]) {
      const pagina = await navegador.newPage({
          viewport: {
            width: largura,
            height: 1000
          }
        }),
        erros = [],
        escritas = [];
      pagina.on('pageerror', evento => erros.push(evento.message));
      let empresa = {
        EmpresaNome: 'Firma de teste',
        EmpresaCNPJ: '',
        EmpresaEmail: 'firma@example.test',
        EmpresaTemaTelaAssinatura: 'firma',
        EmpresaTemplates: {
          versao: 2,
          ativo: modelos[0].id,
          modelos
        }
      };
      let somenteLeitura = false;
      let canais = [{
        id: 801,
        CanalNome: 'Atendimento',
        CanalNumeroCelular: '5511999999999',
        CanalProvedorAPI: 'Baileys',
        CanalStatusUso: true,
        CanalStatus: 'DESCONECTADO',
        CanalVisualizaMenuBOTChat: false
      }, {
        id: 802,
        CanalNome: 'Oficial',
        CanalProvedorAPI: 'Meta',
        CanalStatusUso: true,
        CanalMetaNumeroIdentificador: '1234',
        CanalMetaWAID: '5678'
      }];
      await pagina.addInitScript(() => localStorage.setItem('assineai-token', 'token-simulado'));
      await pagina.route('**/api/**', async route => {
        const requisicao = route.request(),
          endereco = new URL(requisicao.url()),
          paragrafo = endereco.pathname,
          metodo = requisicao.method();
        const json = dadosAtual => route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(dadosAtual)
        });
        if (metodo !== 'GET') escritas.push({
          p: paragrafo,
          method: metodo,
          body: requisicao.postDataJSON()
        });
        if (paragrafo === '/api/assineai/empresa') {
          if (metodo === 'PUT') empresa = {
            ...empresa,
            ...requisicao.postDataJSON()
          };
          return json(empresa);
        }
        if (paragrafo === '/api/firma/contexto') return json(somenteLeitura ? {
          ...contexto,
          modulos: contexto.modulos.map(modulo => modulo.slug === 'canais' ? {
            ...modulo,
            editavel: false,
            criavel: false,
            acoes: []
          } : modulo)
        } : contexto);
        if (paragrafo === '/api/assineai/empresa/templates') return json(modelos);
        if (paragrafo.endsWith('/templates/previa')) return route.fulfill({
          status: 200,
          contentType: 'image/png',
          body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l9sAAAAASUVORK5CYII=', 'base64')
        });
        if (paragrafo === '/api/firma/registros/canais' && metodo === 'GET') return json({
          itens: canais,
          total: canais.length
        });
        if (paragrafo === '/api/firma/registros/canais' && metodo === 'POST') {
          const registro = {
            id: 803,
            ...requisicao.postDataJSON()
          };
          canais.push(registro);
          return json(registro);
        }
        if (/\/registros\/canais\/\d+$/.test(paragrafo) && metodo === 'PUT') {
          const id = Number(paragrafo.split('/').at(-1)),
            registro = {
              ...canais.find(item => item.id === id),
              ...requisicao.postDataJSON()
            };
          canais = canais.map(item => item.id === id ? registro : item);
          return json(registro);
        }
        if (paragrafo.endsWith('/conectar')) return json({
          estado: 'CONECTANDO'
        });
        if (paragrafo.endsWith('/conexao')) return json({
          estado: 'QR_PENDENTE',
          qr: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l9sAAAAASUVORK5CYII='
        });
        throw Error('Chamada não esperada: ' + metodo + ' ' + paragrafo);
      });
      await pagina.goto(base + '/firma');
      await pagina.waitForSelector('#firma-canal-form:not([hidden])');
      assert.equal(await pagina.locator('iframe, #editor-templates, .tabela-scroll').count(), 0);
      assert.equal(await pagina.inputValue('#firma-CanalNome'), 'Atendimento');
      assert.equal(await pagina.locator('#firma-CanalStatus, #firma-CanalNomePastaWhatsApp').count(), 0);
      assert(await pagina.locator('#firma-CanalMetaWAID').isHidden());
      await pagina.fill('#perfil-nome', 'Firma editada');
      await pagina.locator('#dados-empresa [type=submit]').click();
      await pagina.waitForFunction(() => document.querySelector('#mensagem-empresa').textContent === 'Dados daFIRMA salvos.');
      const dados = escritas.find(evento => evento.p === '/api/assineai/empresa');
      assert(!('EmpresaTemplates' in dados.body), 'Salvar dados alterou modelos');
      await pagina.fill('#firma-CanalNome', 'Atendimento editado');
      await pagina.check('#firma-CanalVisualizaMenuBOTChat');
      await pagina.click('#firma-canal-salvar');
      await pagina.waitForFunction(() => document.querySelector('#mensagem-empresa').textContent === 'Canal salvo.');
      assert.equal(canais[0].CanalNome, 'Atendimento editado');
      assert.equal(canais[0].CanalVisualizaMenuBOTChat, true);
      await pagina.getByRole('button', {
        name: 'CONECTAR / QR',
        exact: true
      }).click();
      await pagina.waitForSelector('#firma-canal-qr:not([hidden])');
      await pagina.click('#firma-canal-fechar');
      assert(await pagina.locator('#firma-canal-painel').isHidden());
      await pagina.selectOption('#firma-canal', '802');
      assert.equal(await pagina.inputValue('#firma-CanalMetaWAID'), '5678');
      assert.equal(await pagina.getByRole('button', {
        name: 'CONECTAR / QR',
        exact: true
      }).count(), 0);
      await pagina.click('#firma-canal-novo');
      await pagina.fill('#firma-CanalNome', 'Outro canal');
      await pagina.fill('#firma-CanalNumeroCelular', '5511888888888');
      await pagina.click('#firma-canal-salvar');
      await pagina.waitForFunction(() => document.querySelector('#firma-canal').value === '803');
      assert.equal(canais.length, 3);
      arquivosEmDisco.mkdirSync('/tmp/firma-config', {
        recursive: true
      });
      await pagina.screenshot({
        path: `/tmp/firma-config/firma-${largura}.png`,
        fullPage: true
      });
      assert(await pagina.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'Firma com rolagem horizontal');
      await pagina.getByText('ACESSO TÉCNICO OU JWT EXISTENTE', {
        exact: true
      }).click();
      await pagina.fill('#empresa-token', 'outro-token');
      assert(await pagina.locator('#firma-canais').isHidden());
      await pagina.goto(base + '/assineai/pagina-assinatura');
      await pagina.waitForSelector('#editor-templates:not([hidden])');
      assert.equal(await pagina.inputValue('#perfil-nome'), 'Firma editada');
      await pagina.waitForFunction(() => document.querySelectorAll('.registro-card').length === 7);
      await pagina.fill('[data-modelo=titulo]', 'Quem assinou tá aqui');
      await pagina.click('#template-salvar');
      await pagina.waitForFunction(() => document.querySelector('#assineai-modelos-aviso').textContent.includes('Padrão salvo'));
      const salvo = escritas.filter(evento => evento.p === '/api/assineai/empresa').at(-1);
      assert.deepEqual(Object.keys(salvo.body), ['EmpresaTemplates']);
      assert.equal(empresa.EmpresaNome, 'Firma editada');
      await pagina.reload();
      await pagina.waitForSelector('#editor-templates:not([hidden])');
      assert.equal(await pagina.inputValue('[data-modelo=titulo]'), 'Quem assinou tá aqui');
      assert(await pagina.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'ADVai com rolagem horizontal');
      await pagina.screenshot({
        path: `/tmp/firma-config/advai-${largura}.png`,
        fullPage: true
      });
      somenteLeitura = true;
      await pagina.goto(base + '/firma');
      await pagina.waitForSelector('#firma-canal-form:not([hidden])');
      assert(await pagina.locator('#firma-CanalNome').isDisabled());
      assert(await pagina.locator('#firma-canal-novo').isHidden());
      assert(await pagina.locator('#firma-canal-salvar').isHidden());
      assert.equal(await pagina.locator('#firma-canal-acoes button').count(), 0);
      assert.deepEqual(erros, []);
      await pagina.close();
      console.log(`OK [${largura}]: firma sem trator, dados, edição/criação de canais, QR fechável, Meta e modelos no AssineAI com salvamento separado`);
    }
  } finally {
    await navegador.close();
  }
})().catch(evento => {
  console.error(evento);
  process.exitCode = 1;
});
