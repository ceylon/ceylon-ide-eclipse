package com.redhat.ceylon.test.eclipse.plugin.ui;

import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestImageRegistry.TESTS_ERROR;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestImageRegistry.TESTS_FAILED;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestImageRegistry.TESTS_INTERRUPTED;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestImageRegistry.TESTS_RUNNING;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestImageRegistry.TESTS_SUCCESS;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestMessages.compare;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestMessages.historyColumnErrors;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestMessages.historyColumnFailures;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestMessages.historyColumnIgnored;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestMessages.historyColumnName;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestMessages.historyColumnStartDate;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestMessages.historyColumnSuccess;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestMessages.historyColumnTotal;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestMessages.historyDlgCanNotCompareRunningTest;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestMessages.historyDlgCanNotRemoveRunningTest;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestMessages.historyDlgMessage;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestMessages.historyDlgTitle;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestMessages.information;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestMessages.remove;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestMessages.removeAll;
import static com.redhat.ceylon.test.eclipse.plugin.util.CeylonTestUtil.getDisplay;

import java.text.DateFormat;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import com.redhat.ceylon.test.eclipse.plugin.CeylonTestImageRegistry;
import com.redhat.ceylon.test.eclipse.plugin.CeylonTestPlugin;
import com.redhat.ceylon.test.eclipse.plugin.model.TestElement;
import com.redhat.ceylon.test.eclipse.plugin.model.TestRun;
import com.redhat.ceylon.test.eclipse.plugin.model.TestRunContainer;
import com.redhat.ceylon.test.eclipse.plugin.model.TestRunListener;

public class HistoryDialog extends TitleAreaDialog {

    private TestRunContainer testRunContainer;
    private TestRunListener testRunListener;
    private TestRun selectedTestRun;

    private Composite panel;
    private TableViewer tableViewer;
    private Button buttonCompare;
    private Button buttonRemove;
    private Button buttonRemoveAll;

    private DateFormat startDateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);
    private Color successColor = new Color(getDisplay(), 95, 191, 95);
    private Color failureColor = new Color(getDisplay(), 159, 63, 63);
    private Color ignoredColor = new Color(getDisplay(), 160, 160, 160);

    public HistoryDialog(Shell shell) {
        super(shell);
        createTestRunListener();
        testRunContainer = CeylonTestPlugin.getDefault().getModel();
        testRunContainer.addTestRunListener(testRunListener);
    }

    public TestRun getSelectedTestRun() {
        return selectedTestRun;
    }

    @Override
    protected boolean isResizable() {
        return true;
    }

    @Override
    protected Point getInitialSize() {
        return new Point(900, 500);
    }

    @Override
    public void create() {
        setBlockOnOpen(true);
        setHelpAvailable(false);
        super.create();
        setTitle(historyDlgTitle);
        setMessage(historyDlgMessage);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText(historyDlgTitle);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        GridLayout layout = new GridLayout(2, false);
        layout.horizontalSpacing = 10;

        panel = new Composite(parent, SWT.NONE);
        panel.setLayout(layout);
        panel.setLayoutData(GridDataFactory.swtDefaults().align(SWT.FILL, SWT.FILL).grab(true, true).create());
        parent.setLayout(new GridLayout(1, false));

        createViewer();
        createButtonCompare();
        createButtonRemove();
        createButtonRemoveAll();

        return parent;
    }

    private void createViewer() {
        tableViewer = new TableViewer(panel, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);

        createColumnName();
        createColumnStartDate();
        createColumnCounts();

        tableViewer.setContentProvider(ArrayContentProvider.getInstance());
        tableViewer.setInput(testRunContainer.getTestRuns());
        tableViewer.getTable().setHeaderVisible(true);
        tableViewer.getTable().setLinesVisible(true);
        tableViewer.getTable().setLayoutData(GridDataFactory.swtDefaults().align(SWT.FILL, SWT.FILL).grab(true, true).span(1, 4).create());
    }

    private void createColumnName() {
        TableViewerColumn colName = new TableViewerColumn(tableViewer, SWT.NONE);
        colName.getColumn().setWidth(270);
        colName.getColumn().setText(historyColumnName);
        colName.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((TestRun) element).getRunName();
            }

            @Override
            public Image getImage(Object element) {
                TestRun testRun = (TestRun) element;
                Image image = null;
                if (testRun.isRunning()) {
                    image = CeylonTestImageRegistry.getImage(TESTS_RUNNING);
                } else if (testRun.isSuccess()) {
                    image = CeylonTestImageRegistry.getImage(TESTS_SUCCESS);
                } else if (testRun.isInterrupted()) {
                    image = CeylonTestImageRegistry.getImage(TESTS_INTERRUPTED);
                } else if (testRun.getErrorCount() != 0) {
                    image = CeylonTestImageRegistry.getImage(TESTS_ERROR);
                } else if (testRun.getFailureCount() != 0) {
                    image = CeylonTestImageRegistry.getImage(TESTS_FAILED);
                }
                return image;
            }
        });
    }

    private void createColumnStartDate() {
        TableViewerColumn colStartDate = new TableViewerColumn(tableViewer, SWT.NONE);
        colStartDate.getColumn().setWidth(160);
        colStartDate.getColumn().setText(historyColumnStartDate);
        colStartDate.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return startDateFormat.format(((TestRun) element).getRunStartDate());
            }
        });
    }

    private void createColumnCounts() {
        TableViewerColumn colTotal = new TableViewerColumn(tableViewer, SWT.NONE);
        colTotal.getColumn().setWidth(65);
        colTotal.getColumn().setText(historyColumnTotal);
        colTotal.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return Integer.toString(((TestRun) element).getTotalCount());
            }
        });

        TableViewerColumn colSuccess = new TableViewerColumn(tableViewer, SWT.NONE);
        colSuccess.getColumn().setWidth(65);
        colSuccess.getColumn().setText(historyColumnSuccess);
        colSuccess.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return Integer.toString(((TestRun) element).getSuccessCount());
            }
            @Override
            public Color getForeground(Object element) {
                return successColor;
            }
        });

        TableViewerColumn colFailures = new TableViewerColumn(tableViewer, SWT.NONE);
        colFailures.getColumn().setWidth(65);
        colFailures.getColumn().setText(historyColumnFailures);
        colFailures.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return Integer.toString(((TestRun) element).getFailureCount());
            }
            @Override
            public Color getForeground(Object element) {
                return failureColor;
            }
        });

        TableViewerColumn colErrors = new TableViewerColumn(tableViewer, SWT.NONE);
        colErrors.getColumn().setWidth(65);
        colErrors.getColumn().setText(historyColumnErrors);
        colErrors.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return Integer.toString(((TestRun) element).getErrorCount());
            }
            @Override
            public Color getForeground(Object element) {
                return failureColor;
            }
        });
        
        TableViewerColumn colIgnored = new TableViewerColumn(tableViewer, SWT.NONE);
        colIgnored.getColumn().setWidth(65);
        colIgnored.getColumn().setText(historyColumnIgnored);
        colIgnored.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return Integer.toString(((TestRun) element).getIgnoreCount());
            }
            @Override
            public Color getForeground(Object element) {
                return ignoredColor;
            }
        });
        
    }

    private void createButtonCompare() {
        buttonCompare = new Button(panel, SWT.PUSH);
        buttonCompare.setEnabled(false);
        buttonCompare.setText(compare);
        buttonCompare.setLayoutData(GridDataFactory.swtDefaults().align(SWT.FILL, SWT.CENTER).hint(110, SWT.DEFAULT).create());
        buttonCompare.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                IStructuredSelection selection = (IStructuredSelection) tableViewer.getSelection();
                if (selection.size() == 2) {
                    TestRun testRun1 = (TestRun) selection.toArray()[0];
                    TestRun testRun2 = (TestRun) selection.toArray()[1];

                    if (testRun1.isRunning() || testRun2.isRunning()) {
                        MessageDialog.openInformation(getShell(), information, historyDlgCanNotCompareRunningTest);
                        return;
                    }

                    TestRun testRunOlder = null;
                    TestRun testRunYounger = null;
                    if (testRun1.getRunStartDate().before(testRun2.getRunStartDate())) {
                        testRunOlder = testRun1;
                        testRunYounger = testRun2;
                    } else {
                        testRunOlder = testRun2;
                        testRunYounger = testRun1;
                    }

                    CompareRunsDialog dlg = new CompareRunsDialog(getShell(), testRunOlder, testRunYounger);
                    dlg.open();
                }
            }
        });
        tableViewer.addSelectionChangedListener(new ISelectionChangedListener() {
            @Override
            public void selectionChanged(SelectionChangedEvent event) {
                IStructuredSelection selection = (IStructuredSelection) event.getSelection();
                if (selection.size() == 2) {
                    buttonCompare.setEnabled(true);
                } else {
                    buttonCompare.setEnabled(false);
                }
            }
        });
    }

    private void createButtonRemove() {
        buttonRemove = new Button(panel, SWT.PUSH);
        buttonRemove.setText(remove);
        buttonRemove.setLayoutData(GridDataFactory.swtDefaults().align(SWT.FILL, SWT.CENTER).indent(0, 10).create());
        buttonRemove.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                IStructuredSelection selection = (IStructuredSelection) tableViewer.getSelection();
                if (!selection.isEmpty()) {
                    for (Object selectedElement : selection.toArray()) {
                        TestRun testRun = (TestRun) selectedElement;
                        if (testRun.isRunning()) {
                            MessageDialog.openInformation(getShell(), information, historyDlgCanNotRemoveRunningTest);
                        } else {
                            testRunContainer.removeTestRun(testRun);
                        }
                    }
                }
            }
        });
    }

    private void createButtonRemoveAll() {
        buttonRemoveAll = new Button(panel, SWT.PUSH);
        buttonRemoveAll.setText(removeAll);
        buttonRemoveAll.setLayoutData(GridDataFactory.swtDefaults().align(SWT.FILL, SWT.CENTER).create());
        buttonRemoveAll.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                for (TestRun testRun : testRunContainer.getTestRuns()) {
                    if (!testRun.isRunning()) {
                        testRunContainer.removeTestRun(testRun);
                    }
                }
            }
        });
    }

    @Override
    public boolean close() {
        IStructuredSelection selection = (IStructuredSelection) tableViewer.getSelection();
        if (!selection.isEmpty()) {
            Object selectedElement = selection.getFirstElement();
            if (selectedElement instanceof TestRun) {
                selectedTestRun = (TestRun) selectedElement;
            }
        }

        testRunContainer.removeTestRunListener(testRunListener);
        successColor.dispose();
        failureColor.dispose();
        ignoredColor.dispose();
        return super.close();
    }

    private void createTestRunListener() {
        testRunListener = new TestRunListener() {
            @Override
            public void testRunAdded(TestRun testRun) {
                updateViewAsync();
            }
            @Override
            public void testRunRemoved(TestRun testRun) {
                updateViewAsync();
            }
            @Override
            public void testRunStarted(TestRun testRun) {
                updateViewAsync();
            }
            @Override
            public void testRunFinished(TestRun testRun) {
                updateViewAsync();
            }
            @Override
            public void testRunInterrupted(TestRun testRun) {
                updateViewAsync();
            }
            @Override
            public void testStarted(TestRun testRun, TestElement testElement) {
                updateViewAsync();
            }
            @Override
            public void testFinished(TestRun testRun, TestElement testElement) {
                updateViewAsync();
            }
        };
    }

    private void updateViewAsync() {
        getDisplay().asyncExec(new Runnable() {
            public void run() {
                if (tableViewer != null && !tableViewer.getTable().isDisposed()) {
                    tableViewer.refresh();
                }
            }
        });
    }

}