package com.finemp.audit;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccessAuditRecorder {

    private final EntityManager entityManager;
    private final Clock clock;

    public AccessAuditRecorder(EntityManager entityManager, Clock clock) {
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID record(AccessAuditAction action, UUID actorId, UUID subjectUserId,
                       UUID firmId, UUID companyId, Set<? extends Enum<?>> permissions) {
        var event = new AccessAuditEvent(action, actorId, subjectUserId, firmId, companyId,
                clock.instant(), permissions);
        entityManager.persist(event);
        return event.getId();
    }
}
