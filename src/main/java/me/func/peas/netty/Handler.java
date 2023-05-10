package me.func.peas.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import me.func.peas.PeasApplication;

public final class Handler extends SimpleChannelInboundHandler<ByteBuf> {
  public static final byte TYPE_REQUEST_PART = 0x00;
  public static final byte TYPE_RECEIVE_PART = 0x01;
  public static final byte TYPE_NOTIFY_ME = 0x02;

  private final PeasApplication peasApplication;

  public Handler(PeasApplication peasApplication) {
    this.peasApplication = peasApplication;
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
    var type = msg.readByte();

    switch (type) {
      case TYPE_REQUEST_PART -> this.peasApplication.handleRequestPart(ctx.channel(), msg);
      case TYPE_RECEIVE_PART -> this.peasApplication.handleReceivePart(ctx.channel(), msg);
      case TYPE_NOTIFY_ME -> this.peasApplication.handleNotifyMe(ctx.channel(), msg);
      default -> ctx.channel().close();
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    cause.printStackTrace();
  }
}
