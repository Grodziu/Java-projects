package pl.edu.bidroom.server.service;

import pl.edu.bidroom.common.*;
import pl.edu.bidroom.common.Models.*;
import pl.edu.bidroom.server.persistence.*;
import java.sql.*;
import java.time.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import static pl.edu.bidroom.common.BusinessException.require;

public final class AuthService {
    public static final long ADMIN_LOCK=710001L;
    private final Database db;
    private final Clock clock;
    private final PasswordHasher passwords=new PasswordHasher();
    private final PasswordHasher.Hash dummy=passwords.create(UUID.randomUUID().toString());
    private final ConcurrentHashMap<String,Session> sessions=new ConcurrentHashMap<>();
    private final SecureRandom random=new SecureRandom();
    private Consumer<Long> revoker=id -> { };
    private record Session(Long user,Instant created,Instant touched) { }
    public record Login(String token,User user,Instant expiresAt) { }
    public AuthService(Database db,Clock clock) { this.db=db;this.clock=clock; }
    public void onRevoke(Consumer<Long> revoker) { this.revoker=revoker; }
    public User register(Registration input) throws Exception {
        String login=login(input.login());String display=text(input.displayName(),2,50,"Nazwa wyświetlana");
        PasswordHasher.Hash hash=passwords.create(input.password());
        return db.transaction(c -> insert(c,login,display,hash,"USER",null));
    }
    public void bootstrap(String login,String password) throws Exception {
        if(login==null||password==null) return;
        String normalized=login(login);
        if(db.read(c -> Sql.one(c,"SELECT id FROM app_user WHERE role='ADMIN' AND enabled",r->r.getLong(1)))!=null) return;
        PasswordHasher.Hash hash=passwords.create(password);
        db.transaction(c -> {
            Sql.advisory(c,ADMIN_LOCK);
            if(Sql.one(c,"SELECT id FROM app_user WHERE role='ADMIN' AND enabled",r->r.getLong(1))==null) insert(c,normalized,"Administrator",hash,"ADMIN",null);
            return null;
        });
    }
    public Login login(Credentials input) throws Exception {
        String login=input.login()==null?"":input.login().strip().toLowerCase(Locale.ROOT);
        record Stored(User user,PasswordHasher.Hash hash) { }
        Stored saved=db.read(c -> Sql.one(c,"SELECT * FROM app_user WHERE login_normalized=?",r -> new Stored(map(r),new PasswordHasher.Hash(r.getBytes("password_hash"),r.getBytes("password_salt"),r.getInt("password_iterations"))),login));
        boolean matches=passwords.matches(input.password(),saved==null?dummy:saved.hash());
        require(saved!=null&&matches&&saved.user().enabled(),"INVALID_CREDENTIALS","Nieprawidłowy login lub hasło.");
        sweep(); require(sessions.size()<1024,"CAPACITY_REACHED","Limit sesji. Spróbuj później.");
        byte[] bytes=new byte[32];random.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now=clock.instant();sessions.put(token,new Session(saved.user().id(),now,now));
        return new Login(token,saved.user(),now.plus(Duration.ofMinutes(30)));
    }
    public User authenticate(String token) throws Exception {
        require(token!=null,"AUTH_REQUIRED","Zaloguj się, aby kontynuować.");
        Session session=sessions.get(token);Instant now=clock.instant();
        require(session!=null&&!expired(session,now),"SESSION_EXPIRED","Sesja wygasła. Zaloguj się ponownie.");
        User user=db.read(c -> active(c,session.user(),false));
        sessions.replace(token,session,new Session(session.user(),session.created(),now));return user;
    }
    public void logout(String token) { if(token!=null)sessions.remove(token); }
    public void revoke(Long id) { sessions.entrySet().removeIf(e->e.getValue().user().equals(id));revoker.accept(id); }
    public void sweep() { Instant now=clock.instant();sessions.entrySet().removeIf(e->expired(e.getValue(),now)); }
    private boolean expired(Session s,Instant now) { return !now.isBefore(s.created().plus(Duration.ofHours(8)))||!now.isBefore(s.touched().plus(Duration.ofMinutes(30))); }
    public User active(Connection c,Long id,boolean lock) throws SQLException {
        User u=Sql.one(c,"SELECT * FROM app_user WHERE id=?"+(lock?" FOR SHARE":""),AuthService::map,id);
        require(u!=null&&u.enabled(),"ACCOUNT_DISABLED","Konto zostało zablokowane.");return u;
    }
    public User adminCreate(Connection c,User actor,NewUser input,PasswordHasher.Hash hash) throws SQLException {
        require(actor.admin(),"FORBIDDEN","Wymagane uprawnienia administratora.");
        require(Set.of("USER","ADMIN").contains(input.role()),"VALIDATION_ERROR","Nieprawidłowa rola.");
        return insert(c,login(input.login()),text(input.displayName(),2,50,"Nazwa wyświetlana"),hash,input.role(),actor.id());
    }
    public PasswordHasher.Hash hash(String password) { return passwords.create(password); }
    public User setStatus(Connection c,User actor,UserStatus input) throws SQLException {
        require(actor.admin(),"FORBIDDEN","Wymagane uprawnienia administratora.");
        String reason=text(input.reason(),2,500,"Powód");
        User target=Sql.one(c,"SELECT * FROM app_user WHERE id=? FOR UPDATE",AuthService::map,input.userId());
        require(target!=null,"NOT_FOUND","Nie znaleziono użytkownika.");
        if(target.admin()&&target.enabled()&&!input.enabled()) {
            long count=Sql.one(c,"SELECT count(*) FROM app_user WHERE role='ADMIN' AND enabled",r->r.getLong(1));
            require(count>1,"FORBIDDEN","Nie można zablokować ostatniego administratora.");
        }
        Sql.update(c,"UPDATE app_user SET enabled=?,updated_at=? WHERE id=?",input.enabled(),clock.instant(),input.userId());
        audit(c,actor.id(),target.id(),input.enabled()?"ACCOUNT_ENABLED":"ACCOUNT_DISABLED",Map.of("reason",reason));
        return new User(target.id(),target.login(),target.displayName(),target.role(),input.enabled(),target.legacyId());
    }
    public List<User> users(Connection c,String query,int offset,int limit) throws SQLException {
        return Sql.list(c,"SELECT * FROM app_user WHERE login_normalized LIKE ? ORDER BY login_normalized LIMIT ? OFFSET ?",AuthService::map,"%"+query.toLowerCase(Locale.ROOT)+"%",limit,offset);
    }
    private User insert(Connection c,String login,String display,PasswordHasher.Hash hash,String role,Long actor) throws SQLException {
        UUID legacy=UUID.randomUUID();Instant now=clock.instant();Long id;
        try { id=Sql.one(c,"INSERT INTO app_user(legacy_uuid,login_normalized,display_name,password_hash,password_salt,password_iterations,role,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?) RETURNING id",r->r.getLong(1),legacy,login,display,hash.value(),hash.salt(),hash.iterations(),role,now,now); }
        catch(SQLException e) { if("23505".equals(e.getSQLState())) throw new BusinessException("LOGIN_TAKEN","Ten login jest zajęty.");throw e; }
        audit(c,actor,id,"ACCOUNT_CREATED",Map.of("role",role));return new User(id,login,display,role,true,legacy);
    }
    private void audit(Connection c,Long actor,Long target,String action,Object details) throws SQLException {
        Sql.update(c,"INSERT INTO security_audit VALUES(?,?,?,?,?,?::jsonb)",UUID.randomUUID(),actor,target,action,clock.instant(),Json.node(details).toString());
    }
    private static User map(ResultSet r) throws SQLException { return new User(r.getLong("id"),r.getString("login_normalized"),r.getString("display_name"),r.getString("role"),r.getBoolean("enabled"),r.getObject("legacy_uuid",UUID.class)); }
    public static String login(String value) { require(value!=null&&value.matches("[A-Za-z0-9._-]{3,32}"),"VALIDATION_ERROR","Login: 3–32 litery ASCII, cyfry lub . _ -");return value.toLowerCase(Locale.ROOT); }
    public static String text(String value,int min,int max,String label) { require(value!=null&&value.strip().length()>=min&&value.strip().length()<=max,"VALIDATION_ERROR",label+": od "+min+" do "+max+" znaków.");return value.strip(); }
}
