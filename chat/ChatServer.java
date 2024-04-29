package com.example.chat;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ChatServer {
    private static final int PORT_NUMBER = 12345; //서버 포트 번호
    private static final HashMap<String, PrintWriter> clients = new HashMap<>(); //client 관리
    private static final HashMap<Integer, ChatRoom> chatRooms = new HashMap<>(); // 방 관리
    private static final ArrayList<Integer> roomList = new ArrayList<>(); //room 방 관리
    private static int roomCount = 0;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT_NUMBER)) {
            System.out.println("서버가 작동중입니다.");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("새로운 연결 : " + clientSocket.getInetAddress().getHostAddress());

                // 클라이언트를 위한 새 스레드 시작
                //new Thread(new ClientHandler(clientSocket)).start();
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                Thread thread = new Thread(clientHandler);
                thread.start();
            }
        } catch (IOException e) {
            System.out.println(e);
            e.printStackTrace();
        }
    }

    static class ClientHandler implements Runnable {
        private Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;
        private String nickname;
        private boolean inRoom;
        private int currentRoom;

        public ClientHandler(Socket clientSocket) {
            try {
                this.clientSocket = clientSocket;
                this.out = new PrintWriter(clientSocket.getOutputStream(), true);
                this.in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                inRoom = false;
                currentRoom = -1;
            } catch (IOException e) {
                System.out.println("클라이언트 핸들러 초기화 중 오류: " + e.getMessage());
            }
        }


        @Override
        public void run() {
            try (PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            ) {
                // 클라이언트로부터 닉네임을 받아서 설정하기
                boolean nicknameSet = false;
                while (!nicknameSet) {
                    nickname = in.readLine();
                    if (nickname == null || nickname.trim().isEmpty()) {
                        out.println("닉네임을 공백으로 설정할 수 없습니다.");
                        continue;
                    }
                    synchronized (clients) {
                        if (clients.containsKey(nickname)) {
                            out.println("이미 사용 중인 닉네임입니다. 다른 닉네임을 선택해주세요.");
                            out.print("닉네임을 입력하세요 : ");
                        } else {
                            clients.put(nickname, out);
                            nicknameSet = true;
                        }
                    }
                }

                System.out.println(nickname + "님이 연결되었습니다.");
                // 클라이언트를 clients HashMap에 추가
                clients.put(nickname, out);
                //명령어 전송하기
                lobbyCommands();
                // 클라이언트 메시지 처리
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    if (!inRoom) {
                        handleLobbyCommands(inputLine); //로비 명령어
                    } else {
                        handleRoomCommands(inputLine); //방 명령어
                    }
                }

            } catch (IOException e) {
                System.out.println("클라이언트 처리 중 예외 발생: " + e.getMessage());
                if (inRoom) {
                    exitRoom();
                }
                clients.remove(nickname);
            }
        }

        //명령어 모음
        private void lobbyCommands() {
            out.println("방 목록 보기 : /list");
            out.println("방 생성 : /create");
            out.println("방 입장 : /join [방번호]");
            out.println("접속종료 : /bye");
            out.println("현재 접속 중인 모든 사용자 목록 : /users");
            out.println("귓속말 전송하기 : /whisper [닉네임] [메시지]");
        }
        private void roomCommands() {
            out.println("방에서 사용가능한 명령어");
            out.println("방 나가기 : /exit");
            out.println("접속종료 : /bye");
            out.println("현재 방에 있는 사용자 목록 : /roomusers");
            out.println("귓속말 전송하기 : /whisper [닉네임] [메시지]");
        }
        //대기실(로비)에서 명령어 처리
        private void handleLobbyCommands(String inputLine) {
            String[] parts = inputLine.split(" ");
            if (parts[0].equals("/join")) {
                try {
                    int roomNumber = Integer.parseInt(parts[1]);
                    joinRoom(roomNumber);
                } catch (NumberFormatException e) {
                    out.println("방 번호는 숫자로 입력해주세요. 예: /join 1");
                } catch (ArrayIndexOutOfBoundsException e) {
                    out.println("방 번호를 입력해주세요. 예: /join 1");
                }
            } else if (parts[0].equals("/whisper")) {
                // 비밀 메시지 보내기
                sendWhisper(parts);
            } else {
                switch (inputLine) {
                    case "/list":
                        sendRoomList();
                        break;
                    case "/create":
                        createRoom();
                        break;
                    case "/bye":
                        try {
                            out.println("접속을 종료합니다.");
                            clientSocket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        break;
                    case "/users":
                        showUsers();
                        break;
                    default:
                        out.println("원하시는 명령어를 찾을 수 없습니다. 다시 입력해주세요.");
                }
            }
        }
        //입장한 방(채팅)에서 명령어 처리
        private void handleRoomCommands(String inputLine) {
            switch (inputLine) {
                case "/exit":
                    exitRoom();
                    break;
                case "/roomusers":
                    showRoomUsers();
                    break;
                case "/bye":
                    try {
                        out.println("접속을 종료합니다.");
                        clientSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                default:
                    sendMessageToRoom(inputLine);
            }
        }
        //list 방 목록 보여주기
        private void sendRoomList() {
            if (roomList.isEmpty()) {
                out.println("현재 생성된 방이 없습니다.");
            } else {
                out.println("생성된 방 목록 :");
                for (int room : roomList) {
                    out.println(room);
                }
            }
        }
        //create 방 생성하기
        private void createRoom() {
            roomCount++;
            roomList.add(roomCount);
            out.println("방 번호 : " + roomCount + " 생성되었습니다.");
            joinRoom(roomCount);
        }
        //방 입장
        private void joinRoom(int roomNumber) {
            currentRoom = roomNumber;
            inRoom = true;
            ChatRoom chatRoom = chatRooms.getOrDefault(roomNumber, new ChatRoom(roomNumber));
            chatRooms.put(roomNumber, chatRoom);
            chatRoom.addClient(nickname, out);
            roomCommands();
        }
        //방 퇴장
        private void exitRoom() {
            ChatRoom chatRoom = chatRooms.get(currentRoom);
            chatRoom.removeClient(nickname, out);
            // 방이 비어 있는지 확인하고, 비어 있다면 방을 삭제하고 방 목록에서 제거
            if (chatRoom.isEmpty()) {
                chatRooms.remove(currentRoom);
                roomList.remove(Integer.valueOf(currentRoom));
                out.println("방 번호" + currentRoom + "이 삭제되었습니다.");

            }
            currentRoom = -1;
            inRoom = false;
            lobbyCommands();
        }
        // 현재 접속 중인 모든 사용자 보여 주기
        private void showUsers() {
            StringBuilder userList = new StringBuilder("현재 접속 중인 모든 사용자: ");
            userList.append(String.join(", ", clients.keySet()));
            userList.append(" (").append(clients.size()).append(")");
            out.println(userList.toString());
        }
        //현재 방 안에 접속 중인 사용자 보여 주기
        private void showRoomUsers() {
            ChatRoom chatRoom = chatRooms.get(currentRoom);
            Map<String, PrintWriter> roomClients = chatRoom.getClients();
            StringBuilder userList = new StringBuilder("현재 방 안에 접속 중인 사용자: ");
            userList.append(String.join(", ", roomClients.keySet()));
            userList.append(" (").append(roomClients.size()).append(")");
            out.println(userList.toString());
        }



        //전체 메시지
        private void broadcastMessageInRoom(String message) {
            for (PrintWriter clientWriter : clients.values()) {
                clientWriter.println(message);
            }
        }

        //방에 메시지 전송
        private void sendMessageToRoom(String message) {
            String formattedMessage = nickname + " : " + message;
            for (String client : clients.keySet()) {
                if (!client.equals(nickname) && clients.get(client) != null) {
                    clients.get(client).println(formattedMessage);
                }
            }
        }
        //귓속말 보내기
        private void sendWhisper(String[] parts) {
            if (parts.length < 3) {
                out.println("귓속말 사용법 : /whisper [닉네임] [메시지]");
                return;
            }
            String targetNickname = parts[1];
            String message = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));

            // 대상 사용자가 존재하는지 확인하고 메시지 전송
            if (clients.containsKey(targetNickname)) {
                PrintWriter targetWriter = clients.get(targetNickname);
                targetWriter.println("[귓속말] " + nickname + " : " + message);
                out.println("[귓속말] " + targetNickname + "님에게 메시지를 전송했습니다.");
            } else {
                out.println(targetNickname + " 사용자를 찾을 수 없습니다.");
            }
        }


    }
}
