package com.nyshup.model;

public class Host {

    private final String host;
    private final int port;
    private final boolean ssl;

    public Host(String hostName, int port, boolean ssl) {
        this.host = hostName;
        this.port = port;
        this.ssl = ssl;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public boolean isSsl() {
        return ssl;
    }

}
