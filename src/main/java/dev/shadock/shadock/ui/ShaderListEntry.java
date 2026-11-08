package dev.shadock.shadock.ui;

import dev.shadock.shadock.shader.ShaderPack;
import dev.shadock.shadock.util.TextUtils;
import net.minecraft.client.gui.Font;

/** One row in the list, with its animated vertical position (in list-content pixels). */
final class ShaderListEntry {
	final ShaderPack pack;
	float animY;

	private String cachedName = "";
	private int cachedWidth = -1;

	ShaderListEntry(ShaderPack pack, float animY) {
		this.pack = pack;
		this.animY = animY;
	}

	/** The display name truncated to {@code width}, recomputed only when the width changes. */
	String name(Font font, int width) {
		if (width != cachedWidth) {
			cachedWidth = width;
			cachedName = TextUtils.ellipsize(font, pack.displayName(), width);
		}
		return cachedName;
	}

	boolean isTruncated() {
		return !cachedName.equals(pack.displayName());
	}
}
