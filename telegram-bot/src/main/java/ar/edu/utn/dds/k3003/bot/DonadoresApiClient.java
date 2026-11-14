package ar.edu.utn.dds.k3003.bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public class DonadoresApiClient {
    public record Resultado(boolean ok, int status, String cuerpo) {}

    private final String urlDonadores;
    private final String urlDonaciones;
    private final String urlLogistica;
    private final String urlIncentivos;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DonadoresApiClient(String urlDonadores, String urlDonaciones, String urlLogistica, String urlIncentivos) {
        this.urlDonadores = limpiarUrl(urlDonadores);
        this.urlDonaciones = limpiarUrl(urlDonaciones);
        this.urlLogistica = limpiarUrl(urlLogistica);
        this.urlIncentivos = limpiarUrl(urlIncentivos);
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    private String limpiarUrl(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    // ==========================================
    // DONADORES Y ENTIDADES
    // ==========================================
    public Resultado listarDonadores() throws IOException, InterruptedException {
        return get(urlDonadores, "/donadores");
    }

    public Resultado buscarDonadorPorId(String id) throws IOException, InterruptedException {
        return get(urlDonadores, "/donadores/" + id);
    }

    public Resultado crearDonador(String nombre, String apellido, String email, String edad, String domicilio, String nroDocumento) throws IOException, InterruptedException {
        Map body = new LinkedHashMap<>();
        body.put("nombre", nombre);
        body.put("apellido", apellido);
        body.put("email", email);
        body.put("edad", parseEntero(edad, "edad"));
        body.put("domicilio", domicilio);
        body.put("nroDocumento", nroDocumento);
        return post(urlDonadores, "/donadores", body);
    }

    public Resultado modificarEstadoDonador(String id, String estado) throws IOException, InterruptedException {
        Map body = new LinkedHashMap<>();
        body.put("estado", estado.toUpperCase());
        return patch(urlDonadores, "/donadores/" + id + "/estado", body);
    }

    public Resultado modificarCategoriaDonador(String id, String categoria) throws IOException, InterruptedException {
        Map body = new LinkedHashMap<>();
        body.put("categoria", categoria);
        return patch(urlDonadores, "/donadores/" + id + "/categoria", body);
    }

    public Resultado puedeDonar(String id) throws IOException, InterruptedException {
        return get(urlDonadores, "/donadores/" + id + "/puede-donar");
    }

    public Resultado estadisticasDonador(String id) throws IOException, InterruptedException {
        return get(urlDonadores, "/donadores/" + id + "/estadisticas");
    }

    public Resultado registrarQuejaDonador(String id, String donacionId, String descripcion) throws IOException, InterruptedException {
        Map body = new LinkedHashMap<>();
        body.put("donacionID", donacionId);
        body.put("descripcion", descripcion);
        return post(urlDonadores, "/donadores/" + id + "/quejas", body);
    }

    public Resultado listarQuejasDonador(String id) throws IOException, InterruptedException {
        return get(urlDonadores, "/donadores/" + id + "/quejas");
    }

    public Resultado listarEntidades() throws IOException, InterruptedException {
        return get(urlDonadores, "/entidades");
    }

    public Resultado buscarEntidadPorId(String id) throws IOException, InterruptedException {
        return get(urlDonadores, "/entidades/" + id);
    }

    public Resultado crearEntidad(String razonSocial, String domicilio, String telefono, String correo) throws IOException, InterruptedException {
        Map body = new LinkedHashMap<>();
        body.put("razonSocial", razonSocial);
        body.put("domicilio", domicilio);
        body.put("telefono", telefono);
        body.put("correo", correo);
        return post(urlDonadores, "/entidades", body);
    }

    public Resultado editarEntidad(String id, String razonSocial, String domicilio, String telefono, String correo) throws IOException, InterruptedException {
        Map body = new LinkedHashMap<>();
        body.put("razonSocial", razonSocial);
        body.put("domicilio", domicilio);
        body.put("telefono", telefono);
        body.put("correo", correo);
        return put(urlDonadores, "/entidades/" + id, body);
    }

    public Resultado crearNecesidad(String entidadId, String descripcion, String productoSolicitadoId, String cantidadObjetivo, String tipo) throws IOException, InterruptedException {
        Map body = new LinkedHashMap<>();
        body.put("entidadID", entidadId);
        body.put("descripcion", descripcion);
        body.put("productoSolicitadoID", productoSolicitadoId);
        body.put("cantidadObjetivo", parseEntero(cantidadObjetivo, "cantidadObjetivo"));
        body.put("tipo", tipo.toUpperCase());
        return post(urlDonadores, "/necesidades", body);
    }

    public Resultado listarNecesidadesInsatisfechas(String productoSolicitadoId) throws IOException, InterruptedException {
        String path = "/necesidades";
        if (productoSolicitadoId != null && !productoSolicitadoId.isBlank()) {
            path += "?productoSolicitadoID=" + productoSolicitadoId;
        }
        return get(urlDonadores, path);
    }

    public Resultado buscarNecesidadPorId(String id) throws IOException, InterruptedException {
        return get(urlDonadores, "/necesidades/" + id);
    }

    public Resultado modificarNecesidad(String id, String urgencia, String descripcion, String cantObjetivo) throws IOException, InterruptedException {
        Map body = new LinkedHashMap<>();
        body.put("nivelDeUrgencia", parseEntero(urgencia, "urgencia"));
        body.put("descripcion", descripcion);
        body.put("cantidadObjetivo", parseEntero(cantObjetivo, "cantidadObjetivo"));
        return put(urlDonadores, "/necesidades/" + id, body);
    }

    public Resultado borrarNecesidad(String id) throws IOException, InterruptedException {
        return delete(urlDonadores, "/necesidades/" + id);
    }

    public Resultado satisfacerNecesidad(String necesidadId, String cantidad) throws IOException, InterruptedException {
        Map body = new LinkedHashMap<>();
        body.put("cantidad", parseEntero(cantidad, "cantidad"));
        return post(urlDonadores, "/necesidades/" + necesidadId + "/satisfaccion", body);
    }

    public Resultado limpiarBaseDeDatos() throws IOException, InterruptedException {
        return delete(urlDonadores, "/admin/limpiar");
    }

    // ==========================================
    // DONACIONES
    // ==========================================
    public Resultado crearDonacion(String donadorId, String depositoId, String productoId, String cantidad, String descripcion) throws IOException, InterruptedException {
        Map body = new LinkedHashMap<>();
        body.put("donadorID", donadorId);
        body.put("depositoID", depositoId);
        body.put("productoID", productoId);
        body.put("cantidad", parseEntero(cantidad, "cantidad"));
        body.put("descripcion", descripcion);
        return post(urlDonaciones, "/donaciones", body);
    }

    public Resultado buscarDonacionPorId(String id) throws IOException, InterruptedException {
        return get(urlDonaciones, "/donaciones/" + id);
    }

    public Resultado buscarDonacionesPorDonador(String donadorId) throws IOException, InterruptedException {
        return get(urlDonaciones, "/donaciones/search?donadorID=" + donadorId + "&fechaInicio=2024-01-01");
    }

    public Resultado borrarDonacion(String id) throws IOException, InterruptedException {
        return delete(urlDonaciones, "/donaciones/" + id);
    }

    public Resultado cambiarEstadoDonacion(String id, String estado) throws IOException, InterruptedException {
        Map body = new LinkedHashMap<>();
        body.put("estado", estado.toUpperCase());
        return patch(urlDonaciones, "/donaciones/" + id + "/estado", body);
    }

    public Resultado crearProducto(String descripcion) throws IOException, InterruptedException {
        Map body = new LinkedHashMap<>();
        body.put("descripcion", descripcion);
        return post(urlDonaciones, "/productos", body);
    }

    public Resultado listarProductos() throws IOException, InterruptedException {
        return get(urlDonaciones, "/productos");
    }

    public Resultado borrarProducto(String id) throws IOException, InterruptedException {
        return delete(urlDonaciones, "/productos/" + id);
    }

    // ==========================================
    // LOGÍSTICA
    // ==========================================
    public Resultado listarDepositos() throws IOException, InterruptedException {
        return get(urlLogistica, "/depositos");
    }

    public Resultado buscarDeposito(String id) throws IOException, InterruptedException {
        return get(urlLogistica, "/depositos/" + id);
    }

    public Resultado crearDeposito(String algoritmo, String nombre, String direccion, String capacidad) throws IOException, InterruptedException {
        Map body = new LinkedHashMap<>();
        body.put("algoritmo", algoritmo.toUpperCase());
        body.put("nombre", nombre);
        body.put("direccion", direccion);
        body.put("capacidadMaxima", parseEntero(capacidad, "capacidadMaxima"));
        body.put("stockActual", java.util.List.of());
        return post(urlLogistica, "/depositos", body);
    }

    public Resultado borrarDeposito(String id) throws IOException, InterruptedException {
        return delete(urlLogistica, "/depositos/" + id);
    }

    public Resultado buscarAsignacion(String id) throws IOException, InterruptedException {
        return get(urlLogistica, "/asignaciones/" + id);
    }

    public Resultado obtenerStock(String productoId) throws IOException, InterruptedException {
        return get(urlLogistica, "/stock/" + productoId);
    }

    public Resultado reportarEntrega(String paqueteId, String donacionId, String productoId, String cantidad) throws IOException, InterruptedException {
        Map body = new LinkedHashMap<>();
        body.put("id", paqueteId);
        body.put("donacionID", donacionId);
        body.put("producto", productoId);
        body.put("cantidad", parseEntero(cantidad, "cantidad"));
        return post(urlLogistica, "/entregas", body);
    }

    public Resultado limpiarLogistica() throws IOException, InterruptedException {
        return delete(urlLogistica, "/admin/limpiar");
    }

    // ==========================================
    // INCENTIVOS
    // ==========================================
    public Resultado procesarDonador(String id) throws IOException, InterruptedException {
        return post(urlIncentivos, "/donadores/" + id + "/procesamiento", new LinkedHashMap<>());
    }

    public Resultado listarInsignias() throws IOException, InterruptedException {
        return get(urlIncentivos, "/insignias");
    }

    public Resultado buscarInsignia(String id) throws IOException, InterruptedException {
        return get(urlIncentivos, "/insignias/" + id);
    }

    public Resultado crearInsignia(String nombre, String descripcion) throws IOException, InterruptedException {
        Map body = new LinkedHashMap<>();
        body.put("nombre", nombre);
        body.put("descripcion", descripcion);
        return post(urlIncentivos, "/insignias", body);
    }

    public Resultado borrarInsignia(String id) throws IOException, InterruptedException {
        return delete(urlIncentivos, "/insignias/" + id);
    }

    public Resultado listarMisiones() throws IOException, InterruptedException {
        return get(urlIncentivos, "/misiones");
    }

    public Resultado buscarMision(String id) throws IOException, InterruptedException {
        return get(urlIncentivos, "/misiones/" + id);
    }

    public Resultado crearMision(String nombre, String insigniaId, String categoriaInicio, String categoriaFin, String tipo) throws IOException, InterruptedException {
        Map body = new LinkedHashMap<>();
        body.put("nombre", nombre);
        body.put("insigniaID", insigniaId);
        body.put("categoriaInicio", categoriaInicio.toUpperCase());
        body.put("categoriaFin", categoriaFin.toUpperCase());
        body.put("tipo", tipo.toUpperCase());
        return post(urlIncentivos, "/misiones", body);
    }

    public Resultado borrarMision(String id) throws IOException, InterruptedException {
        return delete(urlIncentivos, "/misiones/" + id);
    }

    public Resultado verInsigniasDonador(String donadorId) throws IOException, InterruptedException {
        return get(urlIncentivos, "/donadores/" + donadorId + "/insignias");
    }

    public Resultado asignarInsignia(String donadorId, String insigniaId) throws IOException, InterruptedException {
        Map body = new LinkedHashMap<>();
        body.put("insigniaID", insigniaId);
        return post(urlIncentivos, "/donadores/" + donadorId + "/insignias", body);
    }

    public Resultado verMisionDonador(String donadorId) throws IOException, InterruptedException {
        return get(urlIncentivos, "/donadores/" + donadorId + "/mision");
    }

    public Resultado asignarMision(String donadorId, String misionId) throws IOException, InterruptedException {
        Map body = new LinkedHashMap<>();
        body.put("misionID", misionId);
        return post(urlIncentivos, "/donadores/" + donadorId + "/mision", body);
    }

    // ==========================================
    // HELPERS HTTP
    // ==========================================
    public Resultado ping(String urlCompleta) {
        try {
            HttpRequest request = HttpRequest.newBuilder(java.net.URI.create(urlCompleta)).GET().timeout(java.time.Duration.ofSeconds(5)).build();
            return enviar(request);
        } catch (Exception e) {
            return new Resultado(false, -1, e.getMessage());
        }
    }

    private int parseEntero(String valor, String nombreCampo) {
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("El campo '" + nombreCampo + "' debe ser un numero entero");
        }
    }

    private Resultado get(String baseUrl, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build();
        return enviar(request);
    }

    private Resultado post(String baseUrl, String path, Map body) throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(json))
                .build();
        return enviar(request);
    }

    private Resultado put(String baseUrl, String path, Map body) throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .PUT(BodyPublishers.ofString(json))
                .build();
        return enviar(request);
    }

    private Resultado patch(String baseUrl, String path, Map body) throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .method("PATCH", BodyPublishers.ofString(json))
                .build();
        return enviar(request);
    }

    private Resultado delete(String baseUrl, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path)).DELETE().build();
        return enviar(request);
    }

    private Resultado enviar(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse response = httpClient.send(request, BodyHandlers.ofString());
        boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
        return new Resultado(ok, response.statusCode(), response.body());
    }
}