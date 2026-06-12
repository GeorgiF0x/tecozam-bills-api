package com.tecozam.bills.admin.application;

import com.tecozam.bills.admin.dto.ImportTarjetasReportDTO;
import com.tecozam.bills.admin.infrastructure.import_.FilaImportada;
import com.tecozam.bills.admin.infrastructure.import_.ImportContext;
import com.tecozam.bills.admin.infrastructure.import_.ListadoTarjetasParserFactory;
import com.tecozam.bills.admin.infrastructure.import_.ListadoTarjetasRowParser;
import com.tecozam.bills.admin.infrastructure.import_.TipoRecurso;
import com.tecozam.bills.centrocoste.domain.CentroCoste;
import com.tecozam.bills.centrocoste.infrastructure.persistence.CentroCosteRepository;
import com.tecozam.bills.proveedor.domain.Proveedor;
import com.tecozam.bills.proveedor.infrastructure.persistence.ProveedorRepository;
import com.tecozam.bills.trabajador.application.TrabajadorResolver;
import com.tecozam.bills.trabajador.domain.OrigenTrabajador;
import com.tecozam.bills.shared.domain.enums.EstadoRecurso;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.tarjeta.domain.Tarjeta;
import com.tecozam.bills.tarjeta.domain.TarjetaNumeroNormalizer;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaRepository;
import com.tecozam.bills.trabajador.domain.Trabajador;
import com.tecozam.bills.trabajador.infrastructure.persistence.TrabajadorRepository;
import com.tecozam.bills.viat.domain.Viat;
import com.tecozam.bills.viat.infrastructure.persistence.ViatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Orquestador del import de Excel de listado de tarjetas. Reemplaza la parte
 * de {@code RepsolImportService} que procesaba la hoja "DATOS MATRICULA"
 * (las hojas mensuales con operaciones se quedan en el service antiguo).
 *
 * <p>Resuelve el parser via {@link ListadoTarjetasParserFactory}, valida que el
 * proveedor existe y está activo en BD, recorre las filas y delega la
 * persistencia (Trabajador, CentroCoste, Tarjeta o Viat) aquí mismo dentro de
 * una transacción.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ListadoTarjetasImportService {

    private final ListadoTarjetasParserFactory parserFactory;
    private final ProveedorRepository proveedorRepo;
    private final TarjetaRepository tarjetaRepo;
    private final ViatRepository viatRepo;
    private final TrabajadorRepository trabajadorRepo;
    private final TrabajadorResolver trabajadorResolver;
    private final CentroCosteRepository centroCosteRepo;

    public ImportTarjetasReportDTO importar(MultipartFile file, String codigoProveedor) {
        long inicio = System.currentTimeMillis();
        validarArchivo(file);
        if (codigoProveedor == null || codigoProveedor.isBlank()) {
            throw new BusinessException("Selecciona un proveedor");
        }

        // Factory primero: rechaza proveedores no soportados por la app (IllegalArgumentException).
        // Después resolverProveedor consulta BD para verificar registro + activo (BusinessException).
        ListadoTarjetasRowParser parser = parserFactory.parserPara(codigoProveedor);
        Proveedor proveedor = resolverProveedor(codigoProveedor);
        ImportContext ctx = new ImportContext(proveedor);

        try (InputStream in = file.getInputStream();
             Workbook wb = new XSSFWorkbook(in)) {
            Sheet hoja = wb.getSheetAt(0);
            Map<String, Integer> headers = parser.leerCabeceras(hoja);
            for (Row row : hoja) {
                if (row.getRowNum() == 0) continue;
                Optional<FilaImportada> parsed = parser.parse(row, headers);
                parsed.ifPresent(fila -> materializar(fila, ctx));
            }
        } catch (IOException e) {
            throw new BusinessException("Excel inválido o ilegible", e);
        }

        long duracion = System.currentTimeMillis() - inicio;
        log.info("Listado {} importado: tarjetas={}, viats={}, ignoradas={}, duracion={}ms",
                codigoProveedor, ctx.tarjetasCreadas, ctx.viatsCreados, ctx.filasIgnoradas, duracion);
        return toDTO(ctx, duracion);
    }

    private void validarArchivo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("El archivo es obligatorio");
        }
        if (file.getSize() > 10L * 1024 * 1024) {
            throw new BusinessException("El archivo no puede superar 10 MB");
        }
    }

    private Proveedor resolverProveedor(String codigoProveedor) {
        String codigo = codigoProveedor.trim().toUpperCase(Locale.ROOT);
        String alias = switch (codigo) {
            case "SOLRED" -> "REPSOL";
            case "CEPSA", "MOEVE" -> "MOEVE_CEPSA";
            default -> codigo;
        };
        Proveedor proveedor = proveedorRepo.findByCodigo(alias)
                .orElseThrow(() -> new BusinessException("Proveedor no registrado: " + codigoProveedor));
        if (!proveedor.isActivo()) {
            throw new BusinessException("Proveedor inactivo: " + codigoProveedor);
        }
        return proveedor;
    }

    private void materializar(FilaImportada fila, ImportContext ctx) {
        if (!fila.conceptoConocido()) {
            ctx.filasIgnoradas++;
            // NEW-13: mensaje claro con motivo concreto en lugar del contador
            // genérico. El admin puede entender qué falla y arreglar el Excel.
            String concepto = fila.concepto();
            String motivo;
            if (concepto == null || concepto.isBlank()) {
                motivo = "Columna concepto vacía";
            } else {
                motivo = "Concepto '" + concepto + "' no reconocido";
            }
            ctx.errores.add("Tarjeta " + fila.numero() + " — " + motivo
                    + " · procesado como TARJETA por defecto");
        }
        materializarCentroCoste(fila, ctx);
        Trabajador trabajador = materializarTrabajador(fila, ctx);
        if (fila.tipo() == TipoRecurso.VIAT) {
            materializarViat(fila, trabajador, ctx);
        } else {
            materializarTarjeta(fila, ctx);
        }
    }

    private void materializarCentroCoste(FilaImportada fila, ImportContext ctx) {
        String codigo = fila.centroCoste();
        if (codigo == null || codigo.isBlank()) return;
        if (ctx.centrosCache.containsKey(codigo)) return;
        Optional<CentroCoste> existente = centroCosteRepo.findByCodigo(codigo);
        if (existente.isPresent()) {
            ctx.centrosCache.put(codigo, existente.get());
            ctx.centrosExistentes++;
        } else {
            CentroCoste nuevo = CentroCoste.builder()
                    .codigo(codigo)
                    .nombre(codigo)
                    .activo(true)
                    .build();
            ctx.centrosCache.put(codigo, centroCosteRepo.save(nuevo));
            ctx.centrosCreados++;
        }
    }

    private Trabajador materializarTrabajador(FilaImportada fila, ImportContext ctx) {
        String key = fila.nombreCompleto() == null ? "" : fila.nombreCompleto().trim();
        if (key.isBlank()) return null;
        Trabajador cached = ctx.trabajadoresCache.get(key);
        if (cached != null) {
            ctx.trabajadoresExistentes++;
            return cached;
        }
        // BILLS-10: TrabajadorResolver decide si crear o reusar (busca por
        // DNI/email/nombre+apellidos antes de crear). Asi evitamos duplicados
        // entre import y signup.
        long count = trabajadorRepo.count();
        Trabajador resolved = trabajadorResolver.resolverDesdeNombreCompleto(
                key, null, null, OrigenTrabajador.IMPORTACION);
        boolean creado = trabajadorRepo.count() > count;
        ctx.trabajadoresCache.put(key, resolved);
        if (creado) ctx.trabajadoresCreados++; else ctx.trabajadoresExistentes++;
        return resolved;
    }

    private void materializarTarjeta(FilaImportada fila, ImportContext ctx) {
        String canonico = TarjetaNumeroNormalizer.canonical(fila.numero(), ctx.getCodigoProveedor());
        Tarjeta cached = ctx.tarjetasCache.get(canonico);
        if (cached != null) {
            ctx.tarjetasExistentes++;
            return;
        }
        Optional<Tarjeta> existente = tarjetaRepo.findByNumeroTarjeta(canonico);
        if (existente.isPresent()) {
            ctx.tarjetasCache.put(canonico, existente.get());
            ctx.tarjetasExistentes++;
            return;
        }
        Tarjeta nueva = Tarjeta.builder()
                .numeroTarjeta(canonico)
                .alias(fila.matricula())
                .proveedor(ctx.getProveedor())
                .estado(EstadoRecurso.DISPONIBLE)
                .activa(true)
                .build();
        Tarjeta saved = tarjetaRepo.save(nueva);
        ctx.tarjetasCache.put(canonico, saved);
        ctx.tarjetasCreadas++;
    }

    private void materializarViat(FilaImportada fila, Trabajador trabajador, ImportContext ctx) {
        String codigo = fila.numero();
        Viat cached = ctx.viatsCache.get(codigo);
        if (cached != null) {
            ctx.viatsExistentes++;
            return;
        }
        Optional<Viat> existente = viatRepo.findByCodigo(codigo);
        if (existente.isPresent()) {
            ctx.viatsCache.put(codigo, existente.get());
            ctx.viatsExistentes++;
            return;
        }
        String numeroSerie = fila.matricula() != null && !fila.matricula().isBlank() ? fila.matricula() : null;
        String descripcion = trabajador != null
                ? (trabajador.getNombre() + " " + trabajador.getApellidos()).trim()
                : null;
        Viat nuevo = Viat.builder()
                .codigo(codigo)
                .numeroSerie(numeroSerie)
                .descripcion(descripcion)
                .estado(EstadoRecurso.DISPONIBLE)
                .activo(true)
                .build();
        Viat saved = viatRepo.save(nuevo);
        ctx.viatsCache.put(codigo, saved);
        ctx.viatsCreados++;
    }

    private ImportTarjetasReportDTO toDTO(ImportContext ctx, long duracion) {
        return new ImportTarjetasReportDTO(
                ctx.centrosCreados,
                ctx.centrosExistentes,
                ctx.trabajadoresCreados,
                ctx.trabajadoresExistentes,
                ctx.tarjetasCreadas,
                ctx.tarjetasExistentes,
                ctx.viatsCreados,
                ctx.viatsExistentes,
                ctx.filasIgnoradas,
                ctx.errores,
                duracion);
    }
}
