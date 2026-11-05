// SMTP de teste em loopback: guarda as mensagens na memória e nunca as encaminha.
const net = require('node:net'),
  servidorHttp = require('node:http'),
  mensagens = [];
net.createServer(socket => {
  socket.write('220 localhost FIRMA teste\r\n');
  let buffer = '',
    data = false,
    corpo = [];
  socket.on('data', chunk => {
    buffer += chunk.toString();
    for (let indice; (indice = buffer.indexOf('\r\n')) >= 0;) {
      const line = buffer.slice(0, indice);
      buffer = buffer.slice(indice + 2);
      if (data) {
        if (line === '.') {
          mensagens.push(corpo.join('\n'));
          corpo = [];
          data = false;
          socket.write('250 Recebida somente no teste\r\n');
        } else corpo.push(line);
        continue;
      }
      const cmd = line.split(' ')[0].toUpperCase();
      if (cmd === 'EHLO' || cmd === 'HELO') socket.write('250-localhost\r\n250 8BITMIME\r\n');else if (cmd === 'DATA') {
        data = true;
        socket.write('354 Envie o corpo\r\n');
      } else if (cmd === 'QUIT') socket.end('221 Encerrado\r\n');else socket.write('250 OK\r\n');
    }
  });
}).listen(18092, '127.0.0.1');
servidorHttp.createServer((requisicao, resposta) => {
  resposta.setHeader('Content-Type', 'application/json');
  resposta.end(JSON.stringify(mensagens));
}).listen(18093, '127.0.0.1', () => console.log('SMTP simulado local em 18092; diário local em 18093'));
