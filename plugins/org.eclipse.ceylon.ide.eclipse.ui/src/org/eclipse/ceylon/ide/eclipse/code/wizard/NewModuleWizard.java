/********************************************************************************
 * Copyright (c) 2011-2017 Red Hat Inc. and/or its affiliates and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * http://www.eclipse.org/legal/epl-v10.html.
 *
 * SPDX-License-Identifier: EPL-1.0
 ********************************************************************************/
package org.eclipse.ceylon.ide.eclipse.code.wizard;

import static org.eclipse.ceylon.ide.eclipse.code.editor.Navigation.gotoLocation;
import static org.eclipse.ceylon.ide.eclipse.code.imports.ModuleImportUtil.appendImportStatement;
import static org.eclipse.ceylon.ide.eclipse.code.preferences.ModuleImportSelectionDialog.selectModules;
import static org.eclipse.ceylon.ide.eclipse.code.refactor.MoveUtil.escapePackageName;
import static org.eclipse.ceylon.ide.eclipse.code.wizard.WizardUtil.runOperation;
import static org.eclipse.ceylon.ide.eclipse.core.builder.CeylonBuilder.getProjectTypeChecker;
import static org.eclipse.ceylon.ide.eclipse.util.ModuleQueries.getModuleSearchResults;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.AbstractOperation;
import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.wizards.newresource.BasicNewResourceWizard;

import org.eclipse.ceylon.cmr.api.ModuleSearchResult;
import org.eclipse.ceylon.ide.eclipse.code.preferences.ModuleImportContentProvider;
import org.eclipse.ceylon.ide.eclipse.code.preferences.ModuleImportSelectionDialog;
import org.eclipse.ceylon.ide.eclipse.core.builder.CeylonBuilder;
import org.eclipse.ceylon.ide.eclipse.ui.CeylonPlugin;
import org.eclipse.ceylon.ide.common.modulesearch.ModuleVersionNode;

public class NewModuleWizard extends Wizard implements INewWizard {
    
    private final class CreateModuleOperation extends AbstractOperation {
        
        private IFile result;
        private List<IUndoableOperation> ops = 
                new ArrayList<IUndoableOperation>(3);
        private Map<String, String> imports;
        private Set<String> sharedImports;
        
        public IFile getResult() {
            return result;
        }
        
        public CreateModuleOperation(Map<String,String> imports,
                Set<String> sharedImports) {
            super("New Ceylon Module");
            this.imports = imports;
            this.sharedImports = sharedImports;
        }
        
        @Override
        public IStatus execute(IProgressMonitor monitor, IAdaptable info)
                throws ExecutionException {
            
            IPackageFragment pf = page.getPackageFragment();
            
            CreateSourceFileOperation op = runFunctionOp(pf);
            ops.add(op);
            IStatus status = op.execute(monitor, info);
            if (!status.isOK()) {
                return status;
            }
            result = op.getFile();
            
            op = moduleDescriptorOp(pf);
            status = op.execute(monitor, info);
            ops.add(op);
            if (!status.isOK()) {
                return status;
            }
            
            op = packageDescriptorOp(pf);
            status = op.execute(monitor, info);
            ops.add(op);
            if (!status.isOK()) {
                return status;
            }
            
            return Status.OK_STATUS;
        }

        private CreateSourceFileOperation packageDescriptorOp(
                IPackageFragment pf) {
            String moduleName = 
                    escapePackageName(pf.getElementName());
            boolean preamble = page.isIncludePreamble();
            String newline = System.lineSeparator();
            StringBuilder packageDescriptor = 
                    new StringBuilder();
            if (page.isShared()) {
                packageDescriptor
                    .append("shared "); 
            }
            packageDescriptor
                    .append("package ")
                    .append(moduleName)
                    .append(";")
                    .append(newline);
            return new CreateSourceFileOperation(page.getSourceDir(), 
                    pf, "package", preamble, 
                    packageDescriptor.toString());
        }

        private CreateSourceFileOperation moduleDescriptorOp(
                IPackageFragment pf) {
            String moduleName = 
                    escapePackageName(pf.getElementName());
            boolean preamble = page.isIncludePreamble();
            String newline = System.lineSeparator();
            StringBuilder moduleDescriptor = 
                    new StringBuilder();
            IProject project = 
                    pf.getResource()
                      .getProject();
            boolean compileToJava = 
                    CeylonBuilder.compileToJava(project);
            boolean compileToJs = 
                    CeylonBuilder.compileToJs(project);
            if (compileToJava==compileToJs) {
                //no native annotation
            }
            else if (!compileToJs) {
                moduleDescriptor
                    .append("native(\"jvm\")")
                    .append(newline);
            }
            else if (!compileToJava) {
                moduleDescriptor
                    .append("native(\"js\")")
                    .append(newline);
            }
            moduleDescriptor
                    .append("module ")
                    .append(moduleName)
                    .append(" \"")
                    .append(page.getVersion())
                    .append("\"")
                    .append(" {");
            for (Map.Entry<String,String> entry: 
                    imports.entrySet()) {
                String name = entry.getKey();
                String version = entry.getValue();
                boolean shared = sharedImports.contains(name);
                appendImportStatement(moduleDescriptor, 
                        shared, null, name, version, newline);
            }
            if (!imports.isEmpty()) {
                moduleDescriptor.append(newline);
            }
            moduleDescriptor.append("}").append(newline);
            return new CreateSourceFileOperation(page.getSourceDir(), 
                    pf, "module", preamble, 
                    moduleDescriptor.toString());
        }

        private CreateSourceFileOperation runFunctionOp(
                IPackageFragment pf) {
            String moduleName = pf.getElementName();
            boolean preamble = page.isIncludePreamble();
            String newline = System.lineSeparator();
            String runFunction = 
                    new StringBuilder()
                        .append("\"Run the module `")
                        .append(moduleName)
                        .append("`.\"")
                        .append(newline)
                        .append("shared void run() {")
                        .append(newline)
                        .append("    ")
                        .append(newline)
                        .append("}")
                        .toString();
            
            return new CreateSourceFileOperation(
                    page.getSourceDir(), 
                    pf, page.getUnitName(), 
                    preamble, runFunction);
        }
        
        @Override
        public IStatus redo(IProgressMonitor monitor, 
                IAdaptable info)
                throws ExecutionException {
            return execute(monitor, info);
        }
        @Override
        public IStatus undo(IProgressMonitor monitor, 
                IAdaptable info)
                throws ExecutionException {
            for (IUndoableOperation op: ops) {
                op.undo(monitor, info);
            }
            return Status.OK_STATUS;
        }
    }

    private IStructuredSelection selection;
    private NewModuleWizardPage page;
    private ImportModulesWizardPage importsPage;
    private IWorkbench workbench;
    
    public NewModuleWizard() {
        setDialogSettings(CeylonPlugin.getInstance()
                .getDialogSettings());
        setWindowTitle("New Ceylon Module");
    }
    
    @Override
    public void init(IWorkbench workbench, 
            IStructuredSelection selection) {
        this.selection = selection;
        this.workbench = workbench;
    }
    
    @Override
    public boolean performFinish() {
        CreateModuleOperation op = 
                new CreateModuleOperation(
                        importsPage.getImports(), 
                        importsPage.getSharedImports());
        if (runOperation(op, getContainer())) {        
            BasicNewResourceWizard.selectAndReveal(
                    op.getResult(), 
                    workbench.getActiveWorkbenchWindow());
            gotoLocation(op.getResult().getFullPath(), 0);
            return true;
        }
        else {
            return false;
        }
    }
    
    @Override
    public void addPages() {
        super.addPages();
        if (page == null) {
            page = new NewModuleWizardPage();
            page.init(workbench, selection);
        }
        if (importsPage == null) {
            importsPage = new ImportModulesWizardPage() {
                IProject getProject() {
                    IPackageFragmentRoot sourceDir = 
                            page.getSourceDir();
                    return sourceDir==null ? null : 
                            sourceDir.getResource()
                                     .getProject();
                }
                @Override
                Map<String, ModuleVersionNode> getModules() {
                    return selectModules(new ModuleImportSelectionDialog(
                            getShell(), 
                            new ModuleImportContentProvider(null, getProject()) {
                        @Override
                        public ModuleSearchResult getModules(String prefix) {
                            IProject project = 
                                    page.getSourceDir()
                                        .getJavaProject()
                                        .getProject();
                            return getModuleSearchResults(prefix, null,
                                    getProjectTypeChecker(project), 
                                    project);
                        }
                    }), getProject());
                }
            };
        }
        addPage(page);
        addPage(importsPage);
    }
}
