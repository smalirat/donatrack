package ar.edu.utn.dds.k3003.model.mappers;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.*;
import ar.edu.utn.dds.k3003.model.*;

import java.util.stream.Collectors;

public class LogisticaMapper {

    public DepositoDTO toDepositoDTO(Deposito deposito) {
        return new DepositoDTO(
                deposito.getId() != null ? String.valueOf(deposito.getId()) : null,
                deposito.getTipoAlgoritmo(),
                deposito.getNombre(),
                deposito.getDireccion(),
                deposito.getCapacidadMaxima(),
                deposito.getStock().stream()
                        .map(this::toPaqueteDTO)
                        .collect(Collectors.toList()));
    }

    public Deposito toDeposito(DepositoDTO dto) {
        Deposito deposito = new Deposito(dto.nombre(), dto.direccion(), dto.capacidadMaxima());
        deposito.setTipoAlgoritmo(dto.algoritmo());
        return deposito;
    }

    public AsignacionDTO toAsignacionDTO(
            Asignacion asignacion) {

        return new AsignacionDTO(
                asignacion.getId() != null
                        ? String.valueOf(asignacion.getId())
                        : null,

                asignacion.getPaquetes().stream()
                        .map(Paquete::getId)
                        .map(String::valueOf)
                        .toList(),

                asignacion.getNecesidadID(),

                asignacion.getFecha(),

                EstadoAsginacionEnum.valueOf(
                        asignacion.getEstado().name()
                ),

                asignacion.getOrigen().name()
        );
    }

    public PaqueteDTO toPaqueteDTO(Paquete paquete) {
        return new PaqueteDTO(
                paquete.getId() != null ? String.valueOf(paquete.getId()) : null,
                paquete.getDonacionID(),
                paquete.getProducto().getId(),
                paquete.getCantidad());
    }

    public Paquete toPaquete(PaqueteDTO dto) {
        Paquete paquete = new Paquete();
        paquete.setDonacionID(dto.donacionID());
        paquete.setProducto(new Producto(dto.producto()));
        paquete.setCantidad(dto.cantidad());
        return paquete;
    }

    public Necesidad toNecesidad(NecesidadMaterialDTO dto) {
        return new Necesidad(
                dto.id(),
                dto.nivelDeUrgencia(),
                dto.cantidadObjetivo(),
                dto.tipo()
        );
    }
}
