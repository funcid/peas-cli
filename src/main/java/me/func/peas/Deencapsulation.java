package me.func.peas;

import me.func.peas.svm.DeencapsulationSubstitution;
import sun.misc.Unsafe;

import java.lang.invoke.MethodHandles;

import static java.lang.invoke.MethodType.methodType;

public final class Deencapsulation {
	private static boolean initialized = false;

	private Deencapsulation() {}

	/**
	 * @see DeencapsulationSubstitution SubstrateVM nop substitution of this
	 */
	public synchronized static void init() {
		if (!initialized) {
			initialized = true;
		} else {
			return;
		}

		try {
			var theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
			theUnsafeField.setAccessible(true);

			var unsafe = (Unsafe) theUnsafeField.get(null);

			var implLookupField = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
			var IMPL_LOOKUP = (MethodHandles.Lookup) unsafe.getObject(
				unsafe.staticFieldBase(implLookupField),
				unsafe.staticFieldOffset(implLookupField)
			);

			var implAddOpens = IMPL_LOOKUP.findVirtual(
				Module.class,
				"implAddOpens",
				methodType(void.class, String.class)
			);

			var module = Object.class.getModule();

			implAddOpens.invokeExact(module, "jdk.internal.misc");
			implAddOpens.invokeExact(module, "jdk.internal.ref");
			implAddOpens.invokeExact(module, "sun.nio.ch");
		} catch (Throwable t) {
			throw new IllegalStateException(t);
		}
	}
}
