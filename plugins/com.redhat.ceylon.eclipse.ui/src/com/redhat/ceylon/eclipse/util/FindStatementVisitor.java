package com.redhat.ceylon.eclipse.util;

import com.redhat.ceylon.compiler.typechecker.tree.NaturalVisitor;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;

public class FindStatementVisitor extends Visitor 
        implements NaturalVisitor {
    
    private final Node term;
    private Tree.Statement statement;
    private Tree.Statement currentStatement;
    private final boolean toplevel;
    private boolean currentlyToplevel=true;
    private boolean resultIsToplevel;
    private boolean inParameter;
    
    public Tree.Statement getStatement() {
        return statement;
    }
    
    public boolean isToplevel() {
        return resultIsToplevel;
    }
    
    public FindStatementVisitor(Node term, boolean toplevel) {
        this.term = term;
        this.toplevel = toplevel;
    }
    
    @Override
    public void visit(Tree.Parameter that) {
        boolean oip = inParameter;
        inParameter = true;
        super.visit(that);
        inParameter = oip;
    }

    @Override
    public void visit(Tree.Statement that) {
        if ((!toplevel || currentlyToplevel) && !inParameter) {
            if (!(that instanceof Tree.Variable ||
                    that instanceof Tree.TypeConstraint ||
                    that instanceof Tree.TypeParameterDeclaration)) {
                currentStatement = that;
                resultIsToplevel = currentlyToplevel;
            }
        }
        boolean octl = currentlyToplevel;
        currentlyToplevel = false;
        super.visit(that);
        currentlyToplevel = octl;
    }
    
    public void visitAny(Node node) {
        if (node==term) {
            statement = currentStatement;
            resultIsToplevel = currentlyToplevel;
        }
        if (statement==null) {
            super.visitAny(node);
        }
    }
    
}