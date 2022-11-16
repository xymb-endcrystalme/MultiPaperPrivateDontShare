package puregero.multipaper.testing;

import puregero.multipaper.mastermessagingprotocol.messages.masterbound.*;
import puregero.multipaper.mastermessagingprotocol.messages.serverbound.*;

import java.util.concurrent.CompletableFuture;

public class RequestChunk {
    public boolean responded = false;
    public double latency = -1;
    public boolean dataReply = false;
    public boolean anotherServerReply = false;
    public boolean errorReply = false;
    public boolean nullReply = false;
    public int dataSize = 0;

    public RequestChunk(MasterConnection client, int x, int z) {
        long start = System.nanoTime();

        CompletableFuture<ServerBoundMessage> future = client.sendAndAwaitReply(new ReadChunkMessage("world", "region", x, z), ServerBoundMessage.class);
        future.thenAccept(message -> {
            latency = (System.nanoTime() - start) / 1000000.;

            if (message instanceof ChunkLoadedOnAnotherServerMessage chunkLoadedOnAnotherServerMessage) {
                anotherServerReply = true;
            } else if (message instanceof DataMessageReply dataMessageReply) {
                dataReply = true;
                if (dataMessageReply.data == null) nullReply = true;
                dataSize += dataMessageReply.data.length;
            } else {
                errorReply = true;
            }
            responded = true;
        });
    }
    
}
