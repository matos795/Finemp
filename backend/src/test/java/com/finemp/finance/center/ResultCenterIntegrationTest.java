package com.finemp.finance.center;

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
@Import(TestcontainersConfiguration.class)
class ResultCenterIntegrationTest {
    @Autowired ResultCenterService service;
    @Autowired ResultCenterRepository centers;
    @Autowired FinancialCompanySettingsRepository settings;
    @Autowired CompanyRepository companies;
    @Autowired ClientRepository clients;
    @Autowired AccountingFirmRepository firms;
    @Autowired UserAccountRepository users;
    @Autowired CompanyAccessGrantRepository grants;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean ResultCenterUsageGuard usage;
    private Company owner,other;
    private UserAccount actor,viewer,admin,restricted;
    private UUID root,child,foreign;
    private final List<UUID> ownUsers=new ArrayList<>(),clientIds=new ArrayList<>(),firmIds=new ArrayList<>();
    @BeforeEach void prepare() {
        owner=company("Owner","65656565000165");other=company("Other","66666666000166");
        actor=createUser("Manager");viewer=createUser("Viewer");admin=createUser("Admin");restricted=createUser("Restricted");
        grant(actor,CompanyAccessRole.CUSTOM,CostCenterScope.ALL,Set.of(CompanyPermission.RESULT_CENTER_MANAGE));
        grant(viewer,CompanyAccessRole.VIEWER,CostCenterScope.ALL,Set.of(CompanyPermission.RESULT_CENTER_VIEW));
        grant(admin,CompanyAccessRole.ADMIN,CostCenterScope.ALL,Set.of());
        grant(restricted,CompanyAccessRole.CUSTOM,CostCenterScope.RESTRICTED,Set.of(CompanyPermission.RESULT_CENTER_VIEW,CompanyPermission.RESULT_CENTER_MANAGE));
        root=centers.saveAndFlush(new ResultCenter(owner,"1",null,"São Principal",true)).getId();
        child=centers.saveAndFlush(new ResultCenter(owner,"1.2",root,"Synthetic Child",true)).getId();
        centers.saveAndFlush(new ResultCenter(owner,"1.10",root,"Synthetic Tenth",false));
        centers.saveAndFlush(new ResultCenter(owner,"10",null,"Other Branch",true));
        foreign=centers.saveAndFlush(new ResultCenter(other,"7",null,"Foreign Branch",true)).getId();
    }
    @AfterEach void cleanOwnedSyntheticFixtures() {
        for(var company:List.of(owner,other)) {
            jdbc.update("DELETE FROM operational_result_centers WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM financial_company_settings WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=?)",company.getId());
            jdbc.update("DELETE FROM company_access_grants WHERE company_id=?",company.getId());jdbc.update("DELETE FROM companies WHERE id=?",company.getId());
        }
        for(var id:clientIds)jdbc.update("DELETE FROM clients WHERE id=?",id);
        for(var id:firmIds)jdbc.update("DELETE FROM accounting_firms WHERE id=?",id);
        for(var id:ownUsers)jdbc.update("DELETE FROM user_accounts WHERE id=?",id);
    }
    @Test void hierarchyMustBeNumericallyOrderedPagedAndRestrictedToAnExactSubtree()throws Exception {
        mvc.perform(get(route()).with(user(principal(actor))).param("size","100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[*].code").value(org.hamcrest.Matchers.contains("1","1.2","1.10","10")))
                .andExpect(jsonPath("$.items[0].hasChildren").value(true)).andExpect(jsonPath("$.items[1].hasChildren").value(false));
        mvc.perform(get(route()).with(user(principal(actor))).param("rootId",root.toString()).param("size","2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(3)).andExpect(jsonPath("$.items.length()").value(2));
        mvc.perform(get(route()).with(user(principal(actor))).param("rootId",child.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get(route()).with(user(principal(actor))).param("search","SAO")).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get(route()).with(user(principal(actor))).param("search","%")).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
    }
    @Test void createMustResolveTheExactParentAndKeepExplicitTypeAndRejectDuplicateCodes()throws Exception {
        mvc.perform(post(route()).with(user(principal(actor))).with(csrf()).contentType("application/json").content("{\"type\":\"ANALYTICAL\",\"code\":\" 1 . 2 . 1 \",\"name\":\"Synthetic Grandchild\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.parentId").value(child.toString()))
                .andExpect(jsonPath("$.code").value("1.2.1")).andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.version").value(0));
        mvc.perform(post(route()).with(user(principal(actor))).with(csrf()).contentType("application/json").content("{\"type\":\"ANALYTICAL\",\"code\":\"7.1\",\"name\":\"Foreign Parent\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PARENT_NOT_FOUND"));
        mvc.perform(post(route()).with(user(principal(actor))).with(csrf()).contentType("application/json").content("{\"type\":\"ANALYTICAL\",\"code\":\"1\",\"name\":\"Duplicate\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));
        when(usage.hasEntries(owner.getId(),child)).thenReturn(true);
        mvc.perform(post(route()).with(user(principal(actor))).with(csrf()).contentType("application/json").content("{\"type\":\"ANALYTICAL\",\"code\":\"1.2.2\",\"name\":\"Used Parent\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.type").value("ANALYTICAL"));
        assertThat(centers.findById(child).orElseThrow().getType()).isEqualTo(ResultCenterType.ANALYTICAL);
    }
    @Test void typeMustBeExplicitOnCreationAndIndependentOfChildrenOnReadAndUpdate()throws Exception {
        mvc.perform(post(route()).with(user(principal(actor))).with(csrf()).contentType("application/json").content("{\"code\":\"8\",\"name\":\"Missing type\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(route()).with(user(principal(actor))).with(csrf()).contentType("application/json").content("{\"code\":\"8\",\"name\":\"Numeric type\",\"type\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(route()).with(user(principal(actor))).with(csrf()).contentType("application/json").content("{\"code\":\"8\",\"name\":\"Empty group\",\"type\":\"GROUP\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.type").value("GROUP")).andExpect(jsonPath("$.hasChildren").value(false));
        mvc.perform(get(route()+"/"+root).with(user(principal(actor))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.type").value("ANALYTICAL")).andExpect(jsonPath("$.hasChildren").value(true));
        mvc.perform(put(route()+"/"+child).with(user(principal(actor))).with(csrf()).contentType("application/json").content("{\"name\":\"Explicit group\",\"active\":true,\"expectedVersion\":0,\"type\":\"GROUP\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.type").value("GROUP")).andExpect(jsonPath("$.version").value(1));
        when(usage.hasEntries(owner.getId(),root)).thenReturn(true);
        mvc.perform(put(route()+"/"+root).with(user(principal(actor))).with(csrf()).contentType("application/json").content("{\"name\":\"Keep history\",\"active\":true,\"expectedVersion\":0,\"type\":\"GROUP\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HAS_ENTRIES"));
    }
    @Test void explicitPermissionAndOwnershipMustProtectBothHierarchyAndSettings()throws Exception {
        mvc.perform(get(route()).with(user(principal(viewer)))).andExpect(status().isOk());
        for(var denied:List.of(admin,restricted))mvc.perform(get(route()).with(user(principal(denied)))).andExpect(status().isForbidden());
        mvc.perform(get(route()+"/"+foreign).with(user(principal(actor)))).andExpect(status().isNotFound());
        mvc.perform(get(route()).with(user(principal(actor))).param("rootId",foreign.toString())).andExpect(status().isNotFound());
        mvc.perform(get("/api/companies/"+other.getId()+"/result-centers/settings").with(user(principal(actor)))).andExpect(status().isForbidden());
        mvc.perform(put(route()+"/settings").with(user(principal(viewer))).with(csrf()).contentType("application/json").content("{\"requireResultCenter\":true}")).andExpect(status().isForbidden());
        mvc.perform(get(route())).andExpect(status().isUnauthorized());
        mvc.perform(delete(route()+"/"+child).with(user(principal(actor))).param("version","0")).andExpect(status().isForbidden());
        mvc.perform(get(route()+"/"+child+"/unsupported").with(user(principal(actor)))).andExpect(status().isForbidden());
    }
    @Test void updatesMustReturnFlushedVersionAndKeepCodeAndParentImmutable()throws Exception {
        mvc.perform(put(route()+"/"+child).with(user(principal(actor))).with(csrf()).contentType("application/json").content("{\"name\":\"Renamed\",\"active\":false,\"expectedVersion\":0,\"type\":\"ANALYTICAL\",\"code\":\"9\",\"parentId\":\""+foreign+"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.code").value("1.2"))
                .andExpect(jsonPath("$.parentId").value(root.toString()));
        mvc.perform(put(route()+"/"+child).with(user(principal(actor))).with(csrf()).contentType("application/json").content("{\"name\":\"Stale\",\"active\":true,\"expectedVersion\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
        assertThat(centers.findById(child).orElseThrow().getName()).isEqualTo("Renamed");
    }
    @Test void usedCenterMustDeactivateBeforeChildCheckButUnusedGroupCannotBeDeleted()throws Exception {
        mvc.perform(delete(route()+"/"+root).with(user(principal(actor))).with(csrf()).param("version","0"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HAS_CHILDREN"));
        when(usage.hasReferences(owner.getId(),root)).thenReturn(true);
        mvc.perform(delete(route()+"/"+root).with(user(principal(actor))).with(csrf()).param("version","0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.action").value("DEACTIVATED"));
        mvc.perform(delete(route()+"/"+child).with(user(principal(actor))).with(csrf()).param("version","0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.action").value("DELETED"));
        assertThat(centers.findById(root).orElseThrow().isActive()).isFalse();assertThat(centers.findById(child)).isEmpty();
    }
    @Test void requirementMustDefaultFalseAndUseOptimisticVersionsWithoutRewritingHistory()throws Exception {
        mvc.perform(get(route()+"/settings").with(user(principal(actor)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.requireResultCenter").value(false)).andExpect(jsonPath("$.version").isEmpty());
        mvc.perform(put(route()+"/settings").with(user(principal(actor))).with(csrf()).contentType("application/json").content("{\"requireResultCenter\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0)).andExpect(jsonPath("$.requireResultCenter").value(true));
        mvc.perform(put(route()+"/settings").with(user(principal(actor))).with(csrf()).contentType("application/json").content("{\"requireResultCenter\":false,\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(put(route()+"/settings").with(user(principal(actor))).with(csrf()).contentType("application/json").content("{\"requireResultCenter\":true,\"expectedVersion\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
        assertThat(centers.countByCompanyId(owner.getId())).isEqualTo(4);
    }
    @Test void suggestedPlanMustBeExplicitAtomicAndOnlyAvailableForAnEmptyCompany()throws Exception {
        mvc.perform(post(route()+"/suggested-plan").with(user(principal(actor))).with(csrf())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_NOT_EMPTY"));
        jdbc.update("DELETE FROM operational_result_centers WHERE company_id=?",owner.getId());
        mvc.perform(post(route()+"/suggested-plan").with(user(principal(actor))).with(csrf())).andExpect(status().isCreated()).andExpect(jsonPath("$.length()").value(7));
        assertThat(centers.countByCompanyId(owner.getId())).isEqualTo(7);
        mvc.perform(post(route()+"/suggested-plan").with(user(principal(actor))).with(csrf())).andExpect(status().isConflict());
        assertThat(centers.countByCompanyId(owner.getId())).isEqualTo(7);
    }
    @Test void concurrentChildCreationAndParentDeletionMustNeverPublishAnOrphan()throws Exception {
        var parent=centers.saveAndFlush(new ResultCenter(owner,"8",null,"Concurrent Root",true));
        var pool=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try {
            var add=pool.submit(()->{await(ready,start);try {service.create(actor.getId(),owner.getId(),new ResultCenterCreateRequest("8.1","Concurrent Child"));return true;}
                catch(ResultCenterConflictException conflict){assertThat(conflict.reason()).isEqualTo(ResultCenterConflictException.Reason.PARENT_NOT_FOUND);return false;}});
            var remove=pool.submit(()->{await(ready,start);try {service.delete(actor.getId(),owner.getId(),parent.getId(),0);return true;}
                catch(ResultCenterConflictException conflict){assertThat(conflict.reason()).isEqualTo(ResultCenterConflictException.Reason.HAS_CHILDREN);return false;}});
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
            assertThat(List.of(add.get(10,TimeUnit.SECONDS),remove.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(centers.findByCompanyIdAndCode(owner.getId(),"8.1").isPresent()).isEqualTo(centers.findById(parent.getId()).isPresent());
        }finally {start.countDown();pool.shutdownNow();}
    }
    @Test void freshRevocationAndTransactionIsolationMustPreventMutation()throws Exception {
        var template=new TransactionTemplate(transactions);template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        assertThatThrownBy(()->template.execute(status->service.delete(actor.getId(),owner.getId(),child,0)))
                .isInstanceOfSatisfying(ResultCenterConflictException.class,e->assertThat(e.reason()).isEqualTo(ResultCenterConflictException.Reason.TRANSACTION_NOT_SUPPORTED));
        jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=? AND user_account_id=?)",owner.getId(),actor.getId());
        mvc.perform(delete(route()+"/"+child).with(user(principal(actor))).with(csrf()).param("version","0")).andExpect(status().isForbidden());
        assertThat(centers.findById(child)).isPresent();
    }
    private void await(CountDownLatch ready,CountDownLatch start)throws InterruptedException {ready.countDown();if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Synthetic barrier");}
    @Test void databaseMustRejectParentReferencesFromAnotherCompany() {
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            assertThatThrownBy(()->centers.saveAndFlush(new ResultCenter(owner,"7.1",foreign,"Cross Tenant Parent",true)))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            status.setRollbackOnly();
        });
    }
    private Company company(String suffix,String cnpj) {
        var firm=firms.saveAndFlush(new AccountingFirm("Synthetic Center Firm "+suffix,true));firmIds.add(firm.getId());
        var client=clients.saveAndFlush(new Client(firm,"Synthetic Center Client "+suffix,true));clientIds.add(client.getId());
        return companies.saveAndFlush(new Company(client,"Synthetic Center Company "+suffix,cnpj));
    }
    private UserAccount createUser(String name) {var row=users.saveAndFlush(new UserAccount("Synthetic Center "+name,UUID.randomUUID()+"@example.invalid","hash"));ownUsers.add(row.getId());return row;}
    private void grant(UserAccount user,CompanyAccessRole role,CostCenterScope scope,Set<CompanyPermission> permissions) {grants.saveAndFlush(new CompanyAccessGrant(user,owner,role,scope,permissions));}
    private UserAccountPrincipal principal(UserAccount user) {return new UserAccountPrincipal(user);}
    private String route() {return "/api/companies/"+owner.getId()+"/result-centers";}
}
