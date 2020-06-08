package it.mulders.futbolin.webapp.messaging;

/**
 * Generic exception signalling that something went wrong in configuring messaging, publishing a message or receiving
 * one.
 */
public class MessagingException extends RuntimeException {
    public MessagingException(final Throwable cause) {
        super(cause);
    }
}
