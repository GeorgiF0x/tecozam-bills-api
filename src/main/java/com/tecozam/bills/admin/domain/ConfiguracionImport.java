package com.tecozam.bills.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Fila unica de configuracion global: si el modo LLM esta activo, el import
 * (listado de tarjetas y facturas/extracto) usa un LLM en vez de los parsers
 * deterministas. No extiende BaseEntity: es un ajuste de sistema, no un
 * recurso de negocio con borrado logico.
 */
@Entity
@Table(name = "configuracion_import")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfiguracionImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "modo_llm_activo", nullable = false)
    @Builder.Default
    private boolean modoLlmActivo = false;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    @Column(name = "actualizado_por", length = 80)
    private String actualizadoPor;
}
