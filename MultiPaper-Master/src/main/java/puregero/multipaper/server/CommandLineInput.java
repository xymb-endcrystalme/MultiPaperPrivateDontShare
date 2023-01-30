package puregero.multipaper.server;

import puregero.multipaper.mastermessagingprotocol.messages.serverbound.ExecuteCommandMessage;
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
            } if (line.startsWith("execute ")) {
                String command = line.substring("execute ".length());
                System.out.println("Executing command '" + command + "' on all servers...");
                ServerConnection.broadcastAll(new ExecuteCommandMessage(command));
            } else {
                    System.out.println("Unknown command, options:");
                    System.out.println("Use 'execute <command>' to execute a console command on all servers");
                    System.out.println("Use 'shutdown' to shutdown all servers or ctrl+c to stop just this master server");
            }
        }
    }

}
