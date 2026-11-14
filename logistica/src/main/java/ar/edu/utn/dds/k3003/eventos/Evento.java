package ar.edu.utn.dds.k3003.eventos;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

// Sobre común a los eventos de los cuatro módulos (ver contrato de eventos de la entrega 6).
public record Evento<T>(String eventoId, String tipo, String modulo,
                        String entidadId, Instant ocurridoEn, T datos) {

  public static <T> Evento<T> de(String tipo, String modulo, String entidadId, T datos) {
    // Milisegundos: es la precisión con la que MongoDB guarda y compara las fechas.
    return new Evento<>(UUID.randomUUID().toString(), tipo, modulo, entidadId,
            Instant.now().truncatedTo(ChronoUnit.MILLIS), datos);
  }

  // Las entidades guardan LocalDateTime sin zona: se interpreta con la zona del servidor y se publica en UTC.
  public static Instant enUtc(LocalDateTime fecha) {
    return fecha == null ? null : fecha.atZone(ZoneId.systemDefault()).toInstant();
  }
}
