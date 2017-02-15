package com.nyshup.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;


public interface HttpRequestHandler {
    boolean supports(HttpRequest request);

    void process(ChannelHandlerContext ctx, HttpRequest request);
}
