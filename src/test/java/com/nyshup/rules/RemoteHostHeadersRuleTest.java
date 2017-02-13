package com.nyshup.rules;

import com.nyshup.model.Host;
import io.netty.handler.codec.http.*;
import org.junit.Before;
import org.junit.Test;

import static com.nyshup.rules.RemoteHostHeadersRule.X_OTHER_REMOTE_HOST;
import static com.nyshup.rules.RemoteHostHeadersRule.X_OTHER_REMOTE_PORT;
import static com.nyshup.rules.RemoteHostHeadersRule.X_OTHER_REMOTE_REMOTE_SSL;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.*;

/**
 * Created by ruslan on 2/12/17.
 */
public class RemoteHostHeadersRuleTest {

    private static Host defaultHost = new Host("localhost", 8080, true);
    RemoteHostHeadersRule tested;


    @Before
    public void setup() {
        tested = new RemoteHostHeadersRule(defaultHost);
    }

    @Test
    public void testRemoteHostWithoutHeaders() {
        HttpRequest request = request();
        Host remoteHost = tested.getHost(request);
        assertThat(remoteHost, equalTo(defaultHost));
    }

    @Test
    public void testRemoteHostWithHeaders() {
        HttpRequest request = request(
                X_OTHER_REMOTE_HOST, "host",
                X_OTHER_REMOTE_PORT, "9999",
                X_OTHER_REMOTE_REMOTE_SSL, "false");
        Host remoteHost = tested.getHost(request);
        assertThat(remoteHost.getHost(), equalTo("host"));
        assertThat(remoteHost.getPort(), equalTo(9999));
        assertFalse(remoteHost.isSsl());
    }

    HttpRequest request(String... header) {
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "url");
        for (int i = 0; i < header.length; i += 2) {
            request.headers().add(header[i], header[i + 1]);
        }
        return request;
    }

}