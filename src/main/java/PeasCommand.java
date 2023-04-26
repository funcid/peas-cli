import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.net.InetAddress;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;

import static net.openhft.hashing.LongHashFunction.xx3;
import static picocli.CommandLine.Command;

@Command(name = "peas", showEndOfOptionsDelimiterInUsageHelp = true)
final class PeasCommand implements Callable<Integer> {
	@Parameters
	private List<String> files = List.of();

	@Option(names = "--daemon", negatable = true)
	private boolean daemon = false; // true;

	@Option(names = "upload")
	private boolean upload = false;

	@Option(names = "create")
	private boolean create = false;

	@Option(names = "--owner")
	private String owner = ""; // TODO

	@Option(names = "--part-size")
	private long partSize = 4096; // TODO

	@Override
	public Integer call() throws Exception {
		Deencapsulation.init();

		if (files.isEmpty()) {
			return -666;
		}

		if (create) {
			for (String file : files) {
				var path = Path.of(file);
				try (var fc = FileChannel.open(path, StandardOpenOption.READ)) {
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
						file,
						size,
						partSize,
						xx3().hashBytes(mmap),
						partitions,
						LocalDateTime.now(),
						new InetAddress[]{InetAddress.getByName(owner)}
					).save(Path.of(file + ".peas"));
				}
			}
			return 0;
		}

		var app = new PeasApplication(
			Path.of(System.getProperty("user.home")).resolve(".peas"),
			this.daemon
		);
		app.init();

		if (this.upload) {
			for (String file : files) {
				app.upload(PeasFile.from(Path.of(file)));
			}
			while (true) {
				Thread.sleep(Long.MAX_VALUE); // TODO
			}
		} else {
			for (String file : files) {
				new Thread(() -> {
					try {
						app.download(PeasFile.from(Path.of(file)));
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				}).start();
			}
		}

		return 0;
	}

	public static void main(String... args) {
		var cmd = new CommandLine(new PeasCommand());
		int exitCode = cmd.execute(args);
		if (exitCode == -666) {
			cmd.usage(System.out);
		}
	}
}
