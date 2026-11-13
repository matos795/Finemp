package com.finemp.sales;

import com.finemp.administration.SaasResourceNotFoundException;
import com.finemp.finance.partner.BusinessPartnerRepository;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class SalesInsightsService {

    private final OpportunityRepository opportunities;
    private final SalesActivityRepository activities;
    private final BusinessPartnerRepository partners;
    private final SalesAuthorization authorization;
    private final Clock clock;

    public SalesInsightsService(
            OpportunityRepository opportunities,
            SalesActivityRepository activities,
            BusinessPartnerRepository partners,
            SalesAuthorization authorization,
            Clock clock) {
        this.opportunities = opportunities;
        this.activities = activities;
        this.partners = partners;
        this.authorization = authorization;
        this.clock = clock;
    }

    public SalesIndicatorsResponse indicators(UUID actor, UUID companyId) {
        authorization.requireRead(actor, companyId);
        var all = opportunities.findAllByCompanyId(companyId);

        var metrics = all.stream()
                .map(opportunity -> new SalesIndicatorsCalculator.OpportunityMetric(
                        opportunity.getStatus(),
                        opportunity.getValues().monthlyAmount(),
                        opportunity.getValues().probability(),
                        opportunity.getCreatedAt(),
                        opportunity.getWonAt()))
                .toList();

        var calculated = SalesIndicatorsCalculator.calculate(metrics);
        YearMonth current = YearMonth.now(clock);
        Map<YearMonth, BigDecimal> forecast = new TreeMap<>();

        all.stream()
                .filter(opportunity -> opportunity.getStatus() == OpportunityStatus.OPEN)
                .forEach(opportunity -> {
                    var values = opportunity.getValues();
                    YearMonth expected = values.expectedCloseOn() == null
                            ? current
                            : YearMonth.from(values.expectedCloseOn());
                    YearMonth bucket = expected.isBefore(current) ? current : expected;
                    BigDecimal weighted = values.monthlyAmount()
                            .multiply(BigDecimal.valueOf(values.probability()))
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    forecast.merge(bucket, weighted, BigDecimal::add);
                });

        return new SalesIndicatorsResponse(
                calculated.wonCount(),
                calculated.lostCount(),
                calculated.conversionRate(),
                calculated.averageWonTicket(),
                calculated.averageSalesCycleDays(),
                calculated.weightedOpenPipeline(),
                calculated.annualizedWonValue(),
                forecast.entrySet().stream()
                        .map(entry -> SalesIndicatorsResponse.ForecastMonth.of(
                                entry.getKey(),
                                entry.getValue().setScale(2, RoundingMode.UNNECESSARY)))
                        .toList());
    }

    public SalesProfileResponse profile(
            UUID actor,
            UUID companyId,
            UUID partnerId) {
        authorization.requireRead(actor, companyId);
        if (partnerId == null) throw new IllegalArgumentException("Parceiro comercial inválido.");

        var partner = partners.findByIdAndCompanyId(partnerId, companyId)
                .orElseThrow(() -> new SaasResourceNotFoundException("Parceiro comercial"));
        var partnerOpportunities =
                opportunities.findAllByCompanyIdAndBusinessPartnerIdOrderByCreatedAtDesc(
                        companyId,
                        partnerId);
        LocalDate today = LocalDate.now(clock);

        return new SalesProfileResponse(
                partnerId,
                partner.getProfile().name(),
                partner.getProfile().active(),
                partnerOpportunities.stream()
                        .filter(opportunity -> opportunity.getStatus() == OpportunityStatus.OPEN)
                        .count(),
                partnerOpportunities.stream()
                        .filter(opportunity -> opportunity.getStatus() == OpportunityStatus.WON)
                        .count(),
                partnerOpportunities.stream()
                        .filter(opportunity -> opportunity.getStatus() == OpportunityStatus.LOST)
                        .count(),
                activities.countByCompanyIdAndBusinessPartnerIdAndCompletedAtIsNull(
                        companyId,
                        partnerId),
                activities.countOverdueByPartner(companyId, partnerId, today),
                today);
    }
}
