package com.redhat.ceylon.eclipse.code.preferences;

import org.eclipse.jface.preference.ScaleFieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

public final class ScaleWithLabelFieldEditor extends
            ScaleFieldEditor {
    Label label;

    public ScaleWithLabelFieldEditor(String name, String labelText,
            Composite parent) {
        super(name, labelText, parent);
    }

    @Override
    protected void adjustForNumColumns(int numColumns) {
        ((GridData) scale.getLayoutData()).horizontalSpan = numColumns - 2;
    }

    @Override
    protected void doFillIntoGrid(Composite parent,
            int numColumns) {
        super.doFillIntoGrid(parent, numColumns-1);
        label = new Label(parent, SWT.SHADOW_IN);
        getScaleControl().addSelectionListener(new SelectionListener() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                label.setText(scale.getSelection() + " ms");
            }
            @Override
            public void widgetDefaultSelected(SelectionEvent e) {
                label.setText(scale.getSelection() + " ms");
            }
        });
        GridData gd = new GridData();
        label.setLayoutData(gd);
    }

    @Override
    public int getNumberOfControls() {
        return super.getNumberOfControls()+1;
    }

    @Override
    protected void createControl(Composite parent) {
        GridLayout layout = new GridLayout();
        layout.numColumns = getNumberOfControls();
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        layout.horizontalSpacing = HORIZONTAL_GAP;
        parent.setLayout(layout);
        doFillIntoGrid(parent, layout.numColumns);
    }

    @Override
    protected void doLoad() {
        super.doLoad();
        if (label != null) {
            int value = getPreferenceStore().getInt(getPreferenceName());
            label.setText(value + " ms       ");
        }
    }

    @Override
    protected void doLoadDefault() {
        super.doLoadDefault();
        if (label != null) {
            int value = getPreferenceStore().getDefaultInt(getPreferenceName());
            label.setText(value + " ms       ");
        }
    }
}