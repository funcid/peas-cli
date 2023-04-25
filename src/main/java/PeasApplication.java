import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

public final class PeasApplication {
	public static final String LOCKFILE_NAME = "peas.lock";

	private final Path peasDirectory;
	private final Path lockfilePath;

	private final boolean daemon;

	private final Map<Long, Path> files = new HashMap<>();
	private final Map<Long, OpenFile> openFiles = new HashMap<>();
	private final Map<Long, List<InetAddress>> trackers = new HashMap<>();
	private final HttpClient httpClient = HttpClient.newBuilder().build();
	private final Multicast multicast = new Multicast(this);

	public PeasApplication(
		Path peasDirectory,
		boolean daemon
	) {
		this.peasDirectory = peasDirectory;
		this.daemon = daemon;
		if (daemon) {
			throw new UnsupportedOperationException("daemon");
		}

		this.lockfilePath = peasDirectory.resolve(LOCKFILE_NAME);

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				Files.deleteIfExists(this.lockfilePath);
			} catch (IOException e) {
				throw new IllegalStateException("Failed to release(delete) lockfile", e);
			}
		})); // TODO: Переписать
	}

	public void init() {
		try {
			Files.createDirectories(this.peasDirectory);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to create peas directory", e);
		}

		if (!Files.exists(this.lockfilePath)) {
			try {
				Files.createFile(this.lockfilePath);
			} catch (IOException e) {
				throw new IllegalStateException("Failed to acquire(create) lockfile", e);
			}
		}

		try {
			var server = HttpServer.create(new InetSocketAddress(8686), 0);
			server.createContext("/notify", exchange -> {
				var req = new String(exchange.getRequestBody().readAllBytes());
				var r = req.split("\\|");
				if (r[0].equals("me")) {
					var hash = Long.parseLong(r[1]);
					var track = this.trackers.getOrDefault(hash, new ArrayList<>());
					track.add(exchange.getRemoteAddress().getAddress());
					this.trackers.put(hash, track);

					var res = "ok".getBytes(StandardCharsets.UTF_8);
					exchange.sendResponseHeaders(200, res.length);
					exchange.getResponseBody().write(res);
					exchange.getResponseBody().close();
				}
			});
			server.createContext("/get", exchange -> {
				try {
					var args = WeNeedCuteHttpClientAndServer.queryToMap(exchange.getRequestURI().getQuery());
					var hash = Long.parseLong(args.get("h"));
					var bytes = Long.parseLong(args.get("b"));
					var offset = Long.parseLong(args.get("o"));

					var mbb = this.openFiles.computeIfAbsent(hash, h -> {
						try {
							var f = new RandomAccessFile(this.files.get(h).toFile(), "r");
							var bb = f.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, f.length());
							return new OpenFile(f, bb);
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
					}).mbb();

					var part = new byte[(int) bytes];
					mbb.get((int) offset, part);
					exchange.sendResponseHeaders(200, bytes);
					exchange.getResponseBody().write(part);
					exchange.getResponseBody().close();
				} catch (Throwable t) {
					t.printStackTrace();
				}
			});

			server.setExecutor(Executors.newFixedThreadPool(16));
			server.start();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void upload(PeasFile file) {
		files.put(file.hash(), Path.of(file.filename()));
	}

	public void download(PeasFile file) throws IOException {
		this.trackers.put(file.hash(), Arrays.asList(file.owners()));
		this.multicast.send(new Multicast.MulticastMessage(file.hash()));
		var secN = 0;
		var f = new File(file.filename());
		f.createNewFile();
		var rf = new RandomAccessFile(file.filename(), "rw");
		rf.setLength(file.size());
		var mbb = rf.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, rf.length());

		var tr = this.trackers.get(file.hash());

		while (secN < file.partitions().length) {
			var tracker = tr.get(ThreadLocalRandom.current().nextInt(0, tr.size()));

			try {
				var res = httpClient.send(
					HttpRequest.newBuilder(
						new URI("http://" + tracker.getHostAddress() + ":8686/get?h=" + file.hash() + "&b=" + file.paritionSize() + "&o=" + mbb.position())
					).build(),
					HttpResponse.BodyHandlers.ofByteArray()
				);

				mbb.put(res.body());
				secN++;
			} catch (InterruptedException | URISyntaxException e) {
				throw new RuntimeException(e);
			}
		}

		rf.close();
		System.out.println("downloaded");
	}

	public void multicastMessageReceived(Multicast.MulticastMessage msg, InetAddress addr) {
		if (!files.containsKey(msg.hash())) return;

		try {
			var req = HttpRequest.newBuilder(new URI("http://" + addr.getHostAddress() + ":8686/notify"))
				.POST(HttpRequest.BodyPublishers.ofString("me|" + msg.hash()))
				.build();

			httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString());
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}
}
