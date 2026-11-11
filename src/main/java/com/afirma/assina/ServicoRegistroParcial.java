package com.afirma.assina;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class ServicoRegistroParcial {
    @org.springframework.beans.factory.annotation.Value("${ASSINEAI_URL_PUBLICA:}")
    private String origemPublica;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.core.env.Environment ambiente;
    @org.springframework.beans.factory.annotation.Autowired
    private DadosResponsavelEmpresa responsavelEmpresa;
    private final PaginaAssinaturaEmpresa paginas;
    private final CertificadoConfiguradoEmpresa certificados;
    private final ServicoAssinaturaPdf assinador;
    private final ServicoCamposPdf campos;
    private final ServicoEvidenciasAssinatura evidencias;
    private final ObjectMapper json;
    private final boolean permitirVencido;
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm:ss")
            .withZone(ZoneId.of("America/Sao_Paulo"));

    public ServicoRegistroParcial(PaginaAssinaturaEmpresa paginas, CertificadoConfiguradoEmpresa certificados,
            ServicoAssinaturaPdf assinador, ServicoCamposPdf campos, ServicoEvidenciasAssinatura evidencias,
            ObjectMapper json, @Value("${APP_PERMITIR_CERTIFICADO_VENCIDO:false}") boolean permitirVencido) {
        this.paginas = paginas; this.certificados = certificados; this.assinador = assinador;
        this.campos = campos; this.evidencias = evidencias; this.json = json; this.permitirVencido = permitirVencido;
    }

    public void configurar(EnvelopeAssinatura envelope, String criador) {
        configurar(envelope, criador, true);
    }

    public void configurar(EnvelopeAssinatura envelope, String criador, boolean empresaParticipante) {
        var empresa = paginas.doUsuarioAtual();
        if (empresa == null) empresa = paginas.daIdentidade(criador);
        if (empresa != null && !empresa.nomeEmpresa().equals(envelope.getNomeEmpresa())) {
            throw new ErroAssinatura("A empresa do documento deve corresponder à empresa conectada");
        }
        var configuracao = new LinkedHashMap<String, Object>();
        configuracao.put("empresaParticipante", empresaParticipante);
        configuracao.put("empresaId", empresa == null ? 0 : empresa.empresaId());
        configuracao.put("automatica", empresa != null && empresa.assinaturaAutomatica());
        configuracao.put("modelo", empresa == null ? TemplatesEmpresa.padrao() : empresa.modelo());
        String documento = json.writeValueAsString(configuracao);
        envelope.configurarRegistroAssinatura(documento);
        evidencias.guardarArquivo(envelope.getId(), null, "CONFIGURACAO_REGISTRO", "configuracao-registro.json", "application/json",
                documento.getBytes(java.nio.charset.StandardCharsets.UTF_8), null);
    }

    private Map<String, Object> configuracao(EnvelopeAssinatura envelope) {
        var arquivo = evidencias.arquivos(envelope.getId()).stream().filter(item -> "CONFIGURACAO_REGISTRO".equals(item.tipo())).findFirst();
        if (arquivo.isPresent()) return json.readValue(evidencias.lerArquivo(envelope.getId(), arquivo.get()), Map.class);
        if (envelope.getConfiguracaoRegistroAssinatura() != null)
            throw new ErroAssinatura("A configuração preservada da assinatura não foi encontrada");
        return Map.of("empresaId", 0, "automatica", false, "modelo", TemplatesEmpresa.padrao());
    }

    public boolean restaurarCriacao(EnvelopeAssinatura envelope) {
        var arquivos = evidencias.arquivos(envelope.getId());
        var configuracao = arquivos.stream().filter(arquivo -> "CONFIGURACAO_REGISTRO".equals(arquivo.tipo())).findFirst();
        if (configuracao.isEmpty()) return false;
        evidencias.conferir(envelope.getId());
        boolean restaurado = false;
        if (envelope.getConfiguracaoRegistroAssinatura() == null) {
            envelope.configurarRegistroAssinatura(new String(evidencias.lerArquivo(envelope.getId(), configuracao.get()), java.nio.charset.StandardCharsets.UTF_8));
            restaurado = true;
        }
        if (envelope.getEmpresaAssinouEm() == null) {
            var base = arquivos.stream().filter(arquivo -> "PDF_BASE_EMPRESA".equals(arquivo.tipo())).findFirst();
            if (base.isPresent()) {
                var registros = evidencias.eventos(envelope.getId()).stream().filter(evento -> ("EMPRESA_ASSINOU".equals(evento.tipo()) || "SELO_CERTIFICADO_APLICADO".equals(evento.tipo()))).toList();
                if (registros.size() != 1) throw new ErroAssinatura("A assinatura preservada da empresa precisa ter um registro único");
                var informacoes = json.readTree(registros.getFirst().conteudo()).path("informacoes");
                if (!SegredosAssinatura.resumo(evidencias.lerArquivo(envelope.getId(), base.get())).equals(informacoes.path("resumoPdf").asText()))
                    throw new ErroAssinatura("A assinatura da empresa diverge das provas preservadas");
                envelope.registrarAssinaturaEmpresa(Instant.parse(informacoes.path("assinadoEm").asText()),
                        informacoes.path("titular").asText(), informacoes.path("serie").asText());
                restaurado = true;
            }
        }
        if (!envelope.temPdfAtual()) {
            var parciais = arquivos.stream().filter(arquivo -> "PDF_ATUAL".equals(arquivo.tipo())).toList();
            if (!parciais.isEmpty()) {
                envelope.atualizarPdf(evidencias.lerArquivo(envelope.getId(), parciais.getLast()));
                restaurado = true;
            }
        }
        if (restaurado) evidencias.registrar(envelope.getId(), null, "ESTADO_CRIACAO_RECUPERADO",
                Map.of("origem", "Configuração, assinatura da empresa e PDF conferidos nas provas preservadas"));
        return restaurado;
    }

    private String autenticidade(EnvelopeAssinatura envelope) {
        String origem = Objects.toString(origemPublica, "").replaceAll("/+$", "");
        if (origem.isBlank() && ambiente != null && ambiente.matchesProfiles("local-test"))
            origem = "http://127.0.0.1:" + ambiente.getProperty("server.port", "8080");
        return origem.isBlank() ? "" : origem + "/web-panels/autenticidade-assinatura/" + envelope.getId();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> modelo(EnvelopeAssinatura envelope) {
        return (Map<String, Object>) configuracao(envelope).get("modelo");
    }

    public boolean participa(EnvelopeAssinatura envelope) {
        return !Boolean.FALSE.equals(configuracao(envelope).get("empresaParticipante"));
    }

    public boolean automatica(EnvelopeAssinatura envelope) {
        return !participa(envelope) || Boolean.TRUE.equals(configuracao(envelope).get("automatica"));
    }

    public boolean podeConcluirAutomaticamente(EnvelopeAssinatura envelope) {
        return automatica(envelope) || envelope.getEmpresaAssinouEm() != null;
    }

    public CertificadoConfiguradoEmpresa.Material carregarCertificado(EnvelopeAssinatura envelope) {
        long empresa = ((Number) configuracao(envelope).get("empresaId")).longValue();
        return certificados.carregar(empresa);
    }

    public void assinarConfigurado(EnvelopeAssinatura envelope, byte[] original) throws Exception {
        assinarConfigurado(envelope, original, null, null, envelope.getCriador(), "Assinatura da empresa");
    }

    public void assinarConfigurado(EnvelopeAssinatura envelope, byte[] original, ColetaDadosAssinatura.Dados coleta,
            String dispositivo, String operador, String contexto) throws Exception {
        if (envelope.getEmpresaAssinouEm() != null) return;
        try (var material = carregarCertificado(envelope)) {
            assinarEmpresa(envelope, original, material.dados(), material.senha(), coleta, dispositivo, operador, contexto);
        }
    }

    public void assinarEmpresa(EnvelopeAssinatura envelope, byte[] original, byte[] certificado, char[] senha) throws Exception {
        assinarEmpresa(envelope, original, certificado, senha, null, null, envelope.getCriador(), "Assinatura da empresa");
    }

    public void assinarEmpresa(EnvelopeAssinatura envelope, byte[] original, byte[] certificado, char[] senha,
            ColetaDadosAssinatura.Dados coleta, String dispositivo, String operador, String contexto) throws Exception {
        if (envelope.getEmpresaAssinouEm() != null) return;
        long empresa = ((Number) configuracao(envelope).get("empresaId")).longValue();
        var dadosResponsavel = participa(envelope) ? responsavelEmpresa.preparar(empresa, coleta, dispositivo, operador, contexto) : Map.<String, Object>of();
        evidencias.conferirConteudoRegistrado(envelope.getId(), original, "PDF_ORIGINAL");
        var titular = assinador.consultarCertificado(certificado, senha, permitirVencido);
        byte[] base = assinador.assinar(original, certificado, senha, permitirVencido);
        envelope.registrarAssinaturaEmpresa(Instant.now(), titular.titular(), titular.numeroSerie());
        evidencias.guardarArquivo(envelope.getId(), null, "PDF_BASE_EMPRESA", "documento-assinado-empresa.pdf", "application/pdf", base, null);
        evidencias.registrar(envelope.getId(), null, participa(envelope) ? "EMPRESA_ASSINOU" : "SELO_CERTIFICADO_APLICADO", Map.of("titular", titular.titular(),
                "serie", titular.numeroSerie(), "resumoPdf", SegredosAssinatura.resumo(base),
                "escopo", "PDF original; os aceites posteriores são preservados em novas versões",
                "assinadoEm", envelope.getEmpresaAssinouEm().toString(), "dadosResponsavel", dadosResponsavel));
    }

    public byte[] gerar(EnvelopeAssinatura envelope, byte[] original, List<ServicoCamposPdf.Marca> marcas,
            List<SignatarioEnvelope> participantes) throws Exception {
        evidencias.conferirConteudoRegistrado(envelope.getId(), original, "PDF_ORIGINAL");
        byte[] base = original;
        if (envelope.getEmpresaAssinouEm() != null) {
            var arquivo = evidencias.arquivos(envelope.getId()).stream().filter(item -> "PDF_BASE_EMPRESA".equals(item.tipo())).findFirst()
                    .orElseThrow(() -> new ErroAssinatura("A assinatura preservada da empresa não foi encontrada"));
            base = evidencias.lerArquivo(envelope.getId(), arquivo);
        }
        byte[] marcado = marcas.isEmpty() ? base : campos.aplicar(base, marcas);
        var pessoas = new ArrayList<Map<String, Object>>();
        var empresa = new LinkedHashMap<String, Object>();
        if (envelope.getEmpresaAssinouEm() != null) {
            var assinatura = evidencias.eventos(envelope.getId()).stream().filter(evento -> "EMPRESA_ASSINOU".equals(evento.tipo())).findFirst();
            if (assinatura.isPresent()) {
                var dados = json.readTree(assinatura.get().conteudo()).path("informacoes").path("dadosResponsavel");
                if (dados.isObject()) empresa.putAll(json.readValue(dados.toString(), Map.class));
            }
        }
        empresa.remove("operador");
        empresa.put("SignatarioNome", envelope.getNomeEmpresa());
        empresa.put("SignatarioQualificacao", "Empresa");
        empresa.put("SignatarioAssinou", envelope.getEmpresaAssinouEm() != null);
        empresa.put("SignatarioDataAssinatura", data(envelope.getEmpresaAssinouEm()));
        empresa.put("SignatarioFirmadoVia", envelope.getEmpresaAssinouEm() == null ? "Aguardando assinatura da empresa"
                : "Certificado digital");
        if (participa(envelope)) pessoas.add(empresa);
        for (var participante : participantes) {
            var pessoa = new LinkedHashMap<String, Object>();
            pessoa.put("SignatarioNome", participante.getNome());
            pessoa.put("SignatarioQualificacao", Objects.toString(participante.getQualificacao(), "Signatário"));
            pessoa.put("SignatarioAssinou", participante.getAssinadoEm() != null);
            pessoa.put("SignatarioDataAssinatura", data(participante.getAssinadoEm()));
            pessoa.put("SignatarioEmail", participante.getEmail());
            pessoa.put("SignatarioFirmadoVia", participante.getAssinadoEm() == null ? "Pendente" : "Aceite do documento e confirmação do e-mail");
            if (participante.getAssinatura() != null) pessoa.put("SignatarioAssinaturaEscritaBase64",
                    "data:image/png;base64," + Base64.getEncoder().encodeToString(participante.getAssinatura()));
            if (participante.getSelfie() != null) pessoa.put("SignatarioSelfieBase64",
                    "data:image/png;base64," + Base64.getEncoder().encodeToString(participante.getSelfie()));
            pessoa.put("SignatarioRepresentado", Objects.toString(participante.getRepresentado(), ""));
            pessoa.put("SignatarioFundamentoRepresentacao", Objects.toString(participante.getFundamentoRepresentacao(), ""));
            pessoa.put("SignatarioCPF", Objects.toString(participante.getCpf(), ""));
            pessoa.put("SignatarioRG", Objects.toString(participante.getRg(), ""));
            pessoa.put("SignatarioWhatsApp", Objects.toString(participante.getWhatsapp(), ""));
            pessoa.put("SignatarioDispositivo", Objects.toString(participante.getDispositivo(), ""));
            pessoa.put("SignatarioGeolocalizacao", Objects.toString(participante.getGeolocalizacao(), ""));
            pessoa.put("SignatarioIpConexao", Objects.toString(participante.getIpConexao(), ""));
            pessoa.put("SignatarioOrigemIp", Objects.toString(participante.getOrigemIp(), ""));
            pessoa.put("SignatarioIp", Objects.toString(participante.getEnderecoIp(), ""));
            pessoas.add(pessoa);
        }
        return PaginaRegistroAssinaturas.acrescentar(marcado, (Map<String, Object>) configuracao(envelope).get("modelo"),
                new PaginaRegistroAssinaturas.Dados(envelope.getNomeEmpresa(), envelope.getNomeDocumento(),
                        envelope.getId().toString(), SegredosAssinatura.resumo(original), autenticidade(envelope), data(envelope.getCriadoEm()), pessoas));
    }

    private String data(Instant momento) { return momento == null ? "Pendente" : DATA.format(momento); }
}
