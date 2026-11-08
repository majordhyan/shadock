package dev.shadock.shadock.ui;

/** Small, restrained motion helpers. No overshoot or bounce. */
public final class DockAnimations {
	/** Dock slide/fade-in duration. */
	public static final float OPEN_MS = 120f;
	/** Time constant for row movement; rows settle in roughly 100 ms. */
	public static final float ROW_TAU_MS = 28f;
	/** Time constant for smooth scrolling. */
	public static final float SCROLL_TAU_MS = 40f;
	/** Horizontal distance the dock slides in from. */
	public static final float SLIDE_PX = 14f;

	private DockAnimations() {
	}

	/** Dock slide-out duration; short so closing still feels immediate. */
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

	/** Frame-rate independent exponential approach towards {@code target}. */
	public static float approach(float current, float target, float dtMs, float tauMs) {
		if (Math.abs(target - current) < 0.05f) {
			return target;
		}
		float k = 1f - (float) Math.exp(-dtMs / tauMs);
		return current + (target - current) * k;
	}
}
