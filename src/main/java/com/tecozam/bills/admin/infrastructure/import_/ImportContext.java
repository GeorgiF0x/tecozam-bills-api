package com.tecozam.bills.admin.infrastructure.import_;

import com.tecozam.bills.centrocoste.domain.CentroCoste;
import com.tecozam.bills.proveedor.domain.Proveedor;
import com.tecozam.bills.tarjeta.domain.Tarjeta;
import com.tecozam.bills.trabajador.domain.Trabajador;
import com.tecozam.bills.viat.domain.Viat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Estado mutable compartido entre el orquestador {@code ListadoTarjetasImportService}
 * y la implementación concreta de {@link ListadoTarjetasImporter} para un
 * import en curso.
 *
 * <p>Centraliza:
 * <ul>
 *   <li>Los contadores que alimentan {@code ImportTarjetasReportDTO}.</li>
 *   <li>Caches por clave canónica para evitar hits a BD repetidos.</li>
 *   <li>El proveedor activo, usado por {@link com.tecozam.bills.tarjeta.domain.TarjetaNumeroNormalizer}.</li>
 * </ul>
 *
 * <p>No es thread-safe: cada import vive en un único hilo.
 */
public final class ImportContext {

    private final Proveedor proveedor;

    public int centrosCreados;
    public int centrosExistentes;
    public int trabajadoresCreados;
    public int trabajadoresExistentes;
    public int tarjetasCreadas;
    public int tarjetasExistentes;
    public int viatsCreados;
    public int viatsExistentes;
    public int filasIgnoradas;
    public final List<String> errores = new ArrayList<>();

    public final Map<String, CentroCoste> centrosCache = new HashMap<>();
    public final Map<String, Trabajador> trabajadoresCache = new HashMap<>();
    public final Map<String, Tarjeta> tarjetasCache = new HashMap<>();
    public final Map<String, Viat> viatsCache = new HashMap<>();

    public ImportContext(Proveedor proveedor) {
        this.proveedor = proveedor;
    }

    public Proveedor getProveedor() {
        return proveedor;
    }

    public String getCodigoProveedor() {
        return proveedor.getCodigo();
    }
}
