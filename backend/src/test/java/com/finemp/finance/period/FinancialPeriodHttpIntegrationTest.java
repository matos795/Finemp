package com.finemp.finance.period;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.accountingfirm.*;
import com.finemp.client.*;
import com.finemp.company.*;
import com.finemp.security.UserAccountPrincipal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"finemp.finance.periods.enabled=true","finemp.finance.periods.commands.enabled=true"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FinancialPeriodHttpIntegrationTest {
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired CompanyRepository companies;@Autowired ClientRepository clients;@Autowired AccountingFirmRepository firms;@Autowired UserAccountRepository users;@Autowired CompanyAccessGrantRepository grants;
    Company owner,other;UserAccount manager,viewer,closer,reopener;final List<UUID> firmIds=new ArrayList<>(),clientIds=new ArrayList<>(),userIds=new ArrayList<>();
    @BeforeEach void prepare() {
        owner=company("Owner","98989898000198");other=company("Other","99999999000199");
        manager=createUser("Manager");viewer=createUser("Viewer");closer=createUser("Closer");reopener=createUser("Reopener");
        grant(manager,Set.of(CompanyPermission.FINANCIAL_PERIOD_CLOSE,CompanyPermission.FINANCIAL_PERIOD_REOPEN));grant(viewer,Set.of(CompanyPermission.FINANCIAL_PERIOD_VIEW));grant(closer,Set.of(CompanyPermission.FINANCIAL_PERIOD_CLOSE));grant(reopener,Set.of(CompanyPermission.FINANCIAL_PERIOD_REOPEN));
    }
    @AfterEach void cleanup() {
        for(var company:List.of(owner,other)) {
            for(var table:List.of("financial_period_command_receipts","financial_periods","access_audit_events"))jdbc.update("DELETE FROM "+table+" WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=?)",company.getId());jdbc.update("DELETE FROM company_access_grants WHERE company_id=?",company.getId());jdbc.update("DELETE FROM companies WHERE id=?",company.getId());
        }
        for(var id:clientIds)jdbc.update("DELETE FROM clients WHERE id=?",id);for(var id:firmIds)jdbc.update("DELETE FROM accounting_firms WHERE id=?",id);for(var id:userIds)jdbc.update("DELETE FROM user_accounts WHERE id=?",id);
    }
    @Test void closeAndReopenMustExposeAuthoritativeStateAndPagedOfficialHistory()throws Exception {
        mvc.perform(get(route()+"/2026-07").with(user(principal(viewer)))).andExpect(status().isOk()).andExpect(jsonPath("$.closed").value(false)).andExpect(jsonPath("$.version").isEmpty());
        close(closer,"2026-07",UUID.randomUUID()).andExpect(status().isOk()).andExpect(jsonPath("$.closed").value(true)).andExpect(jsonPath("$.version").value(0)).andExpect(jsonPath("$.auditEventId").isNotEmpty());
        mvc.perform(post(route()+"/2026-07/reopenings").with(user(principal(reopener))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content("{\"expectedVersion\":0,\"reason\":\"  Ajuste autorizado  \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.closed").value(false)).andExpect(jsonPath("$.version").value(1));
        mvc.perform(get(route()+"/2026-07/history").with(user(principal(viewer))).param("size","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(2)).andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.items[0].action").value("FINANCIAL_PERIOD_REOPENED"))
                .andExpect(jsonPath("$.items[0].reason").value("Ajuste autorizado")).andExpect(jsonPath("$.items[0].beforeState.closed").value(true)).andExpect(jsonPath("$.items[0].afterState.closed").value(false));
        mvc.perform(get(route()+"/2026-07").with(user(principal(viewer)))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
    }
    @Test void collectionMustPageOnlyPersistedStatesWithInclusiveRangesAndClosedFilter()throws Exception {
        close(manager,"2026-07",UUID.randomUUID()).andExpect(status().isOk());close(manager,"2026-09",UUID.randomUUID()).andExpect(status().isOk());
        mvc.perform(get(route()).with(user(principal(viewer))).param("size","1")).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(2)).andExpect(jsonPath("$.items[0].period").value("2026-09"));
        mvc.perform(get(route()).with(user(principal(viewer))).param("from","2026-07").param("to","2026-08").param("closed","true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1)).andExpect(jsonPath("$.items[0].period").value("2026-07"));
        mvc.perform(get(route()+"/2026-08/history").with(user(principal(viewer)))).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
    }
    @Test void eachActionMustRequireItsOwnPermissionAndForeignCompanyMustStayInaccessible()throws Exception {
        close(viewer,"2026-07",UUID.randomUUID()).andExpect(status().isForbidden());close(reopener,"2026-07",UUID.randomUUID()).andExpect(status().isForbidden());
        close(closer,"2026-07",UUID.randomUUID()).andExpect(status().isOk());
        mvc.perform(post(route()+"/2026-07/reopenings").with(user(principal(closer))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content("{\"expectedVersion\":0,\"reason\":\"Motivo\"}"))
                .andExpect(status().isForbidden());
        for(var suffix:List.of("","/2026-07","/2026-07/history"))mvc.perform(get("/api/companies/"+other.getId()+"/financial-periods"+suffix).with(user(principal(manager)))).andExpect(status().isForbidden());
        assertThat(count("financial_periods")).isEqualTo(1);
    }
    @Test void normalizedRouteMustAuthorizeBeforeApplyingTheSharedBodyGate()throws Exception {
        var oversized="x".repeat(65537);
        mvc.perform(post(route()+"/2026-07/closures").with(user(principal(viewer))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(oversized)).andExpect(status().isForbidden());
        var encoded=java.net.URI.create((route()+"/2026-07/closures").replace("financial-periods","%66inancial-periods"));
        mvc.perform(post(encoded).with(user(principal(manager))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(oversized)).andExpect(status().isPayloadTooLarge());
        mvc.perform(post(route()+"/2026-07/closures").with(user(principal(manager))).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content("{\"expectedVersion\":null}")).andExpect(status().isForbidden());
        assertThat(count("financial_periods")).isZero();
    }
    @Test void replayMustPreserveTheFirstReceiptWhileStaleVersionAndChangedKeyContentReturnConflict()throws Exception {
        var key=UUID.randomUUID();String first=close(manager,"2026-07",key).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        close(manager,"2026-07",key).andExpect(status().isOk()).andExpect(content().json(first));close(manager,"2026-08",key).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_MISMATCH"));
        mvc.perform(post(route()+"/2026-07/reopenings").with(user(principal(manager))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content("{\"expectedVersion\":1,\"reason\":\"Motivo\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
        assertThat(count("access_audit_events")).isEqualTo(1);assertThat(count("financial_period_command_receipts")).isEqualTo(1);
    }
    @Test void strictBodyInvalidCalendarAndPaginationMustFailWithoutCreatingState()throws Exception {
        for(var body:List.of("{}","{\"expectedVersion\":null,\"reason\":\"Extra\"}","{\"expectedVersion\":null,\"expectedVersion\":0}"))
            mvc.perform(post(route()+"/2026-07/closures").with(user(principal(manager))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        close(manager,"0000-07",UUID.randomUUID()).andExpect(status().isBadRequest());
        mvc.perform(get(route()).with(user(principal(viewer))).param("size","101")).andExpect(status().isBadRequest());
        mvc.perform(get(route()).with(user(principal(viewer))).param("from","2026-09").param("to","2026-07")).andExpect(status().isBadRequest());assertThat(count("financial_periods")).isZero();
    }
    org.springframework.test.web.servlet.ResultActions close(UserAccount actor,String period,UUID key)throws Exception {return mvc.perform(post(route()+"/"+period+"/closures").with(user(principal(actor))).with(csrf()).header("Idempotency-Key",key).contentType("application/json").content("{\"expectedVersion\":null}"));}
    String route() {return "/api/companies/"+owner.getId()+"/financial-periods";}
    UserAccountPrincipal principal(UserAccount actor) {return new UserAccountPrincipal(actor);}
    long count(String table) {return jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE company_id=?",Long.class,owner.getId());}
    Company company(String name,String cnpj) {var firm=firms.saveAndFlush(new AccountingFirm("Synthetic Period HTTP Firm "+name,true));firmIds.add(firm.getId());var client=clients.saveAndFlush(new Client(firm,"Synthetic Period HTTP Client "+name,true));clientIds.add(client.getId());return companies.saveAndFlush(new Company(client,"Synthetic Period HTTP Company "+name,cnpj));}
    UserAccount createUser(String name) {var row=users.saveAndFlush(new UserAccount("Synthetic Period HTTP "+name,UUID.randomUUID()+"@example.invalid","hash"));userIds.add(row.getId());return row;}
    void grant(UserAccount actor,Set<CompanyPermission> permissions) {grants.saveAndFlush(new CompanyAccessGrant(actor,owner,CompanyAccessRole.CUSTOM,CostCenterScope.ALL,permissions));}
}
