package ar.edu.utn.dds.k3003.eventos;

import ar.edu.utn.dds.k3003.Eventos;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Date;
import java.util.List;

import static ar.edu.utn.dds.k3003.Eventos.evento;
import static ar.edu.utn.dds.k3003.eventos.ProyectorEventos.Resultado.*;
import static org.junit.jupiter.api.Assertions.*;

@DataMongoTest
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({ProyectorEventos.class, ConversorDocumento.class})
class ProyectorEventosTest {

  // Mensaje real publicado por Logística en la prueba de la cola (depósito 9 después de la donación).
  private static final String DEPOSITO_CON_STOCK = """
          {"id":"9","nombre":"PRUEBA DONACION EVENTOS (borrar)","direccion":"Medrano 951","capacidadMaxima":100,
           "algoritmo":"SUB_ATENDIDOS","stockTotal":5,"stockPorProducto":[{"productoId":"6","cantidad":5}],"eliminado":false}""";

  @Autowired
  private ProyectorEventos proyector;
  @Autowired
  private MongoTemplate mongo;

  @BeforeEach
  void limpiar() {
    mongo.getDb().drop();
  }

  private Document documento(String coleccion, String id) {
    return mongo.getCollection(coleccion).find(new Document("_id", id)).first();
  }

  private String deposito(int stockTotal, boolean eliminado) {
    return """
            {"id":"9","nombre":"Central","capacidadMaxima":100,"algoritmo":"SUB_ATENDIDOS",
             "stockTotal":%d,"stockPorProducto":[],"eliminado":%s}""".formatted(stockTotal, eliminado);
  }

  @Test
  void guardaElDepositoConSusDatosYLaVersionDelModulo() {
    var resultado = proyector.proyectar(evento("DEPOSITO_ACTUALIZADO", "logistica", "9",
            "2026-10-31T12:48:11.543369166Z", DEPOSITO_CON_STOCK));

    assertEquals(APLICADO, resultado);
    Document deposito = documento("depositos", "9");
    assertEquals("PRUEBA DONACION EVENTOS (borrar)", deposito.getString("nombre"));
    assertEquals(5, deposito.getInteger("stockTotal"));
    assertEquals("6", deposito.getList("stockPorProducto", Document.class).get(0).getString("productoId"));
    assertNull(deposito.get("id"), "el id va en _id, no se duplica");
    assertInstanceOf(Date.class, deposito.get("versiones", Document.class).get("logistica"));
  }

  @Test
  void unEventoRepetidoSeIgnora() {
    var evento = evento("DEPOSITO_ACTUALIZADO", "logistica", "9", "2026-10-31T12:00:00Z", deposito(5, false));

    assertEquals(APLICADO, proyector.proyectar(evento));
    assertEquals(IGNORADO_POR_VIEJO, proyector.proyectar(evento));
    assertEquals(1, mongo.getCollection("depositos").countDocuments());
  }

  @Test
  void unEventoViejoQueLlegaTardeNoPisaAlNuevo() {
    proyector.proyectar(evento("DEPOSITO_ACTUALIZADO", "logistica", "9", "2026-10-31T12:00:10Z", deposito(30, false)));

    var resultado = proyector.proyectar(evento("DEPOSITO_ACTUALIZADO", "logistica", "9",
            "2026-10-31T12:00:05Z", deposito(5, false)));

    assertEquals(IGNORADO_POR_VIEJO, resultado);
    assertEquals(30, documento("depositos", "9").getInteger("stockTotal"));
  }

  @Test
  void unaBajaQuedaComoMarcaYUnEventoViejoNoLaRevive() {
    proyector.proyectar(evento("DEPOSITO_ACTUALIZADO", "logistica", "9", "2026-10-31T12:00:20Z", deposito(5, true)));
    proyector.proyectar(evento("DEPOSITO_ACTUALIZADO", "logistica", "9", "2026-10-31T12:00:10Z", deposito(5, false)));

    assertTrue(documento("depositos", "9").getBoolean("eliminado"));
  }

  @Test
  void lasFechasSeGuardanComoDateParaPoderCompararlas() {
    proyector.proyectar(evento("ASIGNACION_ACTUALIZADA", "logistica", "42", "2026-11-03T10:15:00Z", """
            {"id":"42","necesidadId":"9","origen":"MATCHMAKING","estado":"COMPLETADA",
             "fechaCreacion":"2026-11-02T14:00:05Z","fechaEntrega":"2026-11-03T10:15:00Z",
             "paquetes":[{"paqueteId":"15","donacionId":"a3f9","productoId":"7","depositoId":"3","cantidad":30}],
             "eliminado":false}"""));

    Document asignacion = documento("asignaciones", "42");
    assertEquals(Date.from(java.time.Instant.parse("2026-11-03T10:15:00Z")), asignacion.getDate("fechaEntrega"));
    assertInstanceOf(Date.class, asignacion.get("fechaCreacion"));
  }

  @Test
  void elDonadorJuntaLosDatosDeDyEIncentivosConUnaVersionPorModulo() {
    proyector.proyectar(evento("DONADOR_ACTUALIZADO", "dye", "12", "2026-11-02T15:00:00Z", """
            {"id":"12","nombre":"Lucía","apellido":"Fernández","estado":"VERIFICADO",
             "categoria":"COLABORADOR","cantidadQuejas":3,"eliminado":false}"""));
    // Más viejo que lo de D&E, pero es de otro módulo: igual se aplica.
    var resultado = proyector.proyectar(evento("INCENTIVOS_DONADOR_ACTUALIZADO", "incentivos", "12",
            "2026-11-02T14:00:00Z", """
            {"donadorId":"12","insignias":[{"id":"1","nombre":"Completitud"}],
             "misionEnCurso":{"id":"2","nombre":"Donaciones Exitosas","tipo":"DONACIONES_EXITOSAS","progreso":14,"objetivo":20}}"""));

    assertEquals(APLICADO, resultado);
    Document donador = documento("donadores", "12");
    assertEquals("COLABORADOR", donador.getString("categoria"));
    assertEquals(14, donador.get("misionEnCurso", Document.class).getInteger("progreso"));
    assertEquals(1, donador.getList("insignias", Document.class).size());
    assertNull(donador.get("donadorId"));
  }

  @Test
  void armaElHistorialDeCategoriasConCadaCambio() {
    String donador = """
            {"id":"12","nombre":"Lucía","estado":"VERIFICADO","categoria":"%s","cantidadQuejas":0,"eliminado":false}""";
    proyector.proyectar(evento("DONADOR_ACTUALIZADO", "dye", "12", "2026-03-02T10:00:00Z", donador.formatted("OCASIONAL")));
    proyector.proyectar(evento("DONADOR_ACTUALIZADO", "dye", "12", "2026-05-01T10:00:00Z", donador.formatted("OCASIONAL")));
    proyector.proyectar(evento("DONADOR_ACTUALIZADO", "dye", "12", "2026-06-18T10:00:00Z", donador.formatted("COLABORADOR")));

    List<Document> historial = documento("donadores", "12").getList("historialCategorias", Document.class);
    assertEquals(List.of("OCASIONAL", "COLABORADOR"), historial.stream().map(h -> h.getString("categoria")).toList());
  }

  @Test
  void unTipoDesconocidoSeIgnoraSinGuardarNada() {
    var resultado = proyector.proyectar(evento("ALGO_NUEVO", "otro", "1", "2026-11-02T14:00:00Z", "{\"id\":\"1\"}"));

    assertEquals(IGNORADO_TIPO_DESCONOCIDO, resultado);
  }
}
