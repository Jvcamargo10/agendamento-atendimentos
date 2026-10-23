package io.github.jvcamargo10.agendamento.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param fuso                    fuso horário das unidades de atendimento
 * @param diasAFrente             quantos dias de agenda ficam abertos
 * @param antecedenciaCancelamento até quando antes do horário o cidadão pode cancelar
 * @param segredoCpf              chave do HMAC usado para guardar o CPF sem guardar o número
 * @param topicoEventos           tópico Kafka dos eventos de agendamento
 */
@ConfigurationProperties("agenda")
public record AgendaPropriedades(
        @DefaultValue("America/Sao_Paulo") String fuso,
        @DefaultValue("14") int diasAFrente,
        @DefaultValue("2h") Duration antecedenciaCancelamento,
        String segredoCpf,
        @DefaultValue("agendamentos.eventos") String topicoEventos) {
}
