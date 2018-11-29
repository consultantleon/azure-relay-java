package com.microsoft.azure.relay;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class TokenRenewer {
    private final HybridConnectionListener listener;
    private final String appliesTo;
    private final Duration tokenValidFor;
    private Timer renewTimer;
    private Object thisPtr = this;
    public Consumer<SecurityToken> onTokenRenewed;
	public Consumer<Exception> onTokenRenewException;
	
    public TokenRenewer(HybridConnectionListener listener, String appliesTo, Duration tokenValidFor)
    {
    	// TODO: fx
//        Fx.Assert(listener != null, "listener is required");
//        Fx.Assert(!StringUtil.isNullOrEmpty(appliesTo), "appliesTo is required");
        this.listener = listener;
        this.appliesTo = appliesTo;
        this.tokenValidFor = tokenValidFor;
        this.renewTimer = new Timer();
        
        // must create new TimerTask every time when tryin to schedule, because rescheduling same task is disllowed
		// subtract current millis so overflow doesn't happen
        this.renewTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				onRenewTimer(thisPtr);
			}
		}, Long.MAX_VALUE - System.currentTimeMillis(), Long.MAX_VALUE - System.currentTimeMillis());
    }
	
    public void setOnTokenRenewed(Consumer<SecurityToken> onTokenRenewed) {
		this.onTokenRenewed = onTokenRenewed;
	}

	public void setOnTokenRenewException(Consumer<Exception> onTokenRenewException) {
		this.onTokenRenewException = onTokenRenewException;
	}

    public CompletableFuture<SecurityToken> getTokenAsync()
    {
        return this.getTokenAsync(false);
    }

    private CompletableFuture<SecurityToken> getTokenAsync(boolean raiseTokenRenewedEvent)
    {
        try {
        	// TODO: trace
//            RelayEventSource.Log.GetTokenStart(this.listener);
            CompletableFuture<SecurityToken> tokenFuture = this.listener.getTokenProvider().getTokenAsync(this.appliesTo, this.tokenValidFor);
//            RelayEventSource.Log.GetTokenStop(this.listener, token.ExpiresAtUtc);

            if (raiseTokenRenewedEvent) {
                tokenFuture.thenAcceptAsync(onTokenRenewed);
            }

            this.scheduleRenewTimer(tokenFuture.get());
            return tokenFuture;
        }
        // TODO: fx isfatal
        catch (Exception e) {
            this.onTokenRenewException.accept(e);
            throw new RuntimeException();
        }
    }

    public void close() {
    	this.renewTimer.cancel();
    }
    
    static void onRenewTimer(Object state)
    {
    	TokenRenewer thisPtr = (TokenRenewer)state;
        try {
            CompletableFuture.runAsync(new Runnable() {
				@Override
				public void run() {
					thisPtr.getTokenAsync(true);
				}
			});
        }
        // TODO: fx isfatal
        catch (Exception exception) {
//            RelayEventSource.Log.HandledExceptionAsWarning(thisPtr.listener, exception);
        }
    }

    private void scheduleRenewTimer(SecurityToken token) {
        Duration interval = Duration.between(LocalDateTime.now(), token.getExpiresAtUtc());
        if (interval.isNegative()) {
            // TODO: RelayEventSource.Log.WcfEventWarning(Diagnostics.TraceCode.Security, this.traceSource, "Not renewing since " + interval + " < Duration.Zero!");
            return;
        }

        // TokenProvider won't return a token which is within 5min of expiring so we don't have to pad here.
        interval = interval.compareTo(RelayConstants.CLIENT_MINIMUM_TOKEN_REFRESH_INTERVAL) < 0 ? RelayConstants.CLIENT_MINIMUM_TOKEN_REFRESH_INTERVAL : interval;

        // TODO: trace
//        RelayEventSource.Log.TokenRenewScheduled(interval, this.listener);
        this.reschedule(interval.getSeconds() * 1000, Long.MAX_VALUE - System.currentTimeMillis());
    }
    
    private void reschedule(long delay, long period) {
    	this.renewTimer.cancel();
    	this.renewTimer = new Timer();
    	this.renewTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				onRenewTimer(thisPtr);
			}
		}, delay, period);
    }
}