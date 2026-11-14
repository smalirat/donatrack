package ar.edu.utn.dds.k3003.app;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import java.net.http.HttpClient;

@Configuration
public class Config {

    @Bean
    public RestTemplate restTemplate() {
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);

        // Timeout de lectura (evita que se congele si Render tarda en responder)
        requestFactory.setReadTimeout(10000); // 10 segundos

        return new RestTemplate(requestFactory);
    }
}