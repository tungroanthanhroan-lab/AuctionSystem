package org.example.service;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class NetworkService {

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 8080;

    private static Socket socket;
    private static ObjectOutputStream out;
    private static ObjectInputStream in;

    private static void connectIfNeeded() throws Exception {
        if (socket == null || socket.isClosed()) {
            socket = new Socket(SERVER_HOST, SERVER_PORT);

            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();

            in = new ObjectInputStream(socket.getInputStream());
        }
    }

    public synchronized String sendMessage(String message) {
        try {
            connectIfNeeded();

            out.writeObject(message);
            out.flush();

            Object response = in.readObject();

            if (response instanceof String) {
                return (String) response;
            }

            return "ERROR|Server trả về dữ liệu không hợp lệ!";

        } catch (Exception e) {
            e.printStackTrace();
            closeConnection();
            return "ERROR|Không kết nối được tới server!";
        }
    }

    public static void closeConnection() {
        try {
            if (in != null) {
                in.close();
            }

            if (out != null) {
                out.close();
            }

            if (socket != null && !socket.isClosed()) {
                socket.close();
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            in = null;
            out = null;
            socket = null;
        }
    }
}