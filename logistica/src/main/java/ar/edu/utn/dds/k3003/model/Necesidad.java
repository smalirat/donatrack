package ar.edu.utn.dds.k3003.model;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.TipoNecesidadMaterialEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class Necesidad {
    private String id;
    private Integer nivelDeUrgencia;
    private Integer cantidadObjetivo;
    private TipoNecesidadMaterialEnum tipo;

    //si huubiera mas tipos tal vez un refcactor a un strategy
    public boolean estaHabilitadaPara(Integer cantidadDonada) {
        if (this.tipo == TipoNecesidadMaterialEnum.EXTRAORDINARIA) {
            return true;
        } else if (this.tipo == TipoNecesidadMaterialEnum.RECURRENTE) {
            return cantidadDonada >= this.cantidadObjetivo;
        }
        return false;
    }

    public Integer calcularCantidadAAsignar(Integer cantidadDisponible) {
        return Math.min(this.cantidadObjetivo, cantidadDisponible);
    }

    // D&E solo descuenta lo entregado; lo asignado y no entregado lo conoce Logística.
    // Una recurrente admite una única entrega por período, así que con algo pendiente queda cubierta.
    public void descontarPendiente(Integer cantidadPendiente) {
        if (cantidadPendiente == null || cantidadPendiente <= 0) {
            return;
        }
        if (this.tipo == TipoNecesidadMaterialEnum.RECURRENTE) {
            this.cantidadObjetivo = 0;
            return;
        }
        this.cantidadObjetivo = Math.max(0, this.cantidadObjetivo - cantidadPendiente);
    }

    public boolean estaCubierta() {
        return this.cantidadObjetivo == null || this.cantidadObjetivo <= 0;
    }
}