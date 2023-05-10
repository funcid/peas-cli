package me.func.peas.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

@Sharable
public class FrameEncoder extends ChannelOutboundHandlerAdapter {
  public static final FrameEncoder INSTANCE = new FrameEncoder();

  public static final int VARINT21_SIZE_MAX = 3;

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
    if (msg instanceof ByteBuf buf) {
      var packetSize = buf.readableBytes();
      buf.markWriterIndex();
      buf.setIndex(0, 0);

      write21BitVarInt(buf, packetSize);
      buf.resetWriterIndex();
    }
    ctx.write(msg, promise);
  }

  public static void write21BitVarInt(ByteBuf buf, int value) {
    // See https://steinborn.me/posts/performance/how-fast-can-you-write-a-varint/
    int w = (value & 0x7F | 0x80) << 16 | ((value >>> 7) & 0x7F | 0x80) << 8 | (value >>> 14);
    buf.writeMedium(w);
  }
}
