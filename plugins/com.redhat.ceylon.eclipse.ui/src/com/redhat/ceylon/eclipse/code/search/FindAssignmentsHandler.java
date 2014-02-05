package com.redhat.ceylon.eclipse.code.search;

import static com.redhat.ceylon.eclipse.code.editor.EditorUtil.getCurrentEditor;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;

public class FindAssignmentsHandler extends AbstractHandler {
        
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        new FindAssignmentsAction(getCurrentEditor()).run();
        return null;
    }
            
}
