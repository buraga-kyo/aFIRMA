const {test}=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs/promises'),os=require('node:os'),path=require('node:path');
const {HistoricoWhatsApp}=require('../../../chat-baileys/historico.cjs');
test('normaliza histórico, resolve LID, conserva datas e sentidos e separa lotes sem enviar respostas',async()=>{
 const diretorio=await fs.mkdtemp(path.join(os.tmpdir(),'firma-historico-')),eventos=[];try{
 const h=new HistoricoWhatsApp({diretorio,publicar:async e=>eventos.push(e),textoMensagem:m=>m.message?.conversation});const canal='11111111-1111-1111-1111-111111111111';
 const mensagens=Array.from({length:105},(_,i)=>({key:{id:'mensagem-'+i,remoteJid:'123@lid',fromMe:i%2===0},messageTimestamp:1700000000+i,message:{conversation:'Histórico '+i},pushName:i%2===0?'Meu nome':'Marina'}));
 mensagens.push({key:{id:'grupo',remoteJid:'123@g.us'},messageTimestamp:1700000000,message:{conversation:'Grupo'}});
 await h.receber(canal,{contacts:[{id:'5511999990001@s.whatsapp.net',lid:'123@lid',name:'Marina'}],chats:[{id:'123@lid'}],messages:mensagens,isLatest:true,progress:100});
 assert.equal(eventos.filter(e=>e.raw.mensagens.length).length,3);assert.equal(eventos.flatMap(e=>e.raw.mensagens).length,105);assert.equal(eventos[0].raw.conversas[0].nome,'Marina');const m=eventos[1].raw.mensagens[0];assert.equal(m.telefone,'5511999990001');assert.equal(m.timestamp,1700000000);assert.equal(m.fromMe,true);assert.ok(eventos.every(e=>e.tipo==='historico'));assert.equal(eventos.at(-1).raw.ignoradas,1);
 const reaberto=new HistoricoWhatsApp({diretorio,publicar:async()=>{},textoMensagem:()=>''}),pedidos=[];await reaberto.pedir(canal,{user:{id:'me'},fetchMessageHistory:async(...a)=>pedidos.push(a)});await reaberto.trabalho;assert.equal(pedidos.length,1);assert.equal(pedidos[0][2],1700000000);assert.equal((await reaberto.status(canal)).recebidos,105);
 await assert.rejects(()=>reaberto.pedir(canal,null),/Conecte/);
 }finally{await fs.rm(diretorio,{recursive:true,force:true});}
});
test('não usa o nome do proprietário em contatos e identifica mídia indisponível',async()=>{
 const diretorio=await fs.mkdtemp(path.join(os.tmpdir(),'firma-historico-')),eventos=[];try{const h=new HistoricoWhatsApp({diretorio,publicar:async e=>eventos.push(e),textoMensagem:()=>null});await h.receber('canal',{messages:[{key:{id:'arquivo',remoteJid:'5511999990002@s.whatsapp.net',fromMe:true},pushName:'Proprietário',messageTimestamp:1700000000,message:{imageMessage:{}}}]});const m=eventos.flatMap(e=>e.raw.mensagens)[0];assert.equal(m.nome,'');assert.match(m.texto,/mídia não disponibilizada/);}finally{await fs.rm(diretorio,{recursive:true,force:true});}
});
