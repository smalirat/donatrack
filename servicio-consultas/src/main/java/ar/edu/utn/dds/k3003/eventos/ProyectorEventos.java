package ar.edu.utn.dds.k3003.eventos;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.Objects;

// Aplica cada evento sobre el modelo de lectura con las reglas del contrato:
// upsert por id, se descarta lo que sea más viejo que lo guardado (versión por módulo de origen)
// y las bajas quedan como marca "eliminado". Así los eventos repetidos o desordenados no rompen nada.
@Component
public class ProyectorEventos {

  private static final Logger log = LoggerFactory.getLogger(ProyectorEventos.class);

  public enum Resultado { APLICADO, IGNORADO_POR_VIEJO, IGNORADO_TIPO_DESCONOCIDO }

  // A qué colección va cada tipo de evento y qué campo de los datos es el id del documento.
  private record Destino(String coleccion, String campoId) {}

  private static final Map<String, Destino> DESTINOS = Map.of(
          "DEPOSITO_ACTUALIZADO", new Destino("depositos", "id"),
          "ASIGNACION_ACTUALIZADA", new Destino("asignaciones", "id"),
          "DONACION_ACTUALIZADA", new Destino("donaciones", "id"),
          "PRODUCTO_ACTUALIZADO", new Destino("productos", "id"),
          "ENTIDAD_ACTUALIZADA", new Destino("entidades", "id"),
          "NECESIDAD_ACTUALIZADA", new Destino("necesidades", "id"),
          "DONADOR_ACTUALIZADO", new Destino("donadores", "id"),
          // Mismo documento que el anterior: Incentivos completa insignias y misión del donador.
          "INCENTIVOS_DONADOR_ACTUALIZADO", new Destino("donadores", "donadorId"));

  private final MongoTemplate mongoTemplate;
  private final ConversorDocumento conversor;

  public ProyectorEventos(MongoTemplate mongoTemplate, ConversorDocumento conversor) {
    this.mongoTemplate = mongoTemplate;
    this.conversor = conversor;
  }

  public Resultado proyectar(EventoRecibido evento) {
    Destino destino = DESTINOS.get(evento.tipo());
    if (destino == null) {
      log.warn("Evento de tipo desconocido {} (eventoId {}): se ignora", evento.tipo(), evento.eventoId());
      return Resultado.IGNORADO_TIPO_DESCONOCIDO;
    }

    Document datos = conversor.aDocumento(evento.datos());
    datos.remove(destino.campoId());

    Date version = Date.from(evento.ocurridoEn());
    String campoVersion = "versiones." + evento.modulo();

    // Solo se actualiza si el documento no existe o si lo guardado de este módulo es más viejo.
    Query soloSiEsMasNuevo = new Query(Criteria.where("_id").is(evento.entidadId()).orOperator(
            Criteria.where(campoVersion).exists(false),
            Criteria.where(campoVersion).lt(version)));

    Update update = new Update();
    datos.forEach(update::set);
    update.set(campoVersion, version).set("ultimaActualizacion", version);

    try {
      Document anterior = mongoTemplate.findAndModify(soloSiEsMasNuevo, update,
              FindAndModifyOptions.options().upsert(true).returnNew(false),
              Document.class, destino.coleccion());

      if ("DONADOR_ACTUALIZADO".equals(evento.tipo())) {
        registrarCambioDeCategoria(evento.entidadId(), anterior, datos, version);
      }
      return Resultado.APLICADO;
    } catch (DuplicateKeyException documentoMasNuevo) {
      // El documento existe con una versión igual o más nueva: el upsert intentó insertar otro con el mismo id.
      log.info("Evento {} de {} ignorado: ya hay una versión igual o más nueva", evento.tipo(), evento.entidadId());
      return Resultado.IGNORADO_POR_VIEJO;
    }
  }

  // El historial de categorías no lo guarda ningún módulo: se arma acá con cada cambio que llega.
  private void registrarCambioDeCategoria(String donadorId, Document anterior, Document datos, Date version) {
    String categoriaNueva = datos.getString("categoria");
    String categoriaAnterior = anterior != null ? anterior.getString("categoria") : null;
    if (categoriaNueva != null && !Objects.equals(categoriaNueva, categoriaAnterior)) {
      mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(donadorId)),
              new Update().push("historialCategorias",
                      new Document("categoria", categoriaNueva).append("desde", version)),
              "donadores");
    }
  }
}
