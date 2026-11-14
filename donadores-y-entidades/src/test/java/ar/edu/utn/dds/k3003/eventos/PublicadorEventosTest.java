package ar.edu.utn.dds.k3003.eventos;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.TipoNecesidadMaterialEnum;
import ar.edu.utn.dds.k3003.model.Donador;
import ar.edu.utn.dds.k3003.model.EntidadBenefica;
import ar.edu.utn.dds.k3003.model.NecesidadMaterial;
import ar.edu.utn.dds.k3003.model.PeriodoNecesidad;
import ar.edu.utn.dds.k3003.model.Queja;
import ar.edu.utn.dds.k3003.repositories.entidad.EntidadBeneficaJpaRepository;
import ar.edu.utn.dds.k3003.repositories.quejas.QuejaJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PublicadorEventosTest {

  private RabbitTemplate rabbitTemplate;
  private QuejaJpaRepository quejasRepository;
  private EntidadBeneficaJpaRepository entidadesRepository;
  private ObjectMapper objectMapper;
  private PublicadorEventos publicador;

  @BeforeEach
  void setUp() {
    rabbitTemplate = mock(RabbitTemplate.class);
    quejasRepository = mock(QuejaJpaRepository.class);
    entidadesRepository = mock(EntidadBeneficaJpaRepository.class);
    objectMapper = new ObjectMapper().findAndRegisterModules()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    publicador = new PublicadorEventos(rabbitTemplate, objectMapper, quejasRepository, entidadesRepository);
  }

  private JsonNode mensajeEnviado(String routingKey) throws Exception {
    ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
    verify(rabbitTemplate).send(eq("donatrack.eventos"), eq(routingKey), captor.capture());
    assertEquals("application/json", captor.getValue().getMessageProperties().getContentType());
    return objectMapper.readTree(captor.getValue().getBody());
  }

  private Donador donador() {
    Donador donador = new Donador("Lucía", "Fernández", 30, "lucia@mail.com", "123", "Medrano 951");
    donador.setId("12");
    return donador;
  }

  @Test
  void donadorActualizadoPublicaEstadoCategoriaYCantidadDeQuejas() throws Exception {
    when(quejasRepository.findAllByDonadorId("12")).thenReturn(List.of(new Queja(), new Queja(), new Queja()));

    publicador.donadorActualizado(donador());

    JsonNode evento = mensajeEnviado("dye.donador.actualizado");
    assertEquals("DONADOR_ACTUALIZADO", evento.get("tipo").asText());
    assertEquals("dye", evento.get("modulo").asText());
    assertEquals("12", evento.get("entidadId").asText());
    assertTrue(evento.get("ocurridoEn").asText().endsWith("Z"));
    JsonNode datos = evento.get("datos");
    assertEquals("Lucía", datos.get("nombre").asText());
    assertEquals("VERIFICADO", datos.get("estado").asText());
    assertEquals("Ocasional", datos.get("categoria").asText());
    assertEquals(3, datos.get("cantidadQuejas").asInt());
    assertFalse(datos.get("eliminado").asBoolean());
  }

  @Test
  void entidadActualizadaPublicaSusDatos() throws Exception {
    EntidadBenefica entidad = new EntidadBenefica(4L, "Comedor Los Girasoles", "Medrano 951", "1122334455", "c@mail.com");

    publicador.entidadActualizada(entidad);

    JsonNode datos = mensajeEnviado("dye.entidad.actualizada").get("datos");
    assertEquals("4", datos.get("id").asText());
    assertEquals("Comedor Los Girasoles", datos.get("razonSocial").asText());
  }

  @Test
  void necesidadActualizadaPublicaLaRazonSocialDeSuEntidadYSiEstaSatisfecha() throws Exception {
    when(entidadesRepository.findById(4L)).thenReturn(Optional.of(
        new EntidadBenefica(4L, "Comedor Los Girasoles", "Medrano 951", "1122334455", "c@mail.com")));
    NecesidadMaterial necesidad = NecesidadMaterial.builder()
        .id(9L).entidadId("4").nivelDeUrgencia(8).descripcion("Fideos semanales").cantidadObjetivo(100)
        .productoSolicitadoId("7").tipo(TipoNecesidadMaterialEnum.RECURRENTE).periodo(PeriodoNecesidad.SEMANAL)
        .build();

    publicador.necesidadActualizada(necesidad);

    JsonNode datos = mensajeEnviado("dye.necesidad.actualizada").get("datos");
    assertEquals("9", datos.get("id").asText());
    assertEquals("4", datos.get("entidad").get("id").asText());
    assertEquals("Comedor Los Girasoles", datos.get("entidad").get("razonSocial").asText());
    assertEquals("7", datos.get("productoSolicitadoId").asText());
    assertEquals("RECURRENTE", datos.get("tipo").asText());
    assertEquals("SEMANAL", datos.get("periodo").asText());
    assertEquals(100, datos.get("cantidadFaltante").asInt());
    assertFalse(datos.get("satisfecha").asBoolean());
  }

  @Test
  void unaRecurrenteSatisfechaEnElPeriodoSePublicaComoSatisfecha() throws Exception {
    NecesidadMaterial necesidad = NecesidadMaterial.builder()
        .id(9L).entidadId("4").cantidadObjetivo(100).tipo(TipoNecesidadMaterialEnum.RECURRENTE)
        .periodo(PeriodoNecesidad.SEMANAL).fechaUltimaSatisfaccion(LocalDate.now())
        .build();

    publicador.necesidadActualizada(necesidad);

    JsonNode datos = mensajeEnviado("dye.necesidad.actualizada").get("datos");
    assertTrue(datos.get("satisfecha").asBoolean());
    assertTrue(datos.get("fechaUltimaSatisfaccion").asText().endsWith("Z"));
  }

  @Test
  void donadorEliminadoPublicaLaBaja() throws Exception {
    publicador.donadorEliminado(donador());

    assertTrue(mensajeEnviado("dye.donador.actualizado").get("datos").get("eliminado").asBoolean());
  }

  @Test
  void siRabbitFallaLaOperacionNoSeCae() {
    doThrow(new RuntimeException("broker caído")).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class));

    assertDoesNotThrow(() -> publicador.donadorActualizado(donador()));
  }
}
