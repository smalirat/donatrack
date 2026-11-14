package ar.edu.utn.dds.k3003.eventos;

import ar.edu.utn.dds.k3003.config.RabbitMQConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class ConsumidorEventos {

  private static final Logger log = LoggerFactory.getLogger(ConsumidorEventos.class);

  private final ObjectMapper objectMapper;
  private final ProyectorEventos proyector;

  public ConsumidorEventos(ObjectMapper objectMapper, ProyectorEventos proyector) {
    this.objectMapper = objectMapper;
    this.proyector = proyector;
  }

  @RabbitListener(queues = RabbitMQConfig.COLA_EVENTOS)
  public void recibir(Message mensaje) {
    EventoRecibido evento = leer(mensaje.getBody());
    ProyectorEventos.Resultado resultado = proyector.proyectar(evento);
    log.info("Evento {} de {} (entidad {}): {}", evento.tipo(), evento.modulo(), evento.entidadId(), resultado);
  }

  // Un mensaje que no respeta el contrato no se arregla reintentando: va directo a la DLQ.
  EventoRecibido leer(byte[] cuerpo) {
    try {
      EventoRecibido evento = objectMapper.readValue(cuerpo, EventoRecibido.class);
      if (!evento.esValido()) {
        throw new AmqpRejectAndDontRequeueException("Evento incompleto: " + new String(cuerpo, StandardCharsets.UTF_8));
      }
      return evento;
    } catch (IOException e) {
      throw new AmqpRejectAndDontRequeueException("Evento con JSON inválido: " + e.getMessage(), e);
    }
  }
}
