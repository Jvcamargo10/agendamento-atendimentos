package io.github.jvcamargo10.agendamento;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jvcamargo10.agendamento.dominio.Cpf;
import org.junit.jupiter.api.Test;

class CpfTest {

    @Test
    void validaDigitosVerificadores() {
        assertThat(Cpf.valido("529.982.247-25")).isTrue();
        assertThat(Cpf.valido("52998224725")).isTrue();
        assertThat(Cpf.valido("529.982.247-24")).isFalse();
        assertThat(Cpf.valido("111.111.111-11")).isFalse();
        assertThat(Cpf.valido("123")).isFalse();
    }

    @Test
    void hashNaoDependeDaFormatacaoEMudaComOSegredo() {
        assertThat(Cpf.hash("529.982.247-25", "s1")).isEqualTo(Cpf.hash("52998224725", "s1"));
        assertThat(Cpf.hash("52998224725", "s1")).isNotEqualTo(Cpf.hash("52998224725", "s2"));
    }

    @Test
    void mascaraMostraSoOsTresUltimosDigitos() {
        assertThat(Cpf.mascarar(Cpf.finais("529.982.247-25"))).isEqualTo("***.***.**7-25");
    }

    /** Gera CPFs válidos para os testes. */
    static String gerar(int semente) {
        String base = String.format("%09d", 100_000_000 + semente * 7919 % 800_000_000);
        int d1 = digito(base);
        int d2 = digito(base + d1);
        return base + d1 + d2;
    }

    private static int digito(String parcial) {
        int soma = 0;
        for (int i = 0; i < parcial.length(); i++) {
            soma += (parcial.charAt(i) - '0') * (parcial.length() + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }

    @Test
    void geradorProduzCpfsValidos() {
        for (int i = 1; i < 200; i++) {
            assertThat(Cpf.valido(gerar(i))).as(gerar(i)).isTrue();
        }
    }
}
