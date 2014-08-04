package com.redhat.ceylon.eclipse.code.open;

import static com.redhat.ceylon.eclipse.util.EditorUtil.getCurrentEditor;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;

public class OpenCeylonDeclarationInHierarchyHandler extends AbstractHandler {
    
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        new OpenDeclarationInHierarchyAction(getCurrentEditor()).run();
        return null;
    }
        
}