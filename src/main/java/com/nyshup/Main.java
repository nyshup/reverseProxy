package com.nyshup;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

/**
 * Created by ruslan on 2/6/17.
 */
public class Main {

    @Parameter(names={"--port"}, description = "Port which use proxy server to run", required = true)
    private Integer port;

    @Parameter(names = {"--ssl"}, description = "Use ssl to connect proxy server")
    private boolean localSsl = true;

    @Parameter(names={"--rhost"}, description = "Host of backed server", required = true)
    private String remoteHost;

    @Parameter(names={"--rport"}, description = "Port of backed server", required = true)
    private Integer remotePort;

    @Parameter(names = {"--rssl"}, description = "Use ssl to connect backed server")
    private boolean remoteSsl = true;

    @Parameter(names = {"--help"}, help = true)
    private boolean help;


    public static void main(String ... args) throws Exception {
        Main main = new Main();
        JCommander commander = new JCommander(main, args);
        if (main.help) commander.usage();
        new ReverseProxy(main.port, main.localSsl, main.remoteHost, main.remotePort, main.remoteSsl).start();
    }
}