package com.tecozam.bills.audit.infrastructure.persistence;

import com.tecozam.bills.audit.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByTablaAndRegistroIdOrderByFechaDesc(String tabla, Long registroId);
}
