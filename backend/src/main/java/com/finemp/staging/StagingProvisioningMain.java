package com.finemp.staging;

import java.sql.DriverManager;
import java.util.Map;
import java.util.UUID;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

/** Standalone process. No SpringApplication, web server, external readers or automatic migrations. */
public final class StagingProvisioningMain {
    public static void main(String[] args) {
        if(args.length==1 && "--help".equals(args[0])) {System.out.println("FINEMP staging provisioner: explicit environment-only configuration required; no secrets as arguments. Optional --isolation-company creates a negative-access fixture.");return;}
        try {
            boolean isolation=args.length==1 && "--isolation-company".equals(args[0]);
            if(args.length!=0 && !isolation)throw new IllegalArgumentException();
            var env=System.getenv();validateTarget(env);
            String hash=null;
            if(!isolation) {
                String password=required(env,"FINEMP_STAGING_SEED_PASSWORD");
                if(!password.matches("[\\x21-\\x7E]{16,64}"))throw new IllegalArgumentException();
                hash=encodePassword(password);
            }
            try(var connection=DriverManager.getConnection(required(env,"SPRING_DATASOURCE_URL"),required(env,"SPRING_DATASOURCE_USERNAME"),required(env,"SPRING_DATASOURCE_PASSWORD"))) {
                boolean created=isolation?StagingSeed.provisionIsolationCompany(connection):StagingSeed.provision(connection,hash);
                System.out.println(created?"Synthetic staging dataset created.":"Synthetic staging dataset already exists; unchanged.");
                if(isolation) System.out.println("IsolationCompany="+StagingSeed.id("foreign-company"));
                System.out.println("Company="+StagingSeed.id("company")+" Bank="+StagingSeed.id("bank")+" IncomeCategory="+StagingSeed.id("income")+" ExpenseCategory="+StagingSeed.id("expense"));
            }
        } catch(Exception failure) {System.err.println("Staging provisioning refused or failed; no configuration values logged.");System.exit(2);}
    }
    static String encodePassword(String password) {return PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(password);}
    static void validateTarget(Map<String,String> env) {
        var profiles=Arrays.stream(required(env,"SPRING_PROFILES_ACTIVE").split(",")).map(String::trim).collect(Collectors.toSet());
        if(!profiles.equals(Set.of("prod","staging")))throw new IllegalArgumentException("Staging profiles required");
        if(!"PROVISION_SYNTHETIC_STAGING".equals(env.get("FINEMP_STAGING_SEED_CONFIRM")) || !"finemp-staging".equals(env.get("RAILWAY_PROJECT_NAME"))
                || !"staging".equals(env.get("RAILWAY_ENVIRONMENT_NAME")))throw new IllegalArgumentException("Unconfirmed staging target");
        UUID expected=UUID.fromString(required(env,"FINEMP_STAGING_PROJECT_ID"));
        if(!expected.equals(UUID.fromString(required(env,"RAILWAY_PROJECT_ID"))))throw new IllegalArgumentException("Staging project mismatch");
        if(!required(env,"SPRING_DATASOURCE_URL").matches("jdbc:postgresql://[a-zA-Z0-9-]+\\.railway\\.internal:[0-9]{1,5}/[a-zA-Z0-9_]+"))throw new IllegalArgumentException("Private staging datasource required");
    }
    private static String required(Map<String,String> env,String key) {String value=env.get(key);if(value==null || value.isBlank())throw new IllegalArgumentException("Missing provisioning configuration");return value;}
}
