package puregero.multipaper.server.handlers;

import puregero.multipaper.mastermessagingprotocol.messages.masterbound.ReadChunkMessage;
import puregero.multipaper.mastermessagingprotocol.messages.serverbound.ChunkLoadedOnAnotherServerMessage;
import puregero.multipaper.mastermessagingprotocol.messages.serverbound.DataMessageReply;
import puregero.multipaper.server.*;
import puregero.multipaper.server.util.RegionFileCache;
import puregero.multipaper.server.util.MultithreadedRegionManager;

import java.io.File;

public class ReadChunkHandler {
    static long lastTimestamp = 0;
    static long counter = 0;
    static long counterLocal = 0;
    static long completed = 0;

    public static void handle(ServerConnection connection, ReadChunkMessage message) {
        long nano = System.nanoTime();
        if (nano - lastTimestamp > 1000000000) {
            System.out.println("CHUNKREADS " + String.valueOf(counterLocal) + " " + String.valueOf(counter - counterLocal) + " " + String.valueOf(completed) + " " + Thread.currentThread().getName());
            System.out.println("CHUNKLOCKS " + String.valueOf(ChunkLockManager.locks.size()));
            lastTimestamp = nano;
            counter = 0;
            counterLocal = 0;
            completed = 0;
        }

        counter++;

        if (checkIfLoadedOnAnotherServer(connection, message.world, message.path, message.cx, message.cz, message)) {
            return;
        }

        counterLocal++;
        MultithreadedRegionManager.i().getChunkDataAsync(getWorldDir(message.world, message.path), message.cx, message.cz, b -> {
            completed++;
            connection.sendReply(b, message);
        });
    }

    private static boolean checkIfLoadedOnAnotherServer(ServerConnection connection, String world, String path, int cx, int cz, ReadChunkMessage message) {
        if (path.equals("region")) {
            ServerConnection alreadyLoadedChunk = ChunkSubscriptionManager.getOwnerOrSubscriber(world, cx, cz);
            ChunkSubscriptionManager.subscribe(connection, world, cx, cz);
            if (alreadyLoadedChunk != null && alreadyLoadedChunk != connection) {
                connection.sendReply(new ChunkLoadedOnAnotherServerMessage(alreadyLoadedChunk.getBungeeCordName()), message);
                return true;
            }
        }

        if (path.equals("entities")) {
            ServerConnection alreadyLoadedEntities = EntitiesSubscriptionManager.getSubscriber(world, cx, cz);
            EntitiesSubscriptionManager.subscribe(connection, world, cx, cz);
            if (alreadyLoadedEntities != null && alreadyLoadedEntities != connection) {
                connection.sendReply(new ChunkLoadedOnAnotherServerMessage(alreadyLoadedEntities.getBungeeCordName()), message);
                return true;
            }
        }

        return false;
    }

    static File getWorldDir(String world, String path) {
        File file = new File(world);

        if (world.endsWith("_nether")) {
            file = new File(file, "DIM-1");
        }

        if (world.endsWith("_the_end")) {
            file = new File(file, "DIM1");
        }

        return new File(file, path);
    }
}
