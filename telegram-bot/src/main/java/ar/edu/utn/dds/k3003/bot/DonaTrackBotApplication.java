package ar.edu.utn.dds.k3003.bot;

import io.github.cdimascio.dotenv.Dotenv;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

public class DonaTrackBotApplication {
    public static void main(String[] args) throws Exception {
        String token = cargarToken();
        String donadoresApiUrl = cargarUrl("DONADORES_API_URL", "https://donadores-y-entidades-0uv4.onrender.com");
        String donacionesApiUrl = cargarUrl("DONACIONES_API_URL", "https://marco-maggio-springboot-api-entrega2-dsi.onrender.com");
        String logisticaApiUrl = cargarUrl("LOGISTICA_API_URL", "https://logistica-v3xw.onrender.com");
        String incentivosApiUrl = cargarUrl("INCENTIVOS_API_URL", "https://incentivos-nhyc.onrender.com");
        DonadoresApiClient apiClient = new DonadoresApiClient(donadoresApiUrl, donacionesApiUrl, logisticaApiUrl, incentivosApiUrl);
        try (TelegramBotsLongPollingApplication app = new TelegramBotsLongPollingApplication()) {
            app.registerBot(token, new DonaTrackBot(token, apiClient, donadoresApiUrl, donacionesApiUrl, logisticaApiUrl, incentivosApiUrl));
            System.out.println("DonaTrack Bot corriendo. Apuntando a todos los microservicios.");
            System.out.println("Presione Ctrl+C para detener.");
            while (app.isRunning()) {
                Thread.sleep(1000);
            }
        }
    }

    private static String cargarUrl(String variable, String valorPorDefecto) {
        String url = System.getenv(variable);
        if (url == null || url.isBlank()) {
            url = Dotenv.configure().ignoreIfMissing().load().get(variable);
        }
        return (url == null || url.isBlank()) ? valorPorDefecto : url;
    }

    private static String cargarToken() {
        String token = System.getenv("BOT_TOKEN");
        if (token == null || token.isBlank()) {
            token = Dotenv.configure().ignoreIfMissing().load().get("BOT_TOKEN");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "Falta el token del bot: definilo en la variable de entorno BOT_TOKEN "
                            + "o en un archivo .env en la raiz del proyecto (ver .env)");
        }
        return token;
    }
}