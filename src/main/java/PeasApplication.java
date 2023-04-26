import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static net.openhft.hashing.LongHashFunction.xx3;

public final class PeasApplication {
	public static final String LOCKFILE_NAME = "peas.lock";

	private final Path peasDirectory;
	private final Path lockfilePath;

	private final boolean daemon;

	private final Map<Long, Path> files = new HashMap<>();
	private final Map<Long, OpenFile> openFiles = new HashMap<>();
	private final HttpClient httpClient = HttpClient.newBuilder().build();
	private final Multicast multicast = new Multicast(this);

	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

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
					downloadPool.execute(() -> downloadTasks.get(hash).accept(exchange.getRemoteAddress().getAddress(), true));

					System.out.println("Found new tracker for " + hash + ": " + exchange.getRemoteAddress().getAddress());

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
					System.out.println("Sending " + bytes + " bytes of " + hash + " to " + exchange.getRemoteAddress().getAddress() + " from offset " + offset);
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
		multicast.init();
	}

	public void upload(PeasFile file) {
		files.put(file.hash(), Path.of(file.filename()));
	}

	private final Executor downloadPool = Executors.newCachedThreadPool();
	private final Map<Long, BiConsumer<InetAddress, Boolean>> downloadTasks = new ConcurrentHashMap<>();

	public void download(PeasFile file) throws IOException {
		var f = new File(file.filename());
		f.createNewFile();
		var rf = new RandomAccessFile(file.filename(), "rw");
		rf.setLength(file.size());
		var mbb = rf.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, rf.length());

		var partitionN = new AtomicInteger();

		var latch = new Phaser(1);

		downloadTasks.put(file.hash(), (tracker, reg) -> {
			if (reg) {
				latch.register();
			}
			while (partitionN.getAcquire() < file.partitions().length) {
				try {
					var part = partitionN.getAndIncrement();
					var res = httpClient.send(
						HttpRequest.newBuilder(
							new URI("http://" + tracker.getHostAddress() + ":8686/get"
								+ "?h=" + file.hash()
								+ "&b=" + file.partitionSize()
								+ "&o=" + part * file.partitionSize())
						).build(),
						HttpResponse.BodyHandlers.ofByteArray()
					);

					var body = res.body();
					var bodyHash = xx3().hashBytes(body);
					var expectedHash = file.partitions()[part];
					if (bodyHash != expectedHash) {
						System.err.println("ERROR: part " + part + " downloading failed: found hash: " + bodyHash + ", expected: " + expectedHash);
						System.exit(1);
						return;
					}

					mbb.put((int) (part * file.partitionSize()), body, 0, body.length);
				} catch (InterruptedException | URISyntaxException | IOException e) {
					throw new RuntimeException(e);
				}
			}
			latch.arriveAndDeregister();
		});

		latch.bulkRegister(file.owners().length);
		for (var owner : file.owners()) {
			downloadPool.execute(() -> downloadTasks.get(file.hash()).accept(owner, false));
		}

		var multicastSender = scheduler.scheduleAtFixedRate(() -> {
			try {
				this.multicast.send(new Multicast.MulticastMessage(Multicast.MulticastMessage.Type.FIND, file.hash()));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}, 0, 1, TimeUnit.SECONDS);

		latch.arriveAndAwaitAdvance();
		multicastSender.cancel(true);
		try {
			this.multicast.send(new Multicast.MulticastMessage(Multicast.MulticastMessage.Type.CANCEL, file.hash()));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		System.out.println("Downloaded!");
		upload(file);
	}

	private final Set<Pair<InetAddress, Long>> knownMulticastRequesters = Collections.newSetFromMap(new ConcurrentHashMap<>());

	public void multicastMessageReceived(Multicast.MulticastMessage msg, InetAddress addr) {
		if (!files.containsKey(msg.hash())) return;
		var p = new Pair<>(addr, msg.hash());
		switch (msg.type()) {
			case FIND -> {
				if (knownMulticastRequesters.contains(p)) return;

				try {
					var req = HttpRequest.newBuilder(new URI("http://" + addr.getHostAddress() + ":8686/notify"))
						.POST(HttpRequest.BodyPublishers.ofString("me|" + msg.hash()))
						.build();

					var res = httpClient.send(
						req,
						HttpResponse.BodyHandlers.ofString()
					);
					if ("ok".equals(res.body())) {
						knownMulticastRequesters.add(p);
					}
				} catch (URISyntaxException | IOException | InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
			case CANCEL -> knownMulticastRequesters.remove(p);
		}
	}
}
