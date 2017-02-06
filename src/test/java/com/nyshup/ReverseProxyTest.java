package com.nyshup;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
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
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.After;
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
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.http.entity.ContentType.APPLICATION_FORM_URLENCODED;
import static org.junit.Assert.assertEquals;

public class ReverseProxyTest {

    public static final String TEST_STRING = "TEST_STRING";
    private CloseableHttpClient client;
    private String url = "https://127.0.0.1:8080/post";


    @Before
    public void setUp() throws Exception {
        client = createHttpClientAcceptsUntrustedCerts();
    }

    @After
    public void tearDown() throws IOException {
        client.close();
    }
    @Test
    public void testClientPost_Plain() throws Exception {

        HttpPost request = new HttpPost(url);
        request.setEntity(new StringEntity(TEST_STRING));

        HttpResponse httpResponse = client.execute(request);
        String json = EntityUtils.toString(httpResponse.getEntity());
        assertEquals(TEST_STRING, JsonPath.parse(json).read("$.data"));
    }

    @Test
    public void testClientPost_FormData() throws Exception {

        HttpPost request = new HttpPost(url);
        List<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair("param1", "value1"));
        nvps.add(new BasicNameValuePair("param2", "value2"));
        request.setEntity(new UrlEncodedFormEntity(nvps));

        HttpResponse httpResponse = client.execute(request);
        String json = EntityUtils.toString(httpResponse.getEntity());
        DocumentContext jsonDoc = JsonPath.parse(json);
        assertEquals("value1", jsonDoc.read("$.form['param1']"));
        assertEquals("value2", jsonDoc.read("$.form['param2']"));
        assertEquals(APPLICATION_FORM_URLENCODED.getMimeType(), jsonDoc.read("$.headers['Content-Type']"));
    }

    @Test
    public void testClientPost_MultipartData() throws Exception {

        HttpPost request = new HttpPost(url);
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        InputStream in = this.getClass().getClassLoader()
                .getResourceAsStream("testFile.txt");
        builder.addBinaryBody(
                "file",
                in,
                ContentType.APPLICATION_OCTET_STREAM,
                "testFile.txt"
        );
        HttpEntity multipart = builder.build();
        request.setEntity(multipart);

        HttpResponse httpResponse = client.execute(request);
        in.close();
        String json = EntityUtils.toString(httpResponse.getEntity());
        System.out.println(json);
        DocumentContext jsonDoc = JsonPath.parse(json);
        assertEquals(read(this.getClass().getClassLoader()
                .getResourceAsStream("testFile.txt")), jsonDoc.read("$.files.file"));


    }

    private CloseableHttpClient createHttpClientAcceptsUntrustedCerts() throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
        HttpClientBuilder b = HttpClientBuilder.create();

        // setup a Trust Strategy that allows all certificates.
        //
        SSLContext sslContext = new SSLContextBuilder().loadTrustMaterial(null, new TrustStrategy() {
            public boolean isTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                return true;
            }
        }).build();
        b.setSSLContext(sslContext);

        // don't check Hostnames, either.
        //      -- use SSLConnectionSocketFactory.getDefaultHostnameVerifier(), if you don't want to weaken
        HostnameVerifier hostnameVerifier = NoopHostnameVerifier.INSTANCE;

        // here's the special part:
        //      -- need to create an SSL Socket Factory, to use our weakened "trust strategy";
        //      -- and create a Registry, to register it.
        //
        SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext, hostnameVerifier);
        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", sslSocketFactory)
                .build();

        // now, we create connection-manager using our Registry.
        //      -- allows multi-threaded use
        PoolingHttpClientConnectionManager connMgr = new PoolingHttpClientConnectionManager( socketFactoryRegistry);
        b.setConnectionManager( connMgr);

        // finally, build the HttpClient;
        //      -- done!
        return b.build();
    }

    public static String read(InputStream input) throws IOException {
        try (BufferedReader buffer = new BufferedReader(new InputStreamReader(input))) {
            return buffer.lines().collect(Collectors.joining("\n"));
        }
    }

}