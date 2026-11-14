package ar.edu.utn.dds.k3003.exceptions;

import java.util.NoSuchElementException;

public class DepositoNoEncontradoException extends NoSuchElementException {
    public DepositoNoEncontradoException(String mensaje) {
        super(mensaje);
    }

    public DepositoNoEncontradoException() {
        super("El depósito solicitado no fue encontrado.");
    }
}