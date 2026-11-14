    -- 1. Cargar Depósitos Variados (IDs numéricos autogenerados por JPA)
    INSERT INTO depositos (id, capacidad_maxima, direccion, nombre, tipo_algoritmo) VALUES
        (1, 5000, 'Av. Corrientes 1500, CABA', 'Depósito Central Obelisco', 'PRIORIDAD_POR_SCORE'),                                                                                    (2, 1500, 'Cabildo 2000, CABA', 'Depósito Belgrano Norte', 'SUB_ATENDIDOS'),
        (3, 2000, 'Av. Caseros 3000, CABA', 'Depósito Parque Patricios', 'SUB_ATENDIDOS'),
        (4, 1000, 'Alicia Moreau de Justo 500, CABA', 'Depósito Puerto Madero', 'PRIORIDAD_POR_SCORE'),
        (5, 3000, 'Av. Rivadavia 6500, CABA', 'Depósito Flores', 'SUB_ATENDIDOS')
    ON CONFLICT (id) DO NOTHING;

    -- 2. Cargar Paquetes (Relacionados con los IDs numéricos de los depósitos)
    INSERT INTO paquetes (id, cantidad, donacion_id, producto_id, deposito_id) VALUES
        (1, 50, 'DON-1001', 'PROD-ALIMENTO-01', 1),
        (2, 150, 'DON-1002', 'PROD-AGUA-01', 1),
        (3, 20, 'DON-1003', 'PROD-COLCHON-01', 1),
        (4, 5, 'DON-2001', 'PROD-ROPA-01', 2),
        (5, 30, 'DON-2002', 'PROD-MEDICAMENTO-01', 2),
        (6, 100, 'DON-3001', 'PROD-ALIMENTO-02', 3),
        (7, 10, 'DON-3002', 'PROD-FRAZADA-01', 3),
        (8, 200, 'DON-4001', 'PROD-MATERIAL-CONSTRUCCION', 4),
        (9, 45, 'DON-5001', 'PROD-MUEBLE-01', 5)
     ON CONFLICT (id) DO NOTHING;

    -- 3. Cargar Asignaciones (Simulando paquetes que ya hicieron match)
    INSERT INTO asignaciones (id, estado, fecha, necesidad_id, paquete_id) VALUES
        (1, 'ASIGNADA', CURRENT_TIMESTAMP, 'NEC-COMEDOR-01', 1),
        (2, 'COMPLETADA', '2026-05-15 10:30:00', 'NEC-HOSPITAL-01', 5),
        (3, 'ASIGNADA', CURRENT_TIMESTAMP, 'NEC-REFUGIO-01', 7)
     ON CONFLICT (id) DO NOTHING;

    SELECT setval(pg_get_serial_sequence('depositos', 'id'), coalesce(max(id)+1, 1), false) FROM depositos;
    SELECT setval(pg_get_serial_sequence('paquetes', 'id'), coalesce(max(id)+1, 1), false) FROM paquetes;
    SELECT setval(pg_get_serial_sequence('asignaciones', 'id'), coalesce(max(id)+1, 1), false) FROM asignaciones;