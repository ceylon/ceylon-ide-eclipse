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

import static org.eclipse.ceylon.compiler.typechecker.tree.TreeUtil.formatPath;
import static org.eclipse.ceylon.ide.eclipse.code.correct.ImportProposals.importProposals;
import static org.eclipse.ceylon.ide.eclipse.core.builder.CeylonBuilder.getProjectTypeChecker;
import static org.eclipse.ceylon.ide.eclipse.util.EditorUtil.getDocument;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.CopyParticipant;
import org.eclipse.ltk.core.refactoring.participants.CopyProcessor;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;

import org.eclipse.ceylon.compiler.typechecker.TypeChecker;
import org.eclipse.ceylon.compiler.typechecker.context.PhasedUnit;
import org.eclipse.ceylon.compiler.typechecker.tree.Tree;
import org.eclipse.ceylon.compiler.typechecker.tree.Tree.BaseMemberOrTypeExpression;
import org.eclipse.ceylon.compiler.typechecker.tree.Tree.BaseType;
import org.eclipse.ceylon.compiler.typechecker.tree.Tree.ImportMemberOrType;
import org.eclipse.ceylon.compiler.typechecker.tree.Tree.ModuleDescriptor;
import org.eclipse.ceylon.compiler.typechecker.tree.Tree.PackageDescriptor;
import org.eclipse.ceylon.compiler.typechecker.tree.Visitor;
import org.eclipse.ceylon.ide.eclipse.code.correct.correctJ2C;
import org.eclipse.ceylon.model.typechecker.model.Declaration;
import org.eclipse.ceylon.model.typechecker.model.Module;
import org.eclipse.ceylon.model.typechecker.model.Unit;

public class CopyFileRefactoringParticipant extends CopyParticipant {

    private IFile file;

    @Override
    protected boolean initialize(Object element) {
        file = (IFile) element;
        return getProcessor() instanceof CopyProcessor && 
                getProjectTypeChecker(file.getProject())!=null &&
                file.getFileExtension()!=null && 
                file.getFileExtension().equals("ceylon");
    }
    
    @Override
    public String getName() {
        return "Copy file participant for Ceylon source";
    }

    @Override
    public RefactoringStatus checkConditions(IProgressMonitor pm,
            CheckConditionsContext context) 
                    throws OperationCanceledException {
        return new RefactoringStatus();
    }

    public Change createChange(IProgressMonitor pm) 
            throws CoreException {
        try {
            IFolder dest = (IFolder) 
                    getArguments().getDestination();
            final String newName = 
                    dest.getProjectRelativePath()
                        .removeFirstSegments(1)
                        .toPortableString()
                        .replace('/', '.');
            IFile newFile = dest.getFile(file.getName());
            IPath path = file.getProjectRelativePath();
            String relFilePath = 
                    path.removeFirstSegments(1)
                        .toPortableString();
            String relPath = 
                    path.removeFirstSegments(1)
                        .removeLastSegments(1)
                        .toPortableString();
            final String oldName = 
                    relPath.replace('/', '.');
            final IProject project = file.getProject();
            
            if (newName.equals(oldName)) return null;

            TypeChecker tc = 
                    getProjectTypeChecker(project);
            if (tc==null) return null;
            PhasedUnit phasedUnit = 
                    tc.getPhasedUnitFromRelativePath(relFilePath);
            if (phasedUnit==null) return null;
            final List<ReplaceEdit> edits = 
                    new ArrayList<ReplaceEdit>();                
            final List<Declaration> declarations = 
                    phasedUnit.getDeclarations();
            final Map<Declaration,String> imports = 
                    new HashMap<Declaration,String>();
            phasedUnit.getCompilationUnit().visit(new Visitor() {
                @Override
                public void visit(ImportMemberOrType that) {
                    super.visit(that);
                    visitIt(that.getIdentifier(), 
                            that.getDeclarationModel());
                }
                @Override
                public void visit(BaseMemberOrTypeExpression that) {
                    super.visit(that);
                    visitIt(that.getIdentifier(), 
                            that.getDeclaration());
                }
                @Override
                public void visit(BaseType that) {
                    super.visit(that);
                    visitIt(that.getIdentifier(), 
                            that.getDeclarationModel());
                }
                @Override
                public void visit(ModuleDescriptor that) {
                    super.visit(that);
                    visitIt(that.getImportPath());
                }
                @Override
                public void visit(PackageDescriptor that) {
                    super.visit(that);
                    visitIt(that.getImportPath());
                }
                private void visitIt(Tree.ImportPath importPath) {
                    String path = 
                            formatPath(importPath.getIdentifiers());
                    if (path.equals(oldName)) {
                        int start = importPath.getStartIndex();
                        int len = oldName.length();
                        edits.add(new ReplaceEdit(start, len, newName));
                    }
                }
                private void visitIt(Tree.Identifier id, Declaration dec) {
                    if (dec!=null && 
                            //TODO: superflous
                            !declarations.contains(dec)) {
                        Unit unit = dec.getUnit();
                        String packageName = 
                                unit.getPackage().getNameAsString();
                        if (packageName.equals(oldName) && 
                            !packageName.isEmpty() && 
                            !packageName.equals(Module.LANGUAGE_MODULE_NAME) &&
                            !unit.equals(id.getUnit())) {
                            imports.put(dec, id.getText());
                        }
                    }
                }
            });

            try {
                TextFileChange change = 
                        new TextFileChange(file.getName(), 
                                newFile);
                Tree.CompilationUnit cu = 
                        phasedUnit.getCompilationUnit();
                change.setEdit(new MultiTextEdit());
                for (ReplaceEdit edit: edits) {
                    change.addEdit(edit);
                }
                if (!imports.isEmpty()) {
                    new correctJ2C().importEdits(change, cu, imports.keySet(),
                            imports.values(), getDocument(change));
                }
                Tree.Import toDelete = 
                        importProposals().findImportNode(cu, newName, cu.getScope());
                if (toDelete!=null) {
                    int start = toDelete.getStartIndex();
                    int len = toDelete.getDistance();
                    change.addEdit(new DeleteEdit(start, len));
                }
                if (change.getEdit().hasChildren()) {
                    return change;
                }
            }
            catch (Exception e) { 
                e.printStackTrace(); 
            }

            return null;
        }
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
