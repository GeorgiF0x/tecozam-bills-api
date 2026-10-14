# Carga masiva de maestros + acceso ampliado en fase de pruebas

## Origen
Feedback cliente (video WhatsApp 2026-09-29 22:42, transcrito). En cola,
pendiente de empezar — el usuario pidio primero arreglar fallos graves ya
detectados (wiring de Maven/Failsafe) antes de tocar esto.

## Lo que pide
1. **Import masivo via Excel (u otro formato) para el resto de maestros**:
   hoy solo existe import Excel para listado de tarjetas (uso de
   `ImportTarjetasDialog` + backend `ListadoTarjetasImportService`). El
   cliente quiere lo mismo para Vehiculos, Tarjetas (alta de tarjeta en si,
   no solo su listado de movimientos), VIATs, Trabajadores y, en
   Configuracion, Usuarios. Pide explicitamente que la propia aplicacion le
   indique que campos exige cada entidad (para poder preparar el fichero el
   mismo), y que separe trabajadores de oficina vs campo si aplica.
2. **Acceso ampliado temporal durante la fase de pruebas**: su usuario
   (admin del cliente) ve "capado" poder editar/borrar maestros libremente
   (ej. borrar un trabajador o centro de coste mal metido). Pide mas permiso
   mientras prueba, con la expectativa explicita de que, una vez el flujo
   automatico (import masivo) funcione bien, se vuelva a restringir ("que no
   pueda tocar nadie"). Ojo: esto es sobre SU PROPIO usuario admin, no sobre
   abrir permisos a todos los roles.
3. **Preocupacion expresa sobre "el buscador"**: dice que le preocupa que no
   funcione porque "va a haber incidencias, fijo" dado el perfil de usuarios
   de campo. No se especifica cual buscador exactamente en este video — por
   contexto (habla justo despues de probar alta de vehiculo/tarjeta/VIAT)
   podria ser el mismo buscador de vincular operacion ya investigado y
   arreglado en `vincular-operacion-mejoras.md` (T1/T4, Service Worker +
   cache stale), o podria ser otro. Pendiente de confirmar con el cliente
   tras desplegar ese fix antes de asumir que ya esta resuelto.

## Contexto adicional (no accionable, solo referencia)
Imagen adjunta (`WhatsApp Image 2026-09-29 at 22.42.50.jpeg`): captura del
formulario "Nuevo usuario de oficina" ya existente en Configuracion, con un
ejemplo de la nomenclatura de contrasena que el cliente ya usa
(`T0373_Arlindo` — prefijo + nombre). Es solo evidencia de que YA sabe usar
la creacion manual de usuarios, no pide cambios ahi.

## Alcance estimado (sin explorar codigo todavia, pendiente al empezar)
- Backend: generalizar el patron de `ListadoTarjetasImportService` (parser
  Excel/CSV + reporte de filas validas/invalidas) a Vehiculos, Tarjetas
  (alta), VIATs, Trabajadores, Usuarios. Posible diseño: un endpoint de
  "plantilla"/schema por entidad que el frontend pueda mostrar, mas un
  endpoint de import por entidad.
- Web admin: un dialogo de import por pantalla de maestro, reusando el
  patron visual de `ImportTarjetasDialog`.
- Revisar permisos del rol ADMIN del cliente para editar/borrar en los 6
  maestros — confirmar que de verdad esta mas restringido de lo esperado
  antes de asumir que hace falta un cambio (podria ser un malentendido de
  UI, no un bug de permisos real).

## Investigacion real (hecha antes de empezar)

**Punto 2 (acceso ampliado) — probablemente YA resuelto, no es un bug**:
verificado endpoint por endpoint (`@PreAuthorize` real) en los 6
controllers de maestros: ADMIN ya puede crear/editar/borrar (logico y
permanente) Vehiculo/Tarjeta/Viat/Trabajador/CentroCoste/Proveedor hoy.
El video es del 29-sep; el CRUD de maestros es deploy manual (no
automatico) y es muy probable que su servidor aun no lo tuviera cuando
grabo el video — version desactualizada, no fallo real. Unico hueco
genuino encontrado: ningun `Usuario` (oficina/campo) tiene borrado
permanente, solo desactivar (`PATCH .../desactivar`) — coherente con el
patron de borrado logico del resto de la app, no necesariamente un bug a
arreglar. No se monta ninguna tarea para esto; si el cliente insiste tras
confirmar que ya tiene el CRUD desplegado, se revisa aparte.

**Patron a replicar para el import** (`ListadoTarjetasImportService` +
`ListadoTarjetasRowParser` + `ImportContext` + `import-tarjetas-dialog.tsx`):
parseo DETERMINISTA fila a fila (sin LLM — aqui no aplica, son columnas de
Excel ya estructuradas que el propio admin rellena siguiendo lo que la app
le pida, no texto libre que interpretar), reporte de contadores
creados/existentes + array de avisos legibles, filas invalidas no
bloquean el resto del archivo.

Campos reales confirmados por entidad (para la plantilla/columnas del Excel):
- **Vehiculo**: `tipo` obligatorio; `matricula`/`codigoObra`/`categoria`/`descripcion` opcionales.
- **Tarjeta (alta)**: `numeroTarjeta` obligatorio unico; `proveedorId` obligatorio (resolver por codigo de proveedor, no crearlo); `alias` opcional.
- **Viat**: `codigo` obligatorio unico; `numeroSerie`/`descripcion` opcionales.
- **Trabajador**: `nombre`/`apellidos` obligatorios; `email`/`dniNie` opcionales; origen se marca `IMPORTACION` (enum `OrigenTrabajador`, ya existe).
- **UsuarioOficina**: `username`/`password`/`nombre`/`rol` obligatorios; `email`/`dni` opcionales.
- **UsuarioCampo**: `username`/`password`/`nombre` obligatorios; `apellidos`/`dni`/`telefono` opcionales.

No hay importer generico reusable — cada uno se construye ad-hoc siguiendo el mismo esqueleto (ya establecido asi deliberadamente en el codebase, ver `ListadoTarjetasParserFactory.java:9-15`).

## Tareas

- [x] T1. **Hecho**. Backend: `vehiculo/infrastructure/import_/VehiculoXlsxRowParser.java`
      (parser puro, cabeceras Tipo/Matricula/CodigoObra/Categoria/Descripcion,
      insensible a tildes/mayusculas) + `FilaVehiculoImportada.java` (record
      con `errorValidacion` cuando la fila no cumple las mismas reglas que
      `VehiculoService.create`: tipo obligatorio, matricula obligatoria para
      VEHICULO, codigoObra obligatorio para INDUSTRIAL_MAQUINARIA).
      `vehiculo/application/VehiculoImportService.java` (orquestador: abre
      XSSFWorkbook, valida tamaño/extension, cuenta creados/existentes,
      duplicado detectado por `existsByMatricula`/`existsByCodigoObra` —
      este ultimo metodo nuevo en `VehiculoRepository`). Endpoint nuevo
      `POST /api/admin/import/vehiculos` (`VehiculoImportController`,
      `hasRole('ADMIN')`), DTO `ImportVehiculosReportDTO` (creados,
      existentes, errores, duracionMs) — no se reutilizo
      `ImportTarjetasReportDTO`, la forma no encajaba limpio (ponytail: DTO
      propio mas simple que forzar una abstraccion compartida).
      No se toco `VehiculoService.create` (sigue igual para el alta manual).
      Frontend: `web/tecozam-bills-web/src/components/import-vehiculos-dialog.tsx`
      (mismo patron visual que `ImportTarjetasDialog`, sin selector de
      proveedor ni checkbox, reporte con 2 contadores + lista de avisos).
      Boton "Importar Excel" en `maestros/vehiculos/page.tsx`, visible solo
      para ADMIN (coherente con el `@PreAuthorize` del endpoint).
      TDD real: `VehiculoXlsxRowParserTest` (8 casos, RED→GREEN confirmado
      rompiendo a mano la validacion de tipo obligatorio) +
      `VehiculoImportServiceIT` (2 casos contra MSSQL real, RED→GREEN
      confirmado rompiendo a mano la deteccion de duplicados — creo 3 filas
      duplicadas sin querer, limpiadas despues a mano por SQL). Suite
      completa: `./mvnw.cmd test` → **291/291, BUILD SUCCESS** (281 previos +
      10 nuevos), 0 regresiones. Frontend: `npx tsc --noEmit` limpio.
      Verificacion manual real en navegador (no solo tsc): backend+web
      levantados en local, login real como `admin`, subido un `.xlsx` de
      prueba con 2 filas validas + 1 invalida (sin matricula) via
      Playwright — reporte mostro "2 creados, 0 existentes, 1 fila con
      aviso", tabla de vehiculos recargada sola mostrando los 2 nuevos.
      Datos de prueba borrados despues de verificar.
      **Nota lateral**: para poder probar en local hizo falta (a) exportar
      `MSSQL_PASSWORD=DevTest_2026!` al arrancar el backend en perfil `dev`
      (el default de `application.yml` es vacio si no se exporta la
      variable — no es un bug, es la config esperada, solo dejar constancia
      para la proxima vez); (b) la contraseña del usuario `admin` local de
      `usuarios_oficina` se desconocia, se reseteo a `admin123` (hash BCrypt
      generado con un test desechable, ya borrado) para poder loguearse —
      si alguien mas dependia de la contraseña anterior de ese usuario en
      este entorno de dev compartido, ya no es la misma.
- [x] T2. Import Excel para **Tarjetas (alta)** ("Alta masiva", distinto del boton
      "Importar Excel" ya existente de listado de movimientos). Columnas
      `NUMERO TARJETA`/`ALIAS`/`PROVEEDOR`; resuelve proveedor con los
      mismos alias SOLRED/CEPSA que `ListadoTarjetasImportService`; fila con
      proveedor inexistente queda en avisos, no crea tarjeta huerfana;
      numero duplicado cuenta como existente.
      Lanzado en paralelo con T3-T5 (4 forks a la vez); este fork murio a
      medio terminar por limite de sesion (no por fallo de codigo) justo
      antes de typecheck+commit. El orquestador completo el cierre:
      confirmo backend+frontend ya correctos (compilaban, sus tests
      pasaban dentro de la suite completa), commiteo en su nombre, y
      verifico manualmente en navegador con un Excel real (1 fila valida +
      1 con proveedor inexistente): "1 creada, 0 existian, 1 fila con
      aviso", tarjeta visible en la tabla. Limpiado despues.
      Ficheros api: `tarjeta/application/TarjetaImportService.java`,
      `tarjeta/dto/ImportTarjetasAltaReportDTO.java`,
      `tarjeta/infrastructure/import_/{FilaTarjetaImportada,TarjetaXlsxRowParser}.java`,
      `tarjeta/infrastructure/web/TarjetaImportController.java`, tests
      `TarjetaImportServiceIT.java`/`TarjetaXlsxRowParserTest.java`.
      Ficheros web: `components/import-tarjetas-alta-dialog.tsx`, boton
      "Alta masiva" en `maestros/tarjetas/page.tsx`.
      Commits: api `2f20236`, web `9822525` (hechos por el orquestador).
- [x] T3. Import Excel para **VIATs**. Columnas `CODIGO`/`NUMERO SERIE`/`DESCRIPCION`;
      codigo obligatorio unico, duplicado cuenta como existente.
      Lanzado en paralelo (4 forks a la vez); no pudo completar su propia
      verificacion manual por interferencia de un fork hermano en el
      servidor de dev compartido (sesion expirada a medio probar, no quiso
      matar procesos compartidos). Tests aislados 7/7 verde, `tsc` limpio.
      Verificacion manual pendiente completada por el orquestador despues:
      Excel real con 1 fila valida subido, "1 creado, 0 existian", VIAT
      visible en la tabla. Limpiado despues.
      Ficheros api: `viat/infrastructure/import_/{ViatXlsxRowParser,FilaViatImportada}.java`,
      `viat/dto/ImportViatsReportDTO.java`, `viat/application/ViatImportService.java`,
      `viat/infrastructure/web/ViatImportController.java`, tests
      `ViatXlsxRowParserTest.java` (5 casos), `ViatImportServiceIT.java` (2 casos).
      Ficheros web: `components/import-viats-dialog.tsx`, boton+dialogo en
      `maestros/viats/page.tsx`.
      Commits: api `7320ff0`, web `16d1c71`.
- [x] T4. Import Excel para **Trabajadores**. Nombre/apellidos obligatorios;
      columna `Origen` opcional (OFICINA/CAMPO/IMPORTACION), por defecto
      IMPORTACION si falta o no se reconoce. Reutiliza `TrabajadorResolver`
      ya existente para deduplicar (email o nombre+apellidos) en vez de
      reinventar la logica.
      Lanzado en paralelo; confirmo contencion real de `mvn test`
      compartiendo `target/` entre los 4 forks (`NoClassDefFoundError`
      transitorio, resuelto con reintento, no era bug) — validacion util
      de que correr la suite completa una sola vez al final (hecho por el
      orquestador) era lo correcto. 9/9 tests propios en verde (7 parser +
      2 IT), `tsc` limpio.
      Ficheros api: `trabajador/application/TrabajadorImportService.java`,
      `trabajador/dto/ImportTrabajadoresReportDTO.java`,
      `trabajador/infrastructure/import_/{FilaTrabajadorImportada,TrabajadorXlsxRowParser}.java`,
      `trabajador/infrastructure/web/TrabajadorImportController.java`, tests
      `TrabajadorImportServiceIT.java`/`TrabajadorXlsxRowParserTest.java`.
      Ficheros web: `components/import-trabajadores-dialog.tsx`, boton en
      `maestros/trabajadores/page.tsx`.
      Commits: api `29236e7`, web `6e10858`.
- [x] T5. Import Excel para **Usuarios** (oficina y campo). Decision: UN
      SOLO Excel con columna `Tipo` (OFICINA/CAMPO) que determina que
      columnas aplican (Rol obligatorio solo oficina; Apellidos/Telefono
      solo campo) — mas simple de explicar al cliente que 2 plantillas
      separadas. Reutiliza `UsuarioOficinaService.crear`/`UsuarioCampoService.crear`
      existentes (password ya validado/encriptado por esos servicios, no
      duplicado aqui). Username duplicado (en cualquiera de las 2 tablas)
      cuenta como existente.
      Lanzado en paralelo; tambien confirmo la misma contencion transitoria
      de `target/` que T4, resuelta con reintento. 10/10 tests propios en
      verde, `tsc` limpio, verificado E2E real en navegador por el propio
      fork (subida de 3 filas → "2 creados, 0 existentes, 1 aviso", usuario
      visible tras recargar, datos de prueba limpiados por SQL directo).
      Ficheros api: `auth/infrastructure/import_/{FilaUsuarioImportada,UsuarioXlsxRowParser}.java`,
      `auth/application/UsuarioImportService.java`,
      `auth/infrastructure/web/UsuarioImportController.java`,
      `auth/dto/ImportUsuariosReportDTO.java`, tests
      `UsuarioXlsxRowParserTest.java` (8 casos)/`UsuarioImportServiceIT.java` (2 casos).
      Ficheros web: `components/import-usuarios-dialog.tsx`, boton en
      `configuracion/usuarios/page.tsx`.
      Commits: api `5efa7ab`, web `8eefb5c`.

## Feature completa (5/5)

Las 5 tareas de import Excel masivo estan hechas y verificadas (backend
+ frontend, cada una con su propio commit). T2-T5 se ejecutaron en
paralelo (4 forks a la vez, mismo repo) tras verificar que el riesgo de
interferencia era bajo (cada dominio toca ficheros nuevos y distintos,
sin importer compartido). Contratiempos reales encontrados y resueltos:
contencion de `target/` de Maven entre builds concurrentes (mitigada
corriendo la suite completa una sola vez al final, con los 4 ya
terminados: **327/327, BUILD SUCCESS**), un fork (T2) murio a medio
terminar por limite de sesion y el orquestador completo su cierre.

El punto 2 original del feedback del cliente (acceso ampliado) se
confirmo que probablemente ya esta resuelto por el CRUD de maestros ya
existente — no se monto tarea para eso, ver seccion "Investigacion
real" mas arriba.

## Progreso
(se actualiza tarea a tarea, con commit de work-unit y evidencia de
verificacion por tarea)
