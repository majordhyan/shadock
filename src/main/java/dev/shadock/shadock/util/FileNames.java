package dev.shadock.shadock.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Predicate;

public final class FileNames {
	private FileNames() {
	}

	public static boolean isZip(String fileName) {
		return fileName.toLowerCase(Locale.ROOT).endsWith(".zip");
	}

	/** {@code "BSL_v8.zip"} → {@code "BSL_v8"}; folder names are returned unchanged. */
	public static String displayName(String fileName) {
		return isZip(fileName) ? fileName.substring(0, fileName.length() - 4) : fileName;
	}

	/**
	 * First free name in the pattern {@code Name.zip}, {@code Name (2).zip}, {@code Name (3).zip}, ...
	 * Returns {@code fileName} itself when it is not taken.
	 */
	public static String firstFreeName(String fileName, Predicate<String> taken) {
		if (!taken.test(fileName)) {
			return fileName;
		}
		String base;
		String ext;
		if (isZip(fileName)) {
			base = fileName.substring(0, fileName.length() - 4);
			ext = fileName.substring(fileName.length() - 4);
		} else {
			base = fileName;
			ext = "";
		}
		for (int n = 2; n < 10_000; n++) {
			String candidate = base + " (" + n + ")" + ext;
			if (!taken.test(candidate)) {
				return candidate;
			}
		}
		throw new IllegalStateException("No free file name for " + fileName);
	}

	public static String firstFreeName(Path dir, String fileName) {
		return firstFreeName(fileName, name -> Files.exists(dir.resolve(name)));
	}
}
