package com.afirma.assina;

import java.net.InetAddress;
import java.time.Instant;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class ColetaDadosAssinatura {
    private final List<IpAddressMatcher> proxies;
    private final ObjectMapper json;
    public ColetaDadosAssinatura(@Value("${APP_PROXIES_CONFIAVEIS:}") String configurados, ObjectMapper json) {
        this.json = json;
        proxies = Arrays.stream(configurados.split(",")).map(String::trim).filter(valor -> !valor.isEmpty()).map(IpAddressMatcher::new).toList();
    }
    public Conexao conexao(HttpServletRequest requisicao) {
        String endereco = ip(requisicao.getRemoteAddr());
        String origem = "CONEXAO";
        if (confiavel(endereco)) {
            String cabecalho = requisicao.getHeader("X-Forwarded-For");
            if (cabecalho != null && !cabecalho.isBlank()) {
                if (cabecalho.length() > 2048) throw new ErroAssinatura("Cabeçalho de origem inválido");
                String[] cadeia = cabecalho.split(",", -1);
                if (cadeia.length > 20) throw new ErroAssinatura("Cabeçalho de origem inválido");
                for (int indice = cadeia.length - 1; indice >= 0 && confiavel(endereco); indice--) {
                    endereco = ip(cadeia[indice].trim()); origem = "PROXY_CONFIAVEL";
                }
            }
        }
        return new Conexao(endereco, origem, !publico(endereco));
    }
    private boolean confiavel(String endereco) { return proxies.stream().anyMatch(proxy -> proxy.matches(endereco)); }
    public Dados receberEmpresa(HttpServletRequest requisicao) {
        String localizacao = requisicao.getParameter("localizacaoEmpresa");
        if (localizacao == null || localizacao.isBlank()) return null;
        if (localizacao.length() > 2000) throw new ErroAssinatura("Localização da empresa inválida");
        return receber(requisicao, json.readValue(localizacao, Map.class), requisicao.getParameter("ipEmpresa"));
    }
    public Dados receber(HttpServletRequest requisicao, Object localizacao, String ipNavegador) {
        return receber(conexao(requisicao), localizacao, ipNavegador);
    }
    public Dados receber(String observado, Object localizacao, String ipNavegador) {
        String endereco = ip(observado);
        return receber(new Conexao(endereco, "CONEXAO", !publico(endereco)), localizacao, ipNavegador);
    }
    private Dados receber(Conexao conexao, Object objeto, String informado) {
        if (!(objeto instanceof Map<?, ?> valores)) throw new ErroAssinatura("Autorize a coleta da localização para assinar");
        double latitude = numero(valores.get("latitude")), longitude = numero(valores.get("longitude"));
        double precisao = numero(valores.get("accuracy"));
        double instante = numero(valores.get("timestamp"));
        if (Math.abs(latitude) > 90 || Math.abs(longitude) > 180 || precisao < 0 || precisao > 20000000
                || instante < 0 || instante > Long.MAX_VALUE || instante != Math.floor(instante)) throw new ErroAssinatura("Localização do navegador inválida");
        String navegador = informado == null || informado.isBlank() ? "" : ip(informado);
        if (conexao.exigirIpNavegador() && (navegador.isEmpty() || !publico(navegador))) throw new ErroAssinatura("Não foi possível obter o IP público do navegador. Tente novamente");
        String endereco = conexao.exigirIpNavegador() ? navegador : conexao.endereco();
        String origem = conexao.exigirIpNavegador() ? "NAVEGADOR_INFORMADO" : conexao.origem();
        return new Dados(endereco, conexao.endereco(), origem, navegador, latitude, longitude, precisao, Instant.ofEpochMilli((long) instante));
    }
    private double numero(Object valor) {
        if (!(valor instanceof Number numero) || !Double.isFinite(numero.doubleValue())) throw new ErroAssinatura("Localização do navegador incompleta");
        return numero.doubleValue();
    }
    public String serializar(Dados dados) { return json.writeValueAsString(dados.localizacao()); }
    public static String ip(String valor) {
        if (valor == null || valor.length() > 45) throw new ErroAssinatura("IP da conexão inválido");
        try {
            if (valor.contains(":")) {
                if (!valor.matches("[0-9a-fA-F:.]+")) throw new IllegalArgumentException();
            } else {
                if (!valor.matches("(?:0|[1-9][0-9]{0,2})(?:\\.(?:0|[1-9][0-9]{0,2})){3}")) throw new IllegalArgumentException();
                for (String parte : valor.split("\\.")) if (Integer.parseInt(parte) > 255) throw new IllegalArgumentException();
            }
            return InetAddress.getByName(valor).getHostAddress();
        } catch (Exception erro) { throw new ErroAssinatura("IP da conexão inválido"); }
    }
    private static boolean publico(String valor) {
        try {
            InetAddress endereco = InetAddress.getByName(ip(valor));
            if (endereco.isAnyLocalAddress() || endereco.isLoopbackAddress() || endereco.isLinkLocalAddress()
                    || endereco.isSiteLocalAddress() || endereco.isMulticastAddress()) return false;
            byte[] bytes = endereco.getAddress();
            if (bytes.length == 16) {
                if ((bytes[0] & 0xfe) == 0xfc) return false;
                return !((bytes[0] & 255) == 0x20 && (bytes[1] & 255) == 0x01
                        && (bytes[2] & 255) == 0x0d && (bytes[3] & 255) == 0xb8);
            }
            int primeiro = bytes[0] & 255, segundo = bytes[1] & 255, terceiro = bytes[2] & 255;
            if (primeiro == 0 || primeiro >= 240 || primeiro == 100 && segundo >= 64 && segundo <= 127) return false;
            if (primeiro == 192 && segundo == 0 && (terceiro == 0 || terceiro == 2)) return false;
            if (primeiro == 198 && (segundo == 18 || segundo == 19 || segundo == 51 && terceiro == 100)) return false;
            return !(primeiro == 203 && segundo == 0 && terceiro == 113);
        } catch (Exception erro) { return false; }
    }
    public static String descreverOrigem(String origem) {
        if (origem == null || origem.isBlank()) return "Não informada";
        return switch (origem) {
            case "CONEXAO" -> "Observado na conexão";
            case "PROXY_CONFIAVEL" -> "Encaminhado por proxy autorizado";
            case "NAVEGADOR_INFORMADO" -> "IP público informado pelo navegador";
            default -> origem;
        };
    }
    public static String descreverLocalizacao(String registro) {
        if (registro == null || registro.isBlank()) return "Não informada";
        try {
            var localizacao = new ObjectMapper().readTree(registro);
            if (localizacao.has("latitude")) {
                String instante = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss XXX")
                        .withZone(java.time.ZoneId.of("America/Sao_Paulo"))
                        .format(Instant.ofEpochMilli(localizacao.path("timestamp").asLong()));
                return "Latitude " + localizacao.path("latitude").asText() + "; longitude " + localizacao.path("longitude").asText()
                        + "; precisão " + localizacao.path("accuracy").asText() + " m; coletada em " + instante;
            }
            if ("Point".equals(localizacao.path("type").asText()) && localizacao.path("coordinates").size() == 2)
                return "Latitude " + localizacao.path("coordinates").get(1).asText() + "; longitude "
                        + localizacao.path("coordinates").get(0).asText() + "; precisão e horário não registrados no fluxo anterior";
        } catch (RuntimeException ignorado) { }
        return registro;
    }
    public record Conexao(String endereco, String origem, boolean exigirIpNavegador) { }
    public record Dados(String endereco, String ipConexao, String origemIp, String ipNavegador,
            double latitude, double longitude, double precisao, Instant coletadaEm) {
        public void exigirRecente() {
            Instant agora = Instant.now();
            if (coletadaEm.isBefore(agora.minusSeconds(300)) || coletadaEm.isAfter(agora.plusSeconds(30))) throw new ErroAssinatura("A localização expirou. Colete novamente antes de assinar");
        }
        public Map<String, Object> localizacao() { return Map.of("latitude", latitude, "longitude", longitude, "accuracy", precisao, "timestamp", coletadaEm.toEpochMilli()); }
        public Map<String, Object> pedido() { return Map.of("localizacao", localizacao(), "ipNavegador", ipNavegador); }
    }
}
