package com.redhat.ceylon.eclipse.code.complete;

import static com.redhat.ceylon.eclipse.code.complete.CompletionUtil.fullPath;
import static com.redhat.ceylon.eclipse.code.complete.CompletionUtil.nextTokenType;
import static com.redhat.ceylon.eclipse.code.complete.ModuleCompletions.addModuleCompletions;
import static com.redhat.ceylon.eclipse.code.complete.PackageCompletions.addCurrentPackageNameCompletion;
import static com.redhat.ceylon.eclipse.code.complete.PackageCompletions.addPackageCompletions;

import java.util.List;

import org.antlr.runtime.CommonToken;
import org.eclipse.jface.text.contentassist.ICompletionProposal;

import com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;
import com.redhat.ceylon.eclipse.code.parse.CeylonParseController;

final class ImportVisitor extends Visitor {
    
    private final String prefix;
    private final CommonToken token;
    private final int offset;
    private final Node node;
    private final CeylonParseController cpc;
    private final List<ICompletionProposal> result;

    ImportVisitor(String prefix, CommonToken token, int offset,
            Node node, CeylonParseController cpc,
            List<ICompletionProposal> result) {
        this.prefix = prefix;
        this.token = token;
        this.offset = offset;
        this.node = node;
        this.cpc = cpc;
        this.result = result;
    }

    @Override
    public void visit(Tree.ModuleDescriptor that) {
        super.visit(that);
        if (that.getImportPath()==node) {
            addCurrentPackageNameCompletion(cpc, offset, 
                    fullPath(offset, prefix, that.getImportPath()) + prefix, 
                    result);
        }
    }

    public void visit(Tree.PackageDescriptor that) {
        super.visit(that);
        if (that.getImportPath()==node) {
            addCurrentPackageNameCompletion(cpc, offset, 
                    fullPath(offset, prefix, that.getImportPath()) + prefix, 
                    result);
        }
    }

    @Override
    public void visit(Tree.Import that) {
        super.visit(that);
        if (that.getImportPath()==node) {
            addPackageCompletions(cpc, offset, prefix, 
                    (Tree.ImportPath) node, node, result, 
                    nextTokenType(cpc, token)!=CeylonLexer.LBRACE);
        }
    }

    @Override
    public void visit(Tree.PackageLiteral that) {
        super.visit(that);
        if (that.getImportPath()==node) {
            addPackageCompletions(cpc, offset, prefix, 
                    (Tree.ImportPath) node, node, result, false);
        }
    }

    @Override
    public void visit(Tree.ImportModule that) {
        super.visit(that);
        if (that.getImportPath()==node) {
            addModuleCompletions(cpc, offset, prefix, 
                    (Tree.ImportPath) node, node, result, 
                    nextTokenType(cpc, token)!=CeylonLexer.STRING_LITERAL);
        }
    }

    @Override
    public void visit(Tree.ModuleLiteral that) {
        super.visit(that);
        if (that.getImportPath()==node) {
            addModuleCompletions(cpc, offset, prefix, 
                    (Tree.ImportPath) node, node, result, false);
        }
    }
}