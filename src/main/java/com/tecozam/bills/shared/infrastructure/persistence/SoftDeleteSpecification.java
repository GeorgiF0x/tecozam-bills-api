package com.tecozam.bills.shared.infrastructure.persistence;

import com.tecozam.bills.shared.domain.BaseEntity;
import org.springframework.data.jpa.domain.Specification;

/**
 * Reusable JPA Specifications for soft-delete filtering.
 * <p>
 * Usage in any repository or service:
 * <pre>
 *   repository.findAll(SoftDeleteSpecification.notDeleted());
 *   repository.findAll(SoftDeleteSpecification.notDeleted().and(otherSpec));
 *   repository.findAll(SoftDeleteSpecification.includingDeleted()); // no filter
 * </pre>
 */
public final class SoftDeleteSpecification {

    private SoftDeleteSpecification() {
        // Utility class — no instantiation
    }

    /**
     * Filters out soft-deleted records ({@code eliminadoEn IS NULL}).
     */
    public static <T extends BaseEntity> Specification<T> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("eliminadoEn"));
    }

    /**
     * Filters only soft-deleted records ({@code eliminadoEn IS NOT NULL}).
     */
    public static <T extends BaseEntity> Specification<T> onlyDeleted() {
        return (root, query, cb) -> cb.isNotNull(root.get("eliminadoEn"));
    }

    /**
     * No filter — returns all records including soft-deleted ones.
     */
    public static <T extends BaseEntity> Specification<T> includingDeleted() {
        return Specification.where(null);
    }
}
