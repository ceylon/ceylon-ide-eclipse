/********************************************************************************
 * Copyright (c) 2011-2017 Red Hat Inc. and/or its affiliates and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * http://www.eclipse.org/legal/epl-v10.html.
 *
 * SPDX-License-Identifier: EPL-1.0
 ********************************************************************************/
import org.eclipse.ceylon.ide.common.model {
    BaseCeylonProject,
    CrossProjectJavaCompilationUnit
}
import org.eclipse.ceylon.model.loader.model {
    LazyPackage
}

import java.lang.ref {
    SoftReference
}

import org.eclipse.core.resources {
    IProject,
    IFolder,
    IFile
}
import org.eclipse.jdt.core {
    ITypeRoot,
    IJavaElement
}

shared class EclipseCrossProjectJavaCompilationUnit(
    BaseCeylonProject ceylonProject,
    ITypeRoot typeRoot,
    String theFilename,
    String theRelativePath,
    String theFullPath,
    LazyPackage pkg)
        extends CrossProjectJavaCompilationUnit<IProject, IFolder, IFile, ITypeRoot, IJavaElement>(ceylonProject, typeRoot, theFilename, theRelativePath, theFullPath, pkg) 
        satisfies EclipseJavaModelAware
        & EclipseJavaUnitUtils {
    shared actual variable SoftReference<EclipseJavaModelAware.ResolvedElements> resolvedElementsRef = 
            SoftReference<EclipseJavaModelAware.ResolvedElements>(null);
    
}