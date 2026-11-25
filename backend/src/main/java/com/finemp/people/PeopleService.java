package com.finemp.people;

import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.administration.PagedResponse;
import com.finemp.administration.SaasResourceNotFoundException;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import com.finemp.finance.center.ResultCenter;
import com.finemp.finance.center.ResultCenterRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro operacional de cargos e funcionários por Company (#462).
 * Nenhuma regra de remuneração, custo ou trabalhista nesta fatia.
 */
@Service
@Transactional(readOnly = true)
public class PeopleService {

    private static final int MAX_SEARCH = 120;

    private final JobPositionRepository positions;
    private final EmployeeRepository employees;
    private final CompanyRepository companies;
    private final ResultCenterRepository centers;
    private final PeopleAuthorization authorization;
    private final Clock clock;

    public PeopleService(
            JobPositionRepository positions,
            EmployeeRepository employees,
            CompanyRepository companies,
            ResultCenterRepository centers,
            PeopleAuthorization authorization,
            Clock clock) {
        this.positions = positions;
        this.employees = employees;
        this.companies = companies;
        this.centers = centers;
        this.authorization = authorization;
        this.clock = clock;
    }

    public PagedResponse<JobPositionResponse> listPositions(
            UUID actor, UUID companyId, String search, Boolean active, int page, int size) {
        authorization.requireRead(actor, companyId);
        validatePage(search, page, size);
        var pageable = PageRequest.of(page, size, Sort.by("title").ascending().and(Sort.by("id")));
        return PagedResponse.from(
                positions.search(companyId, normalizedSearch(search), active, pageable),
                JobPositionResponse::from);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JobPositionResponse createPosition(UUID actor, UUID companyId, JobPositionRequest request) {
        Company company = writeOwner(actor, companyId);
        if (request == null || request.expectedVersion() != null) throw invalid();
        String title = requiredText(request.title(), 120);
        checkTitle(companyId, title, null);
        return JobPositionResponse.from(positions.saveAndFlush(new JobPosition(
                company, title, optionalText(request.description(), 4000),
                request.active() == null || request.active())));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JobPositionResponse updatePosition(
            UUID actor, UUID companyId, UUID positionId, JobPositionRequest request) {
        writeOwner(actor, companyId);
        if (request == null || request.expectedVersion() == null || request.expectedVersion() < 0
                || request.active() == null) {
            throw invalid();
        }
        JobPosition position = positions.findByIdAndCompanyId(positionId, companyId)
                .orElseThrow(() -> new SaasResourceNotFoundException("Cargo"));
        if (position.getVersion() != request.expectedVersion()) {
            throw new PeopleConflictException(PeopleConflictException.Reason.STALE_VERSION);
        }
        String title = requiredText(request.title(), 120);
        checkTitle(companyId, title, positionId);
        position.replace(title, optionalText(request.description(), 4000), request.active());
        positions.flush();
        return JobPositionResponse.from(position);
    }

    public PagedResponse<EmployeeResponse> listEmployees(
            UUID actor,
            UUID companyId,
            String search,
            EmploymentType type,
            UUID jobPositionId,
            UUID resultCenterId,
            EmployeeStatus status,
            int page,
            int size) {
        authorization.requireRead(actor, companyId);
        validatePage(search, page, size);
        LocalDate today = LocalDate.now(clock);
        var pageable = PageRequest.of(page, size, Sort.by("fullName").ascending().and(Sort.by("id")));
        return PagedResponse.from(
                employees.search(
                        companyId,
                        normalizedSearch(search),
                        type,
                        jobPositionId,
                        resultCenterId,
                        status == null ? null : status.name(),
                        today,
                        pageable),
                employee -> EmployeeResponse.from(employee, today));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EmployeeResponse createEmployee(UUID actor, UUID companyId, EmployeeRequest request) {
        Company company = writeOwner(actor, companyId);
        if (request == null || request.expectedVersion() != null) throw invalid();
        EmployeeValues values = employeeValues(companyId, request, null);
        return EmployeeResponse.from(employees.saveAndFlush(new Employee(company, values)), LocalDate.now(clock));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EmployeeResponse updateEmployee(
            UUID actor, UUID companyId, UUID employeeId, EmployeeRequest request) {
        writeOwner(actor, companyId);
        if (request == null || request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw invalid();
        }
        Employee employee = employees.findByIdAndCompanyId(employeeId, companyId)
                .orElseThrow(() -> new SaasResourceNotFoundException("Funcionário"));
        if (employee.getVersion() != request.expectedVersion()) {
            throw new PeopleConflictException(PeopleConflictException.Reason.STALE_VERSION);
        }
        employee.replace(employeeValues(companyId, request, employee));
        employees.flush();
        return EmployeeResponse.from(employee, LocalDate.now(clock));
    }

    private EmployeeValues employeeValues(UUID companyId, EmployeeRequest request, Employee current) {
        if (request.employmentType() == null || request.hiredOn() == null
                || request.terminatedOn() != null && request.terminatedOn().isBefore(request.hiredOn())) {
            throw invalid();
        }
        String name = requiredText(request.fullName(), 160);

        JobPosition position = null;
        if (request.jobPositionId() != null) {
            position = positions.findByIdAndCompanyId(request.jobPositionId(), companyId)
                    .orElseThrow(() -> new SaasResourceNotFoundException("Cargo"));
            boolean keepsCurrent = current != null && current.getJobPosition() != null
                    && current.getJobPosition().getId().equals(position.getId());
            // Cargo inativo continua visível em quem já o tem, mas não é atribuído de novo.
            if (!position.isActive() && !keepsCurrent) throw invalid();
        }

        ResultCenter center = request.resultCenterId() == null
                ? null
                : centers.findByIdAndCompanyId(request.resultCenterId(), companyId)
                        .orElseThrow(() -> new SaasResourceNotFoundException("Centro de resultado"));

        return new EmployeeValues(
                name, request.employmentType(), position, center, request.hiredOn(), request.terminatedOn());
    }

    private Company writeOwner(UUID actor, UUID companyId) {
        authorization.requireManage(actor, companyId);
        Company company = companies.findLockedById(companyId)
                .orElseThrow(OrganizationAccessDeniedException::new);
        authorization.requireManage(actor, companyId);
        return company;
    }

    private void checkTitle(UUID companyId, String title, UUID excludeId) {
        if (positions.existsTitle(companyId, title, excludeId)) {
            throw new PeopleConflictException(PeopleConflictException.Reason.DUPLICATE_POSITION);
        }
    }

    private static void validatePage(String search, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || search != null && search.length() > MAX_SEARCH) {
            throw invalid();
        }
    }

    private static String normalizedSearch(String search) {
        return search == null ? "" : search.trim();
    }

    private static String requiredText(String value, int max) {
        if (value == null || value.trim().isEmpty() || value.trim().length() > max) throw invalid();
        return value.trim();
    }

    private static String optionalText(String value, int max) {
        if (value == null || value.trim().isEmpty()) return null;
        if (value.trim().length() > max) throw invalid();
        return value.trim();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Parâmetros de pessoas inválidos.");
    }
}
