package ar.edu.utn.dds.k3003.exceptions;

public class AsignacionNoEncontradaException extends RuntimeException {
    public AsignacionNoEncontradaException(String mensaje) {
        super(mensaje);
    }

    public AsignacionNoEncontradaException() {
        super("La asignación solicitada no fue encontrada.");
    }
}