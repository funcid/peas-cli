import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.net.NetworkInterface;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import static picocli.CommandLine.Command;

@Command(name = "peas")
final class PeasCommand implements Callable<Integer> {
	@Parameters(index = "0")
	private Path file;

	@Option(names = "--daemon", negatable = true)
	private boolean daemon = false; // true;

	@Option(names = "upload")
	private boolean upload = false;

	@Override
	public Integer call() throws Exception {
		var app = new PeasApplication(
			Path.of(System.getProperty("user.home")).resolve(".peas"),
			this.daemon
		);
		app.init();

		if (this.upload) {
			app.upload(PeasFile.from(this.file));
		} else {
			app.download(PeasFile.from(this.file));
		}
		while (true) {
			Thread.sleep(Long.MAX_VALUE);
		}
	}

	public static void main(String... args) {
		int exitCode = new CommandLine(new PeasCommand()).execute(args);
		System.exit(exitCode);
	}
}
