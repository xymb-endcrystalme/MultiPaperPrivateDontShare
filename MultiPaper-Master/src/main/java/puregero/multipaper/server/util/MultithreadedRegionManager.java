package puregero.multipaper.server.util;

import java.io.*;
import java.lang.ref.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

import puregero.multipaper.mastermessagingprotocol.messages.masterbound.WriteChunkMessage;
import puregero.multipaper.mastermessagingprotocol.messages.serverbound.DataMessageReply;

public class MultithreadedRegionManager extends Thread {
    private static MultithreadedRegionManager single_instance = null;
    private final int THREADS = Integer.getInteger("regionfile.worker.threads", Runtime.getRuntime().availableProcessors());
    private static List<Thread> threads;
    private Thread regionFileNotifierThread;

    private static class GetChunkRequest {
        Consumer<DataMessageReply> callback;
        File basePath;
        int chunkX, chunkZ;
    }

    private static class PutChunkRequest {
        File basePath;
        WriteChunkMessage message;
    }

    private static class ChunkRequestWrapper {
        ConcurrentLinkedDeque<GetChunkRequest> getChunkRequests = new ConcurrentLinkedDeque<>();
        ConcurrentLinkedDeque<PutChunkRequest> putChunkRequests = new ConcurrentLinkedDeque<>();
        boolean busy = false;
        String fullPath;
    }

    private static final LinkedHashMap<String, ChunkRequestWrapper> chunkRequests = new LinkedHashMap<>();
    private static final ConcurrentHashMap<String, LinearRegionFile> cache = new ConcurrentHashMap<>();

    private static final int SOFT_MAX_CACHE_SIZE = Integer.getInteger("max.regionfile.cache.size", 256);
    private static final int REGION_SAVE_DELAY = Integer.getInteger("regionfile.save.delay", 30);
    private static final Object lock = new Object();
    private static long lastNotify = 0;
    private static boolean saveEverythingImmediately = false;

    static class RegionFileNotifier extends Thread {
        public void run() {
            System.out.println("Starting thread " + Thread.currentThread().getName());
            final long INTERVAL = 1000000000; // 1s
            long lastTick = System.nanoTime();
            while (true) {
                boolean somethingRequiresSaving = false;
                for (LinearRegionFile regionFile: cache.values()) {
                    if (regionFile.requiresSaving(REGION_SAVE_DELAY, saveEverythingImmediately)) {
                        notifyLock();
                        somethingRequiresSaving = true;
                    }
                }

                long newTick = System.nanoTime();
                long timeToSleep = (lastTick + INTERVAL - newTick) / 1000000;
                if (timeToSleep > 0)
                    try {Thread.sleep(timeToSleep);} catch (InterruptedException e) {}
                lastTick = newTick + INTERVAL;
            }
        }
    }

    private MultithreadedRegionManager() {
        if (threads == null) {
            threads = new ArrayList<>();
            for (int i = 0 ; i < THREADS ; i++) {
                String name = "MultithreadedRegionManager_" + String.valueOf(i);
                Thread thread = new Thread(this, name);
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                thread.start();
                threads.add(thread);
            }
            regionFileNotifierThread = new Thread(new RegionFileNotifier(), "MultithreadedRegionManager-RegionFileNotifier");
            regionFileNotifierThread.setPriority(Thread.NORM_PRIORITY - 2);
            regionFileNotifierThread.start();
        }
    }

    public static MultithreadedRegionManager i() {
        if (single_instance == null)
            single_instance = new MultithreadedRegionManager();
        return single_instance;
    }

    private static long waitOnLock(long lastKnownNotify) {
        long newLastNotify = lastNotify;
        synchronized (lock) {
            if (lastNotify > lastKnownNotify) return newLastNotify;
            try {lock.wait();} catch (InterruptedException ex) {}
        }
        return newLastNotify;
    }

    private static void notifyLock() {
        synchronized (lock) {
            lastNotify = System.nanoTime();
            lock.notifyAll();
        }
    }

    private LinearRegionFile getRegionFileForWriting(String fullPath) {
        synchronized (cache) {
            if (cache.containsKey(fullPath))
                return cache.get(fullPath);
        }

        LinearRegionFile regionFile = new LinearRegionFile(fullPath);
        cache.put(fullPath, regionFile);
        return regionFile;
    }

    private LinearRegionFile getRegionFileForReading(String fullPath) {
        synchronized (cache) {
            if (cache.containsKey(fullPath))
                return cache.get(fullPath);
        }

        LinearRegionFile regionFile = null;
        File regionFileFile = new File(fullPath);
        if (regionFileFile.canRead())
            regionFile = new LinearRegionFile(fullPath);
        if (regionFile != null) synchronized (cache) {cache.put(fullPath, regionFile);}
        return regionFile;
    }

    public void run() {
        int threadNo = Integer.valueOf(Thread.currentThread().getName().split("_", 2)[1]); // Yeah, I know. xD
        System.out.println("Starting thread " + Thread.currentThread().getName() + " thread no " + String.valueOf(threadNo));
        long lastKnownNotify = 0;
        while (true) {
            lastKnownNotify = waitOnLock(lastKnownNotify);

            ChunkRequestWrapper wrapper = null;
            synchronized (chunkRequests) {
                for (var candidate: chunkRequests.values()) {
                    if (candidate.busy) continue;
                    if (candidate.getChunkRequests.isEmpty() && candidate.putChunkRequests.isEmpty()) continue;
                    candidate.busy = true;
                    wrapper = candidate;
                    break;
                }
            }
            if (wrapper != null) {
                lastKnownNotify = 0;
                int count = 0;
                LinearRegionFile regionFile = getRegionFileForReading(wrapper.fullPath);
                Iterator it = wrapper.getChunkRequests.iterator();
                while (it.hasNext()) {
                    GetChunkRequest request = (GetChunkRequest)it.next();
                    it.remove();
                    if (regionFile == null) request.callback.accept(new DataMessageReply(new byte[0]));
                    else request.callback.accept(regionFile.getChunkPacket(request.chunkX, request.chunkZ));
                    count++;
                }
                boolean flush = false;
                if (!wrapper.putChunkRequests.isEmpty() && regionFile == null) {
                    flush = true;
                    regionFile = getRegionFileForWriting(wrapper.fullPath);
                }
                it = wrapper.putChunkRequests.iterator();
                while (it.hasNext()) {
                    PutChunkRequest request = (PutChunkRequest)it.next();
                    it.remove();
                    regionFile.putChunkPacket(request.message);
                }
                if (flush) {
                    try {
                        regionFile.flush();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
                synchronized (chunkRequests) {
                    wrapper.busy = false;
                    if (wrapper.getChunkRequests.isEmpty() && wrapper.putChunkRequests.isEmpty()) chunkRequests.remove(wrapper.fullPath);
                }
            }
            boolean flushed = false;
            for (LinearRegionFile regionFile: cache.values()) {
                if (regionFile.requiresSaving(REGION_SAVE_DELAY, saveEverythingImmediately)) {
                    boolean areWeSavingIt = false;
                    synchronized (regionFile) {
                        if (!regionFile.beingSaved) {
                            areWeSavingIt = true;
                            regionFile.beingSaved = true;
                        }
                    }

                    if (areWeSavingIt) {
                        try {regionFile.flush();} catch (IOException ex) {ex.printStackTrace();}
                        regionFile.beingSaved = false;
                        flushed = true;
                    }
                }
            }
            if (cache.size() > SOFT_MAX_CACHE_SIZE) {
                long minLastAccess = Long.MAX_VALUE;
                LinearRegionFile regionFileToRemove = null;
                for (LinearRegionFile regionFile: cache.values()) {
                    if (regionFile.lastAccess < minLastAccess) {
                        minLastAccess = regionFile.lastAccess;
                        regionFileToRemove = regionFile;
                    }
                }
                if (regionFileToRemove != null) {
                    boolean areWeSavingIt = false;
                    synchronized (regionFileToRemove) {
                        if (!regionFileToRemove.beingSaved) {
                            areWeSavingIt = true;
                            regionFileToRemove.beingSaved = true;
                        }
                    }

                    if (areWeSavingIt) {
                        try {regionFileToRemove.flush();} catch (IOException ex) {ex.printStackTrace();}
                        synchronized (chunkRequests) {
                            if (!chunkRequests.containsKey(regionFileToRemove.regionFileString)) {
                                System.out.println("Removing " + regionFileToRemove.regionFileString);
                                cache.remove(regionFileToRemove.regionFileString);
                            }
                        }
                        regionFileToRemove.beingSaved = false;
                    }
                }
            }
            if (saveEverythingImmediately && !flushed) {
                return;
            }
        }
    }

    private static String getFileForRegionFile(File regionDir, int chunkX, int chunkZ) {
        return new File(regionDir, "r." + (chunkX >> 5) + "." + (chunkZ >> 5) + ".linear").toString();
    }

    public static void putChunkDataAsync(File basePath, WriteChunkMessage message) {
        String regionFile = getFileForRegionFile(basePath, message.cx, message.cz);

        PutChunkRequest putChunkRequest = new PutChunkRequest();
        putChunkRequest.basePath = basePath;
        putChunkRequest.message = message;

        synchronized (chunkRequests) {
            if (!chunkRequests.containsKey(regionFile)) {
                ChunkRequestWrapper wrapper = new ChunkRequestWrapper();
                wrapper.fullPath = getFileForRegionFile(basePath, message.cx, message.cz);
                chunkRequests.put(regionFile, wrapper);
            }
            chunkRequests.get(regionFile).putChunkRequests.addLast(putChunkRequest);
        }
        notifyLock();
    }

    public static void getChunkDataAsync(File basePath, int chunkX, int chunkZ, Consumer<DataMessageReply> callback) {
        String regionFile = getFileForRegionFile(basePath, chunkX, chunkZ);

        GetChunkRequest getChunkRequest = new GetChunkRequest();
        getChunkRequest.basePath = basePath;
        getChunkRequest.chunkX = chunkX;
        getChunkRequest.chunkZ = chunkZ;
        getChunkRequest.callback = callback;

        synchronized (chunkRequests) {
            if (!chunkRequests.containsKey(regionFile)) {
                ChunkRequestWrapper wrapper = new ChunkRequestWrapper();
                wrapper.fullPath = getFileForRegionFile(basePath, chunkX, chunkZ);
                chunkRequests.put(regionFile, wrapper);
            }
            chunkRequests.get(regionFile).getChunkRequests.addLast(getChunkRequest);
        }
        notifyLock();
    }

    public static void saveEverything() {
        saveEverythingImmediately = true;
        notifyLock();
        try {
            for (Thread thread: threads) thread.join();
        } catch (InterruptedException ex) {}
    }
}
