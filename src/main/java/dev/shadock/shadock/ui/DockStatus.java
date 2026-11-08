package dev.shadock.shadock.ui;

import net.minecraft.network.chat.Component;

public record DockStatus(Component text, Kind kind) {
	public enum Kind { INFO, BUSY, SUCCESS, WARNING, ERROR }

	private static final DockStatus IDLE = new DockStatus(Component.translatable("shadock.status.ready"), Kind.INFO);

	public static DockStatus ready() {
		return IDLE;
	}

	public boolean isIdle() {
		return this == IDLE;
	}

	public static DockStatus of(Kind kind, String key, Object... args) {
		return new DockStatus(Component.translatable(key, args), kind);
	}
}
