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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="finemp.finance.catalog.enabled=true")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class,CategoryRepositoryTestProbe.class})
class FinancialCategoryIntegrationTest {
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
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean FinancialCategoryUsageGuard usage;
    private Company owner,other;
    private UserAccount actor,viewer,admin,restricted,structural,clientRole,accountantRole;
    private UUID groupId,subId,foreignId;
    private final List<UUID> ownUsers=new ArrayList<>(),clientIds=new ArrayList<>(),firmIds=new ArrayList<>();
    @BeforeEach void prepare() {
        probe.reset();
        owner=company("Owner","67676767000167");other=company("Other","68686868000168");
        actor=createUser("Manager");viewer=createUser("Viewer");admin=createUser("Admin");restricted=createUser("Restricted");
        structural=createUser("Structural");clientRole=createUser("Client Role");accountantRole=createUser("Accountant Role");
        grant(actor,CompanyAccessRole.CUSTOM,CostCenterScope.ALL,Set.of(CompanyPermission.FINANCIAL_CATEGORY_MANAGE));
        grant(viewer,CompanyAccessRole.VIEWER,CostCenterScope.ALL,Set.of(CompanyPermission.FINANCIAL_CATEGORY_VIEW));
        grant(admin,CompanyAccessRole.ADMIN,CostCenterScope.ALL,Set.of());
        grant(restricted,CompanyAccessRole.CUSTOM,CostCenterScope.RESTRICTED,Set.of(CompanyPermission.FINANCIAL_CATEGORY_VIEW,CompanyPermission.FINANCIAL_CATEGORY_MANAGE));
        grant(structural,CompanyAccessRole.CLIENT,CostCenterScope.ALL,Set.of(CompanyPermission.FINANCIAL_CATEGORY_STRUCTURE_MANAGE));
        grant(clientRole,CompanyAccessRole.CLIENT,CostCenterScope.ALL,Set.of());
        grant(accountantRole,CompanyAccessRole.ACCOUNTANT,CostCenterScope.ALL,Set.of());
        var group=categories.saveAndFlush(FinancialCategory.group(owner,FinancialDirection.INCOME,1,"São Receitas"));groupId=group.getId();
        subId=categories.saveAndFlush(FinancialCategory.subcategory(group,1,"Synthetic Income")).getId();
        foreignId=categories.saveAndFlush(FinancialCategory.group(other,FinancialDirection.EXPENSE,1,"Foreign Expense")).getId();
    }
    @AfterEach void cleanOwnedSyntheticFixtures() {
        probe.reset();
        for(var company:List.of(owner,other)) {
            jdbc.update("DELETE FROM financial_categories WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=?)",company.getId());
            jdbc.update("DELETE FROM company_access_grants WHERE company_id=?",company.getId());jdbc.update("DELETE FROM companies WHERE id=?",company.getId());
        }
        for(var id:clientIds)jdbc.update("DELETE FROM clients WHERE id=?",id);
        for(var id:firmIds)jdbc.update("DELETE FROM accounting_firms WHERE id=?",id);
        for(var id:ownUsers)jdbc.update("DELETE FROM user_accounts WHERE id=?",id);
    }
    @Test void groupCreationMustUseSeparateDirectionSequencesAndCreateItsInitialSubcategory()throws Exception {
        mvc.perform(post(route()+"/groups").with(user(principal(actor))).with(csrf()).contentType("application/json").content("{\"direction\":\"EXPENSE\",\"name\":\"Synthetic Expense\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("2.1")).andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.effective.dreLine").value("DADM")).andExpect(jsonPath("$.effective.costBehavior").value("FIXED"));
        mvc.perform(get(route()).with(user(principal(actor))).param("direction","EXPENSE").param("kind","SUBCATEGORY"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1)).andExpect(jsonPath("$.items[0].code").value("2.1.1"));
        mvc.perform(post(route()+"/"+groupId+"/subcategories").with(user(principal(actor))).with(csrf()).contentType("application/json").content("{\"name\":\"Second Income\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("1.1.2"))
                .andExpect(jsonPath("$.dreLine").isEmpty()).andExpect(jsonPath("$.effective.dreLine").value("REC")).andExpect(jsonPath("$.effective.costBehavior").value("VARIABLE"));
    }
    @Test void updateMustPreserveTheObservedDreSnapshotWhileOtherNullOverridesFollowTheGroup()throws Exception {
        mvc.perform(put(route()+"/"+subId).with(user(principal(actor))).with(csrf()).contentType("application/json")
                .content("{\"name\":\"Snapshot Subcategory\",\"cashActivity\":null,\"dreLine\":null,\"costBehavior\":null,\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dreLine").value("REC")).andExpect(jsonPath("$.version").value(1));
        mvc.perform(put(route()+"/"+groupId).with(user(principal(actor))).with(csrf()).contentType("application/json")
                .content("{\"name\":\"Updated Group\",\"cashActivity\":\"FINANCING\",\"dreLine\":\"NAO\",\"costBehavior\":\"FIXED\",\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(get(route()+"/"+subId).with(user(principal(actor)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.effective.dreLine").value("REC")).andExpect(jsonPath("$.effective.cashActivity").value("FINANCING"))
                .andExpect(jsonPath("$.effective.costBehavior").value("FIXED"));
        mvc.perform(put(route()+"/"+groupId).with(user(principal(actor))).with(csrf()).contentType("application/json")
                .content("{\"name\":\"Stale\",\"cashActivity\":\"OPERATING\",\"dreLine\":\"REC\",\"expectedVersion\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
    }
    @Test void explicitPermissionsAndOwnershipMustCoverEveryReadAndWritePath()throws Exception {
        mvc.perform(get(route()).with(user(principal(viewer)))).andExpect(status().isOk());
        for(var denied:List.of(admin,restricted))mvc.perform(get(route()).with(user(principal(denied)))).andExpect(status().isForbidden());
        mvc.perform(get(route()+"/"+foreignId).with(user(principal(actor)))).andExpect(status().isNotFound());
        mvc.perform(get(route()).with(user(principal(actor))).param("parentId",foreignId.toString())).andExpect(status().isNotFound());
        mvc.perform(post(route()+"/"+foreignId+"/subcategories").with(user(principal(actor))).with(csrf()).contentType("application/json").content("{\"name\":\"Attack\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete(route()+"/"+foreignId).with(user(principal(actor))).with(csrf()).param("version","0")).andExpect(status().isNotFound());
        mvc.perform(post(route()+"/groups").with(user(principal(viewer))).with(csrf()).contentType("application/json").content("{\"direction\":\"INCOME\",\"name\":\"Denied\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/companies/"+other.getId()+"/financial-categories").with(user(principal(actor)))).andExpect(status().isForbidden());
    }
    @Test void referencedCategoriesMustRejectDeletionAndUnusedGroupsMustDeleteChildrenAtomically()throws Exception {
        when(usage.hasEntriesInCategoryOrChildren(owner.getId(),groupId)).thenReturn(true);
        mvc.perform(delete(route()+"/"+groupId).with(user(principal(actor))).with(csrf()).param("version","0"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"));
        assertThat(categories.findById(subId)).isPresent();assertThat(categories.findById(groupId)).isPresent();
        when(usage.hasEntriesInCategoryOrChildren(owner.getId(),groupId)).thenReturn(false);
        mvc.perform(delete(route()+"/"+groupId).with(user(principal(actor))).with(csrf()).param("version","0")).andExpect(status().isNoContent());
        assertThat(categories.findById(subId)).isEmpty();assertThat(categories.findById(groupId)).isEmpty();assertThat(categories.findById(foreignId)).isPresent();
    }
    @Test void concurrentGroupCreationMustAllocateDifferentCodesAndInitialChildren()throws Exception {
        var pool=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        Callable<String> command=()->{ready.countDown();if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Synthetic barrier");
            return service.createGroup(actor.getId(),owner.getId(),new FinancialCategoryGroupRequest(FinancialDirection.INCOME,"Concurrent")).code();};
        try {
            var first=pool.submit(command);var second=pool.submit(command);assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
            assertThat(List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder("1.2","1.3");
            assertThat(categories.maxGroupSequence(owner.getId(),FinancialDirection.INCOME)).isEqualTo(3);
            assertThat(service.list(actor.getId(),owner.getId(),"",FinancialDirection.INCOME,FinancialCategory.Kind.SUBCATEGORY,null,0,100).totalItems()).isEqualTo(3);
        }finally {start.countDown();pool.shutdownNow();}
    }
    @Test void pagingSearchAndParentFiltersMustRemainServerSideAndLiteral()throws Exception {
        mvc.perform(get(route()).with(user(principal(actor))).param("search","SAO").param("size","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1)).andExpect(jsonPath("$.items[0].id").value(groupId.toString()));
        mvc.perform(get(route()).with(user(principal(actor))).param("parentId",groupId.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1)).andExpect(jsonPath("$.items[0].id").value(subId.toString()));
        mvc.perform(get(route()).with(user(principal(actor))).param("search","%")).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get(route()).with(user(principal(actor))).param("size","101")).andExpect(status().isBadRequest());
        mvc.perform(post(route()+"/"+subId+"/subcategories").with(user(principal(actor))).with(csrf()).contentType("application/json").content("{\"name\":\"Grandchild\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_A_GROUP"));
    }
    @Test void sessionCsrfFreshRevocationAndIsolationMustProtectMutations()throws Exception {
        mvc.perform(get(route())).andExpect(status().isUnauthorized());
        mvc.perform(delete(route()+"/"+subId).with(user(principal(actor))).param("version","0")).andExpect(status().isForbidden());
        mvc.perform(get(route()+"/"+subId+"/unsupported").with(user(principal(actor)))).andExpect(status().isForbidden());
        var template=new TransactionTemplate(transactions);template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        assertThatThrownBy(()->template.execute(status->{service.delete(actor.getId(),owner.getId(),subId,0);return null;}))
                .isInstanceOfSatisfying(FinancialCategoryConflictException.class,e->assertThat(e.reason()).isEqualTo(FinancialCategoryConflictException.Reason.TRANSACTION_NOT_SUPPORTED));
        jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=? AND user_account_id=?)",owner.getId(),actor.getId());
        mvc.perform(delete(route()+"/"+subId).with(user(principal(actor))).with(csrf()).param("version","0")).andExpect(status().isForbidden());
        assertThat(categories.findById(subId)).isPresent();
    }
    @Test void databaseMustRejectACrossCompanyParentEvenOutsideTheService() {
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            assertThatThrownBy(()->jdbc.update("UPDATE financial_categories SET parent_id=? WHERE company_id=? AND id=?",foreignId,owner.getId(),subId))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            status.setRollbackOnly();
        });
    }
    @Test void deletingAGroupAfterThePageQueryMustNotBreakItsAlreadyReadClassification()throws Exception {
        var loaded=new CountDownLatch(1);var proceed=new CountDownLatch(1);var pool=Executors.newSingleThreadExecutor();
        probe.afterSearch=()->{
            loaded.countDown();
            try {if(!proceed.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Synthetic page barrier");}
            catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException("Synthetic interrupted barrier");}
        };
        try {
            var reading=pool.submit(()->service.list(actor.getId(),owner.getId(),"",null,null,null,0,100));
            if(!loaded.await(5,TimeUnit.SECONDS)) {
                if(reading.isDone())reading.get();
                fail("Real page query did not reach the test barrier");
            }
            service.delete(actor.getId(),owner.getId(),groupId,0);proceed.countDown();
            var page=reading.get(10,TimeUnit.SECONDS);
            assertThat(page.items()).hasSize(2);
            assertThat(page.items().stream().filter(row->row.id().equals(subId)).findFirst().orElseThrow().effective().dreLine()).isEqualTo(OperationalDreLine.REC);
            assertThat(categories.findById(groupId)).isEmpty();assertThat(categories.findById(subId)).isEmpty();
        }finally {proceed.countDown();pool.shutdownNow();}
    }
    @Test void filteredReadMustUseCurrentScalarValuesEvenWithAnAlreadyManagedParent()throws Exception {
        var loaded=new CountDownLatch(1);var proceed=new CountDownLatch(1);var pool=Executors.newSingleThreadExecutor();
        try {
            var reading=pool.submit(()->new TransactionTemplate(transactions).execute(status->{
                var old=categories.findByIdAndCompanyId(groupId,owner.getId()).orElseThrow();
                assertThat(old.getDreLine()).isEqualTo(OperationalDreLine.REC);loaded.countDown();
                try {if(!proceed.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Synthetic managed-parent barrier");}
                catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException("Synthetic interrupted barrier");}
                return service.list(actor.getId(),owner.getId(),"",null,null,groupId,0,100);
            }));
            assertThat(loaded.await(5,TimeUnit.SECONDS)).isTrue();
            service.update(actor.getId(),owner.getId(),groupId,new FinancialCategoryUpdateRequest("Changed",CashActivity.FINANCING,OperationalDreLine.NAO,CostBehavior.FIXED,0L));
            var child=service.createSubcategory(actor.getId(),owner.getId(),groupId,new FinancialSubcategoryRequest("New Concurrent Child"));
            proceed.countDown();
            var page=reading.get(10,TimeUnit.SECONDS);
            var read=page.items().stream().filter(row->row.id().equals(child.id())).findFirst().orElseThrow();
            assertThat(read.effective()).isEqualTo(new FinancialCategory.EffectiveClassification(CashActivity.FINANCING,OperationalDreLine.NAO,CostBehavior.FIXED));
        }finally {proceed.countDown();pool.shutdownNow();}
    }
    @Test void structuralUserMayMaintainTheTreeButNeverTheClassification()throws Exception {
        mvc.perform(get(route()).with(user(principal(structural)))).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(2));
        mvc.perform(get(route()+"/"+subId).with(user(principal(structural)))).andExpect(status().isOk());
        mvc.perform(post(route()+"/groups").with(user(principal(structural))).with(csrf()).contentType("application/json").content("{\"direction\":\"EXPENSE\",\"name\":\"Structural Expense\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("2.1"));
        var created=mvc.perform(post(route()+"/"+groupId+"/subcategories").with(user(principal(structural))).with(csrf()).contentType("application/json").content("{\"name\":\"Structural Sub\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("1.1.2")).andReturn().getResponse().getContentAsString();
        var createdId=UUID.fromString(com.jayway.jsonpath.JsonPath.read(created,"$.id"));
        // Campos de classificação enviados junto ao rename são ignorados: o comando só lê nome e versão.
        mvc.perform(post(route()+"/"+groupId+"/rename").with(user(principal(structural))).with(csrf()).contentType("application/json")
                .content("{\"name\":\"Renamed Income\",\"cashActivity\":\"FINANCING\",\"dreLine\":\"NAO\",\"costBehavior\":\"FIXED\",\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Renamed Income")).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.cashActivity").value("OPERATING")).andExpect(jsonPath("$.dreLine").value("REC")).andExpect(jsonPath("$.costBehavior").isEmpty())
                .andExpect(jsonPath("$.effective.cashActivity").value("OPERATING")).andExpect(jsonPath("$.effective.dreLine").value("REC"))
                .andExpect(jsonPath("$.effective.costBehavior").value("VARIABLE"));
        mvc.perform(put(route()+"/"+groupId).with(user(principal(structural))).with(csrf()).contentType("application/json")
                .content("{\"name\":\"Denied\",\"cashActivity\":\"FINANCING\",\"dreLine\":\"NAO\",\"costBehavior\":\"FIXED\",\"expectedVersion\":1}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FINANCIAL_CATEGORY_ACCESS_DENIED"));
        mvc.perform(get(route()+"/"+groupId).with(user(principal(actor)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed Income")).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.cashActivity").value("OPERATING")).andExpect(jsonPath("$.dreLine").value("REC")).andExpect(jsonPath("$.costBehavior").isEmpty());
        mvc.perform(delete(route()+"/"+createdId).with(user(principal(structural))).with(csrf()).param("version","0")).andExpect(status().isNoContent());
        assertThat(categories.findById(createdId)).isEmpty();
    }
    @Test void structuralUserMayApplyTheStandardPlanToAnEmptyCompany()throws Exception {
        jdbc.update("DELETE FROM financial_categories WHERE company_id=?",owner.getId());
        mvc.perform(post(route()+"/standard-plan").with(user(principal(structural))).with(csrf())).andExpect(status().isCreated());
        assertThat(categories.countByCompanyId(owner.getId())).isPositive();
        mvc.perform(post(route()+"/standard-plan").with(user(principal(structural))).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_NOT_EMPTY"));
    }
    @Test void renamingASubcategoryMustNotSnapshotTheGroupDreUnlikeTheFullUpdate()throws Exception {
        mvc.perform(post(route()+"/"+subId+"/rename").with(user(principal(actor))).with(csrf()).contentType("application/json")
                .content("{\"name\":\"Renamed Sub\",\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Renamed Sub")).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.cashActivity").isEmpty()).andExpect(jsonPath("$.dreLine").isEmpty()).andExpect(jsonPath("$.costBehavior").isEmpty())
                .andExpect(jsonPath("$.effective.dreLine").value("REC"));
        mvc.perform(put(route()+"/"+groupId).with(user(principal(actor))).with(csrf()).contentType("application/json")
                .content("{\"name\":\"Updated Group\",\"cashActivity\":\"FINANCING\",\"dreLine\":\"NAO\",\"costBehavior\":\"FIXED\",\"expectedVersion\":0}"))
                .andExpect(status().isOk());
        // Sem fotografia: a sub continua herdando o DRE atual do grupo.
        mvc.perform(get(route()+"/"+subId).with(user(principal(actor)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.dreLine").isEmpty()).andExpect(jsonPath("$.effective.dreLine").value("NAO"))
                .andExpect(jsonPath("$.effective.cashActivity").value("FINANCING")).andExpect(jsonPath("$.effective.costBehavior").value("FIXED"));
        assertThat(jdbc.queryForObject("SELECT dre_line FROM financial_categories WHERE id=?",String.class,subId)).isNull();
    }
    @Test void renameMustRespectVersionCsrfCompanyAndExplicitPermissions()throws Exception {
        var body="{\"name\":\"Renamed\",\"expectedVersion\":0}";
        mvc.perform(post(route()+"/"+groupId+"/rename").with(user(principal(structural))).with(csrf()).contentType("application/json")
                .content("{\"name\":\"Stale\",\"expectedVersion\":3}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
        mvc.perform(post(route()+"/"+groupId+"/rename").with(user(principal(structural))).with(csrf()).contentType("application/json")
                .content("{\"name\":\" \",\"expectedVersion\":0}")).andExpect(status().isBadRequest());
        mvc.perform(post(route()+"/"+groupId+"/rename").with(user(principal(structural))).with(csrf()).contentType("application/json")
                .content("{\"name\":\"No Version\"}")).andExpect(status().isBadRequest());
        mvc.perform(post(route()+"/"+groupId+"/rename").with(user(principal(structural))).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post(route()+"/"+foreignId+"/rename").with(user(principal(structural))).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/companies/"+other.getId()+"/financial-categories/"+foreignId+"/rename").with(user(principal(structural))).with(csrf())
                .contentType("application/json").content(body)).andExpect(status().isForbidden());
        for(var denied:List.of(viewer,admin,restricted,clientRole,accountantRole))
            mvc.perform(post(route()+"/"+groupId+"/rename").with(user(principal(denied))).with(csrf()).contentType("application/json").content(body))
                    .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT name FROM financial_categories WHERE id=?",String.class,groupId)).isEqualTo("São Receitas");
        assertThat(jdbc.queryForObject("SELECT name FROM financial_categories WHERE id=?",String.class,foreignId)).isEqualTo("Foreign Expense");
        jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=? AND user_account_id=?)",owner.getId(),structural.getId());
        mvc.perform(post(route()+"/"+groupId+"/rename").with(user(principal(structural))).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT name FROM financial_categories WHERE id=?",String.class,groupId)).isEqualTo("São Receitas");
    }
    @Test void rolesWithoutExplicitGrantsMustNotReadOrWriteCategories()throws Exception {
        for(var denied:List.of(admin,clientRole,accountantRole)) {
            mvc.perform(get(route()).with(user(principal(denied)))).andExpect(status().isForbidden());
            mvc.perform(post(route()+"/groups").with(user(principal(denied))).with(csrf()).contentType("application/json")
                    .content("{\"direction\":\"INCOME\",\"name\":\"Denied\"}")).andExpect(status().isForbidden());
            mvc.perform(post(route()+"/standard-plan").with(user(principal(denied))).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(delete(route()+"/"+subId).with(user(principal(denied))).with(csrf()).param("version","0")).andExpect(status().isForbidden());
        }
        mvc.perform(post(route()+"/"+groupId+"/subcategories").with(user(principal(viewer))).with(csrf()).contentType("application/json").content("{\"name\":\"Denied\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete(route()+"/"+subId).with(user(principal(viewer))).with(csrf()).param("version","0")).andExpect(status().isForbidden());
        mvc.perform(put(route()+"/"+subId).with(user(principal(viewer))).with(csrf()).contentType("application/json")
                .content("{\"name\":\"Denied\",\"expectedVersion\":0}")).andExpect(status().isForbidden());
        assertThat(categories.findById(subId)).isPresent();
    }
    private Company company(String suffix,String cnpj) {
        var firm=firms.saveAndFlush(new AccountingFirm("Synthetic Category Firm "+suffix,true));firmIds.add(firm.getId());
        var client=clients.saveAndFlush(new Client(firm,"Synthetic Category Client "+suffix,true));clientIds.add(client.getId());
        return companies.saveAndFlush(new Company(client,"Synthetic Category Company "+suffix,cnpj));
    }
    private UserAccount createUser(String name) {var row=users.saveAndFlush(new UserAccount("Synthetic Category "+name,UUID.randomUUID()+"@example.invalid","hash"));ownUsers.add(row.getId());return row;}
    private void grant(UserAccount user,CompanyAccessRole role,CostCenterScope scope,Set<CompanyPermission> permissions) {grants.saveAndFlush(new CompanyAccessGrant(user,owner,role,scope,permissions));}
    private UserAccountPrincipal principal(UserAccount user) {return new UserAccountPrincipal(user);}
    private String route() {return "/api/companies/"+owner.getId()+"/financial-categories";}
}
