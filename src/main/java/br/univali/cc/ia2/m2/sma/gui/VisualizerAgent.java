package br.univali.cc.ia2.m2.sma.gui;

import br.univali.cc.ia2.m2.sma.JadeMessageBus;
import jade.core.AID;
import jade.core.Agent;
import jade.lang.acl.ACLMessage;

/**
 * Subscribes to the shared JadeMessageBus and converts ACLMessages into
 * AgentMessageEvents pushed onto the MessageQueue for the JavaFX UI.
 */
public class VisualizerAgent extends Agent {

    @Override
    protected void setup() {
        System.out.println("[VisualizerAgent] Started — listening via JadeMessageBus.");
        JadeMessageBus.get().subscribe(this::onMessage);
    }

    @Override
    protected void takeDown() {
        JadeMessageBus.get().unsubscribe(this::onMessage);
        System.out.println("[VisualizerAgent] Stopped.");
    }

    private void onMessage(ACLMessage msg) {
        String sender = msg.getSender() != null ? msg.getSender().getLocalName() : "?";
        String receiver = "?";
        java.util.Iterator<AID> it = msg.getAllReceiver();
        if (it.hasNext()) {
            receiver = it.next().getLocalName();
        }
        AgentMessageEvent event = new AgentMessageEvent(sender, receiver, msg);
        MessageQueue.offer(event);
    }
}