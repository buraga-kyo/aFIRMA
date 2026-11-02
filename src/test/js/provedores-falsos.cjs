// Contratos SGA/Asaas/hControl em loopback. Nenhuma conexão externa.
const http=require('node:http'),calls=[];
http.createServer(async(req,res)=>{const u=new URL(req.url,'http://127.0.0.1');let raw='';for await(const p of req)raw+=p;let b={};try{b=raw?JSON.parse(raw):{};}catch{}calls.push({path:u.pathname,query:Object.fromEntries(u.searchParams),body:b,auth:req.headers.authorization,access:req.headers.access_token});res.setHeader('Content-Type','application/json');let d;
 if(u.pathname==='/_chamadas')d=calls;
 else if(u.pathname==='/payments')d=Number(u.searchParams.get('offset')||0)===0?{data:[{id:'p1',customer:'c1',value:100},{id:'p2',customer:'c2',value:200}],hasMore:true}:{data:[{id:'p3',customer:'c1',value:300}],hasMore:false};
 else if(u.pathname==='/customers')d={data:[{id:'c1',name:'Cliente de teste'}],hasMore:false};
 else if(u.pathname==='/listar/situacao-boleto/ativo')d=[{codigo_situacaoboleto:1,descricao:'Aberto'}];
 else if(u.pathname==='/listar/boleto/periodo')d=Number(b.inicio_paginacao||0)===0?[{codigo:1,nome:'Ana',valor:100},{codigo:2,nome:'Bruno',valor:200}]:[];
 else if(u.pathname==='/Account/Token')d={data:{token:'token-hcontrol-de-teste-sem-valor-real'}};
 else if(u.pathname==='/Financeiro/ObterBoletosEmAbertoPorCpfCnpj'||u.pathname==='/Financeiro/ObterBoletosEmAberto')d={data:[{id:55,nomeFantasia:'Cliente hControl',valor:99}]};
 else if(u.pathname==='/Financeiro/ImprimirBoleto')d={data:{link:'https://example.test/boleto',linhaDigitavel:'0000'}};
 else if(u.pathname==='/ObterClientes')d={data:[{id:55,nome:'Cliente hControl'}]};
 else {res.statusCode=404;d={erro:'Rota não simulada'};}
 res.end(JSON.stringify(d));}).listen(18094,'127.0.0.1',()=>console.log('Provedores simulados locais em 18094'));
