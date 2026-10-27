package com.finemp.connector.agent;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConnectorLocalConfigurationTest {
    @TempDir Path directory;
    @Test void shouldLoadOnlyLocalReferencesAndExplicitIdentityMappingWithoutAnyNetworkOrSecretAccess() throws Exception {
        var file=directory.resolve("connector.properties");Files.writeString(file,valid());
        var config=ConnectorLocalConfiguration.load(file);var profile=config.profiles().getFirst();
        assertThat(config.pollSeconds()).isEqualTo(5);assertThat(profile.maxRecords()).isEqualTo(25000);
        assertThat(profile.identity().table()).isEqualTo("SYN_COMPANIES");assertThat(profile.credentialEnv()).isEqualTo("FINEMP_CONNECTOR_TEST_TOKEN");
        assertThat(config.toString()).doesNotContain("SYN_COMPANIES","FINEMP_CONNECTOR","connector.invalid");
        assertThat(profile.toString()).isEqualTo("ConnectorProfile[redacted]");
    }
    @Test void shouldRefuseUnknownKeysDuplicatePropertiesAndOversizedFilesWithConstantDiagnostic() throws Exception {
        for(var text:List.of(valid()+"\nprofile.test.password=synthetic-private-value",valid()+"\nversion=1",valid()+"\n#"+"x".repeat(32768))) {
            var file=directory.resolve(UUID.randomUUID()+".properties");Files.writeString(file,text);
            assertInvalid(()->ConnectorLocalConfiguration.load(file));
        }
    }
    @Test void shouldRefuseUnsafeOriginMissingMappingAndInvalidBoundsBeforeAnyIo() {
        for(var replacement:List.of("http://connector.invalid","https://private:private@connector.invalid","https://connector.invalid/?token=private","https://connector.invalid/path"))
            assertInvalid(()->parse(valid().replace("https://connector.invalid",replacement)));
        assertInvalid(()->parse(valid().replace("SYN_COMPANIES","TABLE;DROP")));
        for(var suffix:List.of("poll-seconds=0","profile.test.max-records=100001","profile.test.read-seconds=601","profile.test.query-seconds=0","profile.test.http-seconds=121"))
            assertInvalid(()->parse(valid()+"\n"+suffix));
        assertInvalid(()->parse(valid().replace("version=1","version=2")));
    }
    @Test void shouldRefuseUnscopedSecretReferencesAndAliasesBetweenTokenAndJdbcSecrets() {
        assertInvalid(()->parse(valid().replace("FINEMP_CONNECTOR_TEST_TOKEN","UNRELATED_ENVIRONMENT_KEY")));
        assertInvalid(()->parse(valid().replace("FINEMP_CONNECTOR_TEST_TOKEN","FINEMP_CONNECTOR_TEST_PASSWORD")));
    }
    @Test void shouldRejectDuplicateProfilesSourcesAndCredentialReferences() {
        assertInvalid(()->parse(valid().replace("profiles=test","profiles=test,test")));
        var second=valid().substring(valid().indexOf("profile.test.source-id")).replace("profile.test.","profile.other.");
        assertInvalid(()->parse(valid().replace("profiles=test","profiles=test,other")+"\n"+second));
        var uniqueSource=second.replace("53535353-5353-4353-8353-535353535353",UUID.randomUUID().toString());
        assertInvalid(()->parse(valid().replace("profiles=test","profiles=test,other")+"\n"+uniqueSource));
    }
    @Test void shouldUseAnExclusiveOsLockAndAllowReuseOnlyAfterRelease() {
        UUID source=UUID.randomUUID();
        try(var first=ConnectorProfileLock.acquire(directory,source)) {
            assertInvalid(()->ConnectorProfileLock.acquire(directory,source));
            try(var independent=ConnectorProfileLock.acquire(directory,UUID.randomUUID())) {assertThat(independent.toString()).isEqualTo("ConnectorProfileLock[redacted]");}
        }
        try(var reacquired=ConnectorProfileLock.acquire(directory,source)) {assertThat(reacquired).isNotNull();}
        assertThat(directory.resolve(source+".lock")).exists().isEmptyFile();
    }
    private ConnectorLocalConfiguration parse(String text) {
        var properties=new Properties();try{properties.load(new java.io.StringReader(text));}catch(java.io.IOException impossible){throw new AssertionError(impossible);}
        return ConnectorLocalConfiguration.parse(properties);
    }
    private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(ConnectorClientException.class).hasMessage("Connector client: CONFIGURATION").hasNoCause();
    }
    private String valid(){return """
            version=1
            profiles=test
            profile.test.source-id=53535353-5353-4353-8353-535353535353
            profile.test.origin=https://connector.invalid
            profile.test.credential-env=FINEMP_CONNECTOR_TEST_TOKEN
            profile.test.jdbc-url-env=FINEMP_CONNECTOR_TEST_URL
            profile.test.jdbc-user-env=FINEMP_CONNECTOR_TEST_USER
            profile.test.jdbc-password-env=FINEMP_CONNECTOR_TEST_PASSWORD
            profile.test.identity-table=SYN_COMPANIES
            profile.test.identity-code-column=COMPANY_CODE
            profile.test.identity-cnpj-column=TAX_ID
            """;}
}
