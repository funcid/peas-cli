import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.ImmutableSerializer;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

public final class InetAddressSerializers {
	private InetAddressSerializers() {}

	public static class Inet4AddresSerializer extends ImmutableSerializer<Inet4Address> {
		public static final Inet4AddresSerializer INSTANCE = new Inet4AddresSerializer();

		@Override
		public void write(Kryo kryo, Output output, Inet4Address addr) {
			output.writeBytes(addr.getAddress());
		}

		@Override
		public Inet4Address read(Kryo kryo, Input input, Class<? extends Inet4Address> type) {
			try {
				return (Inet4Address) InetAddress.getByAddress(input.readBytes(4));
			} catch (UnknownHostException e) {
				throw new IllegalArgumentException(e);
			}
		}
	}

	public static class Inet6AddresSerializer extends ImmutableSerializer<Inet6Address> {
		public static final Inet6AddresSerializer INSTANCE = new Inet6AddresSerializer();

		@Override
		public void write(Kryo kryo, Output output, Inet6Address addr) {
			output.writeBytes(addr.getAddress());
		}

		@Override
		public Inet6Address read(Kryo kryo, Input input, Class<? extends Inet6Address> type) {
			try {
				return (Inet6Address) InetAddress.getByAddress(input.readBytes(16));
			} catch (UnknownHostException e) {
				throw new IllegalArgumentException(e);
			}
		}
	}
}
