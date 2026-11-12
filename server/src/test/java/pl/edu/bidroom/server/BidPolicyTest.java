package pl.edu.bidroom.server;

import org.junit.jupiter.api.Test;
import pl.edu.bidroom.common.*;
import pl.edu.bidroom.common.Models.*;
import pl.edu.bidroom.server.domain.BidPolicy;
import java.time.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BidPolicyTest {
    private final Instant now=Instant.parse("2026-11-10T10:00:00Z");
    @Test void exactExtensionBoundariesAndNoRounding() {
        long[][] samples={{90000000,90000000},{60000000,60000000},{59999000,60000000},{50000000,60000000},{20000000,50000000},{5000000,35000000},{1,30000001}};
        for(long[] s:samples)assertEquals(now.plusNanos(s[1]*1000),BidPolicy.extend(now.plusNanos(s[0]*1000),now));
        assertThrows(BusinessException.class,()->BidPolicy.extend(now,now));assertThrows(BusinessException.class,()->BidPolicy.extend(now.minusNanos(1000),now));
        assertEquals(now.plusNanos(123456000),BidPolicy.precise(now.plusNanos(123456789)));
    }
    @Test void priceAndOwnershipRules() {
        Long owner=1L,bidder=2L;Auction a=auction(owner,null,Status.ACTIVE);
        assertEquals(a.endAt(),BidPolicy.accept(a,bidder,100,now));
        assertEquals("BID_TOO_LOW",assertThrows(BusinessException.class,()->BidPolicy.accept(a,bidder,99,now)).code());
        assertEquals("OWN_AUCTION",assertThrows(BusinessException.class,()->BidPolicy.accept(a,owner,100,now)).code());
        assertThrows(BusinessException.class,()->BidPolicy.accept(a,bidder,0,now));assertThrows(BusinessException.class,()->BidPolicy.accept(a,bidder,BidPolicy.MAX_AMOUNT+1,now));
        assertThrows(BusinessException.class,()->BidPolicy.accept(auction(owner,100L,Status.ACTIVE),bidder,109,now));
        assertDoesNotThrow(()->BidPolicy.accept(auction(owner,100L,Status.ACTIVE),bidder,110,now));
        assertThrows(BusinessException.class,()->BidPolicy.accept(auction(owner,BidPolicy.MAX_AMOUNT,Status.ACTIVE),bidder,BidPolicy.MAX_AMOUNT,now));
        assertThrows(BusinessException.class,()->BidPolicy.accept(auction(owner,null,Status.FINISHED),bidder,100,now));
    }
    private Auction auction(Long owner,Long price,Status status) { return new Auction(UUID.randomUUID(),owner,"Owner","Test","","OTHER",status,100,10,now.minusSeconds(60),now.plusSeconds(90),now.plusSeconds(90),150,price,price==null?null:2L,null,price==null?0:1,1,now.minusSeconds(60),null,null,1,0); }
}
