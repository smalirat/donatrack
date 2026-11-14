package ar.edu.utn.dds.k3003.controllers;

import ar.edu.utn.dds.k3003.Fachada;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.DepositoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.PaqueteDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.TipoAlgoritmoEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.worker.MatchmakingRequest;
import ar.edu.utn.dds.k3003.exceptions.DepositoNoEncontradoException;
import ar.edu.utn.dds.k3003.exceptions.PaqueteNoEncontradoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.AsignacionDTO;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/depositos")
public class DepositoController {

    private final Fachada fachada;

    public DepositoController(Fachada fachada) {
        this.fachada = fachada;
    }

    @PostMapping
    public ResponseEntity<DepositoDTO> crearDeposito(@RequestBody DepositoDTO depositoDTO) {
        DepositoDTO depositoAgregado = fachada.agregarDeposito(depositoDTO);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(depositoAgregado);
    }

    @GetMapping
    public ResponseEntity<List<DepositoDTO>> obtenerTodos() {
        List<DepositoDTO> depositos = fachada.obtenerTodosLosDepositos();
        return ResponseEntity.ok(depositos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DepositoDTO> obtenerPorId(@PathVariable String id) {
        try {
            DepositoDTO deposito = fachada.buscarDepositoPorID(id);
            return ResponseEntity.ok(deposito);
        } catch (DepositoNoEncontradoException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<DepositoDTO> eliminarDeposito(@PathVariable String id) {
        try {
            DepositoDTO depositoEliminado = fachada.eliminarDeposito(id);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body(depositoEliminado);
        } catch (DepositoNoEncontradoException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/donacion")
    public ResponseEntity<PaqueteDTO> gestionarDonacion(
            @PathVariable String id,
            @RequestBody DonacionDTO request) {

        if (request.id() == null || request.id().isBlank()) {
            throw new IllegalArgumentException(
                    "El ID de donación es obligatorio."
            );
        }

        if (request.cantidad() == null || request.cantidad() <= 0) {
            throw new IllegalArgumentException(
                    "La cantidad debe ser mayor a cero."
            );
        }

        PaqueteDTO paquete = this.fachada.gestionarDonacion(
                id,
                request.id(),
                request.productoID(),
                request.cantidad()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(paquete);
    }

    @PostMapping("/{id}/matchmaking")
    public ResponseEntity<AsignacionDTO> ejecutarMatchmaking(
            @PathVariable String id,
            @RequestBody MatchmakingRequest request) {

        AsignacionDTO asignacion = fachada.ejecutarMatchmaking(
                id,
                request.paqueteDTO(),
                request.necesidadesDTO()
        );

        if (asignacion != null) {
            return ResponseEntity.status(HttpStatus.CREATED).body(asignacion);
        }
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/algoritmo")
    public ResponseEntity modificarAlgoritmo(
            @PathVariable String id,
            @RequestBody Map request) {

        String algoritmoStr = request.get("algoritmo").toString();

        if (algoritmoStr == null || algoritmoStr.isBlank()) {
            throw new IllegalArgumentException("El campo 'algoritmo' es obligatorio.");
        }

        try {
            TipoAlgoritmoEnum nuevoAlgoritmo = TipoAlgoritmoEnum.valueOf(algoritmoStr.toUpperCase());

            fachada.setAlgoritmoMM(id, nuevoAlgoritmo);

            return ResponseEntity.ok("Algoritmo del depósito " + id + " actualizado a " + nuevoAlgoritmo);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Algoritmo no válido");
        }
    }

}