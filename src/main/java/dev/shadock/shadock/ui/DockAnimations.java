package dev.shadock.shadock.ui;

public final class DockAnimations {
	public static final float OPEN_MS = 120f;
	// time constants, not durations: rows settle in roughly 4x ROW_TAU_MS
	public static final float ROW_TAU_MS = 28f;
	public static final float SCROLL_TAU_MS = 40f;

	private DockAnimations() {
	}

	public static final float CLOSE_MS = 90f;

	public static float easeInCubic(float t) {
		float clamped = Math.max(0f, Math.min(1f, t));
		return clamped * clamped * clamped;
	}

	public static float easeOutCubic(float t) {
		float clamped = Math.max(0f, Math.min(1f, t));
		float inv = 1f - clamped;
		return 1f - inv * inv * inv;
	}

	public static float approach(float current, float target, float dtMs, float tauMs) {
		if (Math.abs(target - current) < 0.05f) {
			return target;
		}
		float k = 1f - (float) Math.exp(-dtMs / tauMs);
		return current + (target - current) * k;
	}
}
