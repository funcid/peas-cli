import sun.misc.Unsafe;

import java.lang.invoke.MethodHandles;

import static java.lang.invoke.MethodType.methodType;

public final class Deencapsulation {
	private Deencapsulation() {}

	public static void init() { // TODO: SubstrateVM: Make nop substitution
		try {
			var theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
			theUnsafeField.setAccessible(true);

			var unsafe = (Unsafe) theUnsafeField.get(null);

			var implLookupField = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
			var IMPL_LOOKUP = (MethodHandles.Lookup) unsafe.getObject(
				unsafe.staticFieldBase(implLookupField),
				unsafe.staticFieldOffset(implLookupField)
			);

			var m = IMPL_LOOKUP.findVirtual(
				Module.class,
				"implAddOpens",
				methodType(void.class, String.class)
			);

			m.invokeExact(Object.class.getModule(), "jdk.internal.misc");
			m.invokeExact(Object.class.getModule(), "sun.nio.ch");
		} catch (Throwable t) {
			throw new IllegalStateException(t);
		}
	}
}
