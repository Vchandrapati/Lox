package translation;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
    final Environment globals = new Environment();
    private final List<Integer> randomNumbers = Arrays.asList(57, 97, 28, 7, 71, 1, 79, 83, 64, 82, 89, 24);
    private Environment environment = globals;
    private int randomIndex = 0;

    Interpreter () {
        globals.define("clock", new Callable() {
            @Override
            public int arity () {
                return 0;
            }

            @Override
            public Object call (Interpreter interpreter,
                                List<Object> arguments) {
                return (double) System.currentTimeMillis() / 1000.0;
            }

            @Override
            public String toString () {
                return "<native fn>";
            }
        });

        // Define the "floor" function.
        globals.define("floor", new Callable() {
            @Override
            public int arity () {
                return 1; // floor takes 1 argument.
            }

            @Override
            public Object call (Interpreter interpreter, List<Object> arguments) {
                if (arguments.getFirst() instanceof Double) {
                    return Math.floor((Double) arguments.getFirst());
                }

                return 0;
            }

            @Override
            public String toString () {
                return "<native fn>";
            }
        });

        globals.define("substring", new Callable() {
            @Override
            public int arity () {
                return 3;
            }

            @Override
            public Object call (Interpreter interpreter, List<Object> args) {
                if (args.getFirst() instanceof String && args.get(1) instanceof Double && args.get(2) instanceof Double) {
                    String str = (String) args.getFirst();
                    int start = (int) Math.floor((Double) args.get(1));
                    int end = (int) Math.floor((Double) args.get(2));

                    if (start < 0 || end > str.length() || start > end) {
                        return "";
                    }

                    return str.substring(start, end);
                }

                throw new RuntimeError(null, "Expected a string and two numbers.");
            }

            @Override
            public String toString () {
                return "<native fn>";
            }
        });
    }

    void interpret (List<Stmt> statements) {
        try {
            for (Stmt statement : statements) {
                execute(statement);
            }
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
        }
    }

    private void execute (Stmt stmt) {
        stmt.accept(this);
    }

    void executeBlock (List<Stmt> statements, Environment environment) {
        Environment previous = this.environment;
        try {
            this.environment = environment;
            for (Stmt statement : statements) {
                execute(statement);
            }
        } finally {
            this.environment = previous;
        }
    }

    @Override
    public Void visitBlockStmt (Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    private String stringify (Object object) {
        if (object == null)
            return "nil";

        if (object instanceof Double) {
            String text = object.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }

        return object.toString();
    }

    @Override
    public Void visitExpressionStmt (Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitIfStmt (Stmt.If stmt) {
        if (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch);
        } else
            if (stmt.elseBranch != null) {
                execute(stmt.elseBranch);
            }
        return null;
    }

    @Override
    public Void visitPrintStmt (Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visitWhileStmt (Stmt.While stmt) {
        while (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.body);
        }
        return null;
    }

    @Override
    public Void visitStringLoopStmt (Stmt.StringLoop stmt) {
        Object iteratingObject = evaluate(stmt.str);

        if (!(iteratingObject instanceof String)) {
            throw new RuntimeError(stmt.var, "'in' must be a string");
        }

        String str = (String) iteratingObject;

        for (int i = 0; i < str.length(); i++) {
            String stringChar = String.valueOf(str.charAt(i));
            environment.define(stmt.var.lexeme, stringChar);
            execute(stmt.body);
        }

        return null;
    }

    @Override
    public Object visitLogicalExpr (Expr.Logical expr) {
        Object left = evaluate(expr.left);

        if (expr.operator.type == TokenType.OR) {
            if (isTruthy(left))
                return left;
        } else {
            if (!isTruthy(left))
                return left;
        }

        return evaluate(expr.right);
    }

    @Override
    public Void visitReturnStmt (Stmt.Return stmt) {
        Object value = null;
        if (stmt.value != null)
            value = evaluate(stmt.value);

        throw new Return(value);
    }

    @Override
    public Void visitPrintOnlyStmt (Stmt.PrintOnly stmt) {
        return null;
    }

    @Override
    public Void visitFunctionStmt (Stmt.Function stmt) {
        Function function = new Function(stmt, environment);
        environment.define(stmt.name.lexeme, function);
        return null;
    }

    @Override
    public Void visitVarStmt (Stmt.Var stmt) {
        Object value = null;
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        }

        environment.define(stmt.name.lexeme, value);
        return null;
    }

    @Override
    public Object visitAssignExpr (Expr.Assign expr) {
        Object value = evaluate(expr.value);
        environment.assign(expr.name, value);
        return value;
    }

    @Override
    public Object visitLiteralExpr (Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitGroupingExpr (Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    private Object evaluate (Expr expr) {
        return expr.accept(this);
    }

    @Override
    public Object visitUnaryExpr (Expr.Unary expr) {
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case MINUS:
                checkNumberOperand(expr.operator, right);
                return -(double) right;
            case BANG:
                return !isTruthy(right);
        }

        // Unreachable.
        return null;
    }

    @Override
    public Object visitVariableExpr (Expr.Variable expr) {
        return environment.get(expr.name);
    }

    private void checkNumberOperand (Token operator, Object operand) {
        if (operand instanceof Double)
            return;
        throw new RuntimeError(operator, "Operand must be a number.");
    }

    private boolean isTruthy (Object object) {
        if (object == null)
            return false;
        if (object instanceof Boolean)
            return (boolean) object;
        return true;
    }

    @Override
    public Object visitDynamicLiteralExpr (Expr.DynamicLiteral expr) {
        if (expr.value == "READ") {
            System.out.print("input required > ");
            InputStreamReader input = new InputStreamReader(System.in);
            BufferedReader reader = new BufferedReader(input);
            String line;
            try {
                line = reader.readLine();
            } catch (Exception err) {
                return "";
            }
            return line;
        }

        if (expr.value == "RAND") {
            int randomValue = randomNumbers.get(randomIndex);
            randomIndex = (randomIndex + 1) % randomNumbers.size(); // Cycle through the list
            return (double) randomValue; // Return the next number as a double
        }

        return null;
    }

    @Override
    public Object visitBinaryExpr (Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return (double) left > (double) right;
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double) left >= (double) right;
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return (double) left < (double) right;
            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double) left <= (double) right;
            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (double) left - (double) right;
            case BANG_EQUAL:
                return !isEqual(left, right);
            case EQUAL_EQUAL:
                return isEqual(left, right);
            case PLUS:
                if (left instanceof Double && right instanceof Double) {
                    return (double) left + (double) right;
                }

                if (left instanceof String && right instanceof String) {
                    return (String) left + (String) right;
                }

                throw new RuntimeError(expr.operator, "Operands must be two numbers or two strings.");
            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                return (double) left / (double) right;
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double) left * (double) right;
        }

        // Unreachable.
        return null;
    }

    @Override
    public Object visitCallExpr (Expr.Call expr) {
        Object callee = evaluate(expr.callee);

        List<Object> arguments = new ArrayList<>();
        for (Expr argument : expr.arguments) {
            arguments.add(evaluate(argument));
        }

        if (!(callee instanceof Callable)) {
            throw new RuntimeError(expr.paren, "Can only call functions and classes.");
        }

        Callable function = (Callable) callee;

        if (arguments.size() != function.arity()) {
            throw new RuntimeError(expr.paren, "Expected " +
                    function.arity() + " arguments but got " +
                    arguments.size() + ".");
        }

        return function.call(this, arguments);
    }

    private void checkNumberOperands (Token operator,
                                      Object left, Object right) {
        if (left instanceof Double && right instanceof Double)
            return;

        throw new RuntimeError(operator, "Operands must be numbers.");
    }

    private boolean isEqual (Object a, Object b) {
        if (a == null && b == null)
            return true;
        if (a == null)
            return false;

        return a.equals(b);
    }
}