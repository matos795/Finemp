package com.finemp.staging;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

/**
 * #334: o mesmo operador sintético, com login normal, só enxerga
 * Administração depois da elevação explícita; nenhum grant de escritório
 * ou de Company é criado por ela.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class StagingPlatformAdminHttpIntegrationTest {
    @Container static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17");
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url",postgres::getJdbcUrl);
        properties.add("spring.datasource.username",postgres::getUsername);
        properties.add("spring.datasource.password",postgres::getPassword);
    }
    @LocalServerPort int port;
    @Autowired DataSource datasource;
    private final JsonMapper json=JsonMapper.builder().build();
    private final HttpClient http=HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL))
            .connectTimeout(Duration.ofSeconds(5)).build();

    @Test void elevatedSyntheticOperatorSeesAdministrationOnlyAfterTheExplicitGrant() throws Exception {
        String password="Synthetic-platform-admin-regression";
        try(var connection=datasource.getConnection()) {
            assertThat(StagingSeed.provision(connection,StagingProvisioningMain.encodePassword(password))).isTrue();
        }
        login(password);
        assertThat(platformPermissions()).isEmpty();
        assertThat(get("/api/admin/accounting-firms").statusCode()).isEqualTo(403);
        assertThat(get("/api/admin/users").statusCode()).isEqualTo(403);
        assertThat(post("/api/auth/logout",Map.of()).statusCode()).isEqualTo(204);

        try(var connection=datasource.getConnection()) {
            assertThat(StagingSeed.grantSyntheticPlatformAdmin(connection)).isTrue();
        }
        login(password);
        assertThat(platformPermissions()).containsExactlyInAnyOrder("ACCOUNTING_FIRM_CREATE","ACCOUNTING_FIRM_VIEW","UNASSIGNED_COMPANY_ASSIGN","PLATFORM_USER_MANAGE");
        assertThat(get("/api/admin/accounting-firms").statusCode()).isEqualTo(200);
        assertThat(get("/api/admin/users").statusCode()).isEqualTo(200);
        var me=json.readTree(get("/api/auth/me").body());
        assertThat(me.toString()).doesNotContain("password");
        try(var connection=datasource.getConnection();var statement=connection.createStatement();
            var rows=statement.executeQuery("SELECT (SELECT count(*) FROM accounting_firm_access_grants)||'|'||(SELECT count(*) FROM company_access_grants)||'|'||(SELECT count(*) FROM company_access_grant_permissions)")) {
            rows.next();assertThat(rows.getString(1)).isEqualTo("0|1|3");
        }
        assertThat(post("/api/auth/logout",Map.of()).statusCode()).isEqualTo(204);
        assertThat(get("/api/auth/me").statusCode()).isEqualTo(401);
    }

    @Test void systemAdminSyntheticOperatorSeesEverySyntheticCompany() throws Exception {
        String password="Synthetic-system-admin-regression";
        try(var connection=datasource.getConnection()) {
            assertThat(StagingSeed.provision(connection,StagingProvisioningMain.encodePassword(password))).isTrue();
            assertThat(StagingSeed.provisionIsolationCompany(connection)).isTrue();
            assertThat(StagingSeed.grantSyntheticSystemAdmin(connection)).isTrue();
        }

        login(password);
        var me=json.readTree(get("/api/auth/me").body());

        var permissions=new ArrayList<String>();
        me.get("platformPermissions").forEach(node->permissions.add(node.asString()));
        assertThat(permissions).containsExactly("SYSTEM_ADMIN");

        var companyIds=new ArrayList<String>();
        me.get("companies").forEach(node->companyIds.add(node.get("id").asString()));
        assertThat(companyIds).containsExactlyInAnyOrder(
                StagingSeed.id("company").toString(),
                StagingSeed.id("foreign-company").toString());

        assertThat(post("/api/auth/logout",Map.of()).statusCode()).isEqualTo(204);
    }

    private void login(String password) throws Exception {
        assertThat(post("/api/auth/login",Map.of("email","demo@finemp.example.invalid","password",password)).statusCode()).isEqualTo(204);
    }
    private List<String> platformPermissions() throws Exception {
        var response=get("/api/auth/me");assertThat(response.statusCode()).isEqualTo(200);
        var result=new ArrayList<String>();
        JsonNode permissions=json.readTree(response.body()).get("platformPermissions");
        if(permissions!=null)permissions.forEach(node->result.add(node.asString()));
        return result;
    }
    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path))
                .timeout(Duration.ofSeconds(20)).GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> post(String path,Object body) throws Exception {
        var response=get("/api/auth/csrf");assertThat(response.statusCode()).isEqualTo(200);
        var csrf=json.readTree(response.body());
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path))
                .timeout(Duration.ofSeconds(20)).header("Content-Type","application/json")
                .header(csrf.get("headerName").asString(),csrf.get("token").asString())
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
}
