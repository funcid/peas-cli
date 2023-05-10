package me.func.peas.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import me.func.peas.util.BigLongArray;
import org.checkerframework.checker.nullness.qual.NonNull;

public final class BigLongArraySerializer extends Serializer<@NonNull BigLongArray> {
	public static final BigLongArraySerializer INSTANCE = new BigLongArraySerializer();

	public static void addDefaultSerializers(Kryo kryo) {
		kryo.addDefaultSerializer(BigLongArray.class, INSTANCE);
	}

	public void write(Kryo kryo, Output output, BigLongArray array) {
		output.writeVarLong(array.length(), true);

		if (output.getVariableLengthEncoding()) {
			array.forEach(value -> output.writeVarLong(value, false));
		} else {
			array.forEach(value -> output.writeLong(value, false));
		}
	}

	public BigLongArray read(Kryo kryo, Input input, Class type) {
		var length = input.readVarLong(true);

		var array = BigLongArray.withCleaner(length);

		if (input.getVariableLengthEncoding()) {
			for (var index = 0L; index < length; index++) {
				array.set(index, input.readVarLong(false));
			}
		} else {
			for (var index = 0L; index < length; index++) {
				array.set(index, input.readLong(false));
			}
		}

		return array;
	}

	public BigLongArray copy(Kryo kryo, BigLongArray original) {
		var length = original.length();
		var copy = BigLongArray.withCleaner(length);

		for (long index = 0; index < length; index++) {
			copy.set(index, original.get(index));
		}

		return copy;
	}
}
