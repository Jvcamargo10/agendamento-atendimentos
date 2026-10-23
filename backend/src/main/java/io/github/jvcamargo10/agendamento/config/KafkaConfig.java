package io.github.jvcamargo10.agendamento.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    /** Três partições; a chave é o id da unidade, então os eventos de uma unidade ficam em ordem. */
    @Bean
    NewTopic topicoEventos(AgendaPropriedades propriedades) {
        return TopicBuilder.name(propriedades.topicoEventos()).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic topicoEventosMortos(AgendaPropriedades propriedades) {
        return TopicBuilder.name(propriedades.topicoEventos() + ".dlt").partitions(1).replicas(1).build();
    }

    /**
     * Uma mensagem que falha é tentada mais 3 vezes, com 1 segundo de intervalo. Se continuar falhando,
     * vai para o tópico ".dlt" em vez de travar a partição.
     */
    @Bean
    DefaultErrorHandler tratadorDeErros(KafkaTemplate<String, String> template) {
        var recuperador = new DeadLetterPublishingRecoverer(template,
                (registro, erro) -> new TopicPartition(registro.topic() + ".dlt", -1));
        return new DefaultErrorHandler(recuperador, new FixedBackOff(1000L, 3L));
    }
}
