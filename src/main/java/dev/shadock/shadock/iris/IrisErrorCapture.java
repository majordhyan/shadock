package dev.shadock.shadock.iris;

import org.jspecify.annotations.Nullable;

/** Holds the last exception Iris reported while loading a pack. Written by {@code IrisErrorCaptureMixin}. */
public final class IrisErrorCapture {
	private static volatile @Nullable Exception last;

	private IrisErrorCapture() {
	}

	public static void record(Exception e) {
		last = e;
	}

	public static void clear() {
		last = null;
	}

	public static @Nullable Exception take() {
		Exception e = last;
		last = null;
		return e;
	}
}
