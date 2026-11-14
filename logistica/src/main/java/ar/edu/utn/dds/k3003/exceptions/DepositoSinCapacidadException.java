package ar.edu.utn.dds.k3003.exceptions;

public class DepositoSinCapacidadException extends IllegalStateException {
    public DepositoSinCapacidadException(String mensaje) {
        super(mensaje);
    }
}
