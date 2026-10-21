# Bugs de sesión (NaN en Cepsa, huso horario en ticket) + reclasificación automática ViaT Cepsa/Moeve

## Objetivo
Arreglar dos bugs reales encontrados al verificar el cotejo con datos reales
de Moeve/Cepsa, y automatizar la reclasificación TARJETA→VIAT para
Cepsa/Moeve usando el historial real de operaciones (no el listado maestro,
que no tiene señal fiable — ver `odd/tasks/clasificacion-viat-por-numero.md`).

## Por qué
Al importar la factura real `AF8350000000062431` (Moeve/Cepsa, 94 tarjetas,
1314 operaciones) para probar el cotejo:
1. La fila de la tarjeta `903338001814200008` (concepto PEAJE) se importó
   con importe `NaN €` — fallo de parseo real en `MoeveFacturaParser`.
2. Al crear un ticket manual de prueba para el cotejo, el selector de
   fecha/hora de "Nuevo ticket" (admin) desplazó la hora introducida: -2h en
   junio (CEST, UTC+2), -1h en enero (CET, UTC+1) — confirma que es un bug
   de huso horario dependiente de horario de verano/invierno, no un offset
   fijo. Componente: `web/tecozam-bills-web/src/components/ui/date-time-picker.tsx`.
3. El usuario pidió además automatizar la clasificación TARJETA/VIAT para
   Cepsa/Moeve. La investigación previa (`clasificacion-viat-por-numero.md`)
   ya descartó el número de tarjeta como señal fiable para Cepsa. Pero al
   importar la factura real se confirmó que **cada operación real sí trae su
   concepto correcto** (PEAJE vs combustible) — a diferencia del listado
   maestro de tarjetas, que suele traer el concepto vacío. Eso da una señal
   fiable: el historial real de operaciones de una tarjeta.

## Decisión (usuario)
Tras importar una factura de Cepsa/Moeve, para cada tarjeta tocada: si tiene
**3 o más operaciones reales en BD y el 100% son de peaje/telepeaje (0%
combustible)**, reclasificar automáticamente esa tarjeta de TARJETA a VIAT.
Umbral elegido por el usuario (opción intermedia, no el más laxo ni el más
estricto) para evitar mover una tarjeta de combustible que aún no ha
repostado, pero actuar en cuanto hay patrón real claro.

## Alcance

### 1. Bug NaN en `MoeveFacturaParser`
- Localizar en `factura/infrastructure/parser/MoeveFacturaParser.java` el
  punto donde se calcula `importeTotal` (o el campo equivalente) para una
  operación de concepto PEAJE y reproducir con un fixture basado en la fila
  real que falló (tarjeta `903338001814200008`, factura
  `AF8350000000062431`, concepto PEAJE).
- Root cause primero (no parchear a ciegas): probablemente una columna del
  PDF con un formato distinto para filas de peaje vs combustible (importe en
  otra posición, separador decimal distinto, o un campo vacío que se parsea
  como número sin comprobar antes).
- TDD: test que reproduce el `NaN` en rojo, luego fix.

### 2. Bug de huso horario en `DateTimePicker`
- `web/tecozam-bills-web/src/components/ui/date-time-picker.tsx`: revisar
  `handleDayChange`/`handleTimeChange` y cómo se construye el `Date` final,
  y cómo lo consume el formulario "Nuevo ticket" (admin) al serializarlo
  para el backend (probablemente una conversión a ISO string o
  `toISOString()` que asume UTC en vez de hora local, o viceversa).
- Reproducir con un test (si hay test de componentes en este proyecto
  frontend) o al menos con un caso manual documentado: fecha de verano
  (desfase -2h) y fecha de invierno (desfase -1h) para confirmar que el fix
  corrige ambos casos (no solo uno).

### 3. Reclasificación automática Cepsa/Moeve por historial de operaciones
- Nuevo servicio (paralelo a `ReclasificarViatsService`, mismo patrón:
  dry-run + audit table + idempotente) que, tras importar una factura
  Cepsa/Moeve (`ListadoTarjetasImportService`/`FacturaImportService` — aquí
  es el pipeline de FACTURAS, no el de listado de tarjetas maestro, ver
  `factura/application/*ImportService*`), revise las tarjetas tocadas en esa
  factura: si `COUNT(operaciones de esa tarjeta) >= 3` y
  `COUNT(operaciones con concepto NO peaje) == 0`, mover la tarjeta de
  TARJETA a VIAT (crear Viat, borrar/archivar Tarjeta, igual que hace
  `ReclasificarViatsService` en la dirección opuesta).
- Usar `ConceptoRecognizer`/el enum/categorías ya existentes para decidir
  qué es "peaje" vs "combustible" en una operación real — no inventar un
  segundo clasificador de texto.
- Traza clara (nunca silenciosa): registrar en el reporte de import de
  facturas o en un log de auditoría qué tarjetas se reclasificaron y por
  qué (conteo de operaciones peaje/combustible).
- Confirmar que NO rompe el cotejo ya probado (operaciones ya vinculadas a
  tickets no deben perder su vínculo al mover la tarjeta — revisar si
  `Operacion`/`Ticket` referencian `Tarjeta` por FK directa o por número).

## Fuera de alcance
- Tocar la lógica de Repsol (ya arreglada y verificada, intacta).
- Cambiar el umbral elegido sin que el usuario lo pida.

## TDD
Modo activo (igual que en `clasificacion-viat-por-numero.md`). RED→GREEN
para el bug de `MoeveFacturaParser` y para la reclasificación nueva.

## Progreso
- [x] 1. Root cause + fix del NaN en `MoeveFacturaParser` + test

      Root cause (confirmado con el PDF real, no parcheado a ciegas): en el
      extracto real de la factura `AF8350000000062431`, la fila de operación
      de la tarjeta `903338001814200008` (concepto largo "GESTION
      SERV.AUTOPISTAS PORTUG") se extrae con PDFBox partida en TRES líneas
      de texto en vez de una sola:
      ```
      OPERACIONES GESTION SERVICIOS 2026-01-31 19:43 06401015159 GESTION SERV.AUTOPISTAS
      PORTUG
      135,95
      ```
      (volcado con un test de scratch temporal usando
      `PDFTextStripper.setSortByPosition(...)`, confirmado en ambos modos
      true/false sobre `Extracto.pdf`; test borrado tras el diagnóstico, no
      commiteado). `P_OP_LINE_PEAJE` solo reconocía el formato de una línea
      — la fixture existente `familyA_pt_extracto_cartao.txt` asumía
      (incorrectamente) que el concepto+importe cabían en una sola línea,
      algo que nunca pasó con el PDF real. Al no haber importe en la línea
      que sí matcheaba, la operación se creaba con `importeTotal=null` (el
      frontend lo renderiza como "NaN €"), y la línea aislada "135,95" se
      descartaba en silencio.

      Fix: `MoeveFacturaParser.parseExtracto` ahora detecta cuando
      `P_OP_LINE_PEAJE` matchea pero no trae importe, y deja la operación
      "pendiente" (`PendingPeaje`) acumulando líneas de texto siguientes
      como continuación del concepto, hasta encontrar una línea compuesta
      solo por tokens numéricos (el importe), momento en el que cierra la
      operación. No se tocó Repsol ni el fix anterior de clasificación por
      número de tarjeta.

      Test (RED→GREEN, TDD):
      `MoeveFacturaParserTest#parseExtractoPeajeConceptoPartidoEnTresLineasConImporteAislado`,
      fixture nueva `familyA_extracto_peaje_concepto_partido_tres_lineas.txt`
      basada en el volcado real. RED confirmado antes del fix
      (`expected: "GESTION SERV.AUTOPISTAS PORTUG" but was: "GESTION
      SERV.AUTOPISTAS"`, importeTotal null). GREEN tras el fix, sin romper
      ninguno de los tests existentes de `MoeveFacturaParserTest` (incluidos
      los de peaje de una sola línea, familyA/familyB, ES/PT).

- [x] 2. Root cause + fix del huso horario en `DateTimePicker` + verificación
      manual verano/invierno — causa real: `tickets/page.tsx` serializaba el
      `Date` local con `toISOString()` (UTC) hacia un campo backend
      `LocalDateTime` (sin zona). `date-time-picker.tsx` en sí no tenía bug.
      Fix: nuevo `toLocalIsoString()` en `src/lib/formatters.ts`, usado en el
      `onChange` del picker y en el payload de `/tickets`. `npx tsc --noEmit`
      limpio. Ya existía el mismo patrón de fix (con el mismo diagnóstico en
      comentario) en `prestamos/page.tsx`, lo que confirma la causa.
- [x] 3. Servicio de reclasificación automática Tarjeta→Viat por historial
      real de operaciones (umbral 3+ operaciones, 0% combustible) + test

      Nuevo servicio `ReclasificarTarjetasAViatService`
      (`admin/application/`, mismo paquete y mismo patrón que
      `ReclasificarViatsService`: dry-run + tabla de auditoría
      `auditoria_reclasificacion_tarjetas` (migración `V33`) + idempotente).
      Clasificación de "peaje" reutilizando el campo ya existente
      `Operacion.conceptoUnificado` (enum `ConceptoUnificado.PEAJE`, ya
      asignado por el parser por operación) — se prefirió sobre
      `ConceptoRecognizer` porque ese último es package-private
      (`admin.infrastructure.import_`, no visible desde `admin.application`)
      y solo expone un booleano "concepto conocido" (combustible O peaje),
      no la distinción peaje/no-peaje que pide la regla; `ConceptoUnificado`
      es la clasificación real por operación, más fiable que re-derivar de
      texto.

      **Punto de enganche elegido**: automático, dentro de
      `FacturaService.importar(...)` (pipeline de FACTURAS —
      `factura/application/FacturaService.java`, paso "6. Vincular tarjetas
      del maestro", NO el pipeline de listado maestro
      `ListadoTarjetasImportService`, que no se tocó), justo después de
      `facturaRepository.save(factura)`, limitado a las tarjetas tocadas por
      ESA factura y solo cuando el proveedor es Cepsa/Moeve (reutiliza el
      booleano `esCepsaMoeve` ya existente en el método, el mismo que exige
      el extracto). Se evalúa DESPUÉS de guardar para que las operaciones de
      la factura recién importada cuenten en el historial real (Hibernate
      autoflush garantiza que las queries de conteo ven las filas recién
      persistidas). Motivo de la elección: la decisión del usuario en este
      mismo documento ya fija que es automático tras cada import ("Tras
      importar una factura de Cepsa/Moeve, para cada tarjeta tocada..."), a
      diferencia de `ReclasificarViatsService` que es un endpoint admin bajo
      demanda (retrofit de un bug histórico, un caso distinto). No se creó
      un endpoint admin nuevo para esto.

      **Riesgo de FK investigado** (obligatorio antes de mover nada):
      - `Operacion` (factura.domain) NO referencia la `Tarjeta` del maestro
        directamente — solo referencia `TarjetaResumen` (`tarjeta_resumen_id`
        NOT NULL). El cotejo (`OperacionRepository.findParaCotejo*`) tampoco
        depende de ningún FK a `Tarjeta`: compara `tarjetaResumen.numTarjeta`
        como String. Mover/borrar una `Tarjeta` del maestro NO rompe el
        cotejo ya hecho.
      - `TarjetaResumen.tarjeta` SÍ es un FK opcional a `Tarjeta` (se rellena
        en cada import, `FacturaService` paso 6). Como no lo usa el cotejo,
        antes de borrar la `Tarjeta` el servicio desvincula (pone a `null`)
        TODOS los `TarjetaResumen` que la referencian, de cualquier factura
        pasada o presente (`TarjetaResumenRepository.findByTarjetaId`, nuevo
        método).
      - **Riesgo real confirmado**: `Ticket.tarjeta` (`ticket/domain/
        Ticket.java`) y `TarjetaAsignacion.tarjeta` (`tarjeta/domain/
        TarjetaAsignacion.java`) SÍ son FK directas a `Tarjeta.id`. Borrar
        una tarjeta con tickets reales o asignaciones de conductor activas
        rompería esas referencias (un ticket ya cotejado perdería su
        tarjeta). Mitigación: el servicio comprueba
        `TicketRepository.existsByTarjetaId` y
        `TarjetaAsignacionRepository.existsByTarjetaId` (ambos métodos ya
        existían) antes de mover; si cualquiera es `true`, la tarjeta queda
        **protegida** (no se borra, se reporta en
        `ReclasificarTarjetasAViatResponse.numerosProtegidos` y se loguea en
        WARN) en vez de romperse en silencio. Esto es exactamente el caso
        que el encargo pedía señalar explícitamente en vez de implementar a
        ciegas.
      - Nota menor (no bloqueante): si una tarjeta ya reclasificada a VIAT
        vuelve a aparecer en una factura futura, el paso 6 de
        `FacturaService` (no tocado, compartido con Repsol) la recreará como
        `Tarjeta` nueva antes de que este servicio la vuelva a mover a VIAT
        en la misma transacción — efecto secundario inocuo (reutiliza el
        mismo `Viat` por código, `findByCodigo`), pero crea/borra una fila
        `Tarjeta` de más en cada ciclo.

      Tests (TDD): `ReclasificarTarjetasAViatServiceTest` (7 casos: dispara
      con 3+ operaciones 100% peaje; NO dispara con 2 operaciones; NO
      dispara con 3 operaciones y 1 de combustible; protegida por tickets
      vinculados; protegida por asignación activa; dry-run no muta nada;
      deduplicación por id) + `FacturaServiceTest` (2 casos nuevos: invoca
      la reclasificación tras importar Cepsa/Moeve; NO la invoca tras
      importar Repsol).

- [ ] 4. Verificación manual end-to-end (reimportar la factura real, confirmar
      NaN corregido, confirmar reclasificación de tarjetas de peaje puro,
      confirmar que el cotejo ya probado sigue intacto)

      (pendiente — la hace el orquestador con Playwright)

### Evidencia de test (puntos 1 y 3, backend)

```
$ ./mvnw.cmd -q test
MVN_EXIT=0
```
Suite completa de backend: 475 tests, 0 failures, 0 errors, 0 skipped
(agregado de `target/surefire-reports/*.txt`). Incluye los tests nuevos de
los puntos 1 y 3 en verde y confirma 0 regresiones (Repsol, clasificación
por número de tarjeta, y el resto de la suite, intactos).
