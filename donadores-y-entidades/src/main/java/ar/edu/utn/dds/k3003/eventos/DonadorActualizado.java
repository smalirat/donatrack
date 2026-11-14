package ar.edu.utn.dds.k3003.eventos;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.EstadoDonadorEnum;
import ar.edu.utn.dds.k3003.model.Donador;

// Los datos del mensaje del evento de un donador
public record DonadorActualizado(String id, String nombre, String apellido, EstadoDonadorEnum estado,
                                 String categoria, Integer cantidadQuejas, boolean eliminado) {

  public static DonadorActualizado de(Donador donador, int cantidadQuejas, boolean eliminado) {
    return new DonadorActualizado(
        donador.getId(),
        donador.getNombre(),
        donador.getApellido(),
        donador.getEstado(),
        donador.getCategoria(),
        cantidadQuejas,
        eliminado);
  }
}
