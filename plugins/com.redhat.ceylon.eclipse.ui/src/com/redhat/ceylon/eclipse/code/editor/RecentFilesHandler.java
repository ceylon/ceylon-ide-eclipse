package com.redhat.ceylon.eclipse.code.editor;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.swt.widgets.Shell;

import com.redhat.ceylon.eclipse.ui.CeylonPlugin;

public class RecentFilesHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        Shell shell = CeylonPlugin.getInstance().getWorkbench()
                .getActiveWorkbenchWindow().getShell();
        RecentFilesPopup popup = new RecentFilesPopup(shell);
        popup.open();
        popup.setFocus();
        return null;
    }

}
