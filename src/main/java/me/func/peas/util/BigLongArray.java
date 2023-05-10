package me.func.peas.util;

import jdk.internal.misc.Unsafe;
import jdk.internal.ref.Cleaner;
import me.func.peas.Deencapsulation;

import java.lang.ref.Reference;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.function.LongConsumer;

public final class BigLongArray implements RandomAccess, AutoCloseable {
	static { Deencapsulation.init(); }

	private static final Unsafe U = Unsafe.getUnsafe();
	private static final long ELEMENT_SIZE = 64L; // sizeof(int64_t)

	private long address;
	private final long length;
	private final Cleaner cleaner;

	public static BigLongArray withCleaner(long length) {
		return new BigLongArray(length, true);
	}

	public static BigLongArray withoutCleaner(long length) {
		return new BigLongArray(length, false);
	}

	private BigLongArray(long length, boolean withCleaner) {
		this.address = U.allocateMemory(length * ELEMENT_SIZE);
		this.length = length;

		if (withCleaner) {
			this.cleaner = Cleaner.create(this, this::free);
		} else {
			this.cleaner = null;
		}
	}

	public long get(long index) {
		Objects.checkIndex(index, this.length);
		return _unsafeGet(this, this.address, index);
	}

	public void set(long index, long value) {
		Objects.checkIndex(index, this.length);
		_unsafeSet(this, this.address, index, value);
	}

	public void forEach(LongConsumer consumer) {
		var address = this.address;
		var length = this.length;

		for (var index = 0L; index < length; index++) {
			consumer.accept(_unsafeGet(this, address, index));
		}
	}

	public long length() {
		return this.length;
	}

	private static long _unsafeGet(BigLongArray obj, long address, long index) {
		try {
			return U.getLong(addressFor(address, index));
		} finally {
			Reference.reachabilityFence(obj);
		}
	}

	private static void _unsafeSet(BigLongArray obj, long address, long index, long value) {
		U.putLong(addressFor(address, index), value);
		Reference.reachabilityFence(obj); // Unsafe#putLong can't an throw exception -> try-finally is not needed
	}

	private void free() {
		if (this.address == 0L) return;
		var addr = this.address;
		this.address = 0L;
		U.freeMemory(addr);
	}

	private static long addressFor(long base, long index) {
		return base + (index * ELEMENT_SIZE);
	}

	@Override
	public void close() {
		if (this.cleaner != null) {
			this.cleaner.clean();
		} else {
			this.free();
		}
	}
}
