package com.redhat.ceylon.eclipse.code.preferences;

import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.ElementTreeSelectionDialog;
import org.eclipse.ui.dialogs.SearchPattern;

public abstract class FilteredElementTreeSelectionDialog extends
        ElementTreeSelectionDialog {
    
    public FilteredElementTreeSelectionDialog(Shell parent,
            ILabelProvider labelProvider,
            ITreeContentProvider contentProvider) {
        super(parent, labelProvider, contentProvider);
    }
    
    protected abstract String getElementName(Object element);
    
    private Text createFilterText(Composite parent) {
        final Text text = new Text(parent, SWT.BORDER);
        final SearchPattern searchPattern = new SearchPattern();
        searchPattern.setPattern("");
        addFilter(new ViewerFilter() {
            @Override
            public boolean select(Viewer viewer, 
                    Object parentElement, Object element) {
                return searchPattern.matches(getElementName(element));
            }
        });
        GridData data = new GridData();
        data.grabExcessVerticalSpace = false;
        data.grabExcessHorizontalSpace = true;
        data.horizontalAlignment = GridData.FILL;
        data.verticalAlignment = GridData.BEGINNING;
        text.setLayoutData(data);
        text.setFont(parent.getFont());
        text.setText("");
        text.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent e) {
                searchPattern.setPattern(text.getText());
                getTreeViewer().refresh();
            }
        });
        return text;
    }

    @Override
    protected Label createMessageArea(Composite composite) {
        Label result = super.createMessageArea(composite);
        createFilterText(composite);
        return result;
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        GridData gridData = new GridData(GridData.FILL_BOTH);
        gridData.grabExcessHorizontalSpace = true;
        SashForm sashForm = new SashForm(parent, SWT.VERTICAL);
        Control result = super.createDialogArea(sashForm);
        Composite composite = new Composite(sashForm, SWT.BORDER);
        composite.setLayoutData(gridData);
        GridLayout layout = new GridLayout(1, true);
        layout.marginWidth=0;
        layout.marginHeight=0;
        composite.setLayout(layout);
        final Browser browser = new Browser(composite, SWT.NONE);
        sashForm.setWeights(new int[] {3, 1});
        sashForm.setLayoutData(gridData);
        getTreeViewer().addSelectionChangedListener(new ISelectionChangedListener() {
            @Override
            public void selectionChanged(SelectionChangedEvent event) {
                browser.setText(getDoc());
            }
        });
        browser.setLayoutData(gridData);
        return result;
    }

    protected String getDoc() {
        return "";
    }
    
}