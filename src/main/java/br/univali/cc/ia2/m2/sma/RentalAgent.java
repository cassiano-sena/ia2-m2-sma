package br.univali.cc.ia2.m2.sma;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class RentalAgent extends Agent {

    private static final int MAX_GENERATOR_LOAD = 10000; // W (capacidade máxima do gerador)
    private static final int PRICE_PER_HOUR = 80; // R$ por hora (taxa base)
    private static final int SURCHARGE_HIGH_LOAD = 50; // adicional R$/h acima de 6000W

    private final Random random = new Random();

    private int transportCount = 1;
    private String transportBaseName = "transport";
    private List<AID> transportAgents = new ArrayList<>();

    @Override
    protected void setup() {
        // args[0] (opcional): quantidade de agentes de transporte (transport0..transportN-1)
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            try {
                transportCount = Integer.parseInt(args[0].toString());
            } catch (Exception ignored) {
                // mantém padrão
            }
        }

        for (int i = 0; i < transportCount; i++) {
            transportAgents.add(new AID(transportBaseName + i, AID.ISLOCALNAME));
        }

        System.out.println("[" + getLocalName() + "] Agente de aluguel iniciado.");
        System.out.println("[" + getLocalName() + "] Capacidade máxima: " + MAX_GENERATOR_LOAD
                + "W | Transportes configurados: " + transportAgents.size());

        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                // Só tratamos mensagens de consumidores (REQUEST). As demais respostas ficam para os coordinators.
                ACLMessage msg = receive(MessageTemplate.MatchPerformative(ACLMessage.REQUEST));
                if (msg != null) {
                    handleConsumerRequest(msg);
                } else {
                    block();
                }
            }
        });
    }

    private void handleConsumerRequest(ACLMessage msg) {
        Map<String, String> params = parseContent(msg.getContent());

        String id = params.getOrDefault("id", "?");
        int load = Integer.parseInt(params.getOrDefault("load", "0"));
        int duration = Integer.parseInt(params.getOrDefault("duration", "1"));
        int clientBudget = Integer.parseInt(params.getOrDefault("budget", "9999"));
        String consumerName = msg.getSender().getLocalName();

        String convId = msg.getConversationId() != null ? msg.getConversationId() : (consumerName + "-req-" + id);

        System.out.println("[" + getLocalName() + "] Nova solicitação #" + id + " de [" + consumerName + "]: "
                + load + "W por " + duration + "h (orçamento máx: R$" + clientBudget + ").");

        // Decisão autônoma: conseguimos atender essa solicitação?
        if (load > MAX_GENERATOR_LOAD) {
            System.out.println("[" + getLocalName() + "] Decisão: carga " + load
                    + "W excede capacidade máxima. Recusando pedido #" + id + ".");
            ACLMessage refusal = new ACLMessage(ACLMessage.FAILURE);
            refusal.addReceiver(msg.getSender());
            refusal.setConversationId(convId);
            refusal.setContent("Carga solicitada (" + load + "W) excede capacidade do gerador.");
            send(refusal);
            return;
        }

        // Calcula o preço do aluguel (parte do aluguel sem o transporte)
        int basePrice = duration * PRICE_PER_HOUR;
        int surcharge = (load > 6000) ? duration * SURCHARGE_HIGH_LOAD : 0;
        int rentalPrice = basePrice + surcharge + random.nextInt(50); // variação pequena

        System.out.println("[" + getLocalName() + "] Decisão: pedido #" + id
                + " viável no aluguel. Aluguel calculado: R$" + rentalPrice + ".");

        // Se só o aluguel já estoura o orçamento, não faz sentido tentar transporte.
        if (rentalPrice > clientBudget) {
            System.out.println("[" + getLocalName() + "] Decisão: orçamento insuficiente para pedido #" + id
                    + ". Aluguel (R$" + rentalPrice + ") > orçamento (R$" + clientBudget + ").");
            ACLMessage refusal = new ACLMessage(ACLMessage.FAILURE);
            refusal.addReceiver(msg.getSender());
            refusal.setConversationId(convId);
            refusal.setContent("Orçamento insuficiente para o aluguel (R$" + rentalPrice + ").");
            send(refusal);
            return;
        }

        // Coordena a busca do transporte e confirma ao consumidor quando o transporte terminar.
        addBehaviour(new TransportCoordinatorBehaviour(
                msg.getSender(),
                convId,
                id,
                load,
                duration,
                rentalPrice,
                clientBudget
        ));
    }

    private class TransportCoordinatorBehaviour extends Behaviour {

        private final AID consumer;
        private final String convId;
        private final String pedidoId;
        private final int load;
        private final int duration;
        private final int rentalPrice;
        private final int clientBudget;

        private boolean consultasEnviadas = false;
        private boolean aceito = false;
        private boolean finalizado = false;

        private AID melhorTransport = null;
        private int melhorTransportPrice = Integer.MAX_VALUE;
        private String melhorVeiculo = "?";
        private String melhorEta = "?";

        private long fimSelecaoMs;
        private long fimConclusaoMs;
        private int respostasRecebidas = 0;

        TransportCoordinatorBehaviour(AID consumer,
                                       String convId,
                                       String pedidoId,
                                       int load,
                                       int duration,
                                       int rentalPrice,
                                       int clientBudget) {
            this.consumer = consumer;
            this.convId = convId;
            this.pedidoId = pedidoId;
            this.load = load;
            this.duration = duration;
            this.rentalPrice = rentalPrice;
            this.clientBudget = clientBudget;
        }

        @Override
        public void action() {
            if (finalizado) return;

            if (!consultasEnviadas) {
                // Consulta disponibilidade aos transportes (ofertas).
                for (AID transport : transportAgents) {
                    ACLMessage inquiry = new ACLMessage(ACLMessage.REQUEST);
                    inquiry.addReceiver(transport);
                    inquiry.setConversationId(convId);
                    inquiry.setContent("consumer=" + consumer.getLocalName()
                            + ";load=" + load
                            + ";duration=" + duration);
                    send(inquiry);
                }

                consultasEnviadas = true;
                respostasRecebidas = 0;
                fimSelecaoMs = System.currentTimeMillis() + 2000; // janela para ofertas
                return;
            }

            MessageTemplate mtConv = MessageTemplate.MatchConversationId(convId);
            long agora = System.currentTimeMillis();

            if (!aceito) {
                ACLMessage resp = myAgent.receive(mtConv);

                if (resp != null) {
                    if (resp.getPerformative() == ACLMessage.PROPOSE) {
                        Map<String, String> tp = parseContent(resp.getContent());
                        int transportPrice = Integer.parseInt(tp.getOrDefault("price", "0"));
                        String eta = tp.getOrDefault("eta", "?");
                        String vehicle = tp.getOrDefault("vehicle", "caminhão");

                        int totalPrice = rentalPrice + transportPrice;

                        // Escolhe apenas ofertas que caibam no orçamento do consumidor.
                        if (totalPrice <= clientBudget && transportPrice < melhorTransportPrice) {
                            melhorTransportPrice = transportPrice;
                            melhorTransport = resp.getSender();
                            melhorVeiculo = vehicle;
                            melhorEta = eta;
                        }
                    }

                    // Conta para encurtar a janela quando todo mundo responder.
                    respostasRecebidas++;
                    if (respostasRecebidas >= transportAgents.size()) {
                        fimSelecaoMs = agora; // força fechamento da seleção
                    }

                } else {
                    if (agora > fimSelecaoMs) {
                        if (melhorTransport == null) {
                            System.out.println("[" + getLocalName() + "] Pedido #" + pedidoId
                                    + ": nenhum transporte coube no orçamento.");
                            ACLMessage failure = new ACLMessage(ACLMessage.FAILURE);
                            failure.addReceiver(consumer);
                            failure.setConversationId(convId);
                            failure.setContent("Nenhum transportador disponível (ou dentro do orçamento) para o pedido.");
                            send(failure);
                            finalizado = true;
                            return;
                        }

                        System.out.println("[" + getLocalName() + "] Pedido #" + pedidoId
                                + ": transporte escolhido -> " + melhorVeiculo
                                + " (R$" + melhorTransportPrice + ", ida em " + melhorEta + ").");

                        ACLMessage accept = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                        accept.addReceiver(melhorTransport);
                        accept.setConversationId(convId);
                        accept.setContent("start=true");
                        send(accept);

                        aceito = true;
                        fimConclusaoMs = System.currentTimeMillis() + 12000; // janela para concluir o transporte
                    } else {
                        myAgent.doWait(100);
                    }
                }
            } else {
                ACLMessage doneMsg = myAgent.receive(mtConv);

                if (doneMsg != null) {
                    if (doneMsg.getPerformative() == ACLMessage.INFORM) {
                        Map<String, String> td = parseContent(doneMsg.getContent());
                        String veiculo = td.getOrDefault("vehicle", melhorVeiculo);
                        int transportePreco = Integer.parseInt(td.getOrDefault("price", String.valueOf(melhorTransportPrice)));
                        String eta = td.getOrDefault("eta", melhorEta);

                        int totalPrice = rentalPrice + transportePreco;

                        System.out.println("[" + getLocalName() + "] Transporte concluído para pedido #" + pedidoId
                                + ". Confirmando ao consumidor.");

                        ACLMessage confirm = new ACLMessage(ACLMessage.INFORM);
                        confirm.addReceiver(consumer);
                        confirm.setConversationId(convId);
                        confirm.setContent("Serviço confirmado | Veículo: " + veiculo
                                + " | Ida estimada: " + eta
                                + " | Aluguel: R$" + rentalPrice
                                + " | Transporte: R$" + transportePreco
                                + " | Total: R$" + totalPrice);
                        send(confirm);

                        finalizado = true;
                        return;
                    }
                    // Se chegar alguma outra mensagem no meio, ignoramos e seguimos aguardando.

                } else {
                    if (agora > fimConclusaoMs) {
                        ACLMessage failure = new ACLMessage(ACLMessage.FAILURE);
                        failure.addReceiver(consumer);
                        failure.setConversationId(convId);
                        failure.setContent("Tempo esgotado para finalizar o transporte do pedido.");
                        send(failure);
                        finalizado = true;
                    } else {
                        myAgent.doWait(200);
                    }
                }
            }
        }

        @Override
        public boolean done() {
            return finalizado;
        }
    }

    private static Map<String, String> parseContent(String content) {
        Map<String, String> map = new HashMap<>();
        if (content == null) return map;
        for (String part : content.split(";")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) map.put(kv[0].trim(), kv[1].trim());
        }
        return map;
    }
}
