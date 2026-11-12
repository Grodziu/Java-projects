package pl.edu.bidroom.server.domain;

import pl.edu.bidroom.common.Models.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import static pl.edu.bidroom.common.BusinessException.require;

public final class BidPolicy {
    public static final long MAX_AMOUNT=1_000_000_000_000L;
    private BidPolicy() { }
    public static Instant precise(Instant time) { return time.truncatedTo(ChronoUnit.MICROS); }
    public static long minimum(Auction a) { return a.currentPrice()==null?a.startingPrice():Math.addExact(a.currentPrice(),a.minIncrement()); }
    public static Instant accept(Auction a,Long user,long amount,Instant now) {
        require(!a.ownerId().equals(user),"OWN_AUCTION","Nie możesz licytować własnej aukcji.");
        require(a.status()==Status.ACTIVE,"AUCTION_NOT_ACTIVE","Aukcja nie jest aktywna.");
        require(now.isBefore(a.endAt()),"AUCTION_ENDED","Czas aukcji minął.");
        require(amount>0 && amount<=MAX_AMOUNT,"VALIDATION_ERROR","Kwota musi mieścić się w zakresie 1–1 000 000 000 000 pkt.");
        require(amount>=minimum(a),"BID_TOO_LOW","Minimalna oferta: "+minimum(a)+" pkt.");
        return extend(a.endAt(),now);
    }
    public static Instant extend(Instant end,Instant now) {
        require(now.isBefore(end),"AUCTION_ENDED","Czas aukcji minął.");
        if(Duration.between(now,end).compareTo(Duration.ofSeconds(60))>=0) return end;
        Instant plus=end.plusSeconds(30),cap=now.plusSeconds(60); return plus.isBefore(cap)?plus:cap;
    }
}
