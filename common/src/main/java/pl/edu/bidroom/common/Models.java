package pl.edu.bidroom.common;

import java.time.Instant;
import java.util.UUID;

public final class Models {
    private Models() { }
    public enum Status { DRAFT, SCHEDULED, ACTIVE, FINISHED, CANCELLED;
        public boolean terminal() { return this == FINISHED || this == CANCELLED; }
    }
    public record User(Long id, String login, String displayName, String role, boolean enabled, UUID legacyId) {
        public User(Long id,String login,String displayName,String role,boolean enabled) { this(id,login,displayName,role,enabled,null); }
        public boolean admin() { return "ADMIN".equals(role); }
    }
    public record Auction(UUID auctionId, Long ownerId, String ownerName, String title, String description,
                          String category, Status status, long startingPrice, long minIncrement,
                          Instant startAt, Instant initialEndAt, Instant endAt, long durationSeconds,
                          Long currentPrice, Long leaderId, String leaderName, long bidCount, long version,
                          Instant publishedAt, Instant finishedAt, String cancelReason, long auctionNumber, long photoVersion) {
        public Long salePrice() { return status == Status.FINISHED ? currentPrice : null; }
        public Long winnerId() { return status == Status.FINISHED ? leaderId : null; }
    }
    public record Credentials(String login, String password) { }
    public record Registration(String login, String displayName, String password) { }
    public record NewUser(String login, String displayName, String password, String role) { }
    public record UserStatus(Long userId, boolean enabled, String reason) { }
    public record Draft(UUID auctionId, long expectedVersion, String title, String description, String category,
                        long startingPrice, long minIncrement, Instant startAt, long durationSeconds) { }
    public record Publish(UUID auctionId, long expectedVersion) { }
    public record Cancel(UUID auctionId, String reason) { }
    public record PlaceBid(UUID auctionId, long amount) { }
    public record Bid(UUID bidId, long sequence, Long bidderId, String bidderName, long amount, Instant acceptedAt) { }
    public record BidReceipt(UUID bidId, long amount, Instant acceptedAt, long auctionVersion, Instant endAt, Auction auction) { }
}
