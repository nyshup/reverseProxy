package com.nyshup;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.Before;
import org.junit.Test;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.io.*;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.http.entity.ContentType.APPLICATION_FORM_URLENCODED;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.*;

public class ReverseProxyTest {

    public static final String TEST_JSON = "{'foo': 'bar'}";
    private CloseableHttpClient client;
    private String url = "https://127.0.0.1:8080/post";
    private String ethalonUrl = "https://httpbin.org:443/post";

    @Before
    public void setUp() throws Exception {
        client = createHttpClientAcceptsUntrustedCerts();
    }

    @Test
    public void testPost_JSON() throws Exception {

        HttpPost request = new HttpPost(url);
        request.addHeader(new BasicHeader("Content-Type", "application/json"));
        request.setEntity(new StringEntity(TEST_JSON));

        HttpPost ethalonRequest = new HttpPost(ethalonUrl);
        ethalonRequest.setEntity(new StringEntity(TEST_JSON));

        String json = EntityUtils.toString(client.execute(request).getEntity());

        String jsonEthalon = EntityUtils.toString(client.execute(ethalonRequest).getEntity());
        DocumentContext jsonDoc = JsonPath.parse(json);

        DocumentContext ethalonDoc = JsonPath.parse(jsonEthalon);
        checkEqual("Data should be equal", ethalonDoc, jsonDoc, "$.data");
        assertThat(jsonDoc.read("$.headers['Content-Type']", String.class), equalTo("application/json"));
    }

    @Test
    public void testPostedHeaders() throws IOException {
        Map<String, String> headers = new HashMap<String, String>(){{
            this.put("Accept", "application/json");
            this.put("Accept-Charset", "utf-8");
            this.put("Accept-Encoding", "gzip, deflate");
            this.put("Cache-Control", "no-cache");
            this.put("Pragma", "no-cache");
            this.put("Referer", "http://en.wikipedia.org/wiki/Main_Page");
            this.put("Content-Type", "application/json");
        }};

        HttpPost request = new HttpPost(url);
        headers.forEach((k, v) -> request.addHeader(new BasicHeader(k, v)));
        DocumentContext doc = call(request);
        headers.forEach((k, v) -> assertThat(doc.read("$.headers." + k, String.class), equalTo(v)));
    }

    private void checkEqual(String message, DocumentContext ethalon, DocumentContext doc, String jsonPath) {
        assertEquals(message, ethalon.read(jsonPath, String.class), doc.read(jsonPath));
    }

    @Test
    public void testPost_FormData() throws Exception {

        List<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair("param1", "value1"));
        nvps.add(new BasicNameValuePair("param2", "value2"));
        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(nvps);

        HttpPost request = new HttpPost(url);
        request.setEntity(entity);

        HttpPost requsetEthalon = new HttpPost(ethalonUrl);
        requsetEthalon.setEntity(entity);

        DocumentContext jsonDoc = call(request);
        DocumentContext ethalon = call(requsetEthalon);

        assertEquals("value1", jsonDoc.read("$.form['param1']"));
        assertEquals("value2", jsonDoc.read("$.form['param2']"));
        checkEqual("", ethalon, jsonDoc, "$.form['param1']");
        checkEqual("", ethalon, jsonDoc, "$.form['param2']");
        assertEquals(APPLICATION_FORM_URLENCODED.getMimeType(), jsonDoc.read("$.headers['Content-Type']"));

    }

    @Test
    public void testPost_MultipartData() throws Exception {

        HttpPost request = new HttpPost(url);
        request.setEntity(MultipartEntityBuilder.create()
            .addBinaryBody("file", getFile("testFile.txt"), ContentType.APPLICATION_OCTET_STREAM, "")
            .build());

        HttpPost ethalonRequest = new HttpPost(ethalonUrl);
        ethalonRequest.setEntity(MultipartEntityBuilder.create()
                .addBinaryBody("file", getFile("testFile.txt"), ContentType.APPLICATION_OCTET_STREAM, "")
                .build());
        System.out.println();

        DocumentContext doc = call(request);
        DocumentContext ethalonDoc = call(ethalonRequest);

        checkEqual("Content should be equal", ethalonDoc, doc, "$.files.file");
        assertTrue("Content should not be empty", doc.read("$.files.file", String.class).length() > 0);
        assertThat(doc.read("$.headers['Content-Type']", String.class), startsWith("multipart/form-data"));
    }

    private DocumentContext call(HttpPost request) throws IOException {
        return JsonPath.parse(EntityUtils.toString(client.execute(request).getEntity()));
    }

    private InputStream getFile(String file) {
        return this.getClass().getClassLoader().getResourceAsStream(file);
    }

    private CloseableHttpClient createHttpClientAcceptsUntrustedCerts() throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
        HttpClientBuilder b = HttpClientBuilder.create();
        SSLContext sslContext = new SSLContextBuilder().loadTrustMaterial(null, new TrustStrategy() {
            public boolean isTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                return true;
            }
        }).build();
        b.setSSLContext(sslContext);

        HostnameVerifier hostnameVerifier = NoopHostnameVerifier.INSTANCE;

        SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext, hostnameVerifier);
        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", sslSocketFactory)
                .build();

        PoolingHttpClientConnectionManager connMgr = new PoolingHttpClientConnectionManager( socketFactoryRegistry);
        b.setConnectionManager( connMgr);

        return b.build();
    }

}