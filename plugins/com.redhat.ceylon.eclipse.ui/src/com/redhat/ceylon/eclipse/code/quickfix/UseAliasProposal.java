package com.redhat.ceylon.eclipse.code.quickfix;

import static com.redhat.ceylon.eclipse.code.outline.CeylonLabelProvider.ADD_CORR;

import java.util.Collection;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension6;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.eclipse.code.editor.CeylonEditor;

class UseAliasProposal implements ICompletionProposal, ICompletionProposalExtension6 {
    
    Tree.ImportMemberOrType node;
    Declaration dec;
    CeylonEditor editor;
    IFile file;
    
    UseAliasProposal(IFile file, Tree.ImportMemberOrType node, 
            Declaration dec, CeylonEditor editor) {
        this.node = node;
        this.dec = dec;
        this.file = file;
        this.editor = editor;
    }
    
    @Override
    public void apply(IDocument document) {
        new EnterAliasLinkedMode(node, dec, editor).start();
        
    }
    
    static void addUseAliasProposal(Tree.ImportMemberOrType node,  
            Collection<ICompletionProposal> proposals, 
            Declaration dec, IFile file,  CeylonEditor editor) {
        proposals.add(new UseAliasProposal(file, node, dec, editor));
    }

    @Override
    public StyledString getStyledDisplayString() {
        return ChangeCorrectionProposal.style(getDisplayString());
    }

    @Override
    public Point getSelection(IDocument document) {
        return null;
    }

    @Override
    public String getAdditionalProposalInfo() {
        return null;
    }

    @Override
    public String getDisplayString() {
        return "Enter alias for '" + dec.getName() + "'";
    }

    @Override
    public Image getImage() {
        return ADD_CORR;
    }

    @Override
    public IContextInformation getContextInformation() {
        return null;
    }
    
}
