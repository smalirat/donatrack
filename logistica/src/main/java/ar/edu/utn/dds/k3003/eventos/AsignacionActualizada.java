package ar.edu.utn.dds.k3003.eventos;

import ar.edu.utn.dds.k3003.model.Asignacion;
import ar.edu.utn.dds.k3003.model.Paquete;

import java.time.Instant;
import java.util.List;

// Datos del evento ASIGNACION_ACTUALIZADA. El depósito va por paquete: una asignación hecha
// desde stock puede juntar paquetes de varios depósitos.
public record AsignacionActualizada(String id, String necesidadId, String origen, String estado,
                                    Instant fechaCreacion, Instant fechaEntrega,
                                    List<PaqueteAsignado> paquetes, boolean eliminado) {

  public record PaqueteAsignado(String paqueteId, String donacionId, String productoId,
                                String depositoId, Integer cantidad) {

    static PaqueteAsignado de(Paquete paquete) {
      return new PaqueteAsignado(
              String.valueOf(paquete.getId()),
              paquete.getDonacionID(),
              paquete.getProducto() != null ? paquete.getProducto().getId() : null,
              paquete.getDepositoOrigenId() != null ? String.valueOf(paquete.getDepositoOrigenId()) : null,
              paquete.getCantidad());
    }
  }

  public static AsignacionActualizada de(Asignacion asignacion, boolean eliminado) {
    return new AsignacionActualizada(
            String.valueOf(asignacion.getId()),
            asignacion.getNecesidadID(),
            asignacion.getOrigen() != null ? asignacion.getOrigen().name() : null,
            asignacion.getEstado() != null ? asignacion.getEstado().name() : null,
            Evento.enUtc(asignacion.getFecha()),
            Evento.enUtc(asignacion.getFechaEntrega()),
            asignacion.getPaquetes().stream().map(PaqueteAsignado::de).toList(),
            eliminado);
  }
}
