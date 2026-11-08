package dev.shadock.shadock.ui;

public final class DockColors {
	public static final int WHITE = 0xFFFFFFFF;
	public static final int GRAY = 0xFFAAAAAA;
	public static final int DARK_GRAY = 0xFF555555;
	public static final int GREEN = 0xFF55FF55;
	public static final int YELLOW = 0xFFFFFF55;
	public static final int RED = 0xFFFF5555;
	public static final int AQUA = 0xFF55FFFF;

	// same values AbstractSelectionList uses
	public static final int SELECTION_OUTLINE = 0xFFFFFFFF;
	public static final int SELECTION_OUTLINE_UNFOCUSED = 0xFF808080;
	public static final int SELECTION_FILL = 0xFF000000;
	public static final int ROW_HOVER = 0x30FFFFFF;
	public static final int DROP_SLOT = 0x40FFFFFF;
	public static final int SHADOW = 0x60000000;

	private DockColors() {
	}

	public static int forStatus(DockStatus.Kind kind) {
		return switch (kind) {
			case INFO -> GRAY;
			case BUSY -> AQUA;
			case SUCCESS -> GREEN;
			case WARNING -> YELLOW;
			case ERROR -> RED;
		};
	}
}
