package org.eclipse.ceylon.ide.eclipse.core.adapters;

import org.eclipse.core.runtime.IAdapterFactory;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;

import org.eclipse.ceylon.model.typechecker.model.Package;
import org.eclipse.ceylon.ide.eclipse.core.builder.CeylonBuilder;
import org.eclipse.ceylon.ide.common.model.BaseIdeModule;
import org.eclipse.ceylon.ide.common.model.IJavaModelAware;
import org.eclipse.ceylon.ide.common.model.IUnit;


public class JavaElementAdapterFactory implements IAdapterFactory {
    
    private static Class<?>[] ADAPTER_LIST= new Class[] {
        IUnit.class,
        IJavaModelAware.class,
        BaseIdeModule.class,
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
        if (BaseIdeModule.class.equals(key) && element instanceof IPackageFragment) {
            return CeylonBuilder.asSourceModule((IPackageFragment) element);
        }
        return null;
    }
}
