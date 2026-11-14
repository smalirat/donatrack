# DonaTrack - Servicio de Consultas

Lado de lectura del CQRS de la entrega 6. Escucha los eventos que publican los cuatro módulos,
mantiene el modelo de lectura en MongoDB y resuelve las consultas complejas solo contra Mongo.

Despliegue: https://servicio-consultas.onrender.com

## Cómo funciona

1. Los módulos publican en el exchange `donatrack.eventos` (RabbitMQ).
2. Este servicio declara la cola `consultas.eventos`, atada al exchange con `#`, y su DLQ `consultas.eventos.dlq`.
3. Cada evento se aplica con un upsert por id. Se descarta si es más viejo que lo guardado
   (hay una versión por módulo de origen), así los eventos repetidos o desordenados no rompen nada.
   Las bajas quedan como `eliminado: true`.

El formato de los eventos está en el contrato de eventos del grupo.

## Endpoints

Desde el gateway se usan con el prefijo `/api/consultas`.

| Endpoint | Consulta |
|---|---|
| `GET /donadores/{id}/ficha` | 1. Ficha integral de un donador |
| `GET /necesidades-urgentes?urgenciaMinima=&tipo=&categoriaId=&subcategoriaId=` | 2. Necesidades urgentes sin cubrir |
| `GET /depositos/estado?depositoId=&desde=AAAA-MM-DD&hasta=AAAA-MM-DD` | 3. Estado de los depósitos |
| `GET /calidad?top=10` | 4. Calidad de lo donado |
| `GET /admin/resumen` | Cantidad de documentos por colección |
| `DELETE /admin/modelo` | Vacía el modelo de lectura, para arrancar de cero junto con las bases de los módulos |

Swagger: `/swagger-ui/index.html`.

## Variables de entorno

| Variable | Valor |
|---|---|
| `MONGODB_URI` | Cadena de conexión de MongoDB Atlas |
| `MONGODB_DATABASE` | Base de datos (por defecto `donatrack`) |
| `SPRING_RABBITMQ_HOST`, `SPRING_RABBITMQ_PORT`, `SPRING_RABBITMQ_USERNAME`, `SPRING_RABBITMQ_PASSWORD`, `SPRING_RABBITMQ_VIRTUAL_HOST` | Los mismos valores de CloudAMQP que usa Logística |

## Tests

`mvn test` levanta un MongoDB embebido (no hace falta Docker ni Atlas). La primera vez descarga el binario de MongoDB.
