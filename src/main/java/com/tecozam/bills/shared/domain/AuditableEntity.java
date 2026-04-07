package com.tecozam.bills.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@MappedSuperclass
@Getter
@Setter
public abstract class AuditableEntity extends BaseEntity {

    @Column(name = "creado_por", updatable = false)
    private String creadoPor;

    @Column(name = "modificado_por")
    private String modificadoPor;

    @Override
    @PrePersist
    protected void onPrePersist() {
        super.onPrePersist();
        String currentUser = resolveCurrentUser();
        if (this.creadoPor == null) {
            this.creadoPor = currentUser;
        }
        this.modificadoPor = currentUser;
    }

    @Override
    @PreUpdate
    protected void onPreUpdate() {
        super.onPreUpdate();
        this.modificadoPor = resolveCurrentUser();
    }

    private String resolveCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            return authentication.getName();
        }
        return "SYSTEM";
    }
}
