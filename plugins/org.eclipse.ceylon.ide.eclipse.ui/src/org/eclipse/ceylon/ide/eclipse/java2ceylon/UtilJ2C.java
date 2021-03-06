/********************************************************************************
 * Copyright (c) 2011-2017 Red Hat Inc. and/or its affiliates and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * http://www.eclipse.org/legal/epl-v10.html.
 *
 * SPDX-License-Identifier: EPL-1.0
 ********************************************************************************/
package org.eclipse.ceylon.ide.eclipse.java2ceylon;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.ceylon.ide.eclipse.util.Indents;
import org.eclipse.ceylon.ide.common.util.Path;
import org.eclipse.ceylon.ide.common.util.ProgressMonitor;

public interface UtilJ2C {

    Indents indents();

    IPath toEclipsePath(Path commonPath);

    Path fromEclipsePath(IPath eclipsePath);

    ProgressMonitor<IProgressMonitor> wrapProgressMonitor(IProgressMonitor monitor);
}