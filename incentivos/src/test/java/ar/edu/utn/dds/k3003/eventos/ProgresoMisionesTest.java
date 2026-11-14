package ar.edu.utn.dds.k3003.eventos;

import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.EstadoDonacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.ProductoDTO;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonaciones;
import ar.edu.utn.dds.k3003.model.misiones.Completitud;
import ar.edu.utn.dds.k3003.model.misiones.DonacionesAscendentes;
import ar.edu.utn.dds.k3003.model.misiones.DonacionesExitosas;
import ar.edu.utn.dds.k3003.model.misiones.RevolucionDonadora;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProgresoMisionesTest {

  private DonacionDTO donacion(String producto, int cantidad, EstadoDonacionEnum estado) {
    return new DonacionDTO("d", "12", "3", "desc", producto, cantidad, estado);
  }

  private List<DonacionDTO> conCantidades(int... cantidades) {
    return IntStream.of(cantidades).mapToObj(c -> donacion("p", c, EstadoDonacionEnum.INGRESADA)).toList();
  }

  @Test
  void completitudCuentaCategoriasDistintas() {
    FachadaDonaciones donaciones = mock(FachadaDonaciones.class);
    when(donaciones.buscarProductoPorID("p1")).thenReturn(new ProductoDTO("p1", "", "", "alimentos", null));
    when(donaciones.buscarProductoPorID("p2")).thenReturn(new ProductoDTO("p2", "", "", "alimentos", null));
    when(donaciones.buscarProductoPorID("p3")).thenReturn(new ProductoDTO("p3", "", "", "ropa", null));
    List<DonacionDTO> lista = List.of(donacion("p1", 1, EstadoDonacionEnum.INGRESADA),
        donacion("p2", 1, EstadoDonacionEnum.INGRESADA), donacion("p3", 1, EstadoDonacionEnum.INGRESADA));

    Completitud mision = new Completitud();

    assertEquals(2, mision.progreso(lista, donaciones));
    assertEquals(3, mision.objetivo());
    assertFalse(mision.estaCompleta(lista, donaciones));
  }

  @Test
  void donacionesExitosasCuentaLasAceptadas() {
    List<DonacionDTO> lista = List.of(donacion("p", 1, EstadoDonacionEnum.ACEPTADA),
        donacion("p", 1, EstadoDonacionEnum.ACEPTADA), donacion("p", 1, EstadoDonacionEnum.CONQUEJA));

    DonacionesExitosas mision = new DonacionesExitosas();

    assertEquals(2, mision.progreso(lista, null));
    assertEquals(20, mision.objetivo());
  }

  @Test
  void revolucionDonadoraCuentaLasDeMasDe50() {
    RevolucionDonadora mision = new RevolucionDonadora();

    assertEquals(2, mision.progreso(conCantidades(51, 50, 80), null));
    assertEquals(11, mision.objetivo());
  }

  @Test
  void donacionesAscendentesCuentaLaRachaFinal() {
    DonacionesAscendentes mision = new DonacionesAscendentes();

    assertEquals(0, mision.progreso(List.of(), null));
    assertEquals(3, mision.progreso(conCantidades(9, 1, 2, 3), null));
    assertEquals(1, mision.progreso(conCantidades(5, 4), null));
    assertEquals(5, mision.objetivo());
  }

  @Test
  void elProgresoLlegaAlObjetivoCuandoLaMisionSeCompleta() {
    DonacionesAscendentes mision = new DonacionesAscendentes();
    List<DonacionDTO> lista = conCantidades(1, 2, 3, 4, 5, 6);

    assertTrue(mision.estaCompleta(lista, null));
    assertEquals(mision.objetivo(), mision.progreso(lista, null));
  }
}
