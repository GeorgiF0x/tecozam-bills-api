package com.tecozam.bills.factura.infrastructure.parser;

import java.io.InputStream;

public interface FacturaParser {
    FacturaParseResult parse(InputStream pdfInputStream, InputStream extractoInputStream) throws Exception;
}
