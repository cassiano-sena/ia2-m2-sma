package br.univali.cc.ia2.m2.sma;

import java.io.IOException;
import java.net.ServerSocket;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;

/**
 * Creates a JADE main container, picking a free TCP port when the default (1099) is busy.
 */
public final class JadeContainerFactory {

    private static final int DEFAULT_PORT = 1099;
    private static final int PORT_SCAN_RANGE = 50;

    private JadeContainerFactory() {
    }

    public static AgentContainer createMainContainer(boolean enableJadeGui) {
        Runtime runtime = Runtime.instance();
        runtime.setCloseVM(true);

        Profile profile = new ProfileImpl();
        if (enableJadeGui) {
            profile.setParameter(Profile.GUI, "true");
        }

        int port = findAvailablePort(DEFAULT_PORT);
        String portValue = String.valueOf(port);
        profile.setParameter(Profile.LOCAL_PORT, portValue);
        profile.setParameter(Profile.MAIN_PORT, portValue);

        System.out.println("[JADE] Binding to localhost port " + port);

        AgentContainer container = runtime.createMainContainer(profile);
        if (container == null) {
            throw new IllegalStateException(
                    "JADE main container failed to start (port " + port + "). "
                            + "Close other JADE/Java processes or free TCP ports "
                            + DEFAULT_PORT + "-" + (DEFAULT_PORT + PORT_SCAN_RANGE - 1) + "."
            );
        }
        return container;
    }

    static int findAvailablePort(int preferred) {
        for (int port = preferred; port < preferred + PORT_SCAN_RANGE; port++) {
            if (isPortAvailable(port)) {
                return port;
            }
        }
        throw new IllegalStateException(
                "No free TCP port for JADE in range "
                        + preferred + "-" + (preferred + PORT_SCAN_RANGE - 1)
        );
    }

    private static boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
