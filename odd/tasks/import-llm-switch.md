# Interruptor global regex/LLM para import (maestros y facturas/extractos)

## Objetivo
Añadir un interruptor centralizado, solo ADMIN, que cuando está ENCENDIDO hace que
la extracción de campos en los imports (listado de tarjetas/VIATs desde Excel, y
facturas + extracto desde PDF) la haga un LLM en vez de los parsers deterministas
actuales. Cuando está APAGADO (por defecto), el comportamiento es exactamente el
actual: cero cambios.

## Por qué
El usuario propuso el interruptor tras la investigación de falsos positivos VIAT
(causa: `ConceptoClassifier` clasifica por palabras clave y falla con conceptos
ambiguos como "COMISION AUTOPISTAS"). Se amplió el alcance a extracción completa
de campos (no solo clasificación) en ambos pipelines de import.

## Decisión de seguridad (bloqueante, no negociable)
Extraer importes/fechas/litros/números de tarjeta vía LLM en un sistema de
conciliación financiera es peligroso si el LLM puede alucinar una cifra sin que
nadie se entere — el propio cotejo compararía un ticket real contra un dato de
factura inventado. Por eso el modo LLM NUNCA persiste directo: cada resultado
pasa por una validación cruzada determinista y, si algo no cuadra, la fila queda
marcada como "requiere revisión manual" en vez de entrar al cotejo automático.
En modo regex (por defecto) el comportamiento no cambia — los avisos siguen
siendo informativos, no bloqueantes, como hoy.

## Alcance
1. **Config centralizada**: una tabla `configuracion_import` (fila única),
   boolean `modoLlmActivo`, gestionada por `ConfiguracionImportService` +
   `ConfiguracionImportController` (GET/PUT, solo ADMIN).
2. **Pipeline facturas** (`factura/infrastructure/parser/`): nuevo
   `LlmFacturaParser implements FacturaParser`, reutiliza el cliente OpenAI ya
   existente en `AsistenteService` (mismo patrón: `HttpClient` JDK,
   `app.openai.api-key`/`app.openai.model`). `FacturaParserFactory` elige entre
   el parser determinista y el LLM según el switch. Extiende
   `FacturaImportValidator` con un nuevo chequeo: total de cabecera vs suma de
   importes extraídos. Si hay avisos en modo LLM → no persistir tal cual, marcar
   revisión manual (nuevo campo en `Factura`, p.ej. `requiereRevisionManual`).
3. **Pipeline listado tarjetas** (`admin/infrastructure/import_/`): nuevo
   `LlmListadoTarjetasRowParser` (o equivalente a nivel de hoja completa, para
   evitar una llamada LLM por fila — batch por fichero). Cross-check: si el LLM
   clasifica TARJETA/VIAT en un sentido y `ConceptoClassifier` (regex) en otro,
   marcar para revisión en vez de decidir en silencio.
4. **Frontend**: nueva página `web/tecozam-bills-web/src/app/(dashboard)/configuracion/import/page.tsx`
   (junto a `configuracion/usuarios`), con el toggle y explicación de qué hace.
5. **Tests**: preservar los 80 tests existentes (`RepsolFacturaParserTest` 11,
   `MoeveFacturaParserTest` 49, `RepsolXlsxRowParserTest` 4, `CepsaXlsxRowParserTest` 5,
   `ConceptoClassifierTest` 4, `FacturaImportValidatorTest` 7) sin tocarlos —
   son la red de regresión del modo regex, que no cambia. Tests nuevos para
   cada componente añadido.

## Fuera de alcance (explícitamente descartado)
- Extracción por LLM de valores numéricos SIN red de validación (rechazado por
  riesgo de silent-corruption de datos financieros).
- Interruptor por ventana/pantalla en vez de centralizado (rechazado por el
  usuario para evitar desincronización entre pipelines).

## Progreso

- [x] 1. Backend: entidad + migración + repo + service + controller `ConfiguracionImport`
- [x] 2. Backend: `LlmFacturaParser` + extensión de `FacturaImportValidator` (chequeo de total) + campo `requiereRevisionManual` en `Factura`
- [x] 3. Backend: `LlmListadoTarjetasParser` (batch por hoja) + cross-check con `ConceptoClassifier`
- [x] 4. Frontend: página `configuracion/import` con el toggle
- [x] 5. Wiring: `FacturaParserFactory` y `ListadoTarjetasImportService` leen el switch y eligen parser (resuelto dentro de las tareas 2 y 3)
- [x] 6. **Bug real de archivos reales, arreglado**: `LlmListadoTarjetasParser.parsearHoja` ahora trocea la hoja en lotes de `TAMANO_LOTE=30` filas (no una llamada por hoja completa), y compara los `numero` de tarjeta enviados vs devueltos por lote — cualquier fila que el LLM se salte se reporta en `ResultadoParseoLlm.numerosPerdidos()` (el llamador los añade a `filasParaRevision`, ya no se pierden en silencio) y queda logueada con `log.warn`. Validado con el Excel real de Moeve/Cepsa (347 filas): antes del fix solo llegaban 5 (1,4%), después llegan 343 (98,8%), con las 4 restantes correctamente identificadas por número en vez de desaparecer. También se corrigió la causa real de la alucinación de `periodoDesde`/`periodoHasta` en `LlmFacturaParser`: el schema JSON los marcaba como `string` no-nulo (a diferencia de `vencimiento`/`numCuenta`/etc.), así que bajo `strict:true` el modelo no podía devolver `null` aunque el prompt se lo pidiera — se corrigió el schema (ahora nullable, igual que los demás opcionales) y se reforzó el prompt de forma explícita contra inventar/derivar esos campos. 258/258 tests, 0 regresiones.

## Cambio de alcance tras validar con datos reales (decisión del usuario)

Tras validar el modo LLM con archivos reales, se decidió que **la clasificación semántica TARJETA/VIAT no compensa el riesgo/complejidad que introduce** (alucinaciones, truncamiento silencioso, coste, latencia) frente a una alternativa mucho más simple: que el ADMIN declare explícitamente, al importar, si ese lote son dispositivos VIAT o tarjetas normales — eliminando la necesidad de adivinar por palabras clave (regex o LLM) por completo para esa decisión.

**El interruptor LLM se queda, pero su alcance se reduce**: solo afecta ya al pipeline de **facturas** (`LlmFacturaParser`, para formatos de factura nuevos que el regex no reconozca). El pipeline de **listado de tarjetas** deja de usar LLM del todo.

- [x] 7. **Reemplazada la clasificación automática (regex y LLM) por un toggle explícito del admin** en el import de listado de tarjetas:
  - Backend: `ListadoTarjetasImportController`/`ListadoTarjetasImportService.importar(file, codigoProveedor, esViat)` reciben el nuevo parámetro `esViat: boolean` (del admin, no inferido). Todas las filas del lote usan `tipoLote = esViat ? VIAT : TARJETA`.
  - `ListadoTarjetasRowParser.parse(...)` cambia de firma para recibir `tipoLote`; `RepsolXlsxRowParser`/`CepsaXlsxRowParser` ya no llaman a ningún clasificador de conceptos para decidir tipo.
  - **Eliminados por completo** (código muerto): `ConceptoClassifier.java`/`ConceptoClassifierTest.java`, `LlmListadoTarjetasParser.java`/`LlmListadoTarjetasParserTest.java`, la rama `importarConLlm`, el test `ListadoTarjetasImportServiceTest` (solo cubría esa rama), y el campo `filasParaRevision` de `ImportTarjetasReportDTO`.
  - **Conservado y renombrado** (no era código de clasificación, sino de calidad de dato): `ConceptoClassifier.esConceptoConocido(...)` pasa a `ConceptoRecognizer.esConceptoConocido(...)` — sigue alimentando `filasIgnoradas` para que el admin detecte conceptos raros en el Excel, independientemente del tipo declarado.
  - Frontend: checkbox "Este listado son dispositivos VIAT" en `ImportTarjetasDialog` (por defecto desmarcado); texto de `configuracion/import` corregido para dejar claro que el interruptor LLM ya solo afecta a facturas.
  - `RepsolXlsxRowParserTest`/`CepsaXlsxRowParserTest` actualizados (el tipo viene del parámetro, ya no del concepto — incluye test explícito de que "AUTOPISTAS"/"PORTAGEM" ya no se adivinan como VIAT). `ListadoTarjetasImportServiceIT` actualizado a la nueva firma con casos `esViat=false`/`esViat=true`.
  - Verificado: backend 249/249 tests, `BUILD SUCCESS`; frontend `tsc --noEmit` limpio.

## Verificación
- Backend: `./mvnw.cmd test` (proyecto completo, confirmar 0 regresiones sobre los 239 tests actuales)
- Frontend: `npx tsc --noEmit`
- TDD: no confirmado explícitamente por el usuario esta sesión — se aplican tests unitarios estándar por componente (patrón ya usado en toda la sesión: Mockito + casos happy/error), sin exigir RED-GREEN-REFACTOR estricto salvo que se indique lo contrario.
