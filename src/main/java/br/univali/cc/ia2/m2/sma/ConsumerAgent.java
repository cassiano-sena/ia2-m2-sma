package br.univali.cc.ia2.m2.sma;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.Random;

public class ConsumerAgent extends Agent {

    private Random random;
    private int requestCounter = 0;

    // O quão urgente é a necessidade do consumidor (impacta o tempo de espera)
    private int urgency; // 1 = baixa, 2 = média, 3 = alta

    @Override
    protected void setup() {
        System.out.println("[" + getLocalName() + "] Agente consumidor iniciado. Pronto para alugar geradores.");

        // Seed opcional para diferenciar consumidores quando houver muitos.
        long seed = System.nanoTime();
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            try {
                seed = Long.parseLong(args[0].toString());
            } catch (Exception ignored) {
                // mantém seed padrão
            }
        }
        random = new Random(seed);

        addBehaviour(new CyclicBehaviour() {

            @Override
            public void onStart() {
                fazerDecisaoEEnviar();
            }

            @Override
            public void action() {

                ACLMessage msg = receive();

                if (msg != null) {

                    if (msg.getPerformative() == ACLMessage.INFORM) {

                        System.out.println("[" + getLocalName() + "] Pedido #" + (requestCounter - 1)
                                + " CONFIRMADO. Detalhes: " + msg.getContent());
                        System.out.println("[" + getLocalName() + "] Vou aguardar o serviço e depois farei uma nova solicitação.");

                    // Requisição atendida: aguarda um intervalo normal antes de pedir de novo
                    int waitMs = 3000 + random.nextInt(3000);
                    myAgent.doWait(waitMs);

                    } else if (msg.getPerformative() == ACLMessage.FAILURE) {

                        System.out.println("[" + getLocalName() + "] Pedido #" + (requestCounter - 1)
                                + " FALHOU. Motivo: " + msg.getContent());
                        System.out.println("[" + getLocalName() + "] Transporte indisponível. Vou tentar novamente em breve.");

                        // Falhou: tenta novamente mais rápido
                        int waitMs = 1000 + random.nextInt(1500);
                        myAgent.doWait(waitMs);
                    }

                    fazerDecisaoEEnviar();

                } else {
                    block();
                }
            }

            private void fazerDecisaoEEnviar() {

                // O consumidor decide sua própria necessidade de forma autônoma
                urgency = 1 + random.nextInt(3);
                int load = switch (urgency) {
                    case 1 -> 2000 + random.nextInt(2000);   // baixa:   2000–3999 W
                    case 2 -> 4000 + random.nextInt(3000);   // média:   4000–6999 W
                    default -> 7000 + random.nextInt(3001);  // alta:    7000–10000 W
                };
                int duration = 1 + random.nextInt(12); // 1–12 h
                int maxBudget = 500 + (urgency * 300) + random.nextInt(400);

                String urgencyLabel = switch (urgency) {
                    case 1 -> "baixa";
                    case 2 -> "média";
                    default -> "ALTA";
                };

                System.out.println("[" + getLocalName() + "] Decisão autônoma: necessidade " + urgencyLabel
                        + " — preciso de um gerador de " + load + "W por " + duration
                        + "h (orçamento máx: R$" + maxBudget + ").");

                int pedidoNumero = requestCounter;
                String convId = getLocalName() + "-req-" + pedidoNumero;

                ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                request.addReceiver(new AID("rental", AID.ISLOCALNAME));
                request.setConversationId(convId);
                request.setContent("id=" + pedidoNumero
                        + ";consumer=" + getLocalName()
                        + ";load=" + load
                        + ";duration=" + duration
                        + ";budget=" + maxBudget);

                send(request);

                System.out.println("[" + getLocalName() + "] Solicitação #" + pedidoNumero
                        + " (conv=" + convId + ") enviada para [rental].");
                requestCounter++;
            }
        });
    }
}
