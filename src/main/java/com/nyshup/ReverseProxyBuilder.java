package com.nyshup;

import com.nyshup.model.Host;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ReverseProxyBuilder {

    private int port = 0;
    private int sslPort = 0;
    private Host remoteHost;
    private Set<String> ipFilter = new HashSet<>();

    public ReverseProxyBuilder port(int port) {
        this.port = port;
        return this;
    }

    public ReverseProxyBuilder sslPort(int sslPort) {
        this.sslPort = sslPort;
        return this;
    }

    public ReverseProxyBuilder remoteHost(String host, int port, boolean ssl) {
        this.remoteHost = new Host(host, port, ssl);
        return this;
    }

    public ReverseProxyBuilder remoteHost(Host host) {
        this.remoteHost = host;
        return this;
    }

    public ReverseProxyBuilder ipFilter(Set<String> ipFilter) {
        this.ipFilter = ipFilter;
        return this;
    }

    public ReverseProxy create() {
        if (this.port == 0 && this.sslPort == 0) throw new IllegalArgumentException("At least one port (port or sslPort) should be set");
        if (this.remoteHost == null) throw new IllegalArgumentException("Remote host should be set");
        Map<Integer, Boolean> ports = new HashMap<>();
        if (this.port > 0) {
            ports.put(this.port, false);
        }
        if (this.sslPort > 0) {
            ports.put(this.sslPort, true);
        }
        return new ReverseProxy(ports, remoteHost, ipFilter);
    }
}