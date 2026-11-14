package ar.edu.utn.dds.k3003.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

public class DonaTrackBot implements LongPollingSingleThreadUpdateConsumer {
    private static final int LARGO_MAXIMO_MENSAJE = 3500;
    private static final String MENU_INICIAL =
            "Hola! Soy el bot de DonaTrack.\n"
                    + "Que tipo de usuario sos?\n\n"
                    + "- Donador -> escribi /donador\n"
                    + "- Admin -> escribi /admin\n\n"
                    + "(podes escribir /status para chequear la conexion con los servicios)";

    private static final String MENU_DONADOR =
            "Menu Donador:\n"
                    + "/registrarme nombre|apellido|email|edad|domicilio|nroDocumento\n"
                    + "/misestadisticas \n"
                    + "/verdonador \n"
                    + "/verdonadores\n"
                    + "/donar donadorId|depositoId|productoId|cantidad|descripcion\n"
                    + "/verdonacion \n"
                    + "/misdonaciones ";

    private static final String MENU_ADMIN =
            "Menu Admin Principal:\n"
                    + "Elegi el modulo que queres gestionar:\n\n"
                    + "/admin_donadores (Entidades, Necesidades, Quejas y Donadores)\n"
                    + "/admin_donaciones (Donaciones y Productos)\n"
                    + "/admin_logistica (Depositos, Asignaciones y Entregas)\n"
                    + "/admin_incentivos (Misiones e Insignias)\n\n"
                    + "Otras acciones:\n"
                    + "/limpiarbd (Limpiar Base de Datos Completa)";

    private static final String MENU_ADMIN_DONADORES =
            "Menu Admin - Donadores y Entidades:\n"
                    + "/crearentidad razonSocial|domicilio|telefono|correo\n"
                    + "/editarentidad id|razonSocial|domicilio|telefono|correo\n"
                    + "/verentidad \n"
                    + "/verentidades\n"
                    + "/crearnecesidad entidadID|descripcion|productoSolicitadoID|cantidadObjetivo|tipo\n"
                    + "/modificarnecesidad id|urgencia|descripcion|cantidadObjetivo\n"
                    + "/borrarnecesidad \n"
                    + "/vernecesidad \n"
                    + "/vernecesidadesinsatisfechas [productoSolicitadoID]\n"
                    + "/satisfacernecesidad necesidadId|cantidad\n"
                    + "/cambiarestadodonador id|estado(VERIFICADO, SOSPECHOSO, BANEADO)\n"
                    + "/cambiarcategoriadonador id|categoria\n"
                    + "/puededonar \n"
                    + "/registrarqueja donadorId|donacionId|descripcion\n"
                    + "/verquejas ";

    private static final String MENU_ADMIN_DONACIONES =
            "Menu Admin - Donaciones:\n"
                    + "/borrardonacion \n"
                    + "/cambiarestadodonacion id|estado\n"
                    + "/crearproducto \n"
                    + "/verproductos\n"
                    + "/borrarproducto ";

    private static final String MENU_ADMIN_LOGISTICA =
            "Menu Admin - Logistica:\n"
                    + "/creardeposito algoritmo|nombre|direccion|capacidadMaxima\n"
                    + "/verdepositos\n"
                    + "/verdeposito \n"
                    + "/borrardeposito \n"
                    + "/verstock \n"
                    + "/verasignacion \n"
                    + "/reportarentrega paqueteId|donacionId|productoId|cantidad\n"
                    + "/limpiarlogistica";

    private static final String MENU_ADMIN_INCENTIVOS =
            "Menu Admin - Incentivos:\n"
                    + "/crearinsignia nombre|descripcion\n"
                    + "/verinsignias\n"
                    + "/borrarinsignia \n"
                    + "/crearmision nombre|insigniaId|categoriaInicio|categoriaFin|tipo(COMPLETITUD|DONACIONES_EXITOSAS|DONACIONES_ASCENDENTES|REVOLUCION_DONADORA)\n"
                    + "/vermisiones\n"
                    + "/borrarmision \n"
                    + "/asignarinsignia donadorId|insigniaId\n"
                    + "/asignarmision donadorId|misionId\n"
                    + "/verinsigniasdonador \n"
                    + "/vermisiondonador ";

    private final TelegramClient telegramClient;
    private final DonadoresApiClient apiClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String urlDonadores;
    private final String urlDonaciones;
    private final String urlLogistica;
    private final String urlIncentivos;

    public DonaTrackBot(String token, DonadoresApiClient apiClient, String urlDonadores, String urlDonaciones, String urlLogistica, String urlIncentivos) {
        this.telegramClient = new OkHttpTelegramClient(token);
        this.apiClient = apiClient;
        this.urlDonadores = urlDonadores;
        this.urlDonaciones = urlDonaciones;
        this.urlLogistica = urlLogistica;
        this.urlIncentivos = urlIncentivos;
    }

    @Override
    public void consume(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }
        String chatId = update.getMessage().getChatId().toString();
        String texto = update.getMessage().getText().trim();
        enviar(chatId, responderA(texto));
    }

    private String responderA(String textoRecibido) {
        String comando = textoRecibido.split("\\s+", 2)[0].toLowerCase();
        String resto = quitarComando(textoRecibido);
        try {
            return switch (comando) {
                case "/start" -> MENU_INICIAL;
                case "/donador" -> MENU_DONADOR;
                case "/admin" -> MENU_ADMIN;
                // sub-menús de admin
                case "/admin_donadores" -> MENU_ADMIN_DONADORES;
                case "/admin_donaciones" -> MENU_ADMIN_DONACIONES;
                case "/admin_logistica" -> MENU_ADMIN_LOGISTICA;
                case "/admin_incentivos" -> MENU_ADMIN_INCENTIVOS;
                case "/status" -> chequearStatus();
                // Donadores
                case "/registrarme" -> registrarme(resto);
                case "/misestadisticas" -> misEstadisticas(resto);
                case "/verdonador" -> verDonador(resto);
                case "/verdonadores" -> formatear(apiClient.listarDonadores());
                case "/cambiarestadodonador" -> cambiarEstadoDonador(resto);
                case "/cambiarcategoriadonador" -> cambiarCategoriaDonador(resto);
                case "/puededonar" -> puedeDonar(resto);
                case "/registrarqueja" -> registrarQueja(resto);
                case "/verquejas" -> verQuejas(resto);
                // Entidades y Necesidades
                case "/crearentidad" -> crearEntidad(resto);
                case "/editarentidad" -> editarEntidad(resto);
                case "/verentidad" -> verEntidad(resto);
                case "/verentidades" -> formatear(apiClient.listarEntidades());
                case "/crearnecesidad" -> crearNecesidad(resto);
                case "/modificarnecesidad" -> modificarNecesidad(resto);
                case "/borrarnecesidad" -> borrarNecesidad(resto);
                case "/vernecesidad" -> verNecesidad(resto);
                case "/vernecesidadesinsatisfechas" -> verNecesidadesInsatisfechas(resto);
                case "/satisfacernecesidad" -> satisfacerNecesidad(resto);
                // Donaciones
                case "/donar" -> donar(resto);
                case "/verdonacion" -> verDonacion(resto);
                case "/misdonaciones" -> misDonaciones(resto);
                case "/borrardonacion" -> borrarDonacion(resto);
                case "/cambiarestadodonacion" -> cambiarEstadoDonacion(resto);
                case "/crearproducto" -> crearProducto(resto);
                case "/verproductos" -> formatear(apiClient.listarProductos());
                case "/borrarproducto" -> borrarProducto(resto);
                // Logística
                case "/creardeposito" -> crearDeposito(resto);
                case "/verdepositos" -> formatear(apiClient.listarDepositos());
                case "/verdeposito" -> verDeposito(resto);
                case "/borrardeposito" -> borrarDeposito(resto);
                case "/verstock" -> verStock(resto);
                case "/verasignacion" -> verAsignacion(resto);
                case "/reportarentrega" -> reportarEntrega(resto);
                case "/limpiarlogistica" -> formatear(apiClient.limpiarLogistica());
                // Incentivos
                case "/procesardonador" -> procesarDonador(resto);
                case "/crearinsignia" -> crearInsignia(resto);
                case "/verinsignias" -> formatear(apiClient.listarInsignias());
                case "/borrarinsignia" -> borrarInsignia(resto);
                case "/crearmision" -> crearMision(resto);
                case "/vermisiones" -> formatear(apiClient.listarMisiones());
                case "/borrarmision" -> borrarMision(resto);
                case "/asignarinsignia" -> asignarInsignia(resto);
                case "/asignarmision" -> asignarMision(resto);
                case "/verinsigniasdonador" -> verInsigniasDonador(resto);
                case "/vermisiondonador" -> verMisionDonador(resto);
                // Admin Global
                case "/limpiarbd" -> limpiarBaseDeDatos();
                default -> "No entendi ese comando. Escribi /start para ver las opciones.";
            };
        } catch (IllegalArgumentException e) {
            return "Datos invalidos: " + e.getMessage();
        } catch (IOException | InterruptedException e) {
            return "Error de comunicación con el servicio: " + e.getMessage();
        }
    }

    private String quitarComando(String textoRecibido) {
        String[] partes = textoRecibido.split("\\s+", 2);
        return partes.length > 1 ? partes[1].trim() : "";
    }

    // ==========================================
    // DONADORES Y ENTIDADES
    // ==========================================
    private String registrarme(String args) throws IOException, InterruptedException {
        String[] campos = requerirCampos(args, 6, "/registrarme nombre|apellido|email|edad|domicilio|nroDocumento");
        return formatear(apiClient.crearDonador(campos[0], campos[1], campos[2], campos[3], campos[4], campos[5]));
    }

    private String misEstadisticas(String args) throws IOException, InterruptedException {
        String id = requerirId(args, "/misestadisticas ");
        return formatear(apiClient.estadisticasDonador(id));
    }

    private String verDonador(String args) throws IOException, InterruptedException {
        String id = requerirId(args, "/verdonador ");
        return formatear(apiClient.buscarDonadorPorId(id));
    }

    private String cambiarEstadoDonador(String args) throws IOException, InterruptedException {
        String[] campos = requerirCampos(args, 2, "/cambiarestadodonador id|estado");
        return formatear(apiClient.modificarEstadoDonador(campos[0], campos[1]));
    }

    private String cambiarCategoriaDonador(String args) throws IOException, InterruptedException {
        String[] campos = requerirCampos(args, 2, "/cambiarcategoriadonador id|categoria");
        return formatear(apiClient.modificarCategoriaDonador(campos[0], campos[1]));
    }

    private String puedeDonar(String args) throws IOException, InterruptedException {
        String id = requerirId(args, "/puededonar ");
        return formatear(apiClient.puedeDonar(id));
    }

    private String registrarQueja(String args) throws IOException, InterruptedException {
        String[] campos = requerirCampos(args, 3, "/registrarqueja donadorId|donacionId|descripcion");
        return formatear(apiClient.registrarQuejaDonador(campos[0], campos[1], campos[2]));
    }

    private String verQuejas(String args) throws IOException, InterruptedException {
        String id = requerirId(args, "/verquejas ");
        return formatear(apiClient.listarQuejasDonador(id));
    }

    private String crearEntidad(String args) throws IOException, InterruptedException {
        String[] campos = requerirCampos(args, 4, "/crearentidad razonSocial|domicilio|telefono|correo");
        return formatear(apiClient.crearEntidad(campos[0], campos[1], campos[2], campos[3]));
    }

    private String editarEntidad(String args) throws IOException, InterruptedException {
        String[] campos = requerirCampos(args, 5, "/editarentidad id|razonSocial|domicilio|telefono|correo");
        return formatear(apiClient.editarEntidad(campos[0], campos[1], campos[2], campos[3], campos[4]));
    }

    private String verEntidad(String args) throws IOException, InterruptedException {
        String id = requerirId(args, "/verentidad ");
        return formatear(apiClient.buscarEntidadPorId(id));
    }

    private String crearNecesidad(String args) throws IOException, InterruptedException {
        String[] campos = requerirCampos(args, 5, "/crearnecesidad entidadID|descripcion|productoSolicitadoID|cantidadObjetivo|tipo");
        return formatear(apiClient.crearNecesidad(campos[0], campos[1], campos[2], campos[3], campos[4]));
    }

    private String modificarNecesidad(String args) throws IOException, InterruptedException {
        String[] campos = requerirCampos(args, 4, "/modificarnecesidad id|urgencia|descripcion|cantidadObjetivo");
        return formatear(apiClient.modificarNecesidad(campos[0], campos[1], campos[2], campos[3]));
    }

    private String borrarNecesidad(String args) throws IOException, InterruptedException {
        String id = requerirId(args, "/borrarnecesidad ");
        return formatear(apiClient.borrarNecesidad(id));
    }

    private String verNecesidad(String args) throws IOException, InterruptedException {
        String id = requerirId(args, "/vernecesidad ");
        return formatear(apiClient.buscarNecesidadPorId(id));
    }

    private String verNecesidadesInsatisfechas(String args) throws IOException, InterruptedException {
        String productoId = args.isBlank() ? null : args.trim();
        return formatear(apiClient.listarNecesidadesInsatisfechas(productoId));
    }

    private String satisfacerNecesidad(String args) throws IOException, InterruptedException {
        String[] campos = requerirCampos(args, 2, "/satisfacernecesidad necesidadId|cantidad");
        return formatear(apiClient.satisfacerNecesidad(campos[0], campos[1]));
    }

    private String limpiarBaseDeDatos() throws IOException, InterruptedException {
        return formatear(apiClient.limpiarBaseDeDatos());
    }

    // ==========================================
    // DONACIONES
    // ==========================================
    private String donar(String args) throws IOException, InterruptedException {
        String[] campos = requerirCampos(args, 5, "/donar donadorId|depositoId|productoId|cantidad|descripcion");
        return formatear(apiClient.crearDonacion(campos[0], campos[1], campos[2], campos[3], campos[4]));
    }

    private String verDonacion(String args) throws IOException, InterruptedException {
        String id = requerirId(args, "/verdonacion ");
        return formatear(apiClient.buscarDonacionPorId(id));
    }

    private String misDonaciones(String args) throws IOException, InterruptedException {
        String donadorId = requerirId(args, "/misdonaciones ");
        return formatear(apiClient.buscarDonacionesPorDonador(donadorId));
    }

    private String borrarDonacion(String args) throws IOException, InterruptedException {
        String id = requerirId(args, "/borrardonacion ");
        return formatear(apiClient.borrarDonacion(id));
    }

    private String cambiarEstadoDonacion(String args) throws IOException, InterruptedException {
        String[] campos = requerirCampos(args, 2, "/cambiarestadodonacion id|estado");
        return formatear(apiClient.cambiarEstadoDonacion(campos[0], campos[1]));
    }

    private String crearProducto(String args) throws IOException, InterruptedException {
        if (args.isBlank()) throw new IllegalArgumentException("falta la descripcion. Uso: /crearproducto ");
        return formatear(apiClient.crearProducto(args.trim()));
    }

    private String borrarProducto(String args) throws IOException, InterruptedException {
        String id = requerirId(args, "/borrarproducto ");
        return formatear(apiClient.borrarProducto(id));
    }

    // ==========================================
    // LOGÍSTICA
    // ==========================================
    private String crearDeposito(String args) throws IOException, InterruptedException {
        String[] campos = requerirCampos(args, 4, "/creardeposito algoritmo|nombre|direccion|capacidadMaxima");
        return formatear(apiClient.crearDeposito(campos[0], campos[1], campos[2], campos[3]));
    }

    private String verDeposito(String args) throws IOException, InterruptedException {
        String id = requerirId(args, "/verdeposito ");
        return formatear(apiClient.buscarDeposito(id));
    }

    private String borrarDeposito(String args) throws IOException, InterruptedException {
        String id = requerirId(args, "/borrardeposito ");
        return formatear(apiClient.borrarDeposito(id));
    }

    private String verStock(String args) throws IOException, InterruptedException {
        String id = requerirId(args, "/verstock ");
        return formatear(apiClient.obtenerStock(id));
    }

    private String verAsignacion(String args) throws IOException, InterruptedException {
        String id = requerirId(args, "/verasignacion ");
        return formatear(apiClient.buscarAsignacion(id));
    }

    private String reportarEntrega(String args) throws IOException, InterruptedException {
        String[] c = requerirCampos(args, 4, "/reportarentrega paqueteId|donacionId|productoId|cantidad");
        return formatear(apiClient.reportarEntrega(c[0], c[1], c[2], c[3]));
    }

    // ==========================================
    // INCENTIVOS
    // ==========================================
    private String procesarDonador(String args) throws IOException, InterruptedException {
        String id = requerirId(args, "/procesardonador ");
        return formatear(apiClient.procesarDonador(id));
    }

    private String crearInsignia(String args) throws IOException, InterruptedException {
        String[] campos = requerirCampos(args, 2, "/crearinsignia nombre|descripcion");
        return formatear(apiClient.crearInsignia(campos[0], campos[1]));
    }

    private String borrarInsignia(String args) throws IOException, InterruptedException {
        String id = requerirId(args, "/borrarinsignia ");
        return formatear(apiClient.borrarInsignia(id));
    }

    private String crearMision(String args) throws IOException, InterruptedException {
        String[] campos = requerirCampos(args, 5,
                "/crearmision nombre|insigniaId|categoriaInicio(OCASIONAL|COLABORADOR|TRANSFORMADOR|SALVADOR|REVOLUCIONARIO)|categoriaFin|tipo(COMPLETITUD|DONACIONES_EXITOSAS|DONACIONES_ASCENDENTES|REVOLUCION_DONADORA)");
        return formatear(apiClient.crearMision(campos[0], campos[1], campos[2], campos[3], campos[4]));
    }

    private String borrarMision(String args) throws IOException, InterruptedException {
        String id = requerirId(args, "/borrarmision ");
        return formatear(apiClient.borrarMision(id));
    }

    private String asignarInsignia(String args) throws IOException, InterruptedException {
        String[] campos = requerirCampos(args, 2, "/asignarinsignia donadorId|insigniaId");
        return formatear(apiClient.asignarInsignia(campos[0], campos[1]));
    }

    private String asignarMision(String args) throws IOException, InterruptedException {
        String[] campos = requerirCampos(args, 2, "/asignarmision donadorId|misionId");
        return formatear(apiClient.asignarMision(campos[0], campos[1]));
    }

    private String verInsigniasDonador(String args) throws IOException, InterruptedException {
        String id = requerirId(args, "/verinsigniasdonador ");
        return formatear(apiClient.verInsigniasDonador(id));
    }

    private String verMisionDonador(String args) throws IOException, InterruptedException {
        String id = requerirId(args, "/vermisiondonador ");
        return formatear(apiClient.verMisionDonador(id));
    }

    // ==========================================
    // HELPERS DEL BOT
    // ==========================================
    private String requerirId(String args, String uso) {
        if (args.isBlank()) {
            throw new IllegalArgumentException("falta el ID. Uso: " + uso);
        }
        return args.trim();
    }

    private String[] requerirCampos(String args, int cantidadEsperada, String uso) {
        if (args.isBlank()) {
            throw new IllegalArgumentException("faltan los datos. Uso: " + uso);
        }
        String[] campos = args.split("\\|", -1);
        if (campos.length != cantidadEsperada) {
            throw new IllegalArgumentException("se esperaban " + cantidadEsperada + " campos separados por '|'. Uso: " + uso);
        }
        for (int i = 0; i < campos.length; i++) {
            campos[i] = campos[i].trim();
        }
        return campos;
    }

    private String chequearStatus() {
        StringBuilder sb = new StringBuilder("Estado de los servicios:\n");
        sb.append(lineaStatus("Donadores y Entidades", urlDonadores + "/donadores"));
        sb.append(lineaStatus("Donaciones", urlDonaciones + "/donaciones"));
        sb.append(lineaStatus("Logistica", urlLogistica + "/depositos"));
        sb.append(lineaStatus("Incentivos", urlIncentivos + "/insignias"));
        return sb.toString();
    }

    private String lineaStatus(String nombre, String url) {
        DonadoresApiClient.Resultado resp = apiClient.ping(url);
        String estado;
        if (!resp.ok() && resp.status() == -1) {
            estado = "OFFLINE (" + resp.cuerpo() + ")";
        } else if (resp.ok()) {
            estado = "OK (HTTP " + resp.status() + ")";
        } else {
            estado = "RESPONDE PERO CON ERROR (HTTP " + resp.status() + ")";
        }
        return "- " + nombre + ": " + estado + "\n";
    }

    private String formatear(DonadoresApiClient.Resultado resultado) {
        String prefijo = resultado.ok() ? "" : "[HTTP " + resultado.status() + "] ";
        String cuerpo = intentarEmbellecer(resultado.cuerpo());
        String mensaje = prefijo + cuerpo;
        if (mensaje.length() > LARGO_MAXIMO_MENSAJE) {
            mensaje = mensaje.substring(0, LARGO_MAXIMO_MENSAJE) + "\n... (truncado)";
        }
        return mensaje;
    }

    private String intentarEmbellecer(String posibleJson) {
        if (posibleJson == null || posibleJson.isBlank()) {
            return "(respuesta vacia)";
        }
        try {
            JsonNode nodo = objectMapper.readTree(posibleJson);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(nodo);
        } catch (IOException e) {
            return posibleJson;
        }
    }

    private void enviar(String chatId, String texto) {
        SendMessage mensaje = SendMessage.builder().chatId(chatId).text(texto).build();
        try {
            telegramClient.execute(mensaje);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}