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
    public UUID recordFinancialPeriod(AccessAuditAction action,UUID actor,UUID company,UUID resource,UUID command,
            String before,String after,String reason,Set<? extends Enum<?>> permissions) {
        if(action!=AccessAuditAction.FINANCIAL_PERIOD_CLOSED && action!=AccessAuditAction.FINANCIAL_PERIOD_REOPENED)
            throw new IllegalArgumentException("Ação de período inválida.");
        var event=new AccessAuditEvent(action,actor,actor,null,company,clock.instant(),permissions);
        event.resource("FINANCIAL_PERIOD",resource,command,before,after,reason);entityManager.persist(event);return event.getId();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID recordFinancialWithholding(AccessAuditAction action,UUID actor,UUID company,UUID resource,UUID command,
            String before,String after,Set<? extends Enum<?>> permissions) {
        boolean valid=switch(action) {
            case FINANCIAL_WITHHOLDING_CREATED -> before==null && after!=null;
            case FINANCIAL_WITHHOLDING_UPDATED -> before!=null && after!=null;
            case FINANCIAL_WITHHOLDING_DELETED -> before!=null && after==null;
            default -> false;
        };
        if(!valid)throw new IllegalArgumentException("Transição de retenção inválida.");
        var event=new AccessAuditEvent(action,actor,actor,null,company,clock.instant(),permissions);
        event.resource("FINANCIAL_WITHHOLDING",resource,command,before,after,null);entityManager.persist(event);return event.getId();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID recordStatementImport(UUID actor,UUID company,UUID bank,UUID command,String after,Set<? extends Enum<?>> permissions) {
        if(after==null)throw new IllegalArgumentException("Estado de importação ausente.");
        var event=new AccessAuditEvent(AccessAuditAction.FINANCIAL_STATEMENT_IMPORTED,actor,actor,null,company,clock.instant(),permissions);
        event.resource("FINANCIAL_STATEMENT_IMPORT",bank,command,null,after,null);entityManager.persist(event);return event.getId();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID recordStatementIgnored(UUID actor,UUID company,UUID item,UUID command,String before,String after,Set<? extends Enum<?>> permissions) {
        if(before==null || after==null)throw new IllegalArgumentException("Estado de extrato ausente.");
        var event=new AccessAuditEvent(AccessAuditAction.FINANCIAL_STATEMENT_IGNORED,actor,actor,null,company,clock.instant(),permissions);
        event.resource("FINANCIAL_STATEMENT_ITEM",item,command,before,after,null);entityManager.persist(event);return event.getId();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID recordStatementReconciled(UUID actor,UUID company,UUID item,UUID command,String before,String after,Set<? extends Enum<?>> permissions) {
        if(before==null || after==null)throw new IllegalArgumentException("Estado de conciliação ausente.");
        var event=new AccessAuditEvent(AccessAuditAction.FINANCIAL_STATEMENT_RECONCILED,actor,actor,null,company,clock.instant(),permissions);
        event.resource("FINANCIAL_STATEMENT_ITEM",item,command,before,after,null);entityManager.persist(event);return event.getId();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID recordHierarchyDeletion(
            AccessAuditAction action,
            UUID actorId,
            UUID accountingFirmId,
            String resourceType,
            UUID resourceId) {

        if (action != AccessAuditAction.CLIENT_DELETED
                && action != AccessAuditAction.ACCOUNTING_FIRM_DELETED) {
            throw new IllegalArgumentException("Ação de exclusão hierárquica inválida.");
        }

        var event = new AccessAuditEvent(
                action,
                actorId,
                actorId,
                accountingFirmId,
                null,
                clock.instant(),
                Set.of());

        event.resource(
                resourceType,
                resourceId,
                UUID.randomUUID(),
                "{\"exists\":true}",
                null,
                "EMPTY_HIERARCHY_DELETION");

        entityManager.persist(event);
        return event.getId();
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
