package com.finemp.finance.transfer;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.accountingfirm.*;
import com.finemp.administration.*;
import com.finemp.client.*;
import com.finemp.company.*;
import com.finemp.finance.bank.*;
import com.finemp.security.UserAccountPrincipal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="finemp.finance.transfers.enabled=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BankTransferReadIntegrationTest {
    @Autowired BankTransferReadService service;@Autowired BankTransferRepository transfers;
    @Autowired BankAccountRepository banks;@Autowired CompanyRepository companies;@Autowired ClientRepository clients;@Autowired AccountingFirmRepository firms;
    @Autowired UserAccountRepository users;@Autowired CompanyAccessGrantRepository grants;
    @Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager transactions;@Autowired MockMvc mvc;
    Company owner,other;UserAccount manager,viewer,restricted,admin;
    UUID bankA,bankB,foreignBank,latest,older,foreignTransfer;
    final List<UUID> firmIds=new ArrayList<>(),clientIds=new ArrayList<>(),userIds=new ArrayList<>();
    @BeforeEach void prepare() {
        owner=company("Owner","83838383000183");other=company("Other","84848484000184");
        manager=createUser("Manager");viewer=createUser("Viewer");restricted=createUser("Restricted");admin=createUser("Admin");
        grant(manager,CostCenterScope.ALL,CompanyAccessRole.CUSTOM,Set.of(CompanyPermission.BANK_ACCOUNT_MANAGE));
        grant(viewer,CostCenterScope.ALL,CompanyAccessRole.CUSTOM,Set.of(CompanyPermission.BANK_ACCOUNT_VIEW));
        grant(restricted,CostCenterScope.RESTRICTED,CompanyAccessRole.CUSTOM,Set.of(CompanyPermission.BANK_ACCOUNT_VIEW));
        grant(admin,CostCenterScope.ALL,CompanyAccessRole.ADMIN,Set.of());
        bankA=bank(owner,"Origin");bankB=bank(owner,"Destination");foreignBank=bank(other,"Foreign");var foreignSecond=bank(other,"Other");
        latest=transfer(owner,bankA,bankB,LocalDate.of(2026,7,31),"99999999999999999.99");
        older=transfer(owner,bankB,bankA,LocalDate.of(2026,7,1),"0.00");
        foreignTransfer=transfer(other,foreignBank,foreignSecond,LocalDate.of(2026,8,1),"2.00");
    }
    @AfterEach void cleanSyntheticFixtures() {
        for(var company:List.of(owner,other)) {
            jdbc.update("DELETE FROM financial_bank_transfers WHERE company_id=?",company.getId());jdbc.update("DELETE FROM financial_bank_accounts WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=?)",company.getId());
            jdbc.update("DELETE FROM company_access_grants WHERE company_id=?",company.getId());jdbc.update("DELETE FROM companies WHERE id=?",company.getId());
        }
        for(var id:clientIds)jdbc.update("DELETE FROM clients WHERE id=?",id);for(var id:firmIds)jdbc.update("DELETE FROM accounting_firms WHERE id=?",id);for(var id:userIds)jdbc.update("DELETE FROM user_accounts WHERE id=?",id);
    }
    @Test void paginationMustOrderByDateAndStableIdWithoutMixingCompanies() {
        var first=service.list(viewer.getId(),owner.getId(),0,1);var second=service.list(viewer.getId(),owner.getId(),1,1);
        assertThat(first.items()).extracting(BankTransferResponse::id).containsExactly(latest);assertThat(second.items()).extracting(BankTransferResponse::id).containsExactly(older);
        assertThat(first.totalItems()).isEqualTo(2);assertThat(first.totalPages()).isEqualTo(2);
        assertThat(service.list(manager.getId(),owner.getId(),5,1).items()).isEmpty();
        jdbc.update("UPDATE financial_bank_transfers SET transfer_date=DATE '2026-07-31' WHERE company_id=?",owner.getId());
        var full=service.list(viewer.getId(),owner.getId(),0,50).items().stream().map(BankTransferResponse::id).toList();
        assertThat(service.list(viewer.getId(),owner.getId(),0,1).items().getFirst().id()).isEqualTo(full.getFirst());
        assertThat(service.list(viewer.getId(),owner.getId(),1,1).items().getFirst().id()).isEqualTo(full.get(1));
    }
    @Test void bothAccountLabelsAndFinancialValuesMustBeCurrentScalarProjections() {
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            assertThat(banks.findById(bankA).orElseThrow().getName()).contains("Origin");assertThat(transfers.findById(latest).orElseThrow().getVersion()).isZero();
            jdbc.update("UPDATE financial_bank_accounts SET name='Updated synthetic origin',active=false WHERE id=?",bankA);
            jdbc.update("UPDATE financial_bank_transfers SET amount=1.23,description='Updated synthetic description',version=version+1 WHERE id=?",latest);
            var row=service.find(viewer.getId(),owner.getId(),latest);assertThat(row.originBankAccountName()).isEqualTo("Updated synthetic origin");
            assertThat(row.destinationBankAccountName()).contains("Destination");assertThat(row.amount()).isEqualTo(new BigDecimal("1.23"));assertThat(row.version()).isEqualTo(1);
            assertThat(row.description()).isEqualTo("Updated synthetic description");
        });
    }
    @Test void permissionMustBeFreshAndRestrictedOrAdminWithoutExplicitGrantMustNotBypassIt() {
        for(var actor:List.of(restricted,admin))assertThatThrownBy(()->service.list(actor.getId(),owner.getId(),0,50)).isInstanceOf(OrganizationAccessDeniedException.class);
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            grants.findByUserAccountIdAndCompanyId(viewer.getId(),owner.getId()).orElseThrow().getPermissions().size();
            jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=? AND user_account_id=?)",owner.getId(),viewer.getId());
            assertThatThrownBy(()->service.find(viewer.getId(),owner.getId(),latest)).isInstanceOf(OrganizationAccessDeniedException.class);status.setRollbackOnly();
        });
    }
    @Test void foreignTargetMustBeNotFoundAndForeignCompanyMustBeDenied() {
        assertThatThrownBy(()->service.find(manager.getId(),owner.getId(),foreignTransfer)).isInstanceOf(SaasResourceNotFoundException.class);
        assertThatThrownBy(()->service.find(manager.getId(),other.getId(),foreignTransfer)).isInstanceOf(OrganizationAccessDeniedException.class);
    }
    @Test void httpMustReturnExactMonetaryStringsAndNeverExposeActorOrForeignRows()throws Exception {
        var principal=new UserAccountPrincipal(viewer);
        mvc.perform(get(route()+"/"+latest).with(user(principal))).andExpect(status().isOk()).andExpect(jsonPath("$.amount").isString())
                .andExpect(jsonPath("$.amount").value("99999999999999999.99")).andExpect(jsonPath("$.createdBy").doesNotExist());
        mvc.perform(get(route()).param("size","1").with(user(principal))).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(2)).andExpect(jsonPath("$.items[0].id").value(latest.toString()));
        mvc.perform(get(route()+"/"+foreignTransfer).with(user(principal))).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("BANK_TRANSFER_NOT_FOUND"));
        mvc.perform(get(route()+"/"+older).with(user(principal))).andExpect(status().isOk()).andExpect(jsonPath("$.amount").value("0.00"));
    }
    @Test void anonymousInvalidQueriesAndUnimplementedWritersMustRemainBoundedAndDenied()throws Exception {
        var principal=new UserAccountPrincipal(manager);
        mvc.perform(get(route())).andExpect(status().isUnauthorized());
        for(var size:List.of("0","101","invalid"))mvc.perform(get(route()).param("size",size).with(user(principal))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_BANK_TRANSFER_QUERY"));
        mvc.perform(post(route()).with(user(principal)).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(delete(route()+"/"+latest).with(user(principal)).with(csrf())).andExpect(status().isForbidden());assertThat(transfers.findById(latest)).isPresent();
    }
    String route() {return "/api/companies/"+owner.getId()+"/bank-transfers";}
    UUID transfer(Company company,UUID from,UUID to,LocalDate date,String amount) {return transfers.saveAndFlush(new BankTransfer(company,new BankTransferValues(from,to,date,new BigDecimal(amount),"Synthetic read transfer"),manager.getId())).getId();}
    UUID bank(Company company,String label) {return banks.saveAndFlush(new BankAccount(company,"Synthetic Read "+label,BankAccountType.CASH,BigDecimal.ZERO,LocalDate.of(2026,1,1),true)).getId();}
    Company company(String label,String cnpj) {var firm=firms.saveAndFlush(new AccountingFirm("Synthetic Transfer Read Firm "+label,true));firmIds.add(firm.getId());var client=clients.saveAndFlush(new Client(firm,"Synthetic Transfer Read Client "+label,true));clientIds.add(client.getId());return companies.saveAndFlush(new Company(client,"Synthetic Transfer Read Company "+label,cnpj));}
    UserAccount createUser(String label) {var actor=users.saveAndFlush(new UserAccount("Synthetic Transfer Read "+label,UUID.randomUUID()+"@example.invalid","hash"));userIds.add(actor.getId());return actor;}
    void grant(UserAccount actor,CostCenterScope scope,CompanyAccessRole role,Set<CompanyPermission> permission) {grants.saveAndFlush(new CompanyAccessGrant(actor,owner,role,scope,permission));}
}
