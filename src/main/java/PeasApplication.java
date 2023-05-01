import com.sun.net.httpserver.HttpServer;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarStyle;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.MappedByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static net.openhft.hashing.LongHashFunction.xx3;

public final class PeasApplication {
	public static final String LOCKFILE_NAME = "peas.lock";

	private final Path peasDirectory;
	private final Path lockfilePath;

	private final boolean daemon;

	private final Map<Long, Path> files = new HashMap<>();
	private final Map<Long, MmapFile> openFiles = new HashMap<>();
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
				try {
					var req = new String(exchange.getRequestBody().readAllBytes());
					var r = req.split("\\|");
					if (r[0].equals("me")) {
						var hash = Long.parseLong(r[1]);
						var task = downloadTasks.get(hash);
						var addr = exchange.getRemoteAddress().getAddress();
						if (!Arrays.asList(task.left().owners()).contains(addr)) {
							downloadPool.execute(() -> task.right().accept(addr, true));
							// System.out.println(AnsiCodes.CUR_SAVE + "\033[<1>A\r" + AnsiCodes.LINE_CLEAR + "Found new tracker for " + hash + ": " + addr + AnsiCodes.CUR_RESTORE + "\n");
						}

						var res = "ok".getBytes(StandardCharsets.UTF_8);
						exchange.sendResponseHeaders(200, res.length);
						exchange.getResponseBody().write(res);
						exchange.getResponseBody().close();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			});
			server.createContext("/get", exchange -> {
				try {
					var args = WeNeedCuteHttpClientAndServer.queryToMap(exchange.getRequestURI().getQuery());
					var hash = Long.parseLong(args.get("h"));
					var bytes = Long.parseLong(args.get("b"));
					var offset = Long.parseLong(args.get("o"));

					if (!this.files.containsKey(hash)) {
						var b = "404".getBytes(StandardCharsets.UTF_8);
						exchange.sendResponseHeaders(404, b.length);
						exchange.getResponseBody().write(b);
						exchange.getResponseBody().close();
						return;
					}

					var mbb = this.openFiles.computeIfAbsent(hash, h -> {
						try {
							return MmapFile.mmap(this.files.get(h), false);
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
					}).buf();

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
		var path = Path.of(file.filename());
		try (var mmap = MmapFile.mmap(path, false)) {
			var hash = xx3().hashBytes(mmap.buf());
			if (hash != file.hash()) {
				System.err.println("ERROR: Expected and found hash do not match");
				System.exit(1);
			}
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
		files.put(file.hash(), path);
	}

	private final Executor downloadPool = Executors.newCachedThreadPool();
	private final Map<Long, Pair<PeasFile, BiConsumer<InetAddress, Boolean>>> downloadTasks = new ConcurrentHashMap<>();

	public void download(PeasFile file) throws Exception {
		var path = Path.of(file.filename());
		if (!Files.exists(path)) {
			Files.createFile(path);
		}

		try (var raf = new RandomAccessFile(path.toFile(), "rw")) {
			raf.setLength(file.size());
			try (var mmap = MmapFile.mmap(raf, true)) {
				doDownload(file, mmap.buf());
			}
		}
	}

	private final Set<ProgressBar> progressBars = Collections.newSetFromMap(new ConcurrentHashMap<>());

	private void doDownload(PeasFile file, MappedByteBuffer buf) {
		var partitionN = new AtomicInteger();

		var latch = new Phaser(1);
		var pb = new ProgressBar(
			file.filename(),
			100,
			Integer.MAX_VALUE,
			true,
			false,
			System.err,
			ProgressBarStyle.ASCII,
			"%",
			1,
			false,
			null,
			ChronoUnit.SECONDS,
			0L,
			Duration.ZERO
		);
		pb.setExtraMessage(AnsiCodes.COLOR_RED + "Downloading..." + AnsiCodes.COLOR_RESET);

		progressBars.add(pb);

		downloadTasks.put(file.hash(), new Pair<>(file, (tracker, reg) -> {
			if (reg) {
				latch.register();
			}
			while (partitionN.getAcquire() < file.partitions().length) {
				try {
					var part = partitionN.getAndIncrement();
					var notFull = part == file.partitions().length - 1;
					var partSize = notFull
						? file.size() - (part * file.partitionSize())
						: file.partitionSize();
					var res = httpClient.send(
						HttpRequest.newBuilder(
							new URI("http://" + tracker.getHostAddress() + ":8686/get"
								+ "?h=" + file.hash()
								+ "&b=" + partSize
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

					buf.put((int) (part * file.partitionSize()), body, 0, body.length);

					pb.stepTo((partitionN.getOpaque() * 100L) / file.partitions().length);
					progressBars.forEach(ProgressBar::refresh);
				} catch (InterruptedException | URISyntaxException | IOException e) {
					throw new RuntimeException(e);
				}
			}
			latch.arriveAndDeregister();
		}));

		latch.bulkRegister(file.owners().length);
		for (var owner : file.owners()) {
			downloadPool.execute(() -> downloadTasks.get(file.hash()).right().accept(owner, false));
		}

		var multicastSender = scheduler.scheduleAtFixedRate(() -> {
			try {
				this.multicast.send(new Multicast.MulticastMessage(Multicast.MulticastMessage.Type.FIND, file.hash()));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}, 0, 1, TimeUnit.SECONDS);

		latch.arriveAndAwaitAdvance();
		pb.stepTo(100);
		pb.setExtraMessage(AnsiCodes.COLOR_GREEN + "Downloaded!" + AnsiCodes.COLOR_RESET);
		progressBars.forEach(ProgressBar::refresh);
		pb.close();
		multicastSender.cancel(true);
		try {
			this.multicast.send(new Multicast.MulticastMessage(Multicast.MulticastMessage.Type.CANCEL, file.hash()));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		upload(file);
	}

	private final Set<Pair<InetAddress, Long>> knownPeers = Collections.newSetFromMap(new ConcurrentHashMap<>());

	public void multicastMessageReceived(Multicast.MulticastMessage msg, InetAddress addr) {
		if (!files.containsKey(msg.hash())) return;
		var p = new Pair<>(addr, msg.hash());
		switch (msg.type()) {
			case FIND -> {
				if (knownPeers.contains(p)) return;

				try {
					var req = HttpRequest.newBuilder(new URI("http://" + addr.getHostAddress() + ":8686/notify"))
						.POST(HttpRequest.BodyPublishers.ofString("me|" + msg.hash()))
						.build();

					var res = httpClient.send(
						req,
						HttpResponse.BodyHandlers.ofString()
					);
					if ("ok".equals(res.body())) {
						knownPeers.add(p);
					}
				} catch (URISyntaxException | IOException | InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
			case CANCEL -> knownPeers.remove(p);
		}
	}
}
