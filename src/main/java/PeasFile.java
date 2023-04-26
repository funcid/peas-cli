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

	public static void main(String[] args) throws Throwable {
		Deencapsulation.init();

		var path = Path.of("/home/u/IdeaProjects/peas-cli/samplefiles/unaligned");
		try (var fc = FileChannel.open(path, StandardOpenOption.READ)) {
			var partSize = 4096L; // 4K

			var size = fc.size();
			var mmap = fc.map(FileChannel.MapMode.READ_ONLY, 0, size);

			if (size < partSize) {
				partSize = size;
			}

			var partitions = new long[(int) MathUtil.divideRoundUp(size, partSize)]; // TODO: Big array
			var n = 0;
			var partitionN = 0;

			do {
				var currN = n;
				n += partSize;
				var hash = xx3().hashBytes(
					mmap,
					currN,
					(int) (currN + partSize > size
						? size - currN
						: partSize)
				);
				partitions[partitionN++] = hash;
			} while (n < size);

			new PeasFile(
				"unaligned",
				size,
				partSize,
				xx3().hashBytes(mmap),
				partitions,
				LocalDateTime.now(),
				new InetAddress[] { InetAddress.getByName("192.168.1.25") }
			).save(Path.of("/tmp/unaligned.peas"));

			jdk.internal.misc.Unsafe.getUnsafe().invokeCleaner(mmap);
		}
	}
}
