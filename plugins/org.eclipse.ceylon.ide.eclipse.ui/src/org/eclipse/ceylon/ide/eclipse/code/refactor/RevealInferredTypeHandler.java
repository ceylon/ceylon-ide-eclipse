/********************************************************************************
 * Copyright (c) 2011-2017 Red Hat Inc. and/or its affiliates and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * http://www.eclipse.org/legal/epl-v10.html.
 *
 * SPDX-License-Identifier: EPL-1.0
 ********************************************************************************/
package org.eclipse.ceylon.ide.eclipse.code.refactor;

import static org.eclipse.ceylon.ide.eclipse.code.correct.ImportProposals.importProposals;
import static org.eclipse.ceylon.ide.eclipse.util.EditorUtil.getCurrentEditor;
import static org.eclipse.ceylon.ide.eclipse.util.EditorUtil.getSelection;
import static org.eclipse.core.resources.ResourcesPlugin.getWorkspace;
import static org.eclipse.ltk.core.refactoring.RefactoringCore.getUndoManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.ltk.core.refactoring.PerformChangeOperation;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;

import org.eclipse.ceylon.compiler.typechecker.tree.Node;
import org.eclipse.ceylon.compiler.typechecker.tree.Tree;
import org.eclipse.ceylon.compiler.typechecker.tree.Tree.LocalModifier;
import org.eclipse.ceylon.compiler.typechecker.tree.Visitor;
import org.eclipse.ceylon.ide.eclipse.code.correct.correctJ2C;
import org.eclipse.ceylon.ide.eclipse.code.editor.CeylonEditor;
import org.eclipse.ceylon.ide.eclipse.platform.platformJ2C;
import org.eclipse.ceylon.model.typechecker.model.Declaration;
import org.eclipse.ceylon.model.typechecker.model.Type;
import org.eclipse.ceylon.model.typechecker.model.Unit;

import ceylon.interop.java.CeylonMutableSet;
import ceylon.interop.java.CeylonSet;

public class RevealInferredTypeHandler extends AbstractHandler {

    @Override
    public boolean isEnabled() {
        IEditorPart ce = getCurrentEditor();
        if (!(ce instanceof CeylonEditor)) return false;
        CeylonEditor editor = (CeylonEditor) ce;
        List<Tree.LocalModifier> localModifiers = 
                new ArrayList<Tree.LocalModifier>();
        List<Tree.ValueIterator> valueIterators = 
                new ArrayList<Tree.ValueIterator>();

        findCandidatesForRevelation(editor, localModifiers, 
                valueIterators);

        return !localModifiers.isEmpty() || 
                !valueIterators.isEmpty();
    }

    @Override
    public Object execute(ExecutionEvent event) 
            throws ExecutionException {
        CeylonEditor editor = (CeylonEditor) 
                getCurrentEditor();
        Tree.CompilationUnit rootNode = 
                editor.getParseController().getLastCompilationUnit();

        Set<Declaration> imports = new HashSet<Declaration>();
        List<Tree.LocalModifier> localModifiers = 
                new ArrayList<Tree.LocalModifier>();
        List<Tree.ValueIterator> valueIterators = 
                new ArrayList<Tree.ValueIterator>();

        findCandidatesForRevelation(editor, localModifiers, 
                valueIterators);

        if( !localModifiers.isEmpty() || 
                !valueIterators.isEmpty() ) {
            IFileEditorInput input = (IFileEditorInput) 
                    editor.getEditorInput();
            TextChange tfc = 
                    new TextFileChange("Reveal Inferred Types", 
                            input.getFile());
            tfc.setEdit(new MultiTextEdit());
            tfc.initializeValidationData(null);

            Unit unit = rootNode.getUnit();
            for (Tree.LocalModifier localModifier : localModifiers) {
                if( localModifier.getStartIndex() != null && 
                        localModifier.getTypeModel() != null ) {
                    Type pt = 
                            localModifier.getTypeModel();
                    tfc.addEdit(new ReplaceEdit(
                            localModifier.getStartIndex(), 
                            localModifier.getText().length(), 
                            pt.asSourceCodeString(unit)));
                    importProposals().importType(
                            new CeylonMutableSet<>(null, imports), 
                            pt, rootNode);
                }
            }

            for (Tree.ValueIterator valueIterator : valueIterators) {
                Tree.Variable variable = 
                        valueIterator.getVariable();
                if( variable != null 
                        && variable.getStartIndex() != null 
                        && variable.getType() != null 
                        && variable.getType().getTypeModel() != null ) {
                    Type pt = 
                            variable.getType().getTypeModel();
                    tfc.addEdit(new InsertEdit(
                            variable.getStartIndex(), 
                            pt.asSourceCodeString(unit) + " "));
                    importProposals()
                        .importType(
                            new CeylonMutableSet<>(null, imports),  
                            variable.getType().getTypeModel(), 
                            rootNode);
                }
            }
            
            try {
                IDocument doc = tfc.getCurrentDocument(null);
                org.eclipse.ceylon.ide.common.platform.TextChange chg =
                        new platformJ2C().newChange(tfc.getName(), tfc);
                importProposals()
                    .applyImports(chg, 
                        new CeylonSet<>(null, imports), 
                        rootNode, 
                        new correctJ2C().newDocument(doc));

                PerformChangeOperation changeOperation = 
                        new PerformChangeOperation(tfc);
                changeOperation.setUndoManager(getUndoManager(), 
                        "Reveal Inferred Types");
                getWorkspace().run(changeOperation, 
                        new NullProgressMonitor());
            }
            catch (CoreException ce) {
                throw new ExecutionException("Error reveal inferred types", ce);
            }
        }

        return null;
    }

    private void findCandidatesForRevelation(CeylonEditor editor, 
            final List<Tree.LocalModifier> localModifiers, 
            final List<Tree.ValueIterator> valueIterators) {
        if (editor!=null &&  
                editor.getParseController()!=null) {
            final Tree.CompilationUnit rootNode = 
                    editor.getParseController().getTypecheckedRootNode();
            final ITextSelection selection = 
                    getSelection(editor);
            if (rootNode==null || selection==null) {
                return;
            }
            final int selectionStart = 
                    selection.getOffset();
            final int selectionStop = 
                    selection.getOffset() + 
                    selection.getLength();

            rootNode.visit(new Visitor() {

                @Override
                public void visit(Tree.TypedDeclaration typedDeclaration) {
                    if( isInSelection(typedDeclaration) ) {
                        Tree.Type type = 
                                typedDeclaration.getType();
                        if( type instanceof Tree.LocalModifier 
                                && type.getToken() != null ) {
                            localModifiers.add((LocalModifier) type);                                
                        }
                    }
                    super.visit(typedDeclaration);
                }

                @Override
                public void visit(Tree.ValueIterator valueIterator) {
                    if (isInSelection(valueIterator)) {
                        Tree.Variable variable = 
                                valueIterator.getVariable();
                        Tree.Type type = variable.getType();
                        if (type instanceof Tree.ValueModifier) {
                            valueIterators.add(valueIterator);
                        }
                    }
                    super.visit(valueIterator);
                }

                private boolean isInSelection(Node node) {
                    Integer startIndex = node.getStartIndex();
                    Integer endIndex = node.getEndIndex();
                    if (startIndex != null && endIndex != null) {
                        if (selection.getLength() == 0 /* if selection is empty, process whole file */ ||
                           (startIndex >= selectionStart 
                               && endIndex <= selectionStop) ) {
                            return true;
                        }
                    }
                    return false;
                }

            });
        }
    }

}