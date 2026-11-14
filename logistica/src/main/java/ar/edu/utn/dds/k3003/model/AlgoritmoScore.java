package ar.edu.utn.dds.k3003.model;

import java.util.Comparator;
import java.util.List;

public class AlgoritmoScore implements AlgoritmoMatchmaking {

    @Override
    public Necesidad seleccionarNecesidad(Paquete paquete, List<Necesidad> necesidades) {
        return necesidades.stream()
                .max(Comparator.comparingDouble(n -> calcularScore(paquete, n)))
                .orElseThrow();
    }

    private double calcularScore(Paquete paquete, Necesidad necesidad) {
        double cobertura = (double) paquete.getCantidad() / necesidad.getCantidadObjetivo();

        if (cobertura == 0) {
            return Double.MAX_VALUE;
        }

        return necesidad.getNivelDeUrgencia() / cobertura;
    }
}