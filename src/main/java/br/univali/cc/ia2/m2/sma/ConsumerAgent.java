package br.univali.cc.ia2.m2.sma;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

public class ConsumerAgent extends Agent {

    @Override
    protected void setup() {

        System.out.println(getLocalName() + " iniciado.");

        ACLMessage request = new ACLMessage(ACLMessage.REQUEST);

        request.addReceiver(new AID("rental", AID.ISLOCALNAME));

        request.setContent("load=5000;duration=5");

        send(request);

        System.out.println(getLocalName() + " enviou solicitação.");

        addBehaviour(new CyclicBehaviour() {

            @Override
            public void action() {

                ACLMessage msg = receive();

                if (msg != null) {

                    System.out.println(
                            getLocalName()
                            + " recebeu resposta: "
                            + msg.getContent()
                    );

                } else {
                    block();
                }
            }
        });
    }
}