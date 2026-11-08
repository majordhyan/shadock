package dev.shadock.shadock.shader;

import dev.shadock.shadock.util.FileNames;

import java.nio.file.Path;

// fileName is the identity everywhere: saved order, iris.properties, duplicate checks.
// Whether a pack is active is never stored, it's asked from Iris when drawing.
public record ShaderPack(String fileName, String displayName, Path path, Type type, Validity validity) {
	public enum Type { ZIP, FOLDER }

	public enum Validity { VALID, INVALID }

	public static ShaderPack of(Path path, Type type, Validity validity) {
		String fileName = path.getFileName().toString();
		return new ShaderPack(fileName, FileNames.displayName(fileName), path, type, validity);
	}

	public boolean canActivate() {
		return validity != Validity.INVALID;
	}
}
