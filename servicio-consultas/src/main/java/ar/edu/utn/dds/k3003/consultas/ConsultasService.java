package ar.edu.utn.dds.k3003.consultas;

import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;

// Las cuatro consultas complejas de la entrega 6. Se resuelven solo contra MongoDB: ninguna llama a los módulos.
@Service
public class ConsultasService {

  private static final Document NO_ELIMINADO = new Document("eliminado", new Document("$ne", true));

  private final MongoTemplate mongoTemplate;

  public ConsultasService(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  private MongoCollection<Document> coleccion(String nombre) {
    return mongoTemplate.getCollection(nombre);
  }

  private List<Document> agregar(String coleccion, List<Document> pipeline) {
    return coleccion(coleccion).aggregate(pipeline).into(new ArrayList<>());
  }

  // ---------------------------------------------------------------------------------------------
  // 1. Ficha integral de un donador
  // ---------------------------------------------------------------------------------------------
  public Document fichaDonador(String donadorId) {
    Document donador = coleccion("donadores").find(new Document("_id", donadorId)).first();
    if (donador == null || Boolean.TRUE.equals(donador.getBoolean("eliminado"))) {
      throw new NoSuchElementException("No hay datos del donador " + donadorId + " en el modelo de lectura");
    }

    Document donacionesPorEstado = new Document();
    agregar("donaciones", List.of(
            new Document("$match", new Document("donadorId", donadorId).append("eliminado", new Document("$ne", true))),
            new Document("$group", new Document("_id", "$estado").append("cantidad", new Document("$sum", 1))),
            new Document("$sort", new Document("_id", 1))
    )).forEach(grupo -> donacionesPorEstado.put(grupo.getString("_id"), grupo.get("cantidad")));

    // Unidades entregadas a cada entidad: paquetes de asignaciones completadas cuya donación es de este donador.
    List<Document> entidadesBeneficiadas = agregar("asignaciones", List.of(
            new Document("$match", new Document("estado", "COMPLETADA").append("eliminado", new Document("$ne", true))),
            new Document("$unwind", "$paquetes"),
            new Document("$lookup", new Document("from", "donaciones").append("localField", "paquetes.donacionId")
                    .append("foreignField", "_id").append("as", "donacion")),
            new Document("$match", new Document("donacion.donadorId", donadorId)),
            new Document("$lookup", new Document("from", "necesidades").append("localField", "necesidadId")
                    .append("foreignField", "_id").append("as", "necesidad")),
            new Document("$unwind", "$necesidad"),
            new Document("$group", new Document("_id", "$necesidad.entidad.id")
                    .append("razonSocial", new Document("$first", "$necesidad.entidad.razonSocial"))
                    .append("unidades", new Document("$sum", "$paquetes.cantidad"))),
            new Document("$project", new Document("_id", 0).append("entidadId", "$_id")
                    .append("razonSocial", 1).append("unidades", 1)),
            new Document("$sort", new Document("unidades", -1))));

    return new Document("id", donadorId)
            .append("nombre", donador.get("nombre"))
            .append("apellido", donador.get("apellido"))
            .append("estado", donador.get("estado"))
            .append("categoria", donador.get("categoria"))
            .append("historialCategorias", donador.getOrDefault("historialCategorias", List.of()))
            .append("insignias", donador.getOrDefault("insignias", List.of()))
            .append("misionEnCurso", donador.get("misionEnCurso"))
            .append("donacionesPorEstado", donacionesPorEstado)
            .append("quejasRecibidas", donador.getOrDefault("cantidadQuejas", 0))
            .append("entidadesBeneficiadas", entidadesBeneficiadas)
            .append("ultimaActualizacion", donador.get("ultimaActualizacion"));
  }

  // ---------------------------------------------------------------------------------------------
  // 2. Necesidades urgentes sin cubrir
  // ---------------------------------------------------------------------------------------------
  public List<Document> necesidadesUrgentes(Integer urgenciaMinima, String tipo, String categoriaId, String subcategoriaId) {
    Document filtro = new Document("eliminado", new Document("$ne", true))
            .append("satisfecha", new Document("$ne", true))
            .append("cantidadFaltante", new Document("$gt", 0));
    if (urgenciaMinima != null) {
      filtro.append("nivelDeUrgencia", new Document("$gte", urgenciaMinima));
    }
    if (tipo != null) {
      filtro.append("tipo", tipo);
    }

    List<Document> pipeline = new ArrayList<>(List.of(
            new Document("$match", filtro),
            new Document("$lookup", new Document("from", "productos").append("localField", "productoSolicitadoId")
                    .append("foreignField", "_id").append("as", "producto")),
            new Document("$unwind", new Document("path", "$producto").append("preserveNullAndEmptyArrays", true))));
    if (categoriaId != null) {
      pipeline.add(new Document("$match", new Document("producto.categoria.id", categoriaId)));
    }
    if (subcategoriaId != null) {
      pipeline.add(new Document("$match", new Document("producto.subcategoria.id", subcategoriaId)));
    }
    pipeline.addAll(List.of(
            // Stock del producto en cada depósito.
            new Document("$lookup", new Document("from", "depositos")
                    .append("let", new Document("producto", "$productoSolicitadoId"))
                    .append("pipeline", List.of(
                            new Document("$match", NO_ELIMINADO),
                            new Document("$unwind", "$stockPorProducto"),
                            new Document("$match", new Document("$expr",
                                    new Document("$eq", List.of("$stockPorProducto.productoId", "$$producto")))),
                            new Document("$project", new Document("_id", 0).append("depositoId", "$_id")
                                    .append("nombre", 1).append("cantidad", "$stockPorProducto.cantidad"))))
                    .append("as", "stockPorDeposito")),
            // Unidades ya asignadas a la necesidad que todavía no se entregaron.
            new Document("$lookup", new Document("from", "asignaciones")
                    .append("let", new Document("necesidad", "$_id"))
                    .append("pipeline", List.of(
                            new Document("$match", new Document("$expr", new Document("$and", List.of(
                                    new Document("$eq", List.of("$necesidadId", "$$necesidad")),
                                    new Document("$eq", List.of("$estado", "ASIGNADA")),
                                    new Document("$ne", List.of("$eliminado", true)))))),
                            new Document("$unwind", "$paquetes"),
                            new Document("$group", new Document("_id", null)
                                    .append("unidades", new Document("$sum", "$paquetes.cantidad")))))
                    .append("as", "pendiente")),
            new Document("$project", new Document("_id", 0)
                    .append("necesidadId", "$_id")
                    .append("entidad", 1)
                    .append("descripcion", 1)
                    .append("tipo", 1)
                    .append("nivelDeUrgencia", 1)
                    .append("cantidadFaltante", 1)
                    .append("producto", new Document("id", "$productoSolicitadoId")
                            .append("nombre", "$producto.nombre")
                            .append("categoria", "$producto.categoria")
                            .append("subcategoria", "$producto.subcategoria"))
                    .append("stockPorDeposito", 1)
                    .append("stockTotal", new Document("$sum", "$stockPorDeposito.cantidad"))
                    .append("unidadesAsignadasSinEntregar",
                            new Document("$ifNull", List.of(new Document("$first", "$pendiente.unidades"), 0)))),
            new Document("$sort", new Document("nivelDeUrgencia", -1).append("cantidadFaltante", -1))));

    return agregar("necesidades", pipeline);
  }

  // ---------------------------------------------------------------------------------------------
  // 3. Estado de los depósitos
  // ---------------------------------------------------------------------------------------------
  public List<Document> estadoDepositos(String depositoId, LocalDate desde, LocalDate hasta) {
    Document filtroDepositos = new Document("eliminado", new Document("$ne", true));
    if (depositoId != null) {
      filtroDepositos.append("_id", depositoId);
    }

    // Una asignación pertenece al depósito si alguno de sus paquetes salió de él.
    Document delDeposito = new Document("$match", new Document("$expr", new Document("$in",
            List.of("$$deposito", new Document("$ifNull", List.of("$paquetes.depositoId", List.of()))))));

    Document filtroEntregas = new Document("estado", "COMPLETADA").append("eliminado", new Document("$ne", true));
    Document rangoEntrega = new Document();
    if (desde != null) {
      rangoEntrega.append("$gte", Date.from(desde.atStartOfDay().toInstant(ZoneOffset.UTC)));
    }
    if (hasta != null) {
      rangoEntrega.append("$lt", Date.from(hasta.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
    }
    if (!rangoEntrega.isEmpty()) {
      filtroEntregas.append("fechaEntrega", rangoEntrega);
    }

    List<Document> pipeline = List.of(
            new Document("$match", filtroDepositos),
            new Document("$lookup", new Document("from", "asignaciones")
                    .append("let", new Document("deposito", "$_id"))
                    .append("pipeline", List.of(
                            new Document("$match", NO_ELIMINADO),
                            delDeposito,
                            new Document("$group", new Document("_id", "$estado")
                                    .append("cantidad", new Document("$sum", 1)))))
                    .append("as", "porEstado")),
            new Document("$lookup", new Document("from", "asignaciones")
                    .append("let", new Document("deposito", "$_id"))
                    .append("pipeline", List.of(
                            new Document("$match", new Document("estado", "COMPLETADA")
                                    .append("eliminado", new Document("$ne", true))),
                            delDeposito,
                            new Document("$lookup", new Document("from", "necesidades").append("localField", "necesidadId")
                                    .append("foreignField", "_id").append("as", "necesidad")),
                            new Document("$unwind", "$necesidad"),
                            new Document("$group", new Document("_id", "$necesidad.entidad.id"))))
                    .append("as", "entidades")),
            // Tiempo entre el ingreso de la donación (Donaciones) y la entrega del paquete (Logística).
            new Document("$lookup", new Document("from", "asignaciones")
                    .append("let", new Document("deposito", "$_id"))
                    .append("pipeline", List.of(
                            new Document("$match", filtroEntregas),
                            new Document("$unwind", "$paquetes"),
                            new Document("$match", new Document("$expr",
                                    new Document("$eq", List.of("$paquetes.depositoId", "$$deposito")))),
                            new Document("$lookup", new Document("from", "donaciones")
                                    .append("localField", "paquetes.donacionId")
                                    .append("foreignField", "_id").append("as", "donacion")),
                            new Document("$unwind", "$donacion"),
                            new Document("$group", new Document("_id", null)
                                    .append("promedioMs", new Document("$avg",
                                            new Document("$subtract", List.of("$fechaEntrega", "$donacion.fechaIngreso"))))
                                    .append("entregas", new Document("$sum", 1)))))
                    .append("as", "tiempos")),
            new Document("$project", new Document("_id", 0)
                    .append("depositoId", "$_id")
                    .append("nombre", 1)
                    .append("algoritmo", 1)
                    .append("capacidadMaxima", 1)
                    .append("stockTotal", 1)
                    .append("ocupacionPorcentaje", new Document("$cond", List.of(
                            new Document("$gt", List.of("$capacidadMaxima", 0)),
                            new Document("$round", List.of(new Document("$multiply", List.of(
                                    new Document("$divide", List.of("$stockTotal", "$capacidadMaxima")), 100)), 2)),
                            0)))
                    .append("asignacionesPorEstado", new Document("$arrayToObject", new Document("$map",
                            new Document("input", "$porEstado").append("as", "e")
                                    .append("in", new Document("k", "$$e._id").append("v", "$$e.cantidad")))))
                    .append("entidadesAtendidas", new Document("$size", "$entidades"))
                    .append("entregasMedidas", new Document("$ifNull", List.of(new Document("$first", "$tiempos.entregas"), 0)))
                    .append("tiempoPromedioEntregaHoras", new Document("$let", new Document("vars",
                            new Document("promedio", new Document("$first", "$tiempos.promedioMs")))
                            // Sin datos de Donaciones (o sin entregas en el rango) no hay promedio: queda null.
                            .append("in", new Document("$cond", Arrays.asList(
                                    new Document("$eq", Arrays.asList(new Document("$ifNull", Arrays.asList("$$promedio", null)), null)),
                                    null,
                                    new Document("$round", List.of(new Document("$divide", List.of("$$promedio", 3600000)), 2)))))))),
            new Document("$sort", new Document("depositoId", 1)));

    return agregar("depositos", pipeline);
  }

  // ---------------------------------------------------------------------------------------------
  // 4. Calidad de lo donado
  // ---------------------------------------------------------------------------------------------
  public Document calidadDonado(int top) {
    List<Document> porCategoria = agregar("donaciones", List.of(
            new Document("$match", NO_ELIMINADO),
            // Se agrupa solo por ids: Donaciones manda la subcategoría sin nombre, y un nombre distinto
            // entre donaciones no debe partir el grupo. $max se queda con el nombre que no sea null.
            new Document("$group", new Document("_id", new Document("categoriaId", "$categoria.id")
                    .append("subcategoriaId", "$subcategoria.id"))
                    .append("categoria", new Document("$max", "$categoria.nombre"))
                    .append("subcategoria", new Document("$max", "$subcategoria.nombre"))
                    .append("total", new Document("$sum", 1))
                    .append("conQueja", new Document("$sum", new Document("$cond",
                            List.of(new Document("$eq", List.of("$estado", "CONQUEJA")), 1, 0))))),
            new Document("$project", new Document("_id", 0)
                    .append("categoriaId", "$_id.categoriaId").append("categoria", 1)
                    .append("subcategoriaId", "$_id.subcategoriaId").append("subcategoria", 1)
                    .append("total", 1).append("conQueja", 1)
                    .append("proporcionConQueja", new Document("$round",
                            List.of(new Document("$divide", List.of("$conQueja", "$total")), 4)))),
            new Document("$sort", new Document("proporcionConQueja", -1).append("total", -1))));

    List<Document> donadoresConMasQuejas = agregar("donadores", List.of(
            new Document("$match", new Document("eliminado", new Document("$ne", true))
                    .append("cantidadQuejas", new Document("$gt", 0))),
            new Document("$sort", new Document("cantidadQuejas", -1)),
            new Document("$limit", top),
            new Document("$project", new Document("_id", 0).append("donadorId", "$_id")
                    .append("nombre", 1).append("apellido", 1).append("estado", 1)
                    .append("categoria", 1).append("cantidadQuejas", 1))));

    return new Document("porCategoria", porCategoria).append("donadoresConMasQuejas", donadoresConMasQuejas);
  }
}
