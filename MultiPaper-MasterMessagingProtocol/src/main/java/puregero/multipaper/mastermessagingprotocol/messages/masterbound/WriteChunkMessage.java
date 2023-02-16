package puregero.multipaper.mastermessagingprotocol.messages.masterbound;

import puregero.multipaper.mastermessagingprotocol.ExtendedByteBuf;

public class WriteChunkMessage extends MasterBoundMessage {

    public final static byte COMPRESSION_DEFLATE = 0;
    public final static byte COMPRESSION_ZSTD = 1;

    public final String world;
    public final String path;
    public final int cx;
    public final int cz;
    public final byte[] data;
    public final boolean isTransientEntities;
    public final byte compressionType;
    public final int uncompressedSize;

    public WriteChunkMessage(String world, String path, int cx, int cz, byte[] data, byte compressionType, int uncompressedSize) {
        this(world, path, cx, cz, data, false, compressionType, uncompressedSize);
    }

    public WriteChunkMessage(String world, String path, int cx, int cz, byte[] data, boolean isTransientEntities, byte compressionType, int uncompressedSize) {
        this.world = world;
        this.path = path;
        this.cx = cx;
        this.cz = cz;
        this.data = data;
        this.isTransientEntities = isTransientEntities;
        this.compressionType = compressionType;
        this.uncompressedSize = uncompressedSize;
    }

    public WriteChunkMessage(ExtendedByteBuf byteBuf) {
        world = byteBuf.readString();
        path = byteBuf.readString();
        cx = byteBuf.readInt();
        cz = byteBuf.readInt();
        data = new byte[byteBuf.readVarInt()];
        byteBuf.readBytes(data);
        isTransientEntities = byteBuf.readBoolean();
        compressionType = byteBuf.readByte();
        uncompressedSize = byteBuf.readInt();
    }

    @Override
    public void write(ExtendedByteBuf byteBuf) {
        byteBuf.writeString(world);
        byteBuf.writeString(path);
        byteBuf.writeInt(cx);
        byteBuf.writeInt(cz);
        byteBuf.writeVarInt(data.length);
        byteBuf.writeBytes(data);
        byteBuf.writeBoolean(isTransientEntities);
        byteBuf.writeByte(compressionType);
        byteBuf.writeInt(uncompressedSize);

    }

    @Override
    public void handle(MasterBoundMessageHandler handler) {
        handler.handle(this);
    }
}
