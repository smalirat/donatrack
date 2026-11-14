package ar.edu.utn.dds.k3003.controllers;

import ar.edu.utn.dds.k3003.Fachada;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.AsignacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.PaqueteDTO;
import ar.edu.utn.dds.k3003.exceptions.AsignacionNoEncontradaException;
import ar.edu.utn.dds.k3003.exceptions.PaqueteNoEncontradoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class LogisticaController {

    private final Fachada fachada;

    public LogisticaController(Fachada fachada) {
        this.fachada = fachada;
    }

    @GetMapping("/asignaciones/{id}")
    public ResponseEntity<AsignacionDTO> obtenerAsignacion(@PathVariable String id) {
        try {
            AsignacionDTO asignacion = fachada.buscarAsignacionPorId(id);
            return ResponseEntity.ok(asignacion);

        } catch (AsignacionNoEncontradaException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/entregas")
    public ResponseEntity<?> reportarEntrega(@RequestBody PaqueteDTO paqueteDTO) {
        try {
            AsignacionDTO asignacionCompletada = fachada.reportarEntrega(paqueteDTO);
            return ResponseEntity.status(HttpStatus.CREATED).body(asignacionCompletada);

        } catch (PaqueteNoEncontradoException | AsignacionNoEncontradaException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Error en Logística (Interno): " + e.getMessage());

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("Error en servicio externo: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error inesperado: " + e.getMessage());
        }
    }

    @GetMapping("/stock/{productoID}")
    public ResponseEntity<Integer> obtenerStockDisponible(
            @PathVariable String productoID) {

        Integer cantidad =
                this.fachada.obtenerStockDisponible(productoID);

        return ResponseEntity.ok(cantidad);
    }

    @PostMapping("/asignaciones/stock")
    public ResponseEntity<AsignacionDTO> asignarStockANecesidad(
            @RequestBody NecesidadMaterialDTO necesidad) {

        AsignacionDTO asignacion =
                this.fachada.asignarStockANecesidad(necesidad);

        if (asignacion == null) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(asignacion);
    }
}