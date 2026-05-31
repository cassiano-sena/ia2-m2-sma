package br.univali.cc.ia2.m2.sma;

import jade.core.Agent;
import jade.lang.acl.ACLMessage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Lightweight message bus decoupled from the GUI package.
 * Allows any component (e.g. VisualizerAgent) to subscribe to sent ACLMessages
 * without the core agents having GUI dependencies.
 *
 * Core agents call {@code JadeMessageBus.get().onSend(...)} after each {@code send()}.
 */
public final class JadeMessageBus {

    private static final JadeMessageBus INSTANCE = new JadeMessageBus();
    private final CopyOnWriteArrayList<Consumer<ACLMessage>> subscribers = new CopyOnWriteArrayList<>();

    private JadeMessageBus() {}

    public static JadeMessageBus get() { return INSTANCE; }

    /** Subscribe to all sent ACLMessages. Pass {@code null} to unsubscribe. */
    public void subscribe(Consumer<ACLMessage> callback) {
        if (callback != null) {
            subscribers.add(callback);
        }
    }

    public void unsubscribe(Consumer<ACLMessage> callback) {
        subscribers.remove(callback);
    }

    /** Called by agents after each send(). */
    public void onSend(ACLMessage msg) {
        for (Consumer<ACLMessage> sub : subscribers) {
            sub.accept(msg);
        }
    }

    /** Convenience: calls agent.send(msg) then publishes to bus. */
    public void sendAndPublish(Agent agent, ACLMessage msg) {
        agent.send(msg);
        onSend(msg);
    }
}