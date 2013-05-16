package fr.proline.core.orm.util;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonSerializer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /* Private constructor (Utility class) */
    private JsonSerializer() {
    }

    public static ObjectMapper getMapper() {
	return MAPPER;
    }

}
