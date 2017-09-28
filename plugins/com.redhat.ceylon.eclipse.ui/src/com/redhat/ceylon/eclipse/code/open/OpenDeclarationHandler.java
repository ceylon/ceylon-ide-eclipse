package org.eclipse.ceylon.ide.eclipse.code.open;

import static org.eclipse.ceylon.ide.eclipse.util.EditorUtil.getCurrentEditor;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;

public class OpenDeclarationHandler extends AbstractHandler {
    
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        new OpenDeclarationAction(getCurrentEditor()).run();
        return null;
    }
        
}