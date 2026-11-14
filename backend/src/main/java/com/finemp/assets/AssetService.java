package com.finemp.assets;

import com.finemp.access.CompanyPermission;
import com.finemp.access.CompanyPermissionAuthorizationService;
import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.administration.PagedResponse;
import com.finemp.administration.SaasResourceNotFoundException;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import com.finemp.finance.center.ResultCenter;
import com.finemp.finance.center.ResultCenterRepository;
import com.finemp.finance.partner.BusinessPartner;
import com.finemp.finance.partner.BusinessPartnerRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AssetService {

    private final AssetRepository assets;
    private final CompanyRepository companies;
    private final BusinessPartnerRepository partners;
    private final ResultCenterRepository centers;
    private final CompanyPermissionAuthorizationService permissions;
    private final Clock clock;

    public AssetService(
            AssetRepository assets,
            CompanyRepository companies,
            BusinessPartnerRepository partners,
            ResultCenterRepository centers,
            CompanyPermissionAuthorizationService permissions,
            Clock clock) {
        this.assets = assets;
        this.companies = companies;
        this.partners = partners;
        this.centers = centers;
        this.permissions = permissions;
        this.clock = clock;
    }

    public PagedResponse<AssetResponse> list(
            UUID actor,
            UUID companyId,
            String search,
            AssetStatus status,
            AssetCategory category,
            int page,
            int size) {

        requireRead(actor, companyId);
        if (page < 0 || size < 1 || size > 100 || search != null && search.length() > 120) {
            throw invalid();
        }

        LocalDate today = LocalDate.now(clock);
        var pageable = PageRequest.of(
                page,
                size,
                Sort.by("acquisitionDate").descending().and(Sort.by("id")));

        return PagedResponse.from(
                assets.search(
                        companyId,
                        search == null ? "" : search.trim(),
                        status,
                        category,
                        pageable),
                asset -> AssetResponse.from(asset, today));
    }

    public AssetResponse find(UUID actor, UUID companyId, UUID assetId) {
        requireRead(actor, companyId);
        return AssetResponse.from(owned(companyId, assetId), LocalDate.now(clock));
    }

    public AssetSummaryResponse summary(UUID actor, UUID companyId) {
        requireRead(actor, companyId);
        LocalDate today = LocalDate.now(clock);

        long count = 0;
        BigDecimal acquisition = BigDecimal.ZERO;
        BigDecimal book = BigDecimal.ZERO;
        BigDecimal monthly = BigDecimal.ZERO;

        try (var stream = assets.streamByCompanyAndStatusNot(companyId, AssetStatus.DISPOSED)) {
            var iterator = stream.iterator();
            while (iterator.hasNext()) {
                Asset asset = iterator.next();
                count += 1;
                acquisition = acquisition.add(asset.getAcquisitionValue());
                var input = asset.depreciationInput();
                book = book.add(AssetDepreciationCalculator.bookValue(input, today));
                monthly = monthly.add(
                        AssetDepreciationCalculator.depreciationForMonth(
                                input,
                                java.time.YearMonth.from(today)));
            }
        }

        return new AssetSummaryResponse(
                count,
                money(acquisition),
                money(book),
                money(monthly));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AssetResponse create(UUID actor, UUID companyId, AssetRequest request) {
        Company company = writeOwner(actor, companyId);
        if (request == null || request.expectedVersion() != null) throw invalid();

        AssetValues values = values(companyId, request, false);
        checkTag(companyId, values.assetTag(), null);

        try {
            return AssetResponse.from(
                    assets.saveAndFlush(new Asset(company, values)),
                    LocalDate.now(clock));
        } catch (DataIntegrityViolationException exception) {
            throw new AssetConflictException(AssetConflictException.Reason.DUPLICATE_TAG);
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AssetResponse update(
            UUID actor,
            UUID companyId,
            UUID assetId,
            AssetRequest request) {

        writeOwner(actor, companyId);
        if (request == null || request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw invalid();
        }

        Asset asset = owned(companyId, assetId);
        if (asset.getVersion() != request.expectedVersion()) {
            throw new AssetConflictException(AssetConflictException.Reason.STALE_VERSION);
        }
        if (asset.getStatus() == AssetStatus.DISPOSED) {
            throw new AssetConflictException(AssetConflictException.Reason.STALE_VERSION);
        }

        AssetValues values = values(companyId, request, true);
        checkTag(companyId, values.assetTag(), assetId);

        try {
            asset.replace(values);
            assets.flush();
            return AssetResponse.from(asset, LocalDate.now(clock));
        } catch (DataIntegrityViolationException exception) {
            throw new AssetConflictException(AssetConflictException.Reason.DUPLICATE_TAG);
        }
    }

    private AssetValues values(UUID companyId, AssetRequest request, boolean update) {
        try {
            AssetCategory category = request.category();
            if (category == null || request.acquisitionDate() == null) throw invalid();

            boolean depreciable = request.depreciable() == null
                    ? category.depreciableByDefault()
                    : request.depreciable();

            int life = request.usefulLifeMonths() == null
                    ? category.defaultUsefulLifeMonths()
                    : request.usefulLifeMonths();

            int quantity = request.quantity() == null ? 1 : request.quantity();
            BigDecimal acquisitionValue = money(request.acquisitionValue(), true);
            BigDecimal residual = request.residualPercent() == null
                    || request.residualPercent().isBlank()
                    ? BigDecimal.ZERO.setScale(2)
                    : percent(request.residualPercent());

            if (quantity < 1
                    || life < 0
                    || depreciable && life < 1
                    || residual.signum() < 0
                    || residual.compareTo(new BigDecimal("90")) > 0
                    || request.description() == null
                    || request.description().trim().length() < 2
                    || request.depreciationStartDate() != null
                    && request.depreciationStartDate().isBefore(request.acquisitionDate())) {
                throw invalid();
            }

            AssetStatus status = request.status() == null
                    ? AssetStatus.IN_USE
                    : request.status();

            if (status == AssetStatus.DISPOSED) {
                throw invalid();
            }

            BusinessPartner partner = request.businessPartnerId() == null
                    ? null
                    : partners.findByIdAndCompanyId(request.businessPartnerId(), companyId)
                            .orElseThrow(() -> new SaasResourceNotFoundException("Parceiro comercial"));

            ResultCenter center = request.resultCenterId() == null
                    ? null
                    : centers.findByIdAndCompanyId(request.resultCenterId(), companyId)
                            .orElseThrow(() -> new SaasResourceNotFoundException("Centro de resultado"));

            return new AssetValues(
                    text(request.assetTag()),
                    request.description().trim(),
                    category,
                    text(request.brandModel()),
                    text(request.serialNumber()),
                    request.acquisitionDate(),
                    acquisitionValue,
                    quantity,
                    partner,
                    text(request.documentReference()),
                    center,
                    text(request.location()),
                    text(request.responsible()),
                    life,
                    residual,
                    depreciable,
                    request.depreciationStartDate(),
                    status,
                    request.warrantyUntil(),
                    text(request.notes()));
        } catch (NumberFormatException exception) {
            throw invalid();
        }
    }

    private Company writeOwner(UUID actor, UUID companyId) {
        requireManage(actor, companyId);
        Company company = companies.findLockedById(companyId)
                .orElseThrow(OrganizationAccessDeniedException::new);
        requireManage(actor, companyId);
        return company;
    }

    private Asset owned(UUID companyId, UUID assetId) {
        return assets.findByIdAndCompanyId(assetId, companyId)
                .orElseThrow(() -> new SaasResourceNotFoundException("Bem patrimonial"));
    }

    private void checkTag(UUID companyId, String assetTag, UUID excludeId) {
        if (assetTag != null && assets.existsTag(companyId, assetTag, excludeId)) {
            throw new AssetConflictException(AssetConflictException.Reason.DUPLICATE_TAG);
        }
    }

    private void requireRead(UUID actor, UUID companyId) {
        if (!permissions.hasFreshPermission(actor, companyId, CompanyPermission.ASSET_VIEW)
                && !permissions.hasFreshPermission(actor, companyId, CompanyPermission.ASSET_MANAGE)) {
            throw new OrganizationAccessDeniedException();
        }
    }

    private void requireManage(UUID actor, UUID companyId) {
        if (!permissions.hasFreshPermission(actor, companyId, CompanyPermission.ASSET_MANAGE)) {
            throw new OrganizationAccessDeniedException();
        }
    }

    private static BigDecimal money(String raw, boolean positive) {
        if (raw == null || !raw.matches("\\d+(?:\\.\\d{1,2})?")) throw invalid();
        BigDecimal value = new BigDecimal(raw).setScale(2, RoundingMode.UNNECESSARY);
        if (positive && value.signum() <= 0) throw invalid();
        return value;
    }

    private static BigDecimal percent(String raw) {
        if (raw == null || !raw.matches("\\d+(?:\\.\\d{1,2})?")) throw invalid();
        return new BigDecimal(raw).setScale(2, RoundingMode.UNNECESSARY);
    }

    private static String text(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Parâmetros patrimoniais inválidos.");
    }
}
