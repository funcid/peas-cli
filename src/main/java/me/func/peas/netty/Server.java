package me.func.peas.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import me.func.peas.PeasApplication;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public final class Server {
  private final PeasApplication peasApplication;

  private final EventLoopGroup boss;
  private final EventLoopGroup worker;

  private final Bootstrap clientBootstrap;

  private ChannelFuture listeningChannel;

  public Server(PeasApplication peasApplication) {
    this.peasApplication = peasApplication;

    this.boss = new NioEventLoopGroup(1);
    this.worker = new NioEventLoopGroup(4);

    this.clientBootstrap = new Bootstrap()
      .channelFactory(NioSocketChannel::new)
      .option(ChannelOption.TCP_NODELAY, true)
      .option(ChannelOption.SO_KEEPALIVE, true)
      .handler(new ChannelInitializer<>() {
        @Override
        protected void initChannel(Channel ch) {
          ch.pipeline()
            .addLast("frame_decoder", new FrameDecoder())
            .addLast("frame_encoder", FrameEncoder.INSTANCE)
            .addLast("handler", new Handler(Server.this.peasApplication));
        }
      }).group(worker)
      .option(ChannelOption.AUTO_READ, true);
  }

  public void bind(SocketAddress address) {
    var bootstrap = new ServerBootstrap()
      .channelFactory(NioServerSocketChannel::new)
      .childOption(ChannelOption.TCP_NODELAY, true)
      .childOption(ChannelOption.SO_KEEPALIVE, true)
      .childHandler(new ChannelInitializer<>() {
        protected void initChannel(Channel ch) {
          ch.pipeline()
            .addLast("frame_decoder", new FrameDecoder())
            .addLast("frame_encoder", FrameEncoder.INSTANCE)
            .addLast("handler", new Handler(Server.this.peasApplication));
        }
      }).group(boss, worker)
      .localAddress(address)
      .option(ChannelOption.AUTO_READ, true);

    this.listeningChannel = bootstrap.bind().syncUninterruptibly();
  }
  
  public Channel connect(InetSocketAddress addr) {
    return this.clientBootstrap.connect(addr).syncUninterruptibly().channel();
  }
}
