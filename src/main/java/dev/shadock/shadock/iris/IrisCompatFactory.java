package dev.shadock.shadock.iris;

import dev.shadock.shadock.Shadock;
import dev.shadock.shadock.iris.impl.Iris_1_11_7_26_3_Compat;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Picks the adapter for the installed Iris version. Unknown versions get a disabled adapter:
 * Shadock never guesses at Iris internals it has not been tested against.
 */
public final class IrisCompatFactory {
	/** Versions this adapter was built and tested against (Iris reports {@code 1.11.7+mc26.3}). */
	private static final String SUPPORTED_PREFIX = "1.11.7+";

	private IrisCompatFactory() {
	}

	public static IrisCompat create() {
		String version = FabricLoader.getInstance().getModContainer("iris")
			.map(c -> c.getMetadata().getVersion().getFriendlyString())
			.orElse("missing");

		if (!version.startsWith(SUPPORTED_PREFIX)) {
			Shadock.LOGGER.warn("Iris {} is not supported (expected {}x); shader switching is disabled", version, SUPPORTED_PREFIX);
			return new Unavailable("shadock.status.iris_unsupported");
		}

		try {
			Iris_1_11_7_26_3_Compat compat = new Iris_1_11_7_26_3_Compat();
			compat.selfTest();
			Shadock.LOGGER.info("Connected to Iris {}", version);
			return compat;
		} catch (LinkageError | RuntimeException e) {
			Shadock.LOGGER.error("Could not connect to Iris {}", version, e);
			return new Unavailable("shadock.status.iris_connect_failed");
		}
	}

	/** Adapter used when Iris cannot be driven. Every switching call fails safely. */
	private record Unavailable(String reasonKey) implements IrisCompat {
		@Override
		public boolean isAvailable() {
			return false;
		}

		@Override
		public Optional<String> unavailableReasonKey() {
			return Optional.of(reasonKey);
		}

		@Override
		public Path getShaderpacksDirectory() {
			Path dir = FabricLoader.getInstance().getGameDir().resolve("shaderpacks");
			try {
				Files.createDirectories(dir);
			} catch (Exception ignored) {
				// Reported later by the scanner.
			}
			return dir;
		}

		@Override
		public List<IrisPackInfo> getShaderPacks() {
			return List.of();
		}

		@Override
		public Optional<String> getActiveShaderPackName() {
			return Optional.empty();
		}

		@Override
		public boolean setActiveShaderPack(String packName) {
			return false;
		}

		@Override
		public boolean disableShaders() {
			return false;
		}

		@Override
		public Optional<String> getLastError() {
			return Optional.empty();
		}
	}
}
