package ar.edu.utn.dds.k3003.controllers;

import ar.edu.utn.dds.k3003.catedra.dtos.ErrorDTO;
import ar.edu.utn.dds.k3003.exceptions.DepositoSinCapacidadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.NoSuchElementException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity handleIllegalArgument(IllegalArgumentException ex) {
        log.error("Error no controlado en el servidor: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorDTO("Petición Inválida", ex.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity handleNotFound(NoSuchElementException ex) {
        log.error("Error no controlado en el servidor: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorDTO("Recurso no encontrado", ex.getMessage()));
    }

    @ExceptionHandler(DepositoSinCapacidadException.class)
    public ResponseEntity handleSinCapacidad(DepositoSinCapacidadException ex) {
        log.warn("Donación rechazada por capacidad: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorDTO("Depósito sin capacidad", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity handleGeneralError(Exception ex) {
        log.error("Error no controlado en el servidor: {}", ex.getMessage(), ex);
        ex.printStackTrace();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorDTO("Error Interno", ex.getMessage()));
    }
}