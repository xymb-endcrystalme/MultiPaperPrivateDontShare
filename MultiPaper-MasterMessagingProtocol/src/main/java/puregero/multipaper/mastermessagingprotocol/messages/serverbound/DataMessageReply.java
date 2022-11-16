package puregero.multipaper.mastermessagingprotocol.messages.serverbound;

import puregero.multipaper.mastermessagingprotocol.ExtendedByteBuf;

public class DataMessageReply extends ServerBoundMessage {

    public final static byte COMPRESSION_DEFLATE = 0;
    public final static byte COMPRESSION_ZSTD = 1;

    public final byte compressionType;
    public final byte[] data;

    public DataMessageReply(byte[] data) {
        this.compressionType = COMPRESSION_DEFLATE;
        this.data = data;
    }

    public DataMessageReply(byte[] data, byte compressionType) {
        this.compressionType = compressionType;
        this.data = data;
    }

    public DataMessageReply(ExtendedByteBuf byteBuf) {
        compressionType = byteBuf.readByte();
        data = new byte[byteBuf.readVarInt()];
        byteBuf.readBytes(data);
    }

    @Override
    public void write(ExtendedByteBuf byteBuf) {
        byteBuf.writeByte(compressionType);
        byteBuf.writeVarInt(data.length);
        byteBuf.writeBytes(data);
    }

    @Override
    public void handle(ServerBoundMessageHandler handler) {
        throw new UnsupportedOperationException("This message can only be handled in a reply");
    }
}
