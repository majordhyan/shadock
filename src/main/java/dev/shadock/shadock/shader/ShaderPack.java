package dev.shadock.shadock.shader;

import dev.shadock.shadock.util.FileNames;

import java.nio.file.Path;

/**
 * One entry in the {@code shaderpacks} folder.
 * Whether it is active is not stored here: it is asked from Iris at render time.
 *
 * @param fileName exact file or folder name, the stable identifier used for ordering and for Iris
 */
public record ShaderPack(String fileName, String displayName, Path path, Type type, Validity validity) {
	public enum Type { ZIP, FOLDER }

	public enum Validity { VALID, INVALID, UNKNOWN }

	public static ShaderPack of(Path path, Type type, Validity validity) {
		String fileName = path.getFileName().toString();
		return new ShaderPack(fileName, FileNames.displayName(fileName), path, type, validity);
	}

	public boolean canActivate() {
		return validity != Validity.INVALID;
	}
}
