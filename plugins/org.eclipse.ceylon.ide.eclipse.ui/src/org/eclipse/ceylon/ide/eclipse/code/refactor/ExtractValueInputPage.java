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

import org.eclipse.jface.text.IRegion;
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

import org.eclipse.ceylon.ide.common.refactoring.ExtractValueRefactoring;
import org.eclipse.ceylon.ide.common.util.escaping_;

public class ExtractValueInputPage extends UserInputWizardPage {
    public ExtractValueInputPage(String name) {
        super(name);
    }

    public void createControl(Composite parent) {
        Composite result = new Composite(parent, SWT.NONE);
        setControl(result);
        GridLayout layout = new GridLayout();
        layout.numColumns = 2;
        result.setLayout(layout);
        Label label = new Label(result, SWT.RIGHT);  
        label.setText("Value name: ");
        final Text text = new Text(result, SWT.SINGLE|SWT.BORDER);
        text.setText(getExtractValueRefactoring().getNewName());
        text.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
        text.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent event) {
                String name = text.getText();
                validateIdentifier(name);
                getExtractValueRefactoring().setNewName(name);
            }
        });
        final Button et = new Button(result, SWT.CHECK);
        et.setText("Use explicit type declaration");
        et.setSelection(getExtractValueRefactoring().getExplicitType());
        et.addSelectionListener(new SelectionListener() {
            @Override
            public void widgetSelected(SelectionEvent event) {
                getExtractValueRefactoring().setExplicitType(
                        !getExtractValueRefactoring().getExplicitType());
            }
            @Override
            public void widgetDefaultSelected(SelectionEvent event) {}
        });
        final Button gs = new Button(result, SWT.CHECK);
        gs.setText("Create a getter");
        gs.addSelectionListener(new SelectionListener() {
            @Override
            public void widgetSelected(SelectionEvent event) {
                getExtractValueRefactoring().setGetter(!getExtractValueRefactoring().getGetter());
            }
            @Override
            public void widgetDefaultSelected(SelectionEvent event) {}
        });
        text.addKeyListener(new SubwordIterator(text));
        text.selectAll();
        text.setFocus();
    }

    @SuppressWarnings("unchecked")
    private ExtractValueRefactoring<IRegion> getExtractValueRefactoring() {
        return (ExtractValueRefactoring<IRegion>) getRefactoring();
    }

    void validateIdentifier(String name) {
        if (!name.matches("^[a-z_]\\w*$")) {
            setErrorMessage("Not a legal Ceylon identifier");
            setPageComplete(false);
        }
        else if (escaping_.get_().isKeyword(name)) {
            setErrorMessage("'" + name + "' is a Ceylon keyword");
            setPageComplete(false);
        }
        else {
            setErrorMessage(null);
            setPageComplete(true);
        }
    }

}
