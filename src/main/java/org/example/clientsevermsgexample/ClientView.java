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
import java.net.Socket;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * "User 1" window. Acts as the client side of the chat:
 * connects to the server on localhost:PORT and then sends/receives
 * messages in real time.
 */
public class ClientView implements Initializable {

    @FXML private Button button_send;
    @FXML private TextField tf_message;
    @FXML private ScrollPane sp_main;
    @FXML private VBox vbox_messages;

    private Socket socket;
    private DataOutputStream out;
    private volatile boolean running = true;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        vbox_messages.heightProperty().addListener((obs, oldV, newV) -> sp_main.setVvalue(1.0));

        button_send.setOnAction(e -> sendMessage());
        tf_message.setOnAction(e -> sendMessage());   // Enter key

        Thread t = new Thread(this::runClient, "chat-client");
        t.setDaemon(true);
        t.start();
    }

    /** Connects (retrying until the server is up) and then reads incoming messages. */
    private void runClient() {
        boolean told = false;
        while (running && socket == null) {
            try {
                Socket s = new Socket("localhost", ServerView.PORT);
                synchronized (this) {
                    socket = s;
                    out = new DataOutputStream(s.getOutputStream());
                }
            } catch (IOException e) {
                if (!told) {
                    addSystemMessage("Waiting for User 2 (server) on localhost:" + ServerView.PORT + "...");
                    told = true;
                }
                try { Thread.sleep(1000); } catch (InterruptedException ie) { return; }
            }
        }
        if (!running) return;

        addSystemMessage("Connected to User 2.");

        try (DataInputStream in = new DataInputStream(socket.getInputStream())) {
            while (running) {
                String msg = in.readUTF();               // blocks until a message arrives
                addMessage(msg, false);
            }
        } catch (IOException e) {
            if (running) addSystemMessage("Disconnected from User 2.");
        } finally {
            synchronized (this) { out = null; }
            try { socket.close(); } catch (IOException ignored) { }
        }
    }

    private void sendMessage() {
        String msg = tf_message.getText().trim();
        if (msg.isEmpty()) return;

        DataOutputStream o;
        synchronized (this) { o = out; }
        if (o == null) {
            addSystemMessage("Not connected to the server yet.");
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
        try { if (socket != null) socket.close(); } catch (IOException ignored) { }
    }
}
