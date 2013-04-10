package fr.proline.core.orm.util;

import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonSerializer {

	private static ObjectMapper MAPPER = new ObjectMapper();

	public static ObjectMapper getMapper() {
		return MAPPER;
	}
	
}
