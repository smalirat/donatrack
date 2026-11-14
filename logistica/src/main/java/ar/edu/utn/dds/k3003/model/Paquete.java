package ar.edu.utn.dds.k3003.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "paquetes")
public class Paquete {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "donacion_id")
    private String donacionID;

    @Embedded
    private Producto producto;

    @Column
    private Integer cantidad;

    // El depósito que lo recibió. A diferencia del vínculo con el stock, se conserva cuando el paquete se asigna.
    @Column(name = "deposito_origen_id")
    private Long depositoOrigenId;

    public Paquete(Long id, String donacionID, Producto producto, Integer cantidad) {
        this(id, donacionID, producto, cantidad, null);
    }

    public boolean tieneSobrante(Integer cantidadAAsignar) {
        return this.cantidad > cantidadAAsignar;
    }

    public Paquete extraerRemanente(Integer cantidadAAsignar) {
        if (cantidadAAsignar >= this.cantidad || cantidadAAsignar <= 0) {
            throw new IllegalArgumentException("La cantidad a asignar es inválida.");
        }

        Integer cantidadSobrante = this.cantidad - cantidadAAsignar;
        this.cantidad = cantidadAAsignar;

        Paquete paqueteRemanente = new Paquete();
        paqueteRemanente.setDonacionID(this.donacionID);
        paqueteRemanente.setProducto(this.producto);
        paqueteRemanente.setCantidad(cantidadSobrante);
        paqueteRemanente.setDepositoOrigenId(this.depositoOrigenId);

        return paqueteRemanente;
    }
}