package ar.edu.utn.dds.k3003;

import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.EstadoDonacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.*;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonaciones;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonadoresYEntidades;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaLogistica;
import ar.edu.utn.dds.k3003.eventos.PublicadorEventos;
import ar.edu.utn.dds.k3003.exceptions.AsignacionNoEncontradaException;
import ar.edu.utn.dds.k3003.exceptions.DepositoNoEncontradoException;
import ar.edu.utn.dds.k3003.exceptions.DepositoSinCapacidadException;
import ar.edu.utn.dds.k3003.model.*;
import ar.edu.utn.dds.k3003.model.mappers.*;
import ar.edu.utn.dds.k3003.repositories.*;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import io.micrometer.core.instrument.Timer;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;


@Component
public class Fachada implements FachadaLogistica {

  private final DepositoRepository depositoRepository;
  private final AsignacionRepository asignacionRepository;
  private final PaqueteRepository paqueteRepository;
  private final LogisticaMapper logisticaMapper;
  private FachadaDonadoresYEntidades fachadaDonadores;
  private FachadaDonaciones fachadaDonaciones;

  private final MeterRegistry meterRegistry;

  private final Counter depositosCreadosCounter;
  private final Counter paquetesRecibidosCounter;
  private final Counter entregasCompletadasCounter;
  private final Counter donacionesSinAsignacionCounter;
  private final Counter descartesCapacidadCounter;
  private final Counter asignacionesMatchmakingCounter;
  private final Counter asignacionesSolicitudCounter;
  private final Timer matchmakingTimer;
  private final RabbitTemplate rabbitTemplate;
  private final PublicadorEventos publicadorEventos;

  private static final Logger log = LoggerFactory.getLogger(Fachada.class);
  private static final String GAUGE_OCUPACION = "logistica.deposito.ocupacion.porcentaje";

  @Autowired
  public Fachada(DepositoRepository depositoRepository,
                 AsignacionRepository asignacionRepository, PaqueteRepository paqueteRepository,
                 MeterRegistry meterRegistry, RabbitTemplate rabbitTemplate,
                 PublicadorEventos publicadorEventos) {
    this.depositoRepository = depositoRepository;
    this.asignacionRepository = asignacionRepository;
    this.paqueteRepository = paqueteRepository;
    this.logisticaMapper = new LogisticaMapper();

    this.meterRegistry = meterRegistry;

    this.depositosCreadosCounter = meterRegistry.counter("logistica.depositos.creados.total");
    this.paquetesRecibidosCounter = meterRegistry.counter("logistica.paquetes.recibidos.total");
    this.entregasCompletadasCounter = meterRegistry.counter("logistica.entregas.completadas.total");
    this.descartesCapacidadCounter = meterRegistry.counter("logistica.donaciones.descartes", "motivo", "falta_capacidad");
    this.donacionesSinAsignacionCounter = meterRegistry.counter("logistica.donaciones.sin_asignacion");
    this.asignacionesMatchmakingCounter = meterRegistry.counter("logistica.asignaciones.creadas", "origen", "MATCHMAKING");
    this.asignacionesSolicitudCounter = meterRegistry.counter("logistica.asignaciones.creadas", "origen", "SOLICITUD_DyE");
    this.matchmakingTimer = meterRegistry.timer("logistica.matchmaking.tiempo_ejecucion");
    this.rabbitTemplate = rabbitTemplate;
    this.publicadorEventos = publicadorEventos;
  }

  @Override
  public DepositoDTO agregarDeposito(DepositoDTO depositoDTO) {
    if (depositoDTO.id() != null && depositoRepository.findById(Long.parseLong(depositoDTO.id())).isPresent()) {
      throw new RuntimeException("El depósito con ese ID ya existe");
    }
    if (depositoDTO.algoritmo() == null) {
      throw new RuntimeException("El depósito debe tener un algoritmo configurado");
    }

    Deposito deposito = logisticaMapper.toDeposito(depositoDTO);
    deposito = depositoRepository.save(deposito);

    this.registrarGaugeOcupacion(deposito.getId());
    this.publicadorEventos.depositoActualizado(deposito);

    this.depositosCreadosCounter.increment();
    return logisticaMapper.toDepositoDTO(deposito);
  }

  @Override
  public DepositoDTO buscarDepositoPorID(String depositoID) throws NoSuchElementException {
    Deposito deposito = depositoRepository.findById(Long.parseLong(depositoID))
            .orElseThrow(() -> new NoSuchElementException("No se encontró el depósito con ID: " + depositoID));
    return logisticaMapper.toDepositoDTO(deposito);
  }

  @Override
  public AsignacionDTO buscarAsignacionPorPaqueteID(String paqueteID) throws NoSuchElementException {
    Asignacion asignacion = asignacionRepository.findByPaquetes_Id(Long.parseLong(paqueteID))
            .orElseThrow(() -> new RuntimeException("No hay asignación para el paquete: " + paqueteID));
    return logisticaMapper.toAsignacionDTO(asignacion);
  }

  @Override
  @Transactional
  public PaqueteDTO gestionarDonacion( String depositoID, String donacionID, String productoID, Integer cantidad) {

    if (cantidad == null || cantidad <= 0) {
      throw new IllegalArgumentException("La cantidad debe ser mayor a cero.");
    }

    Deposito deposito = this.depositoRepository
            .findById(Long.parseLong(depositoID))
            .orElseThrow(() ->
                    new DepositoNoEncontradoException("No se encontró el depósito: " + depositoID));

    if (!deposito.tieneCapacidadPara(cantidad)) {
      this.descartesCapacidadCounter.increment();
      log.warn("Rechazo de donación {}: El depósito {} no tiene capacidad suficiente. Capacidad max: {}, Stock actual: {}, Intentó ingresar: {}",
              donacionID, depositoID, deposito.getCapacidadMaxima(), deposito.getStockActual(), cantidad);
      throw new DepositoSinCapacidadException(
              "El depósito no tiene capacidad suficiente para recibir la donación."
      );
    }

    Paquete paquete = this.logisticaMapper.toPaquete(
            new PaqueteDTO(
                    null,
                    donacionID,
                    productoID,
                    cantidad
            )
    );

    Paquete paqueteGuardado = this.paqueteRepository.save(paquete);

    deposito.recibirEnStock(paqueteGuardado);

    this.depositoRepository.save(deposito);
    this.publicadorEventos.depositoActualizado(deposito);

    String payload = paqueteGuardado.getId() + "," + deposito.getId();

    log.info("Paquete {} creado exitosamente y asignado al depósito {}. Enviando a cola de RabbitMQ",
            paqueteGuardado.getId(), deposito.getId());

    this.rabbitTemplate.convertAndSend("donaciones_nuevas_queue",payload);

    this.paquetesRecibidosCounter.increment(cantidad);

    return this.logisticaMapper.toPaqueteDTO(paqueteGuardado);
  }

  @Override
  public void setAlgoritmoMM(String depositoID, TipoAlgoritmoEnum tipoAlgoritmo) {
    Deposito deposito = this.depositoRepository.findById(Long.parseLong(depositoID))
            .orElseThrow(() -> new NoSuchElementException("No se encontró el depósito con ID: " + depositoID));

    deposito.setTipoAlgoritmo(tipoAlgoritmo);

    this.depositoRepository.save(deposito);
    this.publicadorEventos.depositoActualizado(deposito);
  }

  @Transactional
  @Override
  public AsignacionDTO ejecutarMatchmaking(
          String depositoID,
          PaqueteDTO paqueteDTO,
          List<NecesidadMaterialDTO> necesidadesDTO) {

    log.info("Ejecutando matchmaking para paquete {} en depósito {}", paqueteDTO.id(), depositoID);

    Deposito deposito = depositoRepository.findById(Long.parseLong(depositoID))
            .orElseThrow(() -> new RuntimeException(
                    "No se encontró el depósito: " + depositoID
            ));

    Long paqueteId = Long.parseLong(paqueteDTO.id());

    // Solo se puede asignar un paquete que siga en el stock de este depósito: evita reasignar
    // uno ya asignado si el endpoint se invoca dos veces.
    Paquete paquete = deposito.buscarEnStock(paqueteId)
            .orElseThrow(() -> new NoSuchElementException(
                    "El paquete " + paqueteId + " no está en el stock del depósito " + depositoID
            ));

    List<Necesidad> necesidades = necesidadesDTO.stream()
            .map(dto -> {
              Necesidad necesidad = logisticaMapper.toNecesidad(dto);
              necesidad.setId(dto.id());
              necesidad.descontarPendiente(cantidadPendienteDeEntrega(dto.id()));
              return necesidad;
            })
            .toList();

    ResultadoMatch resultado = this.matchmakingTimer.record(() ->
            deposito.buscarMatch(paquete, necesidades)
    );

    if (resultado == null) {
      this.donacionesSinAsignacionCounter.increment();
      return null;
    }

    deposito.getStock().removeIf(
            paqueteEnStock -> paqueteEnStock.getId().equals(paquete.getId())
    );

    Asignacion asignacion = resultado.getAsignacion();

    asignacion = asignacionRepository.save(asignacion);

    if (resultado.getPaqueteSobrante() != null) {
      deposito.recibirEnStock(resultado.getPaqueteSobrante());
    }

    depositoRepository.save(deposito);
    this.publicadorEventos.asignacionActualizada(asignacion);
    this.publicadorEventos.depositoActualizado(deposito);

    this.asignacionesMatchmakingCounter.increment();

    log.info("Matchmaking exitoso: Paquete {} asignado a necesidad {}. Asignación ID: {}",
            paquete.getId(), asignacion.getNecesidadID(), asignacion.getId());

    return logisticaMapper.toAsignacionDTO(asignacion);
  }

  @Override
  public AsignacionDTO reportarEntrega(PaqueteDTO paqueteDTO) {
    log.info("Iniciando reporte de entrega para el paquete {}", paqueteDTO.id());
    Long paqueteId = Long.parseLong(
            Objects.requireNonNull(paqueteDTO.id(), "El paquete debe tener un ID")
    );

    Paquete paquete = this.paqueteRepository.findById(paqueteId)
            .orElseThrow(() ->
                    new RuntimeException("No se encontró el paquete con ID: " + paqueteId)
            );

    Asignacion asignacion = this.asignacionRepository.findByPaquetes_Id(paquete.getId())
            .orElseThrow(() ->
                    new AsignacionNoEncontradaException(
                            "No se encontró asignación para el paquete: " + paquete.getId()
                    )
            );

    if (asignacion.estaCompletada()) {
      // Un reporte anterior completó la asignación (y por lo tanto satisfizo la necesidad en D&E)
      // pero pudo fallar al actualizar Donaciones: solo se reintenta esa parte.
      log.info("Asignación {} ya completada: se reintenta la actualización de donaciones", asignacion.getId());
    } else {
      int cantidadTotalAsignacion = asignacion.getPaquetes().stream()
              .mapToInt(Paquete::getCantidad)
              .sum();

      // D&E primero: si falla, la asignación no queda completada y la entrega se puede volver a reportar.
      this.fachadaDonadores.satisfacerNecesidad(
              asignacion.getNecesidadID(),
              cantidadTotalAsignacion
      );

      asignacion.completarEntrega();
      asignacion = this.asignacionRepository.save(asignacion);
      this.publicadorEventos.asignacionActualizada(asignacion);
      this.entregasCompletadasCounter.increment();
    }

    this.aceptarDonacionesEntregadas(asignacion);

    log.info("Entrega de asignación {} completada exitosamente. Notificando a módulos Donadores y Donaciones.",
            asignacion.getId());
    return this.logisticaMapper.toAsignacionDTO(asignacion);
  }

  // Una donación se acepta con la primera entrega de cualquiera de sus partes: el sobrante que
  // quede en stock no la deja indefinidamente en INGRESADA.
  private void aceptarDonacionesEntregadas(Asignacion asignacion) {
    asignacion.getPaquetes().stream()
            .map(Paquete::getDonacionID)
            .distinct()
            .filter(donacionID -> this.fachadaDonaciones.buscarDonacionPorID(donacionID).estado()
                    == EstadoDonacionEnum.INGRESADA)
            .forEach(donacionID ->
                    this.fachadaDonaciones.cambiarEstadoDeDonacion(
                            donacionID,
                            EstadoDonacionEnum.ACEPTADA
                    )
            );
  }

  @Autowired
  @Override
  public void setFachadaDonadoresYEntidades(FachadaDonadoresYEntidades fachadaDonadoresYEntidades) {
    this.fachadaDonadores = fachadaDonadoresYEntidades;
  }

  @Autowired
  @Override
  public void setFachadaDonaciones(FachadaDonaciones fachadaDonaciones) {
    this.fachadaDonaciones = fachadaDonaciones;
  }

  public List<DepositoDTO> obtenerTodosLosDepositos() {
    return this.depositoRepository.findAll().stream()
            .map(logisticaMapper::toDepositoDTO)
            .toList();
  }

  public DepositoDTO eliminarDeposito(String depositoID) {
    Deposito deposito = depositoRepository.findById(Long.parseLong(depositoID))
            .orElseThrow(() -> new NoSuchElementException("No se encontró el depósito con ID: " + depositoID));

    this.depositoRepository.deleteById(Long.parseLong(depositoID));
    this.publicadorEventos.depositoEliminado(deposito);

    Gauge gauge = this.meterRegistry.find(GAUGE_OCUPACION).tag("deposito_id", depositoID).gauge();
    if (gauge != null) {
      this.meterRegistry.remove(gauge);
    }

    return logisticaMapper.toDepositoDTO(deposito);
  }

  // Los gauges no sobreviven a un reinicio: se vuelven a registrar para los depósitos existentes.
  @EventListener(ApplicationReadyEvent.class)
  public void registrarGaugesDeDepositosExistentes() {
    this.depositoRepository.findAll()
            .forEach(deposito -> this.registrarGaugeOcupacion(deposito.getId()));
  }

  // Se consulta el depósito en cada lectura para reportar la ocupación actual y no la del momento del alta.
  private void registrarGaugeOcupacion(Long depositoId) {
    Gauge.builder(GAUGE_OCUPACION, () -> this.depositoRepository.findById(depositoId)
                    .map(Deposito::porcentajeOcupacion)
                    .orElse(0.0))
            .tag("deposito_id", String.valueOf(depositoId))
            .register(this.meterRegistry);
  }

  public AsignacionDTO buscarAsignacionPorId(String id) {
    Asignacion asignacion = this.asignacionRepository.findById(Long.parseLong(id))
            .orElseThrow(() -> new AsignacionNoEncontradaException("No se encontró la asignación con ID: " + id));

    return logisticaMapper.toAsignacionDTO(asignacion);
  }

  private int cantidadPendienteDeEntrega(String necesidadID) {
    if (necesidadID == null) {
      return 0;
    }
    return this.asignacionRepository
            .findByNecesidadIDAndEstado(necesidadID, EstadoAsignacion.ASIGNADA).stream()
            .flatMap(asignacion -> asignacion.getPaquetes().stream())
            .mapToInt(Paquete::getCantidad)
            .sum();
  }

  @Override
  public Integer obtenerStockDisponible(String productoID) {
    return this.depositoRepository.findAll().stream()
            .mapToInt(deposito -> deposito.stockDe(productoID))
            .sum();
  }

  @Override
  @Transactional
  public AsignacionDTO asignarStockANecesidad(
          NecesidadMaterialDTO necesidad) {

    Objects.requireNonNull(
            necesidad,
            "La necesidad no puede ser null"
    );

    Objects.requireNonNull(
            necesidad.id(),
            "La necesidad debe tener un ID"
    );

    Objects.requireNonNull(
            necesidad.productoSolicitadoID(),
            "La necesidad debe tener un producto"
    );

    if (necesidad.cantidadObjetivo() == null
            || necesidad.cantidadObjetivo() <= 0) {
      throw new IllegalArgumentException(
              "La cantidad de la necesidad debe ser mayor a cero"
      );
    }

    Necesidad necesidadAjustada = logisticaMapper.toNecesidad(necesidad);
    necesidadAjustada.descontarPendiente(cantidadPendienteDeEntrega(necesidad.id()));

    if (necesidadAjustada.estaCubierta()) {
      log.info("Necesidad {} ya cubierta por asignaciones pendientes de entrega", necesidad.id());
      return null;
    }

    int cantidadRestante = necesidadAjustada.getCantidadObjetivo();

    List<Paquete> paquetesAsignados = new ArrayList<>();
    List<Deposito> depositosModificados = new ArrayList<>();

    List<Deposito> depositos =
            this.depositoRepository.findAll();

    int stockDelProducto = depositos.stream()
            .mapToInt(deposito -> deposito.stockDe(necesidad.productoSolicitadoID()))
            .sum();

    if (!necesidadAjustada.estaHabilitadaPara(stockDelProducto)) {
      log.info("Necesidad {} ({}) no asignada: stock {} no alcanza el faltante {}",
              necesidad.id(), necesidad.tipo(), stockDelProducto, cantidadRestante);
      return null;
    }

    for (Deposito deposito : depositos) {

      List<Paquete> paquetesDelProducto =
              deposito.getStock().stream()
                      .filter(paquete ->
                              necesidad.productoSolicitadoID()
                                      .equals(paquete.getProducto().getId())
                      )
                      .toList();

      for (Paquete paquete : paquetesDelProducto) {

        if (cantidadRestante == 0) {
          break;
        }

        int cantidadAAsignar =
                Math.min(
                        paquete.getCantidad(),
                        cantidadRestante
                );

        deposito.getStock().remove(paquete);

        if (paquete.tieneSobrante(cantidadAAsignar)) {

          Paquete sobrante =
                  paquete.extraerRemanente(
                          cantidadAAsignar
                  );

          deposito.recibirEnStock(sobrante);
        }

        paquetesAsignados.add(paquete);

        cantidadRestante -= cantidadAAsignar;
      }

      if (!paquetesDelProducto.isEmpty()) {
        this.depositoRepository.save(deposito);
        depositosModificados.add(deposito);
      }

      if (cantidadRestante == 0) {
        break;
      }
    }

    if (paquetesAsignados.isEmpty()) {
      return null;
    }

    Asignacion asignacion = new Asignacion();

    asignacion.setPaquetes(
            new ArrayList<>(paquetesAsignados)
    );

    asignacion.setNecesidadID(necesidad.id());

    asignacion.setFecha(LocalDateTime.now());

    asignacion.setEstado(
            EstadoAsignacion.ASIGNADA
    );

    asignacion.setOrigen(
            OrigenAsignacion.SOLICITUD_DyE
    );

    Asignacion asignacionGuardada =
            this.asignacionRepository.save(asignacion);

    this.publicadorEventos.asignacionActualizada(asignacionGuardada);
    depositosModificados.forEach(this.publicadorEventos::depositoActualizado);

    this.asignacionesSolicitudCounter.increment();

    return this.logisticaMapper
            .toAsignacionDTO(asignacionGuardada);
  }

  // =========================================================================
  // MÉTODOS ADMINISTRATIVOS Y CONSULTAS
  // =========================================================================

  public List<Paquete> listarPaquetesEnStock() {
    return this.depositoRepository.findAll().stream()
            .flatMap(d -> d.getStock().stream())
            .toList();
  }

  public List<Paquete> listarPaquetesEntregados() {
    return this.asignacionRepository.findAll().stream()
            .filter(asignacion -> asignacion.getEstado() == EstadoAsignacion.COMPLETADA)
            .flatMap(asignacion -> asignacion.getPaquetes().stream())
            .distinct()
            .toList();
  }

  public boolean borrarAsignacion(Long id) {
    Optional<Asignacion> asignacion = this.asignacionRepository.findById(id);
    if (asignacion.isEmpty()) {
      return false;
    }
    this.asignacionRepository.deleteById(id);
    this.publicadorEventos.asignacionEliminada(asignacion.get());
    return true;
  }

  public boolean borrarPaquete(Long id) {
    // 1. Si está en el stock de algún depósito, lo removemos de la lista
    List<Deposito> depositos = this.depositoRepository.findAll();
    for (Deposito d : depositos) {
      boolean removido = d.getStock().removeIf(p -> p.getId().equals(id));
      if (removido) {
        this.depositoRepository.save(d);
        this.publicadorEventos.depositoActualizado(d);
        return true;
      }
    }
    // 2. Si no estaba en stock (era un paquete asignado/entregado suelto), lo borramos directo del repo
    if (this.paqueteRepository.existsById(id)) {
      this.paqueteRepository.deleteById(id);
      return true;
    }
    return false;
  }

  public Asignacion forzarCreacionAsignacion(Asignacion asignacion) {
    Asignacion guardada = this.asignacionRepository.save(asignacion);
    this.publicadorEventos.asignacionActualizada(guardada);
    return guardada;
  }

  public boolean forzarCreacionPaquete(Long depositoId, Paquete paquete) {
    Optional<Deposito> depositoOpt = this.depositoRepository.findById(depositoId);
    if (depositoOpt.isPresent()) {
      Deposito deposito = depositoOpt.get();
      deposito.recibirEnStock(paquete);
      this.depositoRepository.save(deposito);
      this.publicadorEventos.depositoActualizado(deposito);
      return true;
    }
    return false;
  }

  public Optional<Asignacion> forzarActualizacionAsignacion(Long id, Asignacion asignacionActualizada) {
    if (!this.asignacionRepository.existsById(id)) {
      return Optional.empty();
    }
    asignacionActualizada.setId(id);
    Asignacion guardada = this.asignacionRepository.save(asignacionActualizada);
    this.publicadorEventos.asignacionActualizada(guardada);
    return Optional.of(guardada);
  }

  // Antes de borrar se publican las bajas, para que el modelo de lectura no quede con datos que ya no existen.
  public void limpiarTodo() {
    this.asignacionRepository.findAll().forEach(this.publicadorEventos::asignacionEliminada);
    this.depositoRepository.findAll().forEach(this.publicadorEventos::depositoEliminado);
    this.asignacionRepository.deleteAll();
    this.depositoRepository.deleteAll();
    this.paqueteRepository.deleteAll();
  }

}