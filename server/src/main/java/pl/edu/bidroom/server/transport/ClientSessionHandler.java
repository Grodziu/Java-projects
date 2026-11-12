package pl.edu.bidroom.server.transport;

import pl.edu.bidroom.common.*;
import pl.edu.bidroom.server.service.TaskRegistry;
import java.io.*;
import java.net.Socket;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class ClientSessionHandler implements Callable<Void>,AutoCloseable {
    private final TaskRegistry tasks;private final Socket socket;private final Dispatcher dispatcher;private final ExecutorService executor;
    private final Semaphore globalBytes;private final Semaphore localBytes=new Semaphore(1024*1024);
    private record Outbound(byte[] bytes,boolean closeAfter) { }
    private final ArrayBlockingQueue<Outbound> outgoing=new ArrayBlockingQueue<>(128);
    private final AtomicBoolean closed=new AtomicBoolean();private final Consumer<ClientSessionHandler> completion;
    private final Consumer<Exception> infrastructureFailure;
    private volatile Future<?> writer;private volatile long writingSince;
    public volatile Long userId;public volatile String token;public boolean hello;
    public final Set<UUID> subscriptions=ConcurrentHashMap.newKeySet();
    public ClientSessionHandler(Socket socket,Dispatcher dispatcher,ExecutorService executor,Semaphore budget,Consumer<ClientSessionHandler> completion,Consumer<Exception> failure) { this(socket,dispatcher,executor,budget,completion,failure,new TaskRegistry()); }
    public ClientSessionHandler(Socket socket,Dispatcher dispatcher,ExecutorService executor,Semaphore budget,Consumer<ClientSessionHandler> completion,Consumer<Exception> failure,TaskRegistry tasks) {
        this.tasks=tasks;
        this.socket=socket;this.dispatcher=dispatcher;this.executor=executor;globalBytes=budget;this.completion=completion;infrastructureFailure=failure;
    }
    public String address() { return socket.getInetAddress().getHostAddress(); }
    @Override public Void call() {
        try {
            socket.setTcpNoDelay(true);socket.setSoTimeout(5000);
            if(socket instanceof javax.net.ssl.SSLSocket ssl)ssl.startHandshake();
            writer=executor.submit(tasks.track("WRITER","wysyłanie odpowiedzi",this::sendLoop));
            while(!closed.get()) {
                Wire request=FrameCodec.read(socket,hello?45000:5000);Wire response;
                try { response=dispatcher.dispatch(this,request); }
                catch(BusinessException e) { response=Wire.failure(request.requestId(),Instant.now(),e.code(),e.getMessage()); }
                catch(java.sql.SQLException e) { infrastructureFailure.accept(e);response=Wire.failure(request.requestId(),Instant.now(),"RESULT_UNKNOWN","Baza jest niedostępna. Wynik polecenia zostanie sprawdzony po połączeniu."); }
                catch(Exception e) { response=Wire.failure(request.requestId(),Instant.now(),"INTERNAL_ERROR","Nie można wykonać operacji."); }
                boolean incompatible=response.error()!=null&&response.error().code().equals("PROTOCOL_UNSUPPORTED");
                send(response,incompatible);
                if(incompatible) {
                    try { writer.get(10,TimeUnit.SECONDS); }
                    catch(InterruptedException e) { Thread.currentThread().interrupt(); }
                    catch(ExecutionException|TimeoutException|CancellationException ignored) { }
                    break;
                }
            }
        } catch(IOException ignored) { /* A malformed/partial frame closes only this socket. */ }
        finally { close();completion.accept(this); }
        return null;
    }
    public void send(Wire message) {
        send(message,false);
    }
    private void send(Wire message,boolean closeAfter) {
        try {
            byte[] bytes=FrameCodec.encode(message);if(closed.get())return;
            if(!localBytes.tryAcquire(bytes.length)) { close();return; }
            if(!globalBytes.tryAcquire(bytes.length)) { localBytes.release(bytes.length);close();return; }
            Outbound frame=new Outbound(bytes,closeAfter);
            if(!outgoing.offer(frame)) { release(bytes);close();return; }
            if(closed.get()&&outgoing.remove(frame))release(bytes);
        } catch(IOException e) { close(); }
    }
    private Void sendLoop() {
        try {
            while(!closed.get()) {
                Outbound frame=outgoing.take();byte[] bytes=frame.bytes();
                try { writingSince=System.nanoTime();socket.getOutputStream().write(bytes);socket.getOutputStream().flush(); }
                finally { writingSince=0;release(bytes); }
                if(frame.closeAfter()) { close();return null; }
            }
        } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
        catch(IOException e) { close(); }
        return null;
    }
    private void release(byte[] bytes) { localBytes.release(bytes.length);globalBytes.release(bytes.length); }
    public void checkProgress() { if(writingSince!=0&&System.nanoTime()-writingSince>TimeUnit.SECONDS.toNanos(10))close(); }
    @Override public void close() {
        if(!closed.compareAndSet(false,true))return;
        try { socket.close(); }catch(IOException ignored) { }
        if(writer!=null)writer.cancel(true);Outbound frame;while((frame=outgoing.poll())!=null)release(frame.bytes());
    }
}
