package com.nyshup;

import com.jayway.restassured.http.ContentType;
import com.nyshup.model.Host;
import org.junit.*;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.core.IsEqual.equalTo;

/**
 * Post json to http(s)://httpbin.org/post through http(s) proxy's port.
 */
public class ReverseProxyHttpbinPostTestIT {

    public static final String TEST_JSON = "{'foo': 'bar'}";
    private static final String REMOTE_HOST = "httpbin.org";
    private static final String PROXY_HOST = "127.0.0.1";
    private static final int PROXY_PORT = 8088;
    private static final int PROXY_PORT_SSL = 8080;
    private static final Host REMOTE_WITH_SSL = new Host(REMOTE_HOST, 443, true);
    private static final Host REMOTE_WITHOUT_SSL = new Host(REMOTE_HOST, 80, false);

    @Test(timeout = 10000)
    public void testPostJsonWithSsl() throws Exception {
        postJsonThoughProxy(new ReverseProxyBuilder()
                .port(PROXY_PORT)
                .sslPort(PROXY_PORT_SSL)
                .remoteHost(REMOTE_WITH_SSL)
                .create());
    }

    @Test(timeout = 10000)
    public void testPostJsonWithoutSsl() throws Exception {
        postJsonThoughProxy(new ReverseProxyBuilder()
                .port(PROXY_PORT)
                .sslPort(PROXY_PORT_SSL)
                .remoteHost(REMOTE_WITHOUT_SSL)
                .create());
    }

    private void postJsonThoughProxy(ReverseProxy proxy) throws Exception {
        try {
            ProxyTestUtils.startServer(proxy);
            postDataAndCheck(true);
            postDataAndCheck(false);
        } finally {
            ProxyTestUtils.stopServer(proxy);
        }
    }

    private void postDataAndCheck(boolean useSsl) {
        given().config(ProxyTestUtils.getRestAssuredWithSslConfig())
                .contentType(ContentType.JSON)
                .baseUri(proxyUrl(useSsl))
                .body(TEST_JSON)
                .when()
                .post("post")
                .then()
                .body("data", equalTo(TEST_JSON))
                .statusCode(200);
    }

    private String proxyUrl(boolean useSsl) {
        return String.format("%s://%s:%s",
                useSsl? "https": "http",
                PROXY_HOST,
                useSsl? PROXY_PORT_SSL: PROXY_PORT);
    }



}
