package com.finemp.finance.bank;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.accountingfirm.*;
import com.finemp.client.*;
import com.finemp.company.*;
import com.finemp.security.UserAccountPrincipal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties="finemp.finance.catalog.enabled=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BankAccountIntegrationTest{
    @Autowired BankAccountService service;
    @Autowired BankAccountRepository banks;
    @Autowired CompanyRepository companies;
    @Autowired ClientRepository clients;
    @Autowired AccountingFirmRepository firms;
    @Autowired UserAccountRepository users;
    @Autowired CompanyAccessGrantRepository grants;
    @Autowired AccountingFirmAccessGrantRepository memberships;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean(name="financialStatementBankReferences") BankAccountReferenceGuard usage;
    private UserAccount actor,viewer,admin,restricted;
    private Company owner,other;
    private UUID firmId,clientId,bankId,foreignId;
    private final List<UUID> ownUsers=new ArrayList<>();
    @BeforeEach void prepare(){
        var firm=firms.saveAndFlush(new AccountingFirm("Synthetic Financial Firm",true));firmId=firm.getId();
        var client=clients.saveAndFlush(new Client(firm,"Synthetic Financial Client",true));clientId=client.getId();
        owner=companies.saveAndFlush(new Company(client,"Synthetic Financial Owner","61616161000161"));
        other=companies.saveAndFlush(new Company(client,"Synthetic Financial Other","62626262000162"));
        actor=createUser("Manager");viewer=createUser("Viewer");admin=createUser("Administrator");restricted=createUser("Restricted");
        grant(actor,CompanyAccessRole.CUSTOM,CostCenterScope.ALL,Set.of(CompanyPermission.BANK_ACCOUNT_MANAGE));
        grant(viewer,CompanyAccessRole.VIEWER,CostCenterScope.ALL,Set.of(CompanyPermission.BANK_ACCOUNT_VIEW));
        grant(admin,CompanyAccessRole.ADMIN,CostCenterScope.ALL,Set.of());
        grant(restricted,CompanyAccessRole.CUSTOM,CostCenterScope.RESTRICTED,Set.of(CompanyPermission.BANK_ACCOUNT_VIEW,CompanyPermission.BANK_ACCOUNT_MANAGE));
        memberships.saveAndFlush(new AccountingFirmAccessGrant(admin,firm,Set.of(AccountingFirmPermission.USER_ACCESS_MANAGE)));
        bankId=banks.saveAndFlush(new BankAccount(owner,"A Synthetic Cash",BankAccountType.CASH,new BigDecimal("1.23"),LocalDate.of(2026,7,1),true)).getId();
        foreignId=banks.saveAndFlush(new BankAccount(other,"Other Isolated Account",BankAccountType.OTHER,new BigDecimal("999.99"),LocalDate.of(2026,7,1),false)).getId();
    }
    @AfterEach void cleanOnlyOwnedSyntheticFixtures(){
        for(var company:List.of(owner,other)){
            jdbc.update("DELETE FROM financial_bank_accounts WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=?)",company.getId());
            jdbc.update("DELETE FROM company_access_grants WHERE company_id=?",company.getId());jdbc.update("DELETE FROM companies WHERE id=?",company.getId());
        }
        jdbc.update("DELETE FROM accounting_firm_access_grant_permissions WHERE accounting_firm_access_grant_id IN (SELECT id FROM accounting_firm_access_grants WHERE accounting_firm_id=?)",firmId);
        jdbc.update("DELETE FROM accounting_firm_access_grants WHERE accounting_firm_id=?",firmId);
        jdbc.update("DELETE FROM clients WHERE id=?",clientId);jdbc.update("DELETE FROM accounting_firms WHERE id=?",firmId);
        for(var id:ownUsers)jdbc.update("DELETE FROM user_accounts WHERE id=?",id);
    }
    @Test void shouldEnforceExplicitFinancePermissionAndFullScopeWithoutAdministrativeBypass()throws Exception{
        mvc.perform(get(route()).with(user(new UserAccountPrincipal(viewer)))).andExpect(status().isOk());
        for(var denied:List.of(admin,restricted))
            mvc.perform(get(route()).with(user(new UserAccountPrincipal(denied)))).andExpect(status().isForbidden());
        mvc.perform(post(route()).with(user(new UserAccountPrincipal(viewer))).with(csrf()).contentType("application/json").content(body("New","0.00",null))).andExpect(status().isForbidden());
        assertThat(banks.countByCompanyId(owner.getId())).isEqualTo(1);
    }
    @Test void shouldPersistExactMoneyReturnUpdatedVersionAndRefuseLostUpdates()throws Exception{
        var response=mvc.perform(post(route()).with(user(new UserAccountPrincipal(actor))).with(csrf()).contentType("application/json").content(body("New","-1.2300",null)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.companyId").value(owner.getId().toString()))
                .andExpect(jsonPath("$.openingBalance").value("-1.23")).andExpect(jsonPath("$.version").value(0)).andReturn();
        var id=UUID.fromString(tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.getResponse().getContentAsByteArray()).get("id").asString());
        mvc.perform(put(route()+"/"+id).with(user(new UserAccountPrincipal(actor))).with(csrf()).contentType("application/json").content(body("Updated","-2.34",0L)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.openingBalance").value("-2.34")).andExpect(jsonPath("$.version").value(1));
        mvc.perform(put(route()+"/"+id).with(user(new UserAccountPrincipal(actor))).with(csrf()).contentType("application/json").content(body("Stale","7.00",0L)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
        assertThat(banks.findById(id).orElseThrow().getOpeningBalance()).isEqualByComparingTo("-2.34");
        mvc.perform(post(route()).with(user(new UserAccountPrincipal(actor))).with(csrf()).contentType("application/json").content(body("Bad","1.005",null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_BANK_ACCOUNT"));
    }
    @Test void shouldScopePagingSearchAndEveryRecordLookupToCompany()throws Exception{
        mvc.perform(get(route()).with(user(new UserAccountPrincipal(actor))).param("page","0").param("size","1").param("search","SYNTHETIC").param("type","CASH").param("active","true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1)).andExpect(jsonPath("$.items[0].id").value(bankId.toString()));
        mvc.perform(get(route()).with(user(new UserAccountPrincipal(actor))).param("search","%"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get(route()+"/"+foreignId).with(user(new UserAccountPrincipal(actor)))).andExpect(status().isNotFound());
        mvc.perform(put(route()+"/"+foreignId).with(user(new UserAccountPrincipal(actor))).with(csrf()).contentType("application/json").content(body("Attack","0.00",0L)))
                .andExpect(status().isNotFound());
        assertThat(banks.findById(foreignId).orElseThrow().getOpeningBalance()).isEqualByComparingTo("999.99");
        mvc.perform(get(route()).with(user(new UserAccountPrincipal(actor))).param("size","101")).andExpect(status().isBadRequest());
    }
    @Test void shouldRevokeFinancialPermissionWithoutTrustingAnExistingPrincipal()throws Exception{
        var principal=new UserAccountPrincipal(actor);
        jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=? AND user_account_id=?)",owner.getId(),actor.getId());
        mvc.perform(get(route()).with(user(principal))).andExpect(status().isForbidden());
        mvc.perform(delete(route()+"/"+bankId).with(user(principal)).with(csrf()).param("version","0")).andExpect(status().isForbidden());
        assertThat(banks.countByCompanyId(owner.getId())).isEqualTo(1);
    }
    @Test void shouldKeepTheLastRowAndDeactivateReferencedHistoryInsteadOfDeletingIt()throws Exception{
        mvc.perform(delete(route()+"/"+bankId).with(user(new UserAccountPrincipal(actor))).with(csrf()).param("version","0"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_ACCOUNT"));
        when(usage.hasReferences(owner.getId(),bankId)).thenReturn(true);
        mvc.perform(delete(route()+"/"+bankId).with(user(new UserAccountPrincipal(actor))).with(csrf()).param("version","0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.action").value("DEACTIVATED"));
        assertThat(banks.countByCompanyId(owner.getId())).isEqualTo(1);assertThat(banks.findById(bankId).orElseThrow().isActive()).isFalse();
    }
    @Test void shouldSerializeConcurrentDeletesSoTheyCannotRemoveBothRemainingRows()throws Exception{
        UUID second=banks.saveAndFlush(new BankAccount(owner,"Second",BankAccountType.CURRENT,BigDecimal.ZERO,LocalDate.of(2026,7,1),false)).getId();
        var pool=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try{
            var first=pool.submit(()->removeAfter(start,ready,bankId));var last=pool.submit(()->removeAfter(start,ready,second));
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
            assertThat(List.of(first.get(10,TimeUnit.SECONDS),last.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(banks.countByCompanyId(owner.getId())).isEqualTo(1);
        }finally{start.countDown();pool.shutdownNow();}
    }
    @Test void shouldRejectAnAmbientRepeatableReadTransactionBeforeMutation(){
        var template=new TransactionTemplate(transactions);template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        assertThatThrownBy(()->template.execute(status->service.delete(actor.getId(),owner.getId(),bankId,0)))
                .isInstanceOfSatisfying(BankAccountConflictException.class,e->assertThat(e.reason()).isEqualTo(BankAccountConflictException.Reason.TRANSACTION_NOT_SUPPORTED));
        assertThat(banks.countByCompanyId(owner.getId())).isEqualTo(1);
    }
    @Test void shouldProvideACompositeTenantKeyForFutureOperationalReferences(){
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            // PostgreSQL disallows a temporary table referencing a permanent table.
            // Transactional DDL is rolled back with this synthetic probe.
            jdbc.execute("CREATE TABLE synthetic_bank_reference(company_id UUID,bank_id UUID,FOREIGN KEY(company_id,bank_id) REFERENCES financial_bank_accounts(company_id,id))");
            assertThat(jdbc.update("INSERT INTO synthetic_bank_reference(company_id,bank_id) VALUES (?,?)",owner.getId(),bankId)).isEqualTo(1);
            assertThatThrownBy(()->jdbc.update("INSERT INTO synthetic_bank_reference(company_id,bank_id) VALUES (?,?)",other.getId(),bankId)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            status.setRollbackOnly();
        });
    }
    @Test void shouldRequireAuthenticationAndCsrfAndKeepUnimplementedCompanyRoutesDenied()throws Exception{
        mvc.perform(get(route())).andExpect(status().isUnauthorized());
        mvc.perform(post(route()).with(user(new UserAccountPrincipal(actor))).contentType("application/json").content(body("No CSRF","0.00",null)))
                .andExpect(status().isForbidden());
        mvc.perform(get(route()+"/"+bankId+"/unsupported").with(user(new UserAccountPrincipal(actor)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/companies/"+other.getId()+"/bank-accounts").with(user(new UserAccountPrincipal(actor)))).andExpect(status().isForbidden());
    }
    private boolean removeAfter(CountDownLatch start,CountDownLatch ready,UUID bank)throws Exception{
        ready.countDown();if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Synthetic concurrency timeout");
        try{return service.delete(actor.getId(),owner.getId(),bank,0).action()==BankAccountService.DeleteAction.DELETED;}
        catch(BankAccountConflictException conflict){if(conflict.reason()!=BankAccountConflictException.Reason.LAST_ACCOUNT)throw conflict;return false;}
    }
    private UserAccount createUser(String name){var result=users.saveAndFlush(new UserAccount("Synthetic Financial "+name,UUID.randomUUID()+"@example.invalid","hash"));ownUsers.add(result.getId());return result;}
    private void grant(UserAccount user,CompanyAccessRole role,CostCenterScope scope,Set<CompanyPermission> permissions){grants.saveAndFlush(new CompanyAccessGrant(user,owner,role,scope,permissions));}
    private String route(){return "/api/companies/"+owner.getId()+"/bank-accounts";}
    private String body(String name,String money,Long version){return "{\"name\":\""+name+"\",\"type\":\"CASH\",\"openingBalance\":\""+money+"\",\"openingDate\":\"2026-07-01\",\"active\":true,\"expectedVersion\":"+version+"}";}
}
