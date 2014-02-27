package com.redhat.ceylon.eclipse.core.builder;

import static com.redhat.ceylon.compiler.typechecker.model.Util.formatPath;
import static com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.isCompilable;
import static com.redhat.ceylon.eclipse.core.vfs.ResourceVirtualFile.createResourceVirtualFile;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.SubMonitor;

import com.redhat.ceylon.compiler.typechecker.TypeChecker;
import com.redhat.ceylon.compiler.typechecker.analyzer.ModuleManager;
import com.redhat.ceylon.compiler.typechecker.context.PhasedUnit;
import com.redhat.ceylon.compiler.typechecker.context.PhasedUnits;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.compiler.typechecker.model.Package;
import com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.RootFolderType;
import com.redhat.ceylon.eclipse.core.model.JDTModelLoader;
import com.redhat.ceylon.eclipse.core.model.JDTModuleManager;
import com.redhat.ceylon.eclipse.core.vfs.IFolderVirtualFile;
import com.redhat.ceylon.eclipse.core.vfs.ResourceVirtualFile;

final class RootFolderScanner implements IResourceVisitor {
    private final Module defaultModule;
    private final JDTModelLoader modelLoader;
    private final JDTModuleManager moduleManager;
    private final IFolderVirtualFile rootDir;
    private final TypeChecker typeChecker;
    private final List<IFile> scannedFiles;
    private final PhasedUnits phasedUnits;
    private RootFolderType rootFolderType;
    private Module module;
    private SubMonitor monitor;
    

    RootFolderScanner(RootFolderType rootFolderType, Module defaultModule, JDTModelLoader modelLoader,
            JDTModuleManager moduleManager, IFolderVirtualFile rootDir, TypeChecker typeChecker,
            List<IFile> scannedFiles, PhasedUnits phasedUnits, SubMonitor monitor) {
        this.rootFolderType = rootFolderType;
        this.defaultModule = defaultModule;
        this.modelLoader = modelLoader;
        this.moduleManager = moduleManager;
        this.rootDir = rootDir;
        this.typeChecker = typeChecker;
        this.scannedFiles = scannedFiles;
        this.phasedUnits = phasedUnits;
        this.monitor = monitor;
    }

    public boolean visit(IResource resource) throws CoreException {
        Package pkg;

        monitor.setWorkRemaining(10000);
        monitor.worked(1);

        if (resource.equals(rootDir.getResource())) {
            resource.setSessionProperty(CeylonBuilder.RESOURCE_PROPERTY_PACKAGE_MODEL, new WeakReference<Package>(modelLoader.findPackage("")));
            resource.setSessionProperty(CeylonBuilder.RESOURCE_PROPERTY_ROOT_FOLDER, rootDir.getFolder());
            resource.setSessionProperty(CeylonBuilder.RESOURCE_PROPERTY_ROOT_FOLDER_TYPE, rootFolderType);
            return true;
        }

        if (resource.getParent().equals(rootDir.getResource())) {
            // We've come back to a source directory child : 
            //  => reset the current Module to default and set the package to emptyPackage
            module = defaultModule;
            pkg = modelLoader.findPackage("");
            assert(pkg != null);
        }

        if (resource instanceof IFolder) {
            List<String> pkgName = getPackageName((IFolder)resource);
            String pkgNameAsString = formatPath(pkgName);
            
            if ( module != defaultModule ) {
                if (! pkgNameAsString.startsWith(module.getNameAsString() + ".")) {
                    // We've ran above the last module => reset module to default 
                    module = defaultModule;
                }
            }
            
            IFile moduleFile = ((IFolder) resource).getFile(ModuleManager.MODULE_FILE);
            if (moduleFile.exists()) {
                if ( module != defaultModule ) {
                    // This is an error : no nested modules
                } else {
                    final List<String> moduleName = pkgName;
                    //we don't know the version here, but it is already added with the right version in ModulesScanner
                    module = moduleManager.getOrCreateModule(moduleName, null);
                    assert(module != null);
                }
            }
            if (module == defaultModule) {
                Module realModule = modelLoader.getLoadedModule(pkgNameAsString);
                if (realModule != null) {
                    // The module descriptor had probably been found in another source directory
                    module = realModule;
                }
            }
            
            pkg = modelLoader.findOrCreatePackage(module, pkgNameAsString);
            resource.setSessionProperty(CeylonBuilder.RESOURCE_PROPERTY_PACKAGE_MODEL, new WeakReference<Package>(pkg));
            resource.setSessionProperty(CeylonBuilder.RESOURCE_PROPERTY_ROOT_FOLDER, rootDir.getFolder());
            return true;
        }

        if (resource instanceof IFile) {
            IFile file = (IFile) resource;
            if (file.exists()) {
                if (rootFolderType.equals(RootFolderType.RESOURCE) || 
                        (isCompilable(file) && rootFolderType.equals(RootFolderType.SOURCE)) ) {
                    List<String> pkgName = getPackageName(file.getParent());
                    String pkgNameAsString = formatPath(pkgName);
                    pkg = modelLoader.findOrCreatePackage(module, pkgNameAsString);
                    
                    if (scannedFiles != null) {
                        scannedFiles.add((IFile)resource);
                    }
                    
                    if (CeylonBuilder.isCeylon(file)) {
                        ResourceVirtualFile virtualFile = createResourceVirtualFile(file);
                        try {
                            PhasedUnit newPhasedUnit = CeylonBuilder.parseFileToPhasedUnit(moduleManager, 
                                    typeChecker, virtualFile, rootDir, pkg);
                            phasedUnits.addPhasedUnit(virtualFile, newPhasedUnit);
                        } 
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        return false;
    }

    private List<String> getPackageName(IContainer container) {
        List<String> pkgName = Arrays.asList(container.getProjectRelativePath()
                .makeRelativeTo(rootDir.getResource().getProjectRelativePath()).segments());
        return pkgName;
    }
}