package io.github.jvcamargo10.agendamento.eventos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jvcamargo10.agendamento.agenda.AgendaRepositorio;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mantém o painel diário de cada unidade a partir dos eventos (modelo de leitura, no estilo CQRS).
 * Idempotente: o id do evento é registrado na mesma transação da atualização do painel, então uma
 * mensagem entregue duas vezes conta uma vez só.
 */
@Component
public class PainelConsumidor {

    private static final Logger log = LoggerFactory.getLogger(PainelConsumidor.class);
    private static final String CONSUMIDOR = "painel";

    private final AgendaRepositorio repositorio;
    private final ObjectMapper json;

    public PainelConsumidor(AgendaRepositorio repositorio, ObjectMapper json) {
        this.repositorio = repositorio;
        this.json = json;
    }

    @KafkaListener(topics = "${agenda.topico-eventos:agendamentos.eventos}", groupId = "painel")
    @Transactional
    public void receber(String mensagem) throws Exception {
        JsonNode evento = json.readTree(mensagem);
        UUID id = UUID.fromString(evento.get("id").asText());
        if (!repositorio.registrarProcessamento(CONSUMIDOR, id)) {
            log.info("Evento {} já processado; ignorando", id);
            return;
        }
        int unidade = evento.get("unidadeId").asInt();
        LocalDate dia = LocalDate.parse(evento.get("dia").asText());
        switch (evento.get("tipo").asText()) {
            case "AGENDAMENTO_CRIADO" -> repositorio.somarNoPainel(unidade, dia, 1, 0, 0, 0);
            case "AGENDAMENTO_CANCELADO" -> repositorio.somarNoPainel(unidade, dia, -1, 1, 0, 0);
            case "CIDADAO_ATENDIDO" -> repositorio.somarNoPainel(unidade, dia, 0, 0, 1, 0);
            case "CIDADAO_FALTOU" -> repositorio.somarNoPainel(unidade, dia, 0, 0, 0, 1);
            default -> log.warn("Tipo de evento desconhecido: {}", evento.get("tipo").asText());
        }
    }
}
