package com.tecozam.bills.shared.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/**
 * Punto unico de separacion de "NOMBRE APELLIDO1 APELLIDO2..." en [nombre, apellidos].
 *
 * <p>Reconoce nombres compuestos castellanos comunes (p. ej. "JOSE LUIS",
 * "MARIA CARMEN", "MIGUEL ANGEL") para no romperlos al partir. Si las dos
 * primeras palabras del input coinciden con uno de la lista, se mantienen
 * juntas como nombre. La comparacion es insensible a tildes y a mayusculas.
 *
 * <p>Antes este metodo estaba duplicado en RepsolImportService, UsuarioOficinaService
 * y ListadoTarjetasImportService con tres implementaciones distintas. Esta es la
 * unica que deben usar todos los call-sites (BILLS-06).
 */
public final class NombreApellidosSplitter {

    private static final Set<String> NOMBRES_COMPUESTOS = Set.of(
            "JOSE LUIS", "JOSE ANTONIO", "JOSE MIGUEL", "JOSE MANUEL", "JOSE MARIA",
            "JOSE ANGEL", "JOSE RAMON", "JOSE CARLOS", "JOSE FRANCISCO", "JOSE IGNACIO",
            "JOSE JAVIER", "JOSE ALBERTO", "JOSE DANIEL", "JOSE ENRIQUE", "JOSE ANDRES",
            "JUAN CARLOS", "JUAN MANUEL", "JUAN PEDRO", "JUAN ANTONIO", "JUAN JOSE",
            "JUAN PABLO", "JUAN LUIS", "JUAN FRANCISCO", "JUAN JESUS", "JUAN MIGUEL",
            "MARIA CARMEN", "MARIA JOSE", "MARIA TERESA", "MARIA DOLORES", "MARIA ISABEL",
            "MARIA ANGELES", "MARIA LUISA", "MARIA PILAR", "MARIA JESUS", "MARIA AMPARO",
            "MARIA ROSA", "MARIA ELENA", "MARIA CRISTINA", "MARIA MERCEDES", "MARIA VICTORIA",
            "MARIA NIEVES", "MARIA SOLEDAD", "MARIA CONCEPCION", "MARIA EUGENIA", "MARIA SOL",
            "ANA MARIA", "ANA BELEN", "ANA ISABEL", "ANA SOFIA",
            "MIGUEL ANGEL",
            "LUIS MIGUEL", "LUIS ALBERTO", "LUIS FERNANDO", "LUIS ANTONIO",
            "FRANCISCO JAVIER", "FRANCISCO JOSE", "FRANCISCO MANUEL",
            "CARLOS JOSE", "CARLOS ANTONIO",
            "ANTONIO JOSE", "ANTONIO LUIS", "ANTONIO MANUEL", "ANTONIO MIGUEL",
            "PEDRO JESUS", "PEDRO LUIS",
            "MANUEL JESUS", "MANUEL JOSE", "MANUEL ANTONIO",
            "ANGEL LUIS", "ANGEL MARIA"
    );

    private NombreApellidosSplitter() { }

    /**
     * @param nombreCompleto entrada cruda; admite null/blank.
     * @return array de 2 posiciones [nombre, apellidos]. Nunca null. Si la entrada
     *         es vacia, ambos son cadena vacia.
     */
    public static String[] split(String nombreCompleto) {
        if (nombreCompleto == null || nombreCompleto.isBlank()) {
            return new String[]{"", ""};
        }
        String trim = nombreCompleto.trim().replaceAll("\\s+", " ");
        String[] palabras = trim.split(" ");
        if (palabras.length == 1) {
            return new String[]{palabras[0], ""};
        }
        if (palabras.length >= 3) {
            String candidato = palabras[0] + " " + palabras[1];
            String normalizado = Normalizer.normalize(candidato, Normalizer.Form.NFD)
                    .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                    .toUpperCase(Locale.ROOT);
            if (NOMBRES_COMPUESTOS.contains(normalizado)) {
                StringBuilder apellidos = new StringBuilder();
                for (int i = 2; i < palabras.length; i++) {
                    if (i > 2) apellidos.append(" ");
                    apellidos.append(palabras[i]);
                }
                return new String[]{candidato, apellidos.toString()};
            }
        }
        int idx = trim.indexOf(' ');
        return new String[]{trim.substring(0, idx), trim.substring(idx + 1)};
    }
}
