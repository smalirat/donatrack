package ar.edu.utn.dds.k3003.eventos;

import ar.edu.utn.dds.k3003.Fachada;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.EstadoDonacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.incentivos.CategoriaDonadorEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.incentivos.MisionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.incentivos.TipoMisionEnum;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonaciones;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonadoresYEntidades;
import ar.edu.utn.dds.k3003.model.DonadorIncentivos;
import ar.edu.utn.dds.k3003.model.Insignia;
import ar.edu.utn.dds.k3003.model.misiones.DonacionesExitosas;
import ar.edu.utn.dds.k3003.servicies.DonadorIncentivosService;
import ar.edu.utn.dds.k3003.servicies.InsigniaService;
import ar.edu.utn.dds.k3003.servicies.MisionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FachadaEventosTest {

  private FachadaDonaciones donaciones;
  private DonadorIncentivosService donadorService;
  private PublicadorEventos publicador;
  private Fachada fachada;
  private DonadorIncentivos donador;

  @BeforeEach
  void setUp() {
    donaciones = mock(FachadaDonaciones.class);
    donadorService = mock(DonadorIncentivosService.class);
    publicador = mock(PublicadorEventos.class);
    fachada = new Fachada(mock(InsigniaService.class), mock(MisionService.class), donadorService,
        donaciones, mock(FachadaDonadoresYEntidades.class), new SimpleMeterRegistry());
    fachada.setPublicadorEventos(publicador);

    Insignia insignia = new Insignia("Exitosa", "20 aceptadas");
    insignia.setId(1L);
    DonacionesExitosas mision = new DonacionesExitosas("Donaciones Exitosas", insignia,
        CategoriaDonadorEnum.TRANSFORMADOR, CategoriaDonadorEnum.COLABORADOR, TipoMisionEnum.DONACIONES_EXITOSAS);
    mision.setId(2L);
    donador = new DonadorIncentivos("12");
    donador.setMisionActual(mision);
    when(donadorService.obtenerDonador("12")).thenReturn(donador);
    when(donaciones.buscarPorDonadorYFechaInicio(eq("12"), any())).thenReturn(List.of(
        new DonacionDTO("d1", "12", "3", "", "p", 1, EstadoDonacionEnum.ACEPTADA),
        new DonacionDTO("d2", "12", "3", "", "p", 1, EstadoDonacionEnum.INGRESADA)));
  }

  private IncentivosDonadorActualizado publicado() {
    ArgumentCaptor<IncentivosDonadorActualizado> captor = ArgumentCaptor.forClass(IncentivosDonadorActualizado.class);
    verify(publicador).incentivosActualizados(captor.capture());
    return captor.getValue();
  }

  @Test
  void procesarAlDonadorPublicaElProgresoDeSuMision() {
    fachada.procesarDonador("12");

    IncentivosDonadorActualizado datos = publicado();
    assertEquals("12", datos.donadorId());
    assertEquals("2", datos.misionEnCurso().id());
    assertEquals(1, datos.misionEnCurso().progreso());
    assertEquals(20, datos.misionEnCurso().objetivo());
  }

  @Test
  void asignarUnaMisionPublicaElEstado() {
    fachada.asignarMisionADonador("12", new MisionDTO("2", "Donaciones Exitosas", "1",
        CategoriaDonadorEnum.COLABORADOR, CategoriaDonadorEnum.TRANSFORMADOR, TipoMisionEnum.DONACIONES_EXITOSAS));

    assertEquals(1, publicado().misionEnCurso().progreso());
  }

  @Test
  void eliminarAlDonadorPublicaQueNoTieneIncentivos() {
    fachada.eliminarDonadorIncentivos("12");

    IncentivosDonadorActualizado datos = publicado();
    assertNull(datos.misionEnCurso());
    assertTrue(datos.insignias().isEmpty());
  }

  @Test
  void sinPublicadorLaFachadaFuncionaIgual() {
    Fachada sinEventos = new Fachada(mock(InsigniaService.class), mock(MisionService.class), donadorService,
        donaciones, mock(FachadaDonadoresYEntidades.class), new SimpleMeterRegistry());

    assertDoesNotThrow(() -> sinEventos.procesarDonador("12"));
  }
}
