package com.finemp.connector.agent;

import com.finemp.integration.questor.QuestorIdentityMapping;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Trusted local configuration contains only secret references, never their values. */
public record ConnectorLocalConfiguration(int pollSeconds, List<Profile> profiles) {
    private static final Set<String> FIELDS=Set.of("source-id","origin","credential-env","jdbc-url-env","jdbc-user-env","jdbc-password-env",
            "identity-table","identity-code-column","identity-cnpj-column","max-records","read-seconds","query-seconds","http-seconds");
    public ConnectorLocalConfiguration {
        if(pollSeconds<1||pollSeconds>60||profiles==null||profiles.isEmpty()||profiles.size()>8)throw invalid();
        profiles=List.copyOf(profiles);var sources=new HashSet<UUID>();var tokens=new HashSet<String>();var jdbc=new HashSet<String>();
        for(var profile:profiles) {
            if(profile==null||!sources.add(profile.sourceId())||!tokens.add(profile.credentialEnv()))throw invalid();
            jdbc.addAll(List.of(profile.jdbcUrlEnv(),profile.jdbcUserEnv(),profile.jdbcPasswordEnv()));
        }
        if(tokens.stream().anyMatch(jdbc::contains))throw invalid();
    }
    public static ConnectorLocalConfiguration load(Path file) {
        try {
            if(file==null||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw invalid();
            byte[] data;try(var input=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)){data=input.readNBytes(32769);}
            if(data.length>32768)throw invalid();
            var properties=new Properties(){@Override public synchronized Object put(Object key,Object value){if(containsKey(key))throw invalid();return super.put(key,value);}};
            properties.load(new InputStreamReader(new ByteArrayInputStream(data),StandardCharsets.UTF_8));return parse(properties);
        }catch(ConnectorClientException safe){throw safe;}catch(IOException|RuntimeException unavailable){throw invalid();}
    }
    static ConnectorLocalConfiguration parse(Properties values) {
        try {
            if(!"1".equals(values.getProperty("version")))throw invalid();
            var names=required(values,"profiles").split(",",-1);if(names.length<1||names.length>8)throw invalid();
            var seen=new HashSet<String>();var allowed=new HashSet<>(Set.of("version","profiles","poll-seconds"));var profiles=new ArrayList<Profile>();
            for(var raw:names) {
                String name=raw.trim();if(!name.matches("[a-z][a-z0-9-]{0,31}")||!seen.add(name))throw invalid();String prefix="profile."+name+".";
                for(var field:FIELDS)allowed.add(prefix+field);
                String id=required(values,prefix+"source-id");UUID source=UUID.fromString(id);if(!source.toString().equals(id))throw invalid();
                profiles.add(new Profile(source,URI.create(required(values,prefix+"origin")),required(values,prefix+"credential-env"),
                        required(values,prefix+"jdbc-url-env"),required(values,prefix+"jdbc-user-env"),required(values,prefix+"jdbc-password-env"),
                        new QuestorIdentityMapping(required(values,prefix+"identity-table"),required(values,prefix+"identity-code-column"),required(values,prefix+"identity-cnpj-column")),
                        integer(values,prefix+"max-records",25000),integer(values,prefix+"read-seconds",120),integer(values,prefix+"query-seconds",30),integer(values,prefix+"http-seconds",20)));
            }
            if(values.keySet().stream().anyMatch(key->!allowed.contains(key)))throw invalid();
            return new ConnectorLocalConfiguration(integer(values,"poll-seconds",5),profiles);
        }catch(ConnectorClientException safe){throw safe;}catch(RuntimeException invalid){throw invalid();}
    }
    public record Profile(UUID sourceId,URI origin,String credentialEnv,String jdbcUrlEnv,String jdbcUserEnv,String jdbcPasswordEnv,
            QuestorIdentityMapping identity,int maxRecords,int readSeconds,int querySeconds,int httpSeconds) {
        public Profile {
            if(sourceId==null||identity==null||maxRecords<1||maxRecords>100000||readSeconds<1||readSeconds>600
                    ||querySeconds<1||querySeconds>120||httpSeconds<1||httpSeconds>120)throw invalid();
            if(origin==null||!"https".equals(origin.getScheme())||origin.getHost()==null||origin.getRawUserInfo()!=null||origin.getRawQuery()!=null
                    ||origin.getRawFragment()!=null||origin.getPort()>65535||!(origin.getRawPath().isEmpty()||origin.getRawPath().equals("/")))throw invalid();
            for(var reference:new String[]{credentialEnv,jdbcUrlEnv,jdbcUserEnv,jdbcPasswordEnv})
                if(reference==null||!reference.matches("FINEMP_CONNECTOR_[A-Z0-9_]{1,100}"))throw invalid();
            if(new HashSet<>(List.of(credentialEnv,jdbcUrlEnv,jdbcUserEnv,jdbcPasswordEnv)).size()!=4)throw invalid();
        }
        @Override public String toString(){return "ConnectorProfile[redacted]";}
    }
    private static String required(Properties values,String key){String value=values.getProperty(key);if(value==null||value.isBlank())throw invalid();return value.trim();}
    private static int integer(Properties values,String key,int fallback){String value=values.getProperty(key);return value==null?fallback:Integer.parseInt(value);}
    private static ConnectorClientException invalid(){return new ConnectorClientException(ConnectorClientException.Kind.CONFIGURATION);}
    @Override public String toString(){return "ConnectorLocalConfiguration[profiles="+profiles.size()+"]";}
}
