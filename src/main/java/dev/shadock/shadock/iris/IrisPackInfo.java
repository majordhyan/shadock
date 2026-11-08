package dev.shadock.shadock.iris;

import java.nio.file.Path;

public record IrisPackInfo(String name, Path path, boolean zip, boolean valid) {
}
