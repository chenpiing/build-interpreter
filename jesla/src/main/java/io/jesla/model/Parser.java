package io.jesla.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.jesla.model.TokenType.*;

public class Parser {

    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }

        return statements;
    }

    private Stmt declaration() {
        try {
            if (match(VAR)) return varDeclaration();

            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expect variable name.");

        Expr initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        }

        consume(SEMICOLON, "Expect ';' after variable declaration.");
        return new Stmt.Var(name, initializer);
    }

    private Stmt statement() {
        if (match(IF)) return ifStatement();
        if (match(PRINT)) return printStatement();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());
        if (match(WHILE)) return whileStatement("");
        if (match(FOR)) return forStatement("");
        if (match(BREAK)) return breakStatement();
        if (check(IDENTIFIER, COLON)) return loopFlagStatement();

        return expressionStatement();
    }

    /**
     * loop statement begin with
     * @return
     */
    private Stmt loopFlagStatement() {
        Token flagToken = consume(IDENTIFIER, "Expect identifier.");
        String flag = flagToken.lexeme;
        consume(COLON, String.format("Expect ':' after '%s'.", flag));
        if (match(WHILE))
            return whileStatement(flag);
        else if (match(FOR))
            return forStatement(flag);
        throw error(flagToken, "Invalid loop statement!");
    }

    private Stmt breakStatement() {
        if (check(SEMICOLON)) {
            consume(SEMICOLON, "Expect ';' after 'break'.");
            return new Stmt.Break();
        } else if (check(IDENTIFIER)) {
            Token flagToken = consume(IDENTIFIER, "Expect identifier after break.");
            consume(SEMICOLON, "Expect ';' after 'break'.");
            return new Stmt.Break(flagToken.lexeme);
        }
        throw error(peek(), "Invalid break statement!");
    }

    private Stmt forStatement(String flag) {
        consume(LEFT_PAREN, "Expect '(' after 'for'.");
        Stmt initializer;
        if (match(SEMICOLON)) {
            initializer = null;
        } else if (match(VAR)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }

        Expr condition = null;
        if (!check(SEMICOLON)) {
            condition = expression();
        }
        consume(SEMICOLON, "Expect ';' after loop condition.");

        Expr increment = null;
        if (!check(RIGHT_PAREN)) {
            increment = expression();
        }
        consume(RIGHT_PAREN, "Expect ')' after for clauses.");

        Stmt body = statement();

        return new Stmt.For(initializer, condition, body, new Stmt.Expression(increment), flag);
    }

    private Stmt whileStatement(String flag) {
        consume(LEFT_PAREN, "Expect '(' after 'while'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after condition.");
        Stmt body = statement();
        return new Stmt.For(null, condition, body, null, flag);
    }

    private Stmt ifStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'if'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after if condition.");

        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if (match(ELSE)) {
            elseBranch = statement();
        }
        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }

        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }

    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after expression.");
        return new Stmt.Expression(expr);
    }

    private Expr expression() {
        return assignment();
    }

    private Expr assignment() {
        // try to match along or -> and -> equality -> comparison -> addition -> multiplication -> unary -> primary
        // return the first matched Expr type
        Expr expr =  or();

        // if match any one of {=, +=, -=, *=, /=}
        if (match(EQUAL, PLUS_EQUAL, MINUS_EQUAL, STAR_EQUAL, SLASH_EQUAL)) {
            Token operator = previous();
            Expr value = assignment();

            // if l-value(ie, expr) is Variable type
            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable)expr).name;
                Token optr = null;
                switch (operator.type) {
                    case PLUS_EQUAL:
                        optr = new Token(PLUS, operator.lexeme, operator.literal, operator.line);
                        break;
                    case MINUS_EQUAL:
                        optr = new Token(MINUS, operator.lexeme, operator.literal, operator.line);
                        break;
                    case STAR_EQUAL:
                        optr = new Token(STAR, operator.lexeme, operator.literal, operator.line);
                        break;
                    case SLASH_EQUAL:
                        optr = new Token(SLASH, operator.lexeme, operator.literal, operator.line);
                        break;
                    case EQUAL:
                        return new Expr.Assign(name, value);
                }
                value = new Expr.Binary(expr, optr, value);
                return  new Expr.Assign(name, value);
            }

            // if match assignment operation but l-value is not Variable type, log error
            error(operator, "Invalid assignment target.");
        }

        return expr;
    }

    private Expr or() {
        Expr expr = and();

        while (match(OR)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr and() {
        Expr expr = equality();

        while (match(AND)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    /**
     * grammar:  equality → comparison ( ( "!=" | "==" ) comparison )* ;
     * equality is left associated, executed left to right
     */
    private Expr equality() {
        // consume first comparison expr as the left operand
        Expr left = comparison();

        // check whether match !=, == or not
        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            // if matched, take the previous consumed token is operator (!= or ==)
            Token operator = previous();
            // consume the comparision expr as the right operand
            Expr right = comparison();
            // build new binary expr using left operand expr, operator (!= or ==) and right operand expr
            // and make this new binary expr as left operand again
            left = new Expr.Binary(left, operator, right);
        }

        return left;
    }

    private Expr comparison() {
        Expr expr = addition();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = addition();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr addition() {
        Expr expr = multiplication();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = multiplication();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr multiplication() {
        Expr expr = unary();

        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return primary();
    }

    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        throw error(peek(), "Expect expression.");
    }

    /**
     * 消费指定type的token，如果当前token类型不符合，则抛异常
     * 有点assert的感觉
     */
    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();

        throw error(peek(), message);
    }

    /**
     * 当前token是否命中给定的type list，命中的话消费这个token，并返回true；否则返回false
     */
    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }

        return false;
    }

    /**
     * 判断当前token类型是否等于给定token类型
     */
    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    /**
     * check whether the two forthcoming token type match the given type
     */
    private boolean check(TokenType first, TokenType second) {
        return (current < tokens.size() - 1) && check(first) && tokens.get(current + 1).type == second;
    }

    /**
     * 返回当前token，且current加一
     */
    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private ParseError error(Token token, String message) {
        Jesla.error(token, message);
        return new ParseError();
    }

    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            if (previous().type == SEMICOLON) return;

            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }

            advance();
        }
    }

}
