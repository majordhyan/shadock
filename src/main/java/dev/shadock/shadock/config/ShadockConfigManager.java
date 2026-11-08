package dev.shadock.shadock.config;

import com.google.gson.JsonParseException;
import dev.shadock.shadock.Shadock;
import dev.shadock.shadock.util.JsonUtils;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Loads and saves {@code config/shadock/shadock.json}.
 * Malformed or missing files never crash: defaults are used instead.
 */
public final class ShadockConfigManager {
	private final Path file;
	private ShadockConfig config = new ShadockConfig();
	private String lastSavedJson = "";

	public ShadockConfigManager(Path configDir) {
		this.file = configDir.resolve("shadock").resolve("shadock.json");
	}

	public ShadockConfig get() {
		return config;
	}

	public Path file() {
		return file;
	}

	public void load() {
		ShadockConfig loaded = null;
		boolean needsWrite = false;

		if (Files.isRegularFile(file)) {
			try {
				loaded = JsonUtils.GSON.fromJson(Files.readString(file), ShadockConfig.class);
			} catch (IOException | JsonParseException | IllegalStateException e) {
				Shadock.LOGGER.warn("Could not read {}; using defaults", file, e);
				backupBrokenFile();
			}
		}

		if (loaded == null) {
			loaded = new ShadockConfig();
			needsWrite = true;
		}

		if (loaded.sanitize()) {
			needsWrite = true;
		}

		config = loaded;
		if (needsWrite) {
			save();
		} else {
			lastSavedJson = JsonUtils.GSON.toJson(config);
		}
	}

	/** Writes the config only when its content actually changed since the last save. */
	public void save() {
		String json = JsonUtils.GSON.toJson(config);
		if (json.equals(lastSavedJson) && Files.isRegularFile(file)) {
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(tmp, json);
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
			lastSavedJson = json;
		} catch (IOException e) {
			Shadock.LOGGER.error("Could not save {}", file, e);
		}
	}

	private void backupBrokenFile() {
		try {
			Files.move(file, file.resolveSibling("shadock.json.broken"), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ignored) {
			// Best effort: the defaults overwrite it on the next save anyway.
		}
	}
}
