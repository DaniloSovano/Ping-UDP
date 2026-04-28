package br.ufpa;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Scanner;
import java.util.Random;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        System.out.println("Escolha o cenário:\n1) Normal\n2) Burst (enviar vários sem esperar)\n3) Payload Variation\n4) Simulated Loss (cliente)\n5) Concurrent (multithread)");
        Scanner scanner = new Scanner(System.in);
        int choice = 1;
        try {
            String line = scanner.nextLine();
            if (!line.trim().isEmpty()) choice = Integer.parseInt(line.trim());
        } catch (Exception e) {
            System.out.println("Entrada inválida — usando cenário 1 (Normal)");
        }

        try {
            String address = "127.0.0.1";
            int port = 12000;

            switch (choice) {
                case 2:
                    runBurst(address, port, 20, 1000);
                    break;
                case 3:
                    runPayloadVariation(address, port);
                    break;
                case 4:
                    runSimulatedLoss(address, port, 0.3); // 30% drop
                    break;
                case 5:
                    runConcurrent(address, port, 5, 5);
                    break;
                case 1:
                default:
                    runNormal(address, port);
                    break;
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            scanner.close();
        }
    }

    // Cenário 1: comportamento padrão sequencial
    private static void runNormal(String address, int port) throws Exception {
        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(1000);
        InetAddress serverAddress = InetAddress.getByName(address);

        int sent = 0, received = 0, lost = 0;
        long totalRttNano = 0;

        Map<Integer, Long> sendTimes = new HashMap<>();

        for (int i = 1; i <= 10; i++) {
            String message = "PING|" + i;
            byte[] sendData = message.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, port);

            long tSend = System.nanoTime();
            sendTimes.put(i, tSend);
            socket.send(sendPacket);
            sent++;

            try {
                byte[] buf = new byte[2048];
                DatagramPacket rcv = new DatagramPacket(buf, buf.length);
                socket.receive(rcv);
                long tRecv = System.nanoTime();

                String reply = new String(rcv.getData(), 0, rcv.getLength());
                int seq = extractSeq(reply);
                long tSentRecorded = sendTimes.getOrDefault(seq, tSend);
                long rttNano = tRecv - tSentRecorded;
                double rttMs = rttNano / 1_000_000.0;

                totalRttNano += rttNano;
                received++;
                System.out.println("Resposta: " + reply + " | RTT: " + String.format("%.3f", rttMs) + " ms");

            } catch (SocketTimeoutException e) {
                System.out.println("Requisicao " + i + " esgotou o tempo limite.");
                lost++;
            }
        }

        System.out.println("\nEstatisticas:");
        System.out.println("Enviados: " + sent + " | Recebidos: " + received + " | Perdidos: " + lost);
        System.out.println("Taxa de perda: " + ((lost * 100.0) / sent) + "%");
        if (received > 0) {
            double avgMs = totalRttNano / (received * 1_000_000.0);
            System.out.println("RTT Medio: " + String.format("%.3f", avgMs) + " ms");
        }

        socket.close();
    }

    // Cenário 2: burst — enviar vários pacotes sem esperar e depois coletar respostas
    private static void runBurst(String address, int port, int batchSize, int timeoutMs) throws Exception {
        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(200);
        InetAddress serverAddress = InetAddress.getByName(address);

        Map<Integer, Long> sendTimes = new HashMap<>();
        int sent = 0, received = 0;
        long totalRttNano = 0;

        // Enviar em rajada
        for (int i = 1; i <= batchSize; i++) {
            String payload = "BURST|" + i + "|" + randomPayload(64);
            byte[] data = payload.getBytes();
            DatagramPacket p = new DatagramPacket(data, data.length, serverAddress, port);
            long t = System.nanoTime();
            sendTimes.put(i, t);
            socket.send(p);
            sent++;
        }

        // Receber até timeout total
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        byte[] buf = new byte[4096];
        while (System.nanoTime() < deadline && received < sent) {
            try {
                DatagramPacket rcv = new DatagramPacket(buf, buf.length);
                socket.receive(rcv);
                long tRecv = System.nanoTime();
                String reply = new String(rcv.getData(), 0, rcv.getLength());
                int seq = extractSeq(reply);
                long tSent = sendTimes.getOrDefault(seq, tRecv);
                long rtt = tRecv - tSent;
                totalRttNano += rtt;
                received++;
                System.out.println("Resposta: " + reply + " | RTT: " + String.format("%.3f", rtt / 1_000_000.0) + " ms");
            } catch (SocketTimeoutException e) {
                // continuar até deadline
            }
        }

        int lost = sent - received;
        System.out.println("\nEstatisticas (burst):");
        System.out.println("Enviados: " + sent + " | Recebidos: " + received + " | Perdidos: " + lost);
        System.out.println("Taxa de perda: " + ((lost * 100.0) / sent) + "%");
        if (received > 0) {
            double avg = totalRttNano / (received * 1_000_000.0);
            System.out.println("RTT Medio: " + String.format("%.3f", avg) + " ms");
        }

        socket.close();
    }

    // Cenário 3: variação de payload
    private static void runPayloadVariation(String address, int port) throws Exception {
        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(1000);
        InetAddress serverAddress = InetAddress.getByName(address);

        int[] sizes = {32, 128, 512, 1024};
        int sent = 0, received = 0, lost = 0;
        long totalRtt = 0;

        for (int i = 0; i < sizes.length; i++) {
            int size = sizes[i];
            String payload = "PAYLOAD|" + (i + 1) + "|" + randomPayload(size);
            byte[] data = payload.getBytes();
            DatagramPacket p = new DatagramPacket(data, data.length, serverAddress, port);
            long tSend = System.nanoTime();
            socket.send(p);
            sent++;

                try {
                    byte[] buf = new byte[2048];
                    DatagramPacket r = new DatagramPacket(buf, buf.length);
                    socket.receive(r);
                    long tRecv = System.nanoTime();
                    long rtt = tRecv - tSend;
                    totalRtt += rtt;
                    received++;
                    System.out.println("Tamanho=" + size + " bytes, RTT=" + String.format("%.3f", rtt / 1_000_000.0) + " ms");
                } catch (SocketTimeoutException e) {
                System.out.println("Tamanho=" + size + " bytes, timeout");
                lost++;
            }
        }

        System.out.println("\nEstatisticas (payload variation):");
        System.out.println("Enviados: " + sent + " | Recebidos: " + received + " | Perdidos: " + lost);
        if (received > 0) {
            System.out.println("RTT Medio: " + String.format("%.3f", (totalRtt / (received * 1_000_000.0))) + " ms");
        }

        socket.close();
    }

    // Cenário 4: cliente simula perda aleatória antes de enviar
    private static void runSimulatedLoss(String address, int port, double dropProb) throws Exception {
        Random rnd = new Random();
        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(1000);
        InetAddress serverAddress = InetAddress.getByName(address);

        int sent = 0, received = 0, dropped = 0, lost = 0;
        long totalRtt = 0;

        for (int i = 1; i <= 20; i++) {
            String payload = "LOSS|" + i;
            byte[] data = payload.getBytes();
            DatagramPacket p = new DatagramPacket(data, data.length, serverAddress, port);

            if (rnd.nextDouble() < dropProb) {
                System.out.println("Simulando perda do pacote " + i);
                dropped++;
                // não envia — simula perda na rede
            } else {
                long tSend = System.nanoTime();
                socket.send(p);
                sent++;
                try {
                    byte[] buf = new byte[1024];
                    DatagramPacket r = new DatagramPacket(buf, buf.length);
                    socket.receive(r);
                    long tRecv = System.nanoTime();
                    long rtt = tRecv - tSend;
                    totalRtt += rtt;
                    received++;
                    String reply = new String(r.getData(), 0, r.getLength());
                    System.out.println("Resposta: " + reply + " | RTT: " + String.format("%.3f", rtt / 1_000_000.0) + " ms");
                } catch (SocketTimeoutException e) {
                    System.out.println("Requisicao " + i + " esgotou o tempo limite.");
                    lost++;
                }
            }
        }

        System.out.println("\nEstatisticas (simulated loss):");
        System.out.println("Enviados: " + sent + " | Dropped(local): " + dropped + " | Recebidos: " + received + " | Perdidos: " + lost);
        if (received > 0) {
            System.out.println("RTT Medio: " + String.format("%.3f", (totalRtt / (received * 1_000_000.0))) + " ms");
        }

        socket.close();
    }

    // Cenário 5: concorrência — múltiplas threads cada uma enviando pings
    private static void runConcurrent(String address, int port, int threadCount, int pingsPerThread) throws Exception {
        ExecutorService exec = Executors.newFixedThreadPool(threadCount);
        List<Callable<int[]>> tasks = new ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            tasks.add(() -> {
                DatagramSocket s = new DatagramSocket();
                s.setSoTimeout(1000);
                InetAddress srv = InetAddress.getByName(address);
                int sent = 0, received = 0, lost = 0;
                long totalRtt = 0;

                for (int i = 1; i <= pingsPerThread; i++) {
                    String msg = "CT|" + Thread.currentThread().getName() + "|" + i;
                    byte[] d = msg.getBytes();
                    DatagramPacket p = new DatagramPacket(d, d.length, srv, port);
                    long ts = System.nanoTime();
                    s.send(p);
                    sent++;
                    try {
                        byte[] buf = new byte[1024];
                        DatagramPacket r = new DatagramPacket(buf, buf.length);
                        s.receive(r);
                        long tr = System.nanoTime();
                        totalRtt += (tr - ts);
                        received++;
                    } catch (SocketTimeoutException e) {
                        lost++;
                    }
                }

                s.close();
                return new int[] {sent, received, lost, (int) (totalRtt / 1_000_000)}; // last is ms total approx
            });
        }

        List<Future<int[]>> results = exec.invokeAll(tasks);
        exec.shutdown();

        int sent = 0, received = 0, lost = 0;
        long totalRttMs = 0;
        for (Future<int[]> f : results) {
            try {
                int[] r = f.get();
                sent += r[0];
                received += r[1];
                lost += r[2];
                totalRttMs += r[3];
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        System.out.println("\nEstatisticas (concurrent):");
        System.out.println("Threads: " + threadCount + " | Enviados: " + sent + " | Recebidos: " + received + " | Perdidos: " + lost);
        if (received > 0) {
            System.out.println("RTT Medio (aprox, ms): " + String.format("%.3f", (totalRttMs / (double) received)));
        }
    }

    private static int extractSeq(String msg) {
        // tenta extrair o primeiro número encontrado
        for (String part : msg.split("\\D+")) {
            if (!part.isEmpty()) {
                try {
                    return Integer.parseInt(part);
                } catch (NumberFormatException e) {
                }
            }
        }
        return -1;
    }

    private static String randomPayload(int size) {
        StringBuilder sb = new StringBuilder(size);
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        Random r = new Random();
        for (int i = 0; i < size; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
        return sb.toString();
    }
}