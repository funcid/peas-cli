import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

import static picocli.CommandLine.Command;

@Command(name = "peas", showEndOfOptionsDelimiterInUsageHelp = true)
final class PeasCommand implements Callable<Integer> {
	@Parameters
	private List<Path> files = List.of();

	@Option(names = "--daemon", negatable = true)
	private boolean daemon = false; // true;

	@Option(names = "upload")
	private boolean upload = false;

	@Override
	public Integer call() throws Exception {
		if (files.isEmpty()) {
			return -666;
		}

		Deencapsulation.init();

		var app = new PeasApplication(
			Path.of(System.getProperty("user.home")).resolve(".peas"),
			this.daemon
		);
		app.init();

		if (this.upload) {
			for (Path file : files) {
				app.upload(PeasFile.from(file));
			}
			while (true) {
				Thread.sleep(Long.MAX_VALUE); // TODO
			}
		} else {
			for (Path file : files) {
				new Thread(() -> {
					try {
						app.download(PeasFile.from(file));
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
