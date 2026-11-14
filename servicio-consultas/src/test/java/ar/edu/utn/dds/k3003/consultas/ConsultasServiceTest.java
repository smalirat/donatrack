package ar.edu.utn.dds.k3003.consultas;

import ar.edu.utn.dds.k3003.eventos.ConversorDocumento;
import ar.edu.utn.dds.k3003.eventos.ProyectorEventos;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

import static ar.edu.utn.dds.k3003.Eventos.evento;
import static org.junit.jupiter.api.Assertions.*;

// El modelo de lectura se carga con eventos de los cuatro módulos, como llegarían por la cola.
@DataMongoTest
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({ProyectorEventos.class, ConversorDocumento.class, ConsultasService.class})
class ConsultasServiceTest {

  @Autowired
  private ProyectorEventos proyector;
  @Autowired
  private ConsultasService consultas;
  @Autowired
  private MongoTemplate mongo;

  private void publicar(String tipo, String modulo, String id, String datos) {
    proyector.proyectar(evento(tipo, modulo, id, "2026-11-04T12:00:00Z", datos));
  }

  private static double numero(Object valor) {
    return ((Number) valor).doubleValue();
  }

  @BeforeEach
  void cargarModelo() {
    mongo.getDb().drop();

    publicar("PRODUCTO_ACTUALIZADO", "donaciones", "7", """
            {"id":"7","nombre":"Fideos guiseros","subcategoria":{"id":"4","nombre":"Fideos"},
             "categoria":{"id":"1","nombre":"Alimentos"},"eliminado":false}""");
    publicar("PRODUCTO_ACTUALIZADO", "donaciones", "5", """
            {"id":"5","nombre":"Silla","subcategoria":{"id":"8","nombre":"Sillas"},
             "categoria":{"id":"2","nombre":"Mobiliario"},"eliminado":false}""");

    publicar("NECESIDAD_ACTUALIZADA", "dye", "9", """
            {"id":"9","entidad":{"id":"4","razonSocial":"Comedor Los Girasoles"},"productoSolicitadoId":"7",
             "descripcion":"Fideos semanales","nivelDeUrgencia":8,"tipo":"RECURRENTE","cantidadFaltante":100,
             "satisfecha":false,"eliminado":false}""");
    publicar("NECESIDAD_ACTUALIZADA", "dye", "10", """
            {"id":"10","entidad":{"id":"5","razonSocial":"Escuela Rural 12"},"productoSolicitadoId":"5",
             "descripcion":"Sillas tras inundación","nivelDeUrgencia":3,"tipo":"EXTRAORDINARIA","cantidadFaltante":30,
             "satisfecha":false,"eliminado":false}""");
    publicar("NECESIDAD_ACTUALIZADA", "dye", "11", """
            {"id":"11","entidad":{"id":"4","razonSocial":"Comedor Los Girasoles"},"productoSolicitadoId":"7",
             "descripcion":"Ya cubierta","nivelDeUrgencia":10,"tipo":"EXTRAORDINARIA","cantidadFaltante":0,
             "satisfecha":true,"eliminado":false}""");

    publicar("DEPOSITO_ACTUALIZADO", "logistica", "3", """
            {"id":"3","nombre":"Central","capacidadMaxima":1000,"algoritmo":"PRIORIDAD_POR_SCORE","stockTotal":420,
             "stockPorProducto":[{"productoId":"7","cantidad":300},{"productoId":"2","cantidad":120}],"eliminado":false}""");
    publicar("DEPOSITO_ACTUALIZADO", "logistica", "4", """
            {"id":"4","nombre":"Norte","capacidadMaxima":100,"algoritmo":"SUB_ATENDIDOS","stockTotal":10,
             "stockPorProducto":[{"productoId":"5","cantidad":10}],"eliminado":false}""");

    publicar("DONACION_ACTUALIZADA", "donaciones", "a1", """
            {"id":"a1","donadorId":"12","depositoId":"3","producto":{"id":"7"},"subcategoria":{"id":"4","nombre":"Fideos"},
             "categoria":{"id":"1","nombre":"Alimentos"},"cantidad":30,"estado":"ACEPTADA",
             "fechaIngreso":"2026-11-02T10:00:00Z","eliminado":false}""");
    publicar("DONACION_ACTUALIZADA", "donaciones", "a2", """
            {"id":"a2","donadorId":"12","depositoId":"4","producto":{"id":"5"},"subcategoria":{"id":"8","nombre":"Sillas"},
             "categoria":{"id":"2","nombre":"Mobiliario"},"cantidad":5,"estado":"CONQUEJA",
             "fechaIngreso":"2026-11-01T10:00:00Z","eliminado":false}""");
    publicar("DONACION_ACTUALIZADA", "donaciones", "a3", """
            {"id":"a3","donadorId":"13","depositoId":"3","producto":{"id":"7"},"subcategoria":{"id":"4","nombre":"Fideos"},
             "categoria":{"id":"1","nombre":"Alimentos"},"cantidad":20,"estado":"INGRESADA",
             "fechaIngreso":"2026-11-03T09:00:00Z","eliminado":false}""");

    // Entregada 12 h después del ingreso de la donación a1.
    publicar("ASIGNACION_ACTUALIZADA", "logistica", "42", """
            {"id":"42","necesidadId":"9","origen":"MATCHMAKING","estado":"COMPLETADA",
             "fechaCreacion":"2026-11-02T10:05:00Z","fechaEntrega":"2026-11-02T22:00:00Z",
             "paquetes":[{"paqueteId":"15","donacionId":"a1","productoId":"7","depositoId":"3","cantidad":30}],"eliminado":false}""");
    // Entregada 6 h después del ingreso de la donación a2.
    publicar("ASIGNACION_ACTUALIZADA", "logistica", "43", """
            {"id":"43","necesidadId":"10","origen":"MATCHMAKING","estado":"COMPLETADA",
             "fechaCreacion":"2026-11-01T10:05:00Z","fechaEntrega":"2026-11-01T16:00:00Z",
             "paquetes":[{"paqueteId":"16","donacionId":"a2","productoId":"5","depositoId":"4","cantidad":5}],"eliminado":false}""");
    publicar("ASIGNACION_ACTUALIZADA", "logistica", "44", """
            {"id":"44","necesidadId":"9","origen":"MATCHMAKING","estado":"ASIGNADA",
             "fechaCreacion":"2026-11-03T09:05:00Z","fechaEntrega":null,
             "paquetes":[{"paqueteId":"17","donacionId":"a3","productoId":"7","depositoId":"3","cantidad":20}],"eliminado":false}""");

    publicar("DONADOR_ACTUALIZADO", "dye", "12", """
            {"id":"12","nombre":"Lucía","apellido":"Fernández","estado":"VERIFICADO","categoria":"COLABORADOR",
             "cantidadQuejas":3,"eliminado":false}""");
    publicar("DONADOR_ACTUALIZADO", "dye", "13", """
            {"id":"13","nombre":"Juan","apellido":"Pérez","estado":"VERIFICADO","categoria":"OCASIONAL",
             "cantidadQuejas":0,"eliminado":false}""");
    publicar("DONADOR_ACTUALIZADO", "dye", "14", """
            {"id":"14","nombre":"Ana","apellido":"Gómez","estado":"SOSPECHOSO","categoria":"OCASIONAL",
             "cantidadQuejas":7,"eliminado":false}""");
    publicar("INCENTIVOS_DONADOR_ACTUALIZADO", "incentivos", "12", """
            {"donadorId":"12","insignias":[{"id":"1","nombre":"Completitud"}],
             "misionEnCurso":{"id":"2","nombre":"Donaciones Exitosas","tipo":"DONACIONES_EXITOSAS","progreso":14,"objetivo":20}}""");
  }

  // ---------------------------------------------------------------------------------------------
  // 3. Estado de los depósitos
  // ---------------------------------------------------------------------------------------------
  @Test
  void estadoDeLosDepositos() {
    List<Document> depositos = consultas.estadoDepositos(null, null, null);

    assertEquals(2, depositos.size());
    Document central = depositos.get(0);
    assertEquals("3", central.getString("depositoId"));
    assertEquals("PRIORIDAD_POR_SCORE", central.getString("algoritmo"));
    assertEquals(42.0, numero(central.get("ocupacionPorcentaje")));
    Document porEstado = central.get("asignacionesPorEstado", Document.class);
    assertEquals(1, porEstado.getInteger("COMPLETADA"));
    assertEquals(1, porEstado.getInteger("ASIGNADA"));
    assertEquals(1, central.getInteger("entidadesAtendidas"));
    assertEquals(12.0, numero(central.get("tiempoPromedioEntregaHoras")));

    Document norte = depositos.get(1);
    assertEquals(10.0, numero(norte.get("ocupacionPorcentaje")));
    assertEquals(6.0, numero(norte.get("tiempoPromedioEntregaHoras")));
  }

  @Test
  void estadoDeUnDepositoSinEntregasEnElRangoNoTienePromedio() {
    List<Document> depositos = consultas.estadoDepositos("3", LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 5));

    assertEquals(1, depositos.size());
    assertNull(depositos.get(0).get("tiempoPromedioEntregaHoras"));
    assertEquals(0, depositos.get(0).getInteger("entregasMedidas"));
  }

  // ---------------------------------------------------------------------------------------------
  // 2. Necesidades urgentes sin cubrir
  // ---------------------------------------------------------------------------------------------
  @Test
  void necesidadesUrgentesOrdenadasPorUrgenciaConStockYPendiente() {
    List<Document> necesidades = consultas.necesidadesUrgentes(null, null, null, null);

    assertEquals(List.of("9", "10"), necesidades.stream().map(n -> n.getString("necesidadId")).toList());
    Document fideos = necesidades.get(0);
    assertEquals("Comedor Los Girasoles", fideos.get("entidad", Document.class).getString("razonSocial"));
    assertEquals("Alimentos", fideos.get("producto", Document.class).get("categoria", Document.class).getString("nombre"));
    List<Document> stock = fideos.getList("stockPorDeposito", Document.class);
    assertEquals(1, stock.size());
    assertEquals("3", stock.get(0).getString("depositoId"));
    assertEquals(300, stock.get(0).getInteger("cantidad"));
    assertEquals(20, fideos.getInteger("unidadesAsignadasSinEntregar"));
    assertEquals(0, necesidades.get(1).getInteger("unidadesAsignadasSinEntregar"));
  }

  @Test
  void necesidadesUrgentesConFiltros() {
    assertEquals(List.of("9"), ids(consultas.necesidadesUrgentes(5, null, null, null)));
    assertEquals(List.of("10"), ids(consultas.necesidadesUrgentes(null, "EXTRAORDINARIA", null, null)));
    assertEquals(List.of("10"), ids(consultas.necesidadesUrgentes(null, null, "2", null)));
    assertEquals(List.of("9"), ids(consultas.necesidadesUrgentes(null, null, null, "4")));
  }

  private List<String> ids(List<Document> necesidades) {
    return necesidades.stream().map(n -> n.getString("necesidadId")).toList();
  }

  // ---------------------------------------------------------------------------------------------
  // 1. Ficha integral de un donador
  // ---------------------------------------------------------------------------------------------
  @Test
  void fichaIntegralDelDonador() {
    Document ficha = consultas.fichaDonador("12");

    assertEquals("COLABORADOR", ficha.getString("categoria"));
    assertEquals(3, ficha.getInteger("quejasRecibidas"));
    assertEquals(14, ficha.get("misionEnCurso", Document.class).getInteger("progreso"));
    assertEquals(1, ficha.getList("insignias", Document.class).size());
    assertEquals(1, ficha.getList("historialCategorias", Document.class).size());

    Document porEstado = ficha.get("donacionesPorEstado", Document.class);
    assertEquals(1, porEstado.getInteger("ACEPTADA"));
    assertEquals(1, porEstado.getInteger("CONQUEJA"));

    List<Document> entidades = ficha.getList("entidadesBeneficiadas", Document.class);
    assertEquals(List.of("4", "5"), entidades.stream().map(e -> e.getString("entidadId")).toList());
    assertEquals(30, entidades.get(0).getInteger("unidades"));
    assertEquals("Escuela Rural 12", entidades.get(1).getString("razonSocial"));
  }

  @Test
  void fichaDeUnDonadorQueNoEstaEnElModelo() {
    assertThrows(NoSuchElementException.class, () -> consultas.fichaDonador("999"));
  }

  // ---------------------------------------------------------------------------------------------
  // 4. Calidad de lo donado
  // ---------------------------------------------------------------------------------------------
  @Test
  void calidadDeLoDonado() {
    Document calidad = consultas.calidadDonado(10);

    List<Document> porCategoria = calidad.getList("porCategoria", Document.class);
    assertEquals("Sillas", porCategoria.get(0).getString("subcategoria"));
    assertEquals(1.0, numero(porCategoria.get(0).get("proporcionConQueja")));
    assertEquals("Fideos", porCategoria.get(1).getString("subcategoria"));
    assertEquals(2, porCategoria.get(1).getInteger("total"));
    assertEquals(0.0, numero(porCategoria.get(1).get("proporcionConQueja")));

    List<Document> ranking = calidad.getList("donadoresConMasQuejas", Document.class);
    assertEquals(List.of("14", "12"), ranking.stream().map(d -> d.getString("donadorId")).toList());
    assertEquals("SOSPECHOSO", ranking.get(0).getString("estado"));
  }

  // Como las manda Donaciones: la subcategoría solo tiene id.
  @Test
  void calidadAgrupaPorIdsAunqueLaSubcategoriaNoTengaNombre() {
    mongo.getCollection("donaciones").drop();
    publicar("DONACION_ACTUALIZADA", "donaciones", "b1", """
            {"id":"b1","donadorId":"12","producto":{"id":"7"},"subcategoria":{"id":"1","nombre":null},
             "categoria":{"id":"2","nombre":"Alimentos"},"cantidad":5,"estado":"CONQUEJA","eliminado":false}""");
    publicar("DONACION_ACTUALIZADA", "donaciones", "b2", """
            {"id":"b2","donadorId":"13","producto":{"id":"7"},"subcategoria":{"id":"1","nombre":null},
             "categoria":{"id":"2","nombre":null},"cantidad":5,"estado":"ACEPTADA","eliminado":false}""");

    List<Document> porCategoria = consultas.calidadDonado(10).getList("porCategoria", Document.class);

    assertEquals(1, porCategoria.size());
    Document grupo = porCategoria.get(0);
    assertEquals("2", grupo.getString("categoriaId"));
    assertEquals("Alimentos", grupo.getString("categoria"));
    assertEquals("1", grupo.getString("subcategoriaId"));
    assertNull(grupo.getString("subcategoria"));
    assertEquals(2, grupo.getInteger("total"));
    assertEquals(0.5, numero(grupo.get("proporcionConQueja")));
  }
}
