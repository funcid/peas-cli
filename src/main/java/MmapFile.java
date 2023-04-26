import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public record MmapFile(
	FileChannel fc,
	MappedByteBuffer buf
) implements AutoCloseable {
	private static final StandardOpenOption[] READ_ONLY = new StandardOpenOption[]{StandardOpenOption.READ};
	private static final StandardOpenOption[] READ_WRITE = new StandardOpenOption[]{StandardOpenOption.READ, StandardOpenOption.WRITE};

	public static MmapFile mmap(Path path, boolean writable) throws IOException {
		var fc = FileChannel.open(
			path,
			writable ? READ_WRITE : READ_ONLY
		);

		var buf = fc.map(
			writable ? FileChannel.MapMode.READ_WRITE : FileChannel.MapMode.READ_ONLY,
			0,
			fc.size()
		);
		return new MmapFile(fc, buf);
	}

	public static MmapFile mmap(RandomAccessFile raf, boolean writable) throws IOException {
		var fc = raf.getChannel();

		var buf = fc.map(
			writable ? FileChannel.MapMode.READ_WRITE : FileChannel.MapMode.READ_ONLY,
			0,
			raf.length()
		);
		return new MmapFile(fc, buf);
	}

	@Override
	public void close() throws Exception {
		jdk.internal.misc.Unsafe.getUnsafe().invokeCleaner(this.buf);
		this.fc.close();
	}
}
