package com.redhat.ceylon.eclipse.code.correct;

import static com.redhat.ceylon.eclipse.code.refactor.MoveUtil.canMoveDeclaration;
import static com.redhat.ceylon.eclipse.code.refactor.MoveUtil.getDeclarationName;

import java.util.Collection;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

import com.redhat.ceylon.eclipse.code.editor.CeylonEditor;
import com.redhat.ceylon.eclipse.code.outline.CeylonLabelProvider;
import com.redhat.ceylon.eclipse.code.refactor.MoveToUnitRefactoringAction;

class MoveToUnitProposal implements ICompletionProposal {

    private final CeylonEditor editor;
    private final String name;
    
    public MoveToUnitProposal(String name, CeylonEditor editor) {
        this.editor = editor;
        this.name = name;
    }
    
    @Override
    public Point getSelection(IDocument doc) {
        return null;
    }

    @Override
    public Image getImage() {
        return CeylonLabelProvider.MOVE;
    }

    @Override
    public String getDisplayString() {
        return "Move '" + name + "' to another source file";
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
        new MoveToUnitRefactoringAction(editor).run();
    }
    
    static void add(Collection<ICompletionProposal> proposals, 
            CeylonEditor editor) {
        if (canMoveDeclaration(editor)) {
            proposals.add(new MoveToUnitProposal(getDeclarationName(editor), 
                    editor));
        }
    }

}