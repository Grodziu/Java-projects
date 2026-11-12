package pl.edu.bidroom.common;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

public final class FrameCodec {
    public static final int MAX_BYTES = 65_536;
    private FrameCodec() { }
    public static byte[] encode(Wire frame) throws IOException {
        byte[] json = Json.bytes(frame);
        checkSize(json.length);
        return ByteBuffer.allocate(json.length + 4).putInt(json.length).put(json).array();
    }
    public static Wire read(InputStream input) throws IOException {
        DataInputStream in = new DataInputStream(input);
        int size = in.readInt(); checkSize(size);
        byte[] body = new byte[size]; in.readFully(body);
        return Json.MAPPER.readValue(body, Wire.class);
    }
    /** The frame deadline includes header and body; a dribbling peer cannot reset it. */
    public static Wire read(Socket socket, int idleMillis) throws IOException {
        InputStream in = socket.getInputStream();
        socket.setSoTimeout(idleMillis);
        int first = in.read();
        if (first < 0) throw new EOFException();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        byte[] header = new byte[4]; header[0] = (byte)first;
        readUntil(socket,in,header,1,3,deadline);
        int size = ByteBuffer.wrap(header).getInt(); checkSize(size);
        byte[] body = new byte[size]; readUntil(socket,in,body,0,size,deadline);
        return Json.MAPPER.readValue(body,Wire.class);
    }
    private static void readUntil(Socket socket, InputStream in, byte[] bytes, int offset, int count, long deadline) throws IOException {
        int end = offset + count;
        while (offset < end) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new SocketTimeoutException("Przekroczono czas ramki");
            socket.setSoTimeout((int)Math.max(1,TimeUnit.NANOSECONDS.toMillis(remaining)));
            int n = in.read(bytes,offset,end-offset);
            if (n < 0) throw new EOFException("Niepełna ramka");
            offset += n;
        }
    }
    private static void checkSize(int size) throws IOException {
        if (size < 1 || size > MAX_BYTES) throw new IOException("Niedozwolony rozmiar ramki");
    }
}
