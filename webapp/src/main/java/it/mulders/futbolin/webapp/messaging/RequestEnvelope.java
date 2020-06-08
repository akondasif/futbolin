package it.mulders.futbolin.webapp.messaging;

import lombok.AllArgsConstructor;

/**
 * Envelope for requests messages sent over a message bus.
 */
@AllArgsConstructor
public class RequestEnvelope {
    /** An unique identifier for the message. The response message will have the same correlationId, so they can be matched. */
    final String correlationId;
    /** Name of the queue where the consumer expects the response message to be published. */
    final String responseQueueName;
    /** Raw content of the message. */
    final byte[] message;

    /**
     * Convenience constructor for "fire-and-forget" message style, where the consumer does not expect a response
     * message.
     * @param message The bytes of the request message.
     */
    public RequestEnvelope(final byte[] message) {
        this(null, null, message);
    }
}
