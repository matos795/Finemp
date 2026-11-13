package com.finemp.contracts;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.finemp.access.CompanyPermission;
import com.finemp.access.CompanyPermissionAuthorizationService;
import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.administration.PagedResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MonthlyContractBillingPreviewService {

    private final ContractRepository contracts;
    private final CompanyPermissionAuthorizationService permissions;

    public MonthlyContractBillingPreviewService(
            ContractRepository contracts,
            CompanyPermissionAuthorizationService permissions) {
        this.contracts = contracts;
        this.permissions = permissions;
    }

    public Report report(
            UUID actor,
            UUID companyId,
            YearMonth month,
            int page,
            int size) {
        requireRead(actor, companyId);
        if (month == null || page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Parâmetros de faturamento inválidos.");
        }

        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();
        var pageable = PageRequest.of(
                page,
                size,
                Sort.by("values.billingDay").ascending()
                        .and(Sort.by("values.description").ascending())
                        .and(Sort.by("id")));

        var rows = PagedResponse.from(
                contracts.monthlyPreview(companyId, monthStart, monthEnd, pageable),
                contract -> line(contract, month));

        BigDecimal income = money(contracts.monthlyPreviewTotal(
                companyId,
                ContractDirection.INCOME,
                monthStart,
                monthEnd));
        BigDecimal expense = money(contracts.monthlyPreviewTotal(
                companyId,
                ContractDirection.EXPENSE,
                monthStart,
                monthEnd));

        return new Report(
                month.toString(),
                income,
                expense,
                income.subtract(expense).setScale(2, RoundingMode.UNNECESSARY),
                rows);
    }

    private static Line line(Contract contract, YearMonth month) {
        var values = contract.getValues();
        var partner = contract.getBusinessPartner();
        String partnerName = partner.getProfile().tradeName().isBlank()
                ? partner.getProfile().name()
                : partner.getProfile().tradeName();

        return new Line(
                contract.getId(),
                partner.getId(),
                partnerName,
                values.direction(),
                values.description(),
                ContractBillingDate.in(month, values.billingDay()),
                values.monthlyAmount());
    }

    private void requireRead(UUID actor, UUID companyId) {
        if (!permissions.hasFreshPermission(actor, companyId, CompanyPermission.CONTRACT_VIEW)
                && !permissions.hasFreshPermission(actor, companyId, CompanyPermission.CONTRACT_MANAGE)) {
            throw new OrganizationAccessDeniedException();
        }
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value)
                .setScale(2, RoundingMode.UNNECESSARY);
    }

    public record Line(
            UUID contractId,
            UUID businessPartnerId,
            String businessPartnerName,
            ContractDirection direction,
            String description,
            LocalDate billingDate,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount) {
    }

    public record Report(
            String month,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal incomeTotal,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal expenseTotal,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal netTotal,
            PagedResponse<Line> contracts) {
    }
}
