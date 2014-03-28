package com.redhat.ceylon.eclipse.code.correct;

import java.util.Collection;

import org.antlr.runtime.CommonToken;
import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.ReplaceEdit;

import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;

class ChangeDeclarationProposal extends CorrectionProposal {
    
    ChangeDeclarationProposal(String kw, int offset, 
            TextFileChange change) {
        super("Change declaration to '" + kw + "'", change,
                new Region(offset, kw.length()));
    }
    
    static void addChangeDeclarationProposal(ProblemLocation problem, IFile file,
            Collection<ICompletionProposal> proposals, Node node) {
        Tree.Declaration decNode = (Tree.Declaration) node;
        CommonToken token = (CommonToken) decNode.getMainToken();
        String keyword;
        if (decNode instanceof Tree.AnyClass){
            keyword = "interface";
        }
        else if (decNode instanceof Tree.AnyMethod) {
            if (token.getText().equals("void")) return;
            keyword = "value";
        }
        else {
            return;
        }
        TextFileChange change = new TextFileChange("Change Declaration", file);
        change.setEdit(new ReplaceEdit(token.getStartIndex(), 
                token.getText().length(), keyword));
        proposals.add(new ChangeDeclarationProposal(keyword, 
                token.getStartIndex(), change));
    }
}