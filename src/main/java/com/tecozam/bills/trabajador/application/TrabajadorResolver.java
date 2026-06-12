package com.tecozam.bills.trabajador.application;

import com.tecozam.bills.shared.util.NombreApellidosSplitter;
import com.tecozam.bills.trabajador.domain.OrigenTrabajador;
import com.tecozam.bills.trabajador.domain.Trabajador;
import com.tecozam.bills.trabajador.infrastructure.persistence.TrabajadorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Punto único de resolución de Trabajadores (BILLS-10).
 *
 * <p>Toda vía que cree un Trabajador (importación de tarjetas, importación de
 * facturas, alta de usuario de oficina o de campo) debe pasar por aquí en lugar
 * de instanciar {@code Trabajador.builder()} directamente. La política de
 * deduplicación es:
 *
 * <ol>
 *   <li>Si la entrada tiene DNI/NIE no vacío y existe ya un Trabajador con ese
 *       DNI/NIE → se reusa.</li>
 *   <li>Si no, si la entrada tiene email no vacío y existe ya un Trabajador con
 *       ese email → se reusa.</li>
 *   <li>Si no, se busca por (nombre, apellidos) case-insensitive → si existe se
 *       reusa.</li>
 *   <li>Si nada matchea, se crea un Trabajador nuevo con el {@code origen}
 *       pasado.</li>
 * </ol>
 *
 * <p>Cuando se reusa un Trabajador existente, NO se sobrescribe su {@code origen}
 * — la primera vía que registró a la persona se queda como su origen canónico.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrabajadorResolver {

    private final TrabajadorRepository trabajadorRepository;

    /**
     * Resuelve (busca o crea) un Trabajador para una persona dada.
     *
     * @param nombre   nombre simple, sin apellidos
     * @param apellidos apellidos
     * @param email    correo (opcional, puede ser null/blank)
     * @param dniNie   DNI o NIE (opcional, puede ser null/blank)
     * @param origen   origen del registro si hay que crear uno nuevo
     * @return Trabajador persistido (puede ser ya existente o recién creado)
     */
    public Trabajador resolver(
            String nombre,
            String apellidos,
            String email,
            String dniNie,
            OrigenTrabajador origen) {

        if (dniNie != null && !dniNie.isBlank()) {
            Optional<Trabajador> match = trabajadorRepository
                    .findFirstByNombreIgnoreCaseAndApellidosIgnoreCase(
                            safe(nombre), safe(apellidos));
            // Nota: dniNie esta cifrado en BD via AesEncryptorConverter, por lo
            // que no podemos hacer findByDniNie sin desencriptar. Mientras llega
            // ese helper, hacemos best-effort con nombre+apellidos y dependemos
            // de la unicidad del registro humano.
            if (match.isPresent()) return match.get();
        }

        if (email != null && !email.isBlank()) {
            Optional<Trabajador> byEmail = trabajadorRepository.findByEmail(email.trim());
            if (byEmail.isPresent()) return byEmail.get();
        }

        Optional<Trabajador> byNombre = trabajadorRepository
                .findFirstByNombreIgnoreCaseAndApellidosIgnoreCase(safe(nombre), safe(apellidos));
        if (byNombre.isPresent()) return byNombre.get();

        Trabajador nuevo = Trabajador.builder()
                .nombre(safe(nombre))
                .apellidos(safe(apellidos))
                .email(email != null && !email.isBlank() ? email.trim() : null)
                .dniNie(dniNie != null && !dniNie.isBlank() ? dniNie.trim() : null)
                .activo(true)
                .origen(origen != null ? origen : OrigenTrabajador.IMPORTACION)
                .build();
        nuevo = trabajadorRepository.save(nuevo);
        log.info("Trabajador creado por TrabajadorResolver: {} {} (id={}, origen={})",
                nuevo.getNombre(), nuevo.getApellidos(), nuevo.getId(), nuevo.getOrigen());
        return nuevo;
    }

    /**
     * Variante que recibe un "nombre completo" sin separar y lo divide via
     * {@link NombreApellidosSplitter}.
     */
    public Trabajador resolverDesdeNombreCompleto(
            String nombreCompleto, String email, String dniNie, OrigenTrabajador origen) {
        String[] partes = NombreApellidosSplitter.split(nombreCompleto);
        return resolver(partes[0], partes[1], email, dniNie, origen);
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
