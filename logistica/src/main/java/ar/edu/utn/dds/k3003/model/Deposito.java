package ar.edu.utn.dds.k3003.model;

import ar.edu.utn.dds.k3003.catedra.dtos.logistica.TipoAlgoritmoEnum;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "depositos")
public class Deposito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String nombre;

    @Column
    private String direccion;

    @Column(name = "capacidad_maxima")
    private Integer capacidadMaxima;

    @OneToMany(cascade = {CascadeType.MERGE, CascadeType.PERSIST, CascadeType.REMOVE},
            fetch = FetchType.EAGER,
            orphanRemoval = true)
    @JoinColumn(name = "deposito_id")
    private List<Paquete> stock;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_algoritmo")
    private TipoAlgoritmoEnum tipoAlgoritmo;

    @Transient
    @Getter(lombok.AccessLevel.NONE)
    @Setter(lombok.AccessLevel.NONE)
    private AlgoritmoMatchmaking algoritmoMatchMaking;

    public Deposito(String nombre, String direccion, Integer capacidadMaxima) {
        this.nombre = nombre;
        this.direccion = direccion;
        this.capacidadMaxima = capacidadMaxima;
        this.stock = new ArrayList<>();
    }

    public void setTipoAlgoritmo(TipoAlgoritmoEnum tipoAlgoritmo) {
        this.tipoAlgoritmo = tipoAlgoritmo;
        this.instanciarEstrategia();
    }

    @PostLoad
    private void instanciarEstrategia() {
        if (this.tipoAlgoritmo != null) {
            this.algoritmoMatchMaking = switch (this.tipoAlgoritmo) {
                case PRIORIDAD_POR_SCORE -> new AlgoritmoScore();
                case SUB_ATENDIDOS -> new AlgoritmoSubAtendidos();
            };
        }
    }

    public Integer capacidadDisponible() {
        Integer ocupado = this.stock.stream().mapToInt(Paquete::getCantidad).sum();
        return this.capacidadMaxima - ocupado;
    }

    public void recibirEnStock(Paquete paquete) {
        if (paquete.getDepositoOrigenId() == null) {
            paquete.setDepositoOrigenId(this.id);
        }
        this.stock.add(paquete);
    }

    public Optional<Paquete> buscarEnStock(Long paqueteID) {
        return this.stock.stream()
                .filter(paquete -> paquete.getId().equals(paqueteID))
                .findFirst();
    }

    public double porcentajeOcupacion() {
        if (this.capacidadMaxima == null || this.capacidadMaxima == 0) {
            return 0.0;
        }
        return (double) (this.capacidadMaxima - this.capacidadDisponible()) / this.capacidadMaxima * 100;
    }

    public Map<String, Integer> stockPorProducto() {
        return this.stock.stream()
                .collect(Collectors.groupingBy(paquete -> paquete.getProducto().getId(),
                        TreeMap::new, Collectors.summingInt(Paquete::getCantidad)));
    }

    public Integer stockDe(String productoID) {
        return this.stock.stream()
                .filter(paquete -> productoID.equals(paquete.getProducto().getId()))
                .mapToInt(Paquete::getCantidad)
                .sum();
    }

    public Boolean tieneCapacidadPara(Integer cantidad) {
        return this.capacidadDisponible() >= cantidad;
    }

    public ResultadoMatch buscarMatch(Paquete paquete, List<Necesidad> todasLasNecesidades) {
        if (this.algoritmoMatchMaking == null) {
            throw new IllegalStateException("El depósito no tiene un algoritmo configurado.");
        }

        List<Necesidad> candidatosValidos = todasLasNecesidades.stream()
                .filter(n -> !n.estaCubierta())
                .filter(n -> n.estaHabilitadaPara(paquete.getCantidad()))
                .toList();

        if (candidatosValidos.isEmpty()) {
            return null;
        }

        Necesidad elegida = this.algoritmoMatchMaking.seleccionarNecesidad(paquete, candidatosValidos);
        Integer cantidadAAsignar = elegida.calcularCantidadAAsignar(paquete.getCantidad());

        Paquete sobrante = null;
        if (paquete.tieneSobrante(cantidadAAsignar)) {
            sobrante = paquete.extraerRemanente(cantidadAAsignar);
        }

        Asignacion asignacion = new Asignacion(
                null,
                new ArrayList<>(List.of(paquete)),
                elegida.getId(),
                LocalDateTime.now(),
                EstadoAsignacion.ASIGNADA,
                OrigenAsignacion.MATCHMAKING
        );

        return new ResultadoMatch(asignacion, sobrante);
    }

    public Object getStockActual() {
        return this.stock.stream().mapToInt(Paquete::getCantidad).sum();
    }


}