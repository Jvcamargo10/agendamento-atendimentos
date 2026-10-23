package io.github.jvcamargo10.agendamento.dominio;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * O sistema nunca grava o CPF. Ele guarda:
 * - um HMAC-SHA256 (com chave secreta), para aplicar a regra de um agendamento por dia;
 * - os 3 últimos dígitos, para o atendente conferir o documento no balcão.
 */
public final class Cpf {

    private Cpf() {
    }

    public static String digitos(String cpf) {
        return cpf == null ? "" : cpf.replaceAll("\\D", "");
    }

    public static boolean valido(String cpf) {
        String d = digitos(cpf);
        if (d.length() != 11 || d.chars().distinct().count() == 1) {
            return false;
        }
        return d.charAt(9) - '0' == digitoVerificador(d, 9) && d.charAt(10) - '0' == digitoVerificador(d, 10);
    }

    static int digitoVerificador(String d, int posicao) {
        int soma = 0;
        for (int i = 0; i < posicao; i++) {
            soma += (d.charAt(i) - '0') * (posicao + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }

    public static String hash(String cpf, String segredo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(digitos(cpf).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC indisponível", e);
        }
    }

    public static String finais(String cpf) {
        return digitos(cpf).substring(8);
    }

    public static String mascarar(String finais) {
        return "***.***.**" + finais.charAt(0) + "-" + finais.substring(1);
    }
}
