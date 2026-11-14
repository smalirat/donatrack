package ar.edu.utn.dds.k3003.proxies;

import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.*;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonaciones;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@Component
public class DonacionesProxy implements FachadaDonaciones {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public DonacionesProxy(RestTemplate restTemplate, @Value("${urls.donaciones}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    @Override
    public DonacionDTO buscarDonacionPorID(String donacionID) throws NoSuchElementException {
        String url = baseUrl + "/donaciones/" + donacionID;
        return restTemplate.getForObject(url, DonacionDTO.class);
    }

    @Override
    public DonacionDTO cambiarEstadoDeDonacion(String donacionID, EstadoDonacionEnum estado) throws NoSuchElementException {
        String url = baseUrl + "/donaciones/" + donacionID + "/estado";

        Map<String, Object> body = new HashMap<>();
        body.put("estado", estado.name());
        return restTemplate.patchForObject(url, body, DonacionDTO.class);
    }

}