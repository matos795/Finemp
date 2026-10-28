package com.finemp.finance.category;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.accountingfirm.*;
import com.finemp.client.*;
import com.finemp.company.*;
import com.finemp.security.UserAccountPrincipal;
import java.util.*;
import java.util.concurrent.*;
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

@SpringBootTest(properties="finemp.finance.catalog.enabled=true")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class,CategoryRepositoryTestProbe.class})
class FinancialCategoryStandardPlanIntegrationTest {
    @Autowired FinancialCategoryService service;
    @Autowired FinancialCategoryRepository categories;
    @Autowired CategoryRepositoryTestProbe.Probe probe;
    @Autowired CompanyRepository companies;
    @Autowired ClientRepository clients;
    @Autowired AccountingFirmRepository firms;
    @Autowired UserAccountRepository users;
    @Autowired CompanyAccessGrantRepository grants;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    private Company owner,other;
    private UserAccount actor,viewer;
    private UUID clientId,firmId;
    @BeforeEach void prepare() {
        probe.reset();
        var firm=firms.saveAndFlush(new AccountingFirm("Synthetic Standard Firm",true));firmId=firm.getId();
        var client=clients.saveAndFlush(new Client(firm,"Synthetic Standard Client",true));clientId=client.getId();
        owner=companies.saveAndFlush(new Company(client,"Synthetic Standard Owner","69696969000169"));
        other=companies.saveAndFlush(new Company(client,"Synthetic Standard Other","70707070000170"));
        actor=users.saveAndFlush(new UserAccount("Synthetic Standard Manager",UUID.randomUUID()+"@example.invalid","hash"));
        viewer=users.saveAndFlush(new UserAccount("Synthetic Standard Viewer",UUID.randomUUID()+"@example.invalid","hash"));
        grants.saveAndFlush(new CompanyAccessGrant(actor,owner,CompanyAccessRole.CUSTOM,CostCenterScope.ALL,Set.of(CompanyPermission.FINANCIAL_CATEGORY_MANAGE)));
        grants.saveAndFlush(new CompanyAccessGrant(viewer,owner,CompanyAccessRole.VIEWER,CostCenterScope.ALL,Set.of(CompanyPermission.FINANCIAL_CATEGORY_VIEW)));
    }
    @AfterEach void cleanOwnedSyntheticFixtures() {
        probe.reset();
        for(var company:List.of(owner,other)) {
            jdbc.update("DELETE FROM financial_categories WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=?)",company.getId());
            jdbc.update("DELETE FROM company_access_grants WHERE company_id=?",company.getId());jdbc.update("DELETE FROM companies WHERE id=?",company.getId());
        }
        jdbc.update("DELETE FROM clients WHERE id=?",clientId);jdbc.update("DELETE FROM accounting_firms WHERE id=?",firmId);
        jdbc.update("DELETE FROM user_accounts WHERE id IN (?,?)",actor.getId(),viewer.getId());
    }
    @Test void standardPlanMustPersistEveryHomologatedRowOnlyInTheAuthorizedCompanyAndRefuseRepetition()throws Exception {
        mvc.perform(post(route()).with(user(new UserAccountPrincipal(actor))).with(csrf())).andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value("FINEMP_WEB_1")).andExpect(jsonPath("$.groups").value(11)).andExpect(jsonPath("$.subcategories").value(60));
        assertThat(categories.countByCompanyId(owner.getId())).isEqualTo(71);assertThat(categories.countByCompanyId(other.getId())).isZero();
        var tax=service.list(actor.getId(),owner.getId(),"Simples Nacional",null,null,null,0,100).items().getFirst();
        assertThat(tax.code()).isEqualTo("2.5.1");assertThat(tax.effective().dreLine()).isEqualTo(OperationalDreLine.DED);
        assertThat(tax.effective().costBehavior()).isEqualTo(CostBehavior.VARIABLE);
        mvc.perform(post(route()).with(user(new UserAccountPrincipal(actor))).with(csrf())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_NOT_EMPTY"));
        assertThat(categories.countByCompanyId(owner.getId())).isEqualTo(71);
    }
    @Test void existingEvenPartialPlanMustNotBeMergedOrOverwritten() {
        var existing=categories.saveAndFlush(FinancialCategory.group(owner,FinancialDirection.INCOME,9,"Preserved Custom Group"));
        assertThatThrownBy(()->service.createStandardPlan(actor.getId(),owner.getId()))
                .isInstanceOfSatisfying(FinancialCategoryConflictException.class,e->assertThat(e.reason()).isEqualTo(FinancialCategoryConflictException.Reason.PLAN_NOT_EMPTY));
        assertThat(categories.countByCompanyId(owner.getId())).isEqualTo(1);
        assertThat(categories.findById(existing.getId()).orElseThrow().getName()).isEqualTo("Preserved Custom Group");
    }
    @Test void failureAfterSeveralInsertsMustRollbackTheEntirePlan() {
        probe.beforeSave=row->{
            if("2.2".equals(row.getCode()))throw new IllegalStateException("Synthetic metadata write failure");
        };
        assertThatThrownBy(()->service.createStandardPlan(actor.getId(),owner.getId())).isInstanceOf(IllegalStateException.class);
        assertThat(categories.countByCompanyId(owner.getId())).isZero();
    }
    @Test void simultaneousInitializationMustPublishExactlyOneWholePlan()throws Exception {
        var pool=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        Callable<Boolean> command=()->{
            ready.countDown();if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Synthetic barrier");
            try {service.createStandardPlan(actor.getId(),owner.getId());return true;}
            catch(FinancialCategoryConflictException conflict){assertThat(conflict.reason()).isEqualTo(FinancialCategoryConflictException.Reason.PLAN_NOT_EMPTY);return false;}
        };
        try {
            var first=pool.submit(command);var second=pool.submit(command);assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
            assertThat(List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(categories.countByCompanyId(owner.getId())).isEqualTo(71);
        }finally {start.countDown();pool.shutdownNow();}
    }
    @Test void permissionSessionCsrfAndCompanyBoundaryMustBeEnforcedBeforeInitializing()throws Exception {
        mvc.perform(post(route()).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post(route()).with(user(new UserAccountPrincipal(actor)))).andExpect(status().isForbidden());
        mvc.perform(post(route()).with(user(new UserAccountPrincipal(viewer))).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/companies/"+other.getId()+"/financial-categories/standard-plan").with(user(new UserAccountPrincipal(actor))).with(csrf())).andExpect(status().isForbidden());
        assertThat(categories.countByCompanyId(owner.getId())).isZero();assertThat(categories.countByCompanyId(other.getId())).isZero();
    }
    @Test void concurrentManualRegistrationMustSeeEitherTheWholeStandardPlanOrItsOwnEmptyPlan()throws Exception {
        var pool=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try {
            var seed=pool.submit(()->{
                ready.countDown();if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Synthetic barrier");
                try {service.createStandardPlan(actor.getId(),owner.getId());return true;}
                catch(FinancialCategoryConflictException conflict){assertThat(conflict.reason()).isEqualTo(FinancialCategoryConflictException.Reason.PLAN_NOT_EMPTY);return false;}
            });
            var manual=pool.submit(()->{
                ready.countDown();if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Synthetic barrier");
                return service.createGroup(actor.getId(),owner.getId(),new FinancialCategoryGroupRequest(FinancialDirection.INCOME,"Concurrent Custom Group")).code();
            });
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
            boolean seeded=seed.get(10,TimeUnit.SECONDS);String code=manual.get(10,TimeUnit.SECONDS);
            assertThat(code).isEqualTo(seeded?"1.4":"1.1");
            assertThat(categories.countByCompanyId(owner.getId())).isEqualTo(seeded?73:2);
        }finally {start.countDown();pool.shutdownNow();}
    }
    private String route() {return "/api/companies/"+owner.getId()+"/financial-categories/standard-plan";}
}
