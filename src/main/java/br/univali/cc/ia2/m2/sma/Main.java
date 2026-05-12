package br.univali.cc.ia2.m2.sma;
// import br.univali.cc.ia2.m2.sma.ConsumerAgent;
// import br.univali.cc.ia2.m2.sma.RentalAgent;
// import br.univali.cc.ia2.m2.sma.TransportAgent;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;

public class Main {

    public static void main(String[] args) {

        // para iniciar o servidor de agentes do JADE
        Runtime runtime = Runtime.instance();

        // para configurar portas redes e parametros
        Profile profile = new ProfileImpl();

        // para criar o container de agentes (ambiente onde os agentes vivem)
        AgentContainer container = runtime.createMainContainer(profile);

        // trycatch pra criacao de agentes
        try {

            container.createNewAgent(
                    "rental",
                    RentalAgent.class.getName(),
                    null
            ).start();

            container.createNewAgent(
                    "transport",
                    TransportAgent.class.getName(),
                    null
            ).start();

            container.createNewAgent(
                    "consumer",
                    ConsumerAgent.class.getName(),
                    null
            ).start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}