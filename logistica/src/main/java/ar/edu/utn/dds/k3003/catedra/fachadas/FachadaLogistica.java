package ar.edu.utn.dds.k3003.catedra.fachadas;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.*;
import java.util.List;
import java.util.NoSuchElementException;

public interface FachadaLogistica {

  DepositoDTO agregarDeposito(DepositoDTO deposito);

  DepositoDTO buscarDepositoPorID(String depositoID) throws NoSuchElementException;

  AsignacionDTO buscarAsignacionPorPaqueteID(String paqueteID) throws NoSuchElementException;

  PaqueteDTO gestionarDonacion(
          String depositoID,
          String donacionID,
          String productoID,
          Integer cantidad
  );

  void setAlgoritmoMM(String depositoID, TipoAlgoritmoEnum tipoAlgoritmo);

  AsignacionDTO ejecutarMatchmaking(
      String depositoID, PaqueteDTO paqueteDTO, List<NecesidadMaterialDTO> necesidades);

  AsignacionDTO reportarEntrega(PaqueteDTO paqueteDTO);

  void setFachadaDonadoresYEntidades(FachadaDonadoresYEntidades fachadaDonadoresYEntidades);

  void setFachadaDonaciones(FachadaDonaciones fachadaDonaciones);

  Integer obtenerStockDisponible(String productoID);

  AsignacionDTO asignarStockANecesidad(NecesidadMaterialDTO necesidad);

}
