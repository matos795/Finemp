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
    /**
     * One-shot modes. The staging target guards are shared by all modes; each
     * mode then requires its own confirmation, so the fixture opt-in never
     * authorizes elevating platform privileges and vice versa (#334).
     */
    enum Mode {
        PROVISION(null,"FINEMP_STAGING_SEED_CONFIRM","PROVISION_SYNTHETIC_STAGING",true),
        ISOLATION_COMPANY("--isolation-company","FINEMP_STAGING_SEED_CONFIRM","PROVISION_SYNTHETIC_STAGING",false),
        RESET_PASSWORD("--reset-password","FINEMP_STAGING_SEED_CONFIRM","PROVISION_SYNTHETIC_STAGING",true),
        PLATFORM_ADMIN("--platform-admin","FINEMP_STAGING_PLATFORM_ADMIN_CONFIRM","GRANT_SYNTHETIC_STAGING_PLATFORM_ADMIN",false),
        SYSTEM_ADMIN("--system-admin","FINEMP_STAGING_SYSTEM_ADMIN_CONFIRM","GRANT_SYNTHETIC_STAGING_SYSTEM_ADMIN",false);

        private final String flag,confirmationKey,confirmationValue;private final boolean password;
        Mode(String flag,String confirmationKey,String confirmationValue,boolean password) {
            this.flag=flag;this.confirmationKey=confirmationKey;this.confirmationValue=confirmationValue;this.password=password;
        }
        boolean needsPassword() {return password;}
        static Mode parse(String[] args) {
            if(args.length==0)return PROVISION;
            if(args.length==1)for(var mode:values())if(args[0].equals(mode.flag))return mode;
            throw new IllegalArgumentException("Unsupported provisioning arguments");
        }
    }

    public static void main(String[] args) {
        if(args.length==1 && "--help".equals(args[0])) {System.out.println("FINEMP staging provisioner: explicit environment-only configuration required; no secrets as arguments. Optional --isolation-company creates a negative-access fixture; --reset-password resets only the deterministic synthetic operator; --platform-admin grants only that operator the limited platform administration permissions; --system-admin grants SYSTEM_ADMIN to that operator. Each elevation mode needs its own confirmation.");return;}
        try {
            var mode=Mode.parse(args);
            var env=System.getenv();validateTarget(env,mode);
            String hash=null;
            if(mode.needsPassword()) {
                String password=required(env,"FINEMP_STAGING_SEED_PASSWORD");
                if(!password.matches("[\\x21-\\x7E]{16,64}"))throw new IllegalArgumentException();
                hash=encodePassword(password);
            }
            try(var connection=DriverManager.getConnection(required(env,"SPRING_DATASOURCE_URL"),required(env,"SPRING_DATASOURCE_USERNAME"),required(env,"SPRING_DATASOURCE_PASSWORD"))) {
                switch(mode) {
                    case RESET_PASSWORD -> {
                        StagingSeed.resetSyntheticPassword(connection,hash);
                        System.out.println("Synthetic staging operator password reset.");
                    }
                    case PLATFORM_ADMIN -> System.out.println(StagingSeed.grantSyntheticPlatformAdmin(connection)
                            ?"Synthetic staging operator granted platform administration."
                            :"Synthetic staging operator already has platform administration; unchanged.");
                    case SYSTEM_ADMIN -> System.out.println(StagingSeed.grantSyntheticSystemAdmin(connection)
                            ?"Synthetic staging operator granted system administration."
                            :"Synthetic staging operator already has system administration; unchanged.");
                    case ISOLATION_COMPANY -> {
                        boolean created=StagingSeed.provisionIsolationCompany(connection);
                        System.out.println(created?"Synthetic staging dataset created.":"Synthetic staging dataset already exists; unchanged.");
                        System.out.println("IsolationCompany="+StagingSeed.id("foreign-company"));
                    }
                    case PROVISION -> {
                        boolean created=StagingSeed.provision(connection,hash);
                        System.out.println(created?"Synthetic staging dataset created.":"Synthetic staging dataset already exists; unchanged.");
                    }
                }
                System.out.println("Company="+StagingSeed.id("company")+" Bank="+StagingSeed.id("bank")+" IncomeCategory="+StagingSeed.id("income")+" ExpenseCategory="+StagingSeed.id("expense"));
            }
        } catch(Exception failure) {System.err.println("Staging provisioning refused or failed; no configuration values logged.");System.exit(2);}
    }
    static String encodePassword(String password) {return PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(password);}
    /** Fixture modes (default, isolation, reset): unchanged contract with the seed opt-in. */
    static void validateTarget(Map<String,String> env) {validateTarget(env,Mode.PROVISION);}
    static void validateTarget(Map<String,String> env,Mode mode) {
        var profiles=Arrays.stream(required(env,"SPRING_PROFILES_ACTIVE").split(",")).map(String::trim).collect(Collectors.toSet());
        if(!profiles.equals(Set.of("prod","staging")))throw new IllegalArgumentException("Staging profiles required");
        if(!"finemp-staging".equals(env.get("RAILWAY_PROJECT_NAME")) || !"staging".equals(env.get("RAILWAY_ENVIRONMENT_NAME")))
            throw new IllegalArgumentException("Unconfirmed staging target");
        UUID expected=UUID.fromString(required(env,"FINEMP_STAGING_PROJECT_ID"));
        if(!expected.equals(UUID.fromString(required(env,"RAILWAY_PROJECT_ID"))))throw new IllegalArgumentException("Staging project mismatch");
        if(!required(env,"SPRING_DATASOURCE_URL").matches("jdbc:postgresql://[a-zA-Z0-9-]+\\.railway\\.internal:[0-9]{1,5}/[a-zA-Z0-9_]+"))throw new IllegalArgumentException("Private staging datasource required");
        if(!mode.confirmationValue.equals(env.get(mode.confirmationKey)))throw new IllegalArgumentException("Unconfirmed staging operation");
    }
    private static String required(Map<String,String> env,String key) {String value=env.get(key);if(value==null || value.isBlank())throw new IllegalArgumentException("Missing provisioning configuration");return value;}
}
