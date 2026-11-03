package SimpleChatProject.src.client;

import SimpleChatProject.src.common.ChatIF;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ClientConsole implements ChatIF {

    public static final int DEFAULT_PORT = 5555;

    private final ChatClient client;

    public ClientConsole(String loginID, String host, int port) {
        client = new ChatClient(loginID, host, port, this);
    }

    public void accept() {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in)
        );

        try {
            String message;

            while (!client.isQuitRequested() && (message = reader.readLine()) != null) {

                client.handleMessageFromClientUI(message);
            }

        } catch (IOException e) {
            display("Console error: " + e.getMessage());
        } finally {
            client.disconnect();
        }
    }

    @Override
    public void display(String message) {
        System.out.println(message);
    }

    public static void main(String[] args) {
        if (args.length == 0 || args[0].trim().isEmpty()) {
            System.err.println("ERROR - No login ID specified.  Connection aborted.");
            return;
        }
        if (args.length > 3 || args[0].matches(".*\\s.*")) {
            System.err.println("Usage: ClientConsole <loginID> [host] [port]");
            return;
        }
        String host = args.length > 1 ? args[1] : "localhost";

        try {
            int port = args.length > 2
                    ? Integer.parseInt(args[2])
                    : DEFAULT_PORT;
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535.");
            }

            ClientConsole console =
                    new ClientConsole(args[0], host, port);

            console.accept();

        } catch (IllegalArgumentException e) {
            System.err.println(
                    "Client startup failed: " + e.getMessage()
            );
        }
    }
}


