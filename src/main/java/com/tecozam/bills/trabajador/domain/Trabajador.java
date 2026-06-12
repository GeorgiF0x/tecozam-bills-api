package com.tecozam.bills.trabajador.domain;

import com.tecozam.bills.shared.domain.BaseEntity;
import com.tecozam.bills.shared.infrastructure.persistence.AesEncryptorConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

    /**
     * Origen del registro (BILLS-10). IMPORTACION por defecto para
     * trabajadores creados desde importes de tarjetas / facturas; se
     * sobrescribe a OFICINA / CAMPO cuando es promovido desde alta de
     * usuario.
     */
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private OrigenTrabajador origen = OrigenTrabajador.IMPORTACION;
}
