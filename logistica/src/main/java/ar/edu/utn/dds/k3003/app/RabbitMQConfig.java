package ar.edu.utn.dds.k3003.app;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String COLA_DONACIONES = "donaciones_nuevas_queue";
    public static final String COLA_DLQ = "donaciones_nuevas_dlq";
    // Exchange compartido por los cuatro módulos para avisar sus cambios al Servicio de Consultas (CQRS).
    public static final String EXCHANGE_EVENTOS = "donatrack.eventos";

    @Bean
    public TopicExchange exchangeEventos() {
        return new TopicExchange(EXCHANGE_EVENTOS, true, false);
    }

    @Bean
    public Queue colaDonaciones() {
        return QueueBuilder.durable(COLA_DONACIONES)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", COLA_DLQ)
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(COLA_DLQ).build();
    }
}