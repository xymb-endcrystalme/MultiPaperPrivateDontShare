package puregero.multipaper.testing;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;
import puregero.multipaper.mastermessagingprotocol.MessageBootstrap;
import puregero.multipaper.mastermessagingprotocol.messages.masterbound.MasterBoundMessage;
import puregero.multipaper.mastermessagingprotocol.messages.masterbound.MasterBoundProtocol;
import puregero.multipaper.mastermessagingprotocol.messages.serverbound.ServerBoundMessage;
import puregero.multipaper.mastermessagingprotocol.messages.serverbound.ServerBoundProtocol;
import puregero.multipaper.mastermessagingprotocol.messages.serverbound.ServerBoundMessageHandler;
import puregero.multipaper.mastermessagingprotocol.messages.serverbound.*;
import puregero.multipaper.mastermessagingprotocol.messages.masterbound.*;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@ChannelHandler.Sharable
public class MasterConnection extends ServerBoundMessageHandler {
    private SocketChannel channel;
    private final Set<MasterBoundMessage> unhandledRequests = ConcurrentHashMap.newKeySet();
    private boolean channelActive = false;
    private MessageBootstrap<ServerBoundMessage, MasterBoundMessage> bootstrap;
    private String address;
    private int port;
    private static final UUID thisServersUuid = UUID.randomUUID();
    private final String myName;

    public MasterConnection(String address, int port, String myName) {
        this.address = address;
        this.port = port;
        this.myName = myName;
    }

    public void connect() {
        bootstrap = new MessageBootstrap<>(new ServerBoundProtocol(), new MasterBoundProtocol(), channel -> channel.pipeline().addLast(this));
        System.out.println("Connecting to " + address + ":" + String.valueOf(port) + "...");
        bootstrap.connectTo(address, port).addListener(future -> {
            if (future.cause() != null) {
                CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS).execute(this::connect);
            }
        });
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        channel = (SocketChannel) ctx.channel();
        System.out.println("Connected to " + channel.remoteAddress());
        channel.write(new HelloMessage(myName, thisServersUuid));

        if (port >= 0) {
            channel.write(new SetPortMessage(port));
        }

        for (MasterBoundMessage unhandledRequest : unhandledRequests) {
            channel.write(unhandledRequest);
        }

        channelActive = true;
        channel.flush();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        channelActive = false;
        System.out.println("Lost connection to " + ((SocketChannel) ctx.channel()).remoteAddress());
        connect();
    }

    private void waitForActiveChannel() {
        while (channel == null || !channel.isActive() || !channelActive) {
            System.out.println(channel + " " + !channelActive);
            if (channel != null) System.out.println(!channel.isActive());
            // Wait for channel to become active
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void send(MasterBoundMessage message) {
        waitForActiveChannel();
        channel.writeAndFlush(message);
    }

    public void send(MasterBoundMessage message, Consumer<ServerBoundMessage> callback) {
        waitForActiveChannel();
        unhandledRequests.add(message);
        send(setCallback(message, reply -> {
            unhandledRequests.remove(message);
            callback.accept(reply);
        }));
    }

    public <T extends ServerBoundMessage> CompletableFuture<T> sendAndAwaitReply(MasterBoundMessage message, Class<T> expectedClass) {
        CompletableFuture<T> future = new CompletableFuture<>();

        send(message, reply -> {
            try {
                future.complete((T) reply);
            } catch (ClassCastException e) {
                e.printStackTrace();
            }
        });

        return future;
    }

    @Override
    public void handle(ServerInfoUpdateMessage message) {
        System.out.println("STUB: " + message.getClass().toString());
    }

    @Override
    public void handle(SetSecretMessage message) {
        System.out.println("STUB: " + message.getClass().toString());
    }

    @Override
    public void handle(ShutdownMessage message) {
        System.out.println("STUB: " + message.getClass().toString());
    }

    @Override
    public void handle(ServerChangedChunkStatusMessage message) {
        System.out.println("STUB: " + message.getClass().toString());
    }

    @Override
    public void handle(FileContentMessage message) {
        System.out.println("STUB: " + message.getClass().toString());
    }

    @Override
    public void handle(SetChunkOwnerMessage message) {
        System.out.println("STUB: " + message.getClass().toString());
    }

    @Override
    public void handle(ServerStartedMessage message) {
        System.out.println("STUB: " + message.getClass().toString());
    }

    @Override
    public void handle(DataUpdateMessage message) {
        System.out.println("STUB: " + message.getClass().toString());
    }

    @Override
    public void handle(AddChunkSubscriberMessage message) {
        System.out.println("STUB: " + message.getClass().toString());
    }

    @Override
    public void handle(RemoveChunkSubscriberMessage message) {
        System.out.println("STUB: " + message.getClass().toString());
    }

    @Override
    public void handle(AddEntitySubscriberMessage message) {
        System.out.println("STUB: " + message.getClass().toString());
    }

    @Override
    public void handle(RemoveEntitySubscriberMessage message) {
        System.out.println("STUB: " + message.getClass().toString());
    }

    @Override
    public void handle(ChunkSubscribersSyncMessage message) {
        System.out.println("STUB: " + message.getClass().toString());
    }

    @Override
    public void handle(EntitySubscribersSyncMessage message) {
        System.out.println("STUB: " + message.getClass().toString());
    }

    @Override
    public void handle(ExecuteCommandMessage message) {
        System.out.println("STUB: " + message.getClass().toString());
    }
}
