/********************************************************************************
 * Copyright (c) 2011-2017 Red Hat Inc. and/or its affiliates and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * http://www.eclipse.org/legal/epl-v10.html.
 *
 * SPDX-License-Identifier: EPL-1.0
 ********************************************************************************/
package org.eclipse.ceylon.ide.eclipse.code.refactor;

import static org.eclipse.ceylon.ide.eclipse.code.refactor.MoveUtil.canMoveDeclaration;
import static org.eclipse.ceylon.ide.eclipse.util.EditorUtil.getCurrentEditor;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;

import org.eclipse.ceylon.ide.eclipse.code.editor.CeylonEditor;

public class MoveToUnitHandler extends AbstractHandler {
    
    @Override
    public Object execute(ExecutionEvent event) 
            throws ExecutionException {
        new MoveToUnitRefactoringAction(getCurrentEditor()).run();        
        return null;
    }

    @Override
    protected boolean isEnabled(CeylonEditor editor) {
        return canMoveDeclaration(editor);
    }
}
