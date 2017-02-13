package com.nyshup;

import com.jayway.restassured.http.ContentType;
import com.nyshup.model.Host;
import org.junit.Test;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;


public class TrafficHandlersTestIT {

    private static final int PROXY_PORT = 8088;
    private static final Host HOST = new Host("httpbin.org", 80, false);

    @Test
    public void testGetTraffic() throws Exception {
        ReverseProxy proxy = new ReverseProxyBuilder()
                .port(PROXY_PORT)
                .remoteHost(HOST)
                .create();
        ProxyTestUtils.startServer(proxy);
        try {
            given().config(ProxyTestUtils.getRestAssuredWithSslConfig())
                    .contentType(ContentType.TEXT)
                    .baseUri("http://localhost:8088")
                    .when()
                    .get("traffic")
                    .then()
                    .content(containsString("Read bytes"))
                    .statusCode(200);
        } finally {
            ProxyTestUtils.stopServer(proxy);
        }
    }
}
