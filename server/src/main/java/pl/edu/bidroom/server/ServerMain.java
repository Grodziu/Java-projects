package pl.edu.bidroom.server;

import java.nio.file.*;
import java.time.Clock;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class ServerMain {
    private ServerMain() { }
    public static void main(String[] args) throws Exception {
        ServerConfig config=ServerConfig.environment();config.validate();Files.createDirectories(config.dataDir());
        Path stop=config.dataDir().resolve("stop.signal");Files.deleteIfExists(stop);
        AtomicBoolean stopping=new AtomicBoolean();AtomicReference<ServerApp> current=new AtomicReference<>();
        // Shutdown hooks require a Thread object. It is registered with the JVM, never started by application code.
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(()-> { stopping.set(true);ServerApp app=current.get();if(app!=null)app.close(); }));
        while(!stopping.get()&&!Files.exists(stop)) {
            try(ServerApp app=new ServerApp(config,Clock.systemUTC())) {
                current.set(app);System.out.println("BIDROOM_READY port="+app.port()+" profile="+config.profile());
                while(app.isHealthy()&&!stopping.get()&&!Files.exists(stop))TimeUnit.MILLISECONDS.sleep(300);
            } catch(IllegalStateException e) { System.err.println(e.getMessage());break; }
            catch(Exception e) { System.err.println("Serwer niedostępny ("+e.getClass().getSimpleName()+"); ponowne uzgodnienie za 3 s."); }
            finally { current.set(null); }
            if(!Files.exists(stop)&&!stopping.get())TimeUnit.SECONDS.sleep(3);
        }
    }
}
