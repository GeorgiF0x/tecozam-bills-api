# Import de maestros: admitir .xls antiguo y añadir import por PDF con IA + revisión

Objetivo: ampliar el import masivo de maestros (ver `maestros-import-masivo.md`,
ya cerrado 5/5 solo Excel) para admitir también `.xls` antiguo y, como
desarrollo nuevo, import por PDF usando IA con pantalla de revisión previa a
confirmar el alta. Branch: a crear al empezar (`feature/maestros-import-pdf`).
Test runner: `./mvnw.cmd test` (api), `npx tsc --noEmit` (web).

## Origen

Conversación 2026-11-22/23, a raíz de construir la guía de novedades v1.0.18
("import de facturas también en Excel"). El usuario preguntó si esto era
solo para facturas o también para maestros:

> "esto no solo lo quieren para las facturas tambien para los maestros"

Verificado en código: el import de maestros YA aceptaba Excel desde antes
(feature `maestros-import-masivo.md`, cerrada 5/5), pero **solo `.xlsx`**, sin
LLM (parser determinista fila a fila, `XSSFWorkbook` fijo). El import de
facturas en cambio acepta `.xlsx` Y `.xls` (usa `WorkbookFactory`, que
detecta el formato) y SÍ usa IA (`LlmFacturaParser`).

Al aclarar el alcance, el usuario confirmó que el cliente pide más:

> "osea aceptar xls y tambien pdf , en principio como lo procesa la ia no
> deberia romper verdad?"
> "si , me dijeron que les venia bien tambien PDFS"

Verificado y corregido: el import de maestros NO usa IA hoy (ver arriba),
así que "añadir PDF" no es ampliar un parser existente — es construir uno
nuevo por maestro, con el riesgo de que la IA extraiga mal una fila entre
muchas sin que nadie lo note antes de guardar (a diferencia de facturas, que
es una sola entidad por documento). Se lo planteé al usuario y confirmó que
quiere mitigarlo con una pantalla de revisión.

## Decisiones del usuario (AskUserQuestion, confirmadas)

- **Alcance**: "Todos los maestros" — trabajadores, vehículos, tarjetas
  (alta), usuarios (oficina/campo), viáticos (VIATs). Los 5 tipos que ya
  tienen import Excel en `maestros-import-masivo.md`.
- **Seguridad del import por PDF**: "Sí, revisión antes de confirmar
  (Recomendado)" — la IA extrae las filas del PDF, se muestran en una tabla
  editable, el gestor revisa/corrige y confirma antes de que se guarde nada
  en BD. Distinto del Excel (que sigue yendo directo a BD sin revisión, no
  se toca ese comportamiento).
- **Cuándo implementar**: cambio de planes tras crear esta ficha — el
  usuario avisó que hay que entregarlo mañana (2026-11-24). Se repriorizó
  en caliente:
  > "trabajadores vehiculos y tarjetas" (prioridad para mañana, con PDF+IA)
  Viáticos y Usuarios quedan pospuestos, se enchufan al mismo pipeline
  compartido más adelante sin repetir el trabajo de diseño.

## Specs

- **S1**: Los 5 imports Excel ya existentes (Vehiculo, Tarjeta-alta, Viat,
  Trabajador, Usuario) deben aceptar también `.xls` además de `.xlsx`.
  Cambio acotado: sustituir `new XSSFWorkbook(in)` por
  `WorkbookFactory.create(in)` en los 5 `*ImportService.java` (mismo patrón
  que ya usa `LlmFacturaParser.extraerTexto`), más actualizar `accept` en
  los 6 inputs de archivo del frontend (`import-*-dialog.tsx`) de `.xlsx` a
  `.xlsx,.xls`. Sin riesgo: no cambia el parseo de celdas, solo cómo se abre
  el archivo.
- **S2**: Añadir import por PDF (con IA) para los mismos 5 maestros. Cada
  maestro necesita su propio extractor IA (prompt + esquema de salida) que
  lea el PDF y devuelva una lista de filas candidatas con los mismos campos
  que ya exige cada `Fila*Importada` existente (ver tabla de campos reales
  en `maestros-import-masivo.md`, sección "Campos reales confirmados").
- **S3**: El flujo de PDF NO debe guardar directo en BD. Debe generar una
  previsualización (filas extraídas + validación con las mismas reglas que
  ya usa cada `*ImportService` para Excel) que el gestor pueda revisar,
  corregir fila a fila y confirmar antes de persistir. El Excel sigue yendo
  directo a BD, sin cambios ahí.
- **S4**: Reutilizar toda la lógica de validación/dedupe/resolución ya
  existente por maestro (`TrabajadorResolver`, `existsByMatricula`,
  `existsByEmail`, etc.) — el extractor IA solo reemplaza la fuente de las
  filas (PDF en vez de Excel), no la lógica de negocio posterior.

## Alcance estimado (pendiente de explorar código al empezar cada tarea)

- Backend: 5 extractores IA nuevos (uno por maestro) + 1 endpoint de
  previsualización nuevo por maestro (`POST /.../import-pdf/preview` →
  devuelve filas candidatas con validación, sin persistir) + 1 endpoint de
  confirmación (`POST /.../import-pdf/confirmar` → persiste las filas ya
  revisadas/editadas por el usuario).
- Web admin: un componente de revisión reusable (tabla editable + badges de
  error por fila) que cada `import-*-dialog.tsx` pueda montar tras recibir
  la previsualización, antes de confirmar.
- Decidir pendiente al empezar: modelo/proveedor IA a usar para la
  extracción de listas (mismo que `LlmFacturaParser` u otro), límite de
  tamaño de PDF, qué pasa si la IA no reconoce ninguna fila.

## Tareas

- [x] T0. **Hecho**. Backend: los 5 `*ImportService.java` (Vehiculo, Tarjeta,
      Viat, Trabajador, Usuario) cambiados de `new XSSFWorkbook(in)` a
      `WorkbookFactory.create(in)`; los 5 `*ImportController.java` amplían su
      validación de extensión de solo `.xlsx` a `.xlsx` o `.xls`. Frontend:
      los 5 `import-*-dialog.tsx` relevantes (trabajadores, usuarios,
      tarjetas-alta, vehiculos, viats — NO se tocó `import-tarjetas-dialog.tsx`,
      que es el flujo de listado de MOVIMIENTOS, otro dominio) actualizados en
      `accept`, el regex de validación cliente `XLSX_EXTENSION_REGEX` (antes
      bloqueaba `.xls` con "Solo archivos .xlsx" aunque el input ya lo
      aceptara — bug que se habría colado sin revisar el validador JS, no
      solo el atributo `accept`), y los textos visibles ("Sube un Excel
      (.xlsx)..." → "...(.xlsx o .xls)...", "Arrastra el .xlsx aquí" →
      "Arrastra el Excel aquí").
      Verificado: `./mvnw.cmd test` → **439/439, BUILD SUCCESS** (sin
      regresiones), `npx tsc --noEmit` limpio. De paso se extrajeron métodos
      `validar(...)` puros en los 3 `*XlsxRowParser` de Trabajador/Vehiculo/
      Tarjeta y `procesarFilaValidada(fila)` en sus 3 `*ImportService` —
      refactor preparatorio para T1-T4 (PDF+IA), sin cambiar el
      comportamiento del import Excel existente.
- [x] T1. **Hecho**. Pipeline compartido: `shared/infrastructure/llm/LlmListaExtractorService`
      (extrae texto PDF con PDFBox igual que `LlmFacturaParser`, llama a
      OpenAI en modo `json_schema` estricto, envuelve el schema de UNA fila
      — aportado por cada dominio — en el objeto raíz `{"filas":[...]}` que
      exige OpenAI; helper estático `nullableString` para campos opcionales).
      DTOs compartidos en `shared/dto`: `ColumnaDTO`, `FilaPreviewDTO`,
      `ImportPdfPreviewDTO`, `ConfirmarImportPdfRequest`. Refactor
      preparatorio (parte de T0): `validar(...)` público y puro extraído en
      los 3 `*XlsxRowParser` + `procesarFilaValidada(...)` público extraído
      en los 3 `*ImportService` (Tarjeta devuelve un record
      `ResultadoFila(yaExistia, error)` porque ahí puede fallar por
      proveedor no resuelto, Trabajador/Vehiculo devuelven `boolean`).
- [x] T2. **Hecho**. PDF+IA para **Trabajadores**. `TrabajadorPdfImportController`
      (`/api/admin/import/trabajadores/pdf/{preview,confirmar}`), reusa
      `TrabajadorXlsxRowParser.validar` + `TrabajadorImportService.procesarFilaValidada`.
      6/6 tests propios en verde.
- [x] T3. **Hecho**. PDF+IA para **Vehículos**. `VehiculoPdfImportController`
      (`/api/admin/import/vehiculos/pdf/{preview,confirmar}`), reusa
      `VehiculoXlsxRowParser.validar` + `VehiculoImportService.procesarFilaValidada`.
      7/7 tests propios en verde.
- [x] T4. **Hecho**. PDF+IA para **Tarjetas (alta)**. `TarjetaPdfImportController`
      (`/api/admin/import/tarjetas/pdf/{preview,confirmar}`), reusa
      `TarjetaXlsxRowParser.validar` + `TarjetaImportService.procesarFilaValidada`
      (maneja el caso extra de proveedor no resuelto vía `ResultadoFila.error`).
      6/6 tests propios en verde.
      Frontend (compartido por T2-T4): `components/import-pdf-review-table.tsx`
      (tabla editable genérica, parametrizada por columnas, marca filas
      inválidas, permite borrar fila antes de confirmar) + 3 diálogos
      (`import-{trabajadores,vehiculos,tarjetas}-pdf-dialog.tsx`, flujo
      idle→previewing→review→done/error) + botón "Importar PDF (IA)" en las
      3 páginas de maestro (`src/app/(dashboard)/maestros/{dominio}/page.tsx`
      — la ruta real tiene el grupo `(dashboard)`, no estaba en el encargo
      original). `npx tsc --noEmit` limpio.
      T2-T4 lanzados en paralelo (3 workers backend + 1 frontend, archivos
      disjuntos, mismo contrato de API dado por adelantado a los 4 sin que
      se vieran entre ellos). Verificación final del orquestador: suite
      completa `./mvnw.cmd test` → **458/458, BUILD SUCCESS** (439 previos +
      19 nuevos), sin regresiones; spot-check manual de
      `VehiculoPdfImportController.java` completo, consistente con el
      contrato. Un diagnóstico de IDE marcó código de los 3 `page.tsx` como
      "nunca usado" (botón/diálogo nuevos) — investigado y confirmado falso
      positivo (snapshot desactualizado): el botón y el diálogo sí están
      montados en el JSX, `tsc` en fresco sale limpio.
      **Verificación manual en vivo — hecha 2026-11-23**: backend+web
      levantados en local (`run-local.sh` + `NEXT_PUBLIC_API_URL=http://localhost:8090`
      — el frontend por defecto apunta a `localhost:8080`, no al puerto real
      `8090` del `.env`, ojo la próxima vez), `OPENAI_API_KEY` real del
      `.env` ya configurada. BD local sin ningún usuario ADMIN (tablas
      `usuarios`/`usuarios_oficina` vacías tras el último reset del
      contenedor) — se insertó un admin de prueba a mano (hash BCrypt vía
      test desechable, igual que en `maestros-import-masivo.md` T1). 3 PDFs
      de prueba generados con datos ficticios (técnica HTML→Chrome headless,
      no Excel reales — más simple y igual de válido para probar el
      pipeline) subidos vía Playwright real contra los 3 endpoints:
      - **Trabajadores**: extracción perfecta (email vacío respetado como
        null, origen ausente cayó a IMPORTACION por defecto). Confirmado:
        2 creados, 0 errores.
      - **Vehículos**: **la IA SE EQUIVOCÓ** — en las 2 filas, confundió la
        palabra "ZZTEST" de la descripción con el campo código de obra
        (p.ej. "OBRA-ZZTEST-01" se extrajo como solo "ZZTEST", perdiendo el
        valor real). Ambas filas pasaban la validación igualmente
        (código de obra es opcional para VEHICULO, y para
        INDUSTRIAL_MAQUINARIA solo exige que no esté vacío) — es decir, el
        dato corrupto habría entrado en BD en silencio sin la pantalla de
        revisión. Corregido a mano en la tabla de revisión antes de
        confirmar; confirmado: 2 creados, 0 errores con los datos ya
        correctos. **Esto confirma en la práctica, el primer día de uso
        real, exactamente el riesgo por el que se diseñó el paso de
        revisión** — ver decisión del usuario más arriba.
      - **Tarjetas**: extracción perfecta (proveedor REPSOL resuelto bien).
        Confirmado: 2 creados, 0 errores.
      Datos de prueba limpiados por SQL directo tras verificar (BD local,
      sin impacto en ningún entorno compartido).
- [x] T5. **Hecho**. PDF+IA para **VIATs**. `ViatPdfImportController`
      (`/api/admin/import/viats/pdf/{preview,confirmar}`), reusa
      `ViatXlsxRowParser.validar` + `ViatImportService.procesarFilaValidada`.
      5/5 tests propios en verde. Verificado en vivo: extracción perfecta,
      2 creados, 0 errores.
- [x] T6. **Hecho**. PDF+IA para **Usuarios** (oficina/campo). Decisión de
      seguridad del usuario: la contraseña NUNCA se manda a la IA — el
      schema que se le pide al modelo no incluye `password`, el preview
      siempre devuelve ese campo vacío (y por tanto casi toda fila sale
      `valida=false` con "La contraseña es obligatoria", a propósito), el
      gestor la escribe a mano en la tabla de revisión antes de confirmar.
      `UsuarioPdfImportController` (`/api/admin/import/usuarios/pdf/{preview,confirmar}`),
      reusa `UsuarioXlsxRowParser.validar` + `UsuarioImportService.procesarFilaValidada`.
      6/6 tests propios en verde.
      Frontend (T5+T6): `import-viats-pdf-dialog.tsx`, `import-usuarios-pdf-dialog.tsx`
      (con aviso visible de que la IA nunca extrae contraseñas), botones en
      `maestros/viats/page.tsx` y `configuracion/usuarios/page.tsx`. Se
      añadió soporte opcional `type="password"` a `import-pdf-review-table.tsx`
      por clave de columna, retrocompatible con T2-T4.
      Verificado en vivo: VIATs extracción perfecta (2 creados). Usuarios
      confirmado que el campo contraseña del PDF de prueba (sí llevaba
      contraseñas reales de ejemplo) **nunca llegó en la respuesta del
      backend** — password siempre `""`, confirmando que el diseño de
      seguridad funciona; se completaron las contraseñas a mano en la
      pantalla de revisión y se confirmó (2 creados, 0 errores). Aviso
      menor: la IA quitó el prefijo "ZZTEST " de un apellido (lo trató como
      ruido/marca de test, no como parte del dato) — cosmético, no mezcla
      de campos como el caso de Vehículos, pero mismo patrón de fondo
      (la IA "limpia" texto que interpreta como no literal pese a
      pedírselo explícitamente en el prompt).
      Suite completa tras T5+T6: `./mvnw.cmd test` → **469/469, BUILD
      SUCCESS**, `tsc` limpio.

## Feature completa (6/6 — T0 a T6, salvo T0 ya cubría los 5 maestros)

Las 5 maestros (Trabajadores, Vehículos, Tarjetas, VIATs, Usuarios) tienen
ya: `.xls` ampliado (T0) y PDF+IA con pantalla de revisión antes de
confirmar (T1-T6). Construido en su totalidad el mismo día (2026-11-23) con
7 workers en paralelo en 2 tandas (3+1 luego 2+1), todos verificados con
tests automatizados Y verificación manual en vivo contra OpenAI real.

## Progreso

Ficha creada 2026-11-23. Repriorizada el mismo día: entrega mañana
(2026-11-24) de T0 + T1 + T2-T4 primero por el deadline, luego T5-T6
añadidos tras confirmar con el usuario que quería completar los 5 maestros.
**Las 7 tareas hechas y verificadas (tests + manual) el mismo día.**
Pendiente: decisión de pushear/desplegar (nada se ha pusheado todavía).
