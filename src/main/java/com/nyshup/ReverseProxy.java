package com.nyshup;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ipfilter.IpFilterRule;
import io.netty.handler.ipfilter.IpFilterRuleType;
import io.netty.handler.ipfilter.RuleBasedIpFilter;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;

import javax.net.ssl.SSLException;
import java.net.InetSocketAddress;
import java.security.cert.CertificateException;
import java.util.Optional;


public class ReverseProxy {

    final private int port;
    final private boolean ssl;
    final private String remoteHost;
    final private int remotePort;
    final private boolean remoteSsl;

    public ReverseProxy(int port, boolean ssl, String remoteHost, int remotePort, boolean remSsl) {
        this.port = port;
        this.ssl = ssl;
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.remoteSsl = remSsl;
    }

    public void start() throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        Optional<SslContext> sslContext = getSslContext();
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
                            pipeline.addLast(new RuleBasedIpFilter(new IpFilterRule() {
                                @Override
                                public boolean matches(InetSocketAddress remoteAddress) {
                                    return false;
                                }

                                @Override
                                public IpFilterRuleType ruleType() {
                                    return IpFilterRuleType.REJECT;
                                }
                            }));
                            sslContext.ifPresent(s -> pipeline.addLast(s.newHandler(ch.alloc())));
                            pipeline.addLast(new HttpRequestDecoder());
                            pipeline.addLast(new ChildProxyHandler(remoteHost, remotePort, remoteSsl));
                            pipeline.addLast(new LoggingHandler(LogLevel.INFO));
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
