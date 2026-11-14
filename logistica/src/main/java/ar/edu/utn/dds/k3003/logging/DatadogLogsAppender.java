package ar.edu.utn.dds.k3003.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

// Manda los logs a Datadog por HTTP. Sin API key no se inicia: los logs quedan solo en consola
public class DatadogLogsAppender extends AppenderBase<ILoggingEvent> {

    private String apiKey;
    private String site = "us5.datadoghq.com";
    private String service = "logistica";
    private String env = "produccion";

    private HttpClient httpClient;
    private ObjectMapper objectMapper;
    private URI intakeUri;

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setSite(String site) {
        this.site = site;
    }

    public void setService(String service) {
        this.service = service;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    @Override
    public void start() {
        if (apiKey == null || apiKey.isBlank()) {
            addInfo("DatadogLogsAppender sin API key: no se inicia");
            return;
        }
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.objectMapper = new ObjectMapper();
        this.intakeUri = URI.create("https://http-intake.logs." + site + "/api/v2/logs");
        super.start();
    }

    @Override
    protected void append(ILoggingEvent event) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ddsource", "java");
            body.put("ddtags", "env:" + env + ",application:" + service);
            body.put("service", service);
            body.put("message", mensaje(event));
            body.put("level", event.getLevel().toString());
            body.put("logger", event.getLoggerName());
            body.putAll(event.getMDCPropertyMap());

            HttpRequest request = HttpRequest.newBuilder(intakeUri)
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("DD-API-KEY", apiKey)
                    .POST(BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            httpClient.send(request, BodyHandlers.discarding());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            addError("No se pudo enviar el log a Datadog: " + e.getMessage());
        }
    }

    private String mensaje(ILoggingEvent event) {
        IThrowableProxy error = event.getThrowableProxy();
        if (error == null) {
            return event.getFormattedMessage();
        }
        return event.getFormattedMessage() + "\n" + ThrowableProxyUtil.asString(error);
    }
}
