package com.pojosontheweb.selenium;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.util.EntityUtils;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.ExternalSessionKey;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.TestSession;
import org.openqa.selenium.remote.internal.HttpClientFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.logging.Logger;

public class NodeProxy extends org.openqa.grid.selenium.proxy.DefaultRemoteProxy {

    private static final Logger log = Logger.getLogger(NodeProxy.class.getName());

    private final HttpClient client;
    private final HttpHost remoteHost;
    private final String serviceUrl;

    public NodeProxy(RegistrationRequest request, Registry registry) {
        super(request, registry);
        remoteHost = new HttpHost(getRemoteHost().getHost(), getRemoteHost().getPort());
        HttpClientFactory httpClientFactory = new HttpClientFactory();
        client = httpClientFactory.getHttpClient();
        serviceUrl = getRemoteHost() + "/extra/RecorderServlet";
        log.info("NodeProxy Ready, service URL = " + serviceUrl);
    }

    @Override
    public void beforeSession(TestSession session) {
        super.beforeSession(session);
        // we start video recording on this node
        HttpPost r = new HttpPost(serviceUrl + "?command=start");
        try {
            HttpResponse response = client.execute(remoteHost, r);
            if(response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                log.warning("Could not start video reporting: " + EntityUtils.toString(response.getEntity()));
                return;
            }
            log.info("Started recording for new session on node: " + getId());

        } catch (Exception e) {
            log.warning("Could not start video reporting due to exception: " + e.getMessage());
            e.printStackTrace();
        }
        finally {
            r.releaseConnection();
        }
    }

    public static final String REQUEST_PARAM_TEST_NAME = "webtests_test_name";

    @Override
    public void afterSession(TestSession session) {
        super.afterSession(session);

        ExternalSessionKey externalKey = session.getExternalKey();
        if (externalKey!=null) {
            String sessionId = externalKey.getKey();
            log.info("Stopping Video Recording and tagging session " + sessionId);

            String testName = (String)session.getRequestedCapabilities().get(REQUEST_PARAM_TEST_NAME);
            if (testName == null) {
                testName = sessionId;
            }
            HttpPost r;
            try {
                r = new HttpPost(serviceUrl + "?command=stop&sessionId=" + sessionId +
                                "&" + REQUEST_PARAM_TEST_NAME + "=" + URLEncoder.encode(testName, "utf-8"));
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
            try {
                HttpResponse response = client.execute(remoteHost, r);
                if(response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                    log.warning("Could not stop video : " + EntityUtils.toString(response.getEntity()));
                    return;
                }
                log.info("Stopped recording on node: " + getId() + " for session:" + sessionId);

            } catch (Exception e) {
                log.warning("Could not stop video reporting due to exception: " + e.getMessage());
                e.printStackTrace();
            }
            finally {
                r.releaseConnection();
            }

            FrontEndServlet.SESSIONS_AND_URLS.put(sessionId, serviceUrl);
        }

    }

}
