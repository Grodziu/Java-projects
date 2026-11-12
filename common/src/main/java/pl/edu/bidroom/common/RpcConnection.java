package pl.edu.bidroom.common;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** One reader and one writer per socket. Blocking request() belongs on an executor, never on JavaFX. */
public final class RpcConnection implements AutoCloseable {
    private final Socket socket;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<UUID, CompletableFuture<Wire>> pending = new ConcurrentHashMap<>();
    private final Semaphore permits = new Semaphore(16);
    private final ArrayBlockingQueue<byte[]> outgoing = new ArrayBlockingQueue<>(16);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Consumer<Wire> events;
    private volatile long writingSince;
    public RpcConnection(String host, int port, boolean tls, Consumer<Wire> events) throws IOException {
        this.events = events;
        if (!tls && !InetAddress.getByName(host).isLoopbackAddress()) throw new IOException("Połączenie publiczne wymaga TLS");
        socket = tls ? SSLSocketFactory.getDefault().createSocket() : new Socket();
        try {
            socket.connect(new InetSocketAddress(host,port),5000); socket.setTcpNoDelay(true);
            if (socket instanceof SSLSocket ssl) {
                ssl.setSoTimeout(5000);
                SSLParameters parameters = ssl.getSSLParameters(); parameters.setEndpointIdentificationAlgorithm("HTTPS"); ssl.setSSLParameters(parameters);
                ssl.startHandshake();
                try { ((java.security.cert.X509Certificate)ssl.getSession().getPeerCertificates()[0]).checkValidity(); }
                catch(java.security.cert.CertificateException e) { throw new SSLHandshakeException("Certyfikat serwera jest nieważny czasowo"); }
            }
        } catch (IOException e) { socket.close(); executor.shutdownNow(); throw e; }
        executor.submit((Callable<Void>)this::readLoop);
        executor.submit((Callable<Void>)this::writeLoop);
        executor.submit((Callable<Void>)() -> {
            try { while (!closed.get()) { TimeUnit.SECONDS.sleep(1); if (writingSince != 0 && System.nanoTime()-writingSince > TimeUnit.SECONDS.toNanos(10)) close(); } }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return null;
        });
    }
    public Wire request(String operation, Object payload) throws IOException { return request(UUID.randomUUID(),operation,payload); }
    public Wire request(UUID id, String operation, Object payload) throws IOException {
        if (closed.get()) throw new EOFException("Połączenie zamknięte");
        if (!permits.tryAcquire()) throw new IOException("Za dużo oczekujących żądań");
        CompletableFuture<Wire> result = new CompletableFuture<>();
        try {
            if (pending.putIfAbsent(id,result) != null) throw new IOException("Polecenie już oczekuje");
            if (!outgoing.offer(FrameCodec.encode(Wire.request(id,operation,payload)))) throw new IOException("Pełna kolejka nadawcy");
            return result.get(20,TimeUnit.SECONDS);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Przerwano oczekiwanie",e); }
        catch (ExecutionException | TimeoutException e) { close(); throw new IOException("Nie otrzymano potwierdzenia — sprawdzamy wynik po połączeniu.",e); }
        finally { pending.remove(id,result); permits.release(); }
    }
    private Void readLoop() {
        try {
            while (!closed.get()) {
                Wire frame = FrameCodec.read(socket,45000);
                if (frame.protocolVersion()!=2) throw new IOException("Niezgodna wersja protokołu — zainstaluj aktualną wersję BidRoom.");
                if ("EVENT".equals(frame.kind())) events.accept(frame);
                else { CompletableFuture<Wire> future = pending.get(frame.requestId()); if (future != null) future.complete(frame); }
            }
        } catch (Exception e) { close(); }
        return null;
    }
    private Void writeLoop() {
        try {
            while (!closed.get()) {
                byte[] bytes = outgoing.take(); writingSince = System.nanoTime();
                socket.getOutputStream().write(bytes); socket.getOutputStream().flush(); writingSince = 0;
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        catch (IOException e) { close(); }
        return null;
    }
    public boolean isOpen() { return !closed.get(); }
    @Override public void close() {
        if (!closed.compareAndSet(false,true)) return;
        try { socket.close(); } catch (IOException ignored) { }
        pending.values().forEach(f -> f.completeExceptionally(new EOFException("Rozłączono")));
        executor.shutdownNow(); outgoing.clear();
    }
}
