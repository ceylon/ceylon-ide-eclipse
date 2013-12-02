package com.redhat.ceylon.eclipse.core.model;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ITypeRoot;

import com.redhat.ceylon.compiler.typechecker.model.Declaration;

public interface IJavaModelAware extends IProjectAware {
    ITypeRoot getTypeRoot();
    IJavaElement toJavaElement(Declaration ceylonDeclaration);
    
}
