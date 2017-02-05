package com.nyshup;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import javax.net.ssl.SSLException;
import java.util.Optional;

public class ChildProxyHandler extends ChannelInboundHandlerAdapter {

    final private String remoteHost;
    final private int remotePort;
    private final boolean ssl;
    private Channel outboundChannel;

    public ChildProxyHandler(String remoteHost, int remotePort, boolean ssl) {
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.ssl = ssl;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().read();
    }

    private Optional<SslContext> getSslContext() throws SSLException {
        Optional<SslContext> sslCtx = Optional.empty();
        if (this.ssl) {
            sslCtx = Optional.of(SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE).build());
        }
        return sslCtx;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            Optional<SslContext> sslCtx = getSslContext();
            Bootstrap bootstrap = new Bootstrap();
            final Channel inboundChannel = ctx.channel();
            bootstrap.group(inboundChannel.eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.AUTO_READ, false)
                    .handler(new ChannelInitializer<NioSocketChannel>() {
                        @Override
                        protected void initChannel(NioSocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
                            sslCtx.ifPresent(s -> pipeline.addLast(s.newHandler(ch.alloc(), remoteHost, remotePort)));
                            pipeline.addLast(new HttpRequestEncoder());
                            pipeline.addLast(new ChildServerProxyHandler(inboundChannel));
                        }
                    });
            ChannelFuture channelFuture = bootstrap.connect(remoteHost, remotePort);
            outboundChannel = channelFuture.channel();
            channelFuture.addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    writeToOutboundOrClose(ctx, msg);
                } else {
                    inboundChannel.close();
                }
            });
        } else {
            writeToOutboundOrClose(ctx, msg);
        }
    }

    private void writeToOutboundOrClose(ChannelHandlerContext ctx, Object msg) {
        if (outboundChannel.isActive()) {
            outboundChannel.writeAndFlush(msg).addListener((ChannelFutureListener) f -> {
                if (f.isSuccess()) {
                    ctx.channel().read();
                } else {
                    f.channel().close();
                }
            });
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (outboundChannel != null) {
            closeOnFlush(outboundChannel);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        closeOnFlush(ctx.channel());
    }

    void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
