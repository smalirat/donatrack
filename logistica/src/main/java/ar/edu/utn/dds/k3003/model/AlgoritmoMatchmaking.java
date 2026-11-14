package ar.edu.utn.dds.k3003.model;

import java.util.List;

public interface AlgoritmoMatchmaking {
    Necesidad seleccionarNecesidad(Paquete paquete, List<Necesidad> necesidades);
}