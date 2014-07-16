package com.redhat.ceylon.eclipse.code.navigator;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdapterFactory;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaModelException;


public class CeylonNavigatorAdapterFactory implements IAdapterFactory {

    private static Class<?>[] ADAPTER_LIST= new Class[] {
        IFolder.class,
        IPackageFragment.class,
        IResource.class,
        IJavaElement.class,
    };

    public Class<?>[] getAdapterList() {
        return ADAPTER_LIST;
    }

    public Object getAdapter(Object element, @SuppressWarnings("rawtypes") Class key) {
        SourceModuleNode sourceModule = (SourceModuleNode) element;

        IPackageFragment packageFragment = sourceModule.getMainPackageFragment();
        
        if (IJavaElement.class.equals(key)  || IPackageFragment.class.equals(key)) {
            return packageFragment;
        }

        if (IFolder.class.equals(key) || IResource.class.equals(key)) {
            if (packageFragment != null) {
                try {
                    return packageFragment.getCorrespondingResource();
                } catch (JavaModelException e) {
                }
            }
        }
        return null;
    }
}
