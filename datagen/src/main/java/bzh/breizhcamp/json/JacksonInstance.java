package bzh.breizhcamp.json;

import com.fasterxml.jackson.databind.ObjectMapper;

public class JacksonInstance {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static ObjectMapper get() {
        return OBJECT_MAPPER;
    }
}
