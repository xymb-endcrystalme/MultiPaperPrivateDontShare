package puregero.multipaper.server.util;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.zip.*;
import java.util.ArrayList;

import puregero.multipaper.mastermessagingprotocol.messages.serverbound.DataMessageReply;
import puregero.multipaper.mastermessagingprotocol.messages.masterbound.WriteChunkMessage;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdOutputStream;
import com.github.luben.zstd.ZstdInputStream;
import net.jpountz.xxhash.XXHashFactory;

public class LinearRegionFile {
    private File regionFile;
    private final byte[][] buffer = new byte[32*32][];
    private final int[] bufferUncompressedSize = new int[32*32];
    private boolean requiresSaving = false;
    private long lastUpdate = 0;
    private final long SAVE_FORCE_INTERVAL = 10 * 1000000000;

    public boolean beingSaved = false;
    public long lastAccess = System.nanoTime();
    public String regionFileString;

    final byte COMPRESSION_LEVEL = 1;

    public LinearRegionFile(String regionFileString) {
        this.regionFileString = regionFileString;
        this.regionFile = new File(regionFileString);
        this.lastUpdate = System.nanoTime();

        for (int i = 0 ; i < 32 * 32 ; i++)
            this.bufferUncompressedSize[i] = 0;

        try {
            if (this.regionFile.canRead()) {
                long start = System.nanoTime();

                long fileLength = this.regionFile.length();
                FileInputStream fileStream = new FileInputStream(regionFile);
                DataInputStream rawDataStream = new DataInputStream(fileStream);

                long SUPERBLOCK = -4323716122432332390L;
                byte VERSION = 1;
                int HEADER_SIZE = 32;
                int FOOTER_SIZE = 8;

                long superBlock = rawDataStream.readLong();

                if (superBlock != SUPERBLOCK) {
                    System.out.println(this.regionFile.toString());
                    System.out.println("SUPERBLOCK INVALID!");
                    return;
                }

                byte version = rawDataStream.readByte();

                if (version != VERSION) {
                    System.out.println(this.regionFile.toString());
                    System.out.println("VERSION INVALID!");
                    return;
                }

                long newestTimestamp = rawDataStream.readLong();
                byte compressionLevel = rawDataStream.readByte();
                short chunkCount = rawDataStream.readShort();
                int dataCount = rawDataStream.readInt();

                if (fileLength != HEADER_SIZE + dataCount + FOOTER_SIZE) {
                    System.out.println(this.regionFile.toString());
                    System.out.println("FILE LENGTH INVALID! " + String.valueOf(fileLength) + " " + String.valueOf(HEADER_SIZE + dataCount + FOOTER_SIZE));
                    return;
                }

                long dataHash = rawDataStream.readLong();
                byte[] rawCompressed = new byte[dataCount];

                rawDataStream.readFully(rawCompressed, 0, dataCount);

                superBlock = rawDataStream.readLong();

                if (superBlock != SUPERBLOCK) {
                    System.out.println(this.regionFile.toString());
                    System.out.println("FOOTER SUPERBLOCK INVALID!");
                    return;
                }

                DataInputStream dataStream = new DataInputStream(new ZstdInputStream​(new ByteArrayInputStream(rawCompressed)));

                int completeDataCount = 0;
                int total = 4096 * 2;
                int starts[] = new int[32 * 32];
                int timestamps[] = new int[32 * 32];
                for(int i = 0 ; i < 32 * 32 ; i++) {
                    starts[i] = dataStream.readInt();
                    timestamps[i] = dataStream.readInt();
                }

                for(int i = 0 ; i < 32 * 32 ; i++) {
                    if(starts[i] > 0) {
                        int size = starts[i];
                        completeDataCount += size;
                        byte b[] = new byte[size];
                        dataStream.readFully(b, 0, size);

                        byte[] compressed = new byte[(int)Zstd.compressBound(b.length)];
                        long compressedLength = Zstd.compress(compressed, b, COMPRESSION_LEVEL, false);
                        b = new byte[(int)compressedLength];
                        for(int j = 0 ; j < compressedLength ; j++)
                            b[j] = compressed[j];

                        this.buffer[i] = b;
                        this.bufferUncompressedSize[i] = size;
                    }
                }
                System.out.println("Region load " + this.regionFile.toString() + " " + ((System.nanoTime() - start) / 1000000.));
                System.out.println("MEMORY " + String.valueOf(Runtime.getRuntime().totalMemory()) + "    " + String.valueOf(Runtime.getRuntime().freeMemory()));
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            System.out.println("Region file corrupted! " + this.regionFile);
            // TODO: Move to temp file and regenerate
        }
    }

    private static int getChunkIndex(int x, int z) {
        return (x & 31) + (z & 31) * 32;
    }

    public DataMessageReply getChunkPacket(int x, int z) {
        lastAccess = System.nanoTime();
        if (this.bufferUncompressedSize[getChunkIndex(x, z)] != 0) {
            return new DataMessageReply(this.buffer[getChunkIndex(x, z)], DataMessageReply.COMPRESSION_ZSTD);
        }
        return new DataMessageReply(new byte[0]);
    }

    private byte[] toByteArray(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] tempBuffer = new byte[4096];

        int length;
        while ((length = in.read(tempBuffer)) >= 0) {
            out.write(tempBuffer, 0, length);
        }

        return out.toByteArray();
    }

    public void putChunkPacket(WriteChunkMessage chunk) {
        lastAccess = System.nanoTime();
        if (chunk.compressionType == WriteChunkMessage.COMPRESSION_DEFLATE) {
            this.putDeflatedBytes(chunk.cx, chunk.cz, chunk.data);
        } else if (chunk.compressionType == WriteChunkMessage.COMPRESSION_ZSTD) {
            this.putZstdBytes(chunk.cx, chunk.cz, chunk.data, chunk.uncompressedSize);
        } else {
            System.out.println("Unknown compression!");
        }
    }

    private void putZstdBytes(int x, int z, byte[] b, int uncompressedSize) {
        if (b.length != 0) {
            synchronized (this.bufferUncompressedSize) {
                this.buffer[getChunkIndex(x, z)] = b;
                this.bufferUncompressedSize[getChunkIndex(x, z)] = uncompressedSize;
            }
        } else {
            synchronized (this.bufferUncompressedSize) {
                this.buffer[getChunkIndex(x, z)] = null;
                this.bufferUncompressedSize[getChunkIndex(x, z)] = 0;
            }
        }
        this.lastUpdate = System.nanoTime();
        this.requiresSaving = true;
    }

    private void putDeflatedBytes(int x, int z, byte[] b) {
        try {
            if (b.length != 0) {
                b = toByteArray(new InflaterInputStream(new ByteArrayInputStream(b)));

                int uncompressedSize = b.length;

                byte[] compressed = new byte[(int)Zstd.compressBound(b.length)];
                long compressedLength = Zstd.compress(compressed, b, COMPRESSION_LEVEL, false);
                b = new byte[(int)compressedLength];
                for(int j = 0 ; j < compressedLength ; j++)
                    b[j] = compressed[j];

                synchronized (this.bufferUncompressedSize) {
                    this.buffer[getChunkIndex(x, z)] = b;
                    this.bufferUncompressedSize[getChunkIndex(x, z)] = uncompressedSize;
                }
            } else {
                synchronized (this.bufferUncompressedSize) {
                    this.buffer[getChunkIndex(x, z)] = null;
                    this.bufferUncompressedSize[getChunkIndex(x, z)] = 0;
                }
            }
        } catch (IOException e) {
            System.out.println("PutDeflatedBytes exception " + e.toString() + " " + this.regionFile);
        }
        this.lastUpdate = System.nanoTime();
        this.requiresSaving = true;
    }

    public void flush() throws IOException {
        if (!this.requiresSaving) return;
        this.requiresSaving = false;

        int[] localBufferUncompressedSize = new int[32*32];
        byte[][] localBuffer = new byte[32*32][];
        synchronized (this.bufferUncompressedSize) {
            for (int i = 0 ; i < 32 * 32 ; i++) {
                localBufferUncompressedSize[i] = this.bufferUncompressedSize[i];
                localBuffer[i] = this.buffer[i];
            }
        }

        long start = System.nanoTime();

        long SUPERBLOCK = -4323716122432332390L;
        byte VERSION = 1;
        long timestamp = System.currentTimeMillis() / 1000L;
        short chunkCount = 0;

        File tempFile = new File(regionFile.toString() + ".tmp");
        Files.createDirectories(tempFile.toPath().getParent());
        FileOutputStream fileStream = new FileOutputStream(tempFile);

        ByteArrayOutputStream zstdByteArray = new ByteArrayOutputStream();
        ZstdOutputStream zstdStream = new ZstdOutputStream​(zstdByteArray, COMPRESSION_LEVEL);
        zstdStream.setChecksum​(true);
        DataOutputStream zstdDataStream = new DataOutputStream(zstdStream);
        DataOutputStream dataStream = new DataOutputStream(fileStream);

        dataStream.writeLong(SUPERBLOCK);
        dataStream.writeByte(VERSION);
        dataStream.writeLong(timestamp);
        dataStream.writeByte(COMPRESSION_LEVEL);

        boolean saveRegion = false;

        ArrayList<byte[]> byteBuffers = new ArrayList<byte[]>();
        for(int i = 0 ; i < 32 * 32 ; i++) {
            if(localBufferUncompressedSize[i] != 0) {
                saveRegion = true;
                chunkCount += 1;// TODO: localBuffer
                byte[] content = new byte[localBufferUncompressedSize[i]];
                long decompressedLength = Zstd.decompress(content, localBuffer[i]);
                if (decompressedLength != localBufferUncompressedSize[i])
                    throw new IOException("Buffer size invalid - " + decompressedLength + " vs " + localBufferUncompressedSize[i]);
                byteBuffers.add(content);
            } else byteBuffers.add(null);
        }
        for(int i = 0 ; i < 32 * 32 ; i++) {
            zstdDataStream.writeInt(localBufferUncompressedSize[i]);
            zstdDataStream.writeInt(0);
        }
        for(int i = 0 ; i < 32 * 32 ; i++) {
            if(byteBuffers.get(i) != null)
                zstdDataStream.write(byteBuffers.get(i), 0, byteBuffers.get(i).length);
        }
        zstdDataStream.close();

        dataStream.writeShort(chunkCount);

        byte[] compressed = zstdByteArray.toByteArray();

        dataStream.writeInt(compressed.length);
        dataStream.writeLong(XXHashFactory.fastestInstance().hash64().hash(compressed, 0, compressed.length, 0));

        dataStream.write(compressed, 0, compressed.length);
        dataStream.writeLong(SUPERBLOCK);

        dataStream.close();

        fileStream.close();
        Files.move(tempFile.toPath(), regionFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        if(saveRegion) {
            System.out.println("Region file flush " + regionFileString + " " + (System.nanoTime() - start) / 1000000.);
            System.out.println("MEMORY " + String.valueOf(Runtime.getRuntime().totalMemory()) + "    " + String.valueOf(Runtime.getRuntime().freeMemory()));
        }
    }

    boolean requiresSaving(int saveDelaySeconds, boolean saveEverythingImmediately) {
        return requiresSaving && ((System.nanoTime() - lastUpdate) / 1000000000 >= saveDelaySeconds || saveEverythingImmediately);
    }
}
