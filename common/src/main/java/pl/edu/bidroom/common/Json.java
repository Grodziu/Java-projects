package pl.edu.bidroom.common;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class Json {
    public static final ObjectMapper MAPPER = JsonMapper.builder(JsonFactory.builder()
            // A 16 KiB report chunk becomes 21,848 base64 characters inside a bounded frame.
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(20).maxStringLength(32768).maxNumberLength(20).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .addModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).build();
    private Json() { }
    public static JsonNode node(Object value) { return MAPPER.valueToTree(value); }
    public static ObjectNode object() { return MAPPER.createObjectNode(); }
    public static <T> T read(JsonNode node, Class<T> type) {
        try { return MAPPER.treeToValue(node, type); }
        catch (Exception e) { throw new BusinessException("BAD_REQUEST", "Niepoprawne pola polecenia."); }
    }
    public static byte[] bytes(Object value) {
        try { return MAPPER.writeValueAsBytes(value); }
        catch (Exception e) { throw new IllegalArgumentException("Nie można zakodować danych", e); }
    }
}
