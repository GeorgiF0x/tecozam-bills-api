# Paquete para Diego: arreglos de cotejo + parte B

Objetivo: dejar listos en un solo envío los arreglos del cotejo y las mejoras B (backend + web), con la guía para que Diego sincronice los forks y actualice el servidor. Rama `feat/paquete-cotejo-b` en `tecozam-bills-api` y `tecozam-bills-web`. Estrategia de entrega: `exception-ok`: el proyecto entrega por `main` (convención del repo) y el push lo decide el usuario. Runners: `./mvnw test -Dtest=...` (unitarios; los `*IT` necesitan el MSSQL local de Docker) y Playwright con la API simulada (`ADMIN_BASE_URL=http://localhost:3200 npx playwright test <spec> --project=admin`).

## Specs

Contexto del cliente (literal): «Estamos teniendo mucho problema a la hora de cotejar el ticket con la operacion facturada». Caso real: ticket #508 (E.S. PUERTA DE ASTORGA, 06/10/2026 08:54, tarjeta ···2025, 107,00 €, 53,39 L) en INCIDENCIA «Operación no existe», con observación «Auto-detectado: no se encontró operación coincidente en ±24h». La operación existe en el Excel de Cepsa (`incidencia_cotejo/transacciones-cepsa-25-11-2026 (1).xlsx`: 06/10/2026 08:52:54, tarjeta 708011008022412025, 107 €, 53,39 L, factura BA72400000625263). El usuario dijo: «me han enviado un tiket y la factura donde esta la operacion que pone que no existe pero al parecer si». Incidencia nº5 del 19/11: «Que el buscador recopile todos los datos de las facturas y se pueda realizar una búsqueda más extensa».

- **S1 (cotejo 1)**: «Volver a revisar los tickets en INCIDENCIA "operación no existe" automática y los SIN_COINCIDENCIA cuando se importa una factura. Así el #508 se cotejaría solo en cuanto su operación esté disponible. Los que haya marcado una persona no se tocan.» Criterios:
  - `cotejarPendientes()` revisa también los tickets en SIN_COINCIDENCIA y los que están en INCIDENCIA con `tipoIncidencia` OPERACION_NO_EXISTE o SIN_COTEJAR_1_MES, observaciones que empiezan por «Auto-detectado» y `resueltoEn` nulo.
  - En esos tickets reintentados solo hay cambio si aparece una candidata en ±2h: se aplica el resultado normal del cotejo y, si queda COTEJADO, se limpian el tipo de incidencia y la observación automática. Si no aparece nada, el ticket no se toca (nunca empeora).
  - Los PENDIENTE siguen igual que ahora.
- **S2 (cotejo 2)**: «Si no hay coincidencia con el número de tarjeta exacto, buscar por los últimos 4 dígitos, con el desempate por importe que ya existe.»
- **S3 (cotejo 3)**: «Botón "Marcar como revisada" en el detalle de una factura con avisos, que la devuelve al cotejo y vuelve a cotejar los pendientes.» Criterios:
  - Endpoint `PATCH /api/facturas/{id}/revisada` (ADMIN, GESTOR): pone `requiereRevisionManual=false`, ejecuta `cotejarPendientes()` y devuelve el `FacturaDTO`.
  - `FacturaDTO` expone `requiereRevisionManual`.
  - En la web, el detalle de la factura muestra el aviso y el botón; el listado marca las facturas que están fuera del cotejo.
- **S4 (B1)**: «Vincular operación: filtro por rango de importe y de litros.» Parámetros nuevos de `/api/operaciones`: `importeMin`, `importeMax` (sobre `importeTotal`), `litrosMin` y `litrosMax` (sobre `cantidad`). Más los campos correspondientes en la ventana de vincular.
- **S5 (B2)**: «Vincular operación: que la búsqueda de texto encuentre también número de tarjeta, conductor y referencia.» `q` busca además en `tarjetaResumen.numTarjeta`, `tarjetaResumen.conductor` y `referencia`. El placeholder de la web lo indica.
- **S6 (B3)**: «Vehículo de sustitución: al dar de alta, comparar matrículas sin tener en cuenta guiones ni espacios, para no crear duplicados como "1234ABC" y "1234-ABC".»
- **S7 (B4)**: «Vehículo de sustitución: reconocer una matrícula ya registrada aunque esté prestada a otro operario. Hoy da "ya existe" y el operario se queda sin salida.»
  - *Supuesto, pendiente de confirmar con el usuario:* el backend devuelve un mensaje claro que dice que está registrada, en qué estado está y que avise al gestor. No reasigna el préstamo automáticamente: cambiar préstamos ajenos es una decisión de negocio que no se ha tomado.
- **S8 (B5)**: «Mensaje del servidor: cambiar "es obligatorio adjuntar el extracto PDF" por "extracto (PDF o Excel)".»
- **S9**: Guía para Diego en un solo envío: sincronizar los dos forks (`dfersut/tecozam-bills-web`, `dfersut/tecozam-fleet-pwa`) y actualizar el servidor.

## Tasks

- T1 · S1, S2, S3-api, S4-api, S5, S6, S7, S8 · delegado (writer API, en paralelo con T2) · hecho, sin commit: S1-S6 y S8 por el writer; S7 inline por el padre (constructor nuevo `DuplicateResourceException(message, field)`); suite `./mvnw test` 491/491
- T2 · S3-web, S4-web, S5-web · delegado (writer WEB, en paralelo con T1) · hecho, sin commit: Playwright 19/19 (RED→GREEN en 6 tests nuevos), tsc 0, build OK, eslint solo con el error previo `tickets/page.tsx:1588`
- T3 · S1-S3 · revisión independiente del cotejo (agente `tecozam-cotejo-reviewer`; ítem de riesgo 1: cambia estados de tickets guardados) · hecha: 1 🔴 y 4 🟡 → lote de corrección en curso (writer API)
- T4 · S4-S6 · consultas JPQL contra MSSQL real · hecho: `OperacionRepositoryIT`, `VehiculoRepositoryIT` y E2E real con Playwright
- T5 · S9 · guía PDF en `GUIAS/` · inline · hecho: `GUIAS/GUIA-ACTUALIZACION-2026-11-25.pdf` (v1.0.20, TBM-2026-022, 4 págs.), fuente en `GUIAS/fuentes/`

## Log

- L1 (2026-11-25, usuario): «vale nos esperamos , entonces vamos a terminar los cambios para enviar todo junto y no liarle». A la pregunta de si se incluían también los arreglos del cotejo, mandó `incidencia_cotejo/ticket.png` y el Excel de Cepsa. Después: «pero estan hechos los arreglos ya?». Y a «¿Empiezo con todo (puntos 1-5 y los tres del cotejo) para mandarlo en un solo paquete?»: «si».
- L2: Diagnóstico del #508. Todos los datos del ticket coinciden con la operación. Tres causas posibles, las tres confirmadas en el código:
  1. Operación ausente cuando corrió el cotejo, y el ticket ya no se vuelve a revisar (`TicketService.java:303`).
  2. Factura importada en modo IA con avisos: queda con `requiereRevisionManual=true` para siempre (`FacturaService.java:190-191`) y fuera de todas las consultas de cotejo.
  3. Solo se busca por número de tarjeta exacto (`TicketService.java:519-522`).
  El número de tarjeta coincide en el listado (708011008022412025). No se sabe cuál de las tres causas ocurrió: el cliente solo mandó la captura y el Excel.
- L3 (2026-11-25): Engram arreglado con `.engram/config.json` (proyecto `tecozam-bills`). Copia en Engram: topic `odd/paquete-cotejo-b/tasks` (obs 1468). Docker y `tecozam-mssql-dev` arrancados para las IT. T1 y T2 lanzados en paralelo.
- L4 (2026-11-25): T2 entregado. Avisos del writer:
  (a) sin texto ni fechas sigue aplicándose la ventana automática de ±30 días aunque haya filtro de importe o litros; queda pendiente de decidir si un rango de importe o litros debe soltarla, como hace el texto;
  (b) también se invalida `["tickets"]` al marcar una factura como revisada;
  (c) el botón también se ve con el rol CONSULTA y el backend responde 403.
- L5 (2026-11-25): Añadidos por el padre, con RED→GREEN:
  - S7: mensaje 409 «La matrícula X ya está registrada (estado: Prestado). Avisa a tu gestor…».
  - Limpieza de «no existe» también cuando un reintento pasa a MULTIPLE.
  - `SecurityAccessControlIT`: CAMPO recibe 403 en `PATCH /revisada`; GESTOR pasa el filtro (404 con factura inexistente).
  Suite completa: 491/491.
- L6 (2026-11-25): E2E real con Playwright (web 3000 + backend 8090 + MSSQL), con escenario sembrado ZZTEST:
  - La factura sale marcada «Fuera del cotejo».
  - «Re-cotejar» antes de revisar: 0 cotejados.
  - «Marcar como revisada»: ticket A (copia del #508) COTEJADO con ZZREF-0001 y limpio; ticket B (incidencia humana) intacto; ticket C (SIN_COINCIDENCIA) COTEJADO con ZZREF-0003; flag de la factura a 0.
  - Filtros de vincular OK: importe 49 a 51,5 (con coma), litros 24 a 26, `q` por conductor, referencia y tarjeta; una operación ya vinculada no aparece.
  - Horas desplazadas +2h: es un artefacto de la siembra SQL directa, porque `hibernate.jdbc.time_zone=UTC`; no es un fallo.
  - Datos limpiados y servidores parados.
  - Capturas en el scratchpad: `e2e-1..4`.
- L7 (2026-11-25): Revisión independiente del cotejo.
  - 🔴 El fallback por últimos 4 dígitos no comprueba que la operación sea de la misma tarjeta, y puede robar la operación de otra tarjeta con el mismo sufijo.
  - 🟡 Con tarjeta formal, los últimos 4 deben salir de ella y no del texto libre.
  - 🟡 `resueltoEn` no se limpia cuando el sistema reabre una incidencia automática, así que nunca se reintenta.
  - 🟡 El reintento pisa la reasignación hecha por un humano.
  - 🟡 `PATCH /revisada`: el cotejo va en la misma transacción; si falla devuelve 500 y no guarda el flag.
  - ❓ Falta un IT de exclusividad en una misma pasada.

  Lote de corrección (uno solo, según el protocolo) enviado al writer API.

  **Aplazado, no bloqueante:**
  - Cargar los reintentables filtrando en JPQL (rendimiento).
  - El import también ejecuta el cotejo en su transacción (ya pasaba antes).
  - Las altas de vehículo del gestor y la importación de vehículos siguen comparando la matrícula exacta.
  - Normalizar también puntos y otros separadores en las matrículas.
  - Contador de reintentados.
  - Las notas del operario se sustituyen en vez de añadirse (ya pasaba antes).
  - Concurrencia con RCSI (ya pasaba antes).

  **Para la guía S9:**
  - Los tickets atrasados se recuperan en el siguiente cotejo.
  - Las facturas ya apartadas en producción hay que marcarlas como revisadas una a una.
- L8 (2026-11-25): Lote de corrección aplicado por el writer API: suite 502/502 e IT de exclusividad en MSSQL. Recomprobación del revisor: 6 de 7 RESUELTOS.

  El punto 1 está RESUELTO para el escenario reportado, pero la guardia introduce un hueco nuevo (🟡): acepta resúmenes con tarjeta nula. Eso solo ocurre con las tarjetas reclasificadas a VIAT (`ReclasificarTarjetasAViatService.java:161`), así que un ticket de combustible podría vincularse a un peaje con el mismo sufijo. Arreglo propuesto: con Tarjeta formal, aceptar solo el mismo id. Además, 🔵 el comentario de `ReclasificarTarjetasAViatService.java:158-159` ya no es cierto.

  Es un hallazgo nuevo tras la corrección, así que según el protocolo no hay segunda corrección automática: queda como **Necesita tu decisión**.

  ❓ Si el maestro tiene una tarjeta duplicada antigua, la guardia rechaza la operación de su propia tarjeta física. Ya pasaba antes; tiene que ver con el pendiente de las tarjetas autocreadas.
