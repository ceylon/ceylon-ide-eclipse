package com.redhat.ceylon.eclipse.code.open;

import static com.redhat.ceylon.eclipse.code.editor.Navigation.gotoDeclaration;
import static com.redhat.ceylon.eclipse.ui.CeylonPlugin.PLUGIN_ID;
import static com.redhat.ceylon.eclipse.util.EditorUtil.getSelectionText;
import static org.eclipse.ui.PlatformUI.getWorkbench;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.texteditor.ITextEditor;

import com.redhat.ceylon.eclipse.ui.CeylonPlugin;
import com.redhat.ceylon.eclipse.ui.CeylonResources;

public class OpenDeclarationAction extends Action {
    
    private static final ImageDescriptor ICON = CeylonPlugin.getInstance().getImageRegistry()
            .getDescriptor(CeylonResources.CEYLON_OPEN_DECLARATION);
    
    private final IEditorPart editor;
    
    public OpenDeclarationAction(IEditorPart editor) {
        this("Open Ceylon Declaration...", editor);
    }
    
    public OpenDeclarationAction(String text, IEditorPart editor) {
        super(text);
        this.editor = editor;
        setActionDefinitionId(PLUGIN_ID + ".action.openDeclaration");
        setImageDescriptor(ICON);
    }
    
    @Override
    public void run() {
        Shell shell = getWorkbench().getActiveWorkbenchWindow().getShell();
        OpenCeylonDeclarationDialog dialog = 
                new OpenCeylonDeclarationDialog(shell, editor);
        dialog.setTitle("Open Ceylon Declaration");
        dialog.setMessage("Select a Ceylon declaration to open:");
        if (editor instanceof ITextEditor) {
            dialog.setInitialPattern(getSelectionText((ITextEditor) editor));
        }
        dialog.open();
        Object[] types = dialog.getResult();
        if (types != null && types.length > 0) {
            DeclarationWithProject dwp = (DeclarationWithProject) types[0];
            gotoDeclaration(dwp.getDeclaration(), dwp.getProject(), editor);
        }
    }

}