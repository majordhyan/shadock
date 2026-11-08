package dev.shadock.shadock.iris;

import org.jspecify.annotations.Nullable;

// Lives outside the mixin on purpose: mixin classes get merged into their target and can't be
// referenced from normal code, so the adapter reads the captured exception from here.
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
