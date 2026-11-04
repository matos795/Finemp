package com.finemp.staging;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class StagingProvisioningTargetTest {
    @Test void encodedPasswordMatchesApiDelegatingEncoder(){String password="Synthetic-only-password";String hash=StagingProvisioningMain.encodePassword(password);assertThat(hash).startsWith("{bcrypt}");assertThat(org.springframework.security.crypto.factory.PasswordEncoderFactories.createDelegatingPasswordEncoder().matches(password,hash)).isTrue();}
    Map<String,String> valid(){String project=UUID.randomUUID().toString();return new HashMap<>(Map.of("SPRING_PROFILES_ACTIVE","prod,staging","FINEMP_STAGING_SEED_CONFIRM","PROVISION_SYNTHETIC_STAGING","RAILWAY_PROJECT_NAME","finemp-staging","RAILWAY_ENVIRONMENT_NAME","staging","FINEMP_STAGING_PROJECT_ID",project,"RAILWAY_PROJECT_ID",project,"SPRING_DATASOURCE_URL","jdbc:postgresql://postgres.railway.internal:5432/railway"));}
    @Test void acceptsOnlyExplicitMatchingStagingTarget(){StagingProvisioningMain.validateTarget(valid());for(var key:valid().keySet()){var values=valid();values.remove(key);assertThatThrownBy(()->StagingProvisioningMain.validateTarget(values)).isInstanceOf(IllegalArgumentException.class);}}
    @Test void refusesOtherProjectsEnvironmentsAndPublicOrParameterizedUrls(){for(var change:List.of(Map.entry("RAILWAY_PROJECT_NAME","Projeto FluxFund"),Map.entry("RAILWAY_ENVIRONMENT_NAME","production"),Map.entry("RAILWAY_PROJECT_ID",UUID.randomUUID().toString()),Map.entry("SPRING_DATASOURCE_URL","jdbc:postgresql://public.example:5432/db"),Map.entry("SPRING_DATASOURCE_URL","jdbc:postgresql://postgres.railway.internal:5432/db?socketFactory=custom"))){var env=valid();env.put(change.getKey(),change.getValue());assertThatThrownBy(()->StagingProvisioningMain.validateTarget(env)).isInstanceOf(IllegalArgumentException.class);}}
}
