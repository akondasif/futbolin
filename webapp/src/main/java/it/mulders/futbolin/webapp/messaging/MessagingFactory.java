package it.mulders.futbolin.webapp.messaging;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Inject;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Collections.emptyMap;

/**
 * Simple CDI-factory for RabbitMQ stuff. Might want to look into the Java Connector Architecture (JCA) one day.
 *
 * For every injection, it creates a new RabbitMQ {@link Channel}. It also keeps an administration of them so that the
 * channels and connections can be closed when the factory is shut down.
 *
 * The beans do not have a scope qualifier. As a result, produced beans have the default / dependent scope. This means
 * a new instance gets created at every injection point, and that instance lives as long as the object where it is
 * injected lives. This may not be the most efficient in terms of connection and resource management, but it prevents
 * {@link Channel} instances bein used over threads.
 */
@ApplicationScoped
@Slf4j
public class MessagingFactory {
    private static final boolean DURABLE = true;
    private static final boolean NO_AUTO_DELETE = false;
    private static final boolean NOT_EXCLUSIVE = false;

    private final ConnectionFactory connectionFactory = new ConnectionFactory();

    /**
     * Keeps track of all connections that have been opened so they can be properly closed when the application shuts
     * down.
     */
    private final Set<Connection> openedConnections = new HashSet<>();

    /**
     * Counter to generate unique connection identifiers.
     */
    private final AtomicLong connectionId = new AtomicLong(0);

    @Inject
    private MessagingConfig messagingConfig;

    @PostConstruct
    public void configure() {
        log.info("Configuring RabbitMQ connection using {}", messagingConfig);
        connectionFactory.setHost(messagingConfig.getHost());
        connectionFactory.setPort(messagingConfig.getPort());
        connectionFactory.setUsername(messagingConfig.getUsername());
        connectionFactory.setPassword(messagingConfig.getPassword());
    }

    @PreDestroy
    public void cleanup() {
        openedConnections.forEach(this::silentlyCloseConnection);
    }

    private void silentlyCloseConnection(final Connection connection) {
        try {
            if (connection.isOpen()) {
                log.info("Closing connection {}", connection.getId());
                connection.close();
            } else {
                log.debug("Connection {} is already closed", connection.getId());
            }
        } catch (IOException e) {
            log.error("Could not properly close connection {}", connection.getId(), e);
        }
    }

    /**
     * Helper method to open a RabbitMQ {@link Channel} using a new RabbitMQ {@link Connection}.
     * The connection is stored in {@link #openedConnections} so it can be properly closed when needed.
     * @return A fresh {@link Channel}.
     */
    private Channel createChannel() {
        try {
            var connection = connectionFactory.newConnection();
            connection.setId(Long.toString(connectionId.incrementAndGet()));
            openedConnections.add(connection);
            connection.addShutdownListener(cause -> {
                log.debug("Closing connection {} due to {}", connection.getId(), cause.getLocalizedMessage());
                openedConnections.remove(connection);
            });
            log.info("Opened RabbitMQ connection {}....", connection.getId());
            return connection.createChannel();
        } catch (TimeoutException | IOException e) {
            log.error("Could not open connection to RabbitMQ", e);
            throw new MessagingException(e);
        }
    }

    /**
     * Injects a {@link MessageSender} instance at the given {@code InjectionPoint}.
     * @param injectionPoint The injection point where the {@link MessageSender} is requested.
     * @return The {@link MessageSender} instance.
     */
    @NamedQueue(queueName = "")
    @Produces
    public MessageSender messageSender(final InjectionPoint injectionPoint) {
        return new DefaultMessageSender(queue(injectionPoint));
    }

    /**
     * Declare a named, non-exclusive, durable, permanent queue.
     * Useful for referring to request queues that may already exist and are potentially shared with other consumers.
     * @param injectionPoint The point where the {@link Queue} is going to be injected.
     * @return A {@link Queue} instance for the declared queue.
     */
    @NamedQueue(queueName = "")
    @Produces
    public Queue queue(final InjectionPoint injectionPoint) {
        var annotated = injectionPoint.getAnnotated();
        var usesQueue = annotated.getAnnotation(NamedQueue.class);
        var queueName = usesQueue.queueName();
        try {
            var channel = createChannel();
            // Declare the queue we want to send to.
            channel.queueDeclare(queueName, DURABLE, NOT_EXCLUSIVE, NO_AUTO_DELETE, emptyMap());

            return new Queue(channel, queueName);
        } catch (IOException e) {
            log.error("Could not construct an instance of Queue for queue {}", queueName, e);
            throw new MessagingException(e);
        }
    }

    /**
     * Declare a temporary (exclusive for this consumer, auto-deleted when the consumer disconnects, non-durable) queue.
     * Useful for creating response queues that are guaranteed to not conflict with other consumers.
     * @return A {@link Queue} instance for the declared queue.
     */
    @Produces
    @TemporaryQueue
    public Queue temporaryQueue() {
        try {
            var channel = createChannel();
            // Declare the temporary queue.
            var result = channel.queueDeclare();
            return new Queue(channel, result.getQueue());
        } catch (IOException e) {
            log.error("Could not declare a temporary queue", e);
            throw new MessagingException(e);
        }
    }
}
