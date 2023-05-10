package me.func.peas.cli;

import me.func.peas.Deencapsulation;
import me.func.peas.PeasApplication;
import me.func.peas.PeasFile;
import me.func.peas.ThrowingRunnable;
import me.func.peas.util.BigLongArray;
import net.openhft.hashing.Access;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import sun.nio.ch.DirectBuffer;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Arrays;

import static me.func.peas.MathUtil.divideRoundUp;
import static net.openhft.hashing.LongHashFunction.xx3;

@Command(
  name = "peas",
  mixinStandardHelpOptions = true,
  synopsisSubcommandLabel = "[КОМАНДА]",
  subcommands = {
    PeasCommand.CreateCommand.class
  }
)
public class PeasCommand implements ThrowingRunnable<Exception> {
  @Option(names = {"-h", "--help"}, usageHelp = true, description = "Показать это сообщение")
  private boolean helpRequested;

  @Parameters(description = "Файлы, которые нужно загрузить/выгрузить", paramLabel = "<файлы>")
  private Path[] files;

  @Option(names = {"-u", "--upload"}, description = "Режим выгрузки")
  private boolean upload;

  @Spec
  private CommandSpec spec;

  @Override
  public void runThrowing() throws Exception {
    if (files == null || files.length == 0) {
      spec.commandLine().usage(System.out);
      return;
    }

    var app = new PeasApplication(Path.of(System.getProperty("user.home")).resolve(".peas"));
    app.init();

    for (var file : files) {
      new Thread(() -> {
        try {
          if (upload) {
            app.upload(PeasFile.from(file));
          } else {
            app.download(PeasFile.from(file));
          }
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }).start();
    }
  }

  @Command(
    name = "create",
    mixinStandardHelpOptions = true,
    description = "Создать .peas файл"
  )
  public static class CreateCommand implements ThrowingRunnable<Exception> {
		static { Deencapsulation.init(); }

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Показать это сообщение")
    private boolean helpRequested;

    @Option(names = {"-s", "--part-size"}, description = "Размер блока", paramLabel = "SIZE")
    private long partSize = 16384;

    @Option(names = {"-w", "--owners"}, description = "Владельцы файла (трекеры по умолчанию)", paramLabel = "OWNERS")
    private String[] owners = new String[0];

    @Parameters(index = "0", description = "Файл, для которого должен быть создан .peas файл", paramLabel = "<файл>")
    private Path file;

    @Override
    public void runThrowing() throws Exception {
      try (var fc = FileChannel.open(file, StandardOpenOption.READ)) {
        var size = fc.size();
        if (size > Integer.MAX_VALUE) {
          throw new RuntimeException("file too big");
        }
        var mmap = fc.map(FileChannel.MapMode.READ_ONLY, 0, size);
        var addr = ((DirectBuffer) mmap).address();

        if (size < partSize) {
          partSize = size;
        }

        var partitions = BigLongArray.withCleaner(divideRoundUp(size, partSize));

        var n = 0;
        var partitionN = 0L;

        do {
          var currN = n;
          n += partSize;
          var hash = xx3().hash(
            null,
            Access.unsafe(),
            addr + currN,
            currN + partSize > size
              ? size - currN
              : partSize
          );
          partitions.set(partitionN++, hash);
        } while (n < size);

        var filename = file.getFileName().toString();

        new PeasFile(
          filename,
          size,
          partSize,
          xx3().hash(null, Access.unsafe(), addr, size),
          partitions,
          LocalDateTime.now(),
          Arrays.stream(owners).map(a -> {
            try {
              return InetAddress.getByName(a);
            } catch (UnknownHostException e) {
              throw new RuntimeException(e);
            }
          }).toArray(InetAddress[]::new)
        ).save(Path.of(filename + ".peas"));
      }
    }
  }
}
