package dev.shadock.shadock.iris;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The only boundary between Shadock and Iris. Nothing outside {@code dev.shadock.shadock.iris}
 * may reference Iris classes.
 *
 * <p>Switching methods must be called on the render thread. {@link #getShaderPacks()} is safe to call
 * from a background thread.
 */
public interface IrisCompat {
	/** True when shader switching works with the installed Iris. */
	boolean isAvailable();

	/** Why switching is unavailable, as a translation key, or empty when available. */
	Optional<String> unavailableReasonKey();

	/** Folder that Iris loads packs from. Created if missing. */
	Path getShaderpacksDirectory();

	/** Lists packs the way Iris does (ZIPs and folders), including validity. Slow: call off-thread. */
	List<IrisPackInfo> getShaderPacks();

	/** File/folder name of the pack Iris currently has loaded, or empty when shaders are off. */
	Optional<String> getActiveShaderPackName();

	/**
	 * Selects and loads a pack. Blocks while Iris compiles.
	 *
	 * @return true only if Iris actually loaded the pack
	 */
	boolean setActiveShaderPack(String packName);

	/** Turns shaders off, which also makes Iris release the active ZIP file. */
	boolean disableShaders();

	/** Reloads the current selection. */
	boolean reloadShaders();

	/** Iris's message for the last failure, if any. */
	Optional<String> getLastError();
}
