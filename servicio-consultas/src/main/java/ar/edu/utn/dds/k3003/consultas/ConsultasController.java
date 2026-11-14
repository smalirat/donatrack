package ar.edu.utn.dds.k3003.consultas;

import org.bson.Document;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

// Desde el gateway se accede con el prefijo /api/consultas (por ejemplo /api/consultas/calidad).
@RestController
public class ConsultasController {

  private final ConsultasService consultas;

  public ConsultasController(ConsultasService consultas) {
    this.consultas = consultas;
  }

  @GetMapping("/donadores/{id}/ficha")
  public Document fichaDonador(@PathVariable String id) {
    return consultas.fichaDonador(id);
  }

  @GetMapping("/necesidades-urgentes")
  public List<Document> necesidadesUrgentes(@RequestParam(required = false) Integer urgenciaMinima,
                                            @RequestParam(required = false) String tipo,
                                            @RequestParam(required = false) String categoriaId,
                                            @RequestParam(required = false) String subcategoriaId) {
    return consultas.necesidadesUrgentes(urgenciaMinima, tipo, categoriaId, subcategoriaId);
  }

  @GetMapping("/depositos/estado")
  public List<Document> estadoDepositos(
          @RequestParam(required = false) String depositoId,
          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
    return consultas.estadoDepositos(depositoId, desde, hasta);
  }

  @GetMapping("/calidad")
  public Document calidad(@RequestParam(defaultValue = "10") int top) {
    return consultas.calidadDonado(top);
  }
}
