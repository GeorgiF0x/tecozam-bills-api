package com.tecozam.bills.shared.infrastructure.exception;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logAppender);
    }

    @Test
    void handleGeneric_registraLaExcepcionOriginalEnElLog() {
        RuntimeException ex = new RuntimeException("fallo inesperado en un servicio interno");
        ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest("POST", "/api/facturas/importar"));

        var response = handler.handleGeneric(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(logAppender.list).hasSize(1);
        ILoggingEvent event = logAppender.list.get(0);
        assertThat(event.getLevel().toString()).isEqualTo("ERROR");
        assertThat(event.getThrowableProxy().getMessage()).isEqualTo("fallo inesperado en un servicio interno");
        assertThat(event.getFormattedMessage()).contains("/api/facturas/importar");
    }
}
