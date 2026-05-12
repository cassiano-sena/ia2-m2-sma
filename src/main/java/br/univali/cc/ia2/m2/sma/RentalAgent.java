package br.univali.cc.ia2.m2.sma;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

public class RentalAgent extends Agent {

    private AID consumerRequester;

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
                            + " recebeu: "
                            + msg.getContent()
                    );

                    switch (msg.getPerformative()) {
                        case ACLMessage.REQUEST:

                            if (msg.getSender().getLocalName().equals("consumer")) {

                                consumerRequester = msg.getSender();

                                ACLMessage transportRequest =
                                        new ACLMessage(ACLMessage.REQUEST);

                                transportRequest.addReceiver(
                                        new AID("transport", AID.ISLOCALNAME)
                                );

                                transportRequest.setContent(
                                        "deliver generator"
                                );

                                send(transportRequest);

                                System.out.println(
                                        getLocalName()
                                        + " solicitou transporte."
                                );
                            }

                            break;

                        case ACLMessage.PROPOSE:

                            String transportInfo = msg.getContent();

                            ACLMessage success =
                                    new ACLMessage(ACLMessage.INFORM);

                            success.addReceiver(consumerRequester);

                            success.setContent(
                                    "Generator confirmed | "
                                    + transportInfo
                                    + " | total price=1300"
                            );

                            send(success);

                            System.out.println(
                                    getLocalName()
                                    + " confirmou serviço ao consumidor."
                            );

                            break;

                        case ACLMessage.REFUSE:

                            ACLMessage failure =
                                    new ACLMessage(ACLMessage.FAILURE);

                            failure.addReceiver(consumerRequester);

                            failure.setContent(
                                    "Could not schedule transport."
                            );

                            send(failure);

                            System.out.println(
                                    getLocalName()
                                    + " falhou ao contratar transporte."
                            );

                            break;
                    }

                } else {
                    block();
                }
            }
        });
    }
}