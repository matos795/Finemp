package com.finemp.connector.agent;

import java.io.PrintStream;
import java.nio.file.Path;

/** Separate entry point: deliberately does not call SpringApplication or initialize JPA/HTTP server. */
public final class ConnectorMain {
    private ConnectorMain(){ }
    public static void main(String[] args){int code=execute(args,ConnectorSecretSource.environment(),System.out,System.err);if(code!=0)System.exit(code);}
    static int execute(String[] args,ConnectorSecretSource secrets,PrintStream output,PrintStream error){
        if(args!=null&&args.length==1&&"--help".equals(args[0])){output.println("FINEMP Connector: --validate <local-config.properties> | --run <local-config.properties>");return 0;}
        if(args==null||args.length!=2||!("--validate".equals(args[0])||"--run".equals(args[0]))){error.println("FINEMP Connector: argumentos inválidos. Use --help.");return 2;}
        try{
            var config=ConnectorLocalConfiguration.load(Path.of(args[1]));
            if("--validate".equals(args[0])){
                for(var profile:config.profiles())ConnectorLocalProfile.validateSecrets(profile,secrets);
                output.println("Configuração local válida. Conexões e permissões externas não foram testadas.");return 0;
            }
            var locks=Path.of(System.getProperty("user.home"),".finemp","connector","locks");
            try(var runtime=ConnectorRuntime.open(config,secrets,locks)){
                var shutdown=new Thread(runtime::close,"finemp-connector-shutdown");Runtime.getRuntime().addShutdownHook(shutdown);
                try{
                    output.println("FINEMP Connector: perfis com configuração local pronta: "+runtime.readyProfiles()+" de "+config.profiles().size()+".");
                    runtime.start();runtime.await();error.println("FINEMP Connector: todos os perfis foram interrompidos.");return 3;
                }
                catch(InterruptedException stop){Thread.currentThread().interrupt();return 0;}
                finally{runtime.close();try{Runtime.getRuntime().removeShutdownHook(shutdown);}catch(IllegalStateException exiting){/* JVM shutdown owns the hook. */}}
            }
        }catch(RuntimeException unavailable){error.println("FINEMP Connector: configuração, segredo ou recurso local indisponível.");return 2;}
    }
}
