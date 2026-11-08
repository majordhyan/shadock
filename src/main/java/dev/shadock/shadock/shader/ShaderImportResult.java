package dev.shadock.shadock.shader;

import org.jspecify.annotations.Nullable;

public record ShaderImportResult(Status status, String fileName, @Nullable String detail) {
	public enum Status {
		IMPORTED,
		INVALID,
		DUPLICATE,
		ALREADY_PRESENT,
		FAILED
	}

	public static ShaderImportResult of(Status status, String fileName) {
		return new ShaderImportResult(status, fileName, null);
	}
}
