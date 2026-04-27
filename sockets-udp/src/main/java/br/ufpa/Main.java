package br.ufpa;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

public class Main {
    public static void main(String[] args) {
        try {
            String address = "127.0.0.1";
            DatagramSocket socket = new DatagramSocket();
            socket.setSoTimeout(1000);
            InetAddress serverAddress = InetAddress.getByName(address);
            int port = 12000;

            int pacotesRecebidos = 0;
            int pacotesPerdidos = 0;
            long rttTotal = 0;

            for (int i = 1; i <= 10; i++) {
                String message = "Ping " + i;
                byte[] sendData = message.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, port);

                long tempoEnvio = System.currentTimeMillis();
                socket.send(sendPacket);

                try {
                    byte[] receiveData = new byte[1024];
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    
                    socket.receive(receivePacket);
                    long tempoRecebimento = System.currentTimeMillis();
                    long rtt = tempoRecebimento - tempoEnvio;

                    rttTotal += rtt;
                    pacotesRecebidos++;

                    String serverReply = new String(receivePacket.getData(), 0, receivePacket.getLength());
                    System.out.println("Resposta: " + serverReply + " | RTT: " + rtt + " ms");

                } catch (SocketTimeoutException e) {
                    System.out.println("Requisicao " + i + " esgotou o tempo limite.");
                    pacotesPerdidos++;
                }
            }

            System.out.println("\nEstatisticas:");
            System.out.println("Recebidos: " + pacotesRecebidos + " | Perdidos: " + pacotesPerdidos);
            System.out.println("Taxa de perda: " + (pacotesPerdidos * 10) + "%");
            if (pacotesRecebidos > 0) {
                System.out.println("RTT Medio: " + (rttTotal / pacotesRecebidos) + " ms");
            }

            socket.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}