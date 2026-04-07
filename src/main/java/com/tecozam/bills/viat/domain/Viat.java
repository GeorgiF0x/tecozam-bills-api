package com.tecozam.bills.viat.domain;

import com.tecozam.bills.shared.domain.BaseEntity;
import com.tecozam.bills.shared.domain.enums.EstadoRecurso;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "viats")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
public class Viat extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String codigo;

    @Column(name = "numero_serie", length = 100)
    private String numeroSerie;

    @Column(length = 300)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EstadoRecurso estado = EstadoRecurso.DISPONIBLE;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
}
