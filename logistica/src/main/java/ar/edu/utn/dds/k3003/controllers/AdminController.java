package ar.edu.utn.dds.k3003.controllers;

import ar.edu.utn.dds.k3003.Fachada;
import ar.edu.utn.dds.k3003.model.Asignacion;
import ar.edu.utn.dds.k3003.model.Deposito;
import ar.edu.utn.dds.k3003.model.Paquete;
import ar.edu.utn.dds.k3003.repositories.AsignacionRepository;
import ar.edu.utn.dds.k3003.repositories.DepositoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.util.List;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final DepositoRepository depositoRepository;
    private final AsignacionRepository asignacionRepository;
    private final DataSource dataSource;
    private final Fachada fachada;

    @Autowired
    public AdminController(DepositoRepository depositoRepository,
                           AsignacionRepository asignacionRepository,
                           DataSource dataSource,
                           Fachada fachada) {
        this.depositoRepository = depositoRepository;
        this.asignacionRepository = asignacionRepository;
        this.dataSource = dataSource;
        this.fachada = fachada;
    }

    @GetMapping("/depositos")
    public List<Deposito> listarDepositos() {
        return depositoRepository.findAll();
    }

    @GetMapping("/asignaciones")
    public List<Asignacion> listarAsignaciones() {
        return asignacionRepository.findAll();
    }

    @GetMapping("/paquetes/stock")
    public ResponseEntity<List<Paquete>> listarPaquetesEnStock() {
        return ResponseEntity.ok(fachada.listarPaquetesEnStock());
    }

    @GetMapping("/paquetes/entregados")
    public ResponseEntity<List<Paquete>> listarPaquetesEntregados() {
        return ResponseEntity.ok(fachada.listarPaquetesEntregados());
    }

    @DeleteMapping("/asignaciones/{id}")
    public ResponseEntity<String> borrarAsignacion(@PathVariable String id) {
        if (fachada.borrarAsignacion(Long.valueOf(id))) {
            return ResponseEntity.ok("Asignación " + id + " eliminada.");
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/paquetes/{id}")
    public ResponseEntity<String> borrarPaquete(@PathVariable String id) {
        if (fachada.borrarPaquete(Long.valueOf(id))) {
            return ResponseEntity.ok("Paquete " + id + " eliminado correctamente.");
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/asignaciones")
    public ResponseEntity<Asignacion> forzarCreacionAsignacion(@RequestBody Asignacion asignacion) {
        return ResponseEntity.ok(fachada.forzarCreacionAsignacion(asignacion));
    }

    @PostMapping("/depositos/{depositoId}/paquetes")
    public ResponseEntity<String> forzarCreacionPaquete(@PathVariable String depositoId, @RequestBody Paquete paquete) {
        if (fachada.forzarCreacionPaquete(Long.valueOf(depositoId), paquete)) {
            return ResponseEntity.ok("Paquete insertado en depósito " + depositoId);
        }
        return ResponseEntity.notFound().build();
    }

    @PutMapping("/asignaciones/{id}")
    public ResponseEntity<Asignacion> forzarActualizacionAsignacion(@PathVariable String id, @RequestBody Asignacion asignacionActualizada) {
        return fachada.forzarActualizacionAsignacion(Long.valueOf(id), asignacionActualizada)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/limpiar")
    public ResponseEntity<String> limpiarTodo() {
        fachada.limpiarTodo();
        return ResponseEntity.ok("Base de datos de Logística limpiada por completo.");
    }
}