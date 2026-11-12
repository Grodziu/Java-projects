package pl.edu.bidroom.server;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Test-only loopback proxy. Drops PostgreSQL's COMMIT acknowledgement for one bid.
 * Message framing: https://www.postgresql.org/docs/17/protocol-message-formats.html
 * No SQL values, authentication messages or credentials are recorded.
 */
final class PgCommitFaultProxy implements AutoCloseable {
    private final ServerSocket listener = new ServerSocket();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean armed = new AtomicBoolean();
    private final CountDownLatch dropped = new CountDownLatch(1);
    private final InetSocketAddress upstream;
    private volatile boolean closed;

    PgCommitFaultProxy(String jdbcUrl) throws IOException {
        URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
        if (!InetAddress.getByName(uri.getHost()).isLoopbackAddress()
                || !"/bidroom_test".equals(uri.getPath()) || uri.getQuery() != null) {
            throw new IllegalArgumentException("Fault injection requires local bidroom_test");
        }
        upstream = new InetSocketAddress(uri.getHost(), uri.getPort() < 0 ? 5432 : uri.getPort());
        listener.bind(new InetSocketAddress("127.0.0.1", 0));
        executor.submit((Callable<Void>) this::accept);
    }

    String jdbcUrl() {
        return "jdbc:postgresql://127.0.0.1:" + listener.getLocalPort()
                + "/bidroom_test?sslmode=disable&gssEncMode=disable";
    }

    void arm() { armed.set(true); }
    boolean awaitDropped() throws InterruptedException { return dropped.await(5, TimeUnit.SECONDS); }

    private Void accept() throws IOException {
        while (!closed) {
            Socket client;
            try { client = listener.accept(); }
            catch (SocketException e) { if (closed) break; throw e; }
            Socket database = new Socket();
            sockets.add(client);
            sockets.add(database);
            try {
                database.connect(upstream, 3000);
                client.setSoTimeout(30000);
                database.setSoTimeout(30000);
                client.setTcpNoDelay(true);
                database.setTcpNoDelay(true);
                AtomicBoolean bidTransaction = new AtomicBoolean();
                executor.submit((Callable<Void>) () -> relay(client, database, true, bidTransaction));
                executor.submit((Callable<Void>) () -> relay(database, client, false, bidTransaction));
            } catch (IOException | RuntimeException e) {
                closeSocket(client);
                closeSocket(database);
                throw e;
            }
        }
        return null;
    }

    private Void relay(Socket source, Socket destination, boolean frontend, AtomicBoolean bidTransaction) {
        try {
            DataInputStream input = new DataInputStream(source.getInputStream());
            DataOutputStream output = new DataOutputStream(destination.getOutputStream());
            // StartupMessage has no type byte. TLS/GSS encryption is disabled only in this test URL.
            if (frontend) {
                int size = input.readInt();
                byte[] startup = body(input, size);
                output.writeInt(size);
                output.write(startup);
                output.flush();
            }
            while (!closed) {
                int type = input.read();
                if (type < 0) break;
                int size = input.readInt();
                byte[] bytes = body(input, size);
                if (frontend && (type == 'P' || type == 'Q')) {
                    int queryStart = 0;
                    if (type == 'P') {
                        while (queryStart < bytes.length && bytes[queryStart] != 0) queryStart++;
                        queryStart++;
                    }
                    if (queryStart < bytes.length && new String(bytes, queryStart,
                            bytes.length - queryStart, StandardCharsets.UTF_8).startsWith("INSERT INTO bid ")) {
                        bidTransaction.set(true);
                    }
                }
                if (!frontend && type == 'C') {
                    String tag = new String(bytes, StandardCharsets.UTF_8);
                    if (tag.equals("COMMIT\0") && bidTransaction.get() && armed.compareAndSet(true, false)) {
                        // PostgreSQL has committed, but JDBC sees EOF without CommandComplete/ReadyForQuery.
                        dropped.countDown();
                        break;
                    }
                    if (tag.equals("COMMIT\0") || tag.equals("ROLLBACK\0")) bidTransaction.set(false);
                }
                output.writeByte(type);
                output.writeInt(size);
                output.write(bytes);
                output.flush();
            }
        } catch (IOException expected) {
            // Peer shutdown and the deliberately broken connection close both directions.
        } finally {
            closeSocket(source);
            closeSocket(destination);
        }
        return null;
    }

    private static byte[] body(DataInputStream input, int size) throws IOException {
        if (size < 4 || size > 1024 * 1024) throw new IOException("Invalid PostgreSQL test frame");
        byte[] bytes = new byte[size - 4];
        input.readFully(bytes);
        return bytes;
    }

    private void closeSocket(Socket socket) {
        sockets.remove(socket);
        try { socket.close(); } catch (IOException ignored) { }
    }

    @Override public void close() throws IOException {
        closed = true;
        listener.close();
        sockets.forEach(this::closeSocket);
        executor.shutdownNow();
        try { executor.awaitTermination(3, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
