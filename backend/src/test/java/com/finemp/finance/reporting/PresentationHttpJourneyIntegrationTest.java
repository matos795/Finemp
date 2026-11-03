package com.finemp.finance.reporting;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.accountingfirm.*;
import com.finemp.client.*;
import com.finemp.company.*;
import com.finemp.finance.bank.*;
import com.finemp.finance.category.*;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

/** Real HTTP + session + PostgreSQL. Bootstrap is confined to this disposable test fixture. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "finemp.finance.catalog.enabled=true", "finemp.finance.entries.enabled=true",
    "finemp.finance.entries.commands.enabled=true", "finemp.finance.presentation.enabled=true",
    "finemp.finance.aging.enabled=true", "finemp.finance.daily-cash.enabled=true",
    "finemp.finance.monthly-cash.enabled=true", "finemp.finance.operational-dre.enabled=true"})
@Import(TestcontainersConfiguration.class)
class PresentationHttpJourneyIntegrationTest {
    @LocalServerPort int port;
    @Autowired UserAccountRepository users;
    @Autowired CompanyAccessGrantRepository grants;
    @Autowired AccountingFirmRepository firms;
    @Autowired ClientRepository clients;
    @Autowired CompanyRepository companies;
    @Autowired BankAccountRepository banks;
    @Autowired FinancialCategoryRepository categories;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    private final JsonMapper json=JsonMapper.builder().build();
    private final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL))
            .connectTimeout(Duration.ofSeconds(5)).build();

    @Test void authenticatedCommandsFeedPresentationAndCannotReadAnotherCompany() throws Exception {
        var firm=firms.saveAndFlush(new AccountingFirm("Synthetic HTTP journey",true));
        var client=clients.saveAndFlush(new Client(firm,"Synthetic HTTP client",true));
        var company=companies.saveAndFlush(new Company(client,"Synthetic HTTP owner","91919191000191"));
        var foreign=companies.saveAndFlush(new Company(client,"Synthetic HTTP forbidden","92929292000192"));
        String password=UUID.randomUUID().toString();
        var actor=users.saveAndFlush(new UserAccount("Synthetic HTTP actor",UUID.randomUUID()+"@example.invalid",passwords.encode(password)));
        try {
            grants.saveAndFlush(new CompanyAccessGrant(actor,company,CompanyAccessRole.CUSTOM,CostCenterScope.ALL,Set.of(CompanyPermission.FINANCIAL_ENTRY_MANAGE,CompanyPermission.BANK_ACCOUNT_MANAGE)));
            var bank=banks.saveAndFlush(new BankAccount(company,"Synthetic HTTP cash",BankAccountType.CASH,BigDecimal.ZERO,LocalDate.of(2026,1,1),true));
            var group=categories.saveAndFlush(FinancialCategory.group(company,FinancialDirection.INCOME,1,"Synthetic HTTP revenue"));
            var category=categories.saveAndFlush(FinancialCategory.subcategory(group,1,"Synthetic HTTP category"));
            jdbc.update("UPDATE financial_categories SET dre_line='REC' WHERE id=?",category.getId());
            String report="/api/companies/"+company.getId()+"/financial-reports/presentation-snapshot?month=2026-07&asOf=2026-07-31&scope=ALL";
            assertThat(get(report).statusCode()).isEqualTo(401);
            assertThat(post("/api/auth/login",Map.of("email",actor.getEmail(),"password",password),null).statusCode()).isEqualTo(204);
            assertThat(get("/api/auth/me").statusCode()).isEqualTo(200);

            String entries="/api/companies/"+company.getId()+"/financial-entries";
            var values=new LinkedHashMap<String,Object>();
            values.put("direction","INCOME");values.put("description","Synthetic HTTP receipt");values.put("nominalAmount","3.00");
            values.put("documentDate","2026-07-01");values.put("entryDate","2026-07-01");values.put("competence","2026-07");values.put("dueDate","2026-07-01");
            values.put("bankAccountId",bank.getId());values.put("categoryId",category.getId());
            UUID createKey=UUID.randomUUID();
            var created=post(entries,Map.of("values",values),createKey);
            assertThat(created.statusCode()).isEqualTo(201);
            JsonNode receipt=json.readTree(created.body());
            var replay=post(entries,Map.of("values",values),createKey);
            assertThat(replay.statusCode()).isEqualTo(201);assertThat(json.readTree(replay.body())).isEqualTo(receipt);
            var settlement=Map.of("expectedVersion",receipt.get("version").longValue(),"values",Map.of("paymentDate","2026-07-02","paidAmount","3.10","bankAccountId",bank.getId()));
            assertThat(post(entries+"/"+receipt.get("entryId").asString()+"/settlements",settlement,UUID.randomUUID()).statusCode()).isEqualTo(200);
            var response=get(report);assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
            var snapshot=json.readTree(response.body());
            assertThat(snapshot.get("companyId").asString()).isEqualTo(company.getId().toString());
            assertThat(snapshot.get("operationalDre").get("subtotals").get("netResult").asString()).isEqualTo("3.10");
            assertThat(snapshot.get("indicators").get("cash").get("generation").asString()).isEqualTo("3.10");
            assertThat(snapshot.get("receivables").get("totalAmount").asString()).isEqualTo("0.00");
            var effects=get("/api/companies/"+company.getId()+"/financial-reports/operational-dre/effects?month=2026-07&line=RFIN");
            assertThat(effects.statusCode()).isEqualTo(200);
            assertThat(json.readTree(effects.body()).get("items").get(0).get("amount").asString()).isEqualTo("0.10");
            assertThat(get(report.replace(company.getId().toString(),foreign.getId().toString())).statusCode()).isEqualTo(403);
            assertThat(post("/api/auth/logout",Map.of(),null).statusCode()).isEqualTo(204);
            assertThat(get(report).statusCode()).isEqualTo(401);
        } finally {
            for(var owner:List.of(company,foreign)) {
                for(var table:List.of("financial_entry_events","financial_entry_command_receipts","financial_entry_command_keys","financial_entries","financial_categories","financial_bank_accounts","access_audit_events"))
                    jdbc.update("DELETE FROM "+table+" WHERE company_id=?",owner.getId());
                jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=?)",owner.getId());
                jdbc.update("DELETE FROM company_access_grants WHERE company_id=?",owner.getId());
                companies.deleteById(owner.getId());
            }
            clients.deleteById(client.getId());firms.deleteById(firm.getId());users.deleteById(actor.getId());
        }
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(Duration.ofSeconds(20)).GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> post(String path,Object body,UUID command) throws Exception {
        var tokenResponse=get("/api/auth/csrf");assertThat(tokenResponse.statusCode()).isEqualTo(200);
        var csrf=json.readTree(tokenResponse.body());
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(Duration.ofSeconds(20))
                .header("Content-Type","application/json").header(csrf.get("headerName").asString(),csrf.get("token").asString());
        if(command!=null)request.header("Idempotency-Key",command.toString());
        return http.send(request.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
}
