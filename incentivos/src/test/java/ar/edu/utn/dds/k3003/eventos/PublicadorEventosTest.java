package ar.edu.utn.dds.k3003.eventos;

import ar.edu.utn.dds.k3003.catedra.dtos.incentivos.TipoMisionEnum;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PublicadorEventosTest {

  private RabbitTemplate rabbitTemplate;
  private ObjectMapper objectMapper;
  private PublicadorEventos publicador;

  @BeforeEach
  void setUp() {
    rabbitTemplate = mock(RabbitTemplate.class);
    objectMapper = new ObjectMapper().findAndRegisterModules()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    publicador = new PublicadorEventos(rabbitTemplate, objectMapper);
  }

  private IncentivosDonadorActualizado conProgreso(int progreso) {
    return new IncentivosDonadorActualizado("12",
        List.of(new IncentivosDonadorActualizado.InsigniaObtenida("1", "Completitud")),
        new IncentivosDonadorActualizado.MisionEnCurso("2", "Donaciones Exitosas",
            TipoMisionEnum.DONACIONES_EXITOSAS, progreso, 20));
  }

  @Test
  void publicaInsigniasYMisionConProgreso() throws Exception {
    publicador.incentivosActualizados(conProgreso(14));

    ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
    verify(rabbitTemplate).send(eq("donatrack.eventos"), eq("incentivos.donador.actualizado"), captor.capture());
    JsonNode evento = objectMapper.readTree(captor.getValue().getBody());
    assertEquals("INCENTIVOS_DONADOR_ACTUALIZADO", evento.get("tipo").asText());
    assertEquals("incentivos", evento.get("modulo").asText());
    assertEquals("12", evento.get("entidadId").asText());
    JsonNode datos = evento.get("datos");
    assertEquals("12", datos.get("donadorId").asText());
    assertEquals("Completitud", datos.get("insignias").get(0).get("nombre").asText());
    assertEquals("DONACIONES_EXITOSAS", datos.get("misionEnCurso").get("tipo").asText());
    assertEquals(14, datos.get("misionEnCurso").get("progreso").asInt());
    assertEquals(20, datos.get("misionEnCurso").get("objetivo").asInt());
  }

  @Test
  void noRepiteElEventoSiNoCambioNada() {
    publicador.incentivosActualizados(conProgreso(14));
    publicador.incentivosActualizados(conProgreso(14));
    publicador.incentivosActualizados(conProgreso(15));

    verify(rabbitTemplate, times(2)).send(anyString(), anyString(), any(Message.class));
  }

  @Test
  void siRabbitFallaLoReintentaLaProximaVez() {
    doThrow(new RuntimeException("broker caído")).doNothing()
        .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class));

    assertDoesNotThrow(() -> publicador.incentivosActualizados(conProgreso(14)));
    publicador.incentivosActualizados(conProgreso(14));

    verify(rabbitTemplate, times(2)).send(anyString(), anyString(), any(Message.class));
  }

  @Test
  void sinMisionSePublicaConMisionNula() throws Exception {
    publicador.incentivosActualizados(IncentivosDonadorActualizado.sinIncentivos("12"));

    ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
    verify(rabbitTemplate).send(anyString(), anyString(), captor.capture());
    JsonNode datos = objectMapper.readTree(captor.getValue().getBody()).get("datos");
    assertTrue(datos.get("misionEnCurso").isNull());
    assertEquals(0, datos.get("insignias").size());
  }
}
