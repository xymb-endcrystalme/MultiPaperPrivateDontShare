package puregero.multipaper.mastermessagingprotocol.messages.serverbound;

import puregero.multipaper.mastermessagingprotocol.ExtendedByteBuf;

public class ExecuteCommandMessage extends ServerBoundMessage {

    public final String command;

    public ExecuteCommandMessage(String command) {
        this.command = command;
    }

    public ExecuteCommandMessage(ExtendedByteBuf byteBuf) {
        command = byteBuf.readString();
    }

    @Override
    public void write(ExtendedByteBuf byteBuf) {
        byteBuf.writeString(command);
    }

    @Override
    public void handle(ServerBoundMessageHandler handler) {
        handler.handle(this);
    }
}
