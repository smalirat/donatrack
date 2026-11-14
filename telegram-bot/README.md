# donatrack-bot

Bot de Telegram para interactuar con el sistema DonaTrack desde el celular.

Consume la API del modulo **Donadores y Entidades**. No se ejecuta como servicio en
Render: por consigna, alcanza con correrlo localmente (solo una instancia a la vez).

## Requisitos

- Java 21
- Maven

## Variables de entorno

| Variable              | Descripcion                                           | Obligatoria |
|-----------------------|--------------------------------------------------------|-------------|
| `BOT_TOKEN`           | Token del bot generado por @BotFather                 | Si          |
| `DONADORES_API_URL`   | URL base de la API de Donadores y Entidades           | No          |
| `DONACIONES_API_URL`  | URL base de la API de Donaciones                      | No          |
| `LOGISTICA_API_URL`   | URL base de la API de Logistica                       | No          |
| `INCENTIVOS_API_URL`  | URL base de la API de Incentivos                      | No          |

`BOT_TOKEN` no tiene valor por defecto: si falta, el bot no arranca y tira un
error. Las 4 URLs si tienen un default hardcodeado en
`DonaTrackBotApplication.java` apuntando a los deploys reales de cada modulo en
Render, asi que solo hace falta setearlas si queres apuntar a otro ambiente
(por ejemplo, un servicio corriendo en local).

## Correrlo localmente

1. Copiar `.env.example` a `.env` y completar al menos `BOT_TOKEN` (las 4
   URLs son opcionales, ver seccion anterior) (ese archivo esta en
   `.gitignore`, nunca se sube).
2. Ejecutar:

```bash
mvn compile exec:java
```

(o ejecutar `DonaTrackBotApplication` directamente desde la IDE; tambien
funciona seteando las variables como variables de entorno reales en vez de usar
`.env`, esas toman prioridad si estan presentes)

## Comandos

El bot arranca preguntando que tipo de usuario es (`/donador` o `/admin`) y a partir
de ahi despliega los comandos disponibles para cada rol. Los comandos con varios
datos usan `|` como separador de campos (para poder incluir espacios en nombres,
domicilios, etc. sin ambiguedad).

**Usuarios Donador:**
- `/registrarme nombre|apellido|email|edad|domicilio|nroDocumento`
- `/misestadisticas <id>`
- `/verdonador <id>`
- `/verdonadores`

**Usuarios Admin:**
- `/crearentidad razonSocial|domicilio|telefono|correo`
- `/editarentidad <id>` — **pendiente**, no hay endpoint documentado para editar
  una entidad en la API de Entrega 3
- `/verentidad <id>`
- `/verentidades`
- `/crearnecesidad entidadID|descripcion|productoSolicitadoID|cantidadObjetivo|tipo`
  (tipo: `EXTRAORDINARIA` o `RECURRENTE`)
- `/borrarnecesidad <id>` — **pendiente**, no hay endpoint documentado
- `/modificarnecesidad <id>` — **pendiente**, no hay endpoint documentado
- `/vernecesidad <productoSolicitadoID>` — lista las necesidades insatisfechas
  de ese producto (`GET /necesidades?productoSolicitadoID=...`)

## Contrato verificado contra la API real

Los endpoints se probaron contra el deploy real de Entrega 3
(`https://donadores-y-entidades-0uv4.onrender.com`) antes de darlos por
terminados. Formas de body confirmadas:

- `POST /donadores`: `{nombre, apellido, email, edad, domicilio, nroDocumento}`.
  El servidor devuelve ademas `id`, `estado` (default `VERIFICADO`) y
  `categoria` (default `Ocasional`).
- `POST /entidades`: `{razonSocial, domicilio, telefono, correo}`. **No** tiene
  `nombre` ni `descripcion` (a diferencia de lo que sugeria el diagrama de
  dominio de Entrega 3 en la documentacion grupal).
- `GET /entidades/{id}` funciona para buscar una entidad puntual.
- `POST /necesidades`: `{entidadID, descripcion, productoSolicitadoID, cantidadObjetivo, tipo}`.
  El servidor agrega `id` y `nivelDeUrgencia` (queda `null` si no se envia).
- `GET /necesidades?productoSolicitadoID=X` filtra correctamente.
- `GET /donadores/{id}/estadisticas` responde (probado con 400 esperado para
  un donador sin insignias/mision asignada todavia).

Nota: el servicio esta en el plan free de Render, duerme tras inactividad y el
primer request puede tardar ~50-60s en responder (cold start). Si el equipo
decide usar UptimeRobot antes de la entrega presencial, esto deja de ser un
problema.

Si el companero a cargo de Donadores y Entidades cambia alguno de estos
contratos mas adelante, hay que ajustar `DonadoresApiClient.java` (los metodos
estan separados uno por endpoint, el cambio deberia quedar localizado).
