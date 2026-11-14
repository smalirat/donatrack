package ar.edu.utn.dds.k3003.eventos;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Convierte los datos de un evento en un documento de Mongo. Las fechas llegan como texto ISO-8601
// y se guardan como Date, para poder compararlas y restarlas en los pipelines de agregación.
@Component
public class ConversorDocumento {

  private static final Set<String> CAMPOS_FECHA =
          Set.of("fecha", "fechaIngreso", "fechaCreacion", "fechaEntrega", "fechaUltimaSatisfaccion");

  private final ObjectMapper objectMapper;

  public ConversorDocumento(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public Document aDocumento(JsonNode datos) {
    Map<String, Object> mapa = objectMapper.convertValue(datos, new TypeReference<>() {});
    return (Document) convertir(null, mapa);
  }

  private Object convertir(String clave, Object valor) {
    if (valor instanceof Map<?, ?> mapa) {
      Document documento = new Document();
      mapa.forEach((k, v) -> documento.put(String.valueOf(k), convertir(String.valueOf(k), v)));
      return documento;
    }
    if (valor instanceof List<?> lista) {
      return lista.stream().map(elemento -> convertir(clave, elemento)).toList();
    }
    if (valor instanceof String texto && clave != null && CAMPOS_FECHA.contains(clave)) {
      return aFecha(texto);
    }
    return valor;
  }

  static Date aFecha(String texto) {
    try {
      return Date.from(Instant.parse(texto));
    } catch (DateTimeParseException sinZona) {
      try {
        return Date.from(LocalDateTime.parse(texto).toInstant(ZoneOffset.UTC));
      } catch (DateTimeParseException sinHora) {
        return Date.from(LocalDate.parse(texto).atStartOfDay().toInstant(ZoneOffset.UTC));
      }
    }
  }
}
