package pl.edu.bidroom.common;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FrameCodecTest {
    @Test void fragmentedAndCoalescedUtf8Frames() throws Exception {
        Wire one=Wire.request(UUID.randomUUID(),"HELLO",Map.of("text","Zażółć gęślą jaźń"));
        byte[] encoded=FrameCodec.encode(one);ByteArrayOutputStream combined=new ByteArrayOutputStream();combined.write(encoded);combined.write(encoded);
        InputStream fragmented=new FilterInputStream(new ByteArrayInputStream(combined.toByteArray())) {
            @Override public int read(byte[] b,int off,int len) throws IOException { return super.read(b,off,Math.min(2,len)); }
        };
        assertEquals(one,FrameCodec.read(fragmented));assertEquals(one,FrameCodec.read(fragmented));assertThrows(EOFException.class,()->FrameCodec.read(fragmented));
    }
    @Test void invalidLengthsAndTruncatedFramesFailBeforeAllocation() throws Exception {
        for(int length:new int[]{0,-1,65537,Integer.MAX_VALUE})assertThrows(IOException.class,()->FrameCodec.read(new ByteArrayInputStream(ByteBuffer.allocate(4).putInt(length).array())));
        byte[] frame=FrameCodec.encode(Wire.request(UUID.randomUUID(),"PING",Map.of()));
        assertThrows(EOFException.class,()->FrameCodec.read(new ByteArrayInputStream(Arrays.copyOf(frame,frame.length-1))));
    }
    @Test void malformedDeepAndDuplicateJsonRejected() {
        for(String json:new String[]{"{oops}","{\"kind\":\"REQUEST\",\"kind\":\"EVENT\"}","[".repeat(25)+"0"+"]".repeat(25)}) {
            byte[] body=json.getBytes(java.nio.charset.StandardCharsets.UTF_8);byte[] frame=ByteBuffer.allocate(body.length+4).putInt(body.length).put(body).array();
            assertThrows(IOException.class,()->FrameCodec.read(new ByteArrayInputStream(frame)));
        }
    }
}
