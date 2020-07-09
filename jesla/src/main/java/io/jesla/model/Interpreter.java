package io.jesla.model;

import io.jesla.model.constant.StmtConstant;
import io.jesla.model.error.RuntimeError;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor, StmtConstant {

    private Environment environment = new Environment();

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        Object left = evaluate(expr.left);

        // short circuit
        if (expr.operator.type == TokenType.OR) {
            if (isTruthy(left)) return left;
        } else {
            if (!isTruthy(left)) return left;
        }

        return evaluate(expr.right);
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case BANG:
                return !isTruthy(right);
            case MINUS:
                return -(double)right;
        }

        // Unreachable.
        return null;
    }

    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        return environment.get(expr.name);
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value);

        environment.assign(expr.name, value);
        return value;
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return (double)left > (double)right;
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left >= (double)right;
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left < (double)right;
            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left <= (double)right;
            case BANG_EQUAL: return !isEqual(left, right);
            case EQUAL_EQUAL: return isEqual(left, right);
            case MINUS:
                checkNumberOperand(expr.operator, right);
                return (double)left - (double)right;
            case PLUS:
                if (left instanceof Double && right instanceof Double) {
                    return (double)left + (double)right;
                }
                if (left instanceof String || right instanceof String) {
                    return left.toString() + right.toString();
                }
                throw new RuntimeError(expr.operator,
                        "Operands must be two numbers or two strings.");
            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                checkDividend(expr.operator, right);
                return (double)left / (double)right;
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double)left * (double)right;
        }

        // Unreachable.
        return null;
    }

    @Override
    public Map<String, Object> visitBlockStmt(Stmt.Block stmt) {
        return executeBlock(stmt.statements, new Environment(environment));
    }

    private Map<String, Object> executeBlock(List<Stmt> statements, Environment environment) {
        Environment previous = this.environment;
        try {
            this.environment = environment;

            for (Stmt statement : statements) {
                Map<String, Object> result = execute(statement);
                if (result != null && (result.containsKey(RESULT_KEY_LOOP_BREAK)
                        || result.containsKey(RESULT_KEY_LOOP_CONTINUE))) {
                    return result;
                }
            }
            return null;
        } finally {
            this.environment = previous;
        }
    }

    @Override
    public Map<String, Object> visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Map<String, Object> visitPrintStmt(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Map<String, Object> visitVarStmt(Stmt.Var stmt) {
        Object value = null;
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        }

        environment.define(stmt.name.lexeme, value);
        return null;
    }

    @Override
    public Map<String, Object> visitForStmt(Stmt.For stmt) {
        execute(stmt.initializer);
        while (stmt.condition == null || isTruthy(evaluate(stmt.condition))) {
            Map<String, Object> result = execute(stmt.body);
            if (result != null && result.containsKey(RESULT_KEY_LOOP_BREAK)) {
                Object tag = result.getOrDefault(RESULT_KEY_LOOP_BREAK_TAG, "");
                if (!Objects.equals(tag, stmt.tag))
                    return result;
                return null;
            }
            if (result != null && result.containsKey(RESULT_KEY_LOOP_CONTINUE)) {
                Object tag = result.getOrDefault(RESULT_KEY_LOOP_CONTINUE_TAG, "");
                if (!Objects.equals(tag, stmt.tag))
                    return result;
            }
            execute(stmt.increment);
        }
        return null;
    }

    @Override
    public Map<String, Object> visitBreakStmt(Stmt.Break stmt) {
        Map<String, Object> resp = new HashMap<>();
        resp.put(RESULT_KEY_LOOP_BREAK, true);
        resp.put(RESULT_KEY_LOOP_BREAK_TAG, stmt.flag);
        return resp;
    }

    @Override
    public Map<String, Object> visitContinueStmt(Stmt.Continue stmt) {
        Map<String, Object> resp = new HashMap<>();
        resp.put(RESULT_KEY_LOOP_CONTINUE, true);
        resp.put(RESULT_KEY_LOOP_CONTINUE_TAG, stmt.flag);
        return resp;
    }

    @Override
    public Map<String, Object> visitIfStmt(Stmt.If stmt) {
        if (isTruthy(evaluate(stmt.condition))) {
            return execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            return execute(stmt.elseBranch);
        }
        return null;
    }

    void interpret(List<Stmt> statements) {
        try {
            for (Stmt statement : statements) {
                execute(statement);
            }
        } catch (RuntimeError error) {
            Jesla.runtimeError(error);
        }
    }

    private Map<String, Object> execute(Stmt stmt) {
        if (stmt == null)
            return null;
        return stmt.accept(this);
    }

    private void checkDividend(Token token, Object right) {
        if (right instanceof Double) {
            double v = Double.parseDouble(right.toString());
            if (v == 0) {
                throw new RuntimeError(token, "dividend cannot be zero");
            }
        }
    }

    void interpret(Expr expression) {
        try {
            Object value = evaluate(expression);
            System.out.println(stringify(value));
        } catch (RuntimeError error) {
            Jesla.runtimeError(error);
        }
    }

    private String stringify(Object object) {
        if (object == null) return "nil";

        // Hack. Work around Java adding ".0" to integer-valued doubles.
        if (object instanceof Double) {
            String text = object.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }

        return object.toString();
    }

    private boolean isTruthy(Object object) {
        if (object == null) return false;
        if (object instanceof Boolean) return (boolean)object;
        return true;
    }

    private boolean isEqual(Object a, Object b) {
        // nil is only equal to nil.
        if (a == null && b == null) return true;
        if (a == null) return false;

        return a.equals(b);
    }

    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double) return;
        throw new RuntimeError(operator, "Operand must be a number.");
    }

    private void checkNumberOperands(Token operator,
                                     Object left, Object right) {
        if (left instanceof Double && right instanceof Double) return;

        throw new RuntimeError(operator, "Operands must be numbers.");
    }

}
