package dev.shadock.shadock.util;

import net.minecraft.client.gui.Font;

public final class TextUtils {
	private static final String ELLIPSIS = "…";

	private TextUtils() {
	}

	/** Truncates {@code text} to {@code maxWidth} pixels, adding an ellipsis when cut. */
	public static String ellipsize(Font font, String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}
		int ellipsisWidth = font.width(ELLIPSIS);
		if (maxWidth <= ellipsisWidth) {
			return ELLIPSIS;
		}
		return font.plainSubstrByWidth(text, maxWidth - ellipsisWidth).stripTrailing() + ELLIPSIS;
	}
}
