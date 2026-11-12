package pl.edu.bidroom.server.persistence;

import java.sql.*;
import java.time.Instant;
import java.util.*;

public final class Sql {
    private Sql() { }
    @FunctionalInterface public interface Row<T> { T map(ResultSet r) throws SQLException; }
    public static PreparedStatement prepare(Connection c,String query,Object... args) throws SQLException {
        PreparedStatement s = c.prepareStatement(query);
        try {
            for (int i=0;i<args.length;i++) {
                Object v=args[i]; if(v instanceof Instant instant) s.setTimestamp(i+1,Timestamp.from(instant)); else s.setObject(i+1,v);
            }
            return s;
        } catch (SQLException e) { s.close(); throw e; }
    }
    public static int update(Connection c,String query,Object... args) throws SQLException {
        try(PreparedStatement s=prepare(c,query,args)) { return s.executeUpdate(); }
    }
    public static <T> List<T> list(Connection c,String query,Row<T> mapper,Object... args) throws SQLException {
        try(PreparedStatement s=prepare(c,query,args); ResultSet r=s.executeQuery()) {
            List<T> rows=new ArrayList<>(); while(r.next()) rows.add(mapper.map(r)); return rows;
        }
    }
    public static <T> T one(Connection c,String query,Row<T> mapper,Object... args) throws SQLException {
        List<T> rows=list(c,query,mapper,args); return rows.isEmpty()?null:rows.getFirst();
    }
    public static Instant time(ResultSet r,String name) throws SQLException { Timestamp t=r.getTimestamp(name); return t==null?null:t.toInstant(); }
    public static void advisory(Connection c,long key) throws SQLException {
        try(PreparedStatement s=prepare(c,"SELECT pg_advisory_xact_lock(?)",key);ResultSet r=s.executeQuery()) { r.next(); }
    }
}
