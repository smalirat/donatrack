package ar.edu.utn.dds.k3003.exceptions;

public class PaqueteNoEncontradoException extends RuntimeException {
    public PaqueteNoEncontradoException(String mensaje) {
        super(mensaje);
    }

    public PaqueteNoEncontradoException() {
        super("El paquete solicitado no fue encontrado.");
    }
}