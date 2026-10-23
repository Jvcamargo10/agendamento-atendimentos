package io.github.jvcamargo10.agendamento.eventos;

import io.github.jvcamargo10.agendamento.agenda.AgendaRepositorio;
import io.github.jvcamargo10.agendamento.agenda.AgendaRepositorio.EventoPendente;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publica no Kafka os eventos gravados na outbox.
 *
 * Por que outbox: gravar no banco e enviar ao Kafka são duas operações em sistemas diferentes. Se o
 * serviço enviasse direto, um commit que falha depois do envio deixaria um evento "fantasma"; um envio
 * que falha depois do commit perderia o evento. Com a outbox, o evento só existe se a transação existiu,
 * e é reenviado até o Kafka confirmar. A entrega é "pelo menos uma vez": os consumidores são idempotentes.
 */
@Component
public class OutboxPublicador {

    private final AgendaRepositorio repositorio;
    private final KafkaTemplate<String, String> kafka;

    public OutboxPublicador(AgendaRepositorio repositorio, KafkaTemplate<String, String> kafka, MeterRegistry metricas) {
        this.repositorio = repositorio;
        this.kafka = kafka;
        Gauge.builder("outbox.pendentes", repositorio, AgendaRepositorio::eventosNaoPublicados)
                .description("Eventos gravados e ainda não confirmados pelo Kafka")
                .register(metricas);
    }

    @Scheduled(fixedDelayString = "${agenda.outbox.intervalo-ms:500}")
    @Transactional
    public void publicar() throws Exception {
        List<EventoPendente> pendentes = repositorio.eventosPendentes(100);
        if (pendentes.isEmpty()) {
            return;
        }
        List<CompletableFuture<?>> envios = new ArrayList<>();
        for (EventoPendente e : pendentes) {
            envios.add(kafka.send(e.topico(), e.chave(), e.payload()));
        }
        // Espera a confirmação do broker (acks=all). Se falhar, a transação desfaz e o lote é reenviado.
        CompletableFuture.allOf(envios.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
        List<UUID> ids = pendentes.stream().map(EventoPendente::id).toList();
        repositorio.marcarPublicados(ids);
    }
}
