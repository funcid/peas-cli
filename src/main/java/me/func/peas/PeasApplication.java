package me.func.peas;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import jdk.internal.misc.Unsafe;
import me.func.peas.netty.FrameEncoder;
import me.func.peas.netty.Handler;
import me.func.peas.netty.Server;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarStyle;
import net.openhft.hashing.Access;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static net.openhft.hashing.LongHashFunction.xx3;

public final class PeasApplication {
  static { Deencapsulation.init(); }

  private static final Unsafe U = Unsafe.getUnsafe();

  public static final String LOCKFILE_NAME = "peas.lock";

  private final Path peasDirectory;
  private final Path lockfilePath;

  private final Map<Long, Path> files = new HashMap<>();
  private final Map<Long, ByteBuf> openFiles = new HashMap<>();
  private final Multicast multicast = new Multicast(this);
  private final Server server = new Server(this);

  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

  public PeasApplication(Path peasDirectory) {
    this.peasDirectory = peasDirectory;
    this.lockfilePath = peasDirectory.resolve(LOCKFILE_NAME);

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        Files.deleteIfExists(this.lockfilePath);
      } catch (IOException e) {
        throw new IllegalStateException("Failed to release(delete) lockfile", e);
      }
    })); // TODO: Переписать
  }

  public void init() throws IOException {
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

    server.bind(new InetSocketAddress(14514));

    multicast.init();
  }

  public void handleRequestPart(Channel channel, ByteBuf buf) {
    var hash = buf.readLongLE();
    var partSize = buf.readLongLE();
    var part = buf.readLongLE();

    var offset = part * partSize;

    if (!this.files.containsKey(hash)) {
      channel.close();
      return;
    }

    var mbb = this.openFiles.computeIfAbsent(hash, h -> {
      try {
        var file = this.files.get(h);
        try (var fc = file.getFileSystem().provider().newFileChannel(file, Set.of())) {
          var size = fc.size();
          var mmap = fc.map(FileChannel.MapMode.READ_ONLY, 0, size);
          var asNetty = Unpooled.wrappedBuffer(mmap);
          asNetty.setIndex(0, (int) size);
          return asNetty;
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });


    var alloc = channel.alloc();
    var meta = alloc.buffer();
    meta.setIndex(FrameEncoder.VARINT21_SIZE_MAX, FrameEncoder.VARINT21_SIZE_MAX);
    meta.writeByte(Handler.TYPE_RECEIVE_PART);
    meta.writeLongLE(hash);
    meta.writeLongLE(part);

    meta.readerIndex(0);
    var out = alloc.compositeBuffer(2);
    out.addComponent(true, meta);
    out.addComponent(true, mbb.retainedSlice((int) offset, (int) partSize)); // TODO: CRITICAL: REWRITE

    out.readerIndex(FrameEncoder.VARINT21_SIZE_MAX);

    channel.writeAndFlush(out);
  }

  private final ConcurrentMap<Long, ConcurrentMap<Long, CompletableFuture<ByteBuf>>> downloadQueue = new ConcurrentHashMap<>();

  public void handleReceivePart(Channel channel, ByteBuf buf) {
    var hash = buf.readLongLE();
    var part = buf.readLongLE();

    var queue = this.downloadQueue.get(hash);
    CompletableFuture<ByteBuf> partFuture;
    if (queue == null || (partFuture = queue.get(part)) == null) {
      channel.close();
    } else {
      partFuture.complete(buf.retain());
    }
  }

  private final ConcurrentHashMap<InetAddress, Channel> channels = new ConcurrentHashMap<>();

  private Channel getChannel(InetAddress address) {
    return channels.computeIfAbsent(address, addr -> {
      var channel = this.server.connect(new InetSocketAddress(addr, 14514));
      channel.closeFuture().addListener(future -> this.channels.remove(addr));
      return channel;
    });
  }

  public void handleNotifyMe(Channel channel, ByteBuf buf) {
    var hash = buf.readLongLE();
    var task = downloadTasks.get(hash);
    var addr = ((InetSocketAddress) channel.remoteAddress()).getAddress();
    if (!Arrays.asList(task.left().owners()).contains(addr)) {
      downloadPool.execute(() -> task.right().accept(getChannel(addr), true));
    }
  }

  private CompletableFuture<ByteBuf> requestPart(Channel tracker, long hash, long part, long partSize) {
    var queue =
      downloadQueue.computeIfAbsent(hash, k -> new ConcurrentHashMap<>());
    var future = new CompletableFuture<ByteBuf>();
    queue.put(part, future);
    var out = tracker.alloc().buffer();
    out.setIndex(FrameEncoder.VARINT21_SIZE_MAX, FrameEncoder.VARINT21_SIZE_MAX);

    out.writeByte(Handler.TYPE_REQUEST_PART);

    out.writeLongLE(hash);
    out.writeLongLE(partSize);
    out.writeLongLE(part);

    tracker.writeAndFlush(out);
    return future;
  }

  public void upload(PeasFile file) {
    var path = Path.of(file.filename());
    try (var mmap = MmapFile.mmap(path, false)) {
      var buf = mmap.buf();
      var hash = xx3().hash(null, Access.unsafe(), buf.memoryAddress() + buf.readerIndex(), buf.readableBytes());
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
  private final Map<Long, Pair<PeasFile, BiConsumer<Channel, Boolean>>> downloadTasks = new ConcurrentHashMap<>();

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

  private final ConcurrentMap<Long, Phaser> withoutTrackers = new ConcurrentHashMap<>();

  private void doDownload(PeasFile file, ByteBuf buf) {
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
      while (partitionN.getAcquire() < file.partitions().length()) {
        var part = partitionN.getAndIncrement();
        var notFull = part == file.partitions().length() - 1;
        var partSize = notFull
          ? file.size() - (part * file.partitionSize())
          : file.partitionSize();

        var res = requestPart(tracker, file.hash(), part, partSize).join(); // TODO

        long resHash;

        if (res.hasMemoryAddress()) {
          resHash = xx3().hash(null, Access.unsafe(), res.memoryAddress() + res.readerIndex(), partSize);
        } else {
          resHash = xx3().hashBytes(res.array(), res.readerIndex(), (int) partSize);
        }

        var expectedHash = file.partitions().get(part);
        if (resHash != expectedHash) {
          System.err.println("ERROR: part " + part + " downloading failed: found hash: " + resHash + ", expected: " + expectedHash);
          System.exit(1);
          return;
        }

        // дети, не играйтесь с ансейфом
        U.copyMemory(res.memoryAddress() + res.readerIndex(), buf.memoryAddress() + (part * file.partitionSize()), partSize);

        res.release();

        pb.stepTo((partitionN.getOpaque() * 100L) / file.partitions().length());
        progressBars.forEach(ProgressBar::refresh);
      }
      latch.arriveAndDeregister();
      var phaser = withoutTrackers.get(file.hash());
      if (phaser != null) {
        phaser.arrive();
        withoutTrackers.remove(file.hash());
      }
    }));

    var ownersCount = file.owners().length;

    if (ownersCount == 0) {
      latch.register();
      withoutTrackers.put(file.hash(), latch);
    } else {
      latch.bulkRegister(ownersCount);
    }

    for (var owner : file.owners()) {
      downloadPool.execute(() -> downloadTasks.get(file.hash()).right().accept(getChannel(owner), false));
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

        var channel = getChannel(addr);
        var out = channel.alloc().buffer();
        out.setIndex(FrameEncoder.VARINT21_SIZE_MAX, FrameEncoder.VARINT21_SIZE_MAX);
        out.writeByte(Handler.TYPE_NOTIFY_ME);
        out.writeLongLE(msg.hash());
        channel.writeAndFlush(out);
        knownPeers.add(p);
      }
      case CANCEL -> knownPeers.remove(p);
    }
  }
}
