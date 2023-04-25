import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;

public record OpenFile(
	RandomAccessFile file,
	MappedByteBuffer mbb
) {}
