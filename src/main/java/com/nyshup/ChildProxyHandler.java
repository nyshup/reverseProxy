package com.nyshup;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import javax.net.ssl.SSLException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class ChildProxyHandler extends ChannelInboundHandlerAdapter {

    final private String remoteHost;
    final private int remotePort;
    private final boolean ssl;
    private CompletableFuture<Channel> completableFuture = new CompletableFuture<>();


    public ChildProxyHandler(String remoteHost, int remotePort, boolean ssl) {
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.ssl = ssl;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().read();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            updateFields((HttpRequest) msg);
            Bootstrap bootstrap = new Bootstrap();
            final Channel inboundChannel = ctx.channel();
            bootstrap.group(inboundChannel.eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.AUTO_READ, false)
                    .handler(new ChannelInitializer<NioSocketChannel>() {
                        @Override
                        protected void initChannel(NioSocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
                            getSslContext().ifPresent(s -> pipeline.addLast("ssl", s.newHandler(ch.alloc(), remoteHost, remotePort)));
                            pipeline.addLast("encoder", new HttpRequestEncoder());
                            pipeline.addLast(new ChildServerProxyHandler(inboundChannel));
                        }
                    });
            ChannelFuture channelFuture = bootstrap.connect(remoteHost, remotePort);
            CompletableFuture<Channel> cfuture = completableFuture;
            channelFuture.addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    cfuture.complete(channelFuture.channel());
                } else {
                    inboundChannel.close();
                }
            });
        }
        completableFuture = completableFuture.thenApply(outboundChannel -> {
            if (outboundChannel.isActive()) {
                outboundChannel.writeAndFlush(msg).addListener((ChannelFutureListener) f -> {
                    if (f.isSuccess()) {
                        ctx.channel().read();
                    } else {
                        f.channel().close();
                    }
                });
            }
            return outboundChannel;
        });

    }

    private void updateFields(HttpRequest msg) {
        msg.headers().set("Host", remoteHost + ":" + remotePort);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        completableFuture.thenAccept(outboundChannel -> {
            if (outboundChannel != null) {
                closeOnFlush(outboundChannel);
            }

        });
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

    private Optional<SslContext> getSslContext() throws SSLException {
        Optional<SslContext> sslCtx = Optional.empty();
        if (this.ssl) {
            sslCtx = Optional.of(SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE).build());
        }
        return sslCtx;
    }


}
