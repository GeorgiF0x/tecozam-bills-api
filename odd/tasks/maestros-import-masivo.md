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

## Estado
Sin empezar. Todavia no se ha explorado el codigo real para esta tarea
(a diferencia de las otras features de esta sesion, que si se verificaron
contra codigo antes de escribir el plan) — el alcance de arriba es solo lo
que se desprende del video, pendiente de contrastar con el codigo real al
empezar.
