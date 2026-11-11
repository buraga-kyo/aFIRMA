package com.afirma.assina;

import java.util.Set;
import java.util.Locale;

/** Normaliza somente pontuação conhecida; caracteres inesperados são recusados. */
public final class DadosPessoaisSignatario {
    private static final Set<String> DDDS = Set.of(
        "11","12","13","14","15","16","17","18","19","21","22","24","27","28",
        "31","32","33","34","35","37","38","41","42","43","44","45","46","47","48","49",
        "51","53","54","55","61","62","63","64","65","66","67","68","69","71","73","74","75","77","79",
        "81","82","83","84","85","86","87","88","89","91","92","93","94","95","96","97","98","99");
    private DadosPessoaisSignatario() { }

    public static String cpf(String informado) {
        String texto = entrada(informado, 14, "CPF");
        if (texto.isEmpty()) return "";
        if (!texto.matches("(?:[0-9]{11}|[0-9]{3}\\.[0-9]{3}\\.[0-9]{3}-[0-9]{2})")) throw invalido("CPF", "use 11 dígitos");
        String numeros = texto.replace(".", "").replace("-", "");
        if (numeros.chars().distinct().count() == 1) throw invalido("CPF", "dígitos verificadores incorretos");
        for (int quantidade = 9; quantidade <= 10; quantidade++) {
            int soma = 0;
            for (int indice = 0; indice < quantidade; indice++) soma += (numeros.charAt(indice) - '0') * (quantidade + 1 - indice);
            int digito = 11 - soma % 11;
            if (digito >= 10) digito = 0;
            if (digito != numeros.charAt(quantidade) - '0') throw invalido("CPF", "dígitos verificadores incorretos");
        }
        return numeros;
    }

    public static String rg(String informado) {
        String texto = entrada(informado, 24, "RG");
        if (texto.isEmpty()) return "";
        if (!texto.matches("[A-Za-z0-9. -]+")) throw invalido("RG", "use apenas o número, seu prefixo e os separadores do documento");
        String numero = texto.toUpperCase(Locale.ROOT).replace(".", "").replace("-", "").replace(" ", "");
        // Sem UF e órgão emissor não existe um cálculo único de dígito verificador para o RG.
        if (!numero.matches("[A-Z]{0,2}[0-9]{5,12}X?") || numero.matches("[A-Z]{0,2}0+X?")) {
            throw invalido("RG", "informe de 5 a 12 dígitos, com prefixo quando existir e X apenas no final");
        }
        return numero;
    }

    public static String whatsapp(String informado) {
        String texto = entrada(informado, 32, "WhatsApp");
        if (texto.isEmpty()) return "";
        if (!texto.matches("\\+?[0-9 ()-]+") || texto.chars().noneMatch(Character::isDigit)) {
            throw invalido("WhatsApp", "use DDD e número; para outro país, comece com + e o código do país");
        }
        if (texto.indexOf('(') >= 0 && !texto.matches("\\+?[0-9 ]*\\([0-9]{2}\\)[0-9 -]+")) {
            throw invalido("WhatsApp", "parênteses devem envolver somente o DDD");
        }
        if (texto.indexOf('(') < 0 && texto.indexOf(')') >= 0) throw invalido("WhatsApp", "DDD malformado");
        String numeros = texto.replaceAll("[+ ()-]", "");
        boolean internacional = texto.startsWith("+");
        if (internacional && !numeros.startsWith("55")) {
            if (!numeros.matches("[1-9][0-9]{7,14}")) throw invalido("WhatsApp", "o formato internacional deve ter entre 8 e 15 dígitos");
            return "+" + numeros;
        }
        if ((internacional || numeros.length() > 11) && numeros.startsWith("55")) numeros = numeros.substring(2);
        if (!numeros.matches("[0-9]{10,11}") || !DDDS.contains(numeros.substring(0, 2))) throw invalido("WhatsApp", "informe um DDD brasileiro válido e o número completo");
        String assinante = numeros.substring(2);
        if (!(assinante.matches("[2-5][0-9]{7}") || assinante.matches("9[0-9]{8}"))) {
            throw invalido("WhatsApp", "celular deve começar com 9 e ter nove dígitos; fixo deve ter oito");
        }
        return "+55" + numeros;
    }

    private static String entrada(String informado, int limite, String campo) {
        if (informado == null) return "";
        if (informado.length() > limite || informado.chars().anyMatch(c -> c < 32 || c == 127)) throw invalido(campo, "tamanho ou caracteres inválidos");
        return informado.trim();
    }
    private static ErroAssinatura invalido(String campo, String motivo) { return new ErroAssinatura(campo + " inválido: " + motivo); }
}
