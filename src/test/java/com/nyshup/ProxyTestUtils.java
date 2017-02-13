package com.nyshup;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.config.RestAssuredConfig;
import com.jayway.restassured.config.SSLConfig;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

/**
 * Created by ruslan on 2/12/17.
 */
public class ProxyTestUtils {

    public static ReverseProxy startServer(final ReverseProxy proxy) throws Exception {
        new Thread(() -> {
            try {
                proxy.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
        await().atMost(1000, SECONDS).until(() -> proxy.getStatus() == ReverseProxy.Status.RUNNING);
        return proxy;
    }

    public static void stopServer(ReverseProxy proxy) throws Exception {
        proxy.shutdownGracefully();
        await().atMost(10, SECONDS).until(() -> proxy.getStatus() == ReverseProxy.Status.TERMINATED);
    }

    public static RestAssuredConfig getRestAssuredWithSslConfig() {
        return RestAssured.config().sslConfig(
                new SSLConfig().relaxedHTTPSValidation());
    }

}
