package io.github.jvcamargo10.agendamento.agenda;

import io.github.jvcamargo10.agendamento.config.AgendaPropriedades;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Mantém a agenda aberta para os próximos dias úteis: 08h às 16h30, a cada 30 minutos. */
@Component
public class GeradorDeVagas {

    private static final Logger log = LoggerFactory.getLogger(GeradorDeVagas.class);

    private final AgendaRepositorio repositorio;
    private final AgendaPropriedades propriedades;
    private final Clock relogio;

    public GeradorDeVagas(AgendaRepositorio repositorio, AgendaPropriedades propriedades, Clock relogio) {
        this.repositorio = repositorio;
        this.propriedades = propriedades;
        this.relogio = relogio;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 0 1 * * *", zone = "${agenda.fuso:America/Sao_Paulo}")
    public void gerar() {
        int criadas = repositorio.gerarVagas(LocalDate.now(relogio), propriedades.diasAFrente(), propriedades.fuso());
        log.info("Agenda atualizada: {} horários novos", criadas);
    }
}
