import java.io.IOException;
import java.nio.file.Path;

public class D {
	public static void main(String[] args) throws IOException {
		System.out.println(PeasFile.from(Path.of("/tmp/sample_file.peas")));
	}
}
