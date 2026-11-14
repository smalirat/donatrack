package ar.edu.utn.dds.k3003;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.DepositoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.PaqueteDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.worker.MatchmakingRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

@Component
public class MatchmakingWorker {

    private static final Logger log = LoggerFactory.getLogger(MatchmakingWorker.class);

    private final RestTemplate restTemplate;
    private final String urlDonadores;
    private final String urlLogistica;

    public MatchmakingWorker(RestTemplate restTemplate,
                             @Value("${urls.donadores}") String urlDonadores,
                             @Value("${urls.logistica:http://localhost:${server.port:8080}}") String urlLogistica) {
        this.restTemplate = restTemplate;
        this.urlDonadores = urlDonadores;
        this.urlLogistica = urlLogistica;
    }

    @RabbitListener(queues = "donaciones_nuevas_queue")
    public void recibirDonacion(String payload) {
        log.info("Worker: mensaje recibido de RabbitMQ -> {}", payload);
        try {
            String[] partes = payload.split(",");
            String paqueteId = partes[0];
            String depositoId = partes[1];

            DepositoDTO deposito = restTemplate.getForObject(urlLogistica + "/depositos/" + depositoId, DepositoDTO.class);
            if (deposito == null) return;

            PaqueteDTO paqueteDTO = deposito.stockActual().stream()
                    .filter(p -> p.id().equals(paqueteId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Paquete no encontrado en el depósito"));

            NecesidadMaterialDTO[] necesidadesArray = restTemplate.getForObject(
                    urlDonadores + "/necesidades?productoSolicitadoID=" + paqueteDTO.producto(),
                    NecesidadMaterialDTO[].class);
            List<NecesidadMaterialDTO> necesidades = Arrays.asList(necesidadesArray != null ? necesidadesArray : new NecesidadMaterialDTO[0]);

            MatchmakingRequest request = new MatchmakingRequest(paqueteDTO, necesidades);
            restTemplate.postForObject(urlLogistica + "/depositos/" + depositoId + "/matchmaking", request, Void.class);

            log.info("Worker: matchmaking procesado para el paquete {} del depósito {}", paqueteId, depositoId);

        } catch (Exception e) {
            log.error("Worker: error procesando el mensaje {}: {}", payload, e.getMessage());
            throw e;
        }
    }
}