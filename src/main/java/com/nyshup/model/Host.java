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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Host host1 = (Host) o;

        if (port != host1.port) return false;
        if (ssl != host1.ssl) return false;
        return host.equals(host1.host);
    }

    @Override
    public int hashCode() {
        int result = host.hashCode();
        result = 31 * result + port;
        result = 31 * result + (ssl ? 1 : 0);
        return result;
    }
}
