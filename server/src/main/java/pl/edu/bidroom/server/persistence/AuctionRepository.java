package pl.edu.bidroom.server.persistence;

import pl.edu.bidroom.common.*;
import pl.edu.bidroom.common.Models.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public final class AuctionRepository {
    private static final String SELECT="SELECT a.*,o.display_name owner_name,l.display_name leader_name FROM auction a JOIN app_user o ON o.id=a.owner_id LEFT JOIN app_user l ON l.id=a.leader_id ";
    public Auction get(Connection c,UUID id,boolean lock) throws SQLException {
        Auction a=Sql.one(c,SELECT+"WHERE a.id=?"+(lock?" FOR UPDATE OF a":""),this::map,id);
        if(a==null) throw new BusinessException("NOT_FOUND","Nie znaleziono aukcji."); return a;
    }
    public List<Auction> live(Connection c) throws SQLException { return Sql.list(c,SELECT+"WHERE a.status IN ('ACTIVE','SCHEDULED') ORDER BY a.id",this::map); }
    public List<Auction> catalog(Connection c,User user,String view,String query,String category,int offset,int limit) throws SQLException {
        String condition=switch(view) {
            case "mine" -> "a.owner_id=?";
            case "history" -> "a.status IN ('FINISHED','CANCELLED') AND (a.published_at IS NOT NULL OR a.owner_id=? OR ?)";
            case "scheduled" -> "a.status='SCHEDULED'";
            default -> "a.status='ACTIVE'";
        };
        List<Object> p=new ArrayList<>();
        if(view.equals("mine")||view.equals("history")) p.add(user.id()); if(view.equals("history")) p.add(user.admin());
        p.add("%"+query.toLowerCase(Locale.ROOT)+"%"); p.add(category); p.add(category); p.add(limit); p.add(offset);
        return Sql.list(c,SELECT+"WHERE "+condition+" AND lower(a.title) LIKE ? AND (?='' OR a.category=?) ORDER BY a.created_at DESC,a.id LIMIT ? OFFSET ?",this::map,p.toArray());
    }
    public List<Bid> bids(Connection c,UUID id,long after,int limit) throws SQLException {
        return Sql.list(c,"SELECT b.*,u.display_name FROM bid b JOIN app_user u ON u.id=b.bidder_id WHERE auction_id=? AND sequence_no>? ORDER BY sequence_no LIMIT ?",r -> new Bid(r.getObject("id",UUID.class),r.getLong("sequence_no"),r.getLong("bidder_id"),r.getString("display_name"),r.getLong("amount"),Sql.time(r,"accepted_at")),id,after,limit);
    }
    public void event(Connection c,Auction a,String type,Long actor,Instant now,Object details) throws SQLException {
        Sql.update(c,"INSERT INTO auction_event VALUES (?,?,?,?,?,?,?::jsonb)",UUID.randomUUID(),a.auctionId(),a.version(),type,actor,now,new String(Json.bytes(details),java.nio.charset.StandardCharsets.UTF_8));
    }
    private Auction map(ResultSet r) throws SQLException {
        return new Auction(r.getObject("id",UUID.class),r.getLong("owner_id"),r.getString("owner_name"),r.getString("title"),r.getString("description"),r.getString("category"),Status.valueOf(r.getString("status")),r.getLong("starting_price"),r.getLong("min_increment"),Sql.time(r,"start_at"),Sql.time(r,"initial_end_at"),Sql.time(r,"end_at"),r.getLong("duration_seconds"),(Long)r.getObject("current_price"),(Long)r.getObject("leader_id"),r.getString("leader_name"),r.getLong("bid_count"),r.getLong("version"),Sql.time(r,"published_at"),Sql.time(r,"finished_at"),r.getString("cancel_reason"),r.getLong("auction_number"),r.getLong("photo_version"));
    }
    public static void visible(Auction a,User user) {
        BusinessException.require(a.publishedAt()!=null||a.ownerId().equals(user.id())||user.admin(),"NOT_FOUND","Nie znaleziono aukcji.");
    }
}
