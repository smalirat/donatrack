package ar.edu.utn.dds.k3003.eventos;

import ar.edu.utn.dds.k3003.config.RabbitMQConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// El que manda el mensaje al servicio de consultas. Solo publica si cambio algo: el cron procesa cada 15 s
@Component
public class PublicadorEventos {

  private static final Logger log = LoggerFactory.getLogger(PublicadorEventos.class);
  private static final String MODULO = "incentivos";

  private final RabbitTemplate rabbitTemplate;
  private final ObjectMapper objectMapper;
  private final Map<String, IncentivosDonadorActualizado> ultimoPublicado = new ConcurrentHashMap<>();

  public PublicadorEventos(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
    this.rabbitTemplate = rabbitTemplate;
    this.objectMapper = objectMapper;
  }

  public void incentivosActualizados(IncentivosDonadorActualizado datos) {
    if (datos.equals(ultimoPublicado.get(datos.donadorId()))) {
      return;
    }
    if (enviar(Evento.de("INCENTIVOS_DONADOR_ACTUALIZADO", MODULO, datos.donadorId(), datos))) {
      ultimoPublicado.put(datos.donadorId(), datos);
    }
  }

  private boolean enviar(Evento<?> evento) {
    try {
      MessageProperties propiedades = new MessageProperties();
      propiedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
      propiedades.setMessageId(evento.eventoId());
      Message mensaje = new Message(objectMapper.writeValueAsBytes(evento), propiedades);

      rabbitTemplate.send(RabbitMQConfig.EXCHANGE_EVENTOS, "incentivos.donador.actualizado", mensaje);
      log.info("Evento {} publicado para el donador {} (eventoId {})", evento.tipo(), evento.entidadId(), evento.eventoId());
      return true;
    } catch (Exception e) {
      log.error("No se pudo publicar el evento {} del donador {}: {}", evento.tipo(), evento.entidadId(), e.getMessage());
      return false;
    }
  }
}
