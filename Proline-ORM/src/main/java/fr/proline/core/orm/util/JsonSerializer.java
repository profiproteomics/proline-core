package fr.proline.core.orm.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;

public final class JsonSerializer {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	/* Private constructor (Utility class) */
	private JsonSerializer() {
	}

	public static ObjectMapper getMapper() {
		MAPPER.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
		MAPPER.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
		return MAPPER;
	}

}
