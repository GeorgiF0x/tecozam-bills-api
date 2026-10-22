# Release Notes v1.0.11 + Guía de actualización (handoff a Docker/Flyway manual)

## Objetivo

Cerrar la entrega pendiente para Tecozam: Release Notes v1.0.11 y Guía de Actualización
actualizadas, cubriendo **todo lo acumulado desde la v1.0.10** (cutoff real: commit
`68d8158` en api, 2026-10-14 02:23:51 — no la medianoche, el corte real es cuando se
generó el PDF de la v1.0.10, 2026-10-14 03:03) hasta hoy (2026-10-22), en los tres repos
(api, web, PWA). El cliente despliega el backend con Docker manual (no Dokploy), así que
la guía debe mantener esa sección tal cual (git pull + docker build + docker run/compose,
con el volumen `tecozam-uploads` ya corregido) e incorporar las 3 migraciones Flyway
nuevas (V31, V32, V33).

**Why:** Tecozam asume el mantenimiento de la app; el usuario pidió estos documentos el
2026-10-21 tras la reunión (ver memoria `pendiente-entrega-documentacion-handoff`), y hoy
(2026-10-22) confirmó explícitamente que el alcance es "desde lo acumulado" (todo desde
v1.0.10), no solo los fixes de hoy.

**How to apply:** no empezar documentación técnica del código/arquitectura todavía — eso
es un entregable aparte (manual técnico), fuera de alcance de esta tarea concreta.

## Alcance confirmado por el usuario

- Cubrir todos los commits mergeados a `main` desde el cutoff real de v1.0.10 en los 3
  repos, como versión **v1.0.11**.
- Backend del cliente: Docker manual, no Dokploy — mantener instrucciones Docker en la
  guía.
- Incluir las migraciones Flyway nuevas en la sección correspondiente.
- Mismo formato/maquetación que las entregas anteriores (Z-Innova, HTML → PDF vía Chrome
  headless).

## Migraciones Flyway nuevas desde v1.0.10

- [x] V31 — `tickets.observaciones` VARCHAR(255) → NVARCHAR(MAX) (bug real: ticket
      multi-foto con 2+ avisos de plausibilidad superaba 255 caracteres, INSERT fallaba
      con "String or binary data would be truncated", HTTP 500, ticket no se guardaba).
- [x] V32 — tabla `invitaciones_usuario` (feature: invitación de administradores por
      email).
- [x] V33 — tabla `auditoria_reclasificacion_tarjetas` (feature: reclasificación
      automática TARJETA→VIAT para Cepsa/Moeve por historial de operaciones).

Las tres se aplican solas en el arranque del backend (Flyway), igual que las anteriores.
No hay variables de entorno nuevas en ninguna de las tres.

## Grupos de cambio identificados (commits reales, cutoff 2026-10-14 03:03 → hoy)

1. **Hotfixes post-release inmediatos** (mismo 14 oct tarde + 15-16 oct): volumen
   persistente `uploads/` (ya redactado en adenda borrador, nunca entregada — reusar),
   `tickets.observaciones` NVARCHAR(MAX) (V31), OCR con fecha vacía, picker de vincular
   no mostraba tickets en INCIDENCIA/SIN_COINCIDENCIA/MULTIPLE (api+web), asistente con
   estado MULTIPLE + reparto por centro de coste como estimación.
2. **Invitación de administradores por email** (feature nueva, api+web, V32).
3. **Asistente: deeplink y navegación para vincular tickets pendientes** (api+web).
4. **Filtro de fechas + DatePicker propio en "vincular"** (web).
5. **Facturas: import en lote, subida/borrado múltiple, timeout LLM, 4 fixes de
   precisión del parser Repsol** (api+web).
6. **Clasificación ViaT/Tarjeta por prefijo (Repsol) + restore() soft-delete + NaN
   peaje partido en 3 líneas + reclasificación automática Cepsa/Moeve (V33) + fix
   fecha/hora DST en ticket manual** — ya documentado en `odd/tasks/
   clasificacion-viat-por-numero.md` y `odd/tasks/
   bugs-fecha-nan-y-reclasificacion-cepsa.md`, reusar directamente.
7. **PWA**: no perder fotos al pulsar Atrás en la previsualización del escaneo; avisar
   que varias fotos añadidas son del mismo ticket.

## Progreso

- [x] 1. Confirmar alcance con el usuario (todo lo acumulado desde v1.0.10, no solo hoy).
- [x] 2. Localizar plantilla/estilo real (HTML+CSS de la adenda de volumen persistente,
      scratchpad de la sesión anterior) y pipeline de generación (Chrome headless
      `--print-to-pdf`, confirmado disponible en esta máquina).
- [x] 3. Determinar el cutoff real de v1.0.10 (commit `68d8158`, 2026-10-14 03:03) y
      listar los commits nuevos exactos en los 3 repos desde ese punto.
- [x] 4. Leer las 3 migraciones Flyway nuevas (V31, V32, V33) para la sección técnica.
- [x] 5. Explorar en detalle (diffs/docs) los grupos 1-5 y 7 para redactar contenido
      preciso — delegado a 3 sub-agentes en paralelo (lectura, sin escritura).
      Hallazgos clave: "18 facturas sin PDF" y el montaje del volumen persistente
      solo están verificados en el entorno DEMO (Swarm/Dokploy), no en el servidor
      real de Tecozam — redactado como recomendación a verificar, no como hecho
      confirmado en su entorno. `APP_WEB_BASE_URL` es variable de entorno NUEVA y
      obligatoria (sin ella el link de invitación apunta a localhost); SMTP_* ya
      existían mas nunca se habían usado hasta esta entrega — hay que verificar que
      tengan datos reales.
- [x] 6. Redactar RELEASE-NOTES-v1.0.11 (HTML, mismo estilo Z-Innova, 11 páginas:
      cover + 9 secciones + contraportada).
- [x] 7. Redactar GUIA-ACTUALIZACION-2026-10-22 (HTML, mismo estilo, sección Docker +
      Flyway V31-V33 + variable APP_WEB_BASE_URL obligatoria + checklist de
      verificación (8 puntos) + rollback).
- [x] 8. Convertir ambos a PDF (Chrome headless) y guardar en
      `H:\TECOZAM\Tickets\RELEASE-NOTES-v1.0.11.pdf` y
      `H:\TECOZAM\Tickets\GUIA-ACTUALIZACION-2026-10-22.pdf`.
- [x] 9. Verificar visualmente el PDF generado. Bug encontrado y corregido en el
      camino: faltaba `@page { size: A4; margin: 0; }` en el CSS — Chrome aplicaba
      sus márgenes de impresión por defecto además del padding del documento, y
      cada página desbordaba a una segunda página casi en blanco (22 páginas en
      vez de 11 en el primer intento de Release Notes). Tras añadir la regla,
      ambos PDF quedaron limpios: 11 páginas (release notes) y 5 páginas (guía),
      verificadas leyendo el PDF final completo, no solo comprobando que el
      fichero existe.

## Rutas relevantes

- Repos: `H:\TECOZAM\Tickets\api\tecozam-bills-api`, `H:\TECOZAM\Tickets\web\tecozam-bills-web`,
  `H:\TECOZAM\Tickets\PWA\tecozam-fleet-pwa`.
- Entregas anteriores (destino final): `H:\TECOZAM\Tickets\*.pdf`.
- Plantilla de estilo reutilizable: scratchpad de la sesión
  `dcb6450c-bc3c-4ebb-8a86-b666fd0384dd` — `adenda-volumen-persistente-uploads.html`,
  `guia-actualizacion-2026-10-14.html`, `release-notes-v1.0.10.html`.
