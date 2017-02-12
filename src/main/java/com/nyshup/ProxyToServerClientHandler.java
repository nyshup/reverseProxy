package com.nyshup;

import com.nyshup.model.Host;
import com.nyshup.rules.RemoteHostRule;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import io.netty.handler.traffic.TrafficCounter;

import javax.net.ssl.SSLException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class ProxyToServerClientHandler extends ChannelInboundHandlerAdapter {

    final private RemoteHostRule remoteHostRule;
    private CompletableFuture<Channel> completableFuture = new CompletableFuture<>();


    public ProxyToServerClientHandler(RemoteHostRule remoteHostRule) {
        this.remoteHostRule = remoteHostRule;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().read();
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
        if (isTrafficRequest(msg)) {
            processTrafficResponse(ctx);
            return;
        }
        if (msg instanceof HttpRequest) {
            initConnectionToServer(ctx, (HttpRequest) msg);
        }
        processChannelRead(ctx, msg);
    }

    private void processChannelRead(ChannelHandlerContext ctx, Object msg) {
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

    private void initConnectionToServer(ChannelHandlerContext ctx, HttpRequest request) {

        final Host remoteHostToConnect = remoteHostRule.getHost(request);

        updateHeaderFields(request, remoteHostToConnect);

        Bootstrap bootstrap = new Bootstrap();
        final Channel inboundChannel = ctx.channel();
        bootstrap.group(inboundChannel.eventLoop())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.AUTO_READ, false)
                .handler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        getSslContext(remoteHostToConnect.isSsl()).ifPresent(
                                s -> pipeline.addLast("remoteSsl",
                                        s.newHandler(ch.alloc(), remoteHostToConnect.getHost(), remoteHostToConnect.getPort())));
                        pipeline.addLast("serverCodec", new HttpClientCodec());
                        pipeline.addLast(new ProxyToServerBackendHandler(inboundChannel));
                    }
                });
        ChannelFuture channelFuture = bootstrap.connect(remoteHostToConnect.getHost(),
                remoteHostToConnect.getPort());
        CompletableFuture<Channel> cfuture = completableFuture;
        channelFuture.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                cfuture.complete(channelFuture.channel());
            } else {
                inboundChannel.close();
            }
        });
    }

    private boolean isTrafficRequest(Object msg) {
        if (msg instanceof HttpRequest &&
            ((HttpRequest) msg).uri().endsWith("traffic")) return true;
        return false;
    }

    private void processTrafficResponse(ChannelHandlerContext ctx) {
            ctx.pipeline().addBefore("decoder", "encoder", new HttpResponseEncoder());
            TrafficCounter counter = ((GlobalTrafficShapingHandler) ctx.pipeline().get("traffic")).trafficCounter();
            StringBuilder builder = new StringBuilder("Statistics : ");
            builder.append("Read bytes[").append(counter.cumulativeReadBytes() >> 10).append("Kb]  ")
                    .append("Write bytes:").append("[").append(counter.cumulativeWrittenBytes() >> 10).append("Kb]");

            FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.wrappedBuffer(builder.toString().getBytes()));
            response.headers().set(CONTENT_TYPE, "text/plain");
            response.headers().setInt(CONTENT_LENGTH, response.content().readableBytes());
            ctx.write(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void updateHeaderFields(HttpRequest msg, Host host) {
        msg.headers().set("Host", host.getHost() + ":" + host.getPort());
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

    private Optional<SslContext> getSslContext(boolean ssl) throws SSLException {
        Optional<SslContext> sslCtx = Optional.empty();
        if (ssl) {
            sslCtx = Optional.of(SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE).build());
        }
        return sslCtx;
    }


}
