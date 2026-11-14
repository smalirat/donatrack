package ar.edu.utn.dds.k3003.model;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.TipoNecesidadMaterialEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.TipoAlgoritmoEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ModelLogisticaTest {

    private Deposito deposito;
    private Producto fideos;

    @BeforeEach
    void setUp() {
        deposito = new Deposito("Depósito Central", "Av. Medrano 951", 1000);
        deposito.setTipoAlgoritmo(TipoAlgoritmoEnum.SUB_ATENDIDOS);

        fideos = new Producto("PROD-FIDEOS");
    }

    @Test
    void noHayAlgoritmoConfigurado() {
        Deposito depositoSinAlgoritmo = new Deposito("Depósito Vacío", "Calle Falsa 123", 100);
        Paquete paquete = new Paquete(1L, "DON-1", fideos, 10);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            depositoSinAlgoritmo.buscarMatch(paquete, List.of());
        });

        assertEquals("El depósito no tiene un algoritmo configurado.", exception.getMessage());
    }

    @Test
    void necesidadExtraordinariaAceptada() {
        Paquete paquete = new Paquete(1L, "DON-1", fideos, 10);
        Necesidad necesidad = new Necesidad("NEC-1", 5, 30, TipoNecesidadMaterialEnum.EXTRAORDINARIA);

        ResultadoMatch resultado = deposito.buscarMatch(paquete, List.of(necesidad));

        assertNotNull(resultado);
        assertNotNull(resultado.getAsignacion());
        assertEquals("NEC-1", resultado.getAsignacion().getNecesidadID());

        assertEquals(10, paquete.getCantidad());

        assertNull(resultado.getPaqueteSobrante()); //no hay sobrante
    }

    @Test
    void necesidadRecurrenteRechazadaPaqueteInsuficiente() {
        Paquete paquete = new Paquete(1L, "DON-1", fideos, 10);
        Necesidad necesidad = new Necesidad("NEC-1", 5, 30, TipoNecesidadMaterialEnum.RECURRENTE);

        ResultadoMatch resultado = deposito.buscarMatch(paquete, List.of(necesidad));

        assertNull(resultado);
    }

    @Test
    void generaSobrante() {
        Paquete paqueteOriginal = new Paquete(1L, "DON-1", fideos, 50);
        Necesidad necesidad = new Necesidad("NEC-1", 5, 20, TipoNecesidadMaterialEnum.EXTRAORDINARIA);

        ResultadoMatch resultado = deposito.buscarMatch(paqueteOriginal, List.of(necesidad));

        assertNotNull(resultado);

        assertEquals(20, paqueteOriginal.getCantidad());

        Paquete sobrante = resultado.getPaqueteSobrante();
        assertNotNull(sobrante);
        assertEquals(30, sobrante.getCantidad());
        assertEquals("DON-1", sobrante.getDonacionID());
    }

    @Test
    void restarStocks() {
        assertEquals(1000, deposito.capacidadDisponible());

        deposito.getStock().add(new Paquete(1L, "DON-1", fideos, 10));
        deposito.getStock().add(new Paquete(2L, "DON-2", fideos, 20));

        assertEquals(970, deposito.capacidadDisponible());
    }

    @Test
    void superaLimiteDeDeposito() {
        deposito.getStock().add(new Paquete(1L, "DON-1", fideos, 1000));

        assertFalse(deposito.tieneCapacidadPara(1));
        assertTrue(deposito.tieneCapacidadPara(0));
    }

    @Test
    void extraerRemanentFalla() {
        Paquete paquete = new Paquete(1L, "DON-1", fideos, 10);

        assertThrows(IllegalArgumentException.class, () -> paquete.extraerRemanente(15));

        assertThrows(IllegalArgumentException.class, () -> paquete.extraerRemanente(10));

        assertThrows(IllegalArgumentException.class, () -> paquete.extraerRemanente(0));
        assertThrows(IllegalArgumentException.class, () -> paquete.extraerRemanente(-5));
    }

    @Test
    void algoritmoScore() {
        AlgoritmoScore algoritmo = new AlgoritmoScore();
        Paquete paquete = new Paquete(1L, "DON-1", fideos, 10);

        Necesidad necBaja = new Necesidad("NEC-BAJA", 1, 100, TipoNecesidadMaterialEnum.EXTRAORDINARIA);

        Necesidad necAlta = new Necesidad("NEC-ALTA", 10, 100, TipoNecesidadMaterialEnum.EXTRAORDINARIA);

        Necesidad ganadora = algoritmo.seleccionarNecesidad(paquete, List.of(necBaja, necAlta));

        assertEquals("NEC-ALTA", ganadora.getId());
    }

    @Test
    void algoritmoScoreManejaPaquteVacio() {
        AlgoritmoScore algoritmo = new AlgoritmoScore();
        Paquete paqueteVacio = new Paquete(1L, "DON-1", fideos, 0);

        Necesidad necesidad = new Necesidad("NEC-1", 5, 100, TipoNecesidadMaterialEnum.EXTRAORDINARIA);

        Necesidad ganadora = algoritmo.seleccionarNecesidad(paqueteVacio, List.of(necesidad));

        assertEquals("NEC-1", ganadora.getId());
    }

    @Test
    void completarEntrega() {
        Asignacion asignacion = new Asignacion();
        asignacion.setEstado(EstadoAsignacion.ASIGNADA);

        asignacion.completarEntrega();

        assertEquals(EstadoAsignacion.COMPLETADA, asignacion.getEstado());
    }

    @Test
    void stockDeSumaSoloLosPaquetesDelProducto() {
        Producto sillas = new Producto("PROD-SILLAS");
        deposito.getStock().add(new Paquete(1L, "DON-1", fideos, 30));
        deposito.getStock().add(new Paquete(2L, "DON-2", fideos, 20));
        deposito.getStock().add(new Paquete(3L, "DON-3", sillas, 5));

        assertEquals(50, deposito.stockDe("PROD-FIDEOS"));
        assertEquals(5, deposito.stockDe("PROD-SILLAS"));
        assertEquals(0, deposito.stockDe("PROD-ARROZ"));
    }

    @Test
    void extraordinariaDescuentaLoPendienteDeEntrega() {
        Necesidad necesidad = new Necesidad("NEC-1", 5, 30, TipoNecesidadMaterialEnum.EXTRAORDINARIA);

        necesidad.descontarPendiente(20);

        assertEquals(10, necesidad.getCantidadObjetivo());
        assertFalse(necesidad.estaCubierta());
    }

    @Test
    void recurrenteConAsignacionPendienteQuedaCubierta() {
        Necesidad necesidad = new Necesidad("NEC-1", 5, 100, TipoNecesidadMaterialEnum.RECURRENTE);

        necesidad.descontarPendiente(1);

        assertTrue(necesidad.estaCubierta());
    }

    @Test
    void noSeAsignaANecesidadCubiertaPorPendientes() {
        Paquete paquete = new Paquete(1L, "DON-1", fideos, 30);
        Necesidad cubierta = new Necesidad("NEC-1", 10, 30, TipoNecesidadMaterialEnum.EXTRAORDINARIA);
        cubierta.descontarPendiente(30);
        Necesidad libre = new Necesidad("NEC-2", 1, 5, TipoNecesidadMaterialEnum.EXTRAORDINARIA);

        ResultadoMatch resultado = deposito.buscarMatch(paquete, List.of(cubierta, libre));

        assertNotNull(resultado);
        assertEquals("NEC-2", resultado.getAsignacion().getNecesidadID());
    }

    @Test
    void sinMatchSiTodasEstanCubiertasPorPendientes() {
        Paquete paquete = new Paquete(1L, "DON-1", fideos, 120);
        Necesidad recurrente = new Necesidad("NEC-1", 5, 100, TipoNecesidadMaterialEnum.RECURRENTE);
        recurrente.descontarPendiente(120);

        assertNull(deposito.buscarMatch(paquete, List.of(recurrente)));
    }

    @Test
    void buscarEnStockEncuentraSoloPaquetesDelDeposito() {
        deposito.getStock().add(new Paquete(1L, "DON-1", fideos, 10));

        assertTrue(deposito.buscarEnStock(1L).isPresent());
        assertTrue(deposito.buscarEnStock(2L).isEmpty());
    }

    @Test
    void porcentajeOcupacion() {
        deposito.getStock().add(new Paquete(1L, "DON-1", fideos, 250));

        assertEquals(25.0, deposito.porcentajeOcupacion());
    }

    @Test
    void elDepositoMarcaSuOrigenEnLosPaquetesQueRecibe() {
        deposito.setId(3L);
        Paquete paquete = new Paquete(1L, "DON-1", fideos, 50);

        deposito.recibirEnStock(paquete);

        assertEquals(3L, paquete.getDepositoOrigenId());
        assertTrue(deposito.buscarEnStock(1L).isPresent());
    }

    @Test
    void elSobranteConservaElDepositoDeOrigen() {
        deposito.setId(3L);
        Paquete paquete = new Paquete(1L, "DON-1", fideos, 50);
        deposito.recibirEnStock(paquete);

        Paquete sobrante = paquete.extraerRemanente(20);

        assertEquals(3L, sobrante.getDepositoOrigenId());
    }

    @Test
    void completarEntregaRegistraLaFechaDeEntrega() {
        Asignacion asignacion = new Asignacion();
        asignacion.setEstado(EstadoAsignacion.ASIGNADA);

        asignacion.completarEntrega();

        assertNotNull(asignacion.getFechaEntrega());
    }
}
