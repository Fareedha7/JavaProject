import java.util.Scanner;
import javafx.application.Application;

public class MainApplication {
    public static void main(String[] args) {
        // Use a single Scanner for the entire application to avoid issues with System.in
        Scanner scanner = new Scanner(System.in);
        boolean keepRunning = true;

        while (keepRunning) {
            System.out.println("\n========= UNIFIED MAIN MENU =========");
            System.out.println("1. Launch GUI Database Manager (JavaFX)");
            System.out.println("2. Launch Console Database Manager");
            System.out.println("3. Launch Console Calculator");
            System.out.println("4. Exit");
            System.out.print("Please choose an application to run (1-4): ");

            // It's better to check for input existence before reading
            if (!scanner.hasNextLine()) {
                System.out.println("No more input detected. Exiting.");
                break; 
            }
            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1":
                    System.out.println("Launching GUI Database Manager...");
                    // Note: You can only launch a JavaFX application once per JVM run.
                    // Subsequent calls will throw an IllegalStateException.
                    try {
                        // Launching in a new thread to avoid blocking the main menu
                        // in case the GUI needs to run concurrently.
                        // However, for this simple case, the direct launch is what you had.
                        Application.launch(FxDb.class, args);
                    } catch (IllegalStateException e) {
                        System.err.println("Error: JavaFX application can only be launched once.");
                        System.err.println("Please restart the main application to launch the GUI again.");
                    }
                    break;
                case "2":
                    // The DatabaseManager.run() method will now block until the user
                    // chooses to exit from it, at which point it will return here.
                    System.out.println("--- Entering Console Database Manager ---");
                    DatabaseManager.run(scanner);
                    System.out.println("--- Returning to Main Menu ---");
                    break;
                case "3":
                    // Same logic for the calculator.
                    System.out.println("--- Entering Console Calculator ---");
                    calculator.run(scanner);
                    System.out.println("--- Returning to Main Menu ---");
                    break;
                case "4":
                    System.out.println("Exiting program. Goodbye!");
                    keepRunning = false; // This is the ONLY place we should exit the main loop.
                    break;
                default:
                    System.err.println("Invalid choice. Please enter a number between 1 and 4.");
            }
        }
        
        // Close the scanner only when the application is truly finished.
        scanner.close();
    }
}