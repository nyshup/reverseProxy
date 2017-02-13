###VG Trial Task - Netty Based Reverse Proxy

* Create a reverse proxy that routes to https://httpbin.org/ (http://httpbin.org/)
* Create a test that hits the proxy and POSTS some JSON content to the url /post (e.g. POST https://localhost:8080/post { “foo”: “bar” } which then proxies to https://httpbin.org/post {“foo”: “bar”}

####Key Points

* Choose libraries rather than writing your own code
* Explain why you wrote the tests that you did
* If this was used in production what else would you do to ensure that it was reliable?
* If we made the backend that is being proxied to dynamic how would you implement this (either code example or written description)
* How could we record a metric such as how much traffic flows through the proxy?
* How would you secure the proxy so that it could only be used from a specific IP address?

####Bonus

* Make the proxy work with http://www.meldium.com (http://www.meldium.com/) as the backend

###VG Trial Task (Solution)

####Build:

* mvn clean install
####Run:

* For help:
  * java -jar ./target/reverse-proxy-1.0-SNAPSHOT.jar -h
* For proxy on port "8080" with server "https://httpbin.org/" (https://localhost:8080)
  * java -jar ./target/reverse-proxy-1.0-SNAPSHOT.jar --port 8080 --rhost httpbin.org --rport 443
* For proxy on port "8080" with server "http://httpbin.org/"  (http://localhost:8080) (without ssl)) 
  * java -jar ./target/reverse-proxy-1.0-SNAPSHOT.jar --port 8080 --ssl false --rhost httpbin.org --rport 80 --rssl false (http connections)
* For proxy on port "8080" with server "https://www.meldium.com" (https://localhost:8080) (use ssl)
  * java -jar ./target/reverse-proxy-1.0-SNAPSHOT.jar --port 8080 --rhost www.meldium.com --rport 443

####Tests:
* com.nyshup.ReverseProxyTestIT: Integration test. Automatically run/stop server.
  * testClientPost_JSON - send json and check that content are sent correct. Verify with content received from httpbin.org.
  * testPostedHeaders - verify that headers were sent correctly
  * testPost_FormData - verify that form data were sent correctly
  * testPost_MultipartData - verify that multipart request was sent correctly


####Production:
For reliability some action could be implemented:
* write job that checks that server is running. 
    Could be ipmlemented by calling some statistic information from server.
    In case of error send alert to support, could try to restart server.
* run several instances of proxy server behind load balancer
* run several instances. Use one as master. In case of master is down change dns to point to backup server.
        
####Dynamic:
Based on request information behaviour could be changed. This is implemented in ProxyToServerClientHandler.
  After receiving HttpRequest part of request we could analyze request and change behaviour. Next functionality is implemented:
* Route to other backed server.
  * By default proxy route requests to server that was setup during start.
  If request contains headers "X-other-remote-rhost", "X-other-remote-rport", "X-other-remote-rssl" proxy route to server passed
   in these headers only for current request.
  * Only for current request next rules will be applied:
    * Header "X-other-remote-rport" replace parameter passed in --rport 
    * Header "X-other-remote-rhost" replace parameter passed in --rhost  
    * Header "X-other-remote-ssl" replace parameter passed in --rssl
        
####Metrics:
* For counting traffic GlobalTrafficShapingHandler is used. 
You could see traffic values by link "{url to proxy server}/traffic"

####To secure proxy RuleBasedIpFilter is used.
* To enable ip filtering use parameter --ipFilter with comma separated ip addresses.
In this case access will be available only from machines with ip address from setup parameter.
Example: java -jar ./target/reverse-proxy-1.0-SNAPSHOT.jar --port 8080 --rhost httpbin.org --rport 443 --ipFilter 127.0.0.1

    
####Bonus:
* java -jar ./target/reverse-proxy-1.0-SNAPSHOT.jar --port 8080 --sslPort --rhost www.meldium.com --rport 443