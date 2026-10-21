package com.tecozam.bills.factura.application;

import com.tecozam.bills.factura.dto.ImportarFacturaLoteItemDTO;
import com.tecozam.bills.factura.dto.ImportarFacturaLoteResultadoDTO;
import com.tecozam.bills.factura.dto.ImportarFacturaResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Importacion de varias facturas de golpe (ver
 * odd/tasks/facturas-nan-total-y-subida-multiple.md). Se mantiene separado
 * de {@link FacturaService} (que ya tiene toda la logica real de
 * parseo/validacion/persistencia en {@code importar(...)}, reutilizada aqui
 * sin tocarla) para no seguir haciendo crecer esa clase.
 *
 * Cada item del lote se procesa en su propia llamada a
 * {@code facturaService.importar(...)}, que corre en su propia transaccion
 * (bean distinto, sin @Transactional aqui): si un item falla, se captura la
 * excepcion, se marca ese item como fallido y el loop continua con el resto
 * — un PDF roto o un proveedor inexistente no debe tumbar el resto del lote.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FacturaLoteImportService {

    private final FacturaService facturaService;

    public List<ImportarFacturaLoteResultadoDTO> importarLote(
            List<ImportarFacturaLoteItemDTO> items, Map<String, MultipartFile> fileMap) {

        List<ImportarFacturaLoteResultadoDTO> resultados = new ArrayList<>();
        for (int indice = 0; indice < items.size(); indice++) {
            ImportarFacturaLoteItemDTO item = items.get(indice);
            resultados.add(importarItem(indice, item, fileMap));
        }
        return resultados;
    }

    private ImportarFacturaLoteResultadoDTO importarItem(
            int indice, ImportarFacturaLoteItemDTO item, Map<String, MultipartFile> fileMap) {
        try {
            MultipartFile facturaFile = fileMap.get(item.facturaKey());
            MultipartFile extractoFile = item.extractoKey() != null ? fileMap.get(item.extractoKey()) : null;

            ImportarFacturaResponse response = facturaService.importar(item.proveedorId(), facturaFile, extractoFile);

            return new ImportarFacturaLoteResultadoDTO(
                    indice, true, response.facturaId(), response.numFactura(), null);
        } catch (Exception e) {
            log.warn("[FacturaLoteImportService] Item {} del lote fallo: {}", indice, e.getMessage());
            return new ImportarFacturaLoteResultadoDTO(indice, false, null, null, e.getMessage());
        }
    }
}
