package io.github.jvcamargo10.agendamento;

import io.github.jvcamargo10.agendamento.config.AgendaPropriedades;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class AgendamentoApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgendamentoApplication.class, args);
    }

    /** Todas as regras de horário usam este relógio, e não o do banco: assim os testes controlam o tempo. */
    @Bean
    Clock relogio(AgendaPropriedades propriedades) {
        return Clock.system(ZoneId.of(propriedades.fuso()));
    }
}
