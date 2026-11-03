package SimpleChatProject.src.client;

import SimpleChatProject.src.common.ChatIF;
import ocsf.client.AbstractClient;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ChatClient extends AbstractClient {

    private final ChatIF clientUI;
    private String loginID;
    private boolean quitRequested;
    private volatile Thread readerThread;
    private volatile CountDownLatch loginSent;
    private volatile IOException loginFailure;

    public ChatClient(String loginID, String host, int port, ChatIF clientUI) {
        super(host, port);
        this.loginID = loginID;
        this.clientUI = clientUI;
        connect();
    }

    @Override
    protected void handleMessageFromServer(Object msg) {
        clientUI.display(String.valueOf(msg));
    }

    public void handleMessageFromClientUI(String message) {
        if (message.startsWith("#")) {
            handleCommand(message);
        } else if (!isConnected()) {
            clientUI.display("Not connected. Use #login to connect.");
        } else {
            try {
                sendToServer(message);
            } catch (IOException e) {
                clientUI.display("Cannot send message to server: " + e.getMessage());
            }
        }
    }

    private void handleCommand(String message) {
        String[] parts = message.trim().split("\\s+", 2);
        String command = parts[0];
        String argument = parts.length == 2 ? parts[1].trim() : "";
        switch (command) {
            case "#quit":
                quitRequested = true;
                disconnect();
                break;
            case "#logoff":
                disconnect();
                break;
            case "#login":
                if (isConnected()) {
                    clientUI.display("ERROR - Already connected. Use #logoff first.");
                } else if (!argument.isEmpty() && argument.matches(".*\\s.*")) {
                    clientUI.display("Usage: #login [loginID]");
                } else {
                    if (!argument.isEmpty()) {
                        loginID = argument;
                    }
                    connect();
                }
                break;
            case "#sethost":
            case "#setport":
                if (isConnected()) {
                    clientUI.display("ERROR - Log off before changing host or port.");
                } else if (argument.isEmpty()) {
                    clientUI.display("Usage: " + command + " <value>");
                } else if (command.equals("#sethost")) {
                    setHost(argument);
                    clientUI.display("Host set to: " + getHost());
                } else {
                    try {
                        int port = Integer.parseInt(argument);
                        if (port < 1 || port > 65535) {
                            throw new NumberFormatException();
                        }
                        setPort(port);
                        clientUI.display("port set to: " + getPort() + ".");
                    } catch (NumberFormatException e) {
                        clientUI.display("Port must be between 1 and 65535.");
                    }
                }
                break;
            case "#gethost":
                clientUI.display("Host: " + getHost());
                break;
            case "#getport":
                clientUI.display("Port: " + getPort());
                break;
            default:
                clientUI.display("ERROR - Unknown command: " + command);
        }
    }

    private void connect() {
        try {
            awaitReaderExit();
            loginFailure = null;
            loginSent = new CountDownLatch(1);
            openConnection();
            if (!loginSent.await(5, TimeUnit.SECONDS)) {
                closeConnection();
                throw new IOException("Login could not be sent in time.");
            }
            if (loginFailure != null) {
                throw loginFailure;
            }
        } catch (IOException e) {
            clientUI.display("Cannot open connection.  Awaiting command.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            quitRequested = true;
            disconnect();
        }
    }

    public void disconnect() {
        try {
            if (isConnected()) {
                closeConnection();
            }
            awaitReaderExit();
        } catch (IOException e) {
            clientUI.display("Cannot close connection: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void awaitReaderExit() throws InterruptedException, IOException {
        Thread previous = readerThread;
        if (previous != null && previous != Thread.currentThread()) {
            previous.join(3000);
            if (previous.isAlive()) {
                throw new IOException("Previous connection is still closing.");
            }
        }
    }

    public boolean isQuitRequested() {
        return quitRequested;
    }

    @Override
    protected void connectionEstablished() {
        readerThread = Thread.currentThread();
        try {
            sendToServer("#login " + loginID);
        } catch (IOException e) {
            loginFailure = e;
            try {
                closeConnection();
            } catch (IOException closeError) {
                clientUI.display("Cannot close connection: " + closeError.getMessage());
            }
        } finally {
            loginSent.countDown();
        }
    }

    @Override
    protected void connectionClosed() {
        clientUI.display("Connection closed.");
    }

    @Override
    protected void connectionException(Exception e) {
        clientUI.display("Abnormal termination of connection.");
    }
}

