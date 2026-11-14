package ar.edu.utn.dds.k3003.eventos;

import ar.edu.utn.dds.k3003.model.EntidadBenefica;

// Los datos del mensaje del evento de una entidad
public record EntidadActualizada(String id, String razonSocial, String domicilio, String telefono,
                                 String correo, boolean eliminado) {

  public static EntidadActualizada de(EntidadBenefica entidad, boolean eliminado) {
    return new EntidadActualizada(
        String.valueOf(entidad.getId()),
        entidad.getRazonSocial(),
        entidad.getDomicilio(),
        entidad.getTelefono(),
        entidad.getCorreo(),
        eliminado);
  }
}
