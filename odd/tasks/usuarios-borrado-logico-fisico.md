# Borrado lógico/físico para UsuarioOficina y UsuarioCampo

## Objetivo
Replicar el patrón de `TrabajadorService.eliminar(id, real)` en `UsuarioOficina` y `UsuarioCampo`.

## Alcance
- Migración `V34__usuarios_eliminado_en.sql` (columna `eliminado_en` en `usuarios_oficina` y `usuarios_campo`, patrón idempotente plano tipo V25).
- Entidades: `eliminadoEn` + `softDelete()` + `isEliminado()` en `UsuarioOficina` y `UsuarioCampo` (sin tocar `BaseEntity`).
- Repos: `InvitacionUsuarioRepository.existsByUsuarioId/existsByInvitadoPorId`, `WebauthnCredentialRepository.existsByUsuarioCampoId`, `PinAccesoEventoRepository.existsByUsuarioCampoId`, filtro de activos en listados.
- Services: `UsuarioOficinaService.eliminar(id, real)` + `tieneHistorial`; `UsuarioCampoService.eliminar(id, real)` + `tieneHistorial`.
- Controllers: `DELETE /{id}?real=` en ambos.
- Tests TDD: 3 casos en `UsuarioOficinaServiceTest` (ya existe) + nuevo `UsuarioCampoServiceTest`.

## TDD
Modo estricto: test antes del código, confirmar RED, implementar mínimo, confirmar GREEN.
Runner: `mvn test -Dtest=<Clase>`.

## Fuera de alcance
`trabajador/`, `tarjeta/`, `factura/`, frontend. No tocar `BaseEntity`. No commits/git.

## Estado
- [x] Migración V34, entidades, repos, services, controllers, tests TDD (3 casos en UsuarioOficinaServiceTest + nuevo UsuarioCampoServiceTest). RED confirmado antes de implementar, GREEN después.

## Verificación
- `mvn test -Dtest=UsuarioOficinaServiceTest,UsuarioCampoServiceTest,TrabajadorServiceTest` → 26 tests, 0 fallos, 0 errores. Spot-check re-ejecutado por el orquestador: OK.
- `mvn compile` → BUILD SUCCESS, sin errores nuevos.
- Sin tocar `trabajador/`, `tarjeta/`, `factura/`, frontend, `BaseEntity`, git.
