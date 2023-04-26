public final class MathUtil {
	private MathUtil() {}

	public static long divideRoundUp(long num, long divisor) {
		return (num + divisor - 1) / divisor;
	}
}
