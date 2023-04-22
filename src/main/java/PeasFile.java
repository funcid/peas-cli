import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import net.openhft.hashing.LongHashFunction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;

import static net.openhft.hashing.LongHashFunction.xx3;

public record PeasFile(
	String filename,
	long size,
	long paritionSize,
	long hash, // XXH3
	Partition[] partitions,
	LocalDateTime createdAt,
	int[] owners // IP // TODO: Use InetSocketAddress
) {
	private static final Kryo kryo = new Kryo();
	static {
		kryo.register(int[].class);
		kryo.register(LocalDateTime.class);
		kryo.register(Partition[].class);
		kryo.register(Partition.class);
		kryo.register(PeasFile.class);
	}

	public static int SIGNATURE = 0xFEACFEAC;
	public static byte[] SIGNATURE_ARRAY = new byte[]{(byte) 0xfe, (byte) 0xac, (byte) 0xfe, (byte) 0xac};

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
		var path = Path.of("/home/u/IdeaProjects/peas-cli/samplefiles/sample_file");
		var bytes = Files.readAllBytes(path);

		var partitions = new Partition[bytes.length / 4096];

		var n = 0;
		var partitionN = 0;

		do {
			var currN = n;
			var hash = xx3().hashBytes(bytes, n, Math.min(n += 4096, bytes.length - currN));
			partitions[partitionN] = new Partition(
				partitionN++,
				hash
			);
		} while (n < bytes.length);

		new PeasFile(
			"sample_file",
			bytes.length,
			4096, // 4K
			xx3().hashBytes(bytes),
			partitions,
			LocalDateTime.now(),
			new int[0]
		).save(Path.of("/tmp/sample_file.peas"));
	}

	public record Partition(
		long ordinal,
		long hash // XXH3
	) {}
}
