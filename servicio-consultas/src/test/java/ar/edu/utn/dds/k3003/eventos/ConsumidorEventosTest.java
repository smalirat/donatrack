package ar.edu.utn.dds.k3003.eventos;

import ar.edu.utn.dds.k3003.Eventos;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ConsumidorEventosTest {

  private final ConsumidorEventos consumidor = new ConsumidorEventos(Eventos.MAPPER, mock(ProyectorEventos.class));

  private byte[] bytes(String json) {
    return json.getBytes(StandardCharsets.UTF_8);
  }

  @Test
  void leeUnEventoConElFormatoDelContrato() {
    EventoRecibido evento = consumidor.leer(bytes("""
            {"eventoId":"8a57","tipo":"DEPOSITO_ACTUALIZADO","modulo":"logistica","entidadId":"9",
             "ocurridoEn":"2026-10-31T12:48:06.850892611Z","datos":{"id":"9","stockTotal":0}}"""));

    assertEquals("DEPOSITO_ACTUALIZADO", evento.tipo());
    assertEquals("9", evento.entidadId());
    assertEquals(0, evento.datos().get("stockTotal").asInt());
  }

  @Test
  void unJsonInvalidoVaDirectoALaDlq() {
    assertThrows(AmqpRejectAndDontRequeueException.class, () -> consumidor.leer(bytes("no es json")));
  }

  @Test
  void unEventoSinCamposObligatoriosVaDirectoALaDlq() {
    assertThrows(AmqpRejectAndDontRequeueException.class,
            () -> consumidor.leer(bytes("{\"tipo\":\"DEPOSITO_ACTUALIZADO\",\"datos\":{}}")));
  }
}
