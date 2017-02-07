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
import io.netty.handler.traffic.GlobalTrafficShapingHandler;

import javax.net.ssl.SSLException;
import java.net.InetSocketAddress;
import java.security.cert.CertificateException;
import java.util.Optional;
import java.util.Set;


public class ReverseProxy {

    final private int port;
    final private boolean ssl;
    final private String remoteHost;
    final private int remotePort;
    final private boolean remoteSsl;
    private final Set<String> ipFilters;
    private volatile GlobalTrafficShapingHandler globalTrafficShapingHandler;

    public ReverseProxy(int port, boolean ssl, String remoteHost, int remotePort, boolean remSsl, Set<String> ipFilter) {
        this.port = port;
        this.ssl = ssl;
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.remoteSsl = remSsl;
        this.ipFilters = ipFilter;
    }

    public void start() throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        Optional<SslContext> sslContext = getSslContext();
        Optional<RuleBasedIpFilter> ipFilter = getRuleBasedIpFilter();
        globalTrafficShapingHandler = new GlobalTrafficShapingHandler(workerGroup, 1000, 1000);
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
                            sslContext.ifPresent(s -> pipeline.addLast(s.newHandler(ch.alloc())));
                            ipFilter.ifPresent(ip -> pipeline.addLast("ipfilter", ip));
                            pipeline.addLast("traffic", globalTrafficShapingHandler);
                            pipeline.addLast("decoder", new HttpRequestDecoder());
                            pipeline.addLast("proxyToServer", new ProxyToServerClientHandler(remoteHost, remotePort, remoteSsl));
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

    private Optional<RuleBasedIpFilter> getRuleBasedIpFilter() {
        if (!ipFilters.isEmpty()) {
            return Optional.of(new RuleBasedIpFilter(new IpFilterRule() {
                @Override
                public boolean matches(InetSocketAddress remoteAddress) {
                    return !ipFilters.contains(remoteAddress.getAddress().getHostAddress());
                }

                @Override
                public IpFilterRuleType ruleType() {
                    return IpFilterRuleType.REJECT;
                }
            }));
        }
        return Optional.empty();
    }
}
