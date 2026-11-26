package org.example.clientsevermsgexample;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * "User 2" window. Acts as the server side of the chat:
 * it listens on localhost:PORT, accepts a client, then both sides
 * can send and receive messages in real time.
 */
public class ServerView implements Initializable {

    public static final int PORT = 6667;

    @FXML private Button button_send;
    @FXML private TextField tf_message;
    @FXML private ScrollPane sp_main;
    @FXML private VBox vbox_messages;

    private ServerSocket serverSocket;
    private Socket clientSocket;
    private DataOutputStream out;
    private volatile boolean running = true;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // keep the scroll pane pinned to the newest message
        vbox_messages.heightProperty().addListener((obs, oldV, newV) -> sp_main.setVvalue(1.0));

        button_send.setOnAction(e -> sendMessage());
        tf_message.setOnAction(e -> sendMessage());   // Enter key

        Thread t = new Thread(this::runServer, "chat-server");
        t.setDaemon(true);
        t.start();
    }

    /** Waits for clients and reads their messages. */
    private void runServer() {
        try {
            // localhost only: bind to the loopback address
            serverSocket = new ServerSocket(PORT, 50, InetAddress.getLoopbackAddress());
            addSystemMessage("Server started on localhost:" + PORT + ". Waiting for User 1...");

            while (running) {
                Socket s = serverSocket.accept();
                synchronized (this) {
                    clientSocket = s;
                    out = new DataOutputStream(s.getOutputStream());
                }
                addSystemMessage("User 1 connected.");

                try (DataInputStream in = new DataInputStream(s.getInputStream())) {
                    while (running) {
                        String msg = in.readUTF();          // blocks until a message arrives
                        addMessage(msg, false);
                    }
                } catch (IOException e) {
                    if (running) addSystemMessage("User 1 disconnected.");
                } finally {
                    synchronized (this) {
                        out = null;
                        clientSocket = null;
                    }
                    try { s.close(); } catch (IOException ignored) { }
                }
            }
        } catch (IOException e) {
            if (running) addSystemMessage("Server error: " + e.getMessage());
        }
    }

    private void sendMessage() {
        String msg = tf_message.getText().trim();
        if (msg.isEmpty()) return;

        DataOutputStream o;
        synchronized (this) { o = out; }
        if (o == null) {
            addSystemMessage("No client connected yet.");
            return;
        }
        try {
            synchronized (o) {
                o.writeUTF(msg);
                o.flush();
            }
            addMessage(msg, true);
            tf_message.clear();
        } catch (IOException e) {
            addSystemMessage("Could not send message: " + e.getMessage());
        }
    }

    /** Adds a chat bubble. Safe to call from any thread. */
    private void addMessage(String text, boolean mine) {
        Platform.runLater(() -> {
            Label label = new Label(text);
            label.setWrapText(true);
            label.setMaxWidth(280);
            label.setPadding(new Insets(5, 10, 5, 10));
            label.setStyle(mine
                    ? "-fx-background-color: #0b93f6; -fx-text-fill: white; -fx-background-radius: 12;"
                    : "-fx-background-color: #e5e5ea; -fx-text-fill: black; -fx-background-radius: 12;");
            HBox row = new HBox(label);
            row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            row.setPadding(new Insets(3, 8, 3, 8));
            vbox_messages.getChildren().add(row);
        });
    }

    private void addSystemMessage(String text) {
        Platform.runLater(() -> {
            Label label = new Label(text);
            label.setStyle("-fx-text-fill: gray; -fx-font-style: italic;");
            HBox row = new HBox(label);
            row.setAlignment(Pos.CENTER);
            row.setPadding(new Insets(3, 8, 3, 8));
            vbox_messages.getChildren().add(row);
        });
    }

    /** Called when the window is closed. */
    public void shutdown() {
        running = false;
        try { if (clientSocket != null) clientSocket.close(); } catch (IOException ignored) { }
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) { }
    }
}
