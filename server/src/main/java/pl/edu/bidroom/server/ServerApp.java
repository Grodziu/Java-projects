package pl.edu.bidroom.server;

import pl.edu.bidroom.common.*;
import pl.edu.bidroom.common.Models.Auction;
import pl.edu.bidroom.server.persistence.*;
import pl.edu.bidroom.server.service.*;
import pl.edu.bidroom.server.transport.*;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.KeyStore;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ServerApp implements AutoCloseable {
    private final ServerConfig config;private final Clock clock;
    private Database db;private Connection guard;private AuctionService auctions;private AuthService auth;private LogService logs;private ReportService reports;private PhotoService photos;private final TaskRegistry tasks=new TaskRegistry();private final long started=System.nanoTime();
    private ServerSocket listener;private final ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor();
    private final Set<ClientSessionHandler> clients=ConcurrentHashMap.newKeySet();
    private final Semaphore clientSlots;private final Semaphore bytes=new Semaphore(16*1024*1024);
    private final AtomicBoolean closed=new AtomicBoolean();private volatile boolean healthy=true;
    public ServerApp(ServerConfig config,Clock clock) throws Exception {
        this.config=config;this.clock=clock;config.validate();clientSlots=new Semaphore(config.maxClients());
        try {
            Files.createDirectories(config.dataDir());
            db=new Database(config);
            Properties credentials=new Properties();credentials.setProperty("user",config.dbUser());credentials.setProperty("password",config.dbPassword());credentials.setProperty("connectTimeout","3");credentials.setProperty("socketTimeout","3");
            guard=DriverManager.getConnection(config.dbUrl(),credentials);
            boolean acquired=Boolean.TRUE.equals(Sql.one(guard,"SELECT pg_try_advisory_lock(710000)",r->r.getBoolean(1)));
            if(!acquired)throw new IllegalStateException("Dla tej bazy działa już serwer BidRoom.");
            auth=new AuthService(db,clock);auth.bootstrap(config.adminLogin(),config.adminPassword());
            CommandService commands=new CommandService(clock,config.hmacSecret());
            auctions=new AuctionService(db,auth,commands,clock,config.maxAuctions(),tasks);auctions.onFailure(this::unavailable);
            logs=new LogService(db,config.dataDir(),tasks);tasks.onEvent(logs::record);reports=new ReportService(db,auctions.repository(),config.dataDir(),clock,logs,tasks);photos=new PhotoService(db,auth,auctions,commands,clock);
            auctions.onSnapshot(this::broadcast);
            auth.onRevoke(id->{ reports.clear(id);photos.clear(id);for(ClientSessionHandler client:clients)if(id.equals(client.userId)) { client.send(Wire.event("SESSION_REVOKED",clock.instant(),Map.of()));client.close(); } });
            auctions.recover();
            listener=createListener();listener.bind(new InetSocketAddress(config.host(),config.port()),config.maxClients());
            Dispatcher dispatcher=new Dispatcher(db,auth,auctions,commands,logs,reports,photos,clock,config,this::metrics);
            executor.submit(tasks.track("ACCEPTOR","TCP",()->accept(dispatcher)));executor.submit(tasks.track("MONITOR","stan serwera",this::monitor));
            logs.record("SERVER_STARTED","Uruchomiono serwer i odtworzono aukcje.");
        } catch(Exception e) { close();throw e; }
    }
    private ServerSocket createListener() throws Exception {
        if(!config.tls())return new ServerSocket();
        KeyStore store=KeyStore.getInstance("PKCS12");char[] password=config.keyPassword().toCharArray();
        try(InputStream in=Files.newInputStream(Path.of(config.keyStore()))) { store.load(in,password); }
        KeyManagerFactory manager=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());manager.init(store,password);Arrays.fill(password,'\0');
        SSLContext context=SSLContext.getInstance("TLS");context.init(manager.getKeyManagers(),null,null);
        SSLServerSocket socket=(SSLServerSocket)context.getServerSocketFactory().createServerSocket();socket.setEnabledProtocols(new String[]{"TLSv1.3","TLSv1.2"});return socket;
    }
    private Void accept(Dispatcher dispatcher) {
        while(isHealthy()) {
            try {
                Socket socket=listener.accept();
                if(!clientSlots.tryAcquire()) { socket.close();continue; }
                ClientSessionHandler client=new ClientSessionHandler(socket,dispatcher,executor,bytes,c->{clients.remove(c);clientSlots.release();logs.record("CLIENT_DISCONNECTED","Zamknięto połączenie klienta.");},this::unavailable,tasks);
                clients.add(client);
                try { executor.submit(tasks.track("CLIENT","połączenie",client));logs.record("CLIENT_CONNECTED","Przyjęto połączenie klienta."); }
                catch(RejectedExecutionException e) { client.close();clients.remove(client);clientSlots.release(); }
            } catch(IOException e) { if(!closed.get())unavailable(e);break; }
        }
        return null;
    }
    private Void monitor() {
        try {
            while(isHealthy()) {
                TimeUnit.SECONDS.sleep(1);
                if(!guard.isValid(2)) { unavailable(new SQLException("Utrata blokady instancji"));break; }
                clients.forEach(ClientSessionHandler::checkProgress);auth.sweep();reports.sweep();photos.sweep();
                String metricsFile=System.getProperty("bidroom.metricsFile");
                if(metricsFile!=null) {
                    var sample=Map.of("time",clock.instant().toString(),"clients",clients.size(),"liveAuctions",auctions.liveCount(),"auctionContexts",auctions.runtimes().size(),"activeDatabaseConnections",db.activeConnections());
                    try { Files.writeString(Path.of(metricsFile),Json.node(sample)+System.lineSeparator(),StandardOpenOption.CREATE,StandardOpenOption.APPEND); }
                    catch(IOException e) { logs.record("METRICS_WRITE_FAILED","Nie zapisano próbki pomiarowej."); }
                }
            }
        } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
        catch(Exception e) { unavailable(e); }
        return null;
    }
    private void unavailable(Exception failure) {
        healthy=false;
        if(logs!=null)logs.record("STORAGE_UNAVAILABLE","Wstrzymano pracę; wymagane ponowne uzgodnienie stanu bazy.");
        try { if(listener!=null)listener.close(); }catch(IOException ignored) { }
        clients.forEach(ClientSessionHandler::close);
        if(auctions!=null)auctions.close();
    }
    private void broadcast(Auction auction) {
        for(ClientSessionHandler client:clients) {
            if(client.userId==null)continue;
            // Private drafts are never broadcast, even before subscription authorization finishes.
            if(auction.publishedAt()==null)continue;
            client.send(Wire.event("CATALOG_INVALIDATED",clock.instant(),Map.of()));
            if(client.subscriptions.contains(auction.auctionId()))client.send(Wire.event("AUCTION_SNAPSHOT",clock.instant(),auction));
        }
    }
    public boolean isHealthy() { return healthy&&!closed.get(); }
    public int port() { return listener.getLocalPort(); }
    public Database database() { return db; }
    public AuctionService auctions() { return auctions; }
    public AuthService auth() { return auth; }
    public Object metrics() throws Exception {
        var result=new java.util.LinkedHashMap<String,Object>();
        result.put("uptimeSeconds",TimeUnit.NANOSECONDS.toSeconds(System.nanoTime()-started));result.put("clients",clients.size());result.put("maxClients",config.maxClients());
        result.put("liveAuctions",auctions.liveCount());result.put("maxAuctions",config.maxAuctions());result.put("auctionContexts",auctions.runtimes().size());result.put("tasks",tasks.snapshot());
        var memory=java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();result.put("heapUsedBytes",memory.getUsed());result.put("heapMaxBytes",memory.getMax());
        result.put("platformThreads",java.lang.management.ManagementFactory.getThreadMXBean().getThreadCount());
        var operating=java.lang.management.ManagementFactory.getOperatingSystemMXBean();double cpu=operating instanceof com.sun.management.OperatingSystemMXBean os?os.getProcessCpuLoad():-1;
        result.put("cpuPercent",cpu<0?-1:Math.round(cpu*1000)/10.0);result.put("databaseActive",db.activeConnections());result.put("databasePoolMax",config.poolSize());
        result.put("counts",db.read(c->Sql.one(c,"SELECT (SELECT count(*) FROM app_user),(SELECT count(*) FROM auction),(SELECT count(*) FROM bid),(SELECT count(*) FROM auction_report),(SELECT count(*) FROM auction_photo),(SELECT count(*) FROM diagnostic_log)",r->Map.of("users",r.getLong(1),"auctions",r.getLong(2),"bids",r.getLong(3),"reports",r.getLong(4),"photos",r.getLong(5),"logs",r.getLong(6)))));
        result.put("reportsPath",config.dataDir().resolve("reports").toAbsolutePath().toString());result.put("tls",config.tls());return result;
    }
    public int clientCount() { return clients.size(); }
    @Override public void close() {
        if(!closed.compareAndSet(false,true))return;healthy=false;
        try { if(listener!=null)listener.close(); }catch(IOException ignored) { }
        clients.forEach(ClientSessionHandler::close);if(auctions!=null)auctions.close();executor.shutdownNow();
        try { executor.awaitTermination(5,TimeUnit.SECONDS); }catch(InterruptedException e) { Thread.currentThread().interrupt(); }
        if(reports!=null)reports.close();if(logs!=null) { logs.record("SERVER_STOPPED","Zatrzymano serwer.");logs.close(); }
        if(db!=null)db.close();try { if(guard!=null)guard.close(); }catch(SQLException ignored) { }
    }
}
