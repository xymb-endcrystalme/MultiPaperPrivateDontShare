package puregero.multipaper.mastermessagingprotocol.messages.serverbound;

import puregero.multipaper.mastermessagingprotocol.ExtendedByteBuf;

public class SetSecretMessage extends ServerBoundMessage {

    public final static byte COMPRESSION_DEFLATE = 0;
    public final static byte COMPRESSION_ZSTD = 1;

    public final String secret;
    public final byte chunkCompression;

    public SetSecretMessage(String secret, byte chunkCompression) {
        this.secret = secret;
        this.chunkCompression = chunkCompression;
    }

    public SetSecretMessage(ExtendedByteBuf byteBuf) {
        secret = byteBuf.readString();
        chunkCompression = byteBuf.readByte();
    }

    @Override
    public void write(ExtendedByteBuf byteBuf) {
        byteBuf.writeString(secret);
        byteBuf.writeByte(chunkCompression);
    }

    @Override
    public void handle(ServerBoundMessageHandler handler) {
        handler.handle(this);
    }
}
