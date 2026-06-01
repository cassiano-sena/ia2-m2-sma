package br.univali.cc.ia2.m2.sma.gui;

import br.univali.cc.ia2.m2.sma.JadeContainerFactory;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import jade.wrapper.AgentContainer;

/**
 * JavaFX entry point. Starts the JADE container + agents in a background thread,
 * then displays the TrafficControlUI on the JavaFX Application Thread.
 */
public class TrafficControlApp extends Application {

    private static volatile Scene cachedScene;
    private static volatile TrafficControlUI ui;
    private volatile AgentContainer jadeContainer;

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
            System.setProperty("jade.gui", "true");

            jadeContainer = JadeContainerFactory.createMainContainer(true);

            int numTransports = 3;
            int numConsumers = 4;

            jadeContainer.createNewAgent(
                    "rental",
                    "br.univali.cc.ia2.m2.sma.RentalAgent",
                    new Object[]{numTransports}
            ).start();

            // Start transports
            for (int i = 0; i < numTransports; i++) {
                jadeContainer.createNewAgent(
                        "transport" + i,
                        "br.univali.cc.ia2.m2.sma.TransportAgent",
                        null
                ).start();
            }

            // Start consumers
            for (int i = 0; i < numConsumers; i++) {
                jadeContainer.createNewAgent(
                        "consumer" + i,
                        "br.univali.cc.ia2.m2.sma.ConsumerAgent",
                        new Object[]{i + 1}
                ).start();
            }

            // Start visualizer agent
            jadeContainer.createNewAgent(
                    "visualizer",
                    "br.univali.cc.ia2.m2.sma.gui.VisualizerAgent",
                    null
            ).start();

        } catch (Exception e) {
            System.err.println("[TrafficControlApp] Failed to start JADE agents:");
            e.printStackTrace();
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("JADE");
                alert.setHeaderText("Falha ao iniciar agentes JADE");
                alert.setContentText(e.getMessage()
                        + "\n\nFeche outras instancias da GUI ou processos Java antigos e tente de novo.");
                alert.showAndWait();
            });
        }
    }

    @Override
    public void stop() {
        if (ui != null) {
            ui.stop();
        }
        if (jadeContainer != null) {
            try {
                jadeContainer.kill();
            } catch (Exception ignored) {
            }
        }
    }
}