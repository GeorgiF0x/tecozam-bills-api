package com.tecozam.bills.factura.infrastructure.parser;

import com.tecozam.bills.factura.domain.Factura;
import com.tecozam.bills.factura.domain.FacturaConceptoResumen;
import com.tecozam.bills.factura.domain.TarjetaResumen;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class FacturaParseResult {
    private Factura factura;
    private List<FacturaConceptoResumen> conceptos;
    private List<TarjetaResumen> tarjetaResumenes;
}
