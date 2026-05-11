package com.tecozam.bills.vehiculo.domain;

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
@Table(name = "vehiculos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
public class Vehiculo extends BaseEntity {

    @Column(length = 20)
    private String matricula;

    @Column(name = "codigo_obra", length = 50)
    private String codigoObra;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", nullable = false, length = 30)
    @Builder.Default
    private CategoriaRecurso categoria = CategoriaRecurso.VEHICULO;

    @Column(nullable = false, length = 30)
    private String tipo;

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
