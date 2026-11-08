package dev.shadock.shadock.iris.impl;

import dev.shadock.shadock.Shadock;
import dev.shadock.shadock.iris.IrisCompat;
import dev.shadock.shadock.iris.IrisErrorCapture;
import dev.shadock.shadock.iris.IrisPackInfo;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.config.IrisConfig;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Same steps as Iris's own ShaderPackScreen.applyChanges: set name, enable, save iris.properties, reload.
// The save has to come first, reload() re-reads the properties file from disk.
public final class Iris_1_11_7_26_3_Compat implements IrisCompat {
	private static final int MAX_ERROR_LENGTH = 160;

	private @Nullable String lastError;

	// touch everything we use so a changed Iris fails at startup instead of on the first click
	public void selfTest() {
		Iris.getShaderpacksDirectory();
		Iris.getCurrentPackName();
		Iris.isFallback();
		Iris.getCurrentPack();
	}

	@Override
	public boolean isAvailable() {
		return true;
	}

	@Override
	public Optional<String> unavailableReasonKey() {
		return Optional.empty();
	}

	@Override
	public Path getShaderpacksDirectory() {
		Path dir = Iris.getShaderpacksDirectory();
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			Shadock.LOGGER.warn("Could not create {}", dir, e);
		}
		return dir;
	}

	@Override
	public List<IrisPackInfo> getShaderPacks() {
		Path dir = getShaderpacksDirectory();
		List<IrisPackInfo> packs = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
			// TODO: this reopens every zip on every rescan to validate it. fine for 20 packs, noticeably slow
			//  with a few hundred. cache validity by size + mtime.
			for (Path path : stream) {
				if (!Iris.isValidToShowPack(path)) {
					continue;
				}
				boolean zip = !Files.isDirectory(path);
				boolean valid;
				try {
					valid = Iris.isValidShaderpack(path);
				} catch (RuntimeException e) {
					valid = false;
				}
				packs.add(new IrisPackInfo(path.getFileName().toString(), path, zip, valid));
			}
		} catch (IOException e) {
			throw new IllegalStateException("Could not list " + dir, e);
		}
		return packs;
	}

	@Override
	public Optional<String> getActiveShaderPackName() {
		if (Iris.getCurrentPack().isEmpty() || Iris.isFallback()) {
			return Optional.empty();
		}
		return Optional.ofNullable(Iris.getCurrentPackName());
	}

	@Override
	public boolean setActiveShaderPack(String packName) {
		IrisConfig config = Iris.getIrisConfig();
		if (config == null) {
			lastError = "Iris is not ready yet.";
			return false;
		}

		lastError = null;
		IrisErrorCapture.clear();

		// Same rule as Iris's screen: pending option edits belong to the previous pack.
		if (!packName.equals(Iris.getCurrentPackName())) {
			Iris.clearShaderPackOptionQueue();
		}

		try {
			config.setShaderPackName(packName);
			config.setShadersEnabled(true);
			// Iris.reload() re-reads iris.properties, so the selection must be saved first.
			config.save();
			Iris.reload();
		} catch (IOException | RuntimeException e) {
			Shadock.LOGGER.error("Iris failed to switch to {}", packName, e);
			lastError = describe(e);
			return false;
		}

		// TODO: on a compile failure in-world Iris also posts its own chat message, so the player sees the
		//  error twice. no clean way to suppress theirs without a second mixin.
		Exception captured = IrisErrorCapture.take();
		boolean loaded = Iris.getCurrentPack().isPresent()
			&& packName.equals(Iris.getCurrentPackName())
			&& !Iris.isFallback();

		if (!loaded || captured != null) {
			lastError = captured != null ? describe(captured) : null;
			return false;
		}
		return true;
	}

	@Override
	public boolean disableShaders() {
		IrisConfig config = Iris.getIrisConfig();
		if (config == null) {
			return false;
		}
		try {
			config.setShadersEnabled(false);
			config.save();
			Iris.reload();
			return true;
		} catch (IOException | RuntimeException e) {
			Shadock.LOGGER.error("Iris failed to disable shaders", e);
			lastError = describe(e);
			return false;
		}
	}

	@Override
	public Optional<String> getLastError() {
		return Optional.ofNullable(lastError);
	}

	private static String describe(Throwable e) {
		String message = e.getMessage();
		if (message == null || message.isBlank()) {
			return e.getClass().getSimpleName();
		}
		String firstLine = message.strip().lines().findFirst().orElse(message).strip();
		return firstLine.length() > MAX_ERROR_LENGTH ? firstLine.substring(0, MAX_ERROR_LENGTH - 1) + "…" : firstLine;
	}
}
