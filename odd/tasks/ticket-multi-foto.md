# Ticket compuesto por varias fotos (N fotos -> 1 ticket, M operaciones)

## Objetivo
El cliente reporta que en tickets largos el OCR actual "se lía" y rellena mal
el formulario. Solución acordada: permitir que el trabajador de campo adjunte
2 o más fotos ANTES de guardar el ticket, componiendo un único ticket con
varias operaciones (líneas), en vez de forzar 1 foto = 1 ticket.

## Problema (confirmado leyendo el codigo actual)
- PWA (`app/escanear/page.tsx`): captura exactamente 1 foto por sesión de
  escaneo (`imageBlob: Blob | null`, sin selección múltiple).
- API (`TicketController.performOcr`, lineas 153-170 y 444-461 duplicado):
  OCR con prompt de texto libre (SIN `response_format: json_schema`), a
  diferencia de facturas (`LlmFacturaParser`, que ya usa salida estructurada
  estricta). Esto es la causa mas probable de que el formulario se rellene
  mal en tickets largos: no hay schema que fuerce estructura.
- `Ticket` (domain): campos escalares planos (`estacion`, `importeTotal`,
  `litros`...) + una unica `Operacion operacionCotejada` nullable. 1 ticket =
  1 operacion, hard-coded.
- La imagen nunca se persiste (`imagenUrl` existe en la entidad pero no se
  asigna nunca) — se descarta tras el OCR.
- `verificarNoEsDuplicado` (TicketService) BLOQUEARIA hoy subir una 2a foto
  del mismo ticket, interpretandolo como duplicado accidental.
- Pantalla de cotejo en admin web asume 1 `estadoCotejo` + 1
  `operacionCotejada` por fila de ticket.

## Diseño acordado con el usuario
1. Las N fotos se mandan en UNA sola llamada al modelo (no N llamadas
   sueltas fusionadas a mano) — Vision admite varias imagenes en el mismo
   mensaje, da mejor contexto cruzado y menos alucinacion.
2. Salida estructurada con `json_schema` + `strict:true`, mismo patron que
   `LlmFacturaParser` (en vez del prompt libre actual).
3. `Ticket` pasa a tener una lista de operaciones (mismo patron que
   `Factura`/`Operacion`), cada operacion conserva referencia a que foto vino.
4. Las fotos SI se persisten esta vez (evidencia/auditoria necesaria al
   componer un ticket de varias fotos).
5. Reusar el patron `requiereRevisionManual`/`avisosImport` de facturas:
   si los importes no cuadran entre fotos, marcar a revision en vez de
   bloquear.
6. Rehacer el check de duplicados para distinguir "fotos de un ticket en
   composicion" de "ticket subido dos veces por error".

## Alcance
- Backend (`api/tecozam-bills-api`)
- PWA (`PWA/tecozam-fleet-pwa`) — captura multi-foto
- Web admin (`web/tecozam-bills-web`) — pantalla de cotejo adaptada a N
  operaciones por ticket

## TDD / verificacion
- Modo: Strict TDD (configuracion global del usuario) — aplica donde hay
  runner de tests.
- Backend: runner `./mvnw.cmd test` (JUnit) — RED-GREEN-REFACTOR real.
- PWA y web admin: **sin runner de tests unitarios configurado** (solo
  `tsc`/`eslint`, Playwright es e2e en web pero no hay suite de componentes).
  Verificacion ahi = `npx tsc --noEmit` + prueba manual en navegador (patron
  ya usado en esta sesion). Se deja constancia explicita de esta limitacion.

## Pronostico de revision (chained-pr)
Feature grande, multi-repo. Forecast inicial ~900-1200 lineas sumando las
8 tareas. Se aplicara estrategia de entrega por tarea (cada tarea = su
propio commit de work-unit; si una tarea sola supera ~400 lineas, se
evaluara partirla). Estrategia de cadena: `ask-on-risk` (por defecto).

## Tareas

- [x] T1. **Decision de diseño**: entidad nueva `TicketLinea` (no reutilizar
      `Operacion`). Motivo: `Operacion` tiene `tarjetaResumen`/`factura`
      ambas `@ManyToOne(nullable=false)` — pertenece a una Factura+
      TarjetaResumen reales que en el momento de captura en campo (antes de
      cotejar) todavia no existen. Forzar esa FK en una linea de ticket
      habria obligado a inventar una Factura/TarjetaResumen ficticia, mucho
      peor que una entidad nueva.
      Creado: `ticket/domain/TicketLinea.java` (campos escalares que hoy
      estan en `Ticket`: estacion/fechaHora/producto/numRecibo/matricula/
      kms/litros/precioLitro/importeTotal, mas `fotoIndice` para trazar de
      que foto vino — el almacenamiento real de fotos es T3, aqui solo el
      campo). `ticket/infrastructure/persistence/TicketLineaRepository.java`
      (`findByTicketId`). Migracion `V28__ticket_lineas.sql` (patron
      idempotente de V26: `IF NOT EXISTS (SELECT 1 FROM sys.tables...)
      CREATE TABLE ... END`, con FK a `tickets` e indice por `ticket_id`).
      `Ticket.java` gana `@OneToMany(mappedBy="ticket", cascade=ALL,
      orphanRemoval=true) List<TicketLinea> lineas` (patron identico al de
      `Factura.operaciones`/`FacturaDocumento`).
      **Temporal, pendiente de T3**: los campos escalares planos de `Ticket`
      (estacion, importeTotal, litros...) NO se han tocado ni eliminado —
      coexisten con `lineas` a proposito, para no romper
      `TicketService`/`TicketController`/los DTOs existentes (fuera de
      alcance de T1). T3 debe migrar la creacion a poblar `lineas` en vez de
      los escalares, y entonces valorar si los escalares se quitan o se
      quedan como "resumen" cacheado del ticket.
      TDD real: test nuevo `TicketLineaRepositoryIT` (2 casos: guardar
      ticket con 2 lineas y que ambas queden ligadas; quitar una linea de la
      lista y que `orphanRemoval` la borre). RED confirmado primero (fallo
      real: `Schema-validation: missing table [ticket_lineas]`, porque la
      migracion aun no estaba aplicada en el Docker MSSQL de dev — se aplico
      a mano con `sqlcmd` igual que V25/V26/V27 en su momento, ya que este
      proyecto tiene Flyway DESACTIVADO en el perfil `dev`
      (`application-dev.yml: spring.flyway.enabled: false` — lo aplica
      quien gestione el contenedor de dev, no el arranque de la app). GREEN
      despues: `TicketLineaRepositoryIT` 2/2. Suite completa
      `./mvnw.cmd test`: 249/249, `BUILD SUCCESS`, 0 regresiones.
      **2 hallazgos fuera de alcance de T1, no arreglados aqui**:
      (a) `application-test.yml` tiene hardcodeada la contraseña
      `Tecozam_Dev_2026!` pero el contenedor `tecozam-mssql-dev` real usa
      `DevTest_2026!` — desincronizado, bloquea CUALQUIER test que use
      `@SpringBootTest` contra este perfil hasta que alguien lo corrija o
      pase `-Dspring.datasource.password=...` a mano (lo que hice yo para
      poder verificar esta tarea).
      (b) El `pom.xml` no tiene `maven-failsafe-plugin` ni un
      `<includes>`/`<testSourceDirectory>` que capture `**/*IT.java` en
      surefire — confirmado que `./mvnw.cmd test` (sin `-Dtest=`) NO
      ejecuta NINGUNA clase `*IT.java` del proyecto (ni esta, ni
      `OperacionRepositoryIT` de la tarea T1 de `vincular-operacion-mejoras.md`,
      ni ninguna otra). Esto significa que los "330/330"/"249/249 tests, 0
      regresiones" reportados en tareas anteriores de ESTA sesion
      probablemente nunca incluyeron esas suites IT salvo cuando se
      invocaron explicitamente con `-Dtest=NombreIT`. Recomiendo una tarea
      dedicada para arreglar esto (añadir failsafe, o ampliar los
      `<includes>` de surefire) antes de confiar en "mvn test" como
      verificacion completa de los tests de integracion existentes.

      **Ambos hallazgos arreglados** (a peticion del usuario, antes de
      seguir con T2): `pom.xml` — surefire gana `<includes>` explicito con
      `**/*Test.java`, `**/*Tests.java`, `**/*TestCase.java` y `**/*IT.java`
      (no se añadio failsafe, es mas simple ampliar surefire para que
      `./mvnw.cmd test` siga siendo el unico comando). `application-test.yml`
      — contraseña corregida a `DevTest_2026!` (la real del contenedor).
      Al activarse por primera vez, `ListadoTarjetasImportServiceIT` fallo
      de verdad (`BusinessException: Proveedor no registrado: REPSOL`): el
      test asumia que los proveedores REPSOL/MOEVE_CEPSA ya existian en la
      BD compartida de dev, sin sembrarlos el mismo — arreglado con un
      nuevo `@BeforeEach` que los crea si faltan (idempotente,
      `@Transactional` lo deshace al final de cada test, no contamina la
      BD real). Suite completa final: `./mvnw.cmd test` → **258/258,
      BUILD SUCCESS, 0 errores** (249 unitarios + 9 de integracion que
      antes nunca se ejecutaban: `TicketLineaRepositoryIT` 2,
      `OperacionRepositoryIT` 1, `ListadoTarjetasImportServiceIT` 5,
      más 1 de un conteo previo ya incluido).
- [ ] T2. Backend: `LlmTicketParser` nuevo (patron `LlmFacturaParser`):
      recibe N imagenes (base64 o multipart), prompt reforzado, schema
      `json_schema strict` devolviendo lista de operaciones. Sustituye
      `TicketController.performOcr` (las dos copias duplicadas). Tests con
      fixtures reales si estan disponibles, si no, con mocks del cliente HTTP.
- [ ] T3. Backend: `/tickets/ocr-preview` y `/tickets/ocr-validado` aceptan
      `MultipartFile[]` en vez de un solo `imagen`; se persisten las N fotos
      (storage); `TicketService.createOcrValidado` crea 1 `Ticket` con sus N
      `TicketOperacion`. Tests de integracion.
- [ ] T4. Backend: rediseñar `verificarNoEsDuplicado` para distinguir fotos
      de un ticket en composicion vs duplicado accidental real. Tests.
- [ ] T5. Backend: validacion de plausibilidad entre operaciones del mismo
      ticket (patron `FacturaImportValidator`), marca `requiereRevisionManual`
      si no cuadran. Tests.
- [ ] T6. Backend: adaptar `cotejarPendientes`/`detectarDiscrepancia` para
      matching contra N operaciones por ticket (hoy asume 1). Tests.
- [ ] T7. PWA: `app/escanear/page.tsx` — estado `imageBlob` pasa a array de
      blobs; UI para añadir mas fotos antes de avanzar a `ocr-edit`; envio de
      las N imagenes a los endpoints actualizados; `EditableOcrData` pasa a
      lista editable de operaciones. Verificacion: `tsc --noEmit` + prueba
      manual en navegador.
- [ ] T8. Web admin: pantalla de tickets (`tickets/page.tsx`) adaptada para
      mostrar/cotejar N operaciones por ticket en vez de 1. Verificacion:
      `tsc --noEmit` + prueba manual.

## Progreso
(se actualiza tarea a tarea, con commit de work-unit y evidencia de
verificacion por tarea)
