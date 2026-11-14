package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.model.Asignacion;
import ar.edu.utn.dds.k3003.model.EstadoAsignacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AsignacionRepository extends JpaRepository<Asignacion, Long> {

    Optional<Asignacion> findByPaquetes_Id(Long paqueteID);

    List<Asignacion> findByNecesidadIDAndEstado(String necesidadID, EstadoAsignacion estado);
}