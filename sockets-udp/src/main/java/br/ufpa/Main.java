package br.ufpa;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Main {
    public static void main( String[] args )
    {   
        try{
        String address = "127.0.0.1";
        DatagramSocket socket = new DatagramSocket();
        InetAddress serverAddress = InetAddress.getByName(address);
        int port = 12000;
        String message = "Ping";
        byte[] sendData = message.getBytes();
        
        DatagramPacket packet = new DatagramPacket(sendData, sendData.length,serverAddress,port);

        socket.send(packet);

        }catch (Exception e) {
            e.printStackTrace();
        }
    }
}
