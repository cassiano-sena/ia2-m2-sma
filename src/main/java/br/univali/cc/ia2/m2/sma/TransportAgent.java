package br.univali.cc.ia2.m2.sma;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.Random;

public class TransportAgent extends Agent {

    private final Random random = new Random();

    @Override
    protected void setup() {

        System.out.println(getLocalName() + " iniciado.");

        addBehaviour(new CyclicBehaviour() {

            @Override
            public void action() {

                ACLMessage msg = receive();

                if (msg != null) {

                    System.out.println(
                            getLocalName()
                            + " recebeu pedido de transporte."
                    );

                    boolean available = random.nextInt(100) < 70;

                    ACLMessage reply = msg.createReply();

                    if (available) {

                        reply.setPerformative(ACLMessage.PROPOSE);

                        reply.setContent(
                                "transport price=300;time=2h"
                        );

                        System.out.println(
                                getLocalName()
                                + " aceitou transporte."
                        );

                    } else {

                        reply.setPerformative(ACLMessage.REFUSE);

                        reply.setContent(
                                "transport unavailable"
                        );

                        System.out.println(
                                getLocalName()
                                + " recusou transporte."
                        );
                    }

                    send(reply);

                } else {
                    block();
                }
            }
        });
    }
}