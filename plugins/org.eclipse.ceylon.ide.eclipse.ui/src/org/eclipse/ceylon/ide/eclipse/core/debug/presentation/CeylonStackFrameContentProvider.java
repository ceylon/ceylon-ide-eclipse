/********************************************************************************
 * Copyright (c) 2011-2017 Red Hat Inc. and/or its affiliates and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * http://www.eclipse.org/legal/epl-v10.html.
 *
 * SPDX-License-Identifier: EPL-1.0
 ********************************************************************************/
package org.eclipse.ceylon.ide.eclipse.core.debug.presentation;

import static org.eclipse.ceylon.ide.eclipse.core.debug.presentation.CeylonContentProviderFilter.filterVariables;
import static org.eclipse.ceylon.ide.eclipse.core.debug.presentation.CeylonPresentationContext.isCeylonContext;
import static org.eclipse.ceylon.ide.eclipse.core.debug.presentation.CeylonPresentationContext.toCeylonContextIfNecessary;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IPresentationContext;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IViewerUpdate;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.internal.debug.ui.variables.JavaStackFrameContentProvider;

public class CeylonStackFrameContentProvider extends JavaStackFrameContentProvider {

    @Override
    protected Object[] getChildren(Object parent, int index, int length, IPresentationContext context, IViewerUpdate monitor) throws CoreException {
        return super.getChildren(parent, index, length, toCeylonContextIfNecessary(context, monitor), monitor);
    }
        
    @Override
    protected int getChildCount(Object element, IPresentationContext context, IViewerUpdate monitor) throws CoreException {
        return super.getChildCount(element, toCeylonContextIfNecessary(context, monitor), monitor);
    }
    
    @Override
    protected boolean hasChildren(Object element, IPresentationContext context, IViewerUpdate monitor) throws CoreException {
        IPresentationContext newContext = toCeylonContextIfNecessary(context, monitor);
        if (isCeylonContext(newContext)) {
            return getChildCount(element, newContext, monitor) > 0;
        } else {
            return super.hasChildren(element, newContext, monitor);
        }
    }
    
    @Override
    protected Object[] getAllChildren(Object parent, IPresentationContext context, IViewerUpdate monitor) throws CoreException {
        try {
            Object[] variables = super.getAllChildren(parent, context, monitor);
            if (isCeylonContext(context)) {
                return filterVariables(variables, context);
            }
            return variables;
        } catch (CoreException e) {
            if (e.getStatus().getCode() == IJavaThread.ERR_THREAD_NOT_SUSPENDED) {
                monitor.cancel();
                return EMPTY;
            }
            throw e;
        }
    }
}
