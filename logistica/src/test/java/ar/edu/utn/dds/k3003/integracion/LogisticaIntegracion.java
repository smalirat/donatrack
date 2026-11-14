package ar.edu.utn.dds.k3003.integracion;

import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.EstadoDonacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.DepositoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.TipoAlgoritmoEnum;
import ar.edu.utn.dds.k3003.model.Deposito;
import ar.edu.utn.dds.k3003.repositories.AsignacionRepository;
import ar.edu.utn.dds.k3003.repositories.DepositoRepository;
import ar.edu.utn.dds.k3003.repositories.PaqueteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class LogisticaIntegracion {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DepositoRepository depositoRepository;

    @Autowired
    private PaqueteRepository paqueteRepository;

    @Autowired
    private AsignacionRepository asignacionRepository;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @MockitoBean
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        asignacionRepository.deleteAll();
        paqueteRepository.deleteAll();
        depositoRepository.deleteAll();
    }

    @Test
    void flujoIntegrado_API_BaseDeDatos_RabbitMQ() throws Exception {

        DepositoDTO nuevoDeposito = new DepositoDTO(
                null, TipoAlgoritmoEnum.SUB_ATENDIDOS, "Depo Central IT", "Dir IT", 100, null);

        String responseJson = mockMvc.perform(post("/depositos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nuevoDeposito)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Depo Central IT"))
                .andReturn().getResponse().getContentAsString();

        DepositoDTO depositoCreado = objectMapper.readValue(responseJson, DepositoDTO.class);
        String depositoId = depositoCreado.id();


        DonacionDTO donacion = new DonacionDTO(
                "DON-IT-1", "DONADOR-1", depositoId, "Caja de leche", "PROD-LECHE", 15, EstadoDonacionEnum.INGRESADA);

        mockMvc.perform(post("/depositos/" + depositoId + "/donacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(donacion)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cantidad").value(15));


        Deposito depositoEnBd = depositoRepository.findById(Long.parseLong(depositoId)).orElseThrow();
        assertEquals(1, depositoEnBd.getStock().size(), "El paquete debió guardarse en el stock del depósito");
        assertEquals(15, depositoEnBd.getStock().get(0).getCantidad(), "La cantidad del paquete debe coincidir");

        Long paqueteIdEnBd = depositoEnBd.getStock().get(0).getId();

        String payloadEsperado = paqueteIdEnBd + "," + depositoId;
        verify(rabbitTemplate).convertAndSend(eq("donaciones_nuevas_queue"), eq(payloadEsperado));
    }
}