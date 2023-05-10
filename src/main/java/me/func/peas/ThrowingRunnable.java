package me.func.peas;

import static me.func.peas.JavaUtil.sneaky;

@FunctionalInterface
public interface ThrowingRunnable<T extends Exception> extends Runnable {
	void runThrowing() throws T;

	@Override
	default void run() {
		try {
			runThrowing();
		} catch (Exception e) {
			throw sneaky(e);
		}
	}
}
