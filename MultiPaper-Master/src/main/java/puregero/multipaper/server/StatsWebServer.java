package puregero.multipaper.server;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class StatsWebServer implements HttpHandler {
    private final Gson gson = new Gson();
    private final HttpServer server;
    private long offlinePlayers = 0;
    private double size;
    private final Set<String> worlds = Set.of("world", "world_nether", "world_the_end");
    private final Path playerDataFolder = Path.of("world", "playerdata");
    private static final double MEGABYTE = 1024D * 1024D;

    public StatsWebServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this);
        server.setExecutor(null);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));

        Timer dataCrawlerTimer = new Timer("Stats Crawler Timer", true);
        dataCrawlerTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    for (String world : worlds) {
                        try (var stream = Files.walk(Path.of(world))) {
                            long bytesSize = stream.mapToLong(p -> {
                                try {
                                    return Files.size(p);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    return 0;
                                }
                            }).sum();
                            size = bytesSize / MEGABYTE / 1000.0D;
                        }
                    }

                    try (var stream = Files.list(playerDataFolder)) {
                        offlinePlayers = stream.count();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, 0, 60 * 60 * 1000); // Crawl data every hour
    }

    @Override
    public void handle(HttpExchange t) {
        String response = gson.toJson(new StatsResponse(
                offlinePlayers,
                size
        ));
        try {
            t.sendResponseHeaders(200, response.length());
        } catch (IOException e) {
            e.printStackTrace();
        }
        OutputStream os = t.getResponseBody();
        try {
            os.write(response.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private record StatsResponse(long uniquePlayers, double size) {
    }
}
