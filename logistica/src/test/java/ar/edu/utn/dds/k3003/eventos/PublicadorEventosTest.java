package ar.edu.utn.dds.k3003.eventos;

import ar.edu.utn.dds.k3003.catedra.dtos.logistica.TipoAlgoritmoEnum;
import ar.edu.utn.dds.k3003.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class PublicadorEventosTest {

    private RabbitTemplate rabbitTemplate;
    private ObjectMapper objectMapper;
    private PublicadorEventos publicador;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        // Misma configuración de fechas que el ObjectMapper de Spring Boot: ISO-8601, no timestamps.
        objectMapper = new ObjectMapper().findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        publicador = new PublicadorEventos(rabbitTemplate, objectMapper);
    }

    @AfterEach
    void limpiarTransaccion() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private JsonNode mensajeEnviado(String routingKey) throws Exception {
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("donatrack.eventos"), eq(routingKey), captor.capture());
        assertEquals("application/json", captor.getValue().getMessageProperties().getContentType());
        return objectMapper.readTree(captor.getValue().getBody());
    }

    @Test
    void depositoActualizadoPublicaElStockAgrupadoPorProducto() throws Exception {
        Deposito deposito = new Deposito("Central", "Medrano 951", 1000);
        deposito.setId(3L);
        deposito.setTipoAlgoritmo(TipoAlgoritmoEnum.PRIORIDAD_POR_SCORE);
        deposito.recibirEnStock(new Paquete(1L, "DON-1", new Producto("7"), 200));
        deposito.recibirEnStock(new Paquete(2L, "DON-2", new Producto("7"), 100));
        deposito.recibirEnStock(new Paquete(3L, "DON-3", new Producto("2"), 120));

        publicador.depositoActualizado(deposito);

        JsonNode evento = mensajeEnviado("logistica.deposito.actualizado");
        assertEquals("DEPOSITO_ACTUALIZADO", evento.get("tipo").asText());
        assertEquals("logistica", evento.get("modulo").asText());
        assertEquals("3", evento.get("entidadId").asText());
        assertFalse(evento.get("eventoId").asText().isBlank());
        assertTrue(evento.get("ocurridoEn").asText().endsWith("Z"));

        JsonNode datos = evento.get("datos");
        assertEquals("PRIORIDAD_POR_SCORE", datos.get("algoritmo").asText());
        assertEquals(420, datos.get("stockTotal").asInt());
        assertEquals(2, datos.get("stockPorProducto").size());
        assertEquals("2", datos.get("stockPorProducto").get(0).get("productoId").asText());
        assertEquals(300, datos.get("stockPorProducto").get(1).get("cantidad").asInt());
        assertFalse(datos.get("eliminado").asBoolean());
    }

    @Test
    void asignacionActualizadaPublicaElDepositoDeCadaPaqueteYLasFechasEnUtc() throws Exception {
        Paquete paquete = new Paquete(15L, "DON-1", new Producto("7"), 30);
        paquete.setDepositoOrigenId(3L);
        Asignacion asignacion = new Asignacion(42L, List.of(paquete), "9", LocalDateTime.of(2026, 11, 2, 14, 0),
                EstadoAsignacion.ASIGNADA, OrigenAsignacion.MATCHMAKING);
        asignacion.completarEntrega();

        publicador.asignacionActualizada(asignacion);

        JsonNode datos = mensajeEnviado("logistica.asignacion.actualizada").get("datos");
        assertEquals("42", datos.get("id").asText());
        assertEquals("9", datos.get("necesidadId").asText());
        assertEquals("COMPLETADA", datos.get("estado").asText());
        assertEquals("MATCHMAKING", datos.get("origen").asText());
        assertEquals(Evento.enUtc(LocalDateTime.of(2026, 11, 2, 14, 0)).toString(), datos.get("fechaCreacion").asText());
        assertTrue(datos.get("fechaEntrega").asText().endsWith("Z"));
        JsonNode paqueteJson = datos.get("paquetes").get(0);
        assertEquals("15", paqueteJson.get("paqueteId").asText());
        assertEquals("DON-1", paqueteJson.get("donacionId").asText());
        assertEquals("3", paqueteJson.get("depositoId").asText());
        assertEquals(30, paqueteJson.get("cantidad").asInt());
    }

    @Test
    void depositoEliminadoPublicaLaBaja() throws Exception {
        Deposito deposito = new Deposito("Central", "Medrano 951", 1000);
        deposito.setId(3L);

        publicador.depositoEliminado(deposito);

        assertTrue(mensajeEnviado("logistica.deposito.actualizado").get("datos").get("eliminado").asBoolean());
    }

    @Test
    void dentroDeUnaTransaccionPublicaRecienDespuesDelCommit() {
        TransactionSynchronizationManager.initSynchronization();
        Deposito deposito = new Deposito("Central", "Medrano 951", 1000);
        deposito.setId(3L);

        publicador.depositoActualizado(deposito);
        verify(rabbitTemplate, never()).send(anyString(), anyString(), any(Message.class));

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(rabbitTemplate, times(1)).send(eq("donatrack.eventos"), eq("logistica.deposito.actualizado"), any(Message.class));
    }

    @Test
    void siRabbitFallaLaOperacionNoSeCae() {
        doThrow(new RuntimeException("broker caído")).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class));
        Deposito deposito = new Deposito("Central", "Medrano 951", 1000);
        deposito.setId(3L);

        assertDoesNotThrow(() -> publicador.depositoActualizado(deposito));
    }
}
