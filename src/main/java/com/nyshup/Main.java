package com.nyshup;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import java.util.HashSet;
import java.util.Set;


public class Main {

    public static final String TRUE = "true";

    @Parameter(names = {"--port"}, description = "Port which use proxy server to run", required = true)
    private Integer port;

    @Parameter(names = {"--ssl"}, description = "Use ssl to connect proxy server")
    private String localSsl = "true";

    @Parameter(names = {"--rhost"}, description = "Host of backed server", required = true)
    private String remoteHost;

    @Parameter(names = {"--rport"}, description = "Port of backed server", required = true)
    private Integer remotePort;

    @Parameter(names = {"--rssl"}, description = "Use ssl to connect backed server")
    private String remoteSsl = TRUE;

    @Parameter(names = {"--ipFilter"}, description = "List of allowed ip addresses splited with ','")
    private String ipFilters = "";

    @Parameter(names = {"--help", "-h"}, description = "Test", help = true)
    private boolean help;


    public static void main(String... args) throws Exception {
        Main main = new Main();
        JCommander commander = new JCommander(main, args);
        Set<String> ips = new HashSet<>();
        if (!main.ipFilters.isEmpty()) {
            for (String itFilter : main.ipFilters.split(",")) {
                ips.add(itFilter);
            }
        }
        if (main.help) commander.usage();
        new ReverseProxy(main.port,
                TRUE.equalsIgnoreCase(main.localSsl),
                main.remoteHost,
                main.remotePort,
                TRUE.equalsIgnoreCase(main.remoteSsl),
                ips).start();
    }
}
