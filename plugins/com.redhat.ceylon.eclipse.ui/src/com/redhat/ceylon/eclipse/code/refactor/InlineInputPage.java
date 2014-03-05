package com.redhat.ceylon.eclipse.code.refactor;

import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

public class InlineInputPage extends UserInputWizardPage {
    public InlineInputPage(String name) {
        super(name);
    }

    public void createControl(Composite parent) {
        Composite result = new Composite(parent, SWT.NONE);
        setControl(result);
        GridLayout layout = new GridLayout();
        layout.numColumns = 2;
        result.setLayout(layout);
        Label title = new Label(result, SWT.LEFT);  
        title.setText("Inline " + getInlineRefactoring().getCount() + 
                " occurrences of declaration '" + 
                getInlineRefactoring().getDeclaration().getName() + "'.");
        GridData gd2 = new GridData(GridData.FILL_HORIZONTAL);
        gd2.horizontalSpan=2;
        new Label(result, SWT.SEPARATOR|SWT.HORIZONTAL).setLayoutData(gd2);
        GridData gd = new GridData();
        gd.horizontalSpan=2;
        title.setLayoutData(gd);
        boolean ref = getInlineRefactoring().isReference();
        Button radioOne = new Button(result, SWT.RADIO);
        radioOne.setText("Inline just this reference");
        radioOne.setSelection(false);
        radioOne.setEnabled(ref);
        Button radioAll = new Button(result, SWT.RADIO);
        radioAll.setText("Inline all references and delete declaration");
        radioAll.setSelection(true);
        radioAll.setEnabled(ref);
        SelectionListener listener = new SelectionListener() {
            @Override
            public void widgetSelected(SelectionEvent event) {
                getInlineRefactoring().setDelete();
                getInlineRefactoring().setJustOne();
            }
            @Override
            public void widgetDefaultSelected(SelectionEvent event) {}
        };
        radioOne.addSelectionListener(listener);
    }

    private InlineRefactoring getInlineRefactoring() {
        return (InlineRefactoring) getRefactoring();
    }
}
