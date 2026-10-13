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

- [ ] T1. Backend+web: import Excel para **Vehiculos**. Parser fila→`CreateVehiculoRequest`, servicio con `ImportContext` (creados/existentes/errores), endpoint `POST /api/admin/import/vehiculos` (`hasRole('ADMIN')`, mismo patron que tarjetas), dialogo en el admin web (`vehiculos` page) reusando el patron visual de `ImportTarjetasDialog`. Fila duplicada (matricula/codigoObra ya existente) no crea, se cuenta como "existente".
- [ ] T2. Backend+web: import Excel para **Tarjetas (alta)**. Columna de codigo de proveedor obligatoria, resuelta contra `ProveedorRepository` (igual que ya hace `ListadoTarjetasImportService.resolverProveedor`), fila con proveedor inexistente → error legible, no crea tarjeta huerfana.
- [ ] T3. Backend+web: import Excel para **VIATs**.
- [ ] T4. Backend+web: import Excel para **Trabajadores** (separar opcionalmente oficina/campo via columna, si la columna no esta, `origen=IMPORTACION` por defecto).
- [ ] T5. Backend+web: import Excel para **Usuarios** (oficina y campo en el mismo import, o dos pestañas/plantillas separadas — decidir al implementar segun cual formato de columnas sea mas simple de explicar al cliente).

## Progreso
(se actualiza tarea a tarea, con commit de work-unit y evidencia de
verificacion por tarea)
