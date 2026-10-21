# Clasificación automática TARJETA/VIAT por patrón de número (Repsol) + aviso con serie (Cepsa)

## Objetivo
Arreglar el import de listado de tarjetas para que, al reimportar
`LISTADO TARJETAS REPSOL.xlsx` y `LISTADO TARJETAS MOEVE CEPSA.xlsx` (ficheros
reales, proporcionados por el usuario), los dispositivos ViaT no se metan como
tarjetas de combustible ni viceversa.

## Por qué
Reproducido en sesión: al importar el listado real de Repsol (591 tarjetas,
concepto vacío en 96 filas), 0 ViaTs se crearon y 2 de esas 96 filas
(`0972401002002270964`, `0972401002001099309`) son ViaT reales — confirmado
porque el mismo conductor (M. VICENTE, C. GOMEZ) tiene tarjeta normal
(prefijo `0007078833651...`) Y dispositivo con prefijo distinto.

En Cepsa/Moeve se investigó la misma hipótesis (patrón de número) y NO es
fiable: las dos series de prefijo detectadas mezclan conceptos de combustible
y de peaje, y el 92% de filas (320/347) no tienen concepto. Confirmado con el
mismo patrón "mismo conductor, dos tarjetas" (6071MCX: una con concepto
"CUOTA OBE/VIA-T", otra con "ACEITES/LUBES"), pero sin que el prefijo separe
limpiamente los casos.

## Decisión (usuario, tras ver evidencia real de ambos ficheros)
- Repsol: clasificación automática por patrón de número, cuando el concepto
  viene vacío/desconocido. Señal: `TarjetaNumeroNormalizer` ya documenta que
  todo número de tarjeta Repsol empieza por el prefijo `0007`. Si una fila
  declarada TARJETA (lote `esViat=false`) tiene concepto desconocido Y su
  número NO empieza por `0007`, reclasificar automáticamente esa fila a VIAT
  — con traza clara en el reporte, nunca en silencio.
- Moeve/Cepsa: NO automatizar (no hay señal fiable). En su lugar, enriquecer
  el mensaje ya existente en `errores` para las filas con concepto vacío
  añadiendo la serie/prefijo del número, para que el admin pueda agrupar y
  revisar visualmente sin tener que adivinar fila a fila sin contexto.
- Respeta la decisión histórica (`odd/tasks/import-llm-switch.md`, tarea 7):
  nunca decidir el tipo por el texto del concepto (keywords); la nueva señal
  es estructural (prefijo de número), no semántica, y solo actúa cuando hay
  evidencia fuerte y documentada (el prefijo Repsol ya es una invariante de
  dominio usada en otro punto del código).

## Alcance
1. `tarjeta/domain/TarjetaNumeroNormalizer.java`: nuevo método público
   `boolean esNumeroTarjetaRepsol(String raw)` — true si el número (tras quitar
   espacios) empieza por el prefijo Repsol conocido (`0007`). Reutiliza la
   constante existente, no la duplica.
2. `admin/application/ListadoTarjetasImportService.java` (método que decide
   persistir tarjeta vs viat, hoy lee `fila.tipo()` directo): calcular un tipo
   efectivo por fila — si `fila.tipo()==TARJETA`, `!fila.conceptoConocido()`,
   proveedor es Repsol/Solred y el número no pasa `esNumeroTarjetaRepsol`,
   el tipo efectivo pasa a VIAT. Añadir contador nuevo (p.ej.
   `reclasificadosPorNumero`) en `ImportContext`, incrementarlo en vez de
   `filasIgnoradas` para estas filas, y añadir una línea a `errores` con
   mensaje distinto y claro (p.ej. "... reclasificado automáticamente como
   VIAT · número fuera del prefijo Repsol conocido (0007)").
3. Mismo método: para filas con concepto desconocido que NO se reclasifican
   (Cepsa, o Repsol que sí cumple el prefijo), enriquecer el mensaje existente
   de `errores` añadiendo la serie del número (primeros N caracteres, p.ej. 10)
   para que el admin pueda agrupar visualmente. No añadir campos nuevos al DTO
   si no hace falta — mensaje de texto es suficiente.
4. `admin/dto/ImportTarjetasReportDTO.java`: añadir el campo
   `reclasificadosPorNumero` (int) si no se puede derivar ya de otro dato.
5. Frontend `import-tarjetas-dialog.tsx`: si hay `reclasificadosPorNumero > 0`,
   mostrar una línea informativa adicional (igual que las existentes
   "Tarjetas"/"VIATs"/etc.), y revisar que el contador "N filas procesadas
   como TARJETA por defecto" siga siendo correcto (hoy cuenta
   `errores.length`; si ahora `errores` incluye también mensajes de
   reclasificación-a-VIAT exitosa, ese conteo dejaría de ser "procesadas como
   TARJETA" — corregir para que cuente solo `filasIgnoradas`, no
   `errores.length`).
6. Tests (TDD, JUnit5+AssertJ, patrón existente del proyecto — ver
   `RepsolXlsxRowParserTest`, `CepsaXlsxRowParserTest`,
   `ListadoTarjetasImportServiceIT` si existe): casos nuevos para Repsol
   (concepto vacío + prefijo correcto → TARJETA sin cambios; concepto vacío +
   prefijo distinto → VIAT con contador y mensaje nuevos) y confirmar que
   Cepsa/Moeve no cambia de comportamiento salvo el texto del mensaje.
7. Verificar con los dos ficheros reales del usuario tras el fix (repetir el
   ciclo borrar-tarjetas → importar Excel que ya se hizo en sesión, para
   Repsol y para Moeve/Cepsa) y confirmar manualmente que las 2 ViaT de
   Repsol ya aparecen en /maestros/viats.

## Fuera de alcance
- Clasificación automática por patrón de número para Cepsa/Moeve (sin señal
  fiable, descartado explícitamente por el usuario tras ver la evidencia).
- Tocar el import "solo ViaT" puro (`viat/infrastructure/import_/*`,
  independiente de este flujo).
- Tocar `tarjeta/infrastructure/import_/TarjetaXlsxRowParser.java` /
  `TarjetaImportController` (ruta de alta de tarjeta maestro 1 a 1, feature
  distinta, no relacionada con este bug).

## TDD
Modo: activo para este repo según convención existente (tests unitarios por
componente, happy+error, sin mocks en parsers, Mockito en service/IT). Seguir
RED→GREEN para los casos nuevos de clasificación por prefijo.

## Bug adicional encontrado al verificar en real (autorizado por el usuario)

Al verificar el fix en vivo, tras borrar-todas-e-reimportar, las tarjetas
"reactivadas" (encontradas por número pero previamente soft-deleted)
desaparecieron del listado real ~30 min después / en el siguiente login
limpio, aunque el modal de import las contaba como "ya existían". Investigado
y confirmado por código (no fue un reset del entorno, descartado con el
usuario): `materializarTarjeta`/`materializarViat`/`materializarCentroCoste`
en `ListadoTarjetasImportService.java` encuentran el registro existente por
`findByNumeroTarjeta`/`findByCodigo`/`findByCodigo` (sin filtrar
`eliminadoEn`), lo cachean y cuentan como "existente", pero **nunca llaman
`.restore()` ni hacen `save()`** — si el registro encontrado estaba
soft-deleted (`eliminadoEn` con fecha, como pasa justo después de un borrado
masivo), se queda invisible para siempre en el listado real
(`findByEliminadoEnIsNull`/`findByActivaTrueAndEliminadoEnIsNull`), aunque el
import lo cuente como recuperado. `restore()` existe en `BaseEntity` pero no
se invoca en ningún punto de todo el código fuente (confirmado por grep).

### Alcance del arreglo
8. En las tres ramas "ya existía" de `materializarTarjeta`, `materializarViat`
   y `materializarCentroCoste` (`ListadoTarjetasImportService.java`): si la
   entidad encontrada tiene `isEliminado()==true`, llamar `.restore()` y
   persistir (`save()`) antes de cachear/contar, para que vuelva a ser visible
   de verdad en el listado real. Si no estaba eliminada, comportamiento sin
   cambios (no hacer `save()` innecesario).
9. Test nuevo (TDD): fixture con una tarjeta/viat/centro de coste
   soft-deleted (`eliminadoEn` seteado) cuyo número/código coincide con una
   fila del Excel — verificar que tras importar, `eliminadoEn` queda `null` y
   el registro aparece en `findByEliminadoEnIsNull()` (o el método de listado
   real correspondiente), no solo en el contador del reporte.
10. Verificar con los dos ficheros reales (ciclo borrar-todo → importar →
    **esperar unos minutos y volver a consultar el listado en una sesión
    nueva** antes de dar por buena la verificación, para no repetir el mismo
    error de validar solo con el modal del import) que las tarjetas/ViaTs
    quedan visibles de forma persistente.

### Progreso (bug adicional)
- [x] 8. `restore()` + `save()` en las tres ramas "ya existía"
      `ListadoTarjetasImportService.java`: en `materializarCentroCoste` (~línea
      186), `materializarTarjeta` (~línea 228) y `materializarViat` (~línea
      253), la rama "ya existía" ahora comprueba `existente.isEliminado()`;
      si es `true` llama `.restore()` y hace `save()` de esa misma entidad
      (reutilizando el repo ya inyectado en cada método) ANTES de
      cachearla/contarla. Si no estaba eliminada, cero cambios de
      comportamiento (no se añade ningún `save()` extra en ese camino).
- [x] 9. Test nuevo cubriendo reactivación real (TDD RED→GREEN)
      Tres tests nuevos en `ListadoTarjetasImportServiceIT.java`
      (`repsolTarjetaSoftDeletedSeRestauraAlReimportar`,
      `cepsaViatSoftDeletedSeRestauraAlReimportar`,
      `centroCosteSoftDeletedSeRestauraAlReimportar`): cada uno crea el
      registro ya en BD con `softDelete()` en el setup, reimporta un Excel
      cuya fila coincide por número/código, y verifica que tras importar
      `isEliminado()==false` y el registro aparece en
      `findByEliminadoEnIsNull()` (no solo que el contador "existente" del
      reporte lo cuente). Nueva fixture
      `XlsxFixtureBuilder.repsolConCentroCosteSoftDeleted()` (centro de coste
      con código exclusivo de test `CC-SOFT-DELETE-TEST`, para no chocar con
      datos reales de BD). RED confirmado revirtiendo temporalmente el fix de
      la tarea 8 (`git stash` del fichero de servicio) y ejecutando solo los
      3 tests nuevos: `Tests run: 3, Failures: 3, Errors: 0` (los 3 fallan
      con `Expecting value to be false but was true` sobre `isEliminado()`).
      Restaurado el fix (`git stash pop`) → GREEN.
      Evidencia final, suite completa: `./mvnw.cmd -o test` →
      `Tests run: 378, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS`
      (375 anteriores + 3 nuevos, 0 regresiones).
- [x] 10. Verificación con espera real (no solo el modal del import)
      Desplegado (merge+push a main, autodeploy). Repetido en vivo contra
      bills.z-innova.com: borrar-todo → importar Repsol (119 "ya existían",
      0 nuevas) → importar Cepsa (347 "ya existían", 0 nuevas). Comprobado
      en navegación fresca justo después (466 tarjetas, 2 VIATs) Y de nuevo
      tras ~5 minutos de espera con sesión aún activa (mismos 466 + 2,
      sin caer). Esta vez sí persiste: el fix de restore() es correcto.
      Pendiente — lo hace el orquestador con Playwright contra los ficheros
      reales, no cubierto por este ciclo de implementación.

## Progreso
- [x] 1. `TarjetaNumeroNormalizer.esNumeroTarjetaRepsol`
      Nuevo método público `esNumeroTarjetaRepsol(String raw)`: quita espacios
      y comprueba el prefijo `PREFIJO_REPSOL` ya existente (reutilizado, no
      duplicado). `null`/blank → `false` sin lanzar excepción (es predicado,
      no validación de entrada como `canonical`). TDD: tests añadidos en
      `TarjetaNumeroNormalizerTest` (RED confirmado por fallo de compilación
      antes de implementar), luego GREEN.
- [x] 2. Reclasificación automática Repsol en `ListadoTarjetasImportService`
      `materializar()` ahora calcula `tipoEfectivo` en vez de usar
      `fila.tipo()` directo. Si `fila.tipo()==TARJETA`, concepto desconocido,
      proveedor resuelto es REPSOL (cubre alias SOLRED vía
      `resolverProveedor`) y `!esNumeroTarjetaRepsol(numero)` → tipoEfectivo
      pasa a VIAT, se incrementa el contador nuevo `reclasificadosPorNumero`
      (en vez de `filasIgnoradas`) y se añade a `errores` un mensaje distinto:
      "Tarjeta {numero} — reclasificado automáticamente como VIAT · número
      fuera del prefijo Repsol conocido (0007)".
- [x] 3. Mensaje enriquecido con serie para filas no reclasificadas (Cepsa)
      Para el resto de filas con concepto desconocido (Cepsa/Moeve siempre, y
      Repsol cuando el número sí tiene el prefijo) se mantiene el motivo
      original y se añade `· serie {primeros 10 chars del número sin
      espacios}` vía el helper privado `serieDe(String)`.
- [x] 4. DTO `reclasificadosPorNumero`
      Campo `int reclasificadosPorNumero` añadido a `ImportTarjetasReportDTO`
      (entre `filasIgnoradas` y `errores`). Ajustado el único otro
      constructor existente del record (`RepsolImportService`, ruta legacy
      no tocada funcionalmente) con `0` y comentario explicando por qué.
- [x] 5. Frontend: línea informativa + corrección del conteo de "procesadas
      como TARJETA por defecto"
      `import-tarjetas-dialog.tsx`: añadido `reclasificadosPorNumero` a la
      interfaz; nueva línea informativa (estilo propio, azul/sky) cuando
      `reclasificadosPorNumero > 0`; corregido el contador del badge ámbar de
      `errores.length` a `filasIgnoradas` (con comentario explicando que
      `errores` ahora también incluye mensajes de reclasificación exitosa).
      `npx tsc --noEmit` compila limpio.
- [x] 6. Tests nuevos + verificación 0 regresiones
      Nuevos tests en `ListadoTarjetasImportServiceIT` (fixture nueva
      `XlsxFixtureBuilder.repsolConViatPorNumero()` con los dos números reales
      de la sesión: `0007078833651673937` con prefijo y `0972401002002270964`
      sin prefijo, mismo conductor "MANUEL VICENTE PAZ"): reclasificación
      Repsol sin prefijo → VIAT; Repsol con prefijo → sin cambios; Cepsa →
      sin reclasificar, solo mensaje con serie. Evidencia:
      `./mvnw.cmd -o test` → `Tests run: 375, Failures: 0, Errors: 0` /
      `BUILD SUCCESS`.
- [x] 7. Verificación manual con los dos ficheros reales (Repsol y Moeve/Cepsa)
      Desplegado el fix (merge a `main` en ambos repos, push, autodeploy
      Dokploy) y repetido en vivo contra bills.z-innova.com el ciclo
      borrar-tarjetas → importar Excel:
      - Repsol (`LISTADO TARJETAS REPSOL.xlsx`): "2 tarjetas reclasificadas
        automáticamente como VIAT (número fuera del prefijo Repsol
        conocido)"; aviso bajó de 96 a 94 filas "procesadas como TARJETA por
        defecto"; confirmado en /maestros/viats que los 2 ViaT reales
        (`0972401002001099309` C. GOMEZ, `0972401002002270964` M.VICENTE)
        aparecen correctamente, con nombre de conductor real.
      - Moeve/Cepsa (`LISTADO TARJETAS MOEVE CEPSA.xlsx`): 0 reclasificados
        (correcto, sin señal fiable), 320 filas con concepto vacío ahora
        muestran su serie (`7076460769` / `7080110080` / etc.) en el detalle
        por fila, sin ningún falso positivo a VIAT.
