package io.github.jvcamargo10.agendamento;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

/**
 * PostgreSQL e Kafka reais, em contêiner (Testcontainers). O relógio fica fixo numa segunda-feira às 07h,
 * então os horários gerados e as regras de antecedência são previsíveis.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ContainersDeTeste {

    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    public static final OffsetDateTime AGORA = OffsetDateTime.parse("2030-03-04T07:00:00-03:00");

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer("apache/kafka-native:3.8.0");
    }

    @Bean
    @Primary
    Clock relogioFixo() {
        return Clock.fixed(AGORA.toInstant(), FUSO);
    }
}
