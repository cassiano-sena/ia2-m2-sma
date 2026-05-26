package br.univali.cc.ia2.m2.sma;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class TransportAgent extends Agent {

    // Uma transportadora física só faz um transporte por vez.
    private boolean emTransporte = false;
    private final Random random = new Random();

    // Ofertas aguardando aceite do RentalAgent (por ID de conversação).
    private final Map<String, Oferta> ofertasPendentes = new HashMap<>();

    private static class Oferta {
        final String vehicle;
        final int transportPrice;
        final String etaIda;
        final int totalMinutos;
        final String consumidor;
        final AID rental;

        Oferta(String vehicle,
               int transportPrice,
               String etaIda,
               int totalMinutos,
               String consumidor,
               AID rental) {
            this.vehicle = vehicle;
            this.transportPrice = transportPrice;
            this.etaIda = etaIda;
            this.totalMinutos = totalMinutos;
            this.consumidor = consumidor;
            this.rental = rental;
        }
    }

    // Escala de simulação: cada "minuto" vira alguns milissegundos para o demo rodar rápido.
    private static final int MILIS_POR_MINUTO_SIMULADO = 80;
    private static final int TEMPO_MAXIMO_SIMULADO_MS = 12000;

    @Override
    protected void setup() {
        System.out.println(
                "[" + getLocalName() + "] Transportadora iniciada. Estado: disponível.");

        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive();

                if (msg != null) {
                    switch (msg.getPerformative()) {
                        case ACLMessage.REQUEST -> tratarConsulta(msg);
                        case ACLMessage.ACCEPT_PROPOSAL -> tratarAceite(msg);
                        default -> {
                            // Ignora outros tipos por enquanto.
                        }
                    }
                } else {
                    block();
                }
            }
        });
    }

    // Primeiro passo: o RentalAgent consulta disponibilidade e recebe uma proposta (sem “virar ocupado” ainda).
    private void tratarConsulta(ACLMessage msg) {
        String convId = msg.getConversationId();

        Map<String, String> params = parseContent(msg.getContent());
        int load = Integer.parseInt(params.getOrDefault("load", "0"));
        int duration = Integer.parseInt(params.getOrDefault("duration", "1"));
        String consumidor = params.getOrDefault("consumer", "consumidor");

        if (emTransporte) {
            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.REFUSE);
            reply.setContent("transportadora ocupada (ida+volta em andamento).");
            send(reply);

            System.out.println("[" + getLocalName() + "] Consulta recusada: já estou em transporte.");
            return;
        }

        // Decisão autônoma: escolhe o tipo de veículo de acordo com a carga.
        String vehicle;
        int baseTransportPrice;
        if (load <= 3000) {
            vehicle = "Van leve";
            baseTransportPrice = 150;
        } else if (load <= 6000) {
            vehicle = "Caminhão médio";
            baseTransportPrice = 250;
        } else {
            vehicle = "Caminhão pesado";
            baseTransportPrice = 400;
        }

        // Variamos aleatoriamente preço e tempos para parecer mais “inteligente”.
        int transportPrice = baseTransportPrice + random.nextInt(120);
        int idaMin = 25 + random.nextInt(70);  // 25–94 min
        int voltaMin = 15 + random.nextInt(60); // 15–74 min
        int totalMin = idaMin + voltaMin;

        String etaIda = idaMin + " min";

        // Guardamos a oferta para só começar a mover após o aceite do RentalAgent.
        ofertasPendentes.put(convId, new Oferta(
                vehicle,
                transportPrice,
                etaIda,
                totalMin,
                consumidor,
                msg.getSender()
        ));

        ACLMessage reply = msg.createReply();
        reply.setPerformative(ACLMessage.PROPOSE);
        reply.setContent("vehicle=" + vehicle
                + ";price=" + transportPrice
                + ";eta=" + etaIda
                + ";totalMinutes=" + totalMin
                + ";simDuration=" + duration);

        System.out.println("[" + getLocalName() + "] Propondo transporte ao Rental para [" + consumidor + "]: "
                + vehicle + " | R$" + transportPrice + " | ida em " + etaIda + " (load=" + load + ").");

        send(reply);
    }

    // Segundo passo: o RentalAgent aceita uma proposta e aí a transportadora fica indisponível durante a ida+volta.
    private void tratarAceite(ACLMessage msg) {
        String convId = msg.getConversationId();

        if (emTransporte) {
            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.REFUSE);
            reply.setContent("já estou em transporte.");
            send(reply);
            return;
        }

        Oferta oferta = ofertasPendentes.remove(convId);
        if (oferta == null) {
            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.REFUSE);
            reply.setContent("nenhuma oferta pendente encontrada para esse pedido.");
            send(reply);
            return;
        }

        emTransporte = true;

        int waitMs = Math.min(oferta.totalMinutos * MILIS_POR_MINUTO_SIMULADO, TEMPO_MAXIMO_SIMULADO_MS);

        System.out.println("[" + getLocalName() + "] Aceite recebido. Iniciando transporte físico -> consumidor ["
                + oferta.consumidor + "]. Veículo: " + oferta.vehicle
                + ". Indo e voltando (sim: " + waitMs + "ms).");

        final Oferta ofertaFinal = oferta;
        addBehaviour(new WakerBehaviour(this, waitMs) {
            @Override
            protected void onWake() {
                emTransporte = false;

                System.out.println("[" + getLocalName() + "] Transporte finalizado (ida+volta concluídas) para ["
                        + ofertaFinal.consumidor + "]. Agora estou disponível novamente.");

                ACLMessage done = new ACLMessage(ACLMessage.INFORM);
                done.addReceiver(ofertaFinal.rental);
                done.setConversationId(convId);
                done.setContent("vehicle=" + ofertaFinal.vehicle
                        + ";price=" + ofertaFinal.transportPrice
                        + ";eta=" + ofertaFinal.etaIda
                        + ";totalMinutes=" + ofertaFinal.totalMinutos);
                send(done);
            }
        });
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
