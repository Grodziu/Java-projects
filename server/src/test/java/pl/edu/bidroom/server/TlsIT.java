package pl.edu.bidroom.server;

import org.junit.jupiter.api.*;
import pl.edu.bidroom.common.*;
import javax.net.ssl.*;
import java.io.*;
import java.nio.file.*;
import java.security.KeyStore;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(60)
class TlsIT {
    @Test void tlsTrustHostnameAndExpiryAreEnforced() throws Exception {
        String url=System.getenv("BIDROOM_TEST_DB_URL");assertNotNull(url);assertTrue(url.endsWith("/bidroom_test"));
        Path root=Path.of(System.getenv("BIDROOM_TEST_DATA_DIR")).resolve("tls-"+UUID.randomUUID());Files.createDirectories(root);String password=UUID.randomUUID().toString();
        SSLContext original=SSLContext.getDefault();
        try {
            for(String scenario:List.of("valid","wrong-host","expired")) {
                Path key=root.resolve(scenario+".p12");
                List<String> args=new ArrayList<>(List.of("-genkeypair","-alias","server","-keyalg","RSA","-keysize","2048","-storetype","PKCS12","-keystore",key.toString(),"-storepass",password,"-keypass",password,"-dname","CN="+(scenario.equals("wrong-host")?"wrong.invalid":"localhost"),"-ext","SAN=dns:"+(scenario.equals("wrong-host")?"wrong.invalid":"localhost"),"-validity",scenario.equals("expired")?"1":"7","-noprompt"));
                if(scenario.equals("expired"))args.addAll(List.of("-startdate","2020/01/01 00:00:00"));
                keytool(args,root);
                ServerConfig cfg=new ServerConfig("production","127.0.0.1",0,url,System.getenv("BIDROOM_DB_USER"),System.getenv("BIDROOM_DB_PASSWORD"),4,16,16,true,key.toString(),password,System.getenv("BIDROOM_COMMAND_HMAC_SECRET"),root.resolve(scenario),null,null);
                try(ServerApp app=new ServerApp(cfg,Clock.systemUTC())) {
                    KeyStore certs=KeyStore.getInstance("PKCS12");try(InputStream in=Files.newInputStream(key)){certs.load(in,password.toCharArray());}
                    KeyStore trust=KeyStore.getInstance("PKCS12");trust.load(null,null);trust.setCertificateEntry("server",certs.getCertificate("server"));
                    TrustManagerFactory factory=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());factory.init(trust);
                    SSLContext trusted=SSLContext.getInstance("TLS");trusted.init(null,factory.getTrustManagers(),null);SSLContext.setDefault(trusted);
                    if(scenario.equals("valid")) {
                        try(RpcConnection c=new RpcConnection("localhost",app.port(),true,e->{})) { assertTrue(c.request("HELLO",Map.of()).ok()); }
                        SSLContext untrusted=SSLContext.getInstance("TLS");untrusted.init(null,null,null);SSLContext.setDefault(untrusted);
                        assertThrows(IOException.class,()->new RpcConnection("localhost",app.port(),true,e->{}));
                    } else if(scenario.equals("wrong-host"))assertThrows(IOException.class,()->new RpcConnection("localhost",app.port(),true,e->{}));
                    else {
                        // Direct trust anchors need not be validity-checked by PKIX. The client explicitly checks the peer leaf.
                        assertThrows(IOException.class,()->new RpcConnection("localhost",app.port(),true,e->{}));
                    }
                }
            }
        }finally { SSLContext.setDefault(original); }
    }
    private void keytool(List<String> args,Path directory) throws Exception {
        List<String> command=new ArrayList<>();command.add(Path.of(System.getProperty("java.home"),"bin","keytool.exe").toString());command.addAll(args);
        Process process=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(directory.resolve("keytool.log").toFile()).start();
        if(!process.waitFor(20,TimeUnit.SECONDS)){process.destroyForcibly();fail("keytool przekroczył czas");}assertEquals(0,process.exitValue());
    }
}
