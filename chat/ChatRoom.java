package com.example.chat;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

public class ChatRoom {
    private final int roomId;
    private final Map<String, PrintWriter> clients = new HashMap<>();

    public ChatRoom(int roomId) {
        this.roomId = roomId;
    }
    public synchronized void addClient(String nickname, PrintWriter out) {
        clients.put(nickname, out);
        broadcast(nickname + "님이 입장했습니다.");
    }
    public synchronized void removeClient(String nickname, PrintWriter out) {
        clients.remove(nickname, out);
        broadcast(nickname + "님이 나갔습니다.");
    }
    public synchronized void broadcast(String message) {
        for (String clientNickname : clients.keySet()) {
            clients.get(clientNickname).println(message);
        }
    }
    public boolean isEmpty() {
        return clients.isEmpty();
    }

    public Map<String, PrintWriter> getClients() {
        return clients;
    }

}
