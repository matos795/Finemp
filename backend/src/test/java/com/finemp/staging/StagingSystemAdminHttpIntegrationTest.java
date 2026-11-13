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
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class StagingSystemAdminHttpIntegrationTest {
    @Container static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url",postgres::getJdbcUrl);
        properties.add("spring.datasource.username",postgres::getUsername);
        properties.add("spring.datasource.password",postgres::getPassword);
    }

    @LocalServerPort int port;
    @Autowired DataSource datasource;

    private final JsonMapper json=JsonMapper.builder().build();
    private final HttpClient http=HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL))
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Test
    void systemAdminSyntheticOperatorSeesEverySyntheticCompany() throws Exception {
        String password="Synthetic-system-admin-regression";

        try(var connection=datasource.getConnection()) {
            assertThat(StagingSeed.provision(
                    connection,
                    StagingProvisioningMain.encodePassword(password)))
                    .isTrue();
            assertThat(StagingSeed.provisionIsolationCompany(connection))
                    .isTrue();
            assertThat(StagingSeed.grantSyntheticSystemAdmin(connection))
                    .isTrue();
        }

        login(password);
        var response=get("/api/auth/me");
        assertThat(response.statusCode()).isEqualTo(200);
        var me=json.readTree(response.body());

        var permissions=new ArrayList<String>();
        me.get("platformPermissions")
                .forEach(node->permissions.add(node.asString()));
        assertThat(permissions).containsExactly("SYSTEM_ADMIN");

        var companyIds=new ArrayList<String>();
        me.get("companies")
                .forEach(node->companyIds.add(node.get("id").asString()));
        assertThat(companyIds).containsExactlyInAnyOrder(
                StagingSeed.id("company").toString(),
                StagingSeed.id("foreign-company").toString());

        assertThat(post("/api/auth/logout",Map.of()).statusCode())
                .isEqualTo(204);
    }

    private void login(String password) throws Exception {
        assertThat(post(
                "/api/auth/login",
                Map.of(
                        "email","demo@finemp.example.invalid",
                        "password",password))
                .statusCode())
                .isEqualTo(204);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder(
                                URI.create("http://localhost:"+port+path))
                        .timeout(Duration.ofSeconds(20))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path,Object body) throws Exception {
        var response=get("/api/auth/csrf");
        assertThat(response.statusCode()).isEqualTo(200);
        var csrf=json.readTree(response.body());

        return http.send(
                HttpRequest.newBuilder(
                                URI.create("http://localhost:"+port+path))
                        .timeout(Duration.ofSeconds(20))
                        .header("Content-Type","application/json")
                        .header(
                                csrf.get("headerName").asString(),
                                csrf.get("token").asString())
                        .POST(HttpRequest.BodyPublishers.ofString(
                                json.writeValueAsString(body)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
