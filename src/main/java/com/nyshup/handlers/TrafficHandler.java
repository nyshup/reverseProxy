package com.nyshup.handlers;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import io.netty.handler.traffic.TrafficCounter;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class TrafficHandler implements HttpRequestHandler {

    @Override
    public boolean supports(HttpRequest request) {
        return request.uri().endsWith("traffic");
    }

    @Override
    public void process(ChannelHandlerContext ctx, HttpRequest request) {
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
}
