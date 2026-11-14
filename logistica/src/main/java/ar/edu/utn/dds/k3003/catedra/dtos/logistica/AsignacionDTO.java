package ar.edu.utn.dds.k3003.catedra.dtos.logistica;

import java.time.LocalDateTime;
import java.util.List;

public record AsignacionDTO(
        String id,
        List<String> paqueteIDs,
        String necesidadID,
        LocalDateTime fecha,
        EstadoAsginacionEnum estado,
        String origen
) {
}