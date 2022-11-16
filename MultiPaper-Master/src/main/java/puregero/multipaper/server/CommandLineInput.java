package puregero.multipaper.server;

import puregero.multipaper.server.util.MultithreadedRegionManager;
import java.util.Scanner;

public class CommandLineInput extends Thread {

    @Override
    public void run() {
        Scanner scanner = new Scanner(System.in);

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();

            if (line.equalsIgnoreCase("shutdown")) {
                System.out.println("Shutting down servers...");
                ServerConnection.shutdownAndWait();
                MultithreadedRegionManager.saveEverything();
                System.exit(0);
            } else {
                System.out.println("Unknown command, use 'shutdown' to shutdown all servers or ctrl+c to stop just this master server");
            }
        }
    }

}
