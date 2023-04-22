import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public final class PeasApplication {
	private final Path peasDirectory;
	private final boolean daemon;

	private final Map<Long, Path> files = new HashMap<>();
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

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				Files.deleteIfExists(this.peasDirectory.resolve("peas.lock"));
			} catch (IOException e) {
				throw new IllegalStateException("Failed to delete lockfile", e);
			}
		})); // TODO: Переписать
	}

	public void init() {
		var lockfile = peasDirectory.resolve("peas.lock");

		if (!Files.exists(lockfile)) {
			try {
				Files.createFile(lockfile);
			} catch (IOException e) {
				throw new IllegalStateException("Failed to create lockfile", e);
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

					exchange.getResponseBody().write("ok".getBytes(StandardCharsets.UTF_8));
				}
			});
			server.createContext("/part", exchange -> {
				// TODO: How to get ?aa=b&b=a params ?
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
		this.multicast.send(new Multicast.MulticastMessage(file.hash()));
		var secN = 0;
		var rf = new RandomAccessFile(file.filename(), "rw");
		rf.setLength(file.size());
		var mbb = rf.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, rf.length());

		while (true) {
			if (secN > file.partitions().length) break;

			var tr = this.trackers.get(file.hash());
			if (tr.isEmpty()) {
				try {
					TimeUnit.SECONDS.sleep(1);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
				continue;
			}

			var tracker = tr.get(ThreadLocalRandom.current().nextInt(0, tr.size() - 1));

			try {
				var res = httpClient.send(HttpRequest.newBuilder(
					new URI("http://" + tracker.getHostAddress() + ":8686/get?sl=" + file.paritionSize() + "&s=" + secN++)
				).build(), HttpResponse.BodyHandlers.ofByteArray());

				mbb.put(res.body());
			} catch (InterruptedException | URISyntaxException e) {
				throw new RuntimeException(e);
			}
		}

		System.out.println("downloaded");
	}

	public void multicastMessageReceived(Multicast.MulticastMessage msg, InetAddress addr) {
		if (files.containsKey(msg.hash())) {
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
}
