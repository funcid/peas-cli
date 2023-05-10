package me.func.peas;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public record MmapFile(
	FileChannel fc,
	ByteBuf buf,
	ByteBuffer nioBuffer
) implements AutoCloseable {
	private static final StandardOpenOption[] READ_ONLY = new StandardOpenOption[]{StandardOpenOption.READ};
	private static final StandardOpenOption[] READ_WRITE = new StandardOpenOption[]{StandardOpenOption.READ, StandardOpenOption.WRITE};

	public static MmapFile mmap(Path path, boolean writable) throws IOException {
		var fc = FileChannel.open(
			path,
			writable ? READ_WRITE : READ_ONLY
		);

		var size = fc.size();

		var buf = fc.map(
			writable ? FileChannel.MapMode.READ_WRITE : FileChannel.MapMode.READ_ONLY,
			0,
			size
		);
		var asNetty = Unpooled.wrappedBuffer(buf);
		asNetty.setIndex(0, (int) size);
		return new MmapFile(fc, asNetty, buf);
	}

	public static MmapFile mmap(RandomAccessFile raf, boolean writable) throws IOException {
		var fc = raf.getChannel();

		var buf = fc.map(
			writable ? FileChannel.MapMode.READ_WRITE : FileChannel.MapMode.READ_ONLY,
			0,
			raf.length()
		);
		var asNetty = Unpooled.wrappedBuffer(buf);
		asNetty.setIndex(0, (int) raf.length());
		return new MmapFile(fc, asNetty, buf);
	}

	@Override
	public void close() throws Exception {
		this.buf.release();
		jdk.internal.misc.Unsafe.getUnsafe().invokeCleaner(this.nioBuffer);
		this.fc.close();
	}
}
