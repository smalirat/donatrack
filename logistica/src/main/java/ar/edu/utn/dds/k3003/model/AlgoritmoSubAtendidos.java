package ar.edu.utn.dds.k3003.model;

import java.util.Comparator;
import java.util.List;

public class AlgoritmoSubAtendidos implements AlgoritmoMatchmaking {

    @Override
    public Necesidad seleccionarNecesidad(Paquete paquete, List<Necesidad> necesidades) {
        if (necesidades == null || necesidades.isEmpty()) {
            return null;
        }

        return necesidades.stream()
                .max(Comparator.comparing(Necesidad::getCantidadObjetivo)
                        .thenComparing(Necesidad::getNivelDeUrgencia))
                .orElse(necesidades.get(0));
    }
}