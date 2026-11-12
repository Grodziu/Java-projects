package pl.edu.bidroom.server.service;

import pl.edu.bidroom.common.*;
import pl.edu.bidroom.common.Models.User;
import pl.edu.bidroom.server.persistence.*;
import java.sql.*;
import java.time.Clock;
import java.util.*;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Durable replay is resolved before business rules, inside the same transaction as effects. */
public final class CommandService {
    public record Outcome(Wire response,boolean replayed) { }
    @FunctionalInterface public interface Action { Object apply() throws Exception; }
    private final Clock clock;private final byte[] key;
    public CommandService(Clock clock,String secret) { this.clock=clock;this.key=secret.getBytes(StandardCharsets.UTF_8); }
    public Outcome execute(Connection c,User user,UUID request,String operation,Object normalized,Action action) throws Exception {
        BusinessException.require(request!=null,"BAD_REQUEST","Brak identyfikatora polecenia.");
        Sql.advisory(c,user.id()^request.getMostSignificantBits()^request.getLeastSignificantBits());
        String digest=digest(operation,normalized);
        var stored=Sql.one(c,"SELECT operation,input_digest,result::text FROM command_result WHERE user_id=? AND request_id=?",r -> new String[]{r.getString(1),r.getString(2),r.getString(3)},user.id(),request);
        if(stored!=null) {
            BusinessException.require(stored[0].equals(operation)&&stored[1].equals(digest),"REQUEST_ID_CONFLICT","Ten identyfikator dotyczy innego polecenia.");
            return new Outcome(Json.MAPPER.readValue(stored[2],Wire.class),true);
        }
        Savepoint save=c.setSavepoint();Wire response;
        try { response=Wire.success(request,clock.instant(),action.apply()); }
        catch(BusinessException e) { c.rollback(save);response=Wire.failure(request,clock.instant(),e.code(),e.getMessage()); }
        Sql.update(c,"INSERT INTO command_result VALUES(?,?,?,?,?::jsonb,?)",user.id(),request,operation,digest,Json.node(response).toString(),clock.instant());
        return new Outcome(response,false);
    }
    public Object result(Connection c,Long user,UUID request) throws Exception {
        String saved=Sql.one(c,"SELECT result::text FROM command_result WHERE user_id=? AND request_id=?",r->r.getString(1),user,request);
        return saved==null?Map.of("status","UNKNOWN"):Map.of("status","FOUND","result",Json.MAPPER.readTree(saved));
    }
    private String digest(String operation,Object normalized) throws Exception {
        Mac hmac=Mac.getInstance("HmacSHA256");hmac.init(new SecretKeySpec(key,"HmacSHA256"));
        hmac.update(operation.getBytes(StandardCharsets.UTF_8));hmac.update((byte)0);
        return HexFormat.of().formatHex(hmac.doFinal(Json.bytes(normalized)));
    }
}
