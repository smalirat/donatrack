package ar.edu.utn.dds.k3003.eventos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

// Sobre común definido en el contrato de eventos. "datos" queda como JSON genérico:
// cada tipo de evento trae campos distintos y se guardan tal cual en su colección.
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventoRecibido(String eventoId, String tipo, String modulo,
                             String entidadId, Instant ocurridoEn, JsonNode datos) {

  public boolean esValido() {
    return tipo != null && modulo != null && entidadId != null && ocurridoEn != null
            && datos != null && datos.isObject();
  }
}
