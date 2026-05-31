package br.univali.cc.ia2.m2.sma.gui;

import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

/**
 * JavaFX entry point. Starts the JADE container + agents in a background thread,
 * then displays the TrafficControlUI on the JavaFX Application Thread.
 */
public class TrafficControlApp extends Application {

    private static volatile Scene cachedScene;
    private static volatile TrafficControlUI ui;

    public static void main(String[] args) {
        Application.launch(args);
    }

    @Override
    public void start(Stage stage) {
        ui = new TrafficControlUI();
        cachedScene = ui.buildScene();

        stage.setTitle("Agent Traffic Control — SMA Generator Rental");
        stage.setScene(cachedScene);
        stage.setResizable(false);
        stage.sizeToScene();
        stage.show();

        ui.start();

        // Start JADE agents on JavaFX background thread
        new Thread(() -> startJade(), "JADE-container").start();
    }

    private void startJade() {
        try {
            // Ensure JADE system properties for GUI mode
            System.setProperty("jade.gui", "true");

            jade.core.Runtime runtime = jade.core.Runtime.instance();
            jade.core.Profile profile = new jade.core.ProfileImpl();
            profile.setParameter(jade.core.Profile.GUI, "true");
            jade.wrapper.AgentContainer container = runtime.createMainContainer(profile);

            int numTransports = 3;
            int numConsumers = 4;

            // Start rental
            container.createNewAgent(
                    "rental",
                    "br.univali.cc.ia2.m2.sma.RentalAgent",
                    new Object[]{numTransports}
            ).start();

            // Start transports
            for (int i = 0; i < numTransports; i++) {
                container.createNewAgent(
                        "transport" + i,
                        "br.univali.cc.ia2.m2.sma.TransportAgent",
                        null
                ).start();
            }

            // Start consumers
            for (int i = 0; i < numConsumers; i++) {
                container.createNewAgent(
                        "consumer" + i,
                        "br.univali.cc.ia2.m2.sma.ConsumerAgent",
                        new Object[]{i + 1}
                ).start();
            }

            // Start visualizer agent
            container.createNewAgent(
                    "visualizer",
                    "br.univali.cc.ia2.m2.sma.gui.VisualizerAgent",
                    null
            ).start();

        } catch (Exception e) {
            System.err.println("[TrafficControlApp] Failed to start JADE agents:");
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
        if (ui != null) ui.stop();
        try { jade.core.Runtime.instance().createMainContainer(null).kill(); } catch (Exception ignored) {}
    }
}