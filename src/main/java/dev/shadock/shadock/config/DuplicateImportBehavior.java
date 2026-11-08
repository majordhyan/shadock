package dev.shadock.shadock.config;

/** What to do when an imported ZIP has the same name as an existing pack. */
public enum DuplicateImportBehavior {
	/** Show Replace / Keep Both / Cancel. */
	ASK,
	/** Overwrite, but only after an explicit confirmation. */
	REPLACE,
	/** Save as {@code Name (2).zip}, {@code Name (3).zip}, ... */
	KEEP_BOTH,
	/** Cancel the import without touching any files. */
	CANCEL
}
