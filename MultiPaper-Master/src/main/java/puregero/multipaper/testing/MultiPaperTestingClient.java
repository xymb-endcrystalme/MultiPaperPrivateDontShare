package puregero.multipaper.testing;

import puregero.multipaper.mastermessagingprotocol.messages.masterbound.*;
import puregero.multipaper.mastermessagingprotocol.messages.serverbound.*;

import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

public class MultiPaperTestingClient {
    MasterConnection client = null;

    ArrayList<RequestChunk> requests = new ArrayList<>();

    void sleep(int ms) {
        try {Thread.sleep(ms);} catch (InterruptedException ex) {}
    }

    public void main(String[] args) {
        System.out.println("MultiPaper Master testing client!");

        client = new MasterConnection("127.0.0.1", 35353, "TestServer");
        client.connect();

        long start = System.nanoTime();
        int radius = 5000;
        System.out.println("Start! " + radius);
        for (int x = -radius / 16 ; x < radius / 16 ; x++) {
            for (int z = -radius / 16 ; z < radius / 16 ; z++) {
                requests.add(new RequestChunk(client, x, z));
            }
            sleep(50);
            System.out.println(x);
        }
        System.out.println("Chunks per second: " + (requests.size() * 1000000000. / (System.nanoTime() - start)));
        sleep(1000);

        int success = 0;
        int noReply = 0;
        int errorReply = 0;
        int dataReply = 0;
        int nullReply = 0;
        int anotherServerReply = 0;
        double latencyTotal = 0;
        long dataTotal = 0;
        int latencyCount = 0;
        ArrayList<Double> latencies = new ArrayList<>();

        for (RequestChunk chunk: requests) {
            if (chunk.responded) {
                success++;
                if (chunk.dataReply) dataReply++;
                if (chunk.anotherServerReply) anotherServerReply++;
                if (chunk.errorReply) errorReply++;
                if (chunk.nullReply) nullReply++;
                latencyTotal += chunk.latency;
                dataTotal += chunk.dataSize;
                latencyCount++;
                latencies.add(Double.valueOf(chunk.latency));
            } else noReply++;
        }

        Collections.sort(latencies);
        System.out.println("\nDone! Success: " + success + " NoReply: " + noReply + " ErrorReply: " + errorReply + " NullReply: " + nullReply + " DataReply: " + dataReply + " AnotherServerReply: " + anotherServerReply);
        if (latencyCount > 0) {
            System.out.println("Average data: " + (dataTotal / latencies.size()));
            System.out.println("\nLatency average: " + (latencyTotal / latencyCount) + " min: " + latencies.get(0).doubleValue() + " max: " + latencies.get(latencies.size() - 1).doubleValue());
            System.out.println("50%: " + latencies.get(latencies.size() / 2) + " 95%: " + latencies.get((int)(latencies.size() * 0.95)) + " 99%: " + latencies.get((int)(latencies.size() * 0.99)) + " 99.9%: " + latencies.get((int)(latencies.size() * 0.999)));
        }
    }

}
