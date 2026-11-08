package dev.shadock.shadock.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class JsonUtils {
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private JsonUtils() {
	}
}
