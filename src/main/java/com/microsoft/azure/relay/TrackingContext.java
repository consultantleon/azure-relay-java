package com.microsoft.azure.relay;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public class TrackingContext {
	private static final UUID UUID_ZERO = new UUID(0L, 0L); // equivalent of Guid.Empty in C#
	private static final int UUID_STRING_LENGTH = UUID_ZERO.toString().length();
	private static final String TRACKING_ID_NAME = "TrackingId";
	private static final String ADDRESS_NAME = "Address";
	private static final String TIMESTAMP_NAME = "Timestamp";
	private String cachedToString;
	private final UUID activityId;
	private final String trackingId;
	private final String address;

	private TrackingContext(UUID activityId, String trackingId, String address) {
		this.activityId = activityId;
		this.trackingId = trackingId;
		this.address = address;
	}

	public UUID getActivityId() {
		return this.activityId;
	}

	public String getTrackingId() {
		return this.trackingId;
	}

	public String getAddress() {
		return this.address;
	}

	static TrackingContext create() {
		return create(UUID.randomUUID(), (String) null);
	}

	static TrackingContext create(URI address) {
		return create(UUID.randomUUID(), HybridConnectionUtil.getAudience(address));
	}

	static TrackingContext create(UUID activityId, String address) {
		return create(activityId, activityId.toString(), address);
	}

	static TrackingContext create(String trackingId, URI address) {
		return create(trackingId, HybridConnectionUtil.getAudience(address));
	}

	static TrackingContext create(String trackingId, String address) {
		boolean parseFailed = false;
		UUID activityId = UUID.fromString(trackingId.substring(0, Math.min(UUID_STRING_LENGTH, trackingId.length())));

		if (activityId.equals(UUID_ZERO)) {
			parseFailed = true;
			activityId = UUID.randomUUID();
		}

		TrackingContext trackingContext = create(activityId, trackingId, address);
		if (parseFailed) {
			RelayLogger.logEvent("parsingUUIDFailed", trackingContext, trackingId, activityId.toString());
		}

		return trackingContext;
	}

	static TrackingContext create(UUID activityId, URI address) {
		return create(activityId, activityId.toString(), address);
	}

	static TrackingContext create(UUID activityId, String trackingId, URI address) {
		return create(activityId, trackingId, address.toString());
	}

	static TrackingContext create(UUID activityId, String trackingId, String address) {
		return new TrackingContext(activityId, trackingId, address);
	}

	/**
	 * Given a trackingId String with "_GXX" suffix remove that suffix.
	 * Example: "1c048eb5-77c4-4b85-96fd-fa526801af35_G0" becomes "1c048eb5-77c4-4b85-96fd-fa526801af35"
	 * @param trackingId The trackId to be trimmed
	 * @return The new trackId with trimmed suffix
	 */
	static String removeSuffix(String trackingId) {
		int roleSuffixIndex = trackingId.indexOf("_");

		if (roleSuffixIndex == -1) {
			return trackingId;
		}

		return trackingId.substring(0, roleSuffixIndex);
	}

	/**
	 * Returns a String that represents the current object including the trackingId and the address if exists
	 */
	@Override
	public String toString() {
		if (this.cachedToString == null) {
			if (StringUtil.isNullOrEmpty(this.address)) {
				this.cachedToString = TRACKING_ID_NAME + ":" + this.trackingId;
			} else {
				this.cachedToString = TRACKING_ID_NAME + ":" + this.trackingId + ", " + ADDRESS_NAME + ":"
						+ this.address;
			}
		}

		return this.cachedToString;
	}

	/**
	 * Ensures the given String contains a TrackingId. If one is already present, nothing occurs.
	 * Otherwise TrackingId, Timestamp, and if present, SystemTracker are added.
	 * @param exceptionMessage
	 * @return
	 */
	String ensureTrackableMessage(String exceptionMessage) {
		if (StringUtil.isNullOrEmpty(exceptionMessage) || exceptionMessage.indexOf(TRACKING_ID_NAME) == -1) {
			// Ensure there's a period so we don't get a run-on sentence such as "An error
			// occurred TrackingId:foo"
			if (!StringUtil.isNullOrEmpty(exceptionMessage) && !exceptionMessage.endsWith(".")) {
				exceptionMessage += ".";
			}

			return exceptionMessage + " " + this.createClientTrackingExceptionInfo();
		}

		return exceptionMessage;
	}

	String createClientTrackingExceptionInfo() {
		return createClientTrackingExceptionInfo(Instant.now());
	}

	String createClientTrackingExceptionInfo(Instant timestamp) {
		return StringUtil.isNullOrWhiteSpace(this.address)
				? TRACKING_ID_NAME + ":" + this.trackingId + ", " + TIMESTAMP_NAME + ":" + timestamp
				: TRACKING_ID_NAME + ":" + this.trackingId + ", " + ADDRESS_NAME + ":" + this.address + ", "
						+ TIMESTAMP_NAME + ":" + timestamp;
	}
}
