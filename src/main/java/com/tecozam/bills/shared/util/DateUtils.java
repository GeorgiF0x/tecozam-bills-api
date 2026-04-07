package com.tecozam.bills.shared.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * Date parsing and currency formatting utilities.
 * <p>
 * Supports multiple date formats commonly found in Spanish invoices and provider exports.
 */
public final class DateUtils {

    private DateUtils() {
        // Utility class — no instantiation
    }

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy")
    );

    private static final List<DateTimeFormatter> DATETIME_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,                       // 2025-04-06T10:30:00
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("d/M/yyyy H:mm:ss"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
    );

    private static final DecimalFormatSymbols SPANISH_SYMBOLS;
    private static final DecimalFormat CURRENCY_FORMAT;

    static {
        SPANISH_SYMBOLS = new DecimalFormatSymbols(new Locale("es", "ES"));
        SPANISH_SYMBOLS.setDecimalSeparator(',');
        SPANISH_SYMBOLS.setGroupingSeparator('.');
        CURRENCY_FORMAT = new DecimalFormat("#,##0.00", SPANISH_SYMBOLS);
    }

    /**
     * Parses a date string trying multiple formats.
     *
     * @param text the date string
     * @return the parsed {@link LocalDate}
     * @throws DateTimeParseException if no format matches
     */
    public static LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            throw new DateTimeParseException("La fecha no puede estar vacía", "", 0);
        }
        String trimmed = text.strip();
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        throw new DateTimeParseException(
                "No se pudo parsear la fecha: '%s'. Formatos soportados: dd/MM/yyyy, yyyy-MM-dd, dd-MM-yyyy"
                        .formatted(trimmed),
                trimmed, 0);
    }

    /**
     * Parses a date-time string trying multiple formats (ISO and Spanish conventions).
     *
     * @param text the date-time string
     * @return the parsed {@link LocalDateTime}
     * @throws DateTimeParseException if no format matches
     */
    public static LocalDateTime parseDateTime(String text) {
        if (text == null || text.isBlank()) {
            throw new DateTimeParseException("La fecha/hora no puede estar vacía", "", 0);
        }
        String trimmed = text.strip();
        for (DateTimeFormatter formatter : DATETIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        throw new DateTimeParseException(
                "No se pudo parsear la fecha/hora: '%s'".formatted(trimmed),
                trimmed, 0);
    }

    /**
     * Formats a {@link BigDecimal} amount as Spanish currency.
     * <p>
     * Example: {@code 1234.56 → "1.234,56 €"}
     *
     * @param amount the monetary amount
     * @return the formatted string with euro symbol
     */
    public static String formatCurrency(BigDecimal amount) {
        if (amount == null) {
            return "0,00 €";
        }
        return CURRENCY_FORMAT.format(amount) + " €";
    }
}
