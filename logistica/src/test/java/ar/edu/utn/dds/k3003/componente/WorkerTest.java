package ar.edu.utn.dds.k3003.componente;

import ar.edu.utn.dds.k3003.MatchmakingWorker;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.TipoNecesidadMaterialEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.DepositoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.PaqueteDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.TipoAlgoritmoEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.worker.MatchmakingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WorkerTest {

    @Mock
    private RestTemplate restTemplate;

    private MatchmakingWorker worker;

    private final String URL_DONADORES = "http://donadores-api";
    private final String URL_LOGISTICA = "http://localhost:8080";

    @BeforeEach
    void setUp() {
        worker = new MatchmakingWorker(restTemplate, URL_DONADORES, URL_LOGISTICA);
    }

    @Test
    void recibirDonacion_ProcesaExitosamenteElFlujoCompleto() {
        String payload = "5,1";

        PaqueteDTO paqueteSimulado = new PaqueteDTO("5", "DON-1", "PROD-1", 10);
        DepositoDTO depositoSimulado = new DepositoDTO("1", TipoAlgoritmoEnum.SUB_ATENDIDOS, "Depo", "Dir", 100, List.of(paqueteSimulado));

        NecesidadMaterialDTO necesidadSimulada = new NecesidadMaterialDTO("NEC-1", "ENT-1", 5, "Fideos", 10, "PROD-1", TipoNecesidadMaterialEnum.EXTRAORDINARIA);
        NecesidadMaterialDTO[] arrayNecesidades = { necesidadSimulada };

        when(restTemplate.getForObject(URL_LOGISTICA + "/depositos/1", DepositoDTO.class))
                .thenReturn(depositoSimulado);

        when(restTemplate.getForObject(URL_DONADORES + "/necesidades?productoSolicitadoID=PROD-1", NecesidadMaterialDTO[].class))
                .thenReturn(arrayNecesidades);

        worker.recibirDonacion(payload);

        ArgumentCaptor<MatchmakingRequest> requestCaptor = ArgumentCaptor.forClass(MatchmakingRequest.class);

        verify(restTemplate, times(1)).postForObject(
                eq(URL_LOGISTICA + "/depositos/1/matchmaking"),
                requestCaptor.capture(),
                eq(Void.class)
        );

        MatchmakingRequest requestEnviado = requestCaptor.getValue();
        assertEquals("5", requestEnviado.paqueteDTO().id());
        assertEquals(1, requestEnviado.necesidadesDTO().size());
        assertEquals("NEC-1", requestEnviado.necesidadesDTO().get(0).id());
    }

    @Test
    void recibirDonacion_FallaSiNoEncuentraElPaquete() {
        DepositoDTO depositoVacio = new DepositoDTO("1", TipoAlgoritmoEnum.SUB_ATENDIDOS, "Depo", "Dir", 100, List.of());

        when(restTemplate.getForObject(URL_LOGISTICA + "/depositos/1", DepositoDTO.class))
                .thenReturn(depositoVacio);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> worker.recibirDonacion("5,1"));
        assertEquals("Paquete no encontrado en el depósito", exception.getMessage());

        verify(restTemplate, never()).getForObject(contains("/necesidades"), any());
        verify(restTemplate, never()).postForObject(anyString(), any(), any());
    }
}