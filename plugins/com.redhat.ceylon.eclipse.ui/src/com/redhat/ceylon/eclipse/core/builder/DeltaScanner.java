package com.redhat.ceylon.eclipse.core.builder;

import static com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.getPackage;
import static com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.getProjectModelLoader;
import static com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.getRootFolder;
import static com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.isInSourceFolder;
import static com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.isJava;
import static com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.isResourceFile;
import static com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.isSourceFile;

import java.lang.ref.WeakReference;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.runtime.CoreException;

import com.redhat.ceylon.compiler.typechecker.analyzer.ModuleManager;
import com.redhat.ceylon.compiler.typechecker.model.Package;
import com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.BooleanHolder;

final class DeltaScanner implements IResourceDeltaVisitor {
    private final BooleanHolder mustDoFullBuild;
    private final IProject project;
    private final BooleanHolder somethingToBuild;
    private final BooleanHolder mustResolveClasspathContainer;

    DeltaScanner(BooleanHolder mustDoFullBuild, IProject project,
            BooleanHolder somethingToBuild,
            BooleanHolder mustResolveClasspathContainer) {
        this.mustDoFullBuild = mustDoFullBuild;
        this.project = project;
        this.somethingToBuild = somethingToBuild;
        this.mustResolveClasspathContainer = mustResolveClasspathContainer;
    }

    @Override
    public boolean visit(IResourceDelta resourceDelta) 
            throws CoreException {
        IResource resource = resourceDelta.getResource();
        
        if (resource instanceof IProject) { 
            if ((resourceDelta.getFlags() & IResourceDelta.DESCRIPTION)!=0) {
                //some project setting changed : don't do anything, 
                // since the possibly impacting changes have already been
                // checked by JavaProjectStateManager.hasClasspathChanges()
            }
            else if (!resource.equals(project)) {
                //this is some kind of multi-project build,
                //indicating a change in a project we
                //depend upon
                /*mustDoFullBuild.value = true;
                mustResolveClasspathContainer.value = true;*/
            }
        }
        
        if (resource instanceof IFolder) {
            IFolder folder = (IFolder) resource; 
            if (resourceDelta.getKind()==IResourceDelta.REMOVED) {
                Package pkg = getPackage(folder);
                if (pkg!=null) {
                    //a package has been removed
                    mustDoFullBuild.value = true;
                }
            } else {
                if (folder.exists() && folder.getProject().equals(project)) {
                    if (getPackage(folder) == null || getRootFolder(folder) == null) {
                        IContainer parent = folder.getParent();
                        if (parent instanceof IFolder) {
                            Package parentPkg = getPackage((IFolder)parent);
                            IFolder rootFolder = getRootFolder((IFolder)parent);
                            if (parentPkg != null && rootFolder != null) {
                                Package pkg = getProjectModelLoader(project).findOrCreatePackage(parentPkg.getModule(), parentPkg.getNameAsString() + "." + folder.getName());
                                resource.setSessionProperty(CeylonBuilder.RESOURCE_PROPERTY_PACKAGE_MODEL, new WeakReference<Package>(pkg));
                                resource.setSessionProperty(CeylonBuilder.RESOURCE_PROPERTY_ROOT_FOLDER, rootFolder);
                            }
                        }
                    }
                }
            }
            
        }
        
        if (resource instanceof IFile) {
            IFile file = (IFile) resource;
            String fileName = file.getName();
            if (isInSourceFolder(file)) {
                if (fileName.equals(ModuleManager.PACKAGE_FILE)) {
                    //a package descriptor has been added, removed, or changed
                    mustDoFullBuild.value = true;
                }
                if (fileName.equals(ModuleManager.MODULE_FILE)) {
                    //a module descriptor has been added, removed, or changed
                    mustResolveClasspathContainer.value = true;
                    mustDoFullBuild.value = true;
                }
            }
            if (fileName.equals(".classpath") ||
                    fileName.equals("config")) {
                //the classpath changed
                mustDoFullBuild.value = true;
                mustResolveClasspathContainer.value = true;
            }
            if (isJava(file) && 
                    ! file.getProject().equals(project)) {
                //a Java source file in a project we depend
                //on was modified - we must do a full build, 
                //'cos we don't know what Ceylon units in 
                //this project depend on it
                //TODO: fix that by tracking cross-project 
                //      dependencies to Java!
                mustDoFullBuild.value = true;
            }
            if (isSourceFile(file) || 
                    isResourceFile(file)) {
                // a source file or a resource was modified, 
                // we should at least compile incrementally
                somethingToBuild.value = true;
            }
        }
        
        return true;
    }
}