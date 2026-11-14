package ar.edu.utn.dds.k3003.eventos;

import ar.edu.utn.dds.k3003.Fachada;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.ProductoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.*;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaIncentivos;
import ar.edu.utn.dds.k3003.config.MetricsService;
import ar.edu.utn.dds.k3003.model.Donador;
import ar.edu.utn.dds.k3003.model.EntidadBenefica;
import ar.edu.utn.dds.k3003.model.NecesidadMaterial;
import ar.edu.utn.dds.k3003.model.Queja;
import ar.edu.utn.dds.k3003.repositories.donadores.DonadorJpaRepository;
import ar.edu.utn.dds.k3003.repositories.entidad.EntidadBeneficaJpaRepository;
import ar.edu.utn.dds.k3003.repositories.necesidadMaterial.NecesidadMaterialJpaRepository;
import ar.edu.utn.dds.k3003.repositories.quejas.QuejaJpaRepository;
import ar.edu.utn.dds.k3003.rest.DonacionesClient;
import ar.edu.utn.dds.k3003.rest.LogisticaClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FachadaEventosTest {

  private DonadorJpaRepository donadores;
  private QuejaJpaRepository quejas;
  private EntidadBeneficaJpaRepository entidades;
  private NecesidadMaterialJpaRepository necesidades;
  private DonacionesClient donacionesClient;
  private LogisticaClient logisticaClient;
  private PublicadorEventos publicador;
  private Fachada fachada;

  @BeforeEach
  void setUp() {
    donadores = mock(DonadorJpaRepository.class);
    quejas = mock(QuejaJpaRepository.class);
    entidades = mock(EntidadBeneficaJpaRepository.class);
    necesidades = mock(NecesidadMaterialJpaRepository.class);
    donacionesClient = mock(DonacionesClient.class);
    logisticaClient = mock(LogisticaClient.class);
    publicador = mock(PublicadorEventos.class);
    fachada = new Fachada(donadores, quejas, entidades, necesidades, mock(MetricsService.class),
        mock(FachadaIncentivos.class), donacionesClient, logisticaClient, publicador);

    when(donadores.save(any(Donador.class))).thenAnswer(inv -> inv.getArgument(0));
    when(entidades.save(any(EntidadBenefica.class))).thenAnswer(inv -> inv.getArgument(0));
    when(necesidades.save(any(NecesidadMaterial.class))).thenAnswer(inv -> inv.getArgument(0));
    when(quejas.save(any(Queja.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  private Donador donador() {
    Donador donador = new Donador("Lucía", "Fernández", 30, "lucia@mail.com", "123", "Medrano 951");
    donador.setId("12");
    return donador;
  }

  @Test
  void agregarDonadorLoPublica() {
    fachada.agregarDonador(new DonadorDTO(null, "Lucía", "Fernández", 30, "lucia@mail.com", "123", "Medrano 951", null, null));

    verify(publicador, times(1)).donadorActualizado(any(Donador.class));
  }

  @Test
  void cambiarEstadoOCategoriaDelDonadorLoPublica() {
    when(donadores.findById(12L)).thenReturn(Optional.of(donador()));

    fachada.modificarEstado("12", EstadoDonadorEnum.SOSPECHOSO);
    fachada.modifcarCategoria("12", "COLABORADOR");

    verify(publicador, times(2)).donadorActualizado(any(Donador.class));
  }

  @Test
  void unaQuejaPublicaAlDonadorConSuEstadoActualizado() {
    when(donadores.findById(12L)).thenReturn(Optional.of(donador()));
    when(quejas.findAllByDonadorId("12")).thenReturn(List.of(new Queja()));

    fachada.agregarQueja(new QuejaDTO(null, "a3f9", "12", null, "producto roto"));

    verify(publicador, times(1)).donadorActualizado(any(Donador.class));
  }

  @Test
  void altaYModificacionDeEntidadSePublican() {
    when(entidades.findById(4L)).thenReturn(Optional.of(new EntidadBenefica(4L, "Comedor", "Dir", "11", "c@mail.com")));

    fachada.agregarEntidad(new EntidadBeneficaDTO(null, "Comedor", "Dir", "11", "c@mail.com"));
    fachada.modificarEntidad("4", new EntidadBeneficaDTO(null, "Comedor Nuevo", null, null, null));

    verify(publicador, times(2)).entidadActualizada(any(EntidadBenefica.class));
  }

  @Test
  void registrarYSatisfacerUnaNecesidadSePublican() {
    when(donacionesClient.buscarProducto("7")).thenReturn(Optional.of(new ProductoDTO("7", "Fideos", "", "1", null)));
    when(logisticaClient.consultarStockDeProducto("7")).thenReturn(0);
    when(necesidades.findById(9L)).thenReturn(Optional.of(NecesidadMaterial.builder()
        .id(9L).entidadId("4").cantidadObjetivo(30).tipo(TipoNecesidadMaterialEnum.EXTRAORDINARIA).build()));

    fachada.registrarNecesidad(new NecesidadMaterialDTO(null, "4", 8, "Fideos", 30, "7", TipoNecesidadMaterialEnum.EXTRAORDINARIA));
    fachada.satisfacerNecesidad("9", 10);

    verify(publicador, times(2)).necesidadActualizada(any(NecesidadMaterial.class));
  }

  @Test
  void eliminarUnaNecesidadPublicaLaBaja() {
    NecesidadMaterial necesidad = NecesidadMaterial.builder().id(9L).entidadId("4").cantidadObjetivo(30).build();
    when(necesidades.findById(9L)).thenReturn(Optional.of(necesidad));

    fachada.eliminarNecesidad("9");

    verify(publicador, times(1)).necesidadEliminada(necesidad);
  }

  @Test
  void publicarBajaDeTodoPublicaLasBajasDeLasTresEntidades() {
    when(necesidades.findAll()).thenReturn(List.of(NecesidadMaterial.builder().id(9L).build()));
    when(entidades.findAll()).thenReturn(List.of(new EntidadBenefica(4L, "Comedor", "Dir", "11", "c@mail.com")));
    when(donadores.findAll()).thenReturn(List.of(donador()));

    fachada.publicarBajaDeTodo();

    verify(publicador, times(1)).necesidadEliminada(any(NecesidadMaterial.class));
    verify(publicador, times(1)).entidadEliminada(any(EntidadBenefica.class));
    verify(publicador, times(1)).donadorEliminado(any(Donador.class));
  }
}
