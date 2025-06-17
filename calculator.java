import java.util.*;
import java.util.NoSuchElementException;

public class calculator {

    private static int precedence(char op) {
        if (op == '+' || op == '-') return 1;
        if (op == '*' || op == '/') return 2;
        return 0;
    }

    private static double applyOperation(double a, double b, char op) {
        switch (op) {
            case '+': return a + b;
            case '-': return a - b;
            case '*': return a * b;
            case '/':
                if (b == 0) throw new UnsupportedOperationException("Cannot divide by zero");
                return a / b;
            default: throw new IllegalArgumentException("Invalid operator: " + op);
        }
    }

    private static int checkBalance(String expr) {
        int balance = 0;
        for (char c : expr.toCharArray()) {
            if (c == '(') balance++;
            else if (c == ')') balance--;
        }
        return balance;
    }

    /**
     * Checks if the syntax of the expression is valid.
     * This is now stricter and checks for several common invalid patterns.
     */
    private static boolean isValidExpression(String expr) {
        String cleanExpr = expr.replaceAll("\\s+", "");
        
        if (cleanExpr.isEmpty()) return false;
        
        // Pattern 1: Empty parentheses
        if (cleanExpr.contains("()")) return false;
        
        // Pattern 2: Operator immediately before a closing parenthesis, e.g., "+)" or "* )"
        if (cleanExpr.matches(".[+\\-/]\\).*")) return false;
        
        // Pattern 3: Invalid operator immediately after an opening parenthesis, e.g., "(*" or "( /"
        // Note: We allow (+ or (- for unary signs like 5*(-3)
        if (cleanExpr.matches(".\\([/].*")) return false;
        
        // Pattern 4: An operator at the very start or end of the expression
        if (cleanExpr.matches("^[/+].|.[/+-]$")) return false;
        
        // Pattern 5: Double operators (e.g., ++, /). We allow things like 5-2.
        if (cleanExpr.matches(".[+\\-/][+/].")) return false; 
        
        // Final check for any disallowed characters
        for (char c : cleanExpr.toCharArray()) {
            if (!Character.isDigit(c) && "+-*/() .".indexOf(c) == -1) return false;
        }
        
        return true;
    }
    
    private static double evaluateExpression(String expression, Collection<Double> even, Collection<Double> odd) {
        even.clear();
        odd.clear();
        String expr = expression.replaceAll("\\s+", "")
                                .replaceAll("(?<=\\d)(?=\\()", "*")
                                .replaceAll("(?<=\\))(?=\\d)", "*")
                                .replaceAll("(?<=\\))(?=\\()", "*");

        LinkedList<Double> values = new LinkedList<>();
        LinkedList<Character> ops = new LinkedList<>();
        int i = 0;
        while (i < expr.length()) {
            char c = expr.charAt(i);
            if (Character.isDigit(c) || c == '.') {
                StringBuilder sbuf = new StringBuilder();
                while (i < expr.length() && (Character.isDigit(expr.charAt(i)) || expr.charAt(i) == '.')) {
                    sbuf.append(expr.charAt(i++));
                }
                i--;
                double num = Double.parseDouble(sbuf.toString());
                if (Math.round(num) % 2 == 0) even.add(num); else odd.add(num);
                values.add(num);
            } else if (c == '(') {
                ops.add(c);
            } else if (c == ')') {
                while (ops.getLast() != '(') {
                    char op = ops.removeLast();
                    try {
                        double b = values.removeLast();
                        double a = values.removeLast();
                        values.add(applyOperation(a, b, op));
                    } catch (NoSuchElementException e) {
                        throw new IllegalArgumentException("Invalid syntax near '" + op + "'. Check for missing numbers.");
                    }
                }
                ops.removeLast();
            } else {
                 // Handling for unary minus, e.g., 5 * -3 or -3 + 5
                if (c == '-' && (i == 0 || "+-*/(".indexOf(expr.charAt(i - 1)) != -1)) {
                    values.add(0.0); // Push a 0 to subtract the next number from
                }
                while (!ops.isEmpty() && precedence(ops.getLast()) >= precedence(c)) {
                    char op = ops.removeLast();
                    try {
                        double b = values.removeLast();
                        double a = values.removeLast();
                        values.add(applyOperation(a, b, op));
                    } catch (NoSuchElementException e) {
                         throw new IllegalArgumentException("Invalid syntax near '" + op + "'. Check for missing numbers.");
                    }
                }
                ops.add(c);
            }
            i++;
        }
        while (!ops.isEmpty()) {
            char op = ops.removeLast();
            try {
                double b = values.removeLast();
                double a = values.removeLast();
                values.add(applyOperation(a, b, op));
            } catch (NoSuchElementException e) {
                 throw new IllegalArgumentException("Invalid syntax near '" + op + "'. Check for missing numbers.");
            }
        }
        if (values.size() != 1) {
            throw new IllegalArgumentException("Invalid expression: The expression has leftover numbers that could not be processed.");
        }
        return values.getLast();
    }

    private static double arrayListMode(String expr, ArrayList<Double> even, ArrayList<Double> odd) {
        return evaluateExpression(expr, even, odd);
    }
    
    private static double linkedListMode(String expr, LinkedList<Double> even, LinkedList<Double> odd) {
        return evaluateExpression(expr, even, odd);
    }

    private static void addToQueueList(LinkedList<Queue<Double>> list, double number, int capacity) {
        if (list.isEmpty() || list.getLast().size() >= capacity) {
            list.add(new LinkedList<>());
        }
        list.getLast().add(number);
    }

    private static void extractNumbersToQueues(String expression, LinkedList<Queue<Double>> inputQueues, int capacity) {
        String expr = expression.replaceAll("\\s+", "").replaceAll("(?<=\\d)(?=\\()", "").replaceAll("(?<=\\))(?=\\d)", "").replaceAll("(?<=\\))(?=\\()", "*");
        int i = 0;
        while (i < expr.length()) {
            if (Character.isDigit(expr.charAt(i)) || expr.charAt(i) == '.') {
                StringBuilder sbuf = new StringBuilder();
                while (i < expr.length() && (Character.isDigit(expr.charAt(i)) || expr.charAt(i) == '.')) {
                    sbuf.append(expr.charAt(i++));
                }
                i--;
                double num = Double.parseDouble(sbuf.toString());
                addToQueueList(inputQueues, num, capacity);
            }
            i++;
        }
    }

    private static void distributeToEvenOddQueues(LinkedList<Queue<Double>> inputQueues, LinkedList<Queue<Double>> evenQueues, LinkedList<Queue<Double>> oddQueues, int capacity) {
        for (Queue<Double> q : inputQueues) {
            for (double number : q) {
                if (Math.round(number) % 2 == 0) {
                    addToQueueList(evenQueues, number, capacity);
                } else {
                    addToQueueList(oddQueues, number, capacity);
                }
            }
        }
    }

    private static void processQueueMode(String expr, int inputCap, int eoCap, LinkedList<Queue<Double>> inputQ, LinkedList<Queue<Double>> evenQ, LinkedList<Queue<Double>> oddQ) {
        extractNumbersToQueues(expr, inputQ, inputCap);
        distributeToEvenOddQueues(inputQ, evenQ, oddQ, eoCap);
    }

    private static void printQueueList(String label, LinkedList<Queue<Double>> queues) {
        System.out.println(label + ":");
        if (queues.isEmpty()) {
            System.out.println("  (None)");
            return;
        }
        int i = 1;
        for (Queue<Double> q : queues) {
            System.out.println("  Queue " + (i++) + " => " + q);
        }
    }

    private static String fixParentheses(String expr, Scanner scanner) {
        int balance = checkBalance(expr);
        if (balance < 0) {
            int missingCount = Math.abs(balance);
            String pluralSuffix = missingCount > 1 ? "s" : "";
            System.out.printf("--> Unbalanced expression: %d missing '(' character%s.\n", missingCount, pluralSuffix);
            System.out.println("--> This tool can only add ')'. Please re-enter the expression.");
            return null;
        }
        String missingChar = ")";
        int missingCount = balance;
        String pluralSuffix = missingCount > 1 ? "s" : "";
        System.out.printf("--> Unbalanced expression: %d missing '%s' character%s.\n", missingCount, missingChar, pluralSuffix);
        while (true) {
            System.out.printf("--> Enter a position (0 to %d) to insert one '%s', or type 'cancel' to re-enter expression: ", expr.length(), missingChar);
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("cancel")) {
                return null;
            }
            try {
                int pos = Integer.parseInt(input);
                if (pos >= 0 && pos <= expr.length()) {
                    String newExpr = expr.substring(0, pos) + missingChar + expr.substring(pos);
                    System.out.println("? Updated Expression: " + newExpr);
                    return newExpr;
                } else {
                    System.out.println("? Error: Position must be between 0 and " + expr.length() + ".");
                }
            } catch (NumberFormatException e) {
                System.out.println("? Input recognized as a new expression.");
                System.out.println("? Updated Expression: " + input);
                return input;
            }
        }
    }
    
    public static void run(Scanner scanner) {
        System.out.println("\n🔢 Welcome to the Multi-Mode Expression Calculator!");
        System.out.println("   (Type 'exit' at any time to return to the main menu)");

        mainLoop:
        while (true) {
            System.out.print("\nEnter a mathematical expression: ");
            String expr = scanner.nextLine();

            if (expr.equalsIgnoreCase("exit")) {
                System.out.println("Returning to main menu...");
                return;
            }
            
            while (checkBalance(expr) != 0) {
                expr = fixParentheses(expr, scanner);
                if (expr == null) {
                    System.out.println("Fix canceled. Please enter a new expression.");
                    continue mainLoop;
                }
            }
            
            if (!isValidExpression(expr)) {
                String cleanExpr = expr.replaceAll("\\s+", "");
                if (cleanExpr.matches(".[+\\-/]\\).*")) {
                    System.out.println("❌ Invalid Syntax: An operator cannot precede a closing parenthesis (e.g., '5+)').");
                } else if (cleanExpr.contains("()")) {
                    System.out.println("❌ Invalid Syntax: Expression contains empty parentheses '()'.");
                } else if (cleanExpr.matches(".\\([/].*")) {
                    System.out.println("❌ Invalid Syntax: An operator like '*' or '/' cannot follow an opening parenthesis.");
                } else {
                    System.out.println("❌ Invalid Expression. Please check for misplaced operators or invalid characters.");
                }
                continue;
            }

            System.out.println("--> Expression is balanced and seems valid. Well done!");

            boolean stayOnThisExpression = true;
            while (stayOnThisExpression) {
                System.out.println("\nSelect a Calculation Mode for: " + expr);
                System.out.println("1. ArrayList Mode");
                System.out.println("2. LinkedList Mode");
                System.out.println("3. Queue Mode");
                System.out.println("4. Enter a New Expression");
                System.out.println("5. Exit to Main Menu");
                System.out.print("Enter your choice (1-5): ");
                String modeChoice = scanner.nextLine();

                switch (modeChoice) {
                    case "1": {
                        ArrayList<Double> even = new ArrayList<>();
                        ArrayList<Double> odd = new ArrayList<>();
                        try {
                            double result = arrayListMode(expr, even, odd);
                            System.out.println("Result: " + result);
                            System.out.println("Even Numbers: " + even);
                            System.out.println("Odd Numbers: " + odd);
                        } catch (Exception e) {
                             System.out.println("❌ Error during calculation: " + e.getMessage());
                        }
                        break;
                    }
                    case "2": {
                        LinkedList<Double> even = new LinkedList<>();
                        LinkedList<Double> odd = new LinkedList<>();
                        try {
                            double result = linkedListMode(expr, even, odd);
                            System.out.println("Result: " + result);
                            System.out.print("Even Numbers: ");
                            even.forEach(e -> System.out.print(e + " -> "));
                            System.out.println("null");
                            System.out.print("Odd Numbers: ");
                            odd.forEach(o -> System.out.print(o + " -> "));
                            System.out.println("null");
                        } catch (Exception e) {
                             System.out.println("❌ Error during calculation: " + e.getMessage());
                        }
                        break;
                    }
                    case "3": {
                        try {
                            System.out.print("Enter Input Queue Capacity: ");
                            int inputCapacity = Integer.parseInt(scanner.nextLine());
                            System.out.print("Enter Even/Odd Queue Capacity: ");
                            int evenOddCapacity = Integer.parseInt(scanner.nextLine());

                            if (inputCapacity <= 0 || evenOddCapacity <= 0) {
                                System.out.println("❌ Error: Capacities must be positive numbers.");
                                continue;
                            }

                            LinkedList<Queue<Double>> inputQueues = new LinkedList<>();
                            LinkedList<Queue<Double>> evenQueues = new LinkedList<>();
                            LinkedList<Queue<Double>> oddQueues = new LinkedList<>();

                            processQueueMode(expr, inputCapacity, evenOddCapacity, inputQueues, evenQueues, oddQueues);
                            
                            double result = evaluateExpression(expr, new ArrayList<>(), new ArrayList<>());
                            System.out.println("\nResult: " + result);

                            printQueueList("Input Queues", inputQueues);
                            printQueueList("Even Queues", evenQueues);
                            printQueueList("Odd Queues", oddQueues);
                            System.out.println("--- Queue Summary ---" +
                                    "\n  ➤ Input Queues: " + inputQueues.size() +
                                    "\n  ➤ Even Queues: " + evenQueues.size() +
                                    "\n  ➤ Odd Queues: " + oddQueues.size());

                        } catch (NumberFormatException e) {
                            System.out.println("❌ Error: Invalid input. Please enter valid whole numbers for capacities.");
                        } catch (Exception e) {
                            System.out.println("❌ An unexpected error occurred: " + e.getMessage());
                        }
                        break;
                    }
                    case "4":
                        stayOnThisExpression = false;
                        break;
                    case "5":
                        System.out.println("Returning to main menu...");
                        return;
                    default:
                        System.out.println("Invalid choice. Please enter a number between 1 and 5.");
                }
            }
        }
    }
}