package com.nyshup;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;

import javax.net.ssl.SSLException;
import java.security.cert.CertificateException;
import java.util.Optional;


public class ReverseProxy {

    final private int port;
    final private String remoteHost;
    final private int remotePort;
    final private boolean ssl;

    public ReverseProxy(int port, String remoteHost, int remotePort, boolean ssl) {
        this.port = port;
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.ssl = ssl;
    }

    public static void main(String[] args) throws Exception {
        //TODO: write validation
        new ReverseProxy(Integer.parseInt(args[0]), args[1], Integer.parseInt(args[2]), Boolean.parseBoolean(args[3])).start();
    }

    private void start() throws Exception {
        final Optional<SslContext> sslCtx = getSslContext();
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childOption(ChannelOption.AUTO_READ, false)
                    .childHandler(new ChannelInitializer<SocketChannel>() {

                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
                            sslCtx.ifPresent(s -> pipeline.addLast(s.newHandler(ch.alloc())));
                            pipeline.addLast(new HttpRequestDecoder());
                            pipeline.addLast(new ChildProxyHandler(remoteHost, remotePort, ssl));
                        }
                    })
                    .bind(port).sync().channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }

    }

    private Optional<SslContext> getSslContext() throws CertificateException, SSLException {
        Optional<SslContext> sslCtx = Optional.empty();
        if (ssl) {
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            sslCtx = Optional.of(SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build());
        }
        return sslCtx;
    }


}
