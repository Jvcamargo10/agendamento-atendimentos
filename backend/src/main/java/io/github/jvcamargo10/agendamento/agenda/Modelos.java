package io.github.jvcamargo10.agendamento.agenda;

import io.github.jvcamargo10.agendamento.dominio.Status;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Tipos que a API expõe. Ficam juntos por serem pequenos e lidos sempre em conjunto. */
public final class Modelos {

    private Modelos() {
    }

    public record Unidade(int id, String nome, String endereco, String cidade, String uf) {
    }

    public record Servico(int id, String nome, int duracaoMinutos) {
    }

    public record Horario(long vagaId, OffsetDateTime inicio, int disponiveis) {
    }

    public record Agendamento(long id, String codigo, Status status, String cidadaoNome, String cpfMascarado,
                              String email, int unidadeId, int servicoId, long vagaId, OffsetDateTime inicio) {
    }

    public record PainelDia(int unidadeId, LocalDate dia, int agendados, int cancelados, int atendidos, int faltas) {
    }

    public record NovoAgendamento(
            @NotNull Long vagaId,
            @NotBlank @Size(min = 3, max = 120) String nome,
            @NotBlank String cpf,
            @NotBlank @Email String email) {
    }

    /** Vaga reservada: retorno do UPDATE condicional que ocupa um lugar no horário. */
    public record VagaReservada(long id, int unidadeId, int servicoId, OffsetDateTime inicio) {
    }
}
