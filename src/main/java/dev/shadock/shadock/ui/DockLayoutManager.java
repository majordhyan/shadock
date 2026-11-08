package dev.shadock.shadock.ui;

// bottom-left only for now. Header, list, status line, toolbar, top to bottom inside the frame.
public final class DockLayoutManager {
	public static final int MARGIN = 4;
	public static final int PANEL_WIDTH = 168;
	public static final int INSET = 6;
	public static final int HEADER_HEIGHT = 16;
	public static final int ROW_HEIGHT = 12;
	public static final int STATUS_HEIGHT = 12;
	public static final int TOOLBAR_HEIGHT = 16;
	public static final int BUTTON_SIZE = 16;
	public static final int BUTTON_GAP = 2;
	public static final int CLOSE_SIZE = 14;
	public static final int MAX_ROWS = 7;
	public static final int SCROLLBAR_WIDTH = 6;
	public static final int MIN_WIDTH = 136;
	private static final int GAP_TO_HOTBAR = 4;
	private static final int HOTBAR_HALF_WIDTH = 91;

	private DockLayoutManager() {
	}

	public record Rect(int x, int y, int width, int height) {
		public boolean contains(double px, double py) {
			return px >= x && px < x + width && py >= y && py < y + height;
		}

		public int right() {
			return x + width;
		}

		public int bottom() {
			return y + height;
		}
	}

	public record Layout(Rect panel, Rect header, Rect list, Rect status, Rect toolbar, Rect close, int visibleRows) {
		public Rect toolbarButton(int index) {
			return new Rect(toolbar.x() + index * (BUTTON_SIZE + BUTTON_GAP), toolbar.y(), BUTTON_SIZE, BUTTON_SIZE);
		}
	}

	public static Layout compute(int screenWidth, int screenHeight, int rowCount, int minRows) {
		int x = MARGIN;
		// Always anchored in the true bottom-left corner. Where there is room, the dock narrows so it
		// ends before the hotbar; on very small screens it overlaps it (the dock is on top while open).
		int hotbarLeft = screenWidth / 2 - HOTBAR_HALF_WIDTH;
		int roomBeforeHotbar = hotbarLeft - GAP_TO_HOTBAR - x;
		int width = Math.max(MIN_WIDTH, Math.min(PANEL_WIDTH, roomBeforeHotbar));
		width = Math.min(width, screenWidth - MARGIN * 2);
		int bottomOffset = MARGIN;

		int chrome = INSET + HEADER_HEIGHT + 2 + 2 + STATUS_HEIGHT + TOOLBAR_HEIGHT + INSET;
		int available = screenHeight - bottomOffset - MARGIN - chrome;
		int maxRows = Math.max(1, Math.min(MAX_ROWS, available / ROW_HEIGHT));
		int rows = Math.max(Math.min(Math.max(rowCount, minRows), maxRows), Math.min(3, maxRows));

		int listHeight = rows * ROW_HEIGHT;
		int height = chrome + listHeight;
		int y = Math.max(MARGIN, screenHeight - bottomOffset - height);

		int innerX = x + INSET;
		int innerWidth = width - INSET * 2;

		Rect panel = new Rect(x, y, width, height);
		Rect header = new Rect(innerX, y + INSET, innerWidth, HEADER_HEIGHT);
		Rect list = new Rect(innerX, header.bottom() + 2, innerWidth, listHeight);
		Rect status = new Rect(innerX, list.bottom() + 2, innerWidth, STATUS_HEIGHT);
		Rect toolbar = new Rect(innerX, status.bottom(), innerWidth, TOOLBAR_HEIGHT);
		Rect close = new Rect(header.right() - CLOSE_SIZE, header.y() + (HEADER_HEIGHT - CLOSE_SIZE) / 2 - 1, CLOSE_SIZE, CLOSE_SIZE);

		return new Layout(panel, header, list, status, toolbar, close, rows);
	}
}
