package pl.edu.bidroom.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Wire(int protocolVersion, String kind, UUID requestId, String operation,
                   Boolean ok, Instant serverTime, JsonNode payload, Failure error, String eventType) {
    public record Failure(String code, String message) { }
    public static Wire request(UUID id, String op, Object payload) { return new Wire(2,"REQUEST",id,op,null,null,Json.node(payload),null,null); }
    public static Wire success(UUID id, Instant now, Object payload) { return new Wire(2,"RESPONSE",id,null,true,now,Json.node(payload),null,null); }
    public static Wire failure(UUID id, Instant now, String code, String message) { return new Wire(2,"RESPONSE",id,null,false,now,null,new Failure(code,message),null); }
    public static Wire event(String type, Instant now, Object payload) { return new Wire(2,"EVENT",null,null,null,now,Json.node(payload),null,type); }
    public JsonNode checked() {
        if (!Boolean.TRUE.equals(ok)) throw new BusinessException(error.code(),error.message());
        return payload;
    }
}
