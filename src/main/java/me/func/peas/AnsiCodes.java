package me.func.peas;

public final class AnsiCodes {
	private AnsiCodes() {}

	public static final String COLOR_BLACK = "\033[30m";
	public static final String COLOR_RED = "\033[31m";
	public static final String COLOR_GREEN = "\033[32m";
	public static final String COLOR_YELLOW = "\033[33m";
	public static final String COLOR_BLUE = "\033[34m";
	public static final String COLOR_PURPLE = "\033[35m";
	public static final String COLOR_CYAN = "\033[36m";
	public static final String COLOR_WHITE = "\033[37m";
	public static final String COLOR_RESET = "\033[0m";

	public static final String LINE_CLEAR = "\033[2K";

	public static final String CUR_SAVE = "\033[s";
	public static final String CUR_RESTORE = "\033[u";
}
