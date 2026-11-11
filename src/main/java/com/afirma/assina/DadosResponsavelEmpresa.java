package com.afirma.assina;

import java.util.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class DadosResponsavelEmpresa {
    private final BancoAssineai banco;
    private final ObjectMapper json;
    public DadosResponsavelEmpresa(BancoAssineai banco, ObjectMapper json) { this.banco = banco; this.json = json; }

    public static String cnpj(String informado) {
        String texto = Objects.toString(informado, "").strip().toUpperCase(Locale.ROOT);
        if (texto.isEmpty()) return "";
        if (texto.length() > 18 || !texto.matches("(?:[A-Z0-9]{12}[0-9]{2}|[A-Z0-9]{2}\\.[A-Z0-9]{3}\\.[A-Z0-9]{3}/[A-Z0-9]{4}-[0-9]{2})"))
            throw new ErroAssinatura("CNPJ inválido: confira o formato");
        String numero = texto.replaceAll("[./-]", "");
        if (numero.chars().distinct().count() == 1) throw new ErroAssinatura("CNPJ inválido");
        // A conversão ASCII menos 48 atende também ao formato alfanumérico da Receita Federal.
        for (int tamanho = 12; tamanho <= 13; tamanho++) {
            int soma = 0, peso = 2;
            for (int indice = tamanho - 1; indice >= 0; indice--) {
                soma += (numero.charAt(indice) - 48) * peso;
                peso = peso == 9 ? 2 : peso + 1;
            }
            int resto = soma % 11, digito = resto < 2 ? 0 : 11 - resto;
            if (numero.charAt(tamanho) - '0' != digito) throw new ErroAssinatura("CNPJ inválido: confira os dígitos verificadores");
        }
        return numero;
    }
    public static Map<String, Object> normalizar(Map<String, Object> entrada) {
        var dados = new LinkedHashMap<String, Object>();
        for (String campo : List.of("EmpresaCNPJ", "EmpresaResponsavelNome", "EmpresaResponsavelCPF", "EmpresaResponsavelRG", "EmpresaResponsavelEmail", "EmpresaResponsavelCelular")) {
            if (!entrada.containsKey(campo)) continue;
            if (!(entrada.get(campo) instanceof String texto) || texto.chars().anyMatch(caractere -> caractere < 32 || caractere == 127))
                throw new ErroAssinatura("Dados do responsável inválidos");
            String valor = texto.strip();
            switch (campo) {
                case "EmpresaCNPJ" -> valor = cnpj(valor);
                case "EmpresaResponsavelCPF" -> valor = DadosPessoaisSignatario.cpf(valor);
                case "EmpresaResponsavelRG" -> valor = DadosPessoaisSignatario.rg(valor);
                case "EmpresaResponsavelCelular" -> valor = DadosPessoaisSignatario.whatsapp(valor);
                case "EmpresaResponsavelNome" -> { if (valor.length() > 160) throw new ErroAssinatura("Nome do responsável deve ter até 160 caracteres"); }
                case "EmpresaResponsavelEmail" -> {
                    if (valor.length() > 254 || !valor.isEmpty() && !valor.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new ErroAssinatura("E-mail do responsável inválido");
                }
            }
            dados.put(campo, valor);
        }
        return dados;
    }
    public static void exigirCadastro(Map<String, Object> dados) {
        for (String campo : List.of("EmpresaCNPJ", "EmpresaResponsavelNome", "EmpresaResponsavelCPF", "EmpresaResponsavelEmail", "EmpresaResponsavelCelular"))
            if (Objects.toString(dados.get(campo), "").isBlank()) throw new ErroAssinatura("Complete CNPJ e nome, CPF, e-mail e celular do responsável na configuração da empresa antes de assinar");
        normalizar(dados);
    }
    public Map<String, Object> preparar(long empresa, ColetaDadosAssinatura.Dados coleta, String dispositivo, String operador, String contexto) {
        var cadastro = banco.um("SELECT \"EmpresaNome\",\"EmpresaCNPJ\",\"EmpresaEmail\",\"EmpresaResponsavelNome\",\"EmpresaResponsavelCPF\",\"EmpresaResponsavelRG\",\"EmpresaResponsavelEmail\",\"EmpresaResponsavelCelular\" FROM \"Empresas\" WHERE \"EmpresaId\"=:id", Map.of("id", empresa));
        if (cadastro == null) throw new ErroAssinatura("Empresa não encontrada");
        cadastro.replaceAll((campo, valor) -> valor == null ? "" : valor);
        exigirCadastro(cadastro);
        if (coleta == null) throw new ErroAssinatura("Autorize a localização para registrar a assinatura da empresa");
        coleta.exigirRecente();
        var dados = new LinkedHashMap<String, Object>();
        dados.put("SignatarioNome", cadastro.get("EmpresaNome"));
        dados.put("SignatarioQualificacao", "Empresa");
        dados.put("SignatarioResponsavel", cadastro.get("EmpresaResponsavelNome"));
        dados.put("SignatarioCNPJ", cadastro.get("EmpresaCNPJ"));
        dados.put("SignatarioCPF", cadastro.get("EmpresaResponsavelCPF"));
        dados.put("SignatarioRG", Objects.toString(cadastro.get("EmpresaResponsavelRG"), ""));
        dados.put("SignatarioEmail", cadastro.get("EmpresaResponsavelEmail"));
        dados.put("SignatarioEmailEmpresa", Objects.toString(cadastro.get("EmpresaEmail"), ""));
        dados.put("SignatarioWhatsApp", cadastro.get("EmpresaResponsavelCelular"));
        dados.put("SignatarioIp", coleta.endereco()); dados.put("SignatarioIpConexao", coleta.ipConexao());
        dados.put("SignatarioOrigemIp", coleta.origemIp()); dados.put("SignatarioGeolocalizacao", json.writeValueAsString(coleta.localizacao()));
        dados.put("SignatarioDispositivo", dispositivo == null ? "" : dispositivo.substring(0, Math.min(dispositivo.length(), 1024)));
        dados.put("SignatarioContextoAcesso", contexto); dados.put("operador", Objects.toString(operador, ""));
        return dados;
    }
}
