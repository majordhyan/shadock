package dev.shadock.shadock.iris;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

// Everything that touches Iris internals goes through here, one impl per tested Iris line.
// Switching blocks the render thread while Iris compiles; listing packs is slow and runs on the scanner thread.
public interface IrisCompat {
	boolean isAvailable();

	Optional<String> unavailableReasonKey();

	Path getShaderpacksDirectory();

	List<IrisPackInfo> getShaderPacks();

	Optional<String> getActiveShaderPackName();

	boolean setActiveShaderPack(String packName);

	// also makes Iris let go of the active zip, which Windows needs before it can be overwritten
	boolean disableShaders();

	Optional<String> getLastError();
}
