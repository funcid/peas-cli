package me.func.peas;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import me.func.peas.kryo.InetAddressSerializer;
import me.func.peas.kryo.BigLongArraySerializer;
import me.func.peas.util.BigLongArray;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;

public record PeasFile(
	String filename,
	long size,
	long partitionSize,
	long hash, // XXH3
	BigLongArray partitions, // XXH3[]
	LocalDateTime createdAt,
	InetAddress[] owners
) {
	private static final Kryo kryo = new Kryo();
	static {
		InetAddressSerializer.addDefaultSerializers(kryo);
		BigLongArraySerializer.addDefaultSerializers(kryo);
		kryo.register(Inet4Address.class);
		kryo.register(Inet6Address.class);
		kryo.register(LocalDateTime.class);
		kryo.register(InetAddress[].class);
		kryo.register(BigLongArray.class);
		kryo.register(PeasFile.class);
	}

	public static int SIGNATURE = 0xFEACFEAC;
	public static byte[] SIGNATURE_ARRAY = new byte[]{(byte) 0xFE, (byte) 0xAC, (byte) 0xFE, (byte) 0xAC};

	public static PeasFile from(Path path) throws IOException {
		try (var reader = Files.newInputStream(path)) {
			var magicBuf = reader.readNBytes(4);
			if (!Arrays.equals(magicBuf, SIGNATURE_ARRAY)) {
				throw new IllegalArgumentException("Signature mismatch");
			}

			var input = new Input(reader);
			return kryo.readObject(input, PeasFile.class);
		}
	}

	public void save(Path path) throws IOException {
		try (var writer = Files.newOutputStream(path)) {
			writer.write(SIGNATURE_ARRAY);
			var output = new Output(writer);
			kryo.writeObject(output, this);
			output.flush();
		}
	}
}
