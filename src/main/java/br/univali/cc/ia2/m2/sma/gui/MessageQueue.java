package br.univali.cc.ia2.m2.sma.gui;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe singleton queue bridging the JADE VisualizerAgent (producer)
 * and the JavaFX AnimationTimer (consumer).
 */
public final class MessageQueue {

    private static final BlockingQueue<AgentMessageEvent> INSTANCE =
            new LinkedBlockingQueue<>(1024);

    private MessageQueue() {}

    /** Called from JADE agent thread — blocks if queue is full. */
    public static void offer(AgentMessageEvent event) {
        try {
            INSTANCE.put(event);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Called from JavaFX thread — non-blocking, returns null if empty. */
    public static AgentMessageEvent poll() {
        return INSTANCE.poll();
    }

    /** Called from JavaFX thread — blocks up to timeout waiting for an event. */
    public static AgentMessageEvent poll(long timeout, TimeUnit unit) {
        try {
            return INSTANCE.poll(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /** Drain all pending events (for flush on stop). */
    public static void drainAll(java.util.function.Consumer<AgentMessageEvent> consumer) {
        java.util.List<AgentMessageEvent> drained = new java.util.ArrayList<>();
        INSTANCE.drainTo(drained);
        for (AgentMessageEvent ev : drained) {
            consumer.accept(ev);
        }
    }

    /** Clear all queued events. */
    public static void clear() {
        INSTANCE.clear();
    }
}