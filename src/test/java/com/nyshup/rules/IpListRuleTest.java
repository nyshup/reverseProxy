package com.nyshup.rules;

import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.Collections;

import static io.netty.handler.ipfilter.IpFilterRuleType.REJECT;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.*;

/**
 * Created by ruslan on 2/13/17.
 */
public class IpListRuleTest {

    @Test
    public void testRule() {
        IpListRule rule = new IpListRule(Collections.singleton("127.0.0.1"));
        assertFalse(rule.matches(new InetSocketAddress("127.0.0.1", 80)));
        assertTrue(rule.matches(new InetSocketAddress("0.0.0.0", 80)));
        assertThat(rule.ruleType(), equalTo(REJECT));
    }

}