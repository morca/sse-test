package sse;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.glassfish.jersey.media.sse.EventOutput;
import org.glassfish.jersey.media.sse.OutboundEvent;
import org.glassfish.jersey.media.sse.OutboundEvent.Builder;
import org.glassfish.jersey.media.sse.SseFeature;
import org.springframework.stereotype.Component;

@Component
@Path("/")
public class Endpoint {

    private final static ExecutorService executorService = Executors.newScheduledThreadPool(10);

	@GET
    @Path("long")
    public String longResponse(@QueryParam("length") @DefaultValue("100") int length) {
    	StringBuilder sb = new StringBuilder();
    	while (sb.length() < length) {
    		if (sb.length() == 0) {
    			sb.append("0123456789");
    		} else {
    			sb.append(sb);
    		}
    	}
    	if (sb.length() > length) {
    		return sb.substring(0, length);
    	}
    	return sb.toString();
    }
    
    @Produces(SseFeature.SERVER_SENT_EVENTS)
    @GET
    @Path("sse")
    public EventOutput events(@QueryParam("delay") @DefaultValue("1000") long delay, @QueryParam("count") @DefaultValue("10") int count) {
        final EventOutput eventOutput = new EventOutput();
        executorService.submit(new SseRunnable(eventOutput, delay, count));
        return eventOutput;
    }
    
    static class SseRunnable implements Runnable {
    	
    	private final EventOutput output;
		private final long sleep;
		private final int count;

		public SseRunnable(EventOutput output, long sleep, int count) {
			this.output = output;
			this.sleep = sleep;
			this.count = count;
    	}

		@Override
		public void run() {
			try {
				for (int i = 1; i <= count; i++) {
			        final Builder builder = new OutboundEvent.Builder();
			        builder.mediaType(MediaType.APPLICATION_JSON_TYPE);
			        builder.name("test");
			        builder.data("{\"event\":" + i + "}");
			        try {
			        	this.output.write(builder.build());
						Thread.sleep(sleep);
					} catch (Exception e) {
						System.err.println(e.toString());
//						throw new RuntimeException(e);
						break;
					}
				}
			} finally {
				try {
					this.output.close();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}
    	
    }
	
}
