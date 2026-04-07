package com.tecozam.bills.trabajador.domain;

import com.tecozam.bills.shared.domain.BaseEntity;
import com.tecozam.bills.shared.infrastructure.persistence.AesEncryptorConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "trabajadores")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
public class Trabajador extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String apellidos;

    @Column(unique = true)
    private String email;

    @Column(name = "dni_nie")
    @Convert(converter = AesEncryptorConverter.class)
    private String dniNie;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
}
