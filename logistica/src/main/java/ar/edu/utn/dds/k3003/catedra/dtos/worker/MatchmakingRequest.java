package ar.edu.utn.dds.k3003.catedra.dtos.worker;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.PaqueteDTO;
import java.util.List;

public record MatchmakingRequest(
        PaqueteDTO paqueteDTO,
        List<NecesidadMaterialDTO> necesidadesDTO
) {}