package com.nyshup;

import com.jayway.restassured.http.ContentType;
import com.nyshup.model.Host;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.TimeUnit;

import static com.jayway.restassured.RestAssured.given;
import static com.nyshup.rules.RemoteHostHeadersRule.X_OTHER_REMOTE_HOST;
import static com.nyshup.rules.RemoteHostHeadersRule.X_OTHER_REMOTE_PORT;
import static com.nyshup.rules.RemoteHostHeadersRule.X_OTHER_REMOTE_REMOTE_SSL;
import static org.hamcrest.core.IsEqual.equalTo;

public class ProxyDynamicTestIT {
    public static final String TEST_JSON = "{'foo': 'bar'}";
    private static final int PROXY_PORT = 8088;
    private static final Host HOST = new Host("httpbin.org", 80, false);

    @Rule
    public Timeout globalTimeout= new Timeout(10, TimeUnit.SECONDS);

    @Test
    public void testGetServerAddressFromRequestParameters() throws Exception {
        ReverseProxy proxy = new ReverseProxyBuilder()
                .port(PROXY_PORT)
                .remoteHost(new Host("some host", 1111, true))
                .create();
        ProxyTestUtils.startServer(proxy);
        try {

            given().config(ProxyTestUtils.getRestAssuredWithSslConfig())
                    .contentType(ContentType.JSON)
                    .header(X_OTHER_REMOTE_HOST, HOST.getHost())
                    .header(X_OTHER_REMOTE_PORT, HOST.getPort())
                    .header(X_OTHER_REMOTE_REMOTE_SSL, HOST.isSsl())
                    .body(TEST_JSON)
                    .baseUri("http://localhost:8088")
                    .when()
                    .post("post")
                    .then()
                    .body("data", equalTo(TEST_JSON))
                    .statusCode(200);
        } finally {
            ProxyTestUtils.stopServer(proxy);
        }
    }
}
