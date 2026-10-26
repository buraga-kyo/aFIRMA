#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

void gerar_csv() {
    printf("titulo;data;responsavel;status;quantidadeAssinaturas;signatarios\n");
    const char *titulos[] = {
        "Contrato de Prestação de Serviços", "Proposta Comercial", "Termo de Confidencialidade",
        "Aditivo Contratual", "Autorização de Uso de Imagem", "Acordo de Parceria Comercial",
        "Distrato de Prestação de Serviços", "Termo de Posse e Responsabilidade"
    };
    const char *responsaveis[] = {
        "Alpha Engenharia Ltda", "Beta Soluções Digitais", "Silva & Associados",
        "Nexus Tecnologia", "Delta Logística", "Gama Participações",
        "Inova Consultoria", "Omega Serviços", "Prime Comunicação", "Horizonte Empreendimentos"
    };
    const char *status[] = {"Pendente", "Em Processo", "Assinado"};
    const char *pessoas[] = {
        "João da Silva", "Maria Oliveira", "Carlos Eduardo Souza", "Ana Paula Costa",
        "Lucas Mendes", "Mariana Rios", "Roberto Rocha", "Beatriz Lima",
        "Fernando Dias", "Patricia Gomes", "Gabriel Santos", "Camila Martins",
        "Rodrigo Alves", "Juliana Barbosa", "Rafael Ferreira"
    };
    
    srand(time(NULL));
    for (int i = 1; i <= 500; i++) {
        int t = rand() % 8;
        int r = rand() % 10;
        int s = rand() % 3;
        int q = (rand() % 4) + 1; // 1 a 4 signatarios
        
        printf("%s #%03d;25/10/2026;%s;%s;%d;", titulos[t], i, responsaveis[r], status[s], q);
        for(int j = 1; j <= q; j++) {
            int p = rand() % 15;
            printf("%s%s", pessoas[p], (j == q) ? "" : ",");
        }
        printf("\n");
    }
}

void gerar_sql() {
    const char *hex_pdf = "255044462d312e340a312030206f626a0a3c3c202f54797065202f436174616c6f67202f5061676573203220302052203e3e0a656e646f626a0a322030206f626a0a3c3c202f54797065202f5061676573202f4b696473205b33203020525d202f436f756e742031203e3e0a656e646f626a0a332030206f626a0a3c3c202f54797065202f50616765202f506172656e74203220302052202f4d65646961426f78205b30203020363132203739325d202f436f6e74656e7473203420302052202f5265736f7572636573203c3c202f466f6e74203c3c202f4631203520302052203e3e203e3e203e3e0a656e646f626a0a342030206f626a0a3c3c202f4c656e677468203633203e3e0a73747265616d0a4254202f46312031382054662035302037323020546420284341524741204445205445535445202d20614669726d6120417373696e6541692920546a2045540a656e6473747265616d0a656e646f626a0a352030206f626a0a3c3c202f54797065202f466f6e74202f53756274797065202f5479706531202f42617365466f6e74202f48656c766574696361203e3e0a656e646f626a0a787265660a3020360a303030303030303030302036353533352066200a303030303030303039203030303030206e200a303030303030303538203030303030206e200a303030303030313135203030303030206e200a303030303030323237203030303030206e200a303030303030333431203030303030206e200a747261696c65720a3c3c202f53697a652036202f526f6f74203120302052203e3e0a7374617274787265660a3433300a2525454f460a";

    printf("\\set ON_ERROR_STOP on\n");
    printf("BEGIN;\n");
    printf("DELETE FROM campo_assinatura WHERE signatario_id IN (\n");
    printf("    SELECT s.id FROM signatario_envelope s JOIN envelope_assinatura e ON e.id = s.envelope_id\n");
    printf("    WHERE e.criador = 'teste-local'\n");
    printf(");\n");
    printf("DELETE FROM signatario_envelope WHERE envelope_id IN (\n");
    printf("    SELECT id FROM envelope_assinatura WHERE criador = 'teste-local'\n");
    printf(");\n");
    printf("DELETE FROM envelope_assinatura WHERE criador = 'teste-local';\n");
    
    printf("CREATE TEMP TABLE carga_ids ON COMMIT DROP AS\n");
    printf("SELECT numero, gen_random_uuid() AS id FROM generate_series(1, 500) AS numero;\n");
    
    printf("INSERT INTO envelope_assinatura\n");
    printf("    (id, nome_empresa, nome_documento, criador, pdf_original, criado_em, expira_em, finalizado_em)\n");
    printf("SELECT id,\n");
    printf("       (ARRAY[\n");
    printf("           'Alpha Engenharia Ltda', 'Beta Soluções Digitais', 'Silva & Associados',\n");
    printf("           'Nexus Tecnologia', 'Delta Logística', 'Gama Participações',\n");
    printf("           'Inova Consultoria', 'Omega Serviços', 'Prime Comunicação', 'Horizonte Empreendimentos'\n");
    printf("       ])[((numero - 1) %% 10) + 1],\n");
    printf("       (ARRAY[\n");
    printf("           'Contrato de Prestação de Serviços', 'Proposta Comercial', 'Termo de Confidencialidade',\n");
    printf("           'Aditivo Contratual', 'Autorização de Uso de Imagem', 'Acordo de Parceria Comercial',\n");
    printf("           'Distrato de Prestação de Serviços', 'Termo de Posse e Responsabilidade'\n");
    printf("       ])[((numero - 1) %% 8) + 1] || ' #' || lpad(numero::text, 3, '0'),\n");
    printf("       'teste-local',\n");
    printf("       decode('%s', 'hex'),\n", hex_pdf);
    printf("       now() - (numero * interval '2 hours') - (((numero * 13) %% 24) * interval '1 hour'),\n");
    printf("       now() + interval '30 days',\n");
    printf("       CASE WHEN (numero %% 3 = 0) THEN now() - (numero * interval '2 hours') + interval '1 hour' ELSE NULL END\n");
    printf("FROM carga_ids;\n");

    printf("INSERT INTO signatario_envelope (id, envelope_id, nome, email, resumo_convite, assinado_em)\n");
    printf("SELECT gen_random_uuid(), c.id,\n");
    printf("       nomes.lista[((s.indice + c.numero * 3) %% 15) + 1],\n");
    printf("       'signatario-' || c.numero || '-' || s.indice || '@empresa.com',\n");
    printf("       encode(sha256(convert_to(c.id::text || ':' || s.indice::text, 'UTF8')), 'hex'),\n");
    printf("       CASE\n");
    printf("           WHEN (c.numero %% 3 = 0) THEN now() - (c.numero * interval '2 hours') + interval '30 minutes'\n");
    printf("           WHEN (c.numero %% 3 = 1 AND s.indice = 1) THEN now() - (c.numero * interval '2 hours') + interval '30 minutes'\n");
    printf("           ELSE NULL\n");
    printf("       END\n");
    printf("FROM carga_ids c\n");
    printf("CROSS JOIN (\n");
    printf("    SELECT ARRAY[\n");
    printf("        'João da Silva', 'Maria Oliveira', 'Carlos Eduardo Souza', 'Ana Paula Costa',\n");
    printf("        'Lucas Mendes', 'Mariana Rios', 'Roberto Rocha', 'Beatriz Lima',\n");
    printf("        'Fernando Dias', 'Patricia Gomes', 'Gabriel Santos', 'Camila Martins',\n");
    printf("        'Rodrigo Alves', 'Juliana Barbosa', 'Rafael Ferreira'\n");
    printf("    ] AS lista\n");
    printf(") nomes\n");
    printf("CROSS JOIN LATERAL generate_series(1, 1 + ((c.numero * 7) %% 4)) AS s(indice);\n");

    printf("DO $$ BEGIN\n");
    printf("    IF (SELECT count(*) FROM envelope_assinatura WHERE criador = 'teste-local') <> 500 THEN\n");
    printf("        RAISE EXCEPTION 'Carga incompleta';\n");
    printf("    END IF;\n");
    printf("END $$;\n");
    printf("COMMIT;\n");
    printf("SELECT count(*) AS documentos_teste FROM envelope_assinatura WHERE criador = 'teste-local';\n");
}

int main(int argc, char **argv) {
    if (argc < 2) {
        fprintf(stderr, "Uso:\n");
        fprintf(stderr, "  %s csv > importacao.csv\n", argv[0]);
        fprintf(stderr, "  %s sql > banco.sql\n", argv[0]);
        return 1;
    }
    
    if (strcmp(argv[1], "csv") == 0) {
        gerar_csv();
    } else if (strcmp(argv[1], "sql") == 0) {
        gerar_sql();
    } else {
        fprintf(stderr, "Argumento invalido. Use 'csv' ou 'sql'\n");
        return 1;
    }
    
    return 0;
}
