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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
@Import(TestcontainersConfiguration.class)
class FinancialCategoryIntegrationTest {
    @Autowired FinancialCategoryService service;
    @MockitoSpyBean FinancialCategoryRepository categories;
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
    private UserAccount actor,viewer,admin,restricted;
    private UUID groupId,subId,foreignId;
    private final List<UUID> ownUsers=new ArrayList<>(),clientIds=new ArrayList<>(),firmIds=new ArrayList<>();
    @BeforeEach void prepare() {
        owner=company("Owner","67676767000167");other=company("Other","68686868000168");
        actor=createUser("Manager");viewer=createUser("Viewer");admin=createUser("Admin");restricted=createUser("Restricted");
        grant(actor,CompanyAccessRole.CUSTOM,CostCenterScope.ALL,Set.of(CompanyPermission.FINANCIAL_CATEGORY_MANAGE));
        grant(viewer,CompanyAccessRole.VIEWER,CostCenterScope.ALL,Set.of(CompanyPermission.FINANCIAL_CATEGORY_VIEW));
        grant(admin,CompanyAccessRole.ADMIN,CostCenterScope.ALL,Set.of());
        grant(restricted,CompanyAccessRole.CUSTOM,CostCenterScope.RESTRICTED,Set.of(CompanyPermission.FINANCIAL_CATEGORY_VIEW,CompanyPermission.FINANCIAL_CATEGORY_MANAGE));
        var group=categories.saveAndFlush(FinancialCategory.group(owner,FinancialDirection.INCOME,1,"São Receitas"));groupId=group.getId();
        subId=categories.saveAndFlush(FinancialCategory.subcategory(group,1,"Synthetic Income")).getId();
        foreignId=categories.saveAndFlush(FinancialCategory.group(other,FinancialDirection.EXPENSE,1,"Foreign Expense")).getId();
    }
    @AfterEach void cleanOwnedSyntheticFixtures() {
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
        doAnswer(call->{
            Object page=call.callRealMethod();loaded.countDown();
            if(!proceed.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Synthetic page barrier");
            return page;
        }).when(categories).search(eq(owner.getId()),eq(""),isNull(),isNull(),isNull(),any());
        try {
            var reading=pool.submit(()->service.list(actor.getId(),owner.getId(),"",null,null,null,0,100));
            assertThat(loaded.await(5,TimeUnit.SECONDS)).isTrue();
            service.delete(actor.getId(),owner.getId(),groupId,0);proceed.countDown();
            var page=reading.get(10,TimeUnit.SECONDS);
            assertThat(page.items()).hasSize(2);
            assertThat(page.items().stream().filter(row->row.id().equals(subId)).findFirst().orElseThrow().effective().dreLine()).isEqualTo(OperationalDreLine.REC);
            assertThat(categories.findById(groupId)).isEmpty();assertThat(categories.findById(subId)).isEmpty();
        }finally {proceed.countDown();pool.shutdownNow();}
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
