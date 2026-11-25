package com.finemp.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.finemp.TestcontainersConfiguration;
import com.finemp.access.CompanyAccessGrant;
import com.finemp.access.CompanyAccessGrantRepository;
import com.finemp.access.CompanyAccessRole;
import com.finemp.access.CompanyPermission;
import com.finemp.access.CostCenterScope;
import com.finemp.access.UserAccount;
import com.finemp.access.UserAccountRepository;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Synthetic people records only: no real names, documents, salaries or personal data. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class PeopleSecurityHttpIntegrationTest {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    @Autowired private MockMvc mvc;
    @Autowired private UserAccountRepository users;
    @Autowired private CompanyRepository companies;
    @Autowired private CompanyAccessGrantRepository grants;

    @Test
    void viewerCanReadPeopleButCannotWrite() throws Exception {
        var actor = actor("people-viewer");
        var company = company("77777777000191");
        grant(actor, company, CompanyAccessRole.VIEWER, CompanyPermission.EMPLOYEE_VIEW);

        mvc.perform(get("/api/companies/{companyId}/job-positions", company.getId()).with(as(actor)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/companies/{companyId}/employees", company.getId()).with(as(actor)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/companies/{companyId}/job-positions", company.getId())
                        .with(as(actor)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(position("Analista sintético", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PEOPLE_ACCESS_DENIED"));
        mvc.perform(post("/api/companies/{companyId}/employees", company.getId())
                        .with(as(actor)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employee("Pessoa Sintética", "CLT", null, null, "2026-01-05", null, null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void userWithoutPeoplePermissionAndForeignCompanyAreDenied() throws Exception {
        var actor = actor("people-outsider");
        var owned = company("77777777000183");
        var foreign = company("77777777000175");
        grant(actor, owned, CompanyAccessRole.ADMIN, CompanyPermission.ASSET_MANAGE);

        mvc.perform(get("/api/companies/{companyId}/employees", owned.getId()).with(as(actor)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/companies/{companyId}/job-positions", foreign.getId()).with(as(actor)))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerKeepsPositionsAndEmployeesScopedToTheCompany() throws Exception {
        var actor = actor("people-manager");
        var owned = company("77777777000167");
        var other = company("77777777000159");
        grant(actor, owned, CompanyAccessRole.ADMIN, CompanyPermission.EMPLOYEE_MANAGE);
        grant(actor, other, CompanyAccessRole.ADMIN, CompanyPermission.EMPLOYEE_MANAGE);

        JsonNode position = body(mvc.perform(post("/api/companies/{companyId}/job-positions", owned.getId())
                        .with(as(actor)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(position("Analista sintético", "Responsabilidades sintéticas.")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Analista sintético"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(0)));

        mvc.perform(post("/api/companies/{companyId}/job-positions", owned.getId())
                        .with(as(actor)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(position("  ANALISTA SINTÉTICO ", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PEOPLE_DUPLICATE_POSITION"));

        String positionId = position.get("id").asString();

        mvc.perform(post("/api/companies/{companyId}/employees", owned.getId())
                        .with(as(actor)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employee("Pessoa Sintética", "CLT", positionId, null, "2026-01-05", null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName").value("Pessoa Sintética"))
                .andExpect(jsonPath("$.employmentType").value("CLT"))
                .andExpect(jsonPath("$.jobPositionTitle").value("Analista sintético"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.salary").doesNotExist())
                .andExpect(jsonPath("$.cpf").doesNotExist())
                .andExpect(jsonPath("$.birthDate").doesNotExist());

        // A position from another Company is never assignable, even with access there.
        mvc.perform(post("/api/companies/{companyId}/employees", other.getId())
                        .with(as(actor)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employee("Outra Pessoa", "PJ", positionId, null, "2026-01-05", null, null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PEOPLE_NOT_FOUND"));

        mvc.perform(get("/api/companies/{companyId}/employees", other.getId()).with(as(actor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void employeeRulesStatusAndOptimisticLocking() throws Exception {
        var actor = actor("people-rules");
        var company = company("77777777000140");
        grant(actor, company, CompanyAccessRole.ADMIN, CompanyPermission.EMPLOYEE_MANAGE);

        mvc.perform(post("/api/companies/{companyId}/employees", company.getId())
                        .with(as(actor)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employee("Pessoa Sintética", "CLT", null, null, "2026-03-01", "2026-02-01", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PEOPLE"));

        mvc.perform(post("/api/companies/{companyId}/employees", company.getId())
                        .with(as(actor)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employee("Pessoa Sintética", "OUTRO", null, null, "2026-03-01", null, null)))
                .andExpect(status().isBadRequest());

        JsonNode created = body(mvc.perform(post("/api/companies/{companyId}/employees", company.getId())
                        .with(as(actor)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employee("Pessoa Desligada", "EST", null, null, "2025-01-10", "2025-06-30", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("TERMINATED")));

        String id = created.get("id").asString();

        mvc.perform(get("/api/companies/{companyId}/employees", company.getId())
                        .param("status", "TERMINATED").with(as(actor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get("/api/companies/{companyId}/employees", company.getId())
                        .param("status", "ACTIVE").with(as(actor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));

        mvc.perform(put("/api/companies/{companyId}/employees/{id}", company.getId(), id)
                        .with(as(actor)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employee("Pessoa Readmitida", "CLT", null, null, "2025-01-10", null, 0L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(1));

        mvc.perform(put("/api/companies/{companyId}/employees/{id}", company.getId(), id)
                        .with(as(actor)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employee("Edição antiga", "CLT", null, null, "2025-01-10", null, 0L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PEOPLE_STALE_VERSION"));
    }

    @Test
    void inactivePositionCannotBeAssignedToNewEmployees() throws Exception {
        var actor = actor("people-inactive");
        var company = company("77777777000132");
        grant(actor, company, CompanyAccessRole.ADMIN, CompanyPermission.EMPLOYEE_MANAGE);

        JsonNode position = body(mvc.perform(post("/api/companies/{companyId}/job-positions", company.getId())
                        .with(as(actor)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(position("Cargo sintético", null)))
                .andExpect(status().isCreated()));
        String id = position.get("id").asString();

        mvc.perform(put("/api/companies/{companyId}/job-positions/{id}", company.getId(), id)
                        .with(as(actor)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Cargo sintético","description":null,"active":false,"expectedVersion":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mvc.perform(post("/api/companies/{companyId}/employees", company.getId())
                        .with(as(actor)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employee("Pessoa Sintética", "CLT", id, null, "2026-01-05", null, null)))
                .andExpect(status().isBadRequest());
    }

    private UserAccount actor(String key) {
        return users.saveAndFlush(new UserAccount("Synthetic " + key, key + "@example.invalid", "hash"));
    }

    private Company company(String cnpj) {
        return companies.saveAndFlush(new Company("Synthetic People Company " + cnpj, cnpj));
    }

    private void grant(UserAccount actor, Company company, CompanyAccessRole role, CompanyPermission permission) {
        grants.saveAndFlush(new CompanyAccessGrant(actor, company, role, CostCenterScope.ALL, Set.of(permission)));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor as(UserAccount actor) {
        return user(new UserAccountPrincipal(actor));
    }

    private static JsonNode body(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return JSON.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static String position(String title, String description) throws Exception {
        var node = JSON.createObjectNode();
        node.put("title", title);
        node.put("description", description);
        node.put("active", true);
        node.putNull("expectedVersion");
        return JSON.writeValueAsString(node);
    }

    private static String employee(String name, String type, String positionId, String centerId,
                                   String hiredOn, String terminatedOn, Long version) throws Exception {
        var node = JSON.createObjectNode();
        node.put("fullName", name);
        node.put("employmentType", type);
        node.put("jobPositionId", positionId);
        node.put("resultCenterId", centerId);
        node.put("hiredOn", hiredOn);
        node.put("terminatedOn", terminatedOn);
        if (version == null) node.putNull("expectedVersion"); else node.put("expectedVersion", version);
        return JSON.writeValueAsString(node);
    }
}
