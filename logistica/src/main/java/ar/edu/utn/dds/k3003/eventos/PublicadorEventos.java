package ar.edu.utn.dds.k3003.eventos;

import ar.edu.utn.dds.k3003.app.RabbitMQConfig;
import ar.edu.utn.dds.k3003.model.Asignacion;
import ar.edu.utn.dds.k3003.model.Deposito;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// Publica los cambios de Logística en el exchange de eventos para que el Servicio de Consultas
// actualice el modelo de lectura. Los datos se toman en el momento del cambio y se envían
// después del commit; si la publicación falla, la operación de negocio no se cae.
@Component
public class PublicadorEventos {

  private static final Logger log = LoggerFactory.getLogger(PublicadorEventos.class);
  private static final String MODULO = "logistica";

  private final RabbitTemplate rabbitTemplate;
  private final ObjectMapper objectMapper;

  public PublicadorEventos(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
    this.rabbitTemplate = rabbitTemplate;
    this.objectMapper = objectMapper;
  }

  public void depositoActualizado(Deposito deposito) {
    publicarDeposito(deposito, false);
  }

  public void depositoEliminado(Deposito deposito) {
    publicarDeposito(deposito, true);
  }

  public void asignacionActualizada(Asignacion asignacion) {
    publicarAsignacion(asignacion, false);
  }

  public void asignacionEliminada(Asignacion asignacion) {
    publicarAsignacion(asignacion, true);
  }

  private void publicarDeposito(Deposito deposito, boolean eliminado) {
    publicar("DEPOSITO_ACTUALIZADO", "logistica.deposito.actualizado",
            Evento.de("DEPOSITO_ACTUALIZADO", MODULO, String.valueOf(deposito.getId()),
                    DepositoActualizado.de(deposito, eliminado)));
  }

  private void publicarAsignacion(Asignacion asignacion, boolean eliminado) {
    publicar("ASIGNACION_ACTUALIZADA", "logistica.asignacion.actualizada",
            Evento.de("ASIGNACION_ACTUALIZADA", MODULO, String.valueOf(asignacion.getId()),
                    AsignacionActualizada.de(asignacion, eliminado)));
  }

  private void publicar(String tipo, String routingKey, Evento<?> evento) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          enviar(tipo, routingKey, evento);
        }
      });
    } else {
      enviar(tipo, routingKey, evento);
    }
  }

  private void enviar(String tipo, String routingKey, Evento<?> evento) {
    try {
      MessageProperties propiedades = new MessageProperties();
      propiedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
      propiedades.setMessageId(evento.eventoId());
      Message mensaje = new Message(objectMapper.writeValueAsBytes(evento), propiedades);

      rabbitTemplate.send(RabbitMQConfig.EXCHANGE_EVENTOS, routingKey, mensaje);
      log.info("Evento {} publicado para la entidad {} (eventoId {})", tipo, evento.entidadId(), evento.eventoId());
    } catch (Exception e) {
      log.error("No se pudo publicar el evento {} de la entidad {}: {}. Se corrige republicando.",
              tipo, evento.entidadId(), e.getMessage());
    }
  }
}
