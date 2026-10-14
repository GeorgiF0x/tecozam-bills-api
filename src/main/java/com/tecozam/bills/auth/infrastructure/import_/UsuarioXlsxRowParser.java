package com.tecozam.bills.auth.infrastructure.import_;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Lee las cabeceras y filas del Excel de import masivo de usuarios (oficina
 * y campo en el mismo archivo, columna Tipo). Funcion pura sin BD — mismo
 * patron que {@code VehiculoXlsxRowParser}, ver
 * odd/tasks/maestros-import-masivo.md T5.
 *
 * <p>Cabeceras esperadas (en cualquier orden, tildes/mayusculas variables):
 * {@code TIPO | USERNAME | PASSWORD | NOMBRE | APELLIDOS | EMAIL | DNI |
 * TELEFONO | ROL}. Apellidos/Telefono solo aplican a filas CAMPO; Email/Rol
 * solo aplican a filas OFICINA (se ignoran en el otro tipo, no es error).
 */
@Component
public final class UsuarioXlsxRowParser {

    private static final String KEY_TIPO = "TIPO";
    private static final String KEY_USERNAME = "USERNAME";
    private static final String KEY_PASSWORD = "PASSWORD";
    private static final String KEY_NOMBRE = "NOMBRE";
    private static final String KEY_APELLIDOS = "APELLIDOS";
    private static final String KEY_EMAIL = "EMAIL";
    private static final String KEY_DNI = "DNI";
    private static final String KEY_TELEFONO = "TELEFONO";
    private static final String KEY_ROL = "ROL";

    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$");

    private static final DataFormatter FORMATTER = new DataFormatter(new Locale("es", "ES"));

    public Map<String, Integer> leerCabeceras(Sheet hoja) {
        Row header = hoja.getRow(0);
        if (header == null) return Map.of();
        Map<String, Integer> indices = new HashMap<>();
        for (int i = 0; i < header.getLastCellNum(); i++) {
            String key = mapearCabecera(FORMATTER.formatCellValue(header.getCell(i)));
            if (key != null) indices.putIfAbsent(key, i);
        }
        return indices;
    }

    public Optional<FilaUsuarioImportada> parse(Row row, Map<String, Integer> headers) {
        if (row == null) return Optional.empty();

        String tipoRaw = leerCelda(row, headers, KEY_TIPO);
        String username = leerCelda(row, headers, KEY_USERNAME);
        String password = leerCelda(row, headers, KEY_PASSWORD);
        String nombre = leerCelda(row, headers, KEY_NOMBRE);
        String apellidos = leerCelda(row, headers, KEY_APELLIDOS);
        String email = leerCelda(row, headers, KEY_EMAIL);
        String dni = leerCelda(row, headers, KEY_DNI);
        String telefono = leerCelda(row, headers, KEY_TELEFONO);
        String rol = leerCelda(row, headers, KEY_ROL);

        // Fila vacia (separador/salto de linea) — se ignora en silencio.
        if (tipoRaw.isBlank() && username.isBlank() && nombre.isBlank()) {
            return Optional.empty();
        }

        String tipo = tipoRaw.toUpperCase(Locale.ROOT);
        if (!tipo.equals("OFICINA") && !tipo.equals("CAMPO")) {
            return Optional.of(invalida(tipoRaw, username, password, nombre, apellidos, email, dni, telefono, rol,
                    "La columna Tipo debe ser OFICINA o CAMPO (valor recibido: '" + tipoRaw + "')"));
        }
        if (username.isBlank()) {
            return Optional.of(invalida(tipo, username, password, nombre, apellidos, email, dni, telefono, rol,
                    "El username es obligatorio"));
        }
        if (password.isBlank()) {
            return Optional.of(invalida(tipo, username, password, nombre, apellidos, email, dni, telefono, rol,
                    "La contraseña es obligatoria"));
        }
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            return Optional.of(invalida(tipo, username, password, nombre, apellidos, email, dni, telefono, rol,
                    "La contraseña debe tener mínimo 8 caracteres, con mayúscula, minúscula y número"));
        }
        if (nombre.isBlank()) {
            return Optional.of(invalida(tipo, username, password, nombre, apellidos, email, dni, telefono, rol,
                    "El nombre es obligatorio"));
        }
        if (tipo.equals("OFICINA")) {
            String rolUpper = rol.toUpperCase(Locale.ROOT);
            if (rol.isBlank() || !(rolUpper.equals("ADMIN") || rolUpper.equals("GESTOR") || rolUpper.equals("CONSULTA"))) {
                return Optional.of(invalida(tipo, username, password, nombre, apellidos, email, dni, telefono, rol,
                        "El rol es obligatorio para usuarios de oficina (ADMIN, GESTOR o CONSULTA)"));
            }
        }

        return Optional.of(new FilaUsuarioImportada(tipo, username, password, nombre, apellidos, email, dni, telefono, rol, null));
    }

    private FilaUsuarioImportada invalida(String tipo, String username, String password, String nombre,
            String apellidos, String email, String dni, String telefono, String rol, String error) {
        return new FilaUsuarioImportada(tipo, username, password, nombre, apellidos, email, dni, telefono, rol, error);
    }

    private String leerCelda(Row row, Map<String, Integer> headers, String key) {
        Integer idx = headers.get(key);
        if (idx == null) return "";
        String val = FORMATTER.formatCellValue(row.getCell(idx));
        return val == null ? "" : val.trim();
    }

    private String mapearCabecera(String raw) {
        if (raw == null) return null;
        String sinTildes = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        String compact = sinTildes.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        return switch (compact) {
            case "TIPO" -> KEY_TIPO;
            case "USERNAME", "USUARIO" -> KEY_USERNAME;
            case "PASSWORD", "CONTRASENA" -> KEY_PASSWORD;
            case "NOMBRE" -> KEY_NOMBRE;
            case "APELLIDOS" -> KEY_APELLIDOS;
            case "EMAIL", "CORREO" -> KEY_EMAIL;
            case "DNI" -> KEY_DNI;
            case "TELEFONO" -> KEY_TELEFONO;
            case "ROL" -> KEY_ROL;
            default -> null;
        };
    }
}
