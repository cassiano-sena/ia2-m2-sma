package br.univali.cc.ia2.m2.sma;
// import br.univali.cc.ia2.m2.sma.ConsumerAgent;
// import br.univali.cc.ia2.m2.sma.RentalAgent;
// import br.univali.cc.ia2.m2.sma.TransportAgent;

import jade.wrapper.AgentContainer;

public class Main {

    public static void main(String[] args) {

        AgentContainer container = JadeContainerFactory.createMainContainer(false);

        // trycatch pra criacao de agentes
        try {

            int numTransportes = 3; // quantidade de transportadoras físicas
            int numConsumidores = 4; // quantidade de consumidores autônomos

            container.createNewAgent(
                    "rental",
                    RentalAgent.class.getName(),
                    new Object[]{numTransportes}
            ).start();

            for (int i = 0; i < numTransportes; i++) {
                container.createNewAgent(
                        "transport" + i,
                        TransportAgent.class.getName(),
                        null
                ).start();
            }

            for (int i = 0; i < numConsumidores; i++) {
                container.createNewAgent(
                        "consumer" + i,
                        ConsumerAgent.class.getName(),
                        new Object[]{i + 1} // seed opcional para variar decisões
                ).start();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}