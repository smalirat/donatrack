import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from "@modelcontextprotocol/sdk/types.js";





// Con GATEWAY_URL definida, todo pasa por el API Gateway (cada módulo bajo /api/<modulo>).
// Sin ella, se llama a cada servicio directo (útil para probar antes de tener el gateway).
const GATEWAY_URL = process.env.GATEWAY_URL?.replace(/\/+$/, "");

const URLS = GATEWAY_URL
  ? {
      LOGISTICA: `${GATEWAY_URL}/api/logistica`,
      DONACIONES: `${GATEWAY_URL}/api/donaciones`,
      DONADORES_ENTIDADES: `${GATEWAY_URL}/api/donadores-entidades`,
      INCENTIVOS: `${GATEWAY_URL}/api/incentivos`,
      CONSULTAS: `${GATEWAY_URL}/api/consultas`,
    }
  : {
      LOGISTICA: "https://logistica-v3xw.onrender.com",
      DONACIONES: "https://marco-maggio-springboot-api-entrega2-dsi.onrender.com",
      DONADORES_ENTIDADES: "https://donadores-y-entidades-0uv4.onrender.com",
      INCENTIVOS: "https://incentivos-nhyc.onrender.com",
      CONSULTAS: process.env.CONSULTAS_URL ?? "http://localhost:8080",
    };

const server = new Server(
  {
    name: "donatrack-mcp-server",
    version: "1.0.0",
  },
  {
    capabilities: {
      tools: {},
    },
  }
);


// ==========================================
// INCENTIVOS (insignias, misiones y progreso de donadores)
// Las reglas de negocio viven en el modulo Incentivos: estas tools solo llaman a su API.
// ==========================================

const CATEGORIAS_DONADOR = ["OCASIONAL", "COLABORADOR", "TRANSFORMADOR", "SALVADOR", "REVOLUCIONARIO"];
const TIPOS_MISION = ["COMPLETITUD", "DONACIONES_EXITOSAS", "DONACIONES_ASCENDENTES", "REVOLUCION_DONADORA"];

const incentivosTools = [
  {
    name: "listar_insignias",
    description: "Lista todas las insignias que existen en el sistema de incentivos (id, nombre y descripción).",
    inputSchema: { type: "object", properties: {} },
  },
  {
    name: "crear_insignia",
    description: "Crea una nueva insignia. Devuelve la insignia creada con su id, que luego se usa para crear misiones.",
    inputSchema: {
      type: "object",
      properties: {
        nombre: { type: "string", description: "Nombre de la insignia" },
        descripcion: { type: "string", description: "Descripción de la insignia" },
      },
      required: ["nombre", "descripcion"],
    },
  },
  {
    name: "eliminar_insignia",
    description: "Elimina una insignia por su id.",
    inputSchema: {
      type: "object",
      properties: { insigniaId: { type: "string", description: "ID de la insignia" } },
      required: ["insigniaId"],
    },
  },
  {
    name: "listar_misiones",
    description:
      "Lista todas las misiones. Cada misión tiene un tipo, la insignia que otorga al completarse " +
      "y la categoría del donador al empezarla (categoriaInicio) y al completarla (categoriaFin).",
    inputSchema: { type: "object", properties: {} },
  },
  {
    name: "crear_mision",
    description:
      "Crea una misión que, al completarse, otorga una insignia y sube al donador de categoriaInicio a categoriaFin. " +
      "Tipos: COMPLETITUD (donar productos de al menos 3 categorías distintas), " +
      "DONACIONES_EXITOSAS (20 donaciones aceptadas), DONACIONES_ASCENDENTES (5 donaciones seguidas con cantidad creciente), " +
      "REVOLUCION_DONADORA (más de 10 donaciones de más de 50 unidades). La insignia debe existir previamente.",
    inputSchema: {
      type: "object",
      properties: {
        nombre: { type: "string", description: "Nombre de la misión" },
        insigniaId: { type: "string", description: "ID de la insignia que otorga la misión" },
        categoriaInicio: { type: "string", enum: CATEGORIAS_DONADOR },
        categoriaFin: { type: "string", enum: CATEGORIAS_DONADOR },
        tipo: { type: "string", enum: TIPOS_MISION },
      },
      required: ["nombre", "insigniaId", "categoriaInicio", "categoriaFin", "tipo"],
    },
  },
  {
    name: "eliminar_mision",
    description: "Elimina una misión por su id.",
    inputSchema: {
      type: "object",
      properties: { misionId: { type: "string", description: "ID de la misión" } },
      required: ["misionId"],
    },
  },
  {
    name: "asignar_mision_a_donador",
    description:
      "Asigna una misión a un donador (reemplaza la que tuviera). El donador debe existir en el módulo Donadores. " +
      "Después se puede usar procesar_donador_incentivos para evaluar su progreso.",
    inputSchema: {
      type: "object",
      properties: {
        donadorId: { type: "string", description: "ID del donador" },
        misionId: { type: "string", description: "ID de la misión" },
      },
      required: ["donadorId", "misionId"],
    },
  },
  {
    name: "otorgar_insignia_a_donador",
    description:
      "Otorga manualmente una insignia a un donador, sin evaluar misiones. " +
      "Para otorgarla por cumplir una misión usar procesar_donador_incentivos.",
    inputSchema: {
      type: "object",
      properties: {
        donadorId: { type: "string", description: "ID del donador" },
        insigniaId: { type: "string", description: "ID de la insignia" },
      },
      required: ["donadorId", "insigniaId"],
    },
  },
  {
    name: "consultar_incentivos_donador",
    description: "Muestra la misión en curso y las insignias obtenidas por un donador.",
    inputSchema: {
      type: "object",
      properties: { donadorId: { type: "string", description: "ID del donador" } },
      required: ["donadorId"],
    },
  },
  {
    name: "consultar_insignia",
    description: "Obtiene una insignia por su id.",
    inputSchema: {
      type: "object",
      properties: { insigniaId: { type: "string", description: "ID de la insignia" } },
      required: ["insigniaId"],
    },
  },
  {
    name: "consultar_mision",
    description: "Obtiene una misión por su id (tipo, insignia que otorga y categorías de inicio y fin).",
    inputSchema: {
      type: "object",
      properties: { misionId: { type: "string", description: "ID de la misión" } },
      required: ["misionId"],
    },
  },
];

const nombresToolsIncentivos = new Set(incentivosTools.map((t) => t.name));

function enviarJson(url: string, method: string, body?: unknown) {
  return fetch(url, {
    method,
    headers: { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

async function ejecutarToolIncentivos(name: string, args: Record<string, any>) {
  const base = URLS.INCENTIVOS;
  const id = (valor: unknown) => encodeURIComponent(String(valor));

  switch (name) {
    case "listar_insignias":
      return formatResponse(await fetch(`${base}/insignias`));

    case "crear_insignia":
      return formatResponse(
        await enviarJson(`${base}/insignias`, "POST", { nombre: args.nombre, descripcion: args.descripcion })
      );

    case "eliminar_insignia":
      return formatResponse(await enviarJson(`${base}/insignias/${id(args.insigniaId)}`, "DELETE"));

    case "listar_misiones":
      return formatResponse(await fetch(`${base}/misiones`));

    case "crear_mision":
      return formatResponse(
        await enviarJson(`${base}/misiones`, "POST", {
          nombre: args.nombre,
          insigniaID: String(args.insigniaId),
          categoriaInicio: args.categoriaInicio,
          categoriaFin: args.categoriaFin,
          tipo: args.tipo,
        })
      );

    case "eliminar_mision":
      return formatResponse(await enviarJson(`${base}/misiones/${id(args.misionId)}`, "DELETE"));

    case "consultar_insignia":
      return formatResponse(await fetch(`${base}/insignias/${id(args.insigniaId)}`));

    case "consultar_mision":
      return formatResponse(await fetch(`${base}/misiones/${id(args.misionId)}`));

    case "asignar_mision_a_donador":
      return formatResponse(
        await enviarJson(`${base}/donadores/${id(args.donadorId)}/mision`, "POST", { misionID: String(args.misionId) })
      );

    case "otorgar_insignia_a_donador":
      return formatResponse(
        await enviarJson(`${base}/donadores/${id(args.donadorId)}/insignias`, "POST", {
          insigniaID: String(args.insigniaId),
        })
      );

    case "consultar_incentivos_donador": {
      const [resInsignias, resMision] = await Promise.all([
        fetch(`${base}/donadores/${id(args.donadorId)}/insignias`),
        fetch(`${base}/donadores/${id(args.donadorId)}/mision`),
      ]);
      // 404: Incentivos todavia no registro nada para ese donador
      if (resInsignias.status === 404) {
        return {
          content: [{ type: "text", text: `El donador ${args.donadorId} no tiene misión asignada ni insignias en Incentivos.` }],
        };
      }
      if (!resInsignias.ok) return formatResponse(resInsignias);
      // 204: el donador no tiene mision en curso
      if (!resMision.ok && resMision.status !== 404) return formatResponse(resMision);
      const insignias = await resInsignias.json();
      const misionActual = resMision.status === 200 ? await resMision.json() : null;
      return {
        content: [{ type: "text", text: JSON.stringify({ donadorId: args.donadorId, misionActual, insignias }, null, 2) }],
      };
    }

    default:
      throw new Error(`Tool de incentivos no encontrada: ${name}`);
  }
}

const idPath = (valor: unknown) => encodeURIComponent(String(valor));
const soloId = (descripcion: string) => ({
  type: "object",
  properties: { id: { type: "string", description: descripcion } },
  required: ["id"],
});

// ==========================================
// DONADORES Y ENTIDADES (donadores, entidades benéficas y necesidades materiales)
// ==========================================

const ESTADOS_DONADOR = ["VERIFICADO", "SOSPECHOSO", "BANEADO"];
const TIPOS_NECESIDAD = ["EXTRAORDINARIA", "RECURRENTE"];

const propiedadesEntidad = {
  razonSocial: { type: "string" },
  domicilio: { type: "string" },
  telefono: { type: "string" },
  correo: { type: "string" },
};

const propiedadesNecesidad = {
  entidadId: { type: "string", description: "ID de la entidad benéfica" },
  nivelDeUrgencia: { type: "number", description: "Del 1 al 10" },
  descripcion: { type: "string" },
  productoSolicitadoId: { type: "string", description: "ID del producto (módulo Donaciones)" },
  cantidadObjetivo: { type: "number", description: "Cantidad que todavía falta cubrir" },
  tipo: { type: "string", enum: TIPOS_NECESIDAD },
};

const donadoresTools = [
  {
    name: "registrar_donador",
    description: "Registra un nuevo donador. Nace en estado VERIFICADO y categoría OCASIONAL. Devuelve el donador con su id.",
    inputSchema: {
      type: "object",
      properties: {
        nombre: { type: "string" },
        apellido: { type: "string" },
        edad: { type: "number" },
        email: { type: "string" },
        nroDocumento: { type: "string" },
        domicilio: { type: "string" },
      },
      required: ["nombre", "apellido", "edad", "email", "nroDocumento", "domicilio"],
    },
  },
  {
    name: "listar_donadores",
    description: "Lista todos los donadores con su estado y categoría.",
    inputSchema: { type: "object", properties: {} },
  },
  {
    name: "consultar_donador",
    description: "Obtiene los datos de un donador por su id.",
    inputSchema: soloId("ID del donador"),
  },
  {
    name: "cambiar_estado_donador",
    description:
      "Establece manualmente el estado de un donador. VERIFICADO dona sin restricciones, " +
      "SOSPECHOSO solo el 50% de las veces y BANEADO no puede donar.",
    inputSchema: {
      type: "object",
      properties: { id: { type: "string", description: "ID del donador" }, estado: { type: "string", enum: ESTADOS_DONADOR } },
      required: ["id", "estado"],
    },
  },
  {
    name: "cambiar_categoria_donador",
    description: "Establece manualmente la categoría de un donador (normalmente la cambia Incentivos al completar misiones).",
    inputSchema: {
      type: "object",
      properties: { id: { type: "string", description: "ID del donador" }, categoria: { type: "string", enum: CATEGORIAS_DONADOR } },
      required: ["id", "categoria"],
    },
  },
  {
    name: "consultar_si_donador_puede_donar",
    description: "Indica si un donador puede donar según su estado (para SOSPECHOSO el resultado es aleatorio).",
    inputSchema: soloId("ID del donador"),
  },
  {
    name: "listar_quejas_donador",
    description: "Lista el historial de quejas recibidas por las donaciones de un donador.",
    inputSchema: soloId("ID del donador"),
  },
  {
    name: "registrar_entidad",
    description: "Registra una nueva entidad benéfica. Devuelve la entidad con su id.",
    inputSchema: { type: "object", properties: propiedadesEntidad, required: Object.keys(propiedadesEntidad) },
  },
  {
    name: "listar_entidades",
    description: "Lista todas las entidades benéficas.",
    inputSchema: { type: "object", properties: {} },
  },
  {
    name: "consultar_entidad",
    description: "Obtiene una entidad benéfica por su id.",
    inputSchema: soloId("ID de la entidad"),
  },
  {
    name: "modificar_entidad",
    description: "Modifica los datos de una entidad benéfica (se envían todos los campos).",
    inputSchema: {
      type: "object",
      properties: { id: { type: "string", description: "ID de la entidad" }, ...propiedadesEntidad },
      required: ["id", ...Object.keys(propiedadesEntidad)],
    },
  },
  {
    name: "listar_necesidades_insatisfechas",
    description:
      "Lista las necesidades materiales que todavía no se cubrieron. " +
      "Opcionalmente filtra por el producto solicitado.",
    inputSchema: {
      type: "object",
      properties: { productoSolicitadoId: { type: "string", description: "ID del producto (opcional)" } },
    },
  },
  {
    name: "consultar_necesidad",
    description: "Obtiene una necesidad material por su id.",
    inputSchema: soloId("ID de la necesidad"),
  },
  {
    name: "modificar_necesidad",
    description: "Modifica una necesidad material (se envían todos los campos).",
    inputSchema: {
      type: "object",
      properties: { id: { type: "string", description: "ID de la necesidad" }, ...propiedadesNecesidad },
      required: ["id", ...Object.keys(propiedadesNecesidad)],
    },
  },
  {
    name: "eliminar_necesidad",
    description: "Elimina una necesidad material por su id.",
    inputSchema: soloId("ID de la necesidad"),
  },
];

function cuerpoNecesidad(args: Record<string, any>) {
  return {
    entidadID: String(args.entidadId),
    nivelDeUrgencia: Number(args.nivelDeUrgencia),
    descripcion: args.descripcion,
    productoSolicitadoID: String(args.productoSolicitadoId),
    cantidadObjetivo: Number(args.cantidadObjetivo),
    tipo: args.tipo,
  };
}

function cuerpoEntidad(args: Record<string, any>) {
  return { razonSocial: args.razonSocial, domicilio: args.domicilio, telefono: args.telefono, correo: args.correo };
}

async function ejecutarToolDonadores(name: string, args: Record<string, any>) {
  const base = URLS.DONADORES_ENTIDADES;

  switch (name) {
    case "registrar_donador":
      return formatResponse(
        await enviarJson(`${base}/donadores`, "POST", {
          nombre: args.nombre,
          apellido: args.apellido,
          edad: Number(args.edad),
          email: args.email,
          nroDocumento: String(args.nroDocumento),
          domicilio: args.domicilio,
        })
      );

    case "listar_donadores":
      return formatResponse(await fetch(`${base}/donadores`));

    case "consultar_donador":
      return formatResponse(await fetch(`${base}/donadores/${idPath(args.id)}`));

    case "cambiar_estado_donador":
      return formatResponse(await enviarJson(`${base}/donadores/${idPath(args.id)}/estado`, "PATCH", { estado: args.estado }));

    case "cambiar_categoria_donador":
      return formatResponse(
        await enviarJson(`${base}/donadores/${idPath(args.id)}/categoria`, "PATCH", { categoria: args.categoria })
      );

    case "consultar_si_donador_puede_donar":
      return formatResponse(await fetch(`${base}/donadores/${idPath(args.id)}/puede-donar`));

    case "listar_quejas_donador":
      return formatResponse(await fetch(`${base}/donadores/${idPath(args.id)}/quejas`));

    case "registrar_entidad":
      return formatResponse(await enviarJson(`${base}/entidades`, "POST", cuerpoEntidad(args)));

    case "listar_entidades":
      return formatResponse(await fetch(`${base}/entidades`));

    case "consultar_entidad":
      return formatResponse(await fetch(`${base}/entidades/${idPath(args.id)}`));

    case "modificar_entidad":
      return formatResponse(await enviarJson(`${base}/entidades/${idPath(args.id)}`, "PUT", cuerpoEntidad(args)));

    case "listar_necesidades_insatisfechas": {
      const filtro = args.productoSolicitadoId ? `?productoSolicitadoID=${idPath(args.productoSolicitadoId)}` : "";
      return formatResponse(await fetch(`${base}/necesidades${filtro}`));
    }

    case "consultar_necesidad":
      return formatResponse(await fetch(`${base}/necesidades/${idPath(args.id)}`));

    case "modificar_necesidad":
      return formatResponse(await enviarJson(`${base}/necesidades/${idPath(args.id)}`, "PUT", cuerpoNecesidad(args)));

    case "eliminar_necesidad":
      return formatResponse(await enviarJson(`${base}/necesidades/${idPath(args.id)}`, "DELETE"));

    default:
      throw new Error(`Tool de donadores y entidades no encontrada: ${name}`);
  }
}

// ==========================================
// DONACIONES (donaciones, productos, categorías e identificadores)
// ==========================================

const ESTADOS_DONACION = ["INGRESADA", "ACEPTADA", "CONQUEJA"];
const TIPOS_IDENTIFICADOR = ["QR", "CODIGODEBARRAS"];

const propiedadesProducto = {
  nombre: { type: "string" },
  descripcion: { type: "string" },
  categoriaId: { type: "string", description: "ID de una categoría existente" },
  identificadorId: { type: "string", description: "ID de un identificador existente" },
};

const donacionesTools = [
  {
    name: "listar_donaciones",
    description: "Lista todas las donaciones con su estado (INGRESADA, ACEPTADA o CONQUEJA).",
    inputSchema: { type: "object", properties: {} },
  },
  {
    name: "consultar_donacion",
    description: "Obtiene una donación por su id.",
    inputSchema: soloId("ID de la donación"),
  },
  {
    name: "buscar_donaciones_de_donador",
    description: "Lista las donaciones de un donador realizadas desde una fecha hasta hoy.",
    inputSchema: {
      type: "object",
      properties: {
        donadorId: { type: "string" },
        fechaInicio: { type: "string", description: "Fecha en formato AAAA-MM-DD" },
      },
      required: ["donadorId", "fechaInicio"],
    },
  },
  {
    name: "cambiar_estado_donacion",
    description:
      "Cambia manualmente el estado de una donación. Solo se permite INGRESADA → ACEPTADA y ACEPTADA → CONQUEJA. " +
      "Normalmente lo hacen reportar_entrega_paquete y registrar_queja_donacion.",
    inputSchema: {
      type: "object",
      properties: { id: { type: "string", description: "ID de la donación" }, estado: { type: "string", enum: ESTADOS_DONACION } },
      required: ["id", "estado"],
    },
  },
  {
    name: "eliminar_donacion",
    description: "Elimina una donación por su id.",
    inputSchema: soloId("ID de la donación"),
  },
  {
    name: "crear_producto",
    description:
      "Crea un producto. La categoría y el identificador deben existir. Validación según el identificador: " +
      "con CODIGODEBARRAS la descripción debe tener al menos 3 palabras; con QR el nombre debe tener una cantidad par de letras.",
    inputSchema: { type: "object", properties: propiedadesProducto, required: Object.keys(propiedadesProducto) },
  },
  {
    name: "listar_productos",
    description: "Lista todos los productos.",
    inputSchema: { type: "object", properties: {} },
  },
  {
    name: "consultar_producto",
    description: "Obtiene un producto por su id.",
    inputSchema: soloId("ID del producto"),
  },
  {
    name: "modificar_producto",
    description: "Modifica un producto (se envían todos los campos).",
    inputSchema: {
      type: "object",
      properties: { id: { type: "string", description: "ID del producto" }, ...propiedadesProducto },
      required: ["id", ...Object.keys(propiedadesProducto)],
    },
  },
  {
    name: "eliminar_producto",
    description: "Elimina un producto por su id.",
    inputSchema: soloId("ID del producto"),
  },
  {
    name: "crear_categoria",
    description: "Crea una categoría de producto (ej. Alimentos) con su subcategoría (ej. fideos).",
    inputSchema: {
      type: "object",
      properties: {
        nombre: { type: "string" },
        descripcion: { type: "string" },
        subcategoriaId: { type: "string", description: "ID o nombre de la subcategoría" },
      },
      required: ["nombre", "descripcion", "subcategoriaId"],
    },
  },
  {
    name: "listar_categorias",
    description: "Lista todas las categorías de producto.",
    inputSchema: { type: "object", properties: {} },
  },
  {
    name: "consultar_categoria",
    description: "Obtiene una categoría por su id.",
    inputSchema: soloId("ID de la categoría"),
  },
  {
    name: "eliminar_categoria",
    description: "Elimina una categoría por su id.",
    inputSchema: soloId("ID de la categoría"),
  },
  {
    name: "crear_identificador",
    description: "Crea un identificador de producto (código QR o código de barras).",
    inputSchema: {
      type: "object",
      properties: { tipo: { type: "string", enum: TIPOS_IDENTIFICADOR }, descripcion: { type: "string" } },
      required: ["tipo", "descripcion"],
    },
  },
  {
    name: "listar_identificadores",
    description: "Lista todos los identificadores de producto.",
    inputSchema: { type: "object", properties: {} },
  },
  {
    name: "consultar_identificador",
    description: "Obtiene un identificador por su id.",
    inputSchema: soloId("ID del identificador"),
  },
  {
    name: "eliminar_identificador",
    description: "Elimina un identificador por su id.",
    inputSchema: soloId("ID del identificador"),
  },
];

function cuerpoProducto(args: Record<string, any>) {
  return {
    nombre: args.nombre,
    descripcion: args.descripcion,
    categoriaID: String(args.categoriaId),
    identificadorID: String(args.identificadorId),
  };
}

async function ejecutarToolDonaciones(name: string, args: Record<string, any>) {
  const base = URLS.DONACIONES;

  switch (name) {
    case "listar_donaciones":
      return formatResponse(await fetch(`${base}/donaciones`));

    case "consultar_donacion":
      return formatResponse(await fetch(`${base}/donaciones/${idPath(args.id)}`));

    case "buscar_donaciones_de_donador": {
      const query = `donadorID=${idPath(args.donadorId)}&fechaInicio=${idPath(args.fechaInicio)}`;
      return formatResponse(await fetch(`${base}/donaciones/search?${query}`));
    }

    case "cambiar_estado_donacion":
      return formatResponse(await enviarJson(`${base}/donaciones/${idPath(args.id)}/estado`, "PATCH", { estado: args.estado }));

    case "eliminar_donacion":
      return formatResponse(await enviarJson(`${base}/donaciones/${idPath(args.id)}`, "DELETE"));

    case "crear_producto":
      return formatResponse(await enviarJson(`${base}/productos`, "POST", cuerpoProducto(args)));

    case "listar_productos":
      return formatResponse(await fetch(`${base}/productos`));

    case "consultar_producto":
      return formatResponse(await fetch(`${base}/productos/${idPath(args.id)}`));

    case "modificar_producto":
      return formatResponse(await enviarJson(`${base}/productos/${idPath(args.id)}`, "PUT", cuerpoProducto(args)));

    case "eliminar_producto":
      return formatResponse(await enviarJson(`${base}/productos/${idPath(args.id)}`, "DELETE"));

    case "crear_categoria":
      return formatResponse(
        await enviarJson(`${base}/categorias`, "POST", {
          nombre: args.nombre,
          descripcion: args.descripcion,
          subcategoriaID: String(args.subcategoriaId),
        })
      );

    case "listar_categorias":
      return formatResponse(await fetch(`${base}/categorias`));

    case "consultar_categoria":
      return formatResponse(await fetch(`${base}/categorias/${idPath(args.id)}`));

    case "eliminar_categoria":
      return formatResponse(await enviarJson(`${base}/categorias/${idPath(args.id)}`, "DELETE"));

    case "crear_identificador":
      return formatResponse(
        await enviarJson(`${base}/identificadores`, "POST", { tipo: args.tipo, descripcion: args.descripcion })
      );

    case "listar_identificadores":
      return formatResponse(await fetch(`${base}/identificadores`));

    case "consultar_identificador":
      return formatResponse(await fetch(`${base}/identificadores/${idPath(args.id)}`));

    case "eliminar_identificador":
      return formatResponse(await enviarJson(`${base}/identificadores/${idPath(args.id)}`, "DELETE"));

    default:
      throw new Error(`Tool de donaciones no encontrada: ${name}`);
  }
}

// ==========================================
// LOGÍSTICA (depósitos, stock, matchmaking y asignaciones)
// ==========================================

const ALGORITMOS_MATCHMAKING = ["SUB_ATENDIDOS", "PRIORIDAD_POR_SCORE"];

const logisticaTools = [
  {
    name: "crear_deposito",
    description:
      "Crea un depósito con su capacidad máxima (en unidades) y el algoritmo de matchmaking con el que asigna donaciones: " +
      "SUB_ATENDIDOS (la necesidad a la que más le falta) o PRIORIDAD_POR_SCORE (urgencia sobre cobertura).",
    inputSchema: {
      type: "object",
      properties: {
        nombre: { type: "string" },
        direccion: { type: "string" },
        capacidadMaxima: { type: "number" },
        algoritmo: { type: "string", enum: ALGORITMOS_MATCHMAKING },
      },
      required: ["nombre", "direccion", "capacidadMaxima", "algoritmo"],
    },
  },
  {
    name: "listar_depositos",
    description: "Lista todos los depósitos con su algoritmo, capacidad máxima y los paquetes que tienen en stock.",
    inputSchema: { type: "object", properties: {} },
  },
  {
    name: "consultar_deposito",
    description: "Obtiene un depósito por su id, con su algoritmo, capacidad máxima y paquetes en stock.",
    inputSchema: soloId("ID del depósito"),
  },
  {
    name: "eliminar_deposito",
    description: "Elimina un depósito y su stock.",
    inputSchema: soloId("ID del depósito"),
  },
  {
    name: "configurar_algoritmo_deposito",
    description: "Cambia el algoritmo de matchmaking que usa un depósito para asignar las donaciones que recibe.",
    inputSchema: {
      type: "object",
      properties: { id: { type: "string", description: "ID del depósito" }, algoritmo: { type: "string", enum: ALGORITMOS_MATCHMAKING } },
      required: ["id", "algoritmo"],
    },
  },
  {
    name: "consultar_stock_producto",
    description: "Devuelve las unidades disponibles en stock de un producto, sumando todos los depósitos.",
    inputSchema: {
      type: "object",
      properties: { productoId: { type: "string", description: "ID del producto" } },
      required: ["productoId"],
    },
  },
  {
    name: "listar_paquetes_en_stock",
    description: "Lista los paquetes que están en stock en los depósitos (sin asignar), con su donación de origen.",
    inputSchema: { type: "object", properties: {} },
  },
  {
    name: "listar_paquetes_entregados",
    description: "Lista los paquetes que ya fueron entregados a entidades benéficas.",
    inputSchema: { type: "object", properties: {} },
  },
  {
    name: "listar_asignaciones",
    description:
      "Lista todas las asignaciones: qué paquetes (y de qué donación) se asignaron a qué necesidad, su estado " +
      "(ASIGNADA o COMPLETADA) y su origen (MATCHMAKING al recibir una donación, SOLICITUD_DyE al registrar una necesidad). " +
      "Sirve para averiguar a qué necesidad se asignó una donación; la entidad sale de consultar_necesidad.",
    inputSchema: { type: "object", properties: {} },
  },
  {
    name: "consultar_asignacion",
    description: "Obtiene una asignación por su id.",
    inputSchema: soloId("ID de la asignación"),
  },
];

async function ejecutarToolLogistica(name: string, args: Record<string, any>) {
  const base = URLS.LOGISTICA;

  switch (name) {
    case "crear_deposito":
      return formatResponse(
        await enviarJson(`${base}/depositos`, "POST", {
          nombre: args.nombre,
          direccion: args.direccion,
          capacidadMaxima: Number(args.capacidadMaxima),
          algoritmo: args.algoritmo,
        })
      );

    case "listar_depositos":
      return formatResponse(await fetch(`${base}/depositos`));

    case "consultar_deposito":
      return formatResponse(await fetch(`${base}/depositos/${idPath(args.id)}`));

    case "eliminar_deposito":
      return formatResponse(await enviarJson(`${base}/depositos/${idPath(args.id)}`, "DELETE"));

    case "configurar_algoritmo_deposito":
      return formatResponse(
        await enviarJson(`${base}/depositos/${idPath(args.id)}/algoritmo`, "PATCH", { algoritmo: args.algoritmo })
      );

    case "consultar_stock_producto":
      return formatResponse(await fetch(`${base}/stock/${idPath(args.productoId)}`));

    case "listar_paquetes_en_stock":
      return formatResponse(await fetch(`${base}/admin/paquetes/stock`));

    case "listar_paquetes_entregados":
      return formatResponse(await fetch(`${base}/admin/paquetes/entregados`));

    case "listar_asignaciones":
      return formatResponse(await fetch(`${base}/admin/asignaciones`));

    case "consultar_asignacion":
      return formatResponse(await fetch(`${base}/asignaciones/${idPath(args.id)}`));

    default:
      throw new Error(`Tool de logística no encontrada: ${name}`);
  }
}

// ==========================================
// OPERACIONES INTERNAS Y DE ADMINISTRACION
// Endpoints que normalmente usan los módulos entre sí o que sirven para preparar la demo.
// ==========================================

const AVISO_IRREVERSIBLE = "IRREVERSIBLE: confirmar con el usuario antes de ejecutarla.";

const adminTools = [
  // ---- Donaciones
  {
    name: "limpiar_base_donaciones",
    description: `Borra todas las donaciones, productos e identificadores del módulo Donaciones. ${AVISO_IRREVERSIBLE}`,
    inputSchema: { type: "object", properties: {} },
  },
  // ---- Logística
  {
    name: "ingresar_donacion_en_deposito",
    description:
      "Ingresa una donación ya registrada al stock de un depósito (lo que hace Donaciones al registrar una donación). " +
      "Normalmente se usa realizar_donacion, que ya lo hace solo.",
    inputSchema: {
      type: "object",
      properties: {
        depositoId: { type: "string" },
        donacionId: { type: "string" },
        donadorId: { type: "string" },
        productoId: { type: "string" },
        cantidad: { type: "number" },
        descripcion: { type: "string" },
      },
      required: ["depositoId", "donacionId", "productoId", "cantidad"],
    },
  },
  {
    name: "ejecutar_matchmaking",
    description:
      "Ejecuta el algoritmo de matchmaking del depósito para un paquete contra una lista de necesidades y crea la asignación. " +
      "Las necesidades se pueden obtener con listar_necesidades_insatisfechas.",
    inputSchema: {
      type: "object",
      properties: {
        depositoId: { type: "string" },
        paquete: {
          type: "object",
          properties: {
            id: { type: "string" },
            donacionID: { type: "string" },
            producto: { type: "string", description: "ID del producto" },
            cantidad: { type: "number" },
          },
          required: ["id", "producto", "cantidad"],
        },
        necesidades: {
          type: "array",
          items: {
            type: "object",
            properties: {
              id: { type: "string" },
              entidadID: { type: "string" },
              nivelDeUrgencia: { type: "number" },
              descripcion: { type: "string" },
              cantidadObjetivo: { type: "number" },
              productoSolicitadoID: { type: "string" },
              tipo: { type: "string", enum: ["EXTRAORDINARIA", "RECURRENTE"] },
            },
          },
        },
      },
      required: ["depositoId", "paquete", "necesidades"],
    },
  },
  {
    name: "asignar_stock_a_necesidad",
    description:
      "Busca stock en los depósitos para cubrir una necesidad y crea la asignación (lo que hace Donadores y Entidades " +
      "al registrar una necesidad). Si no hay stock, no crea nada.",
    inputSchema: {
      type: "object",
      properties: {
        necesidadId: { type: "string" },
        entidadId: { type: "string" },
        nivelDeUrgencia: { type: "number" },
        descripcion: { type: "string" },
        cantidadObjetivo: { type: "number" },
        productoSolicitadoId: { type: "string" },
        tipo: { type: "string", enum: ["EXTRAORDINARIA", "RECURRENTE"] },
      },
      required: ["necesidadId", "entidadId", "cantidadObjetivo", "productoSolicitadoId"],
    },
  },
  {
    name: "cargar_paquete_en_deposito",
    description: "Admin: inserta un paquete directamente en el stock de un depósito, sin pasar por una donación. Útil para precargar datos.",
    inputSchema: {
      type: "object",
      properties: {
        depositoId: { type: "string" },
        donacionId: { type: "string", description: "Opcional, donación de origen" },
        productoId: { type: "string" },
        cantidad: { type: "number" },
      },
      required: ["depositoId", "productoId", "cantidad"],
    },
  },
  {
    name: "eliminar_paquete",
    description: "Admin: elimina un paquete de Logística por su id.",
    inputSchema: { type: "object", properties: { id: { type: "string" } }, required: ["id"] },
  },
  {
    name: "crear_asignacion_manual",
    description: "Admin: crea una asignación a mano (sin matchmaking). Útil para precargar datos de la demo.",
    inputSchema: {
      type: "object",
      properties: {
        necesidadId: { type: "string" },
        estado: { type: "string", enum: ["ASIGNADA", "COMPLETADA"] },
        origen: { type: "string", enum: ["MATCHMAKING", "SOLICITUD_DyE"] },
      },
      required: ["necesidadId", "estado", "origen"],
    },
  },
  {
    name: "modificar_asignacion",
    description:
      "Admin: reemplaza los datos de una asignación (necesidad, estado, origen). Reemplaza la asignación completa: " +
      "los paquetes que no se envíen quedan desasociados.",
    inputSchema: {
      type: "object",
      properties: {
        id: { type: "string" },
        necesidadId: { type: "string" },
        estado: { type: "string", enum: ["ASIGNADA", "COMPLETADA"] },
        origen: { type: "string", enum: ["MATCHMAKING", "SOLICITUD_DyE"] },
      },
      required: ["id", "necesidadId", "estado", "origen"],
    },
  },
  {
    name: "eliminar_asignacion",
    description: "Admin: elimina una asignación de Logística por su id.",
    inputSchema: { type: "object", properties: { id: { type: "string" } }, required: ["id"] },
  },
  {
    name: "limpiar_base_logistica",
    description: `Borra todos los depósitos, paquetes y asignaciones de Logística. ${AVISO_IRREVERSIBLE}`,
    inputSchema: { type: "object", properties: {} },
  },
  // ---- Donadores y Entidades
  {
    name: "registrar_queja_en_donador",
    description:
      "Registra una queja directamente sobre el donador en Donadores y Entidades (lo que hace Donaciones internamente). " +
      "Para el flujo normal usar registrar_queja_donacion, que además cambia la donación a CONQUEJA.",
    inputSchema: {
      type: "object",
      properties: {
        donadorId: { type: "string" },
        donacionId: { type: "string" },
        descripcion: { type: "string" },
      },
      required: ["donadorId", "donacionId", "descripcion"],
    },
  },
  {
    name: "satisfacer_necesidad",
    description:
      "Registra que se entregaron unidades para una necesidad (lo que hace Logística al reportar una entrega). " +
      "Normalmente se usa reportar_entrega_paquete.",
    inputSchema: {
      type: "object",
      properties: {
        necesidadId: { type: "string" },
        cantidad: { type: "number", description: "Unidades entregadas" },
      },
      required: ["necesidadId", "cantidad"],
    },
  },
  {
    name: "listar_persistidos_donadores_entidades",
    description: "Admin: lista lo que está guardado en la base de Donadores y Entidades (donadores, entidades o necesidades).",
    inputSchema: {
      type: "object",
      properties: { recurso: { type: "string", enum: ["donadores", "entidades", "necesidades"] } },
      required: ["recurso"],
    },
  },
  {
    name: "limpiar_base_donadores_entidades",
    description: `Borra todos los donadores, entidades, necesidades y quejas. ${AVISO_IRREVERSIBLE}`,
    inputSchema: { type: "object", properties: {} },
  },
  // ---- Incentivos
  {
    name: "limpiar_incentivos",
    description: `Borra datos del módulo Incentivos. alcance: "todo", "donadores" (progreso de donadores), "insignias" o "misiones". ${AVISO_IRREVERSIBLE}`,
    inputSchema: {
      type: "object",
      properties: { alcance: { type: "string", enum: ["todo", "donadores", "insignias", "misiones"] } },
      required: ["alcance"],
    },
  },
];

const RUTAS_LIMPIEZA_INCENTIVOS: Record<string, string> = {
  todo: "/reset",
  donadores: "/donadores/limpiar",
  insignias: "/insignias/limpiar",
  misiones: "/misiones/limpiar",
};

async function ejecutarToolAdmin(name: string, args: Record<string, any>) {
  switch (name) {
    case "limpiar_base_donaciones":
      return formatResponse(await enviarJson(`${URLS.DONACIONES}/admin/reset`, "DELETE"));

    case "ingresar_donacion_en_deposito":
      return formatResponse(
        await enviarJson(`${URLS.LOGISTICA}/depositos/${idPath(args.depositoId)}/donacion`, "POST", {
          id: String(args.donacionId),
          donadorID: args.donadorId === undefined ? null : String(args.donadorId),
          depositoID: String(args.depositoId),
          descripcion: args.descripcion ?? null,
          productoID: String(args.productoId),
          cantidad: Number(args.cantidad),
          estado: null,
        })
      );

    case "ejecutar_matchmaking":
      return formatResponse(
        await enviarJson(`${URLS.LOGISTICA}/depositos/${idPath(args.depositoId)}/matchmaking`, "POST", {
          paqueteDTO: args.paquete,
          necesidadesDTO: args.necesidades,
        })
      );

    case "asignar_stock_a_necesidad":
      return formatResponse(
        await enviarJson(`${URLS.LOGISTICA}/asignaciones/stock`, "POST", {
          id: String(args.necesidadId),
          entidadID: String(args.entidadId),
          nivelDeUrgencia: args.nivelDeUrgencia === undefined ? null : Number(args.nivelDeUrgencia),
          descripcion: args.descripcion ?? null,
          cantidadObjetivo: Number(args.cantidadObjetivo),
          productoSolicitadoID: String(args.productoSolicitadoId),
          tipo: args.tipo ?? null,
        })
      );

    case "cargar_paquete_en_deposito":
      return formatResponse(
        await enviarJson(`${URLS.LOGISTICA}/admin/depositos/${idPath(args.depositoId)}/paquetes`, "POST", {
          donacionID: args.donacionId === undefined ? null : String(args.donacionId),
          producto: { id: String(args.productoId) },
          cantidad: Number(args.cantidad),
        })
      );

    case "eliminar_paquete":
      return formatResponse(await enviarJson(`${URLS.LOGISTICA}/admin/paquetes/${idPath(args.id)}`, "DELETE"));

    case "crear_asignacion_manual":
      return formatResponse(
        await enviarJson(`${URLS.LOGISTICA}/admin/asignaciones`, "POST", {
          necesidadID: String(args.necesidadId),
          estado: args.estado,
          origen: args.origen,
        })
      );

    case "modificar_asignacion":
      return formatResponse(
        await enviarJson(`${URLS.LOGISTICA}/admin/asignaciones/${idPath(args.id)}`, "PUT", {
          necesidadID: String(args.necesidadId),
          estado: args.estado,
          origen: args.origen,
        })
      );

    case "eliminar_asignacion":
      return formatResponse(await enviarJson(`${URLS.LOGISTICA}/admin/asignaciones/${idPath(args.id)}`, "DELETE"));

    case "limpiar_base_logistica":
      return formatResponse(await enviarJson(`${URLS.LOGISTICA}/admin/limpiar`, "DELETE"));

    case "registrar_queja_en_donador":
      return formatResponse(
        await enviarJson(`${URLS.DONADORES_ENTIDADES}/donadores/${idPath(args.donadorId)}/quejas`, "POST", {
          donacionID: String(args.donacionId),
          descripcion: args.descripcion,
        })
      );

    case "satisfacer_necesidad":
      return formatResponse(
        await enviarJson(`${URLS.DONADORES_ENTIDADES}/necesidades/${idPath(args.necesidadId)}/satisfaccion`, "POST", {
          cantidad: Number(args.cantidad),
        })
      );

    case "listar_persistidos_donadores_entidades":
      return formatResponse(await fetch(`${URLS.DONADORES_ENTIDADES}/admin/${idPath(args.recurso)}`));

    case "limpiar_base_donadores_entidades":
      return formatResponse(await enviarJson(`${URLS.DONADORES_ENTIDADES}/admin/limpiar`, "DELETE"));

    case "limpiar_incentivos": {
      const ruta = RUTAS_LIMPIEZA_INCENTIVOS[args.alcance];
      if (!ruta) throw new Error(`Alcance inválido: ${args.alcance}`);
      return formatResponse(await enviarJson(`${URLS.INCENTIVOS}${ruta}`, "DELETE"));
    }

    default:
      throw new Error(`Tool de administración no encontrada: ${name}`);
  }
}

// ==========================================
// CONSULTAS (modelo de lectura en MongoDB, Servicio de Consultas)
// Los datos tienen consistencia eventual: un cambio recién hecho puede tardar unos segundos en verse.
// ==========================================

const consultasTools = [
  {
    name: "ficha_integral_donador",
    description:
      "Consulta 1: ficha completa de un donador desde el modelo de lectura: estado, categoría e historial, insignias, " +
      "misión en curso con su progreso, donaciones por estado, quejas recibidas y entidades beneficiadas con sus unidades.",
    inputSchema: { type: "object", properties: { donadorId: { type: "string" } }, required: ["donadorId"] },
  },
  {
    name: "necesidades_urgentes_sin_cubrir",
    description:
      "Consulta 2: necesidades insatisfechas ordenadas por urgencia, con la entidad, la cantidad faltante, el stock " +
      "de ese producto en cada depósito y las unidades asignadas todavía no entregadas. Todos los filtros son opcionales.",
    inputSchema: {
      type: "object",
      properties: {
        urgenciaMinima: { type: "number", description: "Nivel de urgencia mínimo (1 a 10)" },
        tipo: { type: "string", enum: ["EXTRAORDINARIA", "RECURRENTE"] },
        categoriaId: { type: "string" },
        subcategoriaId: { type: "string" },
      },
    },
  },
  {
    name: "estado_depositos",
    description:
      "Consulta 3: por depósito (o uno en particular), ocupación, algoritmo de matchmaking, asignaciones por estado, " +
      "tiempo promedio entre ingreso y entrega en un rango de fechas y cantidad de entidades atendidas.",
    inputSchema: {
      type: "object",
      properties: {
        depositoId: { type: "string", description: "Opcional, si no se pasa se informan todos" },
        desde: { type: "string", description: "Fecha AAAA-MM-DD, opcional" },
        hasta: { type: "string", description: "Fecha AAAA-MM-DD, opcional" },
      },
    },
  },
  {
    name: "calidad_de_lo_donado",
    description:
      "Consulta 4: por categoría y subcategoría de producto, proporción de donaciones que terminaron con queja, y los " +
      "donadores con más quejas con su estado y categoría.",
    inputSchema: {
      type: "object",
      properties: { top: { type: "number", description: "Cantidad de donadores a listar (por defecto 10)" } },
    },
  },
  {
    name: "resumen_modelo_lectura",
    description: "Cantidad de documentos por colección en el modelo de lectura (MongoDB). Sirve para ver si está sincronizado.",
    inputSchema: { type: "object", properties: {} },
  },
  {
    name: "vaciar_modelo_lectura",
    description:
      "Vacía el modelo de lectura (MongoDB). No toca las bases de los módulos: se usa junto con las limpiezas de cada " +
      `módulo para arrancar de cero antes de cargar los datos de la demo. ${AVISO_IRREVERSIBLE}`,
    inputSchema: { type: "object", properties: {} },
  },
  {
    name: "estado_sistema",
    description:
      "Estado general del sistema: verifica si cada módulo y el Servicio de Consultas están vivos y si su base de datos " +
      "responde, y muestra el resumen del modelo de lectura. Los servicios en Render pueden tardar ~1 minuto en despertar.",
    inputSchema: { type: "object", properties: {} },
  },
];

function queryString(params: Record<string, unknown>) {
  const query = new URLSearchParams();
  for (const [clave, valor] of Object.entries(params)) {
    if (valor !== undefined && valor !== null && valor !== "") query.set(clave, String(valor));
  }
  const texto = query.toString();
  return texto ? `?${texto}` : "";
}

async function chequearServicio(nombre: string, base: string) {
  try {
    const res = await fetch(`${base}/actuator/health`, { signal: AbortSignal.timeout(90_000) });
    const cuerpo: any = await res.json().catch(() => null);
    // El health general da DOWN si falla cualquier componente (por ejemplo solo RabbitMQ), aunque la app
    // responda: por eso "vivo" es que el servicio contestó el health, y cada componente se informa aparte.
    return {
      servicio: nombre,
      vivo: cuerpo?.status !== undefined,
      estado: cuerpo?.status ?? `HTTP ${res.status}`,
      baseDeDatos: cuerpo?.components?.db?.status ?? cuerpo?.components?.mongo?.status ?? "sin dato",
      rabbitMQ: cuerpo?.components?.rabbit?.status ?? "sin dato",
    };
  } catch (error) {
    return { servicio: nombre, vivo: false, estado: `sin respuesta (${error})`, baseDeDatos: "sin dato", rabbitMQ: "sin dato" };
  }
}

async function ejecutarToolConsultas(name: string, args: Record<string, any>) {
  const base = URLS.CONSULTAS;
  switch (name) {
    case "ficha_integral_donador":
      return formatResponse(await fetch(`${base}/donadores/${idPath(args.donadorId)}/ficha`));

    case "necesidades_urgentes_sin_cubrir":
      return formatResponse(
        await fetch(
          `${base}/necesidades-urgentes${queryString({
            urgenciaMinima: args.urgenciaMinima,
            tipo: args.tipo,
            categoriaId: args.categoriaId,
            subcategoriaId: args.subcategoriaId,
          })}`
        )
      );

    case "estado_depositos":
      return formatResponse(
        await fetch(`${base}/depositos/estado${queryString({ depositoId: args.depositoId, desde: args.desde, hasta: args.hasta })}`)
      );

    case "calidad_de_lo_donado":
      return formatResponse(await fetch(`${base}/calidad${queryString({ top: args.top })}`));

    case "resumen_modelo_lectura":
      return formatResponse(await fetch(`${base}/admin/resumen`));

    case "vaciar_modelo_lectura":
      return formatResponse(await enviarJson(`${base}/admin/modelo`, "DELETE"));

    case "estado_sistema": {
      const servicios = await Promise.all([
        chequearServicio("Donaciones", URLS.DONACIONES),
        chequearServicio("Logística", URLS.LOGISTICA),
        chequearServicio("Donadores y Entidades", URLS.DONADORES_ENTIDADES),
        chequearServicio("Incentivos", URLS.INCENTIVOS),
        chequearServicio("Servicio de Consultas", URLS.CONSULTAS),
      ]);
      const modeloLectura = await fetch(`${base}/admin/resumen`, { signal: AbortSignal.timeout(30_000) })
        .then((res) => (res.ok ? res.json() : `HTTP ${res.status}`))
        .catch((error) => `sin respuesta (${error})`);
      const resultado = { accesoPorGateway: Boolean(GATEWAY_URL), servicios, modeloLectura };
      return { content: [{ type: "text", text: JSON.stringify(resultado, null, 2) }] };
    }

    default:
      throw new Error(`Tool de consultas no encontrada: ${name}`);
  }
}

const ejecutoresPorModulo: Array<[Set<string>, (name: string, args: Record<string, any>) => Promise<any>]> = [
  [new Set(adminTools.map((t) => t.name)), ejecutarToolAdmin],
  [new Set(consultasTools.map((t) => t.name)), ejecutarToolConsultas],
  [nombresToolsIncentivos, ejecutarToolIncentivos],
  [new Set(donadoresTools.map((t) => t.name)), ejecutarToolDonadores],
  [new Set(donacionesTools.map((t) => t.name)), ejecutarToolDonaciones],
  [new Set(logisticaTools.map((t) => t.name)), ejecutarToolLogistica],
];

server.setRequestHandler(ListToolsRequestSchema, async () => {
  return {
    tools: [
      {
        name: "consultar_estadisticas_donador",
        description: "Obtiene las estadísticas de un donador (estado, categoría, insignias, misión actual).",
        inputSchema: {
          type: "object",
          properties: {
            donadorId: { type: "string", description: "ID del donador" },
          },
          required: ["donadorId"],
        },
      },
      {
        name: "realizar_donacion",
        description: "Registra una nueva donación en el sistema.",
        inputSchema: {
          type: "object",
          properties: {
            donadorId: { type: "string" },
            depositoId: { type: "string" },
            descripcion: { type: "string" },
            productoId: { type: "string" },
            cantidad: { type: "number" }
          },
          required: ["donadorId", "depositoId", "descripcion", "productoId", "cantidad"],
        },
      },
      {
        name: "reportar_entrega_paquete",
        description:
          "Reporta que un paquete fue entregado a la entidad benéfica: completa su asignación, satisface la necesidad " +
          "y pasa la donación a ACEPTADA. Los ids de paquete salen de listar_asignaciones. Si un intento anterior falló " +
          "a mitad de camino, se puede volver a reportar.",
        inputSchema: {
          type: "object",
          properties: {
            paqueteId: { type: "string", description: "ID del paquete entregado" },
          },
          required: ["paqueteId"],
        },
      },
      {
        name: "registrar_queja_donacion",
        description: "Registra una queja sobre una donación ya entregada (ej. alimentos en mal estado). Cambia el estado de la donación a CONQUEJA y notifica al donador.",
        inputSchema: {
          type: "object",
          properties: {
            donacionId: { type: "string", description: "ID de la donación sobre la que se reclama" },
            descripcion: { type: "string" }
          },
          required: ["donacionId", "descripcion"],
        },
      },
      {
        name: "procesar_donador_incentivos",
        description: "Procesa a un donador para evaluar si cumple misiones y debe subir de categoría o ganar insignias.",
        inputSchema: {
          type: "object",
          properties: {
            donadorId: { type: "string" }
          },
          required: ["donadorId"],
        },
      },
      {
        name: "registrar_necesidad",
        description: "Registra una nueva necesidad material para una entidad benéfica.",
        inputSchema: {
          type: "object",
          properties: {
            entidadId: { type: "string" },
            nivelDeUrgencia: { type: "number", description: "Del 1 al 10" },
            descripcion: { type: "string" },
            productoSolicitadoId: { type: "string" },
            cantidadObjetivo: { type: "number" },
            tipo: { type: "string", description: "EXTRAORDINARIA o RECURRENTE" }
          },
          required: ["entidadId", "nivelDeUrgencia", "descripcion", "productoSolicitadoId", "cantidadObjetivo", "tipo"],
        },
      },
      ...incentivosTools,
      ...donadoresTools,
      ...donacionesTools,
      ...logisticaTools,
      ...adminTools,
      ...consultasTools,
    ],
  };
});





server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const { name } = request.params;
  const args = (request.params.arguments ?? {}) as Record<string, any>;

  try {
    const modulo = ejecutoresPorModulo.find(([nombres]) => nombres.has(name));
    if (modulo) {
      return await modulo[1](name, args);
    }

    switch (name) {
      case "consultar_estadisticas_donador": {
        const res = await fetch(`${URLS.DONADORES_ENTIDADES}/donadores/${args.donadorId}/estadisticas`);
        return formatResponse(res);
      }

      case "realizar_donacion": {
        const res = await fetch(`${URLS.DONACIONES}/donaciones`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            donadorID: String(args.donadorId),
            depositoID: String(args.depositoId),
            descripcion: args.descripcion,
            productoID: String(args.productoId),
            cantidad: Number(args.cantidad)
          }),
        });
        return formatResponse(res);
      }

      case "reportar_entrega_paquete": {
        const res = await fetch(`${URLS.LOGISTICA}/entregas`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ id: String(args.paqueteId) }),
        });
        return formatResponse(res);
      }

      case "registrar_queja_donacion": {
        const res = await fetch(`${URLS.DONACIONES}/donaciones/${args.donacionId}/queja`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            descripcion: args.descripcion,
          }),
        });
        return formatResponse(res);
      }

      case "procesar_donador_incentivos": {
        const res = await fetch(`${URLS.INCENTIVOS}/donadores/${args.donadorId}/procesamiento`, {
          method: "POST"
        });
        return formatResponse(res);
      }

      case "registrar_necesidad": {
        const res = await fetch(`${URLS.DONADORES_ENTIDADES}/necesidades`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            entidadID: String(args.entidadId),
            nivelDeUrgencia: Number(args.nivelDeUrgencia),
            descripcion: args.descripcion,
            productoSolicitadoID: String(args.productoSolicitadoId),
            cantidadObjetivo: Number(args.cantidadObjetivo),
            tipo: args.tipo
          }),
        });
        return formatResponse(res);
      }

      default:
        throw new Error(`Tool no encontrada: ${name}`);
    }
  } catch (error) {
    return {
      content: [{ type: "text", text: `Error interno de conexión o ejecución en el MCP: ${error}` }],
      isError: true,
    };
  }
});

async function formatResponse(response: Response) {
  if (!response.ok) {
    let errorMsg = response.statusText;
    // Algunos módulos devuelven el error como texto plano (ej. Donaciones) y otros como JSON.
    const errorText = await response.text().catch(() => "");
    if (errorText) {
      try {
        const errorBody = JSON.parse(errorText);
        errorMsg = errorBody.mensaje || JSON.stringify(errorBody);
      } catch {
        errorMsg = errorText;
      }
    }
    return {
      content: [{ type: "text", text: `Error en la API (${response.status}): ${errorMsg}` }],
      isError: true,
    };
  }

  const text = await response.text();
  let content = "Operación realizada con éxito (sin contenido de respuesta).";
  if (text) {
    // Algunos endpoints (ej. los de admin de Logística) devuelven texto plano en vez de JSON.
    try {
      content = JSON.stringify(JSON.parse(text), null, 2);
    } catch {
      content = text;
    }
  }

  return {
    content: [{ type: "text", text: content }],
  };
}

async function run() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error("DonaTrack MCP Server conectado y escuchando vía Stdio");
}

run().catch(console.error);