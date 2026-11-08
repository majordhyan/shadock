package dev.shadock.shadock.iris;

import java.nio.file.Path;

/** A pack as Iris sees it. {@code valid} uses Iris's own validation rules. */
public record IrisPackInfo(String name, Path path, boolean zip, boolean valid) {
}
