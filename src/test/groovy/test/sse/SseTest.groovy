package test.sse

import javax.ws.rs.client.*
import java.util.concurrent.*

import groovy.util.logging.*
import groovyx.net.http.*
import org.apache.http.impl.client.*
import org.glassfish.jersey.media.sse.*
import org.junit.*
import org.junit.runners.*

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SseTest {

    RESTClient getClient(url) {
        HttpClientBuilder.create().disableAutomaticRetries().build();
        RESTClient restClient = new RESTClient(url)
        if (restClient.client instanceof AbstractHttpClient) {
            ((AbstractHttpClient) restClient.client).setHttpRequestRetryHandler(new DefaultHttpRequestRetryHandler(0, false))
        }
        println restClient.client.class
        restClient.ignoreSSLIssues()
        restClient.encoder.charset = "UTF-8"
        restClient.headers['Accept-Language'] = 'de'
        return restClient;
    }

    @Test
    void t1_testSimpleRequest() throws Exception {
        new SimpleRequest("one", "http://localhost:8080", "/long").call()
    }

    @Test
    public void t4_testManySimpleRequests() throws Exception {
        def results = []
        ExecutorService executorService = Executors.newCachedThreadPool()
        for (int i = 0; i < 20; i++) {
            results << executorService.submit(new SimpleRequest("cient:" + i, "http://localhost:8080", "/long", ["length": "20"]))
        }
        results.each { it.get() }
    }

    @Test
    void t2_testSimpleSseRequest() throws Exception {
        new SimpleRequest("one", "http://localhost:8080", "/sse", ["delay": "10", "count": "10"]).call()
    }

    @Test
    public void t5_testManySimpleSseRequests() throws Exception {
        def results = []
        ExecutorService executorService = Executors.newCachedThreadPool()
        for (int i = 0; i < 20; i++) {
            results << executorService.submit(new SimpleRequest("cient:" + i, "http://localhost:8080", "/sse", ["delay": "10", "count": "10"]))
        }
        results.each { it.get() }
    }

    @Slf4j
    class SimpleRequest implements Callable<Void> {

        String clientName
        String baseurl
        String path
        def query = [:]

        SimpleRequest(clientName, baseurl, path, query = [:]) {
            this.clientName = clientName
            this.baseurl = baseurl
            this.path = path
            this.query = query
        }

        @Override
        Void call() throws Exception {
            RESTClient client = getClient(baseurl)
            client.get(
                    path: path,
                    query: query

            ) { resp, reader ->
                System.out << reader
            }
            return null
        }
    }

    @Test
    public void t3_testWithOneEventsListener() throws Exception {
        new SseListener("one", "http://localhost:8080/sse?delay=100", "*/*", 5, null).call()
    }

    @Test
    public void t6_testWithManyEventsListeners() throws Exception {
        def latch = new CountDownLatch(1)
        def results = []
        ExecutorService executorService = Executors.newCachedThreadPool()
        for (int i = 0; i < 30; i++) {
            results << executorService.submit(new SseListener("client " + i, "http://localhost:8080/sse?delay=100", "*/*", 5, latch))
        }
        latch.countDown()
        results.each { it.get() }
    }

    @Slf4j
    class SseListener implements Callable<Void> {
        Client client
        WebTarget target
        EventInput eventInput
        String clientName
        String eventsUrl
        String mediaType
        int closeAfterNumberOfEvents
        int eventsCount = 0
        CountDownLatch latch

        SseListener(clientName, eventsUrl, mediaType, int closeAfterNumberOfEvents, CountDownLatch latch) {
            this.clientName = clientName
            this.mediaType = mediaType
            this.closeAfterNumberOfEvents = closeAfterNumberOfEvents
            this.eventsUrl = eventsUrl
            this.latch = latch
            client = ClientBuilder.newBuilder().build()
        }

        @Override
        Void call() throws Exception {
            latch?.await()
            try {
                log.info("client <" + clientName + "> starts here")
                target = client.target(eventsUrl).queryParam("mediaType", mediaType);
                eventInput = target.request().get(EventInput.class)
                while (!eventInput.isClosed() && !Thread.currentThread().isInterrupted()) {
                    final InboundEvent inboundEvent = eventInput.read();
                    if (inboundEvent == null) {
                        // connection has been closed
                        break;
                    }
                    process(inboundEvent)
                }
                log.info("client <" + clientName + "> ends here")
                return null
            } catch (Exception e) {
                log.info("client <" + clientName + "> ends with exception", e)
                throw e
            }
        }

        protected process(InboundEvent inboundEvent) {
            eventsCount++
            if (eventsCount >= closeAfterNumberOfEvents) {
                eventInput.close()
            }
        }

    }
}
