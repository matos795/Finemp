package com.finemp.finance.partner;

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
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="finemp.finance.catalog.enabled=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BusinessPartnerIntegrationTest {
    @Autowired BusinessPartnerService service;
    @Autowired BusinessPartnerRepository partners;
    @Autowired CompanyRepository companies;
    @Autowired ClientRepository clients;
    @Autowired AccountingFirmRepository firms;
    @Autowired UserAccountRepository users;
    @Autowired CompanyAccessGrantRepository grants;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean BusinessPartnerReferenceGuard usage;
    private UserAccount actor,viewer,admin,restricted;
    private Company owner,other;
    private UUID partnerId,foreignId;
    private final List<UUID> ownUsers=new ArrayList<>(),firmIds=new ArrayList<>(),clientIds=new ArrayList<>();
    private final JsonMapper json=JsonMapper.builder().build();

    @BeforeEach void prepare() {
        owner=company("Owner","63636363000163");other=company("Other","64646464000164");
        actor=createUser("Manager");viewer=createUser("Viewer");admin=createUser("Admin");restricted=createUser("Restricted");
        grant(actor,CompanyAccessRole.CUSTOM,CostCenterScope.ALL,Set.of(CompanyPermission.BUSINESS_PARTNER_MANAGE));
        grant(viewer,CompanyAccessRole.VIEWER,CostCenterScope.ALL,Set.of(CompanyPermission.BUSINESS_PARTNER_VIEW));
        grant(admin,CompanyAccessRole.ADMIN,CostCenterScope.ALL,Set.of());
        grant(restricted,CompanyAccessRole.CUSTOM,CostCenterScope.RESTRICTED,Set.of(CompanyPermission.BUSINESS_PARTNER_VIEW,CompanyPermission.BUSINESS_PARTNER_MANAGE));
        partnerId=partners.saveAndFlush(new BusinessPartner(owner,profile("12ABC34501DE35",PartnerRole.BOTH,true))).getId();
        foreignId=partners.saveAndFlush(new BusinessPartner(other,profile("98ZYX76543WV39",PartnerRole.SUPPLIER,false))).getId();
    }
    @AfterEach void cleanOwnedSyntheticData() {
        for(var company:List.of(owner,other)) {
            jdbc.update("DELETE FROM business_partners WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=?)",company.getId());
            jdbc.update("DELETE FROM company_access_grants WHERE company_id=?",company.getId());jdbc.update("DELETE FROM companies WHERE id=?",company.getId());
        }
        for(var id:clientIds)jdbc.update("DELETE FROM clients WHERE id=?",id);
        for(var id:firmIds)jdbc.update("DELETE FROM accounting_firms WHERE id=?",id);
        for(var id:ownUsers)jdbc.update("DELETE FROM user_accounts WHERE id=?",id);
    }
    @Test void manualRegistrationMustValidateNormalizeAndReturnServerVersionAndTimestamps()throws Exception {
        var payload=new BusinessPartnerProfile(PartnerPersonType.INDIVIDUAL,"987.654.321-00"," Synthetic Person ","ignored",PartnerRole.CUSTOMER,
                "","synthetic@example.invalid",""," São Paulo ","spx","","","","","","35-01","","","",true);
        var response=mvc.perform(post(route()).with(user(principal(actor))).with(csrf()).contentType("application/json").content(body(payload,null)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.companyId").value(owner.getId().toString()))
                .andExpect(jsonPath("$.profile.document").value("98765432100")).andExpect(jsonPath("$.profile.tradeName").value(""))
                .andExpect(jsonPath("$.profile.stateCode").value("SP")).andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.createdAt").exists()).andExpect(jsonPath("$.updatedAt").exists()).andReturn();
        UUID id=UUID.fromString(json.readTree(response.getResponse().getContentAsByteArray()).get("id").asString());
        mvc.perform(put(route()+"/"+id).with(user(principal(actor))).with(csrf()).contentType("application/json").content(body(payload.withActive(false),0L)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.profile.active").value(false));
        mvc.perform(put(route()+"/"+id).with(user(principal(actor))).with(csrf()).contentType("application/json").content(body(payload,0L)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
        assertThat(partners.findById(id).orElseThrow().getProfile().active()).isFalse();
        String missing=body(payload,null).replace("98765432100","");
        mvc.perform(post(route()).with(user(principal(actor))).with(csrf()).contentType("application/json").content(missing))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_BUSINESS_PARTNER"));
    }
    @Test void mustRequireExplicitPermissionAndFullScopeAndProtectEveryTenantLookup()throws Exception {
        mvc.perform(get(route()).with(user(principal(viewer)))).andExpect(status().isOk());
        for(var denied:List.of(admin,restricted))mvc.perform(get(route()).with(user(principal(denied)))).andExpect(status().isForbidden());
        mvc.perform(post(route()).with(user(principal(viewer))).with(csrf()).contentType("application/json").content(body(profile("98765432100",PartnerRole.CUSTOMER,true),null)))
                .andExpect(status().isForbidden());
        for(var request:List.of(get(route()+"/"+foreignId),delete(route()+"/"+foreignId).param("version","0").with(csrf()))) {
            mvc.perform(request.with(user(principal(actor)))).andExpect(status().isNotFound());
        }
        mvc.perform(put(route()+"/"+foreignId).with(user(principal(actor))).with(csrf()).contentType("application/json").content(body(profile("98765432100",PartnerRole.CUSTOMER,true),0L)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/companies/"+other.getId()+"/business-partners").with(user(principal(actor)))).andExpect(status().isForbidden());
        assertThat(partners.findById(foreignId).orElseThrow().getProfile().document()).isEqualTo("98ZYX76543WV39");
    }
    @Test void searchMustBeAccentInsensitiveLiteralPagedAndIncludeBothCommercialRoles()throws Exception {
        partners.saveAndFlush(new BusinessPartner(owner,profile("98765432100",PartnerRole.CUSTOMER,true)));
        mvc.perform(get(route()).with(user(principal(actor))).param("role","SUPPLIER").param("search","SAO").param("size","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1)).andExpect(jsonPath("$.items[0].id").value(partnerId.toString()));
        mvc.perform(get(route()).with(user(principal(actor))).param("role","CUSTOMER").param("size","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(2)).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get(route()).with(user(principal(actor))).param("role","BOTH"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get(route()).with(user(principal(actor))).param("search","12.abc.345"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get(route()).with(user(principal(actor))).param("search","%")).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get(route()).with(user(principal(actor))).param("size","101")).andExpect(status().isBadRequest());
    }
    @Test void documentUniquenessMustIncludeInactivePartnersButRemainInsideCompany()throws Exception {
        var saved=partners.findById(partnerId).orElseThrow();saved.deactivate();partners.saveAndFlush(saved);
        mvc.perform(post(route()).with(user(principal(actor))).with(csrf()).contentType("application/json").content(body(profile("12ABC34501DE35",PartnerRole.CUSTOMER,true),null)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_DOCUMENT"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("12ABC34501DE35"))));
        assertThat(partners.saveAndFlush(new BusinessPartner(other,profile("12ABC34501DE35",PartnerRole.CUSTOMER,true))).getId()).isNotNull();
    }
    @Test void concurrentDuplicateRequestsMustPublishOnlyOnePartner()throws Exception {
        var pool=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        Callable<Boolean> command=()->{
            ready.countDown();if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Synthetic barrier");
            try {service.create(actor.getId(),owner.getId(),new BusinessPartnerRequest(profile("98765432100",PartnerRole.CUSTOMER,true),null));return true;}
            catch(BusinessPartnerConflictException conflict) {if(conflict.reason()!=BusinessPartnerConflictException.Reason.DUPLICATE_DOCUMENT)throw conflict;return false;}
        };
        try {
            var first=pool.submit(command);var second=pool.submit(command);
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
            assertThat(List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(partners.findAll().stream().filter(p->p.getCompany().getId().equals(owner.getId()) && p.getProfile().document().equals("98765432100")).count()).isEqualTo(1);
        }finally {start.countDown();pool.shutdownNow();}
    }
    @Test void deletionMustPreserveReferencedHistoryAndAllowRemovingTheLastUnusedPartner()throws Exception {
        when(usage.hasReferences(owner.getId(),partnerId)).thenReturn(true);
        mvc.perform(delete(route()+"/"+partnerId).with(user(principal(actor))).with(csrf()).param("version","0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.action").value("DEACTIVATED"));
        var deactivated=partners.findById(partnerId).orElseThrow();
        assertThat(deactivated.getProfile().active()).isFalse();assertThat(deactivated.getVersion()).isEqualTo(1);
        when(usage.hasReferences(owner.getId(),partnerId)).thenReturn(false);
        mvc.perform(delete(route()+"/"+partnerId).with(user(principal(actor))).with(csrf()).param("version","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.action").value("DELETED"));
        assertThat(partners.findById(partnerId)).isEmpty();
    }
    @Test void revokedPermissionAndUntrustedHttpRequestsMustNeverMutatePartners()throws Exception {
        var principal=principal(actor);
        mvc.perform(get(route())).andExpect(status().isUnauthorized());
        mvc.perform(delete(route()+"/"+partnerId).with(user(principal)).param("version","0")).andExpect(status().isForbidden());
        mvc.perform(get(route()+"/"+partnerId+"/unsupported").with(user(principal))).andExpect(status().isForbidden());
        jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=? AND user_account_id=?)",owner.getId(),actor.getId());
        mvc.perform(get(route()).with(user(principal))).andExpect(status().isForbidden());
        mvc.perform(delete(route()+"/"+partnerId).with(user(principal)).with(csrf()).param("version","0")).andExpect(status().isForbidden());
        assertThat(partners.findById(partnerId)).isPresent();
    }
    @Test void ambientRepeatableReadMustBeRefusedBeforeMutation() {
        var template=new TransactionTemplate(transactions);template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        assertThatThrownBy(()->template.execute(status->service.delete(actor.getId(),owner.getId(),partnerId,0)))
                .isInstanceOfSatisfying(BusinessPartnerConflictException.class,e->assertThat(e.reason()).isEqualTo(BusinessPartnerConflictException.Reason.TRANSACTION_NOT_SUPPORTED));
        assertThat(partners.findById(partnerId)).isPresent();
    }
    @Test void databaseReferenceMustRejectACompanyMismatch() {
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            jdbc.execute("CREATE TABLE synthetic_partner_reference(company_id UUID,partner_id UUID,FOREIGN KEY(company_id,partner_id) REFERENCES business_partners(company_id,id))");
            jdbc.update("INSERT INTO synthetic_partner_reference(company_id,partner_id) VALUES (?,?)",owner.getId(),partnerId);
            assertThatThrownBy(()->jdbc.update("INSERT INTO synthetic_partner_reference(company_id,partner_id) VALUES (?,?)",other.getId(),partnerId))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            status.setRollbackOnly();
        });
    }
    private Company company(String suffix,String cnpj) {
        var firm=firms.saveAndFlush(new AccountingFirm("Synthetic Partner Firm "+suffix,true));firmIds.add(firm.getId());
        var client=clients.saveAndFlush(new Client(firm,"Synthetic Partner Client "+suffix,true));clientIds.add(client.getId());
        return companies.saveAndFlush(new Company(client,"Synthetic Partner Company "+suffix,cnpj));
    }
    private UserAccount createUser(String name) {var row=users.saveAndFlush(new UserAccount("Synthetic Partner "+name,UUID.randomUUID()+"@example.invalid","hash"));ownUsers.add(row.getId());return row;}
    private void grant(UserAccount user,CompanyAccessRole role,CostCenterScope scope,Set<CompanyPermission> permissions) {grants.saveAndFlush(new CompanyAccessGrant(user,owner,role,scope,permissions));}
    private BusinessPartnerProfile profile(String doc,PartnerRole role,boolean active) {
        var person=doc.length()==11?PartnerPersonType.INDIVIDUAL:PartnerPersonType.LEGAL_ENTITY;
        return BusinessPartnerProfileTest.profile(doc,person,role).withActive(active);
    }
    private String route() {return "/api/companies/"+owner.getId()+"/business-partners";}
    private String body(BusinessPartnerProfile profile,Long version) {return json.writeValueAsString(new BusinessPartnerRequest(profile,version));}
    private UserAccountPrincipal principal(UserAccount user) {return new UserAccountPrincipal(user);}
}
