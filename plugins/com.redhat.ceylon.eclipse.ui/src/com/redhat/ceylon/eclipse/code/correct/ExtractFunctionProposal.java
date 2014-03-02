package com.redhat.ceylon.eclipse.code.correct;

import static com.redhat.ceylon.eclipse.code.outline.CeylonLabelProvider.CHANGE;
import static com.redhat.ceylon.eclipse.code.refactor.ExtractValueLinkedMode.useLinkedMode;

import java.util.Collection;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

import com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.eclipse.code.editor.CeylonEditor;
import com.redhat.ceylon.eclipse.code.refactor.ExtractFunctionLinkedMode;
import com.redhat.ceylon.eclipse.code.refactor.ExtractFunctionRefactoring;
import com.redhat.ceylon.eclipse.code.refactor.ExtractFunctionRefactoringAction;

public class ExtractFunctionProposal implements ICompletionProposal {

    private CeylonEditor editor;

    public ExtractFunctionProposal(CeylonEditor editor) {
        this.editor = editor;
    }
    
    @Override
    public Point getSelection(IDocument doc) {
        return null;
    }

    @Override
    public Image getImage() {
        return CHANGE;
    }

    @Override
    public String getDisplayString() {
        return "Extract function";
    }

    @Override
    public IContextInformation getContextInformation() {
        return null;
    }

    @Override
    public String getAdditionalProposalInfo() {
        return null;
    }

    @Override
    public void apply(IDocument doc) {
        if (useLinkedMode()) {
            new ExtractFunctionLinkedMode(editor).start();
        }
        else {
            new ExtractFunctionRefactoringAction(editor).run();
        }
    }
    
    public static void add(Collection<ICompletionProposal> proposals, 
            CeylonEditor editor, Node node) {
        if (node instanceof Tree.BaseMemberExpression) {
            Tree.Identifier id = ((Tree.BaseMemberExpression) node).getIdentifier();
            if (id==null || id.getToken().getType()==CeylonLexer.AIDENTIFIER) {
                return;
            }
        }
        ExtractFunctionRefactoring efr = new ExtractFunctionRefactoring(editor);
        if (efr.isEnabled()) {
            proposals.add(new ExtractFunctionProposal(editor));
        }
    }

}