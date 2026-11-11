package com.afirma.assina;

import java.util.ArrayList;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ControleProntidaoAssinatura {
	private final Environment ambiente;
	private final SegredosAssinatura segredos;
	private final EnviadorCodigoConvite enviador;
	private final PoliticaCertificados certificados;
	private final CarimboTempo carimbo;
	private final JdbcTemplate banco;

	public ControleProntidaoAssinatura(Environment ambiente, SegredosAssinatura segredos, EnviadorCodigoConvite enviador,
			PoliticaCertificados certificados, CarimboTempo carimbo, JdbcTemplate banco) {
		this.ambiente = ambiente; this.segredos = segredos; this.enviador = enviador;
		this.certificados = certificados; this.carimbo = carimbo; this.banco = banco;
	}

	@GetMapping("/api/envelopes/prontidao")
	public Diagnostico consultar() {
		boolean local = ambiente.matchesProfiles("local-test");
		var itens = new ArrayList<Item>();
		itens.add(new Item("Proteção dos eventos e dos códigos", segredos.configurado() ? "CONFIGURADO" : "PENDENTE_CONFIGURACAO",
				"As chaves antigas precisam ser preservadas para conferir eventos anteriores à troca de chave."));
		itens.add(new Item("Entrega dos códigos", enviador.configurado() ? "CONFIGURADO_SEM_TESTE_DE_ENTREGA_EXTERNA" : "PENDENTE_CONFIGURACAO",
				"Confirme entrega, remetente e proteção do e-mail no ambiente de publicação."));
		boolean origem = !ambiente.getProperty("ASSINEAI_URL_PUBLICA", "").isBlank();
		itens.add(new Item("Origem da aplicação", origem ? "CONFIGURADO" : local ? "SOMENTE_TESTE_LOCAL" : "PENDENTE_CONFIGURACAO",
				"Publicação exige origem HTTPS e cookies seguros. O perfil local-test deve permanecer restrito ao computador."));
		Boolean superusuario = banco.queryForObject("SELECT rolsuper FROM pg_roles WHERE rolname=current_user", Boolean.class);
		Integer tabelasProprias = banco.queryForObject("""
				SELECT count(*) FROM pg_class tabela JOIN pg_namespace esquema ON esquema.oid=tabela.relnamespace
				WHERE esquema.nspname=current_schema() AND tabela.relname IN ('evento_assinatura','arquivo_evidencia_assinatura')
				AND pg_has_role(current_user,tabela.relowner,'MEMBER')
				""", Integer.class);
		boolean privilegioAmplo = Boolean.TRUE.equals(superusuario) || tabelasProprias != null && tabelasProprias > 0;
		itens.add(new Item("Privilégios do banco", privilegioAmplo ? local ? "SOMENTE_TESTE_LOCAL" : "PENDENTE_OPERACAO" : "SEM_SUPERUSUARIO_OU_PROPRIEDADE_DAS_TABELAS",
				"Use usuário de aplicação sem poderes de superusuário ou propriedade das tabelas. Execute migrações com um papel separado e revise outros privilégios."));
		Integer protecoes = banco.queryForObject("""
				SELECT count(*) FROM pg_trigger WHERE tgname IN ('proteger_evento_assinatura','proteger_arquivo_evidencia',
				'proteger_evento_assinatura_truncate','proteger_arquivo_evidencia_truncate') AND tgenabled<>'D'
				""", Integer.class);
		itens.add(new Item("Proteção das tabelas de evidências", protecoes != null && protecoes == 4 ? "ATIVA_NO_BANCO" : "PENDENTE_OPERACAO",
				"A proteção da aplicação não impede um administrador do banco ou do armazenamento de remover dados. Preserve cópias independentes."));
		itens.add(new Item("Confiança no certificado", certificados.raizesConfiguradas() ? "AUTORIDADES_CONFIGURADAS" : "NAO_CONFIGURADA",
				"Configurar uma autoridade não comprova que ela integra a ICP-Brasil. Confirme a origem das autoridades e o certificado utilizado."));
		itens.add(new Item("Revogação", certificados.consultaRevogacao() ? "CONSULTA_HABILITADA" : "NAO_HABILITADA",
				"O resultado de cada assinatura informa se houve consulta. Esta versão não produz validação de longo prazo PAdES-LT/LTA."));
		itens.add(new Item("Carimbo do tempo", carimbo.ativo() ? "HABILITADO" : "NAO_HABILITADO",
				"Sem autoridade de tempo, os horários são declarados pelo servidor. Mantenha o relógio sincronizado."));
		itens.add(new Item("Classificação jurídica e conformidade ICP-Brasil", "EXIGE_AVALIACAO_EXTERNA",
				"O formato CMS com referência ao certificado e a conferência interna não homologam políticas ICP-Brasil RB/RT nem tornam o aceite por e-mail uma assinatura qualificada."));
		return new Diagnostico("Diagnóstico operacional, sem certificação jurídica ou homologação externa", itens);
	}
	public record Item(String superficie, String estado, String observacao) { }
	public record Diagnostico(String finalidade, List<Item> itens) { }
}
