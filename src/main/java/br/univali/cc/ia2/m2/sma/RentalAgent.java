package br.univali.cc.ia2.m2.sma;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class RentalAgent extends Agent {

    private static final int MAX_GENERATOR_LOAD = 10000; // W (capacidade máxima do gerador)
    private static final int PRICE_PER_HOUR = 80;        // R$ por hora (taxa base)
    private static final int SURCHARGE_HIGH_LOAD = 50;   // adicional R$/h acima de 6000W

    private final Random random = new Random();

    // Mantém requisições do consumidor pendentes por ID da conversação
    private final Map<String, AID> pendingRequests = new HashMap<>();
    private final Map<String, Integer> pendingPrices  = new HashMap<>();

    @Override
    protected void setup() {
        System.out.println("[" + getLocalName() + "] Agente de aluguel iniciado. Capacidade máxima: "
                + MAX_GENERATOR_LOAD + "W.");

        addBehaviour(new CyclicBehaviour() {

            @Override
            public void action() {

                ACLMessage msg = receive();

                if (msg != null) {

                    switch (msg.getPerformative()) {

                        case ACLMessage.REQUEST -> handleConsumerRequest(msg);
                        case ACLMessage.PROPOSE  -> handleTransportProposal(msg);
                        case ACLMessage.REFUSE   -> handleTransportRefusal(msg);
                    }

                } else {
                    block();
                }
            }
        });
    }

    private void handleConsumerRequest(ACLMessage msg) {

        Map<String, String> params = parseContent(msg.getContent());

        String id       = params.getOrDefault("id", "?");
        int load        = Integer.parseInt(params.getOrDefault("load", "0"));
        int duration    = Integer.parseInt(params.getOrDefault("duration", "1"));
        int clientBudget = Integer.parseInt(params.getOrDefault("budget", "9999"));

        System.out.println("[" + getLocalName() + "] Nova solicitação #" + id + " de ["
                + msg.getSender().getLocalName() + "]: " + load + "W por " + duration + "h.");

        // Decisão autônoma: conseguimos atender essa solicitação?
        if (load > MAX_GENERATOR_LOAD) {
            System.out.println("[" + getLocalName() + "] Decisão: carga " + load
                    + "W excede capacidade máxima. Recusando pedido #" + id + ".");
            ACLMessage refusal = msg.createReply();
            refusal.setPerformative(ACLMessage.FAILURE);
            refusal.setContent("Carga solicitada (" + load + "W) excede capacidade do gerador.");
            send(refusal);
            return;
        }

        // Calcula o preço do aluguel
        int basePrice = duration * PRICE_PER_HOUR;
        int surcharge = (load > 6000) ? duration * SURCHARGE_HIGH_LOAD : 0;
        int rentalPrice = basePrice + surcharge + random.nextInt(50); // small variation

        System.out.println("[" + getLocalName() + "] Decisão: pedido #" + id
                + " viável. Preço calculado do aluguel: R$" + rentalPrice
                + " (base R$" + basePrice + (surcharge > 0 ? " + adicional R$" + surcharge : "") + ").");

        // Verificação autônoma de orçamento do cliente
        if (rentalPrice > clientBudget) {
            System.out.println("[" + getLocalName() + "] Decisão: orçamento insuficiente para o pedido #" + id
                    + ". Preço do aluguel (R$" + rentalPrice + ") > orçamento do cliente (R$" + clientBudget + ").");

            ACLMessage refusal = msg.createReply();
            refusal.setPerformative(ACLMessage.FAILURE);
            refusal.setContent("Orçamento insuficiente. Aluguel (R$" + rentalPrice + ") excede o orçamento (R$" + clientBudget + ").");
            send(refusal);
            return;
        }

        // Armazena contexto indexado pelo ID da conversação do consumidor
        String convId = msg.getConversationId() != null ? msg.getConversationId() : id;
        pendingRequests.put(convId, msg.getSender());
        pendingPrices.put(convId, rentalPrice);

        // Encaminha ao agente de transporte
        ACLMessage transportRequest = new ACLMessage(ACLMessage.REQUEST);
        transportRequest.addReceiver(new AID("transport", AID.ISLOCALNAME));
        transportRequest.setConversationId(convId);
        transportRequest.setContent("load=" + load + ";duration=" + duration + ";rental_price=" + rentalPrice);

        send(transportRequest);
        System.out.println("[" + getLocalName() + "] Solicitou transporte para pedido #" + id + ".");
    }

    private void handleTransportProposal(ACLMessage msg) {

        String convId = msg.getConversationId();
        AID consumer = pendingRequests.remove(convId);
        int rentalPrice = pendingPrices.getOrDefault(convId, 0);
        pendingPrices.remove(convId);

        if (consumer == null) {
            System.out.println("[" + getLocalName() + "] Proposta de transporte sem consumidor pendente. Ignorando.");
            return;
        }

        Map<String, String> tp = parseContent(msg.getContent());
        int transportPrice = Integer.parseInt(tp.getOrDefault("price", "0"));
        String deliveryTime = tp.getOrDefault("time", "?");
        String vehicle = tp.getOrDefault("vehicle", "caminhão");
        int totalPrice = rentalPrice + transportPrice;

        System.out.println("[" + getLocalName() + "] Decisão: transporte aceito (" + vehicle
                + ", R$" + transportPrice + ", chegada em " + deliveryTime
                + "). Preço total ao cliente: R$" + totalPrice + ".");

        ACLMessage confirm = new ACLMessage(ACLMessage.INFORM);
        confirm.addReceiver(consumer);
        confirm.setConversationId(convId);
        confirm.setContent("Serviço confirmado | Veículo: " + vehicle
                + " | Entrega em: " + deliveryTime
                + " | Aluguel: R$" + rentalPrice
                + " | Transporte: R$" + transportPrice
                + " | Total: R$" + totalPrice);

        send(confirm);
        System.out.println("[" + getLocalName() + "] Confirmação enviada ao consumidor.");
    }

    private void handleTransportRefusal(ACLMessage msg) {

        String convId = msg.getConversationId();
        AID consumer = pendingRequests.remove(convId);
        pendingPrices.remove(convId);

        if (consumer == null) {
            System.out.println("[" + getLocalName() + "] Recusa de transporte sem consumidor pendente. Ignorando.");
            return;
        }

        System.out.println("[" + getLocalName() + "] Decisão: transporte recusado — notificando consumidor da falha.");

        ACLMessage failure = new ACLMessage(ACLMessage.FAILURE);
        failure.addReceiver(consumer);
        failure.setConversationId(convId);
        failure.setContent("Nenhum transportador disponível no momento. Tente novamente.");

        send(failure);
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
