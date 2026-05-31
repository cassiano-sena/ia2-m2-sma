package br.univali.cc.ia2.m2.sma.gui;

import jade.lang.acl.ACLMessage;
import java.time.Instant;

/**
 * Immutable event POJO representing an ACLMessage observed by the VisualizerAgent.
 * Passed through the thread-safe MessageQueue to the JavaFX UI.
 */
public final class AgentMessageEvent {

    private final String sender;
    private final String receiver;
    private final int performative;
    private final String performativeName;
    private final String content;
    private final String conversationId;
    private final Instant timestamp;

    public AgentMessageEvent(String sender, String receiver, ACLMessage msg) {
        this.sender = sender;
        this.receiver = receiver;
        this.performative = msg.getPerformative();
        this.performativeName = ACLMessage.getPerformative(msg.getPerformative());
        this.content = msg.getContent();
        this.conversationId = msg.getConversationId();
        this.timestamp = Instant.now();
    }

    public String getSender() { return sender; }
    public String getReceiver() { return receiver; }
    public int getPerformative() { return performative; }
    public String getPerformativeName() { return performativeName; }
    public String getContent() { return content; }
    public String getConversationId() { return conversationId; }
    public Instant getTimestamp() { return timestamp; }

    /** Returns "consumer", "rental", or "transport" based on agent name prefix. */
    public String getSenderType() { return agentType(sender); }
    public String getReceiverType() { return agentType(receiver); }

    private static String agentType(String name) {
        if (name == null) return "unknown";
        if (name.equals("rental")) return "rental";
        if (name.startsWith("transport")) return "transport";
        if (name.startsWith("consumer")) return "consumer";
        return "unknown";
    }
}