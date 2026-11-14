package ar.edu.utn.dds.k3003.eventos;

import ar.edu.utn.dds.k3003.config.RabbitMQConfig;
import ar.edu.utn.dds.k3003.model.Donador;
import ar.edu.utn.dds.k3003.model.EntidadBenefica;
import ar.edu.utn.dds.k3003.model.NecesidadMaterial;
import ar.edu.utn.dds.k3003.repositories.entidad.EntidadBeneficaJpaRepository;
import ar.edu.utn.dds.k3003.repositories.quejas.QuejaJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

// El que manda el mensaje al servicio de consultas
@Component
public class PublicadorEventos {

  private static final Logger log = LoggerFactory.getLogger(PublicadorEventos.class);
  private static final String MODULO = "dye";

  private final RabbitTemplate rabbitTemplate;
  private final ObjectMapper objectMapper;
  private final QuejaJpaRepository quejasRepository;
  private final EntidadBeneficaJpaRepository entidadesRepository;

  public PublicadorEventos(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper,
                           QuejaJpaRepository quejasRepository, EntidadBeneficaJpaRepository entidadesRepository) {
    this.rabbitTemplate = rabbitTemplate;
    this.objectMapper = objectMapper;
    this.quejasRepository = quejasRepository;
    this.entidadesRepository = entidadesRepository;
  }

  public void donadorActualizado(Donador donador) {
    publicarDonador(donador, false);
  }

  public void donadorEliminado(Donador donador) {
    publicarDonador(donador, true);
  }

  public void entidadActualizada(EntidadBenefica entidad) {
    publicarEntidad(entidad, false);
  }

  public void entidadEliminada(EntidadBenefica entidad) {
    publicarEntidad(entidad, true);
  }

  public void necesidadActualizada(NecesidadMaterial necesidad) {
    publicarNecesidad(necesidad, false);
  }

  public void necesidadEliminada(NecesidadMaterial necesidad) {
    publicarNecesidad(necesidad, true);
  }

  private void publicarDonador(Donador donador, boolean eliminado) {
    try {
      int quejas = quejasRepository.findAllByDonadorId(donador.getId()).size();
      enviar("dye.donador.actualizado", Evento.de("DONADOR_ACTUALIZADO", MODULO, donador.getId(),
          DonadorActualizado.de(donador, quejas, eliminado)));
    } catch (Exception e) {
      log.error("No se pudo armar el evento DONADOR_ACTUALIZADO de {}: {}", donador.getId(), e.getMessage());
    }
  }

  private void publicarEntidad(EntidadBenefica entidad, boolean eliminado) {
    try {
      enviar("dye.entidad.actualizada", Evento.de("ENTIDAD_ACTUALIZADA", MODULO, String.valueOf(entidad.getId()),
          EntidadActualizada.de(entidad, eliminado)));
    } catch (Exception e) {
      log.error("No se pudo armar el evento ENTIDAD_ACTUALIZADA de {}: {}", entidad.getId(), e.getMessage());
    }
  }

  private void publicarNecesidad(NecesidadMaterial necesidad, boolean eliminado) {
    try {
      EntidadBenefica entidad = buscarEntidad(necesidad.getEntidadId());
      enviar("dye.necesidad.actualizada", Evento.de("NECESIDAD_ACTUALIZADA", MODULO, String.valueOf(necesidad.getId()),
          NecesidadActualizada.de(necesidad, entidad, eliminado)));
    } catch (Exception e) {
      log.error("No se pudo armar el evento NECESIDAD_ACTUALIZADA de {}: {}", necesidad.getId(), e.getMessage());
    }
  }

  private EntidadBenefica buscarEntidad(String entidadId) {
    try {
      return entidadesRepository.findById(Long.parseLong(entidadId)).orElse(null);
    } catch (NumberFormatException | NullPointerException e) {
      return null;
    }
  }

  private void enviar(String routingKey, Evento<?> evento) {
    try {
      MessageProperties propiedades = new MessageProperties();
      propiedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
      propiedades.setMessageId(evento.eventoId());
      Message mensaje = new Message(objectMapper.writeValueAsBytes(evento), propiedades);

      rabbitTemplate.send(RabbitMQConfig.EXCHANGE_EVENTOS, routingKey, mensaje);
      log.info("Evento {} publicado para la entidad {} (eventoId {})", evento.tipo(), evento.entidadId(), evento.eventoId());
    } catch (Exception e) {
      log.error("No se pudo publicar el evento {} de la entidad {}: {}", evento.tipo(), evento.entidadId(), e.getMessage());
    }
  }
}
