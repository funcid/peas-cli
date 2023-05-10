package me.func.peas.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.ImmutableSerializer;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

public final class InetAddressSerializer extends ImmutableSerializer<InetAddress> {
	public static final InetAddressSerializer IPV4 = new InetAddressSerializer(false);
	public static final InetAddressSerializer IPV6 = new InetAddressSerializer(true);

	private final boolean v6;

	public static void addDefaultSerializers(Kryo kryo) {
		kryo.addDefaultSerializer(Inet4Address.class, IPV4);
		kryo.addDefaultSerializer(Inet6Address.class, IPV6);
	}

	private InetAddressSerializer(boolean v6) {
		this.v6 = v6;
	}

	@Override
	public void write(Kryo kryo, Output output, InetAddress addr) {
		output.writeBytes(addr.getAddress());
	}

	@Override
	public InetAddress read(Kryo kryo, Input input, Class<? extends InetAddress> type) {
		try {
			return InetAddress.getByAddress(input.readBytes(this.v6 ? 16 : 4));
		} catch (UnknownHostException e) {
			throw new IllegalArgumentException(e);
		}
	}
}
