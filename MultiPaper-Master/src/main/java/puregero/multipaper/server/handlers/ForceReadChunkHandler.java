package puregero.multipaper.server.handlers;

import puregero.multipaper.mastermessagingprotocol.messages.masterbound.ForceReadChunkMessage;
import puregero.multipaper.mastermessagingprotocol.messages.serverbound.DataMessageReply;
import puregero.multipaper.server.ChunkLockManager;
import puregero.multipaper.server.ServerConnection;
import puregero.multipaper.server.util.RegionFileCache;
import puregero.multipaper.server.util.MultithreadedRegionManager;

import java.io.File;
import java.util.concurrent.CompletableFuture;

/**
 * Like ReadChunkHandler, but forces a read and won't redirect to another server that already has it loaded.
 */
public class ForceReadChunkHandler {
    public static void handle(ServerConnection connection, ForceReadChunkMessage message) {
        MultithreadedRegionManager.i().getChunkDataAsync(getWorldDir(message.world, message.path), message.cx, message.cz, b -> {
                connection.sendReply(b, message);
        });
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
