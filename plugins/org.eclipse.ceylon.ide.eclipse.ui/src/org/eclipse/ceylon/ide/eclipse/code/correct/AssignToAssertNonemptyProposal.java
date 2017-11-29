/********************************************************************************
 * Copyright (c) 2011-2017 Red Hat Inc. and/or its affiliates and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-1.0
 ********************************************************************************/
package org.eclipse.ceylon.ide.eclipse.code.correct;

import static org.eclipse.ceylon.ide.eclipse.code.correct.LinkedModeCompletionProposal.getNameProposals;

import java.util.Collection;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.link.ProposalPosition;
import org.eclipse.ltk.core.refactoring.DocumentChange;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;

import org.eclipse.ceylon.compiler.typechecker.tree.Node;
import org.eclipse.ceylon.compiler.typechecker.tree.Tree;
import org.eclipse.ceylon.ide.eclipse.code.editor.CeylonEditor;
import org.eclipse.ceylon.ide.eclipse.util.LinkedMode;
import org.eclipse.ceylon.model.typechecker.model.Type;
import org.eclipse.ceylon.model.typechecker.model.Unit;

class AssignToAssertNonemptyProposal extends LocalProposal {

    protected DocumentChange createChange(IDocument document, Node expanse,
            int endIndex) {
        DocumentChange change = 
                new DocumentChange("Assign to Assert Nonempty", document);
        change.setEdit(new MultiTextEdit());
        change.addEdit(new InsertEdit(offset, "assert (nonempty " + initialName + " = "));

        String terminal = expanse.getEndToken().getText();
        if (!terminal.equals(";")) {
            change.addEdit(new InsertEdit(endIndex, ");"));
            exitPos = endIndex+13;
        }
        else {
            change.addEdit(new InsertEdit(endIndex-1, ")"));
            exitPos = endIndex+12;
        }
        return change;
    }
    
    public AssignToAssertNonemptyProposal(CeylonEditor ceylonEditor, Tree.CompilationUnit cu, 
            Node node, int currentOffset) {
        super(ceylonEditor, cu, node, currentOffset);
    }
    
    protected void addLinkedPositions(IDocument document, Unit unit)
            throws BadLocationException {
//        ProposalPosition typePosition = 
//        		new ProposalPosition(document, offset, 5, 1, 
//        				getSupertypeProposals(offset, unit, 
//        						type, true, "value"));
        
        ProposalPosition namePosition = 
        		new ProposalPosition(document, offset+17, initialName.length(), 0, 
        				getNameProposals(offset+17, 0, nameProposals));
        
//        LinkedMode.addLinkedPosition(linkedModeModel, typePosition);
        LinkedMode.addLinkedPosition(linkedModeModel, namePosition);
    }
    
    @Override
    String[] computeNameProposals(Node expression) {
        return super.computeNameProposals(expression);
    }
    
    @Override
    public String getDisplayString() {
        return "Assign expression to 'assert (nonempty)' assertion";
    }

    @Override
    boolean isEnabled(Type resultType) {
        return resultType!=null &&
                rootNode.getUnit().isPossiblyEmptyType(resultType);
    }

    static void addAssignToAssertNonemptyProposal(CeylonEditor ceylonEditor, Tree.CompilationUnit cu, 
            Collection<ICompletionProposal> proposals,
            Node node, int currentOffset) {
        AssignToAssertNonemptyProposal prop = 
                new AssignToAssertNonemptyProposal(ceylonEditor, cu, node, currentOffset);
        if (prop.isEnabled()) {
            proposals.add(prop);
        }
    }

}