package ar.edu.utn.dds.k3003.eventos;

import ar.edu.utn.dds.k3003.catedra.dtos.incentivos.TipoMisionEnum;
import ar.edu.utn.dds.k3003.model.Insignia;
import ar.edu.utn.dds.k3003.model.Mision;

import java.util.List;

// Los datos del mensaje del evento de las insignias y la mision de un donador
public record IncentivosDonadorActualizado(String donadorId, List<InsigniaObtenida> insignias,
                                           MisionEnCurso misionEnCurso) {

  public record InsigniaObtenida(String id, String nombre) {}

  public record MisionEnCurso(String id, String nombre, TipoMisionEnum tipo, int progreso, int objetivo) {}

  public static IncentivosDonadorActualizado de(String donadorId, List<Insignia> insignias,
                                                Mision mision, int progreso) {
    return new IncentivosDonadorActualizado(
        donadorId,
        insignias.stream()
            .map(insignia -> new InsigniaObtenida(String.valueOf(insignia.getId()), insignia.getNombre()))
            .toList(),
        mision == null ? null : new MisionEnCurso(String.valueOf(mision.getId()), mision.getNombre(),
            mision.getTipoDeMision(), progreso, mision.objetivo()));
  }

  public static IncentivosDonadorActualizado sinIncentivos(String donadorId) {
    return new IncentivosDonadorActualizado(donadorId, List.of(), null);
  }
}
