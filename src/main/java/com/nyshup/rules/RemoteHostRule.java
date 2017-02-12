package com.nyshup.rules;

import com.nyshup.model.Host;
import io.netty.handler.codec.http.HttpRequest;

public interface RemoteHostRule {
    Host getHost(HttpRequest request);
}
