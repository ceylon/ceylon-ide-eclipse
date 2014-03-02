package com.redhat.ceylon.eclipse.code.correct;

import static com.redhat.ceylon.eclipse.code.outline.CeylonLabelProvider.CHANGE;
import static com.redhat.ceylon.eclipse.code.refactor.ExtractParameterLinkedMode.useLinkedMode;

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
import com.redhat.ceylon.eclipse.code.refactor.ExtractParameterLinkedMode;
import com.redhat.ceylon.eclipse.code.refactor.ExtractParameterRefactoring;
import com.redhat.ceylon.eclipse.code.refactor.ExtractParameterRefactoringAction;

public class ExtractParameterProposal implements ICompletionProposal {

    private CeylonEditor editor;

    public ExtractParameterProposal(CeylonEditor editor) {
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
        return "Extract parameter";
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
            new ExtractParameterLinkedMode(editor).start();
        }
        else {
            new ExtractParameterRefactoringAction(editor).run();
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
        ExtractParameterRefactoring epr = new ExtractParameterRefactoring(editor);
        if (epr.isEnabled()) {
            proposals.add(new ExtractParameterProposal(editor));
        }
    }

}