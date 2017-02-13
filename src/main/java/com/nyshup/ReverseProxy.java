package com.nyshup;

import com.nyshup.model.Host;
import com.nyshup.rules.IpListRule;
import com.nyshup.rules.RemoteHostHeadersRule;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ipfilter.RuleBasedIpFilter;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;

import javax.net.ssl.SSLException;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class ReverseProxy {

    public enum Status {
        RUNNING, TERMINATED
    }

    final private Map<Integer, Boolean> portsToSsl = new HashMap<>();
    final Host remoteHost;
    private final Set<String> ipFilters;
    private volatile GlobalTrafficShapingHandler globalTrafficShapingHandler;
    private volatile EventLoopGroup bossGroup;
    private volatile EventLoopGroup workerGroup;
    private volatile Status status;

    public ReverseProxy(Map<Integer, Boolean> ports, Host remoteHost, Set<String> ipFilter) {
        this.portsToSsl.putAll(ports);
        this.remoteHost = remoteHost;
        this.ipFilters = ipFilter;
    }

    public void start() throws Exception {
        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup();
        SslContext sslContext = getSslContext();
        Optional<RuleBasedIpFilter> ipFilter = getRuleBasedIpFilter();
        globalTrafficShapingHandler = new GlobalTrafficShapingHandler(workerGroup, 15000);
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childOption(ChannelOption.AUTO_READ, false);
            List<Channel> channels = new ArrayList<>();
            for (Integer port: portsToSsl.keySet()) {
                Channel ch = bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {

                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        if (portsToSsl.get(port)) pipeline.addLast(sslContext.newHandler(ch.alloc()));
                        ipFilter.ifPresent(ip -> pipeline.addLast("ipfilter", ip));
                        pipeline.addLast("traffic", globalTrafficShapingHandler);
                        pipeline.addLast("decoder", new HttpRequestDecoder());
                        pipeline.addLast("proxyToServer", new ProxyToServerClientHandler(new RemoteHostHeadersRule(remoteHost)));
                    }

                }).bind(port).sync().channel();
                channels.add(ch);
            }
            this.status = Status.RUNNING;
            for (Channel channel: channels) {
                channel.closeFuture().sync();
            }
        } finally {
            shutdownGracefully();
        }
    }

    public void shutdownGracefully() throws InterruptedException {
        bossGroup.shutdownGracefully(0, 10, TimeUnit.SECONDS).sync();
        workerGroup.shutdownGracefully(0, 10, TimeUnit.SECONDS).sync();
        this.status = Status.TERMINATED;
    }

    public Status getStatus() {
        return status;
    }

    private SslContext getSslContext() throws CertificateException, SSLException {
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        return SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
    }

    private Optional<RuleBasedIpFilter> getRuleBasedIpFilter() {
        if (!ipFilters.isEmpty()) {
            return Optional.of(new RuleBasedIpFilter(new IpListRule(ipFilters)));
        }
        return Optional.empty();
    }
}
