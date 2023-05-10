package me.func.peas;

public final class JavaUtil {
	private JavaUtil() {}

	@SuppressWarnings("unchecked")
	public static <T extends Throwable> RuntimeException sneaky(Throwable t) throws T {
		throw (T) t;
	}
}
