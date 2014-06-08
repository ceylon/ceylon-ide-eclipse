package com.redhat.ceylon.eclipse.code.refactor;


import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import com.redhat.ceylon.eclipse.util.Escaping;

public class RenameInputPage extends UserInputWizardPage {
    public RenameInputPage(String name) {
        super(name);
    }

    public void createControl(Composite parent) {
        Composite result = new Composite(parent, SWT.NONE);
        setControl(result);
        GridLayout layout = new GridLayout();
        layout.numColumns = 3;
        result.setLayout(layout);
        Label title = new Label(result, SWT.LEFT);  
        String name = getRenameRefactoring().getDeclaration().getName();
        title.setText("Rename " + getRenameRefactoring().getCount() + 
                " occurrences of declaration '" + 
                name + "'.");
        GridData gd = new GridData();
        gd.horizontalSpan=3;
        title.setLayoutData(gd);
        GridData gd2 = new GridData(GridData.FILL_HORIZONTAL);
        gd2.horizontalSpan=3;
        new Label(result, SWT.SEPARATOR|SWT.HORIZONTAL).setLayoutData(gd2);
        
        Label label = new Label(result, SWT.RIGHT);  
        label.setText("Rename to: ");
        final Text text = new Text(result, SWT.SINGLE|SWT.BORDER);
        GridData gd3 = new GridData();
        gd3.horizontalSpan=2;
        text.setLayoutData(gd3);
        text.setText(getRenameRefactoring().getNewName());
        text.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
        text.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent event) {
                String name = text.getText();
                validateIdentifier(name);
                getRenameRefactoring().setNewName(name);
            }
        });
        text.addKeyListener(new SubwordIterator(text));
        text.selectAll();
        text.setFocus();
        
        final Button checkbox = new Button(result, SWT.CHECK);
        GridData gd4 = new GridData();
        gd4.horizontalSpan=3;
        checkbox.setLayoutData(gd4);
        String filename = getRenameRefactoring().getDeclaration().getUnit().getFilename();
        checkbox.setText("Also rename source file '" + filename + "'");
        checkbox.setSelection(getRenameRefactoring().isRenameFile());
        checkbox.setEnabled(filename.endsWith(".ceylon"));
        checkbox.addSelectionListener(new SelectionListener() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                getRenameRefactoring().setRenameFile(checkbox.getSelection());
            }
            @Override
            public void widgetDefaultSelected(SelectionEvent e) {}
        });
    }
    
    private RenameRefactoring getRenameRefactoring() {
        return (RenameRefactoring) getRefactoring();
    }
    
    void validateIdentifier(String name) {
        if (!name.matches("^[a-zA-Z_]\\w*$")) {
            setErrorMessage("Not a legal Ceylon identifier");
            setPageComplete(false);
        }
        else if (Escaping.KEYWORDS.contains(name)) {
            setErrorMessage("'" + name + "' is a Ceylon keyword");
            setPageComplete(false);
        }
        else {
            setErrorMessage(null);
            setPageComplete(true);
        }
    }

}
