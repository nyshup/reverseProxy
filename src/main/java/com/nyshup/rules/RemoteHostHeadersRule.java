package com.nyshup.rules;

import com.nyshup.model.Host;
import io.netty.handler.codec.http.HttpRequest;

public class RemoteHostHeadersRule implements RemoteHostRule {

    public static final String X_OTHER_REMOTE_REMOTE_SSL = "X-other-remote-ssl";
    public static final String X_OTHER_REMOTE_HOST = "X-other-remote-host";
    public static final String X_OTHER_REMOTE_PORT = "X-other-remote-port";

    private final Host defaultRemoteHost;

    public RemoteHostHeadersRule(Host remoteHost) {
        this.defaultRemoteHost = remoteHost;
    }

    @Override
    public Host getHost(HttpRequest request) {
        return new Host(
                getRemoteHost(request),
                getRemotePort(request),
                getRemoteSsl(request)
        );
    }

    private boolean getRemoteSsl(HttpRequest request) {
        String ssl = request.headers().get(X_OTHER_REMOTE_REMOTE_SSL);
        if (ssl == null) {
            return this.defaultRemoteHost.isSsl();
        }
        return ssl.equalsIgnoreCase("true");
    }

    private String getRemoteHost(HttpRequest request) {
        String host = request.headers().get(X_OTHER_REMOTE_HOST);
        if (host == null) {
            return this.defaultRemoteHost.getHost();
        }
        return host;
    }

    private int getRemotePort(HttpRequest request) {
        Integer port = request.headers().getInt(X_OTHER_REMOTE_PORT);
        if (port == null) {
            return this.defaultRemoteHost.getPort();
        }
        return port;
    }
}
