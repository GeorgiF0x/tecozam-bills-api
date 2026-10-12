package com.tecozam.bills.ticket.infrastructure.parser;

/** Bytes crudos de una foto de ticket + su content-type, para enviar a Vision. */
public record ImagenTicket(byte[] bytes, String mimeType) {
}
