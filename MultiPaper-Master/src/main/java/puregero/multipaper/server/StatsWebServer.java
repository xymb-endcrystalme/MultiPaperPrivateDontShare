package puregero.multipaper.server;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class StatsWebServer implements HttpHandler {
    private final Gson gson = new Gson();
    private final HttpServer server;
    private long offlinePlayers = 0;
    private String size;
    private final Set<String> worlds = Set.of("world", "world_nether", "world_the_end");
    private final Path playerDataFolder = Path.of("world", "playerdata");

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
                    long totalSize = 0;
                    for (String world : worlds) {
                        try (var stream = Files.walk(Path.of(world), 200, FileVisitOption.FOLLOW_LINKS)) {
                            totalSize += stream.mapToLong(p -> {
                                try {
                                    if (Files.isRegularFile(p)) {
                                        return Files.size(p);
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                return 0;
                            }).sum();
                        }
                    }
                    size = humanReadableByteCountSI(totalSize);

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

    private record StatsResponse(long uniquePlayers, String size) {
    }

    public static String humanReadableByteCountSI(long bytes) {
        if (-1000 < bytes && bytes < 1000) {
            return bytes + " B";
        }
        CharacterIterator ci = new StringCharacterIterator("kMGTPE");
        while (bytes <= -999_950 || bytes >= 999_950) {
            bytes /= 1000;
            ci.next();
        }
        return String.format("%.1f %cB", bytes / 1000.0, ci.current());
    }
}
