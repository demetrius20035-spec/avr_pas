package io.github.avrpas.ast;

/**
 * Visitor over the AST. Used by the semantic analyzer and the code generator.
 */
public interface AstVisitor<R> {
    R visitProgram(Program n);
    R visitVarDecl(VarDecl n);
    R visitConstDecl(ConstDecl n);
    R visitProcDecl(ProcDecl n);

    // statements
    R visitCompound(CompoundStmt n);
    R visitAssign(AssignStmt n);
    R visitIf(IfStmt n);
    R visitWhile(WhileStmt n);
    R visitRepeat(RepeatStmt n);
    R visitFor(ForStmt n);
    R visitCallStmt(CallStmt n);
    R visitAsm(AsmStmt n);
    R visitEmpty(EmptyStmt n);

    // expressions
    R visitBinary(BinaryExpr n);
    R visitUnary(UnaryExpr n);
    R visitIntLiteral(IntLiteral n);
    R visitBoolLiteral(BoolLiteral n);
    R visitCharLiteral(CharLiteral n);
    R visitStringLiteral(StringLiteral n);
    R visitVarExpr(VarExpr n);
    R visitCallExpr(CallExpr n);
    R visitIndexExpr(IndexExpr n);
}
