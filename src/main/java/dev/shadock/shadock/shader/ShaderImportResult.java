package dev.shadock.shadock.shader;

import org.jspecify.annotations.Nullable;

/** Outcome of one import step, delivered back on the main thread. */
public record ShaderImportResult(Status status, String fileName, @Nullable String detail) {
	public enum Status {
		IMPORTED,
		/** The archive does not look like a shader pack. */
		INVALID,
		/** A file with this name already exists; the UI must decide. */
		DUPLICATE,
		/** The chosen file already is the pack in the shaderpacks folder. */
		ALREADY_PRESENT,
		FAILED,
		CANCELLED
	}

	public static ShaderImportResult of(Status status, String fileName) {
		return new ShaderImportResult(status, fileName, null);
	}
}
