window.coletarDadosAssinatura = async function (enderecoColeta) {
  if (!window.isSecureContext || !navigator.geolocation)
    throw new Error('A localização exige HTTPS ou acesso por localhost.');
  const resposta = await fetch(enderecoColeta, { cache: 'no-store' });
  if (!resposta.ok) throw new Error('Não foi possível confirmar a origem do acesso. Reabra o convite.');
  const conexao = await resposta.json();
  const posicao = await new Promise((concluir, rejeitar) => {
    navigator.geolocation.getCurrentPosition(concluir, falha => rejeitar(new Error(
      falha.code === 1 ? 'Autorize o acesso à localização no navegador para assinar.' :
      'Não foi possível obter a localização. Verifique o serviço de localização e tente novamente.'
    )), { enableHighAccuracy: true, maximumAge: 0, timeout: 20000 });
  });
  let ipNavegador = '';
  if (conexao.exigirIpNavegador) {
    try {
      // A consulta parte do navegador para registrar o IP público de quem assina no acesso local.
      const respostaIp = await fetch('https://api64.ipify.org?format=json', {
        signal: AbortSignal.timeout(10000), credentials: 'omit', referrerPolicy: 'no-referrer', cache: 'no-store'
      });
      if (!respostaIp.ok) throw new Error();
      ipNavegador = (await respostaIp.json()).ip;
      if (typeof ipNavegador !== 'string' || ipNavegador.length > 45) throw new Error();
    } catch (_) { throw new Error('Não foi possível obter o IP público do navegador. Verifique a conexão e tente novamente.'); }
  }
  return { ipNavegador, localizacao: {
    latitude: posicao.coords.latitude, longitude: posicao.coords.longitude,
    accuracy: posicao.coords.accuracy, timestamp: posicao.timestamp
  } };
};
