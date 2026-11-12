package pl.edu.bidroom.server.persistence;

import com.zaxxer.hikari.*;
import org.flywaydb.core.Flyway;
import pl.edu.bidroom.server.ServerConfig;
import java.sql.*;

public final class Database implements AutoCloseable {
    @FunctionalInterface public interface Work<T> { T apply(Connection connection) throws Exception; }
    private final HikariDataSource pool;
    public Database(ServerConfig cfg) {
        HikariConfig h = new HikariConfig(); h.setJdbcUrl(cfg.dbUrl()); h.setUsername(cfg.dbUser()); h.setPassword(cfg.dbPassword());
        h.setMaximumPoolSize(cfg.poolSize()); h.setMinimumIdle(0); h.setConnectionTimeout(3000); h.setValidationTimeout(1000);
        h.setConnectionInitSql("SET statement_timeout = '5s'; SET lock_timeout = '3s'; SET TIME ZONE 'UTC'");
        h.addDataSourceProperty("connectTimeout","3"); h.addDataSourceProperty("socketTimeout","8"); h.addDataSourceProperty("tcpKeepAlive","true");
        pool = new HikariDataSource(h);
        try { Flyway.configure().dataSource(pool).cleanDisabled(true).load().migrate(); }
        catch (RuntimeException e) { pool.close(); throw e; }
    }
    public <T> T read(Work<T> work) throws Exception { try (Connection c = pool.getConnection()) { return work.apply(c); } }
    public <T> T transaction(Work<T> work) throws Exception {
        try (Connection c = pool.getConnection()) {
            c.setAutoCommit(false);
            try { T result = work.apply(c); c.commit(); return result; }
            catch (Exception e) { try { c.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); } throw e; }
        }
    }
    public int activeConnections() { return pool.getHikariPoolMXBean().getActiveConnections(); }
    @Override public void close() { pool.close(); }
}
