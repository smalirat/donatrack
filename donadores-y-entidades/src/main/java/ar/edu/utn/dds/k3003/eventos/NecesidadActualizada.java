package ar.edu.utn.dds.k3003.eventos;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.TipoNecesidadMaterialEnum;
import ar.edu.utn.dds.k3003.model.EntidadBenefica;
import ar.edu.utn.dds.k3003.model.NecesidadMaterial;
import ar.edu.utn.dds.k3003.model.PeriodoNecesidad;

import java.time.Instant;

// Los datos del mensaje del evento de una necesidad
public record NecesidadActualizada(String id, EntidadResumen entidad, String productoSolicitadoId,
                                   String descripcion, Integer nivelDeUrgencia, TipoNecesidadMaterialEnum tipo,
                                   PeriodoNecesidad periodo, Integer cantidadFaltante,
                                   Instant fechaUltimaSatisfaccion, boolean satisfecha, boolean eliminado) {

  public record EntidadResumen(String id, String razonSocial) {}

  public static NecesidadActualizada de(NecesidadMaterial necesidad, EntidadBenefica entidad, boolean eliminado) {
    int faltante = necesidad.getCantidadObjetivo() != null ? necesidad.getCantidadObjetivo() : 0;
    return new NecesidadActualizada(
        String.valueOf(necesidad.getId()),
        new EntidadResumen(necesidad.getEntidadId(), entidad != null ? entidad.getRazonSocial() : null),
        necesidad.getProductoSolicitadoId(),
        necesidad.getDescripcion(),
        necesidad.getNivelDeUrgencia(),
        necesidad.getTipo(),
        necesidad.getPeriodo(),
        faltante,
        Evento.enUtc(necesidad.getFechaUltimaSatisfaccion()),
        faltante <= 0 || necesidad.estaSatisfechaEnPeriodoActual(),
        eliminado);
  }
}
