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

    /**
     * @param esViat declarado explícitamente por el admin al importar (checkbox
     *               "Este listado son dispositivos VIAT"), no se adivina por el
     *               texto del concepto de cada fila — ver
     *               odd/tasks/import-llm-switch.md (Tarea 7).
     */
    public ImportTarjetasReportDTO importar(MultipartFile file, String codigoProveedor, boolean esViat) {
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
        TipoRecurso tipoLote = esViat ? TipoRecurso.VIAT : TipoRecurso.TARJETA;

        try (InputStream in = file.getInputStream();
             Workbook wb = new XSSFWorkbook(in)) {
            Sheet hoja = wb.getSheetAt(0);
            Map<String, Integer> headers = parser.leerCabeceras(hoja);
            for (Row row : hoja) {
                if (row.getRowNum() == 0) continue;
                Optional<FilaImportada> parsed = parser.parse(row, headers, tipoLote);
                parsed.ifPresent(fila -> materializar(fila, ctx));
            }
        } catch (IOException e) {
            throw new BusinessException("Excel inválido o ilegible", e);
        }

        long duracion = System.currentTimeMillis() - inicio;
        log.info("Listado {} importado (tipoLote={}): tarjetas={}, viats={}, ignoradas={}, duracion={}ms",
                codigoProveedor, tipoLote, ctx.tarjetasCreadas, ctx.viatsCreados,
                ctx.filasIgnoradas, duracion);
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
        TipoRecurso tipoEfectivo = fila.tipo();
        if (!fila.conceptoConocido()) {
            // NEW-13: mensaje claro con motivo concreto en lugar del contador
            // genérico. El admin puede entender qué falla y arreglar el Excel.
            String concepto = fila.concepto();
            String motivo;
            if (concepto == null || concepto.isBlank()) {
                motivo = "Columna concepto vacía";
            } else {
                motivo = "Concepto '" + concepto + "' no reconocido";
            }
            // odd/tasks/clasificacion-viat-por-numero.md: señal estructural (no
            // semántica) — todo número de tarjeta Repsol lleva el prefijo 0007
            // (TarjetaNumeroNormalizer). Si el lote se declaró TARJETA pero el
            // concepto es desconocido y el número no encaja con ese prefijo, es
            // casi con certeza un dispositivo ViaT real metido en el listado de
            // tarjetas (evidencia real de sesión). Cepsa/Moeve no tiene señal
            // fiable por número (ver plan) y nunca se reclasifica.
            boolean reclasificaAViat = fila.tipo() == TipoRecurso.TARJETA
                    && esProveedorRepsol(ctx)
                    && !TarjetaNumeroNormalizer.esNumeroTarjetaRepsol(fila.numero());
            if (reclasificaAViat) {
                tipoEfectivo = TipoRecurso.VIAT;
                ctx.reclasificadosPorNumero++;
                ctx.errores.add("Tarjeta " + fila.numero()
                        + " — reclasificado automáticamente como VIAT"
                        + " · número fuera del prefijo Repsol conocido (0007)");
            } else {
                ctx.filasIgnoradas++;
                ctx.errores.add("Tarjeta " + fila.numero() + " — " + motivo
                        + " · procesado como TARJETA por defecto"
                        + " · serie " + serieDe(fila.numero()));
            }
        }
        materializarCentroCoste(fila, ctx);
        Trabajador trabajador = materializarTrabajador(fila, ctx);
        if (tipoEfectivo == TipoRecurso.VIAT) {
            materializarViat(fila, trabajador, ctx);
        } else {
            materializarTarjeta(fila, ctx);
        }
    }

    private boolean esProveedorRepsol(ImportContext ctx) {
        // resolverProveedor ya normaliza el alias SOLRED → REPSOL.
        return "REPSOL".equals(ctx.getCodigoProveedor());
    }

    private static String serieDe(String numero) {
        String limpio = numero == null ? "" : numero.replaceAll("\\s+", "");
        return limpio.substring(0, Math.min(10, limpio.length()));
    }

    private void materializarCentroCoste(FilaImportada fila, ImportContext ctx) {
        String codigo = fila.centroCoste();
        if (codigo == null || codigo.isBlank()) return;
        if (ctx.centrosCache.containsKey(codigo)) return;
        Optional<CentroCoste> existente = centroCosteRepo.findByCodigo(codigo);
        if (existente.isPresent()) {
            CentroCoste centro = existente.get();
            if (centro.isEliminado()) {
                // odd/tasks/clasificacion-viat-por-numero.md (punto 8): el
                // registro encontrado puede venir soft-deleted (p.ej. tras un
                // borrado masivo); sin restore()+save() se cuenta como
                // "existente" pero queda invisible para siempre en los
                // listados reales (filtran eliminadoEn IS NULL).
                centro.restore();
                centro = centroCosteRepo.save(centro);
            }
            ctx.centrosCache.put(codigo, centro);
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
            Tarjeta tarjeta = existente.get();
            if (tarjeta.isEliminado()) {
                // odd/tasks/clasificacion-viat-por-numero.md (punto 8): ver
                // comentario análogo en materializarCentroCoste.
                tarjeta.restore();
                tarjeta = tarjetaRepo.save(tarjeta);
            }
            ctx.tarjetasCache.put(canonico, tarjeta);
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
            Viat viat = existente.get();
            if (viat.isEliminado()) {
                // odd/tasks/clasificacion-viat-por-numero.md (punto 8): ver
                // comentario análogo en materializarCentroCoste.
                viat.restore();
                viat = viatRepo.save(viat);
            }
            ctx.viatsCache.put(codigo, viat);
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
                ctx.reclasificadosPorNumero,
                ctx.errores,
                duracion);
    }
}
