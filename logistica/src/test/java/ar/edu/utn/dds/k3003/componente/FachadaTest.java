package ar.edu.utn.dds.k3003.componente;

import ar.edu.utn.dds.k3003.Fachada;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.EstadoDonacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.TipoNecesidadMaterialEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.*;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonaciones;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonadoresYEntidades;
import ar.edu.utn.dds.k3003.eventos.PublicadorEventos;
import ar.edu.utn.dds.k3003.exceptions.AsignacionNoEncontradaException;
import ar.edu.utn.dds.k3003.exceptions.DepositoNoEncontradoException;
import ar.edu.utn.dds.k3003.exceptions.DepositoSinCapacidadException;
import ar.edu.utn.dds.k3003.model.*;
import ar.edu.utn.dds.k3003.repositories.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import io.micrometer.core.instrument.Timer;

@ExtendWith(MockitoExtension.class)
public class FachadaTest {

    @Mock
    private DepositoRepository depositoRepository;
    @Mock
    private AsignacionRepository asignacionRepository;
    @Mock
    private PaqueteRepository paqueteRepository;
    @Mock
    private MeterRegistry meterRegistry;
    @Mock
    private Counter mockCounter;

    private Fachada fachada;

    @Mock
    private FachadaDonadoresYEntidades fachadaDonadores;
    @Mock
    private FachadaDonaciones fachadaDonaciones;

    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private PublicadorEventos publicadorEventos;

    @Mock
    private Timer mockTimer;

    @BeforeEach
    void setUp() {
        when(meterRegistry.counter(anyString())).thenReturn(mockCounter);
        lenient().when(meterRegistry.counter(anyString())).thenReturn(mockCounter);
        lenient().when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(mockCounter);
        lenient().when(meterRegistry.counter(anyString(), any(Iterable.class))).thenReturn(mockCounter);
        lenient().when(meterRegistry.timer(anyString())).thenReturn(mockTimer);
        lenient().when(meterRegistry.timer(anyString(), any(String[].class))).thenReturn(mockTimer);
        lenient().when(meterRegistry.timer(anyString(), any(Iterable.class))).thenReturn(mockTimer);
        fachada = new Fachada(depositoRepository, asignacionRepository, paqueteRepository, meterRegistry, rabbitTemplate,
                publicadorEventos);
        fachada.setFachadaDonadoresYEntidades(fachadaDonadores);
        fachada.setFachadaDonaciones(fachadaDonaciones);
    }


    @Test
    void agregarDeposito_FallaSiIDYaExiste() {
        DepositoDTO dto = new DepositoDTO("1", TipoAlgoritmoEnum.PRIORIDAD_POR_SCORE, "Depo 1", "Direccion", 100, List.of());

        when(depositoRepository.findById(1L)).thenReturn(Optional.of(new Deposito()));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> fachada.agregarDeposito(dto));
        assertEquals("El depósito con ese ID ya existe", exception.getMessage());
    }

    @Test
    void agregarDepositoAlgoritmoNulo() {
        DepositoDTO dtoIn = new DepositoDTO(null, null, "Depo 1", "Direccion", 100, List.of());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> fachada.agregarDeposito(dtoIn));
        assertEquals("El depósito debe tener un algoritmo configurado", exception.getMessage());
    }

    @Test
    void agregarDeposito() {
        DepositoDTO dtoIn = new DepositoDTO(null, TipoAlgoritmoEnum.SUB_ATENDIDOS, "Depo 1", "Direccion", 100, List.of());

        Deposito depositoGuardado = new Deposito("Depo 1", "Direccion", 100);
        depositoGuardado.setId(1L);
        depositoGuardado.setTipoAlgoritmo(TipoAlgoritmoEnum.SUB_ATENDIDOS);

        when(depositoRepository.save(any(Deposito.class))).thenReturn(depositoGuardado);

        DepositoDTO dtoOut = fachada.agregarDeposito(dtoIn);

        assertNotNull(dtoOut);
        assertEquals("1", dtoOut.id());
        verify(depositoRepository, times(1)).save(any(Deposito.class));
        verify(publicadorEventos, times(1)).depositoActualizado(depositoGuardado);
        verify(mockCounter, times(1)).increment();
    }


    @Test
    void buscarDepositoPorIDQueNoExiste() {
        when(depositoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> fachada.buscarDepositoPorID("99"));
    }

    @Test
    void obtenerTodosLosDepositos() {
        Deposito d1 = new Deposito("D1", "Dir1", 100);
        d1.setId(1L);
        when(depositoRepository.findAll()).thenReturn(List.of(d1));

        List depositos = fachada.obtenerTodosLosDepositos();

        assertEquals(1, depositos.size());

        assertEquals("1", ((DepositoDTO) depositos.get(0)).id());
    }


    @Test
    void eliminarDeposito() {
        Fachada fachadaConMetricas = fachadaConRegistry(new SimpleMeterRegistry());
        Deposito d1 = new Deposito("D1", "Dir1", 100);
        d1.setId(1L);
        when(depositoRepository.findById(1L)).thenReturn(Optional.of(d1));

        DepositoDTO dtoOut = fachadaConMetricas.eliminarDeposito("1");

        assertNotNull(dtoOut);
        verify(depositoRepository, times(1)).deleteById(1L);
        verify(publicadorEventos, times(1)).depositoEliminado(d1);
    }


    @Test
    void asignacionNoEncontrada() {
        when(asignacionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(AsignacionNoEncontradaException.class, () -> fachada.buscarAsignacionPorId("1"));
    }

    @Test
    void buscarAsignacionPorPaqueteIDFalla() {
        when(asignacionRepository.findByPaquetes_Id(1L)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> fachada.buscarAsignacionPorPaqueteID("1"));
        assertEquals("No hay asignación para el paquete: 1", exception.getMessage());
    }

    @Test
    void buscarAsignacionPorPaqueteID() {
        Asignacion asignacion = new Asignacion();
        asignacion.setId(1L);
        asignacion.setNecesidadID("NEC-1");
        asignacion.setEstado(EstadoAsignacion.ASIGNADA);
        asignacion.setOrigen(OrigenAsignacion.MATCHMAKING);

        when(asignacionRepository.findByPaquetes_Id(10L)).thenReturn(Optional.of(asignacion));

        AsignacionDTO dtoOut = fachada.buscarAsignacionPorPaqueteID("10");

        assertNotNull(dtoOut);
        assertEquals("1", dtoOut.id());
        assertEquals("NEC-1", dtoOut.necesidadID());
    }

    @Test
    void setAlgoritmoMM() {
        Deposito deposito = new Deposito();
        when(depositoRepository.findById(1L)).thenReturn(Optional.of(deposito));

        fachada.setAlgoritmoMM("1", TipoAlgoritmoEnum.PRIORIDAD_POR_SCORE);

        assertEquals(TipoAlgoritmoEnum.PRIORIDAD_POR_SCORE, deposito.getTipoAlgoritmo());
        verify(depositoRepository, times(1)).save(deposito);
    }

    /**
    @Test
    void noHayMatch() {
        Deposito depositoSpy = spy(new Deposito("D1", "Dir1", 100));
        depositoSpy.setId(1L);
        doReturn(null).when(depositoSpy).buscarMatch(any(), any());

        when(depositoRepository.findById(1L)).thenReturn(Optional.of(depositoSpy));

        Paquete paqueteBd = new Paquete(10L, "DON-1", new Producto("PROD-1"), 10);
        lenient().when(paqueteRepository.findById(10L)).thenReturn(Optional.of(paqueteBd));

        PaqueteDTO paqueteDTO = new PaqueteDTO("10", "DON-1", "PROD-1", 10);

        AsignacionDTO resultado = fachada.ejecutarMatchmaking("1", paqueteDTO, List.of());

        assertNull(resultado);
        verify(asignacionRepository, never()).save(any());
    } **/

    @Test
    void gestionarDonacion_FallaSiCantidadEsNulaOMenorACero() {
        RuntimeException nulaEx = assertThrows(RuntimeException.class,
                () -> fachada.gestionarDonacion("1", "DON-1", "PROD-1", null));
        assertEquals("La cantidad debe ser mayor a cero.", nulaEx.getMessage());

        RuntimeException ceroEx = assertThrows(RuntimeException.class,
                () -> fachada.gestionarDonacion("1", "DON-1", "PROD-1", 0));
        assertEquals("La cantidad debe ser mayor a cero.", ceroEx.getMessage());
    }

    @Test
    void gestionarDonacion_FallaSiDepositoNoExiste() {
        when(depositoRepository.findById(1L)).thenReturn(Optional.empty());

        DepositoNoEncontradoException exception = assertThrows(DepositoNoEncontradoException.class,
                () -> fachada.gestionarDonacion("1", "DON-1", "PROD-1", 10));
        assertEquals("No se encontró el depósito: 1", exception.getMessage());
    }

    @Test
    void gestionarDonacion_FallaSiNoHayCapacidad() {
        Deposito depositoSpy = spy(new Deposito("D1", "Dir1", 100));
        doReturn(false).when(depositoSpy).tieneCapacidadPara(10); // Simulamos que está lleno

        when(depositoRepository.findById(1L)).thenReturn(Optional.of(depositoSpy));

        DepositoSinCapacidadException exception = assertThrows(DepositoSinCapacidadException.class,
                () -> fachada.gestionarDonacion("1", "DON-1", "PROD-1", 10));
        assertEquals("El depósito no tiene capacidad suficiente para recibir la donación.", exception.getMessage());
    }

    @Test
    void gestionarDonacion_GuardaPaqueteYPublicaMensajeEnCola() {
        Deposito depositoSpy = spy(new Deposito("D1", "Dir1", 100));
        depositoSpy.setId(1L);
        doReturn(true).when(depositoSpy).tieneCapacidadPara(10);

        when(depositoRepository.findById(1L)).thenReturn(Optional.of(depositoSpy));
        when(paqueteRepository.save(any(Paquete.class))).thenAnswer(invocation -> {
            Paquete p = invocation.getArgument(0);
            p.setId(5L);
            return p;
        });

        PaqueteDTO resultado = fachada.gestionarDonacion("1", "DON-1", "PROD-1", 10);

        assertNotNull(resultado);
        assertEquals(1, depositoSpy.getStock().size());
        assertEquals("DON-1", depositoSpy.getStock().get(0).getDonacionID());

        verify(depositoRepository, times(1)).save(depositoSpy);
        verify(rabbitTemplate, times(1)).convertAndSend(eq("donaciones_nuevas_queue"), eq("5,1"));
        verify(mockCounter, times(1)).increment(10.0);
    }

    @Test
    void reportarEntregaFallaSiNoEncuentraPaquete() {
        PaqueteDTO dto = new PaqueteDTO("1", "DON-1", "PROD-1", 10);
        when(paqueteRepository.findById(1L)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> fachada.reportarEntrega(dto));
        assertEquals("No se encontró el paquete con ID: 1", exception.getMessage());
    }

    @Test
    void reportarEntregaFallaSiNoEncuentraAsignacionParaElPaquete() {
        PaqueteDTO dto = new PaqueteDTO("10", "DON-1", "PROD-1", 10);
        Paquete paqueteEncontrado = new Paquete(10L, "DON-1", new Producto("PROD-1"), 10);

        when(paqueteRepository.findById(10L)).thenReturn(Optional.of(paqueteEncontrado));
        when(asignacionRepository.findByPaquetes_Id(10L)).thenReturn(Optional.empty());

        AsignacionNoEncontradaException exception = assertThrows(AsignacionNoEncontradaException.class,
                () -> fachada.reportarEntrega(dto));
        assertEquals("No se encontró asignación para el paquete: 10", exception.getMessage());
    }

    @Test
    void reportarEntrega() {
        PaqueteDTO paqueteDTO = new PaqueteDTO("5", "DON-1", "PROD-1", 10);
        Paquete paqueteBd = new Paquete(5L, "DON-1", new Producto("PROD-1"), 10);

        Asignacion asignacionBd = new Asignacion();
        asignacionBd.setId(1L);
        asignacionBd.setNecesidadID("NEC-1");
        asignacionBd.setEstado(EstadoAsignacion.ASIGNADA);
        asignacionBd.setOrigen(OrigenAsignacion.MATCHMAKING); // <--- TIENE QUE ESTAR SÍ O SÍ
        asignacionBd.setPaquetes(List.of(paqueteBd));

        when(paqueteRepository.findById(5L)).thenReturn(Optional.of(paqueteBd));
        when(asignacionRepository.findByPaquetes_Id(5L)).thenReturn(Optional.of(asignacionBd));
        when(asignacionRepository.save(any(Asignacion.class))).thenReturn(asignacionBd);
        when(fachadaDonaciones.buscarDonacionPorID("DON-1")).thenReturn(donacionEnEstado(EstadoDonacionEnum.INGRESADA));

        fachada.reportarEntrega(paqueteDTO);

        verify(fachadaDonadores, times(1)).satisfacerNecesidad("NEC-1", 10);
        verify(fachadaDonaciones, times(1)).cambiarEstadoDeDonacion("DON-1", EstadoDonacionEnum.ACEPTADA);

        assertEquals(EstadoAsignacion.COMPLETADA, asignacionBd.getEstado());
        verify(asignacionRepository, times(1)).save(asignacionBd);
        verify(publicadorEventos, times(1)).asignacionActualizada(asignacionBd);
        verify(mockCounter, times(1)).increment();
    }

    @Test
    void asignarStockANecesidad_RecurrenteConStockInsuficienteNoAsigna() {
        Deposito deposito = new Deposito("D1", "Dir1", 100);
        Paquete enStock = new Paquete(10L, "DON-1", new Producto("PROD-1"), 60);
        deposito.getStock().add(enStock);
        when(depositoRepository.findAll()).thenReturn(List.of(deposito));

        NecesidadMaterialDTO necesidad = new NecesidadMaterialDTO("NEC-1", "ENT-1", 5, "Fideos", 100,
                "PROD-1", TipoNecesidadMaterialEnum.RECURRENTE);

        assertNull(fachada.asignarStockANecesidad(necesidad));
        assertEquals(60, enStock.getCantidad());
        verify(asignacionRepository, never()).save(any());
    }

    @Test
    void asignarStockANecesidad_RecurrenteConStockSuficienteAsigna() {
        Deposito deposito = new Deposito("D1", "Dir1", 200);
        Paquete enStock = new Paquete(10L, "DON-1", new Producto("PROD-1"), 120);
        deposito.getStock().add(enStock);
        when(depositoRepository.findAll()).thenReturn(List.of(deposito));
        when(asignacionRepository.save(any(Asignacion.class))).thenAnswer(inv -> inv.getArgument(0));

        NecesidadMaterialDTO necesidad = new NecesidadMaterialDTO("NEC-1", "ENT-1", 5, "Fideos", 100,
                "PROD-1", TipoNecesidadMaterialEnum.RECURRENTE);

        AsignacionDTO resultado = fachada.asignarStockANecesidad(necesidad);

        assertNotNull(resultado);
        assertEquals(100, enStock.getCantidad());
        assertEquals(20, deposito.getStock().stream().mapToInt(Paquete::getCantidad).sum());
        verify(publicadorEventos, times(1)).asignacionActualizada(any(Asignacion.class));
        verify(publicadorEventos, times(1)).depositoActualizado(deposito);
    }

    @Test
    void asignarStockANecesidad_RecurrenteConAsignacionPendienteNoAsigna() {
        Paquete pendiente = new Paquete(5L, "DON-0", new Producto("PROD-1"), 100);
        Asignacion asignacionPendiente = new Asignacion(1L, List.of(pendiente), "NEC-1", null,
                EstadoAsignacion.ASIGNADA, OrigenAsignacion.MATCHMAKING);
        when(asignacionRepository.findByNecesidadIDAndEstado("NEC-1", EstadoAsignacion.ASIGNADA))
                .thenReturn(List.of(asignacionPendiente));

        NecesidadMaterialDTO necesidad = new NecesidadMaterialDTO("NEC-1", "ENT-1", 5, "Fideos", 100,
                "PROD-1", TipoNecesidadMaterialEnum.RECURRENTE);

        assertNull(fachada.asignarStockANecesidad(necesidad));
        verify(depositoRepository, never()).findAll();
        verify(asignacionRepository, never()).save(any());
    }

    @Test
    void asignarStockANecesidad_ExtraordinariaSoloAsignaLoQueFaltaDescontandoPendientes() {
        Paquete pendiente = new Paquete(5L, "DON-0", new Producto("PROD-1"), 20);
        Asignacion asignacionPendiente = new Asignacion(1L, List.of(pendiente), "NEC-1", null,
                EstadoAsignacion.ASIGNADA, OrigenAsignacion.MATCHMAKING);
        when(asignacionRepository.findByNecesidadIDAndEstado("NEC-1", EstadoAsignacion.ASIGNADA))
                .thenReturn(List.of(asignacionPendiente));

        Deposito deposito = new Deposito("D1", "Dir1", 100);
        Paquete enStock = new Paquete(10L, "DON-1", new Producto("PROD-1"), 50);
        deposito.getStock().add(enStock);
        when(depositoRepository.findAll()).thenReturn(List.of(deposito));
        when(asignacionRepository.save(any(Asignacion.class))).thenAnswer(inv -> inv.getArgument(0));

        NecesidadMaterialDTO necesidad = new NecesidadMaterialDTO("NEC-1", "ENT-1", 5, "Sillas", 30,
                "PROD-1", TipoNecesidadMaterialEnum.EXTRAORDINARIA);

        AsignacionDTO resultado = fachada.asignarStockANecesidad(necesidad);

        assertNotNull(resultado);
        assertEquals(10, enStock.getCantidad());
        assertEquals(40, deposito.getStock().stream().mapToInt(Paquete::getCantidad).sum());
    }

    private DonacionDTO donacionEnEstado(EstadoDonacionEnum estado) {
        return new DonacionDTO("DON-1", "DONADOR-1", "1", "desc", "PROD-1", 10, estado);
    }

    private Asignacion asignacionDelPaquete(Paquete paquete, EstadoAsignacion estado) {
        return new Asignacion(1L, List.of(paquete), "NEC-1", null, estado, OrigenAsignacion.MATCHMAKING);
    }

    @Test
    void reportarEntrega_SiFallaDyENoCompletaLaAsignacion() {
        Paquete paqueteBd = new Paquete(5L, "DON-1", new Producto("PROD-1"), 10);
        Asignacion asignacionBd = asignacionDelPaquete(paqueteBd, EstadoAsignacion.ASIGNADA);
        when(paqueteRepository.findById(5L)).thenReturn(Optional.of(paqueteBd));
        when(asignacionRepository.findByPaquetes_Id(5L)).thenReturn(Optional.of(asignacionBd));
        when(fachadaDonadores.satisfacerNecesidad("NEC-1", 10)).thenThrow(new RuntimeException("D&E caído"));

        assertThrows(RuntimeException.class,
                () -> fachada.reportarEntrega(new PaqueteDTO("5", "DON-1", "PROD-1", 10)));

        assertEquals(EstadoAsignacion.ASIGNADA, asignacionBd.getEstado());
        verify(asignacionRepository, never()).save(any());
        verify(fachadaDonaciones, never()).cambiarEstadoDeDonacion(any(), any());
    }

    @Test
    void reportarEntrega_ReintentoSobreAsignacionCompletadaSoloActualizaDonaciones() {
        Paquete paqueteBd = new Paquete(5L, "DON-1", new Producto("PROD-1"), 10);
        Asignacion asignacionBd = asignacionDelPaquete(paqueteBd, EstadoAsignacion.COMPLETADA);
        when(paqueteRepository.findById(5L)).thenReturn(Optional.of(paqueteBd));
        when(asignacionRepository.findByPaquetes_Id(5L)).thenReturn(Optional.of(asignacionBd));
        when(fachadaDonaciones.buscarDonacionPorID("DON-1")).thenReturn(donacionEnEstado(EstadoDonacionEnum.INGRESADA));

        AsignacionDTO resultado = fachada.reportarEntrega(new PaqueteDTO("5", "DON-1", "PROD-1", 10));

        assertNotNull(resultado);
        verify(fachadaDonadores, never()).satisfacerNecesidad(any(), any());
        verify(asignacionRepository, never()).save(any());
        verify(publicadorEventos, never()).asignacionActualizada(any());
        verify(fachadaDonaciones, times(1)).cambiarEstadoDeDonacion("DON-1", EstadoDonacionEnum.ACEPTADA);
    }

    @Test
    void reportarEntrega_NoVuelveAAceptarUnaDonacionYaAceptada() {
        Paquete paqueteBd = new Paquete(5L, "DON-1", new Producto("PROD-1"), 10);
        Asignacion asignacionBd = asignacionDelPaquete(paqueteBd, EstadoAsignacion.COMPLETADA);
        when(paqueteRepository.findById(5L)).thenReturn(Optional.of(paqueteBd));
        when(asignacionRepository.findByPaquetes_Id(5L)).thenReturn(Optional.of(asignacionBd));
        when(fachadaDonaciones.buscarDonacionPorID("DON-1")).thenReturn(donacionEnEstado(EstadoDonacionEnum.ACEPTADA));

        fachada.reportarEntrega(new PaqueteDTO("5", "DON-1", "PROD-1", 10));

        verify(fachadaDonaciones, never()).cambiarEstadoDeDonacion(any(), any());
    }

    @Test
    void reportarEntrega_AceptaLaDonacionAunqueQuedeSobranteEnStock() {
        // DON-1 se partió: 20 unidades asignadas (paquete 5) y un sobrante de 30 que sigue en stock.
        Paquete parteAsignada = new Paquete(5L, "DON-1", new Producto("PROD-1"), 20);
        Asignacion asignacionBd = asignacionDelPaquete(parteAsignada, EstadoAsignacion.ASIGNADA);
        when(paqueteRepository.findById(5L)).thenReturn(Optional.of(parteAsignada));
        when(asignacionRepository.findByPaquetes_Id(5L)).thenReturn(Optional.of(asignacionBd));
        when(asignacionRepository.save(any(Asignacion.class))).thenReturn(asignacionBd);
        when(fachadaDonaciones.buscarDonacionPorID("DON-1")).thenReturn(donacionEnEstado(EstadoDonacionEnum.INGRESADA));

        fachada.reportarEntrega(new PaqueteDTO("5", "DON-1", "PROD-1", 20));

        verify(fachadaDonadores, times(1)).satisfacerNecesidad("NEC-1", 20);
        verify(fachadaDonaciones, times(1)).cambiarEstadoDeDonacion("DON-1", EstadoDonacionEnum.ACEPTADA);
    }

    private Fachada fachadaConRegistry(MeterRegistry registry) {
        return new Fachada(depositoRepository, asignacionRepository, paqueteRepository, registry, rabbitTemplate,
                publicadorEventos);
    }

    @Test
    void gaugeDeOcupacionReportaElStockActualYSeEliminaConElDeposito() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Fachada fachadaConMetricas = fachadaConRegistry(registry);
        Deposito deposito = new Deposito("D1", "Dir1", 100);
        deposito.setId(1L);
        when(depositoRepository.findAll()).thenReturn(List.of(deposito));
        when(depositoRepository.findById(1L)).thenReturn(Optional.of(deposito));

        fachadaConMetricas.registrarGaugesDeDepositosExistentes();
        Gauge gauge = registry.find("logistica.deposito.ocupacion.porcentaje").tag("deposito_id", "1").gauge();
        assertEquals(0.0, gauge.value());

        deposito.getStock().add(new Paquete(10L, "DON-1", new Producto("PROD-1"), 25));
        assertEquals(25.0, gauge.value());

        fachadaConMetricas.eliminarDeposito("1");
        assertNull(registry.find("logistica.deposito.ocupacion.porcentaje").tag("deposito_id", "1").gauge());
    }

    @Test
    void ejecutarMatchmaking_FallaSiElPaqueteNoEstaEnElStockDelDeposito() {
        Deposito deposito = new Deposito("D1", "Dir1", 100);
        deposito.setId(1L);
        when(depositoRepository.findById(1L)).thenReturn(Optional.of(deposito));

        NoSuchElementException exception = assertThrows(NoSuchElementException.class,
                () -> fachada.ejecutarMatchmaking("1", new PaqueteDTO("10", "DON-1", "PROD-1", 10), List.of()));
        assertEquals("El paquete 10 no está en el stock del depósito 1", exception.getMessage());
        verify(asignacionRepository, never()).save(any());
    }
}
