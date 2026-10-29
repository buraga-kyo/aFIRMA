const elemento = seletor => document.querySelector(seletor);
let token = '';
let chave = '';
let areaAtual = 'contatos';
let registroAtual = null;
let registrosAtuais = [];
const selecao = new Set();
let filtrosInteligentes = [];
const colunas = {
  setores:[['nome','Setor'],['ativo','Situação']],
  atendentes:[['nome','Atendente'],['identidade','Identidade'],['disponibilidade','Disponibilidade'],['ativo','Situação']],
  atendimentos:[['protocolo','Protocolo'],['contato_nome','Contato'],['estado','Estado'],['atualizado_em','Atualização']],
  indicadores:[['canal','Canal'],['total','Total'],['pendentes','Pendentes'],['finalizados','Finalizados']],
  contatos:[['nome','Contato'],['telefone','Telefone'],['email','E-mail'],['atualizado_em','Atualização']],
  etiquetas:[['nome','Etiqueta'],['cor','Cor'],['ativa','Situação']],
  listas:[['nome','Lista'],['descricao','Descrição'],['ativa','Situação']],
  'mensagens-rapidas':[['titulo','Título'],['atalho','Atalho'],['texto','Texto'],['ativa','Situação']],
  modelos:[['nome','Modelo'],['idioma','Idioma'],['categoria','Categoria'],['estado','Estado']],
  campanhas:[['nome','Campanha'],['estado','Estado'],['agendada_em','Agendada'],['criada_em','Criada']],
  automacoes:[['nome','Automação'],['gatilho','Gatilho'],['prioridade','Prioridade'],['ativa','Situação']],
  bots:[['nome','Bot'],['canal_nome','Canal'],['provedor','Provedor'],['sessoes_ativas','Sessões ativas'],['ativa','Situação']],
  agentes:[['nome','Agente'],['provedor','Provedor'],['modelo','Modelo'],['ativa','Situação']],
  integracoes:[['nome','Integração'],['tipo','Tipo'],['ativa','Situação']],
  webhooks:[['nome','Webhook'],['destino','Destino'],['eventos','Eventos'],['ativo','Situação']],
  chaves:[['nome','Chave'],['escopos','Escopos'],['expira_em','Vencimento']],
  empresa:[['nome','Empresa'],['documento','Documento'],['fuso_horario','Fuso horário']],
  plano:[['nome','Plano'],['estado','Estado'],['limite_mensagens','Limite de mensagens']]
};
const campos = {
  setores: { grupo:'Equipe', titulo:'Setores', descricao:'Áreas que recebem e organizam atendimentos.', caminho:'/api/chat/admin/setores', chave:'nome', detalhe:'ativo', edita:true, acoes:'ativo_chat', itens:[['nome','Nome','text',true],['ativo','Ativo','checkbox']] },
  atendentes: { grupo:'Equipe', titulo:'Atendentes', descricao:'Pessoas responsáveis pelas conversas.', caminho:'/api/chat/admin/atendentes', chave:'nome', detalhe:'disponibilidade', edita:true, acoes:'ativo_chat', itens:[['identidade','Identidade no token','text',true],['nome','Nome','text',true],['setores','IDs dos setores (JSON)','lista'],['ativo','Ativo','checkbox']] },
  atendimentos: { grupo:'Conversas', titulo:'Atendimentos', descricao:'Fila e conversas em andamento.', caminho:'/api/chat/atendimentos', chave:'protocolo', detalhe:'estado' },
  indicadores: { grupo:'Análise', titulo:'Indicadores', descricao:'Resumo dos atendimentos por canal.', caminho:'/api/chat/admin/indicadores', chave:'canal', detalhe:'total' },
  contatos: { grupo:'Pessoas', titulo:'Contatos', descricao:'Cadastro e informações de quem conversa com a empresa.', caminho:'/contatos', chave:'nome', detalhe:'telefone', edita:true, itens:[['telefone','Telefone com DDI','tel',true],['nome','Nome','text',true],['email','E-mail','email'],['informacoes','Informações adicionais (JSON)','json']] },
  etiquetas: { grupo:'Organização', titulo:'Etiquetas', descricao:'Classifique os atendimentos por assunto.', caminho:'/etiquetas', chave:'nome', detalhe:'cor', edita:true, acoes:'desativar', itens:[['nome','Nome','text',true],['cor','Cor hexadecimal','text',true]] },
  listas: { grupo:'Pessoas', titulo:'Listas de contatos', descricao:'Organize destinatários para campanhas.', caminho:'/listas', chave:'nome', detalhe:'descricao', edita:true, acoes:'lista', itens:[['nome','Nome','text',true],['descricao','Descrição','text']] },
  'mensagens-rapidas': { grupo:'Conteúdo', titulo:'Mensagens rápidas', descricao:'Atalhos de respostas para atendentes.', caminho:'/mensagens-rapidas', chave:'titulo', detalhe:'atalho', edita:true, acoes:'desativar', itens:[['atalho','Atalho','text',true],['titulo','Título','text',true],['texto','Texto','textarea',true]] },
  modelos: { grupo:'Conteúdo', titulo:'Modelos de mensagem', descricao:'Mensagens aprovadas ou em preparação para canais oficiais.', caminho:'/modelos', chave:'nome', detalhe:'estado', edita:true, acoes:'modelo', itens:[['nome','Nome','text',true],['idioma','Idioma','text',true],['categoria','Categoria','select',true,['UTILIDADE','MARKETING','AUTENTICACAO']],['corpo','Corpo','textarea',true]] },
  campanhas: { grupo:'Envios', titulo:'Campanhas', descricao:'Programe envios para uma lista de contatos.', caminho:'/campanhas', chave:'nome', detalhe:'estado', acoes:'campanha', itens:[['nome','Nome','text',true],['canalId','ID do canal','text',true],['listaId','ID da lista','number',true],['modeloId','ID do modelo, se houver','number'],['texto','Texto, se não houver modelo','textarea'],['agendadaEm','Agendar para','datetime-local'],['intervaloSegundos','Intervalo entre envios (s)','number',true],['maximoDiario','Limite diário','number'],['horarioInicio','Início diário','time'],['horarioFim','Fim diário','time'],['diasSemana','Dias da semana (JSON)','lista']] },
  automacoes: { grupo:'Fluxos', titulo:'Automações', descricao:'Regras disparadas por mensagens recebidas.', caminho:'/automacoes', chave:'nome', detalhe:'gatilho', acoes:'ativacao', itens:[['nome','Nome','text',true],['gatilho','Gatilho','select',true,['MENSAGEM_RECEBIDA']],['condicoes','Condições (JSON)','json',true],['acoes','Ações (JSON)','json',true],['prioridade','Prioridade','number',true]] },
  bots: { grupo:'Fluxos', titulo:'Bots', descricao:'Definições por canal, etapas, horários e sessões. Edite nodes, edges e ações no fluxo JSON.', caminho:'/bots', chave:'nome', detalhe:'provedor', acoes:'bot', edita:true, itens:[['canalId','Canal','text',true],['nome','Nome','text',true],['provedor','Provedor','select',true,['INTERNO','N8N']],['ativo','Bot ativo','checkbox'],['funcionamento','Funcionamento (JSON)','json'],['fluxo','Fluxo (JSON)','json'],['enderecoExterno','Endereço externo','url']] },
  agentes: { grupo:'Inteligência', titulo:'Agentes de IA', descricao:'Modelos e instruções para respostas assistidas.', caminho:'/agentes', chave:'nome', detalhe:'modelo', acoes:'ativacao', itens:[['nome','Nome','text',true],['provedor','Provedor','select',true,['OPENAI','DEEPSEEK']],['modelo','Modelo','text',true],['instrucoes','Instruções','textarea',true],['referenciaSegredo','Nome da variável de segredo','text',true],['ativador','Ativador','text',true]] },
  integracoes: { grupo:'Integração', titulo:'Integrações', descricao:'Conectores configurados para esta empresa.', caminho:'/integracoes', chave:'nome', detalhe:'tipo', acoes:'ativacao', itens:[['nome','Nome','text',true],['tipo','Tipo','text',true],['configuracao','Configuração (JSON)','json',true],['referenciaSegredo','Nome da variável de segredo','text']] },
  webhooks: { grupo:'Integração', titulo:'Webhooks', descricao:'Eventos enviados a sistemas externos.', caminho:'/webhooks', chave:'nome', detalhe:'destino', acoes:'ativacao', itens:[['nome','Nome','text',true],['destino','URL de destino','url',true],['referenciaSegredo','Nome da variável de segredo','text',true],['eventos','Eventos (JSON)','lista',true],['todosCanais','Todos os canais','checkbox'],['canais','IDs de canais (JSON)','lista']] },
  chaves: { grupo:'Integração', titulo:'Chaves de API', descricao:'Acesso programático com escopos e vencimento.', caminho:'/chaves', chave:'nome', detalhe:'escopos', acoes:'chave', itens:[['nome','Nome','text',true],['escopos','Escopos (JSON)','lista',true],['expiraEm','Expira em','datetime-local']] },
  empresa: { grupo:'Conta', titulo:'Empresa', descricao:'Dados da empresa e fuso usado nas campanhas e bots.', caminho:'/empresa', chave:'nome', detalhe:'fuso_horario', edita:true, singular:true, itens:[['nome','Nome','text',true],['razaoSocial','Razão social','text'],['documento','CPF ou CNPJ, somente números','text'],['endereco','Endereço (JSON)','json'],['telefone','Telefone','tel'],['email','E-mail','email'],['fusoHorario','Fuso horário','text',true]] },
  plano: { grupo:'Conta', titulo:'Plano', descricao:'Limites e permissões da assinatura atual.', caminho:'/plano', chave:'nome', detalhe:'estado' }
};

function avisar(mensagem) { elemento('#aviso').textContent = mensagem || ''; }

async function requisitar(caminho, metodo='GET', dados) {
  const endereco = caminho.startsWith('/api/') ? caminho : `/api/relacionamento${caminho}`;
  const resposta = await fetch(endereco, {
    method:metodo,
    headers:{ Authorization:`Bearer ${token}`, 'X-API-Key':chave,
      ...(dados === undefined ? {} : {'Content-Type':'application/json'}) },
    body:dados === undefined ? undefined : JSON.stringify(dados)
  });
  const texto = await resposta.text();
  let resultado;
  try { resultado = texto ? JSON.parse(texto) : null; } catch { resultado = texto; }
  if (!resposta.ok) throw new Error(resultado?.message || resultado?.mensagem || `Erro HTTP ${resposta.status}`);
  return resultado;
}

function campoFormulario([nome, rotulo, tipo, obrigatorio, opcoes]) {
  const etiqueta = document.createElement('label');
  etiqueta.textContent = rotulo;
  let entrada;
  if (tipo === 'textarea' || tipo === 'json' || tipo === 'lista') entrada = document.createElement('textarea');
  else if (tipo === 'select') {
    entrada = document.createElement('select');
    for (const opcao of opcoes) entrada.add(new Option(opcao, opcao));
  } else entrada = document.createElement('input');
  if (tipo !== 'textarea' && tipo !== 'json' && tipo !== 'select') entrada.type = tipo;
  if (tipo === 'checkbox') entrada.checked = false;
  entrada.name = nome;
  entrada.required = !!obrigatorio;
  entrada.autocomplete = 'off';
  etiqueta.append(entrada);
  return etiqueta;
}

function montarFormulario(configuracao) {
  const formulario = elemento('#form-registro');
  formulario.replaceChildren();
  registroAtual = null;
  const painel = elemento('#painel-formulario');
  painel.hidden = true;
  if (!configuracao.itens) return;
  for (const campo of configuracao.itens) formulario.append(campoFormulario(campo));
  const botao = document.createElement('button');
  botao.type = 'submit';
  botao.textContent = 'Salvar';
  formulario.append(botao);
  if (areaAtual === 'bots') montarAbasBot(formulario, botao);
  elemento('#titulo-formulario').textContent = `NOVO REGISTRO · ${configuracao.titulo.toUpperCase()}`;
}

function montarAbasBot(formulario, salvar) {
  const abas = document.createElement('nav');
  abas.className = 'abas-bot';
  abas.setAttribute('aria-label', 'Configuração do bot');
  const grupos = {};
  for (const [id, titulo, descricao] of [
    ['geral','Geral','Identidade, canal e modo de operação.'],
    ['horarios','Horários','Defina quando o bot pode iniciar novas sessões.'],
    ['fluxo','Fluxo','Etapas, ligações, condições e integração REST.'],
    ['sessoes','Sessões','Acompanhe e cancele sessões deste bot.']
  ]) {
    const aba = document.createElement('button');
    aba.type = 'button';
    aba.textContent = titulo;
    aba.dataset.aba = id;
    aba.setAttribute('aria-selected', String(id === 'geral'));
    abas.append(aba);
    const grupo = document.createElement('div');
    grupo.className = 'grupo-bot';
    grupo.dataset.grupo = id;
    grupo.hidden = id !== 'geral';
    const cabecalho = document.createElement('h3');
    cabecalho.textContent = titulo.toUpperCase();
    const ajuda = document.createElement('p');
    ajuda.textContent = descricao;
    grupo.append(cabecalho, ajuda);
    grupos[id] = grupo;
    aba.addEventListener('click', async () => {
      for (const botao of abas.children) botao.setAttribute('aria-selected', String(botao === aba));
      for (const [nome, secao] of Object.entries(grupos)) secao.hidden = nome !== id;
      if (id === 'sessoes') await carregarSessoesBot();
    });
  }
  for (const nome of ['canalId','nome','provedor','ativo','enderecoExterno'])
    grupos.geral.append(formulario.elements.namedItem(nome).closest('label'));
  const entradaCanal = grupos.geral.querySelector('[name=canalId]');
  const seletorCanal = document.createElement('select');
  seletorCanal.name = 'canalId';
  seletorCanal.required = true;
  seletorCanal.add(new Option('Selecione um canal', ''));
  entradaCanal.replaceWith(seletorCanal);
  const entradaFuncionamento = formulario.elements.namedItem('funcionamento');
  entradaFuncionamento.type = 'hidden';
  entradaFuncionamento.closest('label').hidden = true;
  grupos.horarios.append(entradaFuncionamento.closest('label'));
  const integral = document.createElement('label');
  integral.innerHTML = '<input type="checkbox" name="integral" checked> Funcionamento integral';
  grupos.horarios.append(integral);
  const horarios = document.createElement('div');
  horarios.className = 'horarios';
  for (const dia of ['Segunda','Terca','Quarta','Quinta','Sexta','Sabado','Domingo']) {
    const linha = document.createElement('div');
    linha.className = 'horario-dia';
    linha.dataset.dia = dia;
    const ativar = document.createElement('label');
    ativar.innerHTML = `<input type="checkbox" name="${dia}-ativo"> ${dia}`;
    const inicio = document.createElement('label');
    inicio.textContent = 'Das';
    const horaInicio = document.createElement('input');
    horaInicio.type = 'time'; horaInicio.name = `${dia}-inicio`; horaInicio.value = '09:00';
    inicio.append(horaInicio);
    const fim = document.createElement('label');
    fim.textContent = 'Até';
    const horaFim = document.createElement('input');
    horaFim.type = 'time'; horaFim.name = `${dia}-fim`; horaFim.value = '18:00';
    fim.append(horaFim);
    const inverter = document.createElement('label');
    inverter.innerHTML = `<input type="checkbox" name="${dia}-inverter"> Inverter`;
    linha.append(ativar, inicio, fim, inverter);
    horarios.append(linha);
  }
  grupos.horarios.append(horarios);
  integral.querySelector('input').addEventListener('change', () => atualizarHorariosBot());
  grupos.fluxo.append(formulario.elements.namedItem('fluxo').closest('label'));
  const barraFluxo = document.createElement('div');
  barraFluxo.className = 'barra-fluxo';
  const resumoFluxo = document.createElement('span');
  resumoFluxo.id = 'resumo-fluxo';
  const formatarFluxo = document.createElement('button');
  formatarFluxo.type = 'button';
  formatarFluxo.className = 'secundario';
  formatarFluxo.textContent = 'Formatar e validar JSON';
  formatarFluxo.addEventListener('click', () => {
    try {
      const entrada = formulario.elements.namedItem('fluxo');
      const fluxo = JSON.parse(entrada.value);
      if (!Array.isArray(fluxo.nodes) || !Array.isArray(fluxo.edges))
        throw new Error('O fluxo precisa ter nodes e edges.');
      if (fluxo.edges.filter(ligacao => ligacao.source === 'start').length !== 1)
        throw new Error('O fluxo precisa ter exatamente uma ligação de início.');
      entrada.value = JSON.stringify(fluxo, null, 2);
      atualizarResumoFluxo();
      avisar('Estrutura JSON válida. As regras completas serão verificadas ao salvar.');
    } catch (erro) { avisar(erro.message); }
  });
  barraFluxo.append(resumoFluxo, formatarFluxo);
  grupos.fluxo.append(barraFluxo);
  const ajudaFluxo = document.createElement('p');
  ajudaFluxo.className = 'ajuda';
  ajudaFluxo.textContent = 'Use nodes para as etapas e edges para as ligações. As ações podem encaminhar a resposta para outra etapa ou para end.';
  grupos.fluxo.append(ajudaFluxo);
  const listaSessoes = document.createElement('div');
  listaSessoes.className = 'bot-sessoes';
  listaSessoes.id = 'sessoes-bot';
  grupos.sessoes.append(listaSessoes);
  const acoes = document.createElement('div');
  acoes.className = 'bot-acoes-form';
  acoes.append(salvar);
  formulario.prepend(abas, grupos.geral, grupos.horarios, grupos.fluxo, grupos.sessoes);
  formulario.append(acoes);
  formulario.elements.namedItem('provedor').addEventListener('change', atualizarProvedorBot);
  formulario.elements.namedItem('fluxo').addEventListener('input', atualizarResumoFluxo);
  atualizarProvedorBot();
}

function atualizarResumoFluxo() {
  const resumo = elemento('#resumo-fluxo');
  if (!resumo) return;
  try {
    const fluxo = JSON.parse(elemento('#form-registro').elements.namedItem('fluxo').value);
    resumo.textContent = `${fluxo.nodes?.length ?? 0} etapa(s) · ${fluxo.edges?.length ?? 0} ligação(ões)`;
  } catch { resumo.textContent = 'JSON ainda não é válido'; }
}

function atualizarHorariosBot() {
  const formulario = elemento('#form-registro');
  const integral = formulario.elements.namedItem('integral').checked;
  const horarios = formulario.querySelector('.horarios');
  horarios.classList.toggle('desabilitados', integral);
  for (const entrada of horarios.querySelectorAll('input')) entrada.disabled = integral;
}

function atualizarProvedorBot() {
  const formulario = elemento('#form-registro');
  const externo = formulario.elements.namedItem('provedor').value === 'N8N';
  const entrada = formulario.elements.namedItem('enderecoExterno');
  entrada.closest('label').hidden = !externo;
  entrada.required = externo;
}

function lerHorarioBot() {
  const formulario = elemento('#form-registro');
  const funcionamento = {Integral: formulario.elements.namedItem('integral').checked};
  for (const dia of ['Segunda','Terca','Quarta','Quinta','Sexta','Sabado','Domingo']) funcionamento[dia] = {
    Habilitado: formulario.elements.namedItem(`${dia}-ativo`).checked,
    HoraInicio: formulario.elements.namedItem(`${dia}-inicio`).value,
    HoraFim: formulario.elements.namedItem(`${dia}-fim`).value,
    Inverter: formulario.elements.namedItem(`${dia}-inverter`).checked
  };
  return JSON.stringify(funcionamento);
}

function preencherBot(registro) {
  const formulario = elemento('#form-registro');
  atualizarProvedorBot();
  const horario = desembrulhar(registro.funcionamento);
  const funcionamento = typeof horario === 'string' ? JSON.parse(horario) : horario || {};
  formulario.elements.namedItem('integral').checked = !!funcionamento.Integral;
  for (const dia of ['Segunda','Terca','Quarta','Quinta','Sexta','Sabado','Domingo']) {
    const dados = funcionamento[dia] || {};
    formulario.elements.namedItem(`${dia}-ativo`).checked = !!dados.Habilitado;
    formulario.elements.namedItem(`${dia}-inicio`).value = dados.HoraInicio || '09:00';
    formulario.elements.namedItem(`${dia}-fim`).value = dados.HoraFim || '18:00';
    formulario.elements.namedItem(`${dia}-inverter`).checked = !!dados.Inverter;
  }
  atualizarHorariosBot();
  atualizarResumoFluxo();
}

async function prepararBot() {
  const formulario = elemento('#form-registro');
  preencherBot({funcionamento:{Integral:true}});
  try {
    const canais = await requisitar('/api/chat/admin/canais');
    const seletor = formulario.elements.namedItem('canalId');
    seletor.replaceChildren(new Option('Selecione um canal', ''));
    for (const canal of canais.filter(item => item.ativo))
      seletor.add(new Option(`${canal.nome} · ${canal.telefone}`, canal.id));
    if (registroAtual?.canal_id) seletor.value = registroAtual.canal_id;
  } catch (erro) { avisar(erro.message); }
}

async function carregarSessoesBot() {
  const lista = elemento('#sessoes-bot');
  lista.replaceChildren();
  if (!registroAtual?.id) { lista.textContent = 'Salve o bot para acompanhar as sessões.'; return; }
  try {
    const sessoes = await requisitar(`/bots/${registroAtual.id}/sessoes`);
    if (!sessoes.length) { lista.textContent = 'Nenhuma sessão registrada.'; return; }
    const grade = document.createElement('div');
    grade.className = 'grade-rolagem';
    const tabela = document.createElement('table');
    tabela.className = 'tabela-firma';
    const cabecalho = document.createElement('thead');
    const titulos = document.createElement('tr');
    for (const titulo of ['Contato','Etapa','Estado','Atualização','Ações']) {
      const celula = document.createElement('th');
      const rotulo = document.createElement('span');
      rotulo.textContent = titulo;
      celula.append(rotulo);
      titulos.append(celula);
    }
    cabecalho.append(titulos);
    const corpo = document.createElement('tbody');
    for (const sessao of sessoes) {
      const linha = document.createElement('tr');
      for (const valor of [sessao.contato_nome || sessao.contato_telefone,
        sessao.etapa || 'Fluxo externo',sessao.estado,formatar(sessao.atualizada_em)]) {
        const celula = document.createElement('td');
        celula.textContent = valor;
        linha.append(celula);
      }
      const acoes = document.createElement('td');
      if (sessao.estado === 'ATIVA') acoes.append(botaoAcao('Cancelar', async () => {
        await requisitar(`/bots/sessoes/${sessao.id}`, 'DELETE');
        await carregarSessoesBot();
        await carregar();
      }, true));
      linha.append(acoes);
      corpo.append(linha);
    }
    tabela.append(cabecalho, corpo);
    grade.append(tabela);
    lista.append(grade);
  } catch (erro) { avisar(erro.message); }
}

function formatar(valor) {
  valor = desembrulhar(valor);
  if (valor == null) return '';
  if (typeof valor === 'boolean') return valor ? 'Ativo' : 'Inativo';
  if (Array.isArray(valor)) return valor.join(', ');
  if (typeof valor === 'object') return JSON.stringify(valor);
  if (typeof valor === 'string' && /^\d{4}-\d\d-\d\dT/.test(valor)) {
    const data = new Date(valor);
    if (!Number.isNaN(data.getTime())) return data.toLocaleString('pt-BR');
  }
  return String(valor);
}

function desembrulhar(valor) {
  if (valor && typeof valor === 'object' && (valor.type === 'jsonb' || valor.type === 'json')
      && typeof valor.value === 'string') {
    try { return JSON.parse(valor.value); } catch { return valor.value; }
  }
  return valor;
}

function valorColuna(registro, nome) {
  return registro[nome] ?? registro[nome.replace(/[A-Z]/g, letra => `_${letra.toLowerCase()}`)];
}

function atualizarSelecao() {
  elemento('#selecionados').textContent = selecao.size ? `${selecao.size} selecionado(s)` : '';
  elemento('#exportar').disabled = selecao.size === 0;
  const caixas = [...elemento('#lista tbody').querySelectorAll('input[type="checkbox"]')];
  const todos = elemento('#lista thead input[type="checkbox"]');
  if (todos) {
    todos.checked = caixas.length > 0 && caixas.every(caixa => caixa.checked);
    todos.indeterminate = caixas.some(caixa => caixa.checked) && !todos.checked;
  }
}

function desenharTabela() {
  const configuracao = campos[areaAtual];
  const tabela = elemento('#lista');
  const cabecalho = tabela.tHead;
  const corpo = tabela.tBodies[0];
  const busca = elemento('#busca').value.trim().toLocaleLowerCase('pt-BR');
  const registros = registrosAtuais.filter(registro => {
    const buscaSimples = !busca || Object.values(registro).some(valor =>
      formatar(valor).toLocaleLowerCase('pt-BR').includes(busca));
    const buscaInteligente = filtrosInteligentes.every(filtro => {
      const valor = formatar(valorColuna(registro, filtro.campo)).toLocaleLowerCase('pt-BR');
      const esperado = filtro.valor.toLocaleLowerCase('pt-BR');
      return filtro.operador === 'igual' ? valor === esperado :
        filtro.operador === 'diferente' ? valor !== esperado : valor.includes(esperado);
    });
    return buscaSimples && buscaInteligente;
  });
  cabecalho.replaceChildren();
  corpo.replaceChildren();
  const linhaCabecalho = document.createElement('tr');
  const selecaoCabecalho = document.createElement('th');
  selecaoCabecalho.className = 'selecao';
  const marcarTodos = document.createElement('input');
  marcarTodos.type = 'checkbox';
  marcarTodos.setAttribute('aria-label','Selecionar todos os registros visíveis');
  marcarTodos.addEventListener('change', () => {
    for (const registro of registros) {
      if (marcarTodos.checked) selecao.add(String(registro.id ?? registro[configuracao.chave]));
      else selecao.delete(String(registro.id ?? registro[configuracao.chave]));
    }
    desenharTabela();
  });
  selecaoCabecalho.append(marcarTodos);
  linhaCabecalho.append(selecaoCabecalho);
  for (const [, rotulo] of colunas[areaAtual]) {
    const celula = document.createElement('th');
    const pílula = document.createElement('span');
    pílula.textContent = rotulo;
    celula.append(pílula);
    linhaCabecalho.append(celula);
  }
  const acoes = document.createElement('th');
  const rotuloAcoes = document.createElement('span');
  rotuloAcoes.textContent = 'Ações';
  acoes.append(rotuloAcoes);
  linhaCabecalho.append(acoes);
  cabecalho.append(linhaCabecalho);
  for (const registro of registros) {
    const identificador = String(registro.id ?? registro[configuracao.chave]);
    const linha = document.createElement('tr');
    if (selecao.has(identificador)) linha.className = 'selecionada';
    const primeira = document.createElement('td');
    primeira.className = 'selecao';
    const caixa = document.createElement('input');
    caixa.type = 'checkbox';
    caixa.checked = selecao.has(identificador);
    caixa.setAttribute('aria-label', `Selecionar ${formatar(valorColuna(registro, configuracao.chave) ?? identificador)}`);
    caixa.addEventListener('change', () => {
      if (caixa.checked) selecao.add(identificador); else selecao.delete(identificador);
      linha.classList.toggle('selecionada', caixa.checked);
      atualizarSelecao();
    });
    primeira.append(caixa);
    linha.append(primeira);
    for (const [nome] of colunas[areaAtual]) {
      const celula = document.createElement('td');
      const valor = valorColuna(registro, nome);
      if (nome === 'ativo' || nome === 'ativa' || nome === 'estado' || nome === 'conexao') {
        const etiqueta = document.createElement('span');
        etiqueta.className = 'estado';
        const texto = formatar(valor) || 'Não informado';
        etiqueta.textContent = texto;
        if (valor === true || /^(ATIVA|ATIVO|APROVADO|CONECTADO|ENTREGUE|EM_ANDAMENTO)$/i.test(texto)) etiqueta.classList.add('positivo');
        if (valor === false || /^(FALHOU|CANCELADA|REJEITADO|DESCONECTADO)$/i.test(texto)) etiqueta.classList.add('alerta');
        celula.append(etiqueta);
      } else {
        celula.textContent = formatar(valor) || 'Não informado';
      }
      linha.append(celula);
    }
    const celulaAcoes = document.createElement('td');
    celulaAcoes.className = 'acoes';
    celulaAcoes.append(acoesRegistro(registro, configuracao));
    linha.append(celulaAcoes);
    corpo.append(linha);
  }
  if (!registros.length) {
    const linha = document.createElement('tr');
    const celula = document.createElement('td');
    celula.colSpan = colunas[areaAtual].length + 2;
    celula.className = 'vazio-tabela';
    celula.textContent = busca ? 'Nenhum registro corresponde à busca.' : 'Nenhum registro encontrado.';
    linha.append(celula);
    corpo.append(linha);
  }
  elemento('#resumo').textContent = `${registros.length} de ${registrosAtuais.length} registro(s)`;
  atualizarSelecao();
}

function botaoAcao(rotulo, acao, secundario=true) {
  const botao = document.createElement('button');
  botao.type = 'button';
  botao.textContent = rotulo;
  if (secundario) botao.className = 'secundario';
  botao.addEventListener('click', async () => {
    botao.disabled = true;
    try { await acao(); } catch (erro) { avisar(erro.message); }
    finally { botao.disabled = false; }
  });
  return botao;
}

function mostrarDetalhes(titulo, registros, acaoRegistro) {
  const painel = elemento('#painel-detalhes');
  const lista = elemento('#lista-detalhes');
  elemento('#titulo-detalhes').textContent = titulo.toUpperCase();
  lista.replaceChildren();
  if (!registros.length) { lista.textContent = 'Nenhum registro encontrado.'; painel.hidden = false; return; }
  const nomes = Object.keys(registros[0]).filter(nome => !['dados','mensagens'].includes(nome));
  const grade = document.createElement('div');
  grade.className = 'grade-rolagem';
  const tabela = document.createElement('table');
  tabela.className = 'tabela-firma';
  const cabecalho = document.createElement('thead');
  const titulos = document.createElement('tr');
  for (const nome of [...nomes, ...(acaoRegistro ? ['Ações'] : [])]) {
    const celula = document.createElement('th');
    const rotulo = document.createElement('span');
    rotulo.textContent = nome.replaceAll('_',' ');
    celula.append(rotulo);
    titulos.append(celula);
  }
  cabecalho.append(titulos);
  const corpo = document.createElement('tbody');
  for (const registro of registros) {
    const linha = document.createElement('tr');
    for (const nome of nomes) {
      const celula = document.createElement('td');
      celula.textContent = formatar(registro[nome]) || 'Não informado';
      linha.append(celula);
    }
    if (acaoRegistro) {
      const acao = acaoRegistro(registro);
      const celula = document.createElement('td');
      if (acao) celula.append(acao);
      linha.append(celula);
    }
    corpo.append(linha);
  }
  tabela.append(cabecalho, corpo);
  grade.append(tabela);
  lista.append(grade);
  painel.hidden = false;
  painel.scrollIntoView({behavior:'smooth',block:'start'});
}

function editar(registro, configuracao, rolar=true) {
  elemento('#painel-formulario').hidden = false;
  registroAtual = registro;
  const formulario = elemento('#form-registro');
  for (const [nome] of configuracao.itens) {
    const entrada = formulario.elements.namedItem(nome);
    if (!entrada) continue;
    const valor = registro[nome] ?? registro[nome.replace(/[A-Z]/g, letra => `_${letra.toLowerCase()}`)];
    if (entrada.type === 'checkbox') entrada.checked = !!valor;
    else entrada.value = formatar(valor);
  }
  elemento('#titulo-formulario').textContent = `EDITAR · ${configuracao.titulo.toUpperCase()}`;
  if (areaAtual === 'bots') preencherBot(registro);
  if (rolar) formulario.scrollIntoView({behavior:'smooth',block:'start'});
}

function acoesRegistro(registro, configuracao) {
  const acoes = document.createElement('div');
  acoes.className = 'acoes-linha';
  if (configuracao.edita) acoes.append(botaoAcao('Editar', () => editar(registro, configuracao)));
  if (configuracao.acoes === 'ativacao') acoes.append(botaoAcao(registro.ativa ? 'Desativar' : 'Ativar', async () => {
    await requisitar(`${configuracao.caminho}/${registro.id}/ativo`, 'PUT', {ativo:!registro.ativa});
    await carregar();
  }));
  if (configuracao.caminho === '/webhooks') {
    acoes.append(botaoAcao('Entregas', async () => {
      const entregas = await requisitar(`/webhooks/${registro.id}/entregas`);
      mostrarDetalhes(`Entregas · ${registro.nome}`, entregas);
    }));
    acoes.append(botaoAcao('Excluir', async () => {
      if (!confirm(`Excluir o webhook ${registro.nome}?`)) return;
      await requisitar(`/webhooks/${registro.id}`, 'DELETE');
      await carregar();
    }));
  }
  if (configuracao.acoes === 'ativo_chat') acoes.append(botaoAcao(registro.ativo ? 'Desativar' : 'Ativar', async () => {
    await requisitar(`${configuracao.caminho}/${registro.id}`, 'PUT', {nome:registro.nome,ativo:!registro.ativo});
    await carregar();
  }));
  if (configuracao.acoes === 'chave') acoes.append(botaoAcao('Revogar', async () => {
    if (!confirm('Revogar esta chave?')) return;
    await requisitar(`${configuracao.caminho}/${registro.id}`, 'DELETE');
    await carregar();
  }));
  if (configuracao.acoes === 'desativar' && registro.ativa) acoes.append(botaoAcao('Desativar', async () => {
    if (!confirm(`Desativar ${registro[configuracao.chave]}?`)) return;
    await requisitar(`${configuracao.caminho}/${registro.id}`, 'DELETE');
    await carregar();
  }));
  if (configuracao.acoes === 'modelo' && registro.estado === 'RASCUNHO')
    acoes.append(botaoAcao('Arquivar', async () => {
      if (!confirm(`Arquivar ${registro.nome}?`)) return;
      await requisitar(`${configuracao.caminho}/${registro.id}`, 'DELETE');
      await carregar();
    }));
  if (configuracao.acoes === 'lista') acoes.append(botaoAcao('Incluir contato', async () => {
    const contato = prompt('ID do contato');
    if (!contato) return;
    await requisitar(`/listas/${registro.id}/contatos/${encodeURIComponent(contato)}`, 'POST');
    avisar('Contato incluído na lista.');
  }));
  if (configuracao.acoes === 'lista' && registro.ativa) acoes.append(botaoAcao('Desativar', async () => {
    if (!confirm(`Desativar a lista ${registro.nome}?`)) return;
    await requisitar(`${configuracao.caminho}/${registro.id}`, 'DELETE');
    await carregar();
  }));
  if (configuracao.acoes === 'campanha') {
    for (const estado of ['AGENDADA','PAUSADA','CANCELADA']) acoes.append(botaoAcao(estado, async () => {
      await requisitar(`/campanhas/${registro.id}/estado`, 'PUT', {estado});
      await carregar();
    }));
  }
  if (configuracao.acoes === 'bot') {
    acoes.append(botaoAcao('Sessões', async () => {
      const sessoes = await requisitar(`/bots/${registro.id}/sessoes`);
      mostrarDetalhes(`Sessões · ${registro.nome}`, sessoes, sessao =>
        sessao.estado === 'ATIVA' ? botaoAcao('Cancelar sessão', async () => {
          if (!confirm('Cancelar esta sessão?')) return;
          await requisitar(`/bots/sessoes/${sessao.id}`, 'DELETE');
          const atualizadas = await requisitar(`/bots/${registro.id}/sessoes`);
          mostrarDetalhes(`Sessões · ${registro.nome}`, atualizadas);
          await carregar();
        }) : null);
    }));
    acoes.append(botaoAcao('Excluir', async () => {
      if (!confirm(`Excluir ${registro.nome} e encerrar suas sessões?`)) return;
      await requisitar(`/bots/${registro.id}`, 'DELETE');
      await carregar();
    }));
  }
  return acoes;
}

async function carregar() {
  const configuracao = campos[areaAtual];
  const dados = await requisitar(configuracao.caminho);
  registrosAtuais = Array.isArray(dados) ? dados : dados ? [dados] : [];
  selecao.clear();
  desenharTabela();
  avisar('');
}

function selecionarArea(nome) {
  areaAtual = nome;
  elemento('#painel-detalhes').hidden = true;
  elemento('#painel-formulario').hidden = true;
  elemento('#busca').value = '';
  elemento('#pergunta-filtro').value = '';
  filtrosInteligentes = [];
  mostrarFiltrosInteligentes();
  selecao.clear();
  const configuracao = campos[nome];
  for (const botao of elemento('#areas').children)
    botao.setAttribute('aria-current', botao.dataset.area === nome ? 'page' : 'false');
  elemento('#grupo').textContent = configuracao.grupo.toUpperCase();
  elemento('#titulo').textContent = configuracao.titulo.toUpperCase();
  elemento('#descricao').textContent = configuracao.descricao;
  montarFormulario(configuracao);
  elemento('#novo').hidden = !configuracao.itens;
  elemento('#novo').textContent = configuracao.singular ? 'Editar dados' : 'Novo registro';
  const padroes = {
    contatos:{informacoes:'{}'}, campanhas:{intervaloSegundos:'5',diasSemana:'[1,2,3,4,5]'},
    automacoes:{gatilho:'MENSAGEM_RECEBIDA',condicoes:'{}',acoes:'[]',prioridade:'0'},
    bots:{provedor:'INTERNO',funcionamento:'{"Integral":true}',fluxo:JSON.stringify({nodes:[{id:'boas_vindas',type:'tarefa',data:{pergunta:'Olá! Como posso ajudar?',acoes:[{valor:'end',condicao:''}]}}],edges:[{source:'start',target:'boas_vindas'},{source:'boas_vindas',target:'end'}]},null,2)},
    integracoes:{configuracao:'{}'}, webhooks:{eventos:'[]',canais:'[]'},
    chaves:{escopos:'["canais.ler"]'}, atendentes:{setores:'[]'}
  };
  for (const [campo, valor] of Object.entries(padroes[nome] || {}))
    elemento('#form-registro').elements.namedItem(campo).value = valor;
  if (nome === 'webhooks') elemento('#form-registro').elements.namedItem('todosCanais').checked = true;
  if (nome === 'bots') prepararBot();
  carregar().catch(erro => avisar(erro.message));
}

function mostrarFiltrosInteligentes() {
  const painel = elemento('#filtros-ia');
  painel.replaceChildren();
  painel.hidden = filtrosInteligentes.length === 0;
  for (const filtro of filtrosInteligentes) {
    const etiqueta = document.createElement('span');
    etiqueta.textContent = `${filtro.campo} ${filtro.operador} ${filtro.valor}`;
    painel.append(etiqueta);
  }
  if (filtrosInteligentes.length) painel.append(botaoAcao('Limpar filtro', () => {
    filtrosInteligentes = [];
    mostrarFiltrosInteligentes();
    desenharTabela();
  }, true));
}

for (const [nome, configuracao] of Object.entries(campos)) {
  const botao = document.createElement('button');
  botao.type = 'button';
  botao.textContent = configuracao.titulo;
  botao.dataset.area = nome;
  botao.addEventListener('click', () => selecionarArea(nome));
  elemento('#areas').append(botao);
}

elemento('#form-acesso').addEventListener('submit', async evento => {
  evento.preventDefault();
  token = evento.currentTarget.elements.namedItem('token').value.trim();
  chave = evento.currentTarget.elements.namedItem('chave').value.trim();
  try {
    await requisitar(campos[areaAtual].caminho);
    elemento('#acesso').hidden = true;
    elemento('#gestao').hidden = false;
    selecionarArea(areaAtual);
  } catch (erro) { avisar(erro.message); }
});

elemento('#busca').addEventListener('input', desenharTabela);
elemento('#form-filtro-ia').addEventListener('submit', async evento => {
  evento.preventDefault();
  const formulario = evento.currentTarget;
  const pergunta = formulario.elements.namedItem('pergunta').value.trim();
  if (!pergunta) { filtrosInteligentes = []; mostrarFiltrosInteligentes(); desenharTabela(); return; }
  const botao = formulario.querySelector('button');
  botao.disabled = true;
  try {
    const colunasPermitidas = colunas[areaAtual].map(([nome]) => nome);
    const resposta = await requisitar('/filtros/interpretar', 'POST',
      {pergunta, colunas:colunasPermitidas});
    filtrosInteligentes = resposta.filtros || [];
    mostrarFiltrosInteligentes();
    desenharTabela();
    avisar(filtrosInteligentes.length ? 'Filtro aplicado.' : 'A IA não encontrou um filtro para esta pergunta.');
  } catch (erro) { avisar(erro.message); }
  finally { botao.disabled = false; }
});
elemento('#novo').addEventListener('click', () => {
  const configuracao = campos[areaAtual];
  if (configuracao.singular && registrosAtuais.length) {
    editar(registrosAtuais[0], configuracao);
    return;
  }
  const nome = areaAtual;
  selecionarArea(nome);
  elemento('#painel-formulario').hidden = false;
  elemento('#painel-formulario').scrollIntoView({behavior:'smooth',block:'start'});
});
elemento('#fechar-formulario').addEventListener('click', () => {
  elemento('#painel-formulario').hidden = true;
  registroAtual = null;
});
elemento('#exportar').addEventListener('click', () => {
  const configuracao = campos[areaAtual];
  const linhas = registrosAtuais.filter(registro => selecao.has(String(registro.id ?? registro[configuracao.chave])));
  if (!linhas.length) return;
  const nomes = colunas[areaAtual];
  const escapar = valor => '"' + formatar(valor).replaceAll('"','""') + '"';
  const conteudo = [nomes.map(([, rotulo]) => escapar(rotulo)).join(';'),
    ...linhas.map(registro => nomes.map(([nome]) => escapar(valorColuna(registro, nome))).join(';'))].join('\n');
  const arquivo = new Blob(['\ufeff', conteudo], {type:'text/csv;charset=utf-8'});
  const endereco = URL.createObjectURL(arquivo);
  const link = document.createElement('a');
  link.href = endereco;
  link.download = `firma-${areaAtual}.csv`;
  link.click();
  setTimeout(() => URL.revokeObjectURL(endereco), 1000);
});

elemento('#form-registro').addEventListener('submit', async evento => {
  evento.preventDefault();
  const configuracao = campos[areaAtual];
  const formulario = evento.currentTarget;
  const dados = {};
  try {
    if (areaAtual === 'bots') formulario.elements.namedItem('funcionamento').value = lerHorarioBot();
    for (const [nome,, tipo] of configuracao.itens) {
      const entrada = formulario.elements.namedItem(nome);
      const valor = tipo === 'checkbox' ? entrada.checked : entrada.value.trim();
      if (tipo !== 'checkbox' && !valor) { dados[nome] = null; continue; }
      dados[nome] = tipo === 'json' ? JSON.stringify(JSON.parse(valor)) :
        tipo === 'lista' ? JSON.parse(valor) :
        tipo === 'number' ? Number(valor) : tipo === 'datetime-local' ? new Date(valor).toISOString() : valor;
    }
    const edicao = configuracao.singular || registroAtual && configuracao.edita;
    const resultado = await requisitar(edicao && !configuracao.singular ? `${configuracao.caminho}/${registroAtual.id}` : configuracao.caminho,
      edicao ? 'PUT' : 'POST', dados);
    if (resultado?.chave) alert(`Copie a chave agora. Ela não será exibida novamente:\n${resultado.chave}`);
    formulario.reset();
    registroAtual = null;
    elemento('#titulo-formulario').textContent = `NOVO REGISTRO · ${configuracao.titulo.toUpperCase()}`;
    elemento('#painel-formulario').hidden = true;
    await carregar();
    avisar('Registro salvo.');
  } catch (erro) { avisar(erro.message); }
});

elemento('#atualizar').addEventListener('click', () => carregar().catch(erro => avisar(erro.message)));
elemento('#fechar-detalhes').addEventListener('click', () => { elemento('#painel-detalhes').hidden = true; });
if (document.body.dataset.tokenTeste && document.body.dataset.chaveTeste) {
  const botao = elemento('#teste');
  botao.hidden = false;
  botao.addEventListener('click', () => {
    elemento('#form-acesso input[name="token"]').value = document.body.dataset.tokenTeste;
    elemento('#form-acesso input[name="chave"]').value = document.body.dataset.chaveTeste;
    elemento('#form-acesso').requestSubmit();
  });
}
