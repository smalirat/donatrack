package ar.edu.utn.dds.k3003.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Los cuatro módulos publican en el exchange; este servicio es el único que tiene una cola atada a él.
@Configuration
public class RabbitMQConfig {

  public static final String EXCHANGE_EVENTOS = "donatrack.eventos";
  public static final String COLA_EVENTOS = "consultas.eventos";
  public static final String COLA_DLQ = "consultas.eventos.dlq";

  // Mismos argumentos con los que lo declara Logística: si difieren, RabbitMQ rechaza la declaración.
  @Bean
  public TopicExchange exchangeEventos() {
    return new TopicExchange(EXCHANGE_EVENTOS, true, false);
  }

  @Bean
  public Queue colaEventos() {
    return QueueBuilder.durable(COLA_EVENTOS)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", COLA_DLQ)
            .build();
  }

  @Bean
  public Queue colaEventosDlq() {
    return QueueBuilder.durable(COLA_DLQ).build();
  }

  // "#" recibe todos los eventos, de cualquier módulo.
  @Bean
  public Binding bindingEventos(Queue colaEventos, TopicExchange exchangeEventos) {
    return BindingBuilder.bind(colaEventos).to(exchangeEventos).with("#");
  }
}
