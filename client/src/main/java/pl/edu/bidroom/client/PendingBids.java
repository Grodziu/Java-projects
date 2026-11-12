package pl.edu.bidroom.client;

import pl.edu.bidroom.common.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class PendingBids {
    public record Pending(Long userId,UUID requestId,UUID auctionId,long amount) { }
    private final Path directory;
    public PendingBids(Path root) throws IOException { directory=root.resolve("pending-bids");Files.createDirectories(directory); }
    public void save(Pending p) throws IOException {
        try(var files=Files.list(directory)) { if(files.limit(16).count()>=16)throw new IOException("Najpierw rozstrzygnij oczekujące oferty."); }
        try(var channel=java.nio.channels.FileChannel.open(file(p),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
            var bytes=java.nio.ByteBuffer.wrap(Json.bytes(p));while(bytes.hasRemaining())channel.write(bytes);channel.force(true);
        }
    }
    public List<Pending> forUser(Long user) throws IOException {
        List<Pending> result=new ArrayList<>();
        try(var files=Files.newDirectoryStream(directory,user+"-*.json")) {
            for(Path path:files) { if(result.size()>=16)break;if(Files.size(path)>2048)throw new IOException("Uszkodzony rekord oczekującej oferty.");result.add(Json.MAPPER.readValue(Files.readAllBytes(path),Pending.class)); }
        }
        return result;
    }
    public List<Pending> forUser(Long user,UUID legacyId) throws IOException {
        if(legacyId!=null)try(var files=Files.newDirectoryStream(directory,legacyId+"-*.json")) {
            for(Path path:files) {
                if(Files.size(path)>2048)throw new IOException("Uszkodzony rekord oczekującej oferty.");
                var node=(com.fasterxml.jackson.databind.node.ObjectNode)Json.MAPPER.readTree(Files.readAllBytes(path));node.put("userId",user);
                Pending pending=Json.read(node,Pending.class);Path target=file(pending);
                if(!Files.exists(target)) {
                    Path temporary=target.resolveSibling(target.getFileName()+".migrating");
                    try(var channel=java.nio.channels.FileChannel.open(temporary,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)) {
                        var bytes=java.nio.ByteBuffer.wrap(Json.bytes(pending));while(bytes.hasRemaining())channel.write(bytes);channel.force(true);
                    }
                    Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE);
                }
                Files.delete(path);
            }
        }
        return forUser(user);
    }
    public void remove(Pending pending) throws IOException { Files.deleteIfExists(file(pending)); }
    private Path file(Pending p) { return directory.resolve(p.userId()+"-"+p.requestId()+".json"); }
}
