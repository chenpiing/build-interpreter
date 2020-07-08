package io.jesla.model;

import java.util.List;
import java.util.Map;

abstract class Stmt {
    interface Visitor {
        Map<String, Object> visitBlockStmt(Block stmt);

        Map<String, Object> visitExpressionStmt(Expression stmt);

        Map<String, Object> visitIfStmt(If stmt);

        Map<String, Object> visitPrintStmt(Print stmt);

        Map<String, Object> visitVarStmt(Var stmt);

        Map<String, Object> visitForStmt(For stmt);

        Map<String, Object> visitBreakStmt(Break stmt);
    }

    abstract Map<String, Object> accept(Visitor visitor);


    static class Block extends Stmt {

        final List<Stmt> statements;

        public Block(List<Stmt> statements) {
            this.statements = statements;
        }

        @Override
        Map<String, Object> accept(Visitor visitor) {
            return visitor.visitBlockStmt(this);
        }
    }

    static class Expression extends Stmt {

        final Expr expression;

        public Expression(Expr expression) {
            this.expression = expression;
        }

        @Override
        Map<String, Object> accept(Visitor visitor) {
            return visitor.visitExpressionStmt(this);
        }
    }

    static class If extends Stmt {

        final Expr condition;
        final Stmt thenBranch;
        final Stmt elseBranch;

        public If(Expr condition, Stmt thenBranch, Stmt elseBranch) {
            this.condition = condition;
            this.thenBranch = thenBranch;
            this.elseBranch = elseBranch;
        }

        @Override
        Map<String, Object> accept(Visitor visitor) {
            return visitor.visitIfStmt(this);
        }
    }

    static class Print extends Stmt {

        final Expr expression;

        public Print(Expr expression) {
            this.expression = expression;
        }

        @Override
        Map<String, Object> accept(Visitor visitor) {
            return visitor.visitPrintStmt(this);
        }
    }

    static class Var extends Stmt {

        final Token name;
        final Expr initializer;

        public Var(Token name, Expr initializer) {
            this.name = name;
            this.initializer = initializer;
        }

        @Override
        Map<String, Object> accept(Visitor visitor) {
            return visitor.visitVarStmt(this);
        }
    }

    static class For extends Stmt {
        final Stmt initializer;
        final Expr condition;
        final Stmt body;
        final Stmt increment;
        final String tag;

        public For(Stmt initializer, Expr condition, Stmt body, Stmt increment) {
            this.initializer = initializer;
            this.condition = condition;
            this.body = body;
            this.increment = increment;
            this.tag = "";
        }

        public For(Stmt initializer, Expr condition, Stmt body, Stmt increment, String tag) {
            this.initializer = initializer;
            this.condition = condition;
            this.body = body;
            this.increment = increment;
            this.tag = tag == null ? "" : tag.trim();
        }

        @Override
        Map<String, Object> accept(Visitor visitor) {
            return visitor.visitForStmt(this);
        }
    }

    static class Break extends Stmt {
        final String flag;

        public Break() {
            flag = "";
        }

        public Break(String flag) {
            this.flag = flag == null ? "" : flag.trim();
        }

        @Override
        Map<String, Object> accept(Visitor visitor) {
            return visitor.visitBreakStmt(this);
        }
    }
}
