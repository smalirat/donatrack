package ar.edu.utn.dds.k3003.proxies;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.*;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonadoresYEntidades;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaIncentivos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

@Component
public class DonadoresProxy implements FachadaDonadoresYEntidades {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public DonadoresProxy(RestTemplate restTemplate, @Value("${urls.donadores}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    @Override
    public List<NecesidadMaterialDTO> obtenerNecesidadesInsatisfechasDe(String productoSolicitadoID) {

        String url = baseUrl + "/necesidades?productoSolicitadoID=" + productoSolicitadoID;
        NecesidadMaterialDTO[] response = restTemplate.getForObject(url, NecesidadMaterialDTO[].class);
        return response != null ? Arrays.asList(response) : List.of();
    }

    @Override
    public NecesidadMaterialDTO satisfacerNecesidad(String necesidadID, Integer cantidad) throws NoSuchElementException {
        String url = baseUrl + "/necesidades/" + necesidadID + "/satisfaccion";
        SatisfaccionRequest request = new SatisfaccionRequest(cantidad);
        return restTemplate.postForObject(url, request, NecesidadMaterialDTO.class);
    }


}