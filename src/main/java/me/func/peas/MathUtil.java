package me.func.peas;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public final class MathUtil {
	private MathUtil() {}

	public static long divideRoundUp(long num, long divisor) {
		var _num = BigDecimal.valueOf(num);
		var _divisor = BigDecimal.valueOf(divisor);
		var result = _num.divide(_divisor, RoundingMode.HALF_UP);
		return result.longValue();
	}
}
