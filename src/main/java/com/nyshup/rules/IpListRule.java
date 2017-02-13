package com.nyshup.rules;

import io.netty.handler.ipfilter.IpFilterRule;
import io.netty.handler.ipfilter.IpFilterRuleType;

import java.net.InetSocketAddress;
import java.util.Set;


public class IpListRule implements IpFilterRule {

    private Set<String> ipAddresses;

    public IpListRule(Set<String> ipAddresses) {
        this.ipAddresses = ipAddresses;
    }

    @Override
    public boolean matches(InetSocketAddress remoteAddress) {
        return !ipAddresses.contains(remoteAddress.getAddress().getHostAddress());
    }

    @Override
    public IpFilterRuleType ruleType() {
        return IpFilterRuleType.REJECT;
    }
}
