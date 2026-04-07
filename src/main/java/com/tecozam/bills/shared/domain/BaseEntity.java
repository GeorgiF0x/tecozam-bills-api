package com.tecozam.bills.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "modificado_en")
    private LocalDateTime modificadoEn;

    @Column(name = "eliminado_en")
    private LocalDateTime eliminadoEn;

    @PrePersist
    protected void onPrePersist() {
        if (this.creadoEn == null) {
            this.creadoEn = LocalDateTime.now();
        }
        this.modificadoEn = LocalDateTime.now();
    }

    @PreUpdate
    protected void onPreUpdate() {
        this.modificadoEn = LocalDateTime.now();
    }

    public boolean isEliminado() {
        return this.eliminadoEn != null;
    }

    public void softDelete() {
        this.eliminadoEn = LocalDateTime.now();
    }

    public void restore() {
        this.eliminadoEn = null;
    }
}
