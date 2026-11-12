package pl.edu.bidroom.server;

import java.net.*;
import java.nio.file.*;
import java.util.Map;
import java.util.HashMap;
import java.io.IOException;
import pl.edu.bidroom.common.Json;

public record ServerConfig(String profile, String host, int port, String dbUrl, String dbUser, String dbPassword,
                           int poolSize, int maxClients, int maxAuctions, boolean tls, String keyStore, String keyPassword,
                           String hmacSecret, Path dataDir, String adminLogin, String adminPassword) {
    public static ServerConfig environment() {
        Map<String,String> e = configuration(System.getProperty("bidroom.config"), System.getenv());
        String local = e.getOrDefault("LOCALAPPDATA",System.getProperty("user.home"));
        return new ServerConfig(e.getOrDefault("BIDROOM_PROFILE","local"), e.getOrDefault("BIDROOM_BIND_HOST","127.0.0.1"),
            integer(e,"BIDROOM_PORT",5001),e.getOrDefault("BIDROOM_DB_URL","jdbc:postgresql://127.0.0.1:55432/bidroom_dev"),
            e.getOrDefault("BIDROOM_DB_USER","bidroom"),e.get("BIDROOM_DB_PASSWORD"), integer(e,"BIDROOM_DB_POOL_SIZE",4),
            integer(e,"BIDROOM_MAX_CLIENTS",32),integer(e,"BIDROOM_MAX_LIVE_AUCTIONS",16),Boolean.parseBoolean(e.getOrDefault("BIDROOM_TLS_REQUIRED","false")),
            e.get("BIDROOM_TLS_KEYSTORE"),e.get("BIDROOM_TLS_KEYSTORE_PASSWORD"),e.get("BIDROOM_COMMAND_HMAC_SECRET"),
            Path.of(e.getOrDefault("BIDROOM_DATA_DIR",Path.of(local,"BidRoom","server").toString())), e.get("BIDROOM_BOOTSTRAP_ADMIN_LOGIN"), e.get("BIDROOM_BOOTSTRAP_ADMIN_PASSWORD"));
    }
    // Opt-in local configuration for IDE Run/Debug; production continues to use environment variables.
    static Map<String,String> configuration(String file, Map<String,String> environment) {
        Map<String,String> result = new HashMap<>();
        if (file != null) {
            Path path = file.equals("local") ? Path.of(System.getProperty("user.home"),"BidRoomData","server-secrets.json") : Path.of(file);
            try {
                var values = Json.MAPPER.readTree(Files.readAllBytes(path));
                if (!values.isObject()) throw new IOException("Invalid configuration structure");
                values.fields().forEachRemaining(entry -> {
                    if (entry.getKey().startsWith("BIDROOM_") && entry.getValue().isTextual()) result.put(entry.getKey(),entry.getValue().asText());
                });
            } catch (IOException e) {
                // Do not print parser exceptions: their excerpts could contain credentials.
                throw new IllegalArgumentException("Nie odczytano konfiguracji serwera. Uruchom 0-Przygotuj-projekt.cmd lub sprawdź bidroom.config.");
            }
        }
        result.putAll(environment);
        return result;
    }
    public void validate() throws UnknownHostException {
        if (!profile.equals("local") && !profile.equals("production")) throw new IllegalArgumentException("Nieznany profil");
        if ((!profile.equals("local") || !InetAddress.getByName(host).isLoopbackAddress()) && !tls) throw new IllegalArgumentException("Wymagany TLS");
        if (tls && (keyStore == null || keyPassword == null)) throw new IllegalArgumentException("Brak konfiguracji TLS");
        if (dbPassword == null || dbPassword.isBlank() || hmacSecret == null || hmacSecret.length()<32) throw new IllegalArgumentException("Brak sekretów serwera");
        if (port<0 || port>65535 || poolSize<2 || poolSize>32 || maxClients<1 || maxClients>10000 || maxAuctions<1 || maxAuctions>10000) throw new IllegalArgumentException("Nieprawidłowe limity");
    }
    private static int integer(Map<String,String> e,String name,int fallback) { return Integer.parseInt(e.getOrDefault(name,""+fallback)); }
}

