package br.univali.cc.ia2.m2.sma.gui;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main JavaFX Traffic Control UI — Light theme dashboard with agent node canvas (left)
 * and message log sidebar (right) divided into 3 categories.
 */
public class TrafficControlUI {

    // Light theme colour palette
    private static final Color BG = Color.rgb(248, 249, 250);
    private static final Color SURFACE = Color.WHITE;
    private static final Color BORDER = Color.rgb(222, 226, 230);
    private static final Color TEXT = Color.rgb(33, 37, 41);
    private static final Color MUTED = Color.rgb(108, 117, 125);

    private static final Color CONSUMER_COLOR   = Color.rgb(40, 167, 69);
    private static final Color CONSUMER_BG      = Color.rgb(209, 231, 221);
    private static final Color RENTAL_COLOR     = Color.rgb(111, 66, 193);
    private static final Color RENTAL_BG        = Color.rgb(237, 233, 247);
    private static final Color TRANSPORT_COLOR  = Color.rgb(253, 126, 20);
    private static final Color TRANSPORT_BG     = Color.rgb(255, 227, 201);

    private static final Color STATUS_AVAILABLE = Color.rgb(40, 167, 69);
    private static final Color STATUS_BUSY     = Color.rgb(255, 193, 7);
    private static final Color STATUS_THINKING = Color.rgb(0, 123, 255);

    // Agent node centres on the canvas (used for line start/end)
    private static final Map<String, double[]> NODE_POS = Map.of(
            "consumer0", new double[]{145,  85},
            "consumer1", new double[]{145, 225},
            "consumer2", new double[]{145, 365},
            "consumer3", new double[]{145, 505},
            "rental",    new double[]{390, 300},
            "transport0", new double[]{655, 100},
            "transport1", new double[]{655, 280},
            "transport2", new double[]{655, 460}
    );

    // ── Node card refs ────────────────────────────────────────────────────────
    private static class NodeRefs {
        final Label status, detail;
        NodeRefs(Label s, Label d) { status = s; detail = d; }
    }

    // ── State ────────────────────────────────────────────────────────────────
    private final Map<String, NodeRefs> nodeRefs   = new ConcurrentHashMap<>();
    private final Map<String, String>   agentStates  = new ConcurrentHashMap<>();
    private final Map<String, String>   agentDetails = new ConcurrentHashMap<>();

    private final Canvas msgCanvas;
    private final VBox logBoxPendente, logBoxEmAndamento, logBoxConcluida;
    private final Label statTotal, statConfirmed, statFailed;
    private final Label liveBadge;
    private final Button btnPause;
    private final HBox header;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");

    private int totalRequests   = 0;
    private int confirmedRequests = 0;
    private int failedRequests   = 0;

    // Track message IDs to remove from previous lists when transitioning
    private final Map<String, AgentMessageEvent> pendingMessages = new ConcurrentHashMap<>();
    private final Map<String, AgentMessageEvent> inProgressMessages = new ConcurrentHashMap<>();

    // ── Message flow animation ────────────────────────────────────────────────
    private record Flow(double sx, double sy, double ex, double ey, Color col, long startNs) {}
    private final List<Flow> flows = new ArrayList<>();

    public TrafficControlUI() {
        for (String c : new String[]{"consumer0","consumer1","consumer2","consumer3"}) {
            agentStates.put(c, "available");
            agentDetails.put(c, "");
        }
        agentStates.put("rental", "available");
        agentDetails.put("rental", "R$80/h + transport");
        for (String t : new String[]{"transport0","transport1","transport2"}) {
            agentStates.put(t, "available");
            agentDetails.put(t, "");
        }

        msgCanvas = new Canvas(750, 580);

        statTotal      = new Label("Total de Solicitações: 0");
        statConfirmed  = new Label("Confirmadas: 0");
        statFailed     = new Label("Falhas: 0");
        for (Label l : new Label[]{statTotal, statConfirmed, statFailed}) {
            l.setFont(Font.font("Segoe UI", 11));
            l.setTextFill(MUTED);
        }

        liveBadge = new Label("AO VIVO");
        liveBadge.setFont(Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 10.0));
        liveBadge.setTextFill(Color.WHITE);
        liveBadge.setBackground(new Background(new BackgroundFill(STATUS_AVAILABLE, new CornerRadii(12), null)));
        liveBadge.setPadding(new Insets(2, 8, 2, 8));
        liveBadge.setAlignment(Pos.CENTER);

        btnPause = new Button("⏸ Pausar Visual");
        btnPause.setFont(Font.font("Segoe UI", 11));
        btnPause.setTextFill(Color.WHITE);
        btnPause.setBackground(new Background(new BackgroundFill(Color.rgb(108, 117, 125), new CornerRadii(6), null)));
        btnPause.setPadding(new Insets(6, 14, 6, 14));
        btnPause.setCursor(javafx.scene.Cursor.HAND);
        btnPause.setOnAction(e -> {
            if (running.get()) {
                running.set(false);
                btnPause.setText("▶ Retomar Visual");
                btnPause.setBackground(new Background(new BackgroundFill(STATUS_AVAILABLE, new CornerRadii(6), null)));
                liveBadge.setBackground(new Background(new BackgroundFill(Color.rgb(108, 117, 125), new CornerRadii(12), null)));
                liveBadge.setText("PAUSADO");
            } else {
                running.set(true);
                btnPause.setText("⏸ Pausar Visual");
                btnPause.setBackground(new Background(new BackgroundFill(Color.rgb(108, 117, 125), new CornerRadii(6), null)));
                liveBadge.setBackground(new Background(new BackgroundFill(STATUS_AVAILABLE, new CornerRadii(12), null)));
                liveBadge.setText("AO VIVO");
            }
        });

        logBoxPendente = new VBox(5);
        logBoxPendente.setPadding(new Insets(8));
        logBoxPendente.setBackground(new Background(new BackgroundFill(BG, null, null)));

        logBoxEmAndamento = new VBox(5);
        logBoxEmAndamento.setPadding(new Insets(8));
        logBoxEmAndamento.setBackground(new Background(new BackgroundFill(BG, null, null)));

        logBoxConcluida = new VBox(5);
        logBoxConcluida.setPadding(new Insets(8));
        logBoxConcluida.setBackground(new Background(new BackgroundFill(BG, null, null)));

        // Header bar
        Label title = new Label("Controle de Tráfego de Agentes — Locadora de Geradores SMA");
        title.setFont(Font.font("Segoe UI", javafx.scene.text.FontWeight.NORMAL, 15.0));
        title.setTextFill(Color.rgb(0, 123, 255));
        title.setLayoutY(12);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        header = new HBox(title, liveBadge, spacer, btnPause);
        header.setPrefHeight(44);
        header.setBackground(new Background(new BackgroundFill(BORDER, null, null)));
        header.setPadding(new Insets(0, 16, 0, 16));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setSpacing(12);
        HBox.setHgrow(title, Priority.ALWAYS);
        liveBadge.setAlignment(Pos.CENTER);
        btnPause.setAlignment(Pos.CENTER);
    }

    public Scene buildScene() {
        // Canvas with agent nodes
        Pane canvasPane = new Pane(msgCanvas);
        canvasPane.setPrefSize(750, 580);
        canvasPane.setBackground(new Background(new BackgroundFill(BG, null, null)));
        for (Map.Entry<String, double[]> e : NODE_POS.entrySet()) {
            String name = e.getKey();
            Region node = createNodeCard(name, e.getValue()[0], e.getValue()[1]);
            nodeRefs.put(name, (NodeRefs) node.getUserData());
            canvasPane.getChildren().add(node);
        }

        // Right sidebar - 3 categorias de mensagens
        Label logTitlePendente = new Label("Pendentes");
        logTitlePendente.setFont(Font.font("Segoe UI", 12));
        logTitlePendente.setTextFill(MUTED);
        logTitlePendente.setPadding(new Insets(8, 16, 4, 16));
        logTitlePendente.setBackground(new Background(new BackgroundFill(BORDER, null, null)));

        ScrollPane scrollPendente = new ScrollPane(logBoxPendente);
        scrollPendente.setFitToWidth(true);
        scrollPendente.setBackground(new Background(new BackgroundFill(SURFACE, null, null)));
        scrollPendente.setPrefHeight(140);
        scrollPendente.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        Label logTitleEmAndamento = new Label("Em andamento");
        logTitleEmAndamento.setFont(Font.font("Segoe UI", 12));
        logTitleEmAndamento.setTextFill(MUTED);
        logTitleEmAndamento.setPadding(new Insets(8, 16, 4, 16));
        logTitleEmAndamento.setBackground(new Background(new BackgroundFill(BORDER, null, null)));

        ScrollPane scrollEmAndamento = new ScrollPane(logBoxEmAndamento);
        scrollEmAndamento.setFitToWidth(true);
        scrollEmAndamento.setBackground(new Background(new BackgroundFill(SURFACE, null, null)));
        scrollEmAndamento.setPrefHeight(140);
        scrollEmAndamento.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        Label logTitleConcluida = new Label("Concluídas");
        logTitleConcluida.setFont(Font.font("Segoe UI", 12));
        logTitleConcluida.setTextFill(MUTED);
        logTitleConcluida.setPadding(new Insets(8, 16, 4, 16));
        logTitleConcluida.setBackground(new Background(new BackgroundFill(BORDER, null, null)));

        ScrollPane scrollConcluida = new ScrollPane(logBoxConcluida);
        scrollConcluida.setFitToWidth(true);
        scrollConcluida.setBackground(new Background(new BackgroundFill(SURFACE, null, null)));
        scrollConcluida.setPrefHeight(180);
        scrollConcluida.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scrollConcluida, Priority.ALWAYS);

        VBox statsBar = new VBox(4);
        statsBar.setPadding(new Insets(10, 12, 10, 12));
        statsBar.setBackground(new Background(new BackgroundFill(BORDER, null, null)));
        statsBar.getChildren().addAll(statTotal, statConfirmed, statFailed);

        VBox rightPanel = new VBox(logTitlePendente, scrollPendente, logTitleEmAndamento, scrollEmAndamento, logTitleConcluida, scrollConcluida, statsBar);
        rightPanel.setPrefWidth(340);
        rightPanel.setBackground(new Background(new BackgroundFill(SURFACE, null, null)));

        // Root GridPane
        GridPane root = new GridPane();
        root.setBackground(new Background(new BackgroundFill(SURFACE, null, null)));
        root.getColumnConstraints().addAll(col(750), col(340));
        root.getRowConstraints().addAll(row(44), row(580));

        GridPane.setColumnIndex(header,      0);
        GridPane.setRowIndex(header,          0);
        GridPane.setColumnSpan(header,       2);
        GridPane.setColumnIndex(canvasPane,  0);
        GridPane.setRowIndex(canvasPane,      1);
        GridPane.setColumnIndex(rightPanel,  1);
        GridPane.setRowIndex(rightPanel,     1);

        root.getChildren().addAll(header, canvasPane, rightPanel);

        return new Scene(root, 1090, 624);
    }

    private Region createNodeCard(String name, double cx, double cy) {
        Color border = agentBorderColor(name);
        Color bg     = agentBgColor(name);

        Label nameLbl   = new Label(name);
        nameLbl.setFont(Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 13.0));
        nameLbl.setTextFill(TEXT);

        Label statusLbl = new Label("available");
        statusLbl.setFont(Font.font("Segoe UI", 11));
        statusLbl.setTextFill(STATUS_AVAILABLE);

        Label detailLbl = new Label("");
        detailLbl.setFont(Font.font("Segoe UI", 10));
        detailLbl.setTextFill(MUTED);

        VBox box = new VBox(4, nameLbl, statusLbl, detailLbl);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(12, 16, 12, 16));
        box.setMinWidth(140);
        box.setBackground(new Background(new BackgroundFill(bg, new CornerRadii(12), null)));
        box.setBorder(new Border(new BorderStroke(border, BorderStrokeStyle.SOLID, new CornerRadii(12), new BorderWidths(2))));
        box.setUserData(new NodeRefs(statusLbl, detailLbl));

        // Centre card on (cx, cy)
        box.setLayoutX(cx - 70);
        box.setLayoutY(cy - 25);

        return box;
    }

    private Color agentBorderColor(String name) {
        if (name.equals("rental")) return RENTAL_COLOR;
        if (name.startsWith("transport")) return TRANSPORT_COLOR;
        return CONSUMER_COLOR;
    }

    private Color agentBgColor(String name) {
        if (name.equals("rental")) return RENTAL_BG;
        if (name.startsWith("transport")) return TRANSPORT_BG;
        return CONSUMER_BG;
    }

    private static ColumnConstraints col(double w) {
        ColumnConstraints c = new ColumnConstraints();
        c.setPrefWidth(w);
        return c;
    }

    private static RowConstraints row(double h) {
        RowConstraints r = new RowConstraints();
        r.setPrefHeight(h);
        return r;
    }

    // ── AnimationTimer ────────────────────────────────────────────────────────
    private final AnimationTimer timer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            if (!running.get()) return;
            AgentMessageEvent ev;
            while ((ev = MessageQueue.poll()) != null) {
                processEvent(ev);
            }
            drawFlows(now);
        }
    };

    public void start() { timer.start(); }
    public void stop()  { timer.stop();  }

    // ── Per-event processing ─────────────────────────────────────────────────
    private void processEvent(AgentMessageEvent ev) {
        String sender   = ev.getSender();
        String receiver = ev.getReceiver();
        String perf     = ev.getPerformativeName();
        String sType    = ev.getSenderType();

        enqueueFlow(sender, receiver, senderColor(sType));

        if ("REQUEST".equals(perf) && sender.startsWith("consumer")) {
            totalRequests++;
            Platform.runLater(() -> statTotal.setText("Total de Solicitações: " + totalRequests));
            updateNode(sender, "requesting", extractLoad(ev.getContent()));
        }
        if ("INFORM".equals(perf) && sender.equals("rental")) {
            confirmedRequests++;
            Platform.runLater(() -> statConfirmed.setText("Confirmadas: " + confirmedRequests));
        }
        if ("FAILURE".equals(perf)) {
            failedRequests++;
            Platform.runLater(() -> statFailed.setText("Falhas: " + failedRequests));
        }
        if ("PROPOSE".equals(perf) && sender.startsWith("transport")) {
            updateNode(sender, "thinking", vehicle(ev.getContent()) + " | R$" + price(ev.getContent()));
        }
        if ("INFORM".equals(perf) && sender.startsWith("transport")) {
            updateNode(sender, "available", vehicle(ev.getContent()));
        }
        if ("ACCEPT_PROPOSAL".equals(perf) && sender.equals("rental")) {
            String t = guessTransport(ev.getContent());
            if (t != null) updateNode(t, "busy", "in-transit");
        }

        Platform.runLater(() -> appendLog(ev));
    }

    private void updateNode(String agent, String status, String detail) {
        NodeRefs ref = nodeRefs.get(agent);
        if (ref == null) return;
        Color sc = switch (status) {
            case "available"  -> STATUS_AVAILABLE;
            case "busy"       -> STATUS_BUSY;
            default           -> STATUS_THINKING;
        };
        Platform.runLater(() -> {
            ref.status.setText(status);
            ref.status.setTextFill(sc);
            ref.detail.setText(detail != null ? detail : "");
        });
        agentStates.put(agent, status);
        if (detail != null && !detail.isEmpty()) agentDetails.put(agent, detail);
    }

    // ── Flow animation ────────────────────────────────────────────────────────
    private void enqueueFlow(String from, String to, Color col) {
        double[] fp = NODE_POS.get(from);
        double[] tp = NODE_POS.get(to);
        if (fp == null || tp == null) return;
        flows.add(new Flow(fp[0], fp[1], tp[0], tp[1], col, System.nanoTime()));
    }

    private void drawFlows(long now) {
        GraphicsContext gc = msgCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, msgCanvas.getWidth(), msgCanvas.getHeight());

        flows.removeIf(f -> {
            double sec = (now - f.startNs()) / 1_000_000_000.0;
            if (sec > 1.2) return true;

            double t = Math.min(sec / 0.7, 1.0);
            double alpha = (sec > 0.9) ? 0.8 * (1.0 - (sec - 0.9) / 0.3) : 0.8;

            double cx = f.sx() + (f.ex() - f.sx()) * t;
            double cy = f.sy() + (f.ey() - f.sy()) * t;

            gc.setStroke(f.col().deriveColor(0, 1, 1, alpha));
            gc.setLineWidth(2);
            gc.setLineCap(StrokeLineCap.ROUND);
            // Animated dash offset for flowing-line effect
            gc.setLineDashOffset((long) (-sec * 24));
            gc.strokeLine(f.sx(), f.sy(), cx, cy);
            return false;
        });
    }

    // ── Log panel ─────────────────────────────────────────────────────────────
    private Color senderBgColor(String type) {
        return switch (type) {
            case "consumer"  -> CONSUMER_BG;
            case "rental"    -> RENTAL_BG;
            case "transport" -> TRANSPORT_BG;
            default          -> Color.rgb(233, 236, 239);
        };
    }

    private void appendLog(AgentMessageEvent ev) {
        String sType = ev.getSenderType();
        Color bg = senderBgColor(sType);
        String time  = timeFmt.format(LocalDateTime.now());

        VBox entry = new VBox(2);
        entry.setPadding(new Insets(6, 8, 6, 8));
        entry.setBackground(new Background(new BackgroundFill(bg, new CornerRadii(4), null)));

        Label timeLbl = new Label(time + "  " + ev.getSender() + " → " + ev.getReceiver());
        timeLbl.setFont(Font.font("Segoe UI", 10));
        timeLbl.setTextFill(MUTED);
        Tooltip timeTooltip = new Tooltip(time + " | " + ev.getSender() + " → " + ev.getReceiver());
        timeTooltip.setFont(Font.font("Segoe UI", 10));
        Tooltip.install(timeLbl, timeTooltip);

        Label msgLbl = new Label(ev.getPerformativeName() + " | " + truncate(ev.getContent(), 80));
        msgLbl.setFont(Font.font("Segoe UI", 11));
        msgLbl.setTextFill(TEXT);
        msgLbl.setWrapText(true);
        // Tooltip com mensagem completa
        String fullContent = ev.getContent() != null ? ev.getContent() : "";
        Tooltip tooltip = new Tooltip(fullContent);
        tooltip.setFont(Font.font("Segoe UI", 10));
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(400);
        Tooltip.install(msgLbl, tooltip);

        entry.getChildren().addAll(timeLbl, msgLbl);

        String perf = ev.getPerformativeName();
        String sender = ev.getSender();

        // Determine message key for tracking transitions
        String msgKey = sender + "_" + time + "_" + perf;

        // Handle transitions based on performative
        if ("REQUEST".equals(perf) || "PROPOSE".equals(perf)) {
            // Add to Pendentes at TOP (index 0)
            pendingMessages.put(msgKey, ev);
            logBoxPendente.getChildren().add(0, entry);
            if (logBoxPendente.getChildren().size() > 50) {
                logBoxPendente.getChildren().remove(logBoxPendente.getChildren().size() - 1);
            }
        } else if ("ACCEPT_PROPOSAL".equals(perf)) {
            // Remove from Pendentes if exists, add to Em andamento
            removeFromPending(msgKey, entry);
            inProgressMessages.put(msgKey, ev);
            logBoxEmAndamento.getChildren().add(0, entry);
            if (logBoxEmAndamento.getChildren().size() > 50) {
                logBoxEmAndamento.getChildren().remove(logBoxEmAndamento.getChildren().size() - 1);
            }
        } else if ("INFORM".equals(perf) || "FAILURE".equals(perf)) {
            // Remove from Em andamento and Pendentes if exists, add to Concluídas
            removeFromInProgress(msgKey, entry);
            removeFromPending(msgKey, entry);
            logBoxConcluida.getChildren().add(0, entry);
            if (logBoxConcluida.getChildren().size() > 50) {
                logBoxConcluida.getChildren().remove(logBoxConcluida.getChildren().size() - 1);
            }
        } else {
            // Other messages go to Em andamento
            logBoxEmAndamento.getChildren().add(0, entry);
            if (logBoxEmAndamento.getChildren().size() > 50) {
                logBoxEmAndamento.getChildren().remove(logBoxEmAndamento.getChildren().size() - 1);
            }
        }
    }

    private void removeFromPending(String msgKey, VBox entry) {
        pendingMessages.remove(msgKey);
        logBoxPendente.getChildren().remove(entry);
    }

    private void removeFromInProgress(String msgKey, VBox entry) {
        inProgressMessages.remove(msgKey);
        logBoxEmAndamento.getChildren().remove(entry);
    }

    // ── Content extractors ────────────────────────────────────────────────────
    private String extractLoad(String c) {
        if (c == null) return "";
        for (String p : c.split(";")) {
            if (p.trim().startsWith("load="))
                return p.split("=", 2)[1].trim() + "W";
        }
        return "";
    }

    private String vehicle(String c) {
        if (c == null) return "";
        for (String p : c.split(";")) {
            if (p.trim().startsWith("vehicle="))
                return p.split("=", 2)[1].trim();
        }
        return "";
    }

    private String price(String c) {
        if (c == null) return "";
        for (String p : c.split(";")) {
            if (p.trim().startsWith("price="))
                return p.split("=", 2)[1].trim();
        }
        return "";
    }

    private String guessTransport(String content) {
        String loadStr = extractLoad(content).replace("W", "").trim();
        if (loadStr.isEmpty()) return null;
        try {
            int load = Integer.parseInt(loadStr);
            if (load <= 3000) return "transport0";
            if (load <= 6000) return "transport1";
            return "transport2";
        } catch (Exception e) {
            return null;
        }
    }

    private Color senderColor(String type) {
        return switch (type) {
            case "consumer"  -> CONSUMER_COLOR;
            case "rental"    -> RENTAL_COLOR;
            case "transport"  -> TRANSPORT_COLOR;
            default           -> TEXT;
        };
    }

    private String truncate(String s, int len) {
        if (s == null) return "";
        return s.length() > len ? s.substring(0, len) + "…" : s;
    }

    // ── Public controls ───────────────────────────────────────────────────────
    public void clearLog() {
        Platform.runLater(() -> {
            logBoxPendente.getChildren().clear();
            logBoxEmAndamento.getChildren().clear();
            logBoxConcluida.getChildren().clear();
        });
        pendingMessages.clear();
        inProgressMessages.clear();
        totalRequests = confirmedRequests = failedRequests = 0;
        statTotal.setText("Total de Solicitações: 0");
        statConfirmed.setText("Confirmadas: 0");
        statFailed.setText("Falhas: 0");
        MessageQueue.clear();
        flows.clear();
        msgCanvas.getGraphicsContext2D().clearRect(0, 0, msgCanvas.getWidth(), msgCanvas.getHeight());
    }
}
