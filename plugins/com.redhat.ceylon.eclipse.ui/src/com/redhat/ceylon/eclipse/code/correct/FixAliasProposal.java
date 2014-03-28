package com.redhat.ceylon.eclipse.code.correct;

import java.util.Collection;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;

class FixAliasProposal extends CorrectionProposal {
    
    FixAliasProposal(int offset, TextChange change) {
        super("Change = to =>", change, new Region(offset, 0));
    }
    
    static void addFixAliasProposal(Collection<ICompletionProposal> proposals, 
            IFile file, ProblemLocation problem) {
        /*Token token;
        if (decNode instanceof Tree.ClassDeclaration) {
            token = ((Tree.ClassDeclaration) decNode).getClassSpecifier().getMainToken();
        }
        else if (decNode instanceof Tree.InterfaceDeclaration) {
            token = ((Tree.InterfaceDeclaration) decNode).getTypeSpecifier().getMainToken();
        }
        else if (decNode instanceof Tree.TypeAliasDeclaration) {
            token = ((Tree.TypeAliasDeclaration) decNode).getTypeSpecifier().getMainToken();
        }
        else {
            return;
        }
        Integer offset = ((CommonToken)token).getStartIndex();*/
        int offset = problem.getOffset();
        TextChange change = new TextFileChange("Fix Alias Syntax", file);
        change.setEdit(new MultiTextEdit());
        change.addEdit(new ReplaceEdit(offset, 1, "=>"));
        proposals.add(new FixAliasProposal(offset + 2 , change));
    }
    
}