package pl.edu.bidroom.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PendingBidsTest {
    @Test void legacyAccountNumberMigrationRetainsRequestAndAmount() throws Exception {
        var store=new PendingBids(directory);UUID legacy=UUID.randomUUID(),request=UUID.randomUUID(),auction=UUID.randomUUID();
        java.nio.file.Path old=directory.resolve("pending-bids").resolve(legacy+"-"+request+".json");
        java.nio.file.Files.write(old,pl.edu.bidroom.common.Json.bytes(java.util.Map.of("userId",legacy,"requestId",request,"auctionId",auction,"amount",750)));
        var expected=new PendingBids.Pending(7L,request,auction,750);assertEquals(List.of(expected),store.forUser(7L,legacy));assertFalse(java.nio.file.Files.exists(old));
        assertEquals(List.of(expected),new PendingBids(directory).forUser(7L,legacy));store.remove(expected);assertTrue(store.forUser(7L).isEmpty());
    }
    @TempDir Path directory;
    @Test void restartPreservesIdentityAndOnlyMatchingAccountCanRecover() throws Exception {
        var pending=new PendingBids.Pending(1L,UUID.randomUUID(),UUID.randomUUID(),500);
        new PendingBids(directory).save(pending);
        var restarted=new PendingBids(directory);assertEquals(List.of(pending),restarted.forUser(pending.userId()));assertTrue(restarted.forUser(2L).isEmpty());
        restarted.remove(pending);assertTrue(new PendingBids(directory).forUser(pending.userId()).isEmpty());
    }
    @Test void boundedBacklogAndDuplicateRecordCannotOverwriteOriginal() throws Exception {
        var store=new PendingBids(directory);Long user=1L;
        var first=new PendingBids.Pending(user,UUID.randomUUID(),UUID.randomUUID(),100);store.save(first);
        assertThrows(FileAlreadyExistsException.class,()->store.save(new PendingBids.Pending(user,first.requestId(),first.auctionId(),999)));
        assertEquals(first,store.forUser(user).getFirst());
        for(int i=1;i<16;i++)store.save(new PendingBids.Pending(user,UUID.randomUUID(),UUID.randomUUID(),100));
        assertThrows(java.io.IOException.class,()->store.save(new PendingBids.Pending(user,UUID.randomUUID(),UUID.randomUUID(),100)));
    }
}
