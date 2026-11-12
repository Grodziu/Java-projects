package pl.edu.bidroom.common;

import com.fasterxml.jackson.databind.JsonNode;
import pl.edu.bidroom.common.Models.Auction;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

public final class PhotoTransfer {
    private PhotoTransfer() { }
    @FunctionalInterface public interface Request {JsonNode call(String operation,Object payload) throws IOException;}
    public static Auction upload(Request request,Auction auction,byte[] bytes) throws Exception {
        if(bytes.length==0||bytes.length>2*1024*1024)throw new IOException("Wybierz zdjęcie JPG lub PNG do 2 MB.");
        String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        UUID id=UUID.fromString(request.call("PHOTO_BEGIN",Map.of("auctionId",auction.auctionId(),"expectedVersion",auction.version(),"bytes",bytes.length,"sha256",sha)).path("uploadId").asText());
        try {
            for(int offset=0;offset<bytes.length;offset+=16384) {
                int end=Math.min(offset+16384,bytes.length);JsonNode ack=request.call("PHOTO_CHUNK",Map.of("uploadId",id,"offset",offset,"base64",Base64.getEncoder().encodeToString(Arrays.copyOfRange(bytes,offset,end))));
                if(ack.path("nextOffset").asInt()!=end)throw new IOException("Niespójne potwierdzenie zdjęcia.");
            }
            return Json.read(request.call("PHOTO_FINISH",Map.of("uploadId",id)),Auction.class);
        }finally {try {request.call("PHOTO_ABORT",Map.of("uploadId",id));}catch(IOException ignored) {}}
    }
    public static byte[] download(Request request,Auction auction) throws Exception {
        JsonNode info=request.call("GET_PHOTO",Map.of("auctionId",auction.auctionId()));if(!info.path("available").asBoolean())return null;
        int size=info.path("bytes").asInt();if(size<=0||size>2*1024*1024)throw new IOException("Nieprawidłowy rozmiar zdjęcia.");
        ByteArrayOutputStream out=new ByteArrayOutputStream(size);long version=info.path("version").asLong();
        while(out.size()<size) {
            JsonNode part=request.call("GET_PHOTO_CHUNK",Map.of("auctionId",auction.auctionId(),"version",version,"offset",out.size()));byte[] bytes=Base64.getDecoder().decode(part.path("base64").asText());
            if(bytes.length==0||bytes.length>16384||out.size()+bytes.length>size)throw new IOException("Nieprawidłowy fragment zdjęcia.");out.writeBytes(bytes);
            if(part.path("nextOffset").asInt()!=out.size())throw new IOException("Niespójny fragment zdjęcia.");
        }
        byte[] bytes=out.toByteArray();if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(info.path("sha256").asText()))throw new IOException("Nieprawidłowa suma kontrolna zdjęcia.");return bytes;
    }
}
