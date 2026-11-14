package ar.edu.utn.dds.k3003;

import ar.edu.utn.dds.k3003.eventos.EventoRecibido;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

// Arma eventos con el mismo formato JSON que publican los módulos.
public final class Eventos {

  public static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules()
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private Eventos() {
  }

  public static EventoRecibido evento(String tipo, String modulo, String entidadId, String ocurridoEn, String datosJson) {
    String json = """
            {"eventoId":"%s-%s-%s","tipo":"%s","modulo":"%s","entidadId":"%s","ocurridoEn":"%s","datos":%s}
            """.formatted(tipo, entidadId, ocurridoEn, tipo, modulo, entidadId, ocurridoEn, datosJson);
    try {
      return MAPPER.readValue(json, EventoRecibido.class);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }
}
