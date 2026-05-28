package com.tecozam.bills.auth.domain;

import com.tecozam.bills.shared.domain.enums.EstadoRegistro;
import com.tecozam.bills.trabajador.domain.Trabajador;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios_campo")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsuarioCampo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(length = 20)
    private String telefono;

    /** Nombre provisional hasta que se vincule un trabajador. */
    @Column(length = 100)
    private String nombre;

    /** Apellidos provisionales hasta que se vincule un trabajador. */
    @Column(length = 100)
    private String apellidos;

    /** DNI provisional hasta que se cree el Trabajador en la aprobación. */
    @Column(name = "dni", length = 20)
    private String dni;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trabajador_id")
    private Trabajador trabajador;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    @Column(name = "refresh_token")
    private String refreshToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_registro", nullable = false, length = 20)
    @Builder.Default
    private EstadoRegistro estadoRegistro = EstadoRegistro.PENDIENTE;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "modificado_en")
    private LocalDateTime modificadoEn;

    @PrePersist
    protected void onPrePersist() {
        this.creadoEn = LocalDateTime.now();
        this.modificadoEn = LocalDateTime.now();
    }

    @PreUpdate
    protected void onPreUpdate() {
        this.modificadoEn = LocalDateTime.now();
    }
}
