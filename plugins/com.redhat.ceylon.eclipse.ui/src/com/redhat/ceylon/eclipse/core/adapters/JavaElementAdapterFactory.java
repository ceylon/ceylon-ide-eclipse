package com.redhat.ceylon.eclipse.core.adapters;

import org.eclipse.core.runtime.IAdapterFactory;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;

import com.redhat.ceylon.compiler.typechecker.model.Package;
import com.redhat.ceylon.eclipse.core.builder.CeylonBuilder;
import com.redhat.ceylon.eclipse.core.model.IJavaModelAware;
import com.redhat.ceylon.eclipse.core.model.IUnit;
import com.redhat.ceylon.eclipse.core.model.JDTModule;


public class JavaElementAdapterFactory implements IAdapterFactory {
    
    private static Class<?>[] ADAPTER_LIST= new Class[] {
        IUnit.class,
        IJavaModelAware.class,
        JDTModule.class,
        Package.class,
    };

    public Class<?>[] getAdapterList() {
        return ADAPTER_LIST;
    }

    public Object getAdapter(Object element, @SuppressWarnings("rawtypes") Class key) {
        IJavaElement javaElement = (IJavaElement) element;

        if (IUnit.class.equals(key)) {
            return CeylonBuilder.getUnit(javaElement);
        }
        if (IJavaModelAware.class.equals(key)) {
            return CeylonBuilder.getUnit(javaElement);
        }
        if (Package.class.equals(key) && element instanceof IPackageFragment) {
            return CeylonBuilder.getPackage((IPackageFragment) element);
        }
        if (JDTModule.class.equals(key) && element instanceof IPackageFragment) {
            return CeylonBuilder.asSourceModule((IPackageFragment) element);
        }
        return null;
    }
}
