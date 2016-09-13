package test.sse

import javax.ws.rs.client.Client
import javax.ws.rs.client.ClientBuilder
import javax.ws.rs.client.WebTarget
import java.util.concurrent.CountDownLatch

import groovy.util.logging.Slf4j
import groovyx.net.http.*
import org.glassfish.jersey.media.sse.EventInput
import org.glassfish.jersey.media.sse.InboundEvent
import org.junit.*

class SseTest {

    static Random random = new Random()

    RESTClient getClient(url) {
        RESTClient restClient = new RESTClient(url)
        restClient.ignoreSSLIssues()
        restClient.encoder.charset = "UTF-8"
		restClient.headers['Accept-Language'] = 'de'
        return restClient;
    }

    @Test
    void testSimpleRequest() {
        def thread = new Thread(new SimpleRquest("one", "http://localhost:8080", "/long"))
        thread.start()
        thread.join()
    }

    @Test
    public void testManySimpleRequests() throws Exception {
        def allThreads = []
        for (int i = 0; i < 20; i++) {
            def thread = new Thread(new SimpleRquest("cient:" + i, "http://localhost:8080", "/long", ["length": "20"]))
            allThreads << thread
            thread.start()
        }
        allThreads.each {it.join()}
    }

    @Test
    void testSimpleSseRequest() {
        def thread = new Thread(new SimpleRquest("one", "http://localhost:8080", "/sse"))
        thread.start()
        thread.join()
    }

    @Test
    public void testManySimpleSseRequests() throws Exception {
        def allThreads = []
        for (int i = 0; i < 20; i++) {
            def thread = new Thread(new SimpleRquest("cient:" + i, "http://localhost:8080", "/sse", ["delay": "1", "count": "10"]))
            allThreads << thread
            thread.start()
        }
        allThreads.each {it.join()}

    }

    class SimpleRquest implements  Runnable {

        String clientName
        String baseurl
        String path
        def query = [:]

        SimpleRquest(clientName, baseurl, path, query = [:]) {
            this.clientName = clientName
            this.baseurl = baseurl
            this.path = path
            this.query = query
        }

        @Override
        void run() {
            RESTClient client = getClient(baseurl)
            client.get(
                    path: path,
                    query: query

            ) { resp, reader ->
                System.out << reader
            }
        }
    }
    @Test
    public void testWithOneEventsListener() throws Exception {
        EditorEventsListener eel = new EditorEventsListener("one", "http://localhost:8080/sse?delay=100", "*/*", 5, null)
        def thread = new Thread(eel)
        thread.start()
        thread.join()
    }

    @Test
    public void testWithManyEventsListeners() throws Exception {
        def latch = new CountDownLatch(1)
        latch.countDown()
        def allThreads = []
        for (int i = 0; i < 20; i++) {
            EditorEventsListener eel = new EditorEventsListener("client " + i, "http://localhost:8080/sse?delay=100", "*/*", 5, latch)
            def thread = new Thread(eel)
            thread.start()
            allThreads << thread
        }

        allThreads.each {it.join() }
    }

    @Slf4j
    class EditorEventsListener implements Runnable {
        Client client
        WebTarget target
        EventInput eventInput
        String clientName
        String eventsUrl
        String mediaType
        int closeAfterNumberOfEvents
        int eventsCount = 0
        CountDownLatch latch

        EditorEventsListener(clientName, eventsUrl, mediaType, int closeAfterNumberOfEvents, CountDownLatch latch) {
            this.clientName = clientName
            this.mediaType = mediaType
            this.closeAfterNumberOfEvents = closeAfterNumberOfEvents
            this.eventsUrl = eventsUrl
            this.latch = latch
            client = ClientBuilder.newBuilder().build()
        }

        @Override
        void run() {
            latch != null && latch.await()
            Thread.sleep(random.nextInt(100))
            try {
                log.info("client <" + clientName + "> starts here")
                target= client.target(eventsUrl).queryParam("mediaType", mediaType);
                eventInput = target.request().get(EventInput.class)
                while (!eventInput.isClosed() && !Thread.currentThread().isInterrupted()) {
                    final InboundEvent inboundEvent = eventInput.read();
                    if (inboundEvent == null) {
                        // connection has been closed
                        break;
                    }
                    process(inboundEvent)
                }
            } catch (Exception e) {
                log.info("client <" + clientName + "> ends with exception", e)
            }
        }

        protected process(InboundEvent inboundEvent) {
            eventsCount++
            if (eventsCount >= closeAfterNumberOfEvents) {
                eventInput.close()
                log.info("client <" + clientName + "> ends here")
            }
        }

    }
}
