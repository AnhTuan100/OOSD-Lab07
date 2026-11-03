package SimpleChatProject.src.server;


import SimpleChatProject.src.common.ChatIF;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ServerConsole implements ChatIF {

    public static final int DEFAULT_PORT = 5555;

    private final EchoServer server;

    public ServerConsole(int port) throws IOException {
        server = new EchoServer(port, this);
        server.startListening();
    }

    public void accept() {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in)
        );

        try {
            String message;

            while (!server.isQuitRequested() && (message = reader.readLine()) != null) {
                server.handleMessageFromServerUI(message);
            }

        } catch (IOException e) {
            display("Server error: " + e.getMessage());
        } finally {
            try {
                server.shutdown();
            } catch (IOException e) {
                display("Cannot close server: " + e.getMessage());
            }
        }
    }

    @Override
    public void display(String message) {
        System.out.println(message);
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;

        try {
            if (args.length > 1) {
                throw new IllegalArgumentException("Usage: ServerConsole [port]");
            }
            if (args.length > 0) {
                port = Integer.parseInt(args[0]);
            }
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535.");
            }

            ServerConsole console = new ServerConsole(port);
            console.accept();

        } catch (IOException | IllegalArgumentException e) {
            System.err.println(
                    "Cannot start server: " + e.getMessage()
            );
        }
    }
}

