import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.LockSupport;

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
			LockSupport.park(); // TODO
		} else {
			app.download(PeasFile.from(this.file));
		}

		return 0;
	}

	public static void main(String... args) {
		int exitCode = new CommandLine(new PeasCommand()).execute(args);
		System.exit(exitCode);
	}
}
