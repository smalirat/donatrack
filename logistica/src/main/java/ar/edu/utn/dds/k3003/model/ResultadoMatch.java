package ar.edu.utn.dds.k3003.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ResultadoMatch {
    private Asignacion asignacion;
    private Paquete paqueteSobrante; // Es null si no sobra nada
 }