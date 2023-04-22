import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.io.IOException;
import java.net.*;

public final class Multicast {
	public static final int PORT = Integer.getInteger("peas.multicast.port", 4608);
	public static final InetAddress GROUP;

	static {
		try {
			GROUP = InetAddress.getByName(System.getProperty("peas.multicast.group", "224.0.2.77"));
		} catch (UnknownHostException e) {
			var ex = new ExceptionInInitializerError("Invalid multicast group");
			ex.addSuppressed(e);
			throw ex;
		}
	}

	private final PeasApplication peasApplication;
	private final DatagramSocket socket;

	public Multicast(PeasApplication peasApplication) {
		this.peasApplication = peasApplication;
		try {
			this.socket = new DatagramSocket();
		} catch (SocketException e) {
			throw new RuntimeException(e);
		}
	}

	public void init() {
		var listenerThread = new Thread(() -> {
			try (var socket = new MulticastSocket(PORT)) {
				socket.joinGroup(new InetSocketAddress(GROUP, PORT), NetworkInterface.getByInetAddress(GROUP));

				var buf = new byte[9];
				while (true) {
					var pk = new DatagramPacket(buf, buf.length);
					try {
						socket.receive(pk);
					} catch (IOException ioException) {
						ioException.printStackTrace(); // TODO: Loggers, slf4j-simple ?
						continue;
					}

					var message = MulticastMessage.deserialize(pk);

					peasApplication.multicastMessageReceived(message, pk.getAddress());
				}
			} catch (IOException e) {
				throw new IllegalStateException("Failed to bind MulticastSocket on port " + PORT, e);
			}
		}, "Multicast listener");
	}

	public void send(MulticastMessage message) throws IOException {
		var pk = message.serialize();
		pk.setAddress(GROUP);
		pk.setPort(PORT);
		socket.send(pk);
	}
	
	public record MulticastMessage(
		long hash // XXH3
	) {
		private static final ThreadLocal<Kryo> KRYO_THREAD_LOCAL = ThreadLocal.withInitial(() -> {
			var kryo = new Kryo();
			kryo.register(MulticastMessage.class);
			return kryo;
		});

		public DatagramPacket serialize() {
			var output = new Output(9);
			KRYO_THREAD_LOCAL.get().writeObject(output, this);
			output.flush();
			return new DatagramPacket(output.getBuffer(), output.position());
		}

		public static MulticastMessage deserialize(DatagramPacket packet) {
			var input = new Input(packet.getData(), packet.getOffset(), packet.getLength());
			return KRYO_THREAD_LOCAL.get().readObject(input, MulticastMessage.class);
		}
	}
}
