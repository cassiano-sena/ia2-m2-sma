package br.univali.cc.ia2.m2.sma;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class TransportAgent extends Agent {

    // Estado simulado da frota: quantos veículos estão disponíveis agora
    private int availableVehicles = 3;
    private final Random random = new Random();

    @Override
    protected void setup() {
        System.out.println("[" + getLocalName() + "] Agente de transporte iniciado. Frota disponível: "
                + availableVehicles + " veículo(s).");

        addBehaviour(new CyclicBehaviour() {

            @Override
            public void action() {

                ACLMessage msg = receive();

                if (msg != null) {

                    Map<String, String> params = parseContent(msg.getContent());
                    int load     = Integer.parseInt(params.getOrDefault("load", "0"));
                    int duration = Integer.parseInt(params.getOrDefault("duration", "1"));

                    System.out.println("[" + getLocalName() + "] Pedido de transporte recebido: "
                            + load + "W por " + duration + "h. Frota atual: " + availableVehicles + " veículo(s).");

                    // Decisão autônoma: escolhe o tipo de veículo de acordo com a carga
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

                    // Variamos aleatoriamente preço e tempo de entrega
                    int transportPrice = baseTransportPrice + random.nextInt(100);
                    int deliveryMinutes = 30 + random.nextInt(90); // 30–119 min
                    String deliveryTime = deliveryMinutes + " min";

                    // Disponibilidade: depende do tamanho da frota + uma chance aleatória
                    boolean available = availableVehicles > 0 && random.nextInt(100) < 75;

                    ACLMessage reply = msg.createReply();

                    if (available) {

                        availableVehicles--;
                        // O veículo volta para a frota após a entrega simulada (sem bloquear)
                        final int returnDelay = (duration * 60 + deliveryMinutes) * 10; // delay reduzido em ms
                        new Thread(() -> {
                            try { Thread.sleep(Math.min(returnDelay, 8000)); } catch (InterruptedException ignored) {}
                            availableVehicles++;
                            System.out.println("[" + getLocalName() + "] " + vehicle
                                    + " retornou à frota. Frota atual: " + availableVehicles + " veículo(s).");
                        }).start();

                        reply.setPerformative(ACLMessage.PROPOSE);
                        reply.setContent("vehicle=" + vehicle
                                + ";price=" + transportPrice
                                + ";time=" + deliveryTime);

                        System.out.println("[" + getLocalName() + "] Decisão: despachar " + vehicle
                                + " por R$" + transportPrice + ", chegada em " + deliveryTime
                                + ". Frota restante: " + availableVehicles + " veículo(s).");

                    } else {

                        String reason = availableVehicles == 0
                                ? "frota esgotada"
                                : "veículo indisponível no momento";

                        reply.setPerformative(ACLMessage.REFUSE);
                        reply.setContent("transport unavailable: " + reason);

                        System.out.println("[" + getLocalName() + "] Decisão: recusado — " + reason + ".");
                    }

                    send(reply);

                } else {
                    block();
                }
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
