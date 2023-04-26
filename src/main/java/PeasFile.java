import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Arrays;

import static net.openhft.hashing.LongHashFunction.xx3;

public record PeasFile(
	String filename,
	long size,
	long partitionSize,
	long hash, // XXH3
	long[] partitions,
	LocalDateTime createdAt,
	InetAddress[] owners
) {
	private static final Kryo kryo = new Kryo();
	static {
		kryo.register(LocalDateTime.class);
		kryo.register(Inet4Address.class, InetAddressSerializers.Inet4AddresSerializer.INSTANCE);
		kryo.register(Inet6Address.class, InetAddressSerializers.Inet6AddresSerializer.INSTANCE);
		kryo.register(InetAddress[].class);
		kryo.register(long[].class);
		kryo.register(PeasFile.class);
	}

	public static int SIGNATURE = 0xFEACFEAC;
	public static byte[] SIGNATURE_ARRAY = new byte[]{(byte) 0xFE, (byte) 0xAC, (byte) 0xFE, (byte) 0xAC};

	public static byte VERSION_MAJOR = 0;
	public static byte VERSION_MINOR = 0;

	public static PeasFile from(Path path) throws IOException {
		try (var reader = Files.newInputStream(path)) {
			var magicBuf = reader.readNBytes(4);
			if (!Arrays.equals(magicBuf, SIGNATURE_ARRAY)) {
				throw new IllegalArgumentException("Signature mismatch");
			}
			// TODO: Version

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
