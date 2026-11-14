package ar.edu.utn.dds.k3003.eventos;

import ar.edu.utn.dds.k3003.catedra.dtos.logistica.TipoAlgoritmoEnum;
import ar.edu.utn.dds.k3003.model.Deposito;

import java.util.List;
import java.util.Map;

// Datos del evento DEPOSITO_ACTUALIZADO: estado completo del depósito y su stock agrupado por producto.
public record DepositoActualizado(String id, String nombre, String direccion, Integer capacidadMaxima,
                                  TipoAlgoritmoEnum algoritmo, Integer stockTotal,
                                  List<StockProducto> stockPorProducto, boolean eliminado) {

  public record StockProducto(String productoId, Integer cantidad) {}

  public static DepositoActualizado de(Deposito deposito, boolean eliminado) {
    Map<String, Integer> stock = deposito.stockPorProducto();
    return new DepositoActualizado(
            String.valueOf(deposito.getId()),
            deposito.getNombre(),
            deposito.getDireccion(),
            deposito.getCapacidadMaxima(),
            deposito.getTipoAlgoritmo(),
            stock.values().stream().mapToInt(Integer::intValue).sum(),
            stock.entrySet().stream()
                    .map(entrada -> new StockProducto(entrada.getKey(), entrada.getValue()))
                    .toList(),
            eliminado);
  }
}
