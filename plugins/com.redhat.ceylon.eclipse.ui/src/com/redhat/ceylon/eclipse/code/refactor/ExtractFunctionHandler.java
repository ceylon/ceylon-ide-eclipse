package com.redhat.ceylon.eclipse.code.refactor;

import static com.redhat.ceylon.eclipse.code.editor.EditorUtil.getCurrentEditor;
import static com.redhat.ceylon.eclipse.code.refactor.ExtractLinkedMode.useLinkedMode;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.texteditor.ITextEditor;

import com.redhat.ceylon.eclipse.code.editor.CeylonEditor;

public class ExtractFunctionHandler extends AbstractHandler {
        
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        ITextEditor editor = (ITextEditor) getCurrentEditor();
        if (useLinkedMode() && editor instanceof CeylonEditor) {
            CeylonEditor ce = (CeylonEditor)editor;
            if (ce.isInLinkedMode()) {
                if (ce.getLinkedModeOwner() instanceof ExtractFunctionLinkedMode) {
                    ExtractFunctionLinkedMode current = 
                            (ExtractFunctionLinkedMode) ce.getLinkedModeOwner();
                    current.enterDialogMode();
                    current.openDialog();
                }
                else {
                    new ExtractFunctionRefactoringAction(editor).run();
                }
            }
            else {
                new ExtractFunctionLinkedMode(ce).start();
            }
        }
        else {
            new ExtractFunctionRefactoringAction(editor).run();
        }
        return null;
    }
            
}
