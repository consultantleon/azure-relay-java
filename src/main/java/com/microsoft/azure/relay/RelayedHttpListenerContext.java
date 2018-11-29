package com.microsoft.azure.relay;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.websocket.client.WebSocketClient;

public class RelayedHttpListenerContext {
    private static final Duration ACCEPT_TIMEOUT = Duration.ofSeconds(20);
    private String cachedToString;
    private RelayedHttpListenerRequest request;
    private RelayedHttpListenerResponse response;
    private TrackingContext trackingContext;
    private HybridConnectionListener listener;
    
    
    public RelayedHttpListenerRequest getRequest() {
		return request;
	}

	public RelayedHttpListenerResponse getResponse() {
		return response;
	}

	public TrackingContext getTrackingContext() {
		return trackingContext;
	}

	protected HybridConnectionListener getListener() {
		return listener;
	}

	protected RelayedHttpListenerContext(HybridConnectionListener listener, URI requestUri, String trackingId, String method, Map<String, String> requestHeaders)
    {
        this.listener = listener;
        this.trackingContext = TrackingContext.create(trackingId, requestUri);
        this.request = new RelayedHttpListenerRequest(requestUri, method, requestHeaders);
        this.response = new RelayedHttpListenerResponse(this);

        this.FlowSubProtocol();
    }

    /// <summary>
    /// Returns a String that represents the current object. Includes a TrackingId for end to end correlation.
    /// </summary>
	@Override
    public String toString() {
		if (this.cachedToString != null) {
			return this.cachedToString;
		} else {
			return this.cachedToString = "RelayedHttpListenerContext" + "(" + this.trackingContext + ")";
		}
    }

    protected CompletableFuture<ClientWebSocket> acceptAsync(URI rendezvousUri) throws IOException
    {
        // Performance: Address Resolution (ARP) work-around: When we receive the control message from a TCP connection which hasn't had any
        // outbound traffic for 2 minutes the ARP cache no longer has the MAC address required to ACK the control message.  If we also begin
        // connecting a new socket at exactly the same time there's a known race condition (insert link here) where ARP can only resolve one
        // address at a time, which causes the loser of the race to have to retry after 3000ms.  To avoid the 3000ms delay we just pause for
        // a few ms here instead.
        try {
			Thread.sleep(2);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

        // TODO: subprotocol
        // If we are accepting a sub-protocol handle that here
//        String subProtocol = this.response.getHeaders().get(HybridConnectionConstants.Headers.SEC_WEBSOCKET_PROTOCOL);
//        if (!StringUtil.isNullOrEmpty(subProtocol)) {
//            clientWebSocket.Options.AddSubProtocol(subProtocol);
//        }
        
        ClientWebSocket webSocket = new ClientWebSocket();
        return TimedCompletableFuture.timedSupplyAsync(ACCEPT_TIMEOUT, () -> {
			webSocket.connectAsync(rendezvousUri);
			return webSocket;
		});
    }

    protected CompletableFuture<Void> rejectAsync(URI rendezvousUri) throws URISyntaxException, UnsupportedEncodingException, DeploymentException {
    	if (this.response.getStatusCode() == HttpStatus.CONTINUE_100) {
    		this.response.setStatusCode(HttpStatus.BAD_REQUEST_400);
    		this.response.setStatusDescription("Rejected by user code");
    	}
    	
        StringBuilder builder = new StringBuilder(rendezvousUri.toString());
        builder.append("&")
        	.append(HybridConnectionConstants.STATUS_CODE)
        	.append("=")
        	.append(this.response.getStatusCode());
        builder.append("&")
        	.append(HybridConnectionConstants.STATUS_DESCRIPTION)
        	.append("=")
        	.append(URLEncoder.encode(this.response.getStatusDescription(), StringUtil.UTF8.name()));
    	URI rejectURI = new URI(builder.toString());
    	
    	ClientWebSocket webSocket = new ClientWebSocket();
    	
        return TimedCompletableFuture.timedRunAsync(ACCEPT_TIMEOUT, () -> {
			try {
				webSocket.connectAsync(rejectURI);
				// TODO: exception
//	            WebException webException;
//	            HttpWebResponse httpWebResponse;
//	            if (e is WebSocketException &&
//	                (webException = e.InnerException as WebException) != null &&
//	                (httpWebResponse = webException.Response as HttpWebResponse) != null && 
//	                httpWebResponse.StatusCode == HttpStatusCode.Gone)
//	            {
//	                // status code of "Gone" is expected when rejecting a client request
//	                return;
//	            }
//	            RelayEventSource.Log.HandledExceptionAsWarning(this, e);
			}
	        finally {
	            if (webSocket.getSession() != null) {
	            	try {
	            		webSocket.getSession().close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
	            };
	        }
        });
    }

    private WebSocketContainer createWebSocket() {
    	WebSocketContainer container = ContainerProvider.getWebSocketContainer();
//    	container.setDefaultMaxBinaryMessageBufferSize(this.listener.getConnectionBufferSize());
//    	container.setDefaultMaxTextMessageBufferSize(this.listener.getConnectionBufferSize());
    	container.setDefaultMaxSessionIdleTimeout(HybridConnectionConstants.KEEP_ALIVE_INTERVAL.toMillis());
    	return container;

        // TODO: proxy
//      clientWebSocket.Options.Proxy = this.Listener.Proxy;
    }

    private void FlowSubProtocol() {
        // By default use the first sub-protocol (if present)
        String subProtocol = this.request.getHeaders().get(HybridConnectionConstants.Headers.SEC_WEBSOCKET_PROTOCOL);
        
        if (!StringUtil.isNullOrEmpty(subProtocol)) {
        	
            int separatorIndex = subProtocol.indexOf(',');
            if (separatorIndex >= 0) {
                // more than one sub-protocol in headers, only use the first.
                subProtocol = subProtocol.substring(0, separatorIndex);
            }
            this.response.getHeaders().put(HybridConnectionConstants.Headers.SEC_WEBSOCKET_PROTOCOL, subProtocol);
        }
    }
}