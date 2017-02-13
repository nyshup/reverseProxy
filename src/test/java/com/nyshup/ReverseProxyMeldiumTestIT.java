package com.nyshup;

import com.jayway.restassured.filter.log.RequestLoggingFilter;
import com.jayway.restassured.filter.log.ResponseLoggingFilter;
import com.jayway.restassured.http.ContentType;
import com.nyshup.model.Host;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.TimeUnit;

import static com.jayway.restassured.RestAssured.given;

public class ReverseProxyMeldiumTestIT {

    private static final int PROXY_PORT = 8088;
    private static final int PROXY_PORT_SSL = 8080;
    private static final String HTTPS_URL = "https://127.0.0.1:" + PROXY_PORT_SSL;
    private static final String HTTP_URL = "http://127.0.0.1:" + PROXY_PORT;
    private static final Host REMOTE_HOST = new Host("www.meldium.com", 443, true);

    @Rule
    public Timeout globalTimeout= new Timeout(10, TimeUnit.SECONDS);

    @Test
    public void testMeldium() throws Exception {
        ReverseProxy proxy = new ReverseProxyBuilder()
                .port(PROXY_PORT)
                .sslPort(PROXY_PORT_SSL)
                .remoteHost(REMOTE_HOST)
                .create();
        ProxyTestUtils.startServer(proxy);

        checkGetToUrl(HTTP_URL);
        checkGetToUrl(HTTPS_URL);

        ProxyTestUtils.stopServer(proxy);
    }

    private void checkGetToUrl(String url) {
        given().config(ProxyTestUtils.getRestAssuredWithSslConfig())
                .filter(new RequestLoggingFilter())
                .filter(new ResponseLoggingFilter())
                .contentType(ContentType.JSON)
                .baseUri(url)
                .when()
                .get("/")
                .then()
                .statusCode(200);
    }

}
