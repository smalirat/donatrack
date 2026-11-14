package ar.edu.utn.dds.k3003.consultas;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

  static final List<String> COLECCIONES =
          List.of("donadores", "donaciones", "productos", "entidades", "necesidades", "depositos", "asignaciones");

  private final MongoTemplate mongoTemplate;

  public AdminController(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  // Cuántos documentos hay en cada colección: sirve para ver que la sincronización funciona.
  @GetMapping("/resumen")
  public Map<String, Long> resumen() {
    Map<String, Long> resumen = new LinkedHashMap<>();
    COLECCIONES.forEach(c -> resumen.put(c, mongoTemplate.getCollection(c).countDocuments()));
    return resumen;
  }

  // Vacía el modelo de lectura, para arrancar de cero junto con las bases de los módulos.
  @DeleteMapping("/modelo")
  public Map<String, String> vaciarModelo() {
    COLECCIONES.forEach(mongoTemplate::dropCollection);
    return Map.of("resultado", "Modelo de lectura vaciado.");
  }
}
