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
import javafx.scene.shape.StrokeType;
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
 * Main JavaFX Traffic Control UI — matches sketch-v1-traffic-control layout.
 * Dark dashboard with agent node canvas (left) and message log sidebar (right).
 */
public class TrafficControlUI {

    // Colour palette matching sketch-v1
    private static final Color BG = Color.rgb(13, 17, 23);
    private static final Color SURFACE = Color.rgb(22, 27, 34);
    private static final Color BORDER = Color.rgb(48, 54, 61);
    private static final Color TEXT = Color.rgb(230, 237, 243);
    private static final Color MUTED = Color.rgb(139, 148, 158);

    private static final Color CONSUMER_COLOR   = Color.rgb(35, 134, 54);
    private static final Color RENTAL_COLOR    = Color.rgb(163, 113, 247);
    private static final Color TRANSPORT_COLOR = Color.rgb(240, 136, 62);

    private static final Color STATUS_AVAILABLE = Color.rgb(35, 134, 54);
    private static final Color STATUS_BUSY     = Color.rgb(158, 106, 3);
    private static final Color STATUS_THINKING = Color.rgb(31, 111, 235);

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
    private final VBox logBox;
    private final Label statTotal, statConfirmed, statFailed;
    private final Button btnStart, btnStop;
    private final Label liveBadge;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");

    private int totalRequests   = 0;
    private int confirmedRequests = 0;
    private int failedRequests   = 0;

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

        statTotal      = new Label("Total Requests: 0");
        statConfirmed  = new Label("Confirmed: 0");
        statFailed     = new Label("Failed: 0");
        for (Label l : new Label[]{statTotal, statConfirmed, statFailed}) {
            l.setFont(Font.font("Segoe UI", 11));
            l.setTextFill(MUTED);
        }

        btnStart = new Button("▶ Start");
        btnStop  = new Button("■ Stop");
        styleBtn(btnStart, STATUS_AVAILABLE, Color.WHITE);
        styleBtn(btnStop,  Color.rgb(218, 54, 51), Color.WHITE);
        btnStart.setDisable(true);
        btnStop.setDisable(false);
        btnStart.setOnAction(e -> { running.set(true);  btnStart.setDisable(true);  btnStop.setDisable(false); });
        btnStop.setOnAction( e -> { running.set(false); btnStart.setDisable(false); btnStop.setDisable(true); });

        liveBadge = new Label("LIVE");
        liveBadge.setFont(Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 10.0));
        liveBadge.setTextFill(Color.WHITE);
        liveBadge.setBackground(new Background(new BackgroundFill(STATUS_AVAILABLE, new CornerRadii(12), null)));
        liveBadge.setPadding(new Insets(2, 8, 2, 8));
        liveBadge.setAlignment(Pos.CENTER);

        logBox = new VBox(5);
        logBox.setPadding(new Insets(8));
        logBox.setBackground(new Background(new BackgroundFill(BG, null, null)));
    }

    public Scene buildScene() {
        // Header bar
        Label title = new Label("Agent Traffic Control — SMA Generator Rental");
        title.setFont(Font.font("Segoe UI", javafx.scene.text.FontWeight.NORMAL, 15.0));
        title.setTextFill(Color.rgb(88, 166, 255));
        title.setLayoutY(12);

        HBox header = new HBox(title, liveBadge);
        header.setPrefHeight(44);
        header.setBackground(new Background(new BackgroundFill(BORDER, null, null)));
        header.setPadding(new Insets(0, 16, 0, 16));
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);
        liveBadge.setAlignment(Pos.CENTER);

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

        // Right sidebar
        Label logTitle = new Label("Message Timeline");
        logTitle.setFont(Font.font("Segoe UI", 12));
        logTitle.setTextFill(MUTED);
        logTitle.setPadding(new Insets(12, 16, 8, 16));
        logTitle.setBackground(new Background(new BackgroundFill(BORDER, null, null)));

        ScrollPane logScroll = new ScrollPane(logBox);
        logScroll.setFitToWidth(true);
        logScroll.setBackground(new Background(new BackgroundFill(SURFACE, null, null)));
        logScroll.setPrefWidth(340);
        logScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(logScroll, Priority.ALWAYS);

        VBox statsBar = new VBox(4);
        statsBar.setPadding(new Insets(10, 12, 10, 12));
        statsBar.setBackground(new Background(new BackgroundFill(BORDER, null, null)));
        statsBar.getChildren().addAll(statTotal, statConfirmed, statFailed);

        VBox controls = new VBox(8);
        controls.setPadding(new Insets(12));
        controls.setBackground(new Background(new BackgroundFill(SURFACE, null, null)));
        controls.getChildren().addAll(btnStart, btnStop);

        VBox rightPanel = new VBox(logTitle, logScroll, statsBar, controls);
        rightPanel.setPrefWidth(340);
        rightPanel.setBackground(new Background(new BackgroundFill(SURFACE, null, null)));
        VBox.setVgrow(logScroll, Priority.ALWAYS);

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
        if (name.equals("rental")) return Color.rgb(28, 26, 46);
        if (name.startsWith("transport")) return Color.rgb(26, 29, 36);
        return Color.rgb(13, 40, 32);
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

    private void styleBtn(Button btn, Color bg, Color fg) {
        btn.setBackground(new Background(new BackgroundFill(bg, null, null)));
        btn.setTextFill(fg);
        btn.setFont(Font.font("Segoe UI", 12));
        btn.setPrefHeight(34);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setCursor(javafx.scene.Cursor.HAND);
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
            Platform.runLater(() -> statTotal.setText("Total Requests: " + totalRequests));
            updateNode(sender, "requesting", extractLoad(ev.getContent()));
        }
        if ("INFORM".equals(perf) && sender.equals("rental")) {
            confirmedRequests++;
            Platform.runLater(() -> statConfirmed.setText("Confirmed: " + confirmedRequests));
        }
        if ("FAILURE".equals(perf)) {
            failedRequests++;
            Platform.runLater(() -> statFailed.setText("Failed: " + failedRequests));
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
    private void appendLog(AgentMessageEvent ev) {
        String sType = ev.getSenderType();
        Color border = senderColor(sType);
        String time  = timeFmt.format(LocalDateTime.now());

        VBox entry = new VBox(2);
        entry.setPadding(new Insets(6, 8, 6, 8));
        entry.setBackground(new Background(new BackgroundFill(BG, null, null)));
        entry.setBorder(new Border(new BorderStroke(
                border, BorderStrokeStyle.SOLID,
                new CornerRadii(4), new BorderWidths(3, 0, 0, 0))));

        Label timeLbl = new Label(time + "  " + ev.getSender() + " → " + ev.getReceiver());
        timeLbl.setFont(Font.font("Segoe UI", 10));
        timeLbl.setTextFill(MUTED);

        Label msgLbl = new Label(ev.getPerformativeName() + " | " + truncate(ev.getContent(), 80));
        msgLbl.setFont(Font.font("Segoe UI", 11));
        msgLbl.setTextFill(TEXT);

        entry.getChildren().addAll(timeLbl, msgLbl);
        logBox.getChildren().add(entry);

        if (logBox.getChildren().size() > 200) {
            logBox.getChildren().remove(0);
        }
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
        Platform.runLater(() -> logBox.getChildren().clear());
        totalRequests = confirmedRequests = failedRequests = 0;
        statTotal.setText("Total Requests: 0");
        statConfirmed.setText("Confirmed: 0");
        statFailed.setText("Failed: 0");
        MessageQueue.clear();
        flows.clear();
        msgCanvas.getGraphicsContext2D().clearRect(0, 0, msgCanvas.getWidth(), msgCanvas.getHeight());
    }
}