package pl.edu.bidroom.client;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Properties;

public record ClientConfig(String host,int port,boolean tls,Path dataDir) {
    public static ClientConfig load() throws Exception {
        Properties p=new Properties();try(InputStream in=ClientConfig.class.getResourceAsStream("/client.properties")) { p.load(in); }
        boolean production="production".equals(p.getProperty("profile"));
        String host=System.getProperty("bidroom.host",p.getProperty("serverHost"));int port=Integer.parseInt(System.getProperty("bidroom.port",p.getProperty("serverPort")));
        boolean tls=production||Boolean.parseBoolean(System.getProperty("bidroom.tls",p.getProperty("tls")));
        String local=System.getenv().getOrDefault("LOCALAPPDATA",System.getProperty("user.home"));
        Path path=Path.of(System.getProperty("bidroom.dataDir",Path.of(local,"BidRoom","client").toString()));
        return new ClientConfig(host,port,tls,path);
    }
}
