package ar.edu.utn.dds.k3003.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "asignaciones")
public class Asignacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(
            cascade = {CascadeType.PERSIST, CascadeType.MERGE},
            fetch = FetchType.EAGER
    )
    @JoinColumn(name = "asignacion_id")
    private List<Paquete> paquetes = new ArrayList<>();

    @Column(name = "necesidad_id")
    private String necesidadID;

    @Column
    private LocalDateTime fecha;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado")
    private EstadoAsignacion estado;

    @Enumerated(EnumType.STRING)
    @Column(name = "origen")
    private OrigenAsignacion origen;

    @Column(name = "fecha_entrega")
    private LocalDateTime fechaEntrega;

    public Asignacion(Long id, List<Paquete> paquetes, String necesidadID, LocalDateTime fecha,
                      EstadoAsignacion estado, OrigenAsignacion origen) {
        this(id, paquetes, necesidadID, fecha, estado, origen, null);
    }

    public boolean estaCompletada() {
        return this.estado == EstadoAsignacion.COMPLETADA;
    }

    public void completarEntrega() {
        if (this.estado == EstadoAsignacion.COMPLETADA) {
            throw new IllegalStateException(
                    "La asignación ya aprece como completada. Estado:  " + this.estado
            );
        }

        this.estado = EstadoAsignacion.COMPLETADA;
        this.fechaEntrega = LocalDateTime.now();
    }
}