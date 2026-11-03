package SimpleChatProject.src.server;

import SimpleChatProject.src.common.ChatIF;
import ocsf.server.AbstractServer;
import ocsf.server.ConnectionToClient;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class EchoServer extends AbstractServer {

    private final ChatIF serverUI;
    private boolean quitRequested;
    private volatile boolean closed = true;
    private volatile Thread listenerThread;
    private volatile CountDownLatch listenerStarted;

    public EchoServer(int port, ChatIF serverUI) {
        super(port);
        this.serverUI = serverUI;
    }

    @Override
    protected void handleMessageFromClient(
            Object msg, ConnectionToClient client) {
        String loginID = (String) client.getInfo("loginID");
        String message = String.valueOf(msg);
        String[] parts = message.trim().split("\\s+", 2);
        boolean loginCommand = parts[0].equals("#login");
        serverUI.display(loginCommand
                ? "Message received " + msg + " from " + loginID + "."
                : "Message received: " + msg + " from " + loginID);
        if (loginID == null) {
            if (!(msg instanceof String) || !loginCommand || parts.length != 2 || parts[1].trim().isEmpty()
                    || parts[1].trim().matches(".*\\s.*")) {
                reject(client, "ERROR - First message must be #login <loginID>.", true);
                return;
            }
            loginID = parts[1].trim();
            client.setInfo("loginID", loginID);
            String notice = loginID + " has logged on.";
            serverUI.display(notice);
            sendToAllClients(notice);
        } else if (loginCommand) {
            reject(client, "ERROR - #login is only allowed as the first message.", false);
        } else {
            sendToAllClients(loginID + "> " + message);
        }
    }

    private void reject(ConnectionToClient client, String message, boolean disconnect) {
        try {
            client.sendToClient(message);
        } catch (IOException e) {
            serverUI.display("Cannot send error to client: " + e.getMessage());
        } finally {
            if (disconnect) {
                try {
                    client.close();
                } catch (IOException e) {
                    serverUI.display("Cannot close client: " + e.getMessage());
                }
            }
        }
    }

    public void handleMessageFromServerUI(String message) {
        if (!message.startsWith("#")) {
            String outgoing = "SERVER MESSAGE> " + message;
            serverUI.display(outgoing);
            sendToAllClients(outgoing);
            return;
        }
        String[] parts = message.trim().split("\\s+", 2);
        String command = parts[0];
        try {
            switch (command) {
                case "#quit":
                    quitRequested = true;
                    shutdown();
                    break;
                case "#stop":
                    stopListening();
                    awaitListenerExit();
                    break;
                case "#close":
                    shutdown();
                    break;
                case "#start":
                    startListening();
                    break;
                case "#setport":
                    if (!closed) {
                        serverUI.display("ERROR - Close the server before changing port.");
                    } else if (parts.length != 2) {
                        serverUI.display("Usage: #setport <port>");
                    } else {
                        int port = Integer.parseInt(parts[1].trim());
                        if (port < 1 || port > 65535) {
                            throw new NumberFormatException();
                        }
                        setPort(port);
                        serverUI.display("port set to: " + getPort() + ".");
                    }
                    break;
                case "#getport":
                    serverUI.display("Port: " + getPort());
                    break;
                default:
                    serverUI.display("ERROR - Unknown command: " + command);
            }
        } catch (NumberFormatException e) {
            serverUI.display("Port must be between 1 and 65535.");
        } catch (IOException e) {
            serverUI.display("Server error: " + e.getMessage());
        }
    }

    public void startListening() throws IOException {
        if (isListening()) {
            serverUI.display("ERROR - Server is already listening.");
            return;
        }
        awaitListenerExit();
        listenerStarted = new CountDownLatch(1);
        listen();
        closed = false;
        try {
            if (!listenerStarted.await(5, TimeUnit.SECONDS)) {
                throw new IOException("Listener did not start in time.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while starting server.", e);
        }
    }

    public void shutdown() throws IOException {
        if (!closed) {
            sendToAllClients("SERVER SHUTTING DOWN! DISCONNECTING!");
            close();
        }
        awaitListenerExit();
    }

    private void awaitListenerExit() throws IOException {
        Thread previous = listenerThread;
        if (previous != null && previous != Thread.currentThread()) {
            try {
                previous.join(3000);
                if (previous.isAlive()) {
                    throw new IOException("Listener is still stopping. Try again.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while stopping server.", e);
            }
        }
    }

    public boolean isQuitRequested() {
        return quitRequested;
    }

    @Override
    public synchronized void sendToAllClients(Object msg) {
        super.sendToAllClients(msg);
    }

    @Override
    protected synchronized void clientConnected(ConnectionToClient client) {
        serverUI.display("A new client is attempting to connect to the server.");
    }

    @Override
    protected synchronized void clientDisconnected(ConnectionToClient client) {
        announceDisconnection(client);
    }

    @Override
    protected synchronized void clientException(ConnectionToClient client, Throwable exception) {
        announceDisconnection(client);
    }

    private void announceDisconnection(ConnectionToClient client) {
        if (Boolean.TRUE.equals(client.getInfo("disconnected"))) {
            return;
        }
        client.setInfo("disconnected", true);
        Object loginID = client.getInfo("loginID");
        String notice = (loginID == null ? "Unidentified client" : loginID) + " has disconnected.";
        serverUI.display(notice);
        sendToAllClients(notice);
    }

    @Override
    protected void serverStarted() {
        boolean initialDefaultStart = listenerThread == null && getPort() == 5555;
        listenerThread = Thread.currentThread();
        serverUI.display(initialDefaultStart
                ? "Server listening for clients on port " + getPort()
                : "Server listening for connections on port " + getPort() + ".");
        if (listenerStarted != null) {
            listenerStarted.countDown();
        }
    }

    @Override
    protected void serverStopped() {
        serverUI.display("Server has stopped listening for connections.");
        sendToAllClients("WARNING - Server has stopped listening for connections.");
    }

    @Override
    protected void serverClosed() {
        closed = true;
        serverUI.display("Server closed.");
    }

    @Override
    protected void listeningException(Throwable exception) {
        serverUI.display("Listening error: " + exception.getMessage());
    }
}
