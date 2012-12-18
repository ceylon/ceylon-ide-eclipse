package com.redhat.ceylon.test.eclipse.plugin.testview;

import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestImageRegistry.RELAUNCH;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestImageRegistry.SHOW_FAILURES;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestImageRegistry.SHOW_NEXT;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestImageRegistry.SHOW_PREV;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestImageRegistry.STOP;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestImageRegistry.TEST;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestImageRegistry.TESTS;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestImageRegistry.TESTS_ERROR;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestImageRegistry.TESTS_FAILED;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestImageRegistry.TESTS_RUNNING;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestImageRegistry.TESTS_SUCCESS;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestImageRegistry.TEST_ERROR;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestImageRegistry.TEST_FAILED;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestImageRegistry.TEST_RUNNING;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestImageRegistry.TEST_SUCCESS;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestImageRegistry.getImage;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestMessages.relaunchLabel;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestMessages.showFailuresOnlyLabel;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestMessages.showNextFailureLabel;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestMessages.showPreviousFailureLabel;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestMessages.showTestsGroupedByPackages;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestMessages.stopLabel;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestPlugin.PREF_SHOW_FAILURES_ONLY;
import static com.redhat.ceylon.test.eclipse.plugin.CeylonTestPlugin.PREF_SHOW_TESTS_GROUPED_BY_PACKAGES;

import java.util.List;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.StyledCellLabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;

import com.redhat.ceylon.test.eclipse.plugin.CeylonTestImageRegistry;
import com.redhat.ceylon.test.eclipse.plugin.CeylonTestPlugin;
import com.redhat.ceylon.test.eclipse.plugin.model.TestElement;
import com.redhat.ceylon.test.eclipse.plugin.model.TestElement.State;
import com.redhat.ceylon.test.eclipse.plugin.model.TestRun;

public class TestViewer extends Composite {

    private TestRun currentTestRun;
    private TestViewPart viewPart;
    private TreeViewer viewer;
    private ShowFailuresOnlyAction showFailuresOnlyAction;
    private ShowPreviousFailureAction showPreviousFailureAction;
    private ShowNextFailureAction showNextFailureAction;
    private ShowFailuresOnlyFilter showFailuresOnlyFilter;
    private ShowTestsGroupedByPackagesAction showTestsGroupedByPackagesAction;
    private RelaunchAction relaunchAction;
    private StopAction stopAction;

    public TestViewer(TestViewPart viewPart, Composite parent) {
        super(parent, SWT.NONE);
        
        this.viewPart = viewPart;

        GridLayout gridLayout = new GridLayout(1, false);
        gridLayout.marginLeft = 0;
        gridLayout.marginRight = 0;
        gridLayout.marginWidth = 0;
        gridLayout.marginHeight = 0;
        setLayout(gridLayout);

        createToolBar();
        createMenuBar();
        createViewer();
    }

    private void createToolBar() {
        showNextFailureAction = new ShowNextFailureAction();
        showPreviousFailureAction = new ShowPreviousFailureAction();
        showFailuresOnlyAction = new ShowFailuresOnlyAction();
        showFailuresOnlyFilter = new ShowFailuresOnlyFilter();
        relaunchAction = new RelaunchAction();
        stopAction = new StopAction();
        
        IToolBarManager toolBarManager = viewPart.getViewSite().getActionBars().getToolBarManager();
        toolBarManager.add(showNextFailureAction);
        toolBarManager.add(showPreviousFailureAction);
        toolBarManager.add(showFailuresOnlyAction);
        toolBarManager.add(new Separator());
        toolBarManager.add(relaunchAction);
        toolBarManager.add(stopAction);
        toolBarManager.update(true);
    }    

    private void createMenuBar() {
        showTestsGroupedByPackagesAction = new ShowTestsGroupedByPackagesAction();
        
        IMenuManager menuManager = viewPart.getViewSite().getActionBars().getMenuManager();
        menuManager.add(showTestsGroupedByPackagesAction);
        menuManager.update(true);
    }

    private void createViewer() {
        viewer = new TreeViewer(this, SWT.BORDER | SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL);
        viewer.setContentProvider(new TestContentProvider());
        viewer.setLabelProvider(new TestLabelProvider());
        viewer.getControl().setLayoutData(GridDataFactory.swtDefaults().align(SWT.FILL, SWT.FILL).grab(true, true).create());
    }
    
    public void setCurrentTestRun(TestRun currentTestRun) {
        this.currentTestRun = currentTestRun;
        viewer.setInput(currentTestRun);
        updateView();
    }

    public void updateView() {
        viewer.refresh();

        boolean containsFailures = false;
        boolean canRelaunch = false;
        boolean canStop = false;
        
        if (currentTestRun != null) {
            containsFailures = !currentTestRun.isSuccess();
            canRelaunch = !currentTestRun.isRunning();
            canStop = currentTestRun.isRunning();
        }

        showNextFailureAction.setEnabled(containsFailures);
        showPreviousFailureAction.setEnabled(containsFailures);
        relaunchAction.setEnabled(canRelaunch);
        stopAction.setEnabled(canStop);
    }
    
    public TreeViewer getViewer() {
        return viewer;
    }
    
    private class TestContentProvider implements ITreeContentProvider {

        @Override
        public Object[] getElements(Object inputElement) {
            boolean isGrouped = showTestsGroupedByPackagesAction.isChecked();
            if (inputElement instanceof TestRun) {
                TestRun testRun = (TestRun) inputElement;
                if (isGrouped) {
                    return testRun.getTestElementsByPackages().keySet().toArray();
                } else {
                    return testRun.getTestElements().toArray();
                }
            }
            return null;
        }

        @Override
        public Object[] getChildren(Object parentElement) {
            boolean isGrouped = showTestsGroupedByPackagesAction.isChecked();
            if (isGrouped && parentElement instanceof String) {
                String packageName = (String) parentElement;
                List<TestElement> testElementsInPackage = currentTestRun.getTestElementsByPackages().get(packageName);
                if (testElementsInPackage != null) {
                    return testElementsInPackage.toArray();
                }
            }
            return null;
        }

        @Override
        public boolean hasChildren(Object element) {
            boolean isGrouped = showTestsGroupedByPackagesAction.isChecked();
            if (isGrouped && element instanceof String) {
                return true;
            }
            return false;
        }

        @Override
        public Object getParent(Object element) {
            return null;
        }

        @Override
        public void dispose() {
        }

        @Override
        public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
        }

    }

    private class TestLabelProvider extends StyledCellLabelProvider {

        @Override
        public void update(ViewerCell cell) {
            boolean isGrouped = showTestsGroupedByPackagesAction.isChecked();
            
            String text = null;
            Image image = null;
            
            if (cell.getElement() instanceof TestElement) {
                TestElement testElement = (TestElement) cell.getElement();
                text = isGrouped ? testElement.getName() : testElement.getQualifiedName();
                
                switch(testElement.getState()) {
                    case RUNNING: image = getImage(TEST_RUNNING); break;
                    case SUCCESS: image = getImage(TEST_SUCCESS); break;
                    case FAILURE: image = getImage(TEST_FAILED); break;
                    case ERROR: image = getImage(TEST_ERROR); break;
                    default: image = getImage(TEST); break;
                }
            }
            if (cell.getElement() instanceof String) {
                String packageName = (String) cell.getElement();
                text = packageName;
                
                State state = currentTestRun.getPackageState(packageName);
                switch(state) {
                    case RUNNING: image = getImage(TESTS_RUNNING); break;
                    case SUCCESS: image = getImage(TESTS_SUCCESS); break;
                    case FAILURE: image = getImage(TESTS_FAILED); break;
                    case ERROR: image = getImage(TESTS_ERROR); break;
                    default: image = getImage(TESTS); break;
                }
            }

            cell.setText(text);
            cell.setImage(image);

            super.update(cell);
        }

    }

    private class ShowNextFailureAction extends Action {

        public ShowNextFailureAction() {
            super(showNextFailureLabel);
            setDescription(showNextFailureLabel);
            setToolTipText(showNextFailureLabel);
            setImageDescriptor(CeylonTestImageRegistry.getImageDescriptor(SHOW_NEXT));
            setEnabled(false);
        }

        @Override
        public void run() {
            if( currentTestRun == null ) {
                return;
            }
            
            Object currentElement = ((IStructuredSelection) viewer.getSelection()).getFirstElement();
            Object nextElement = null;

            int fromIndex = 0;
            if (currentElement != null) {
                fromIndex = currentTestRun.getTestElements().indexOf(currentElement) + 1;
            }

            if (fromIndex < currentTestRun.getTestElements().size()) {
                for (int i = fromIndex; i < currentTestRun.getTestElements().size(); i++) {
                    TestElement testElement = currentTestRun.getTestElements().get(i);
                    if (testElement.getState() == State.FAILURE || testElement.getState() == State.ERROR) {
                        nextElement = testElement;
                        break;
                    }
                }
            }

            if (nextElement != null) {
                viewer.setSelection(new StructuredSelection(nextElement), true);
            }
        }
        
    }

    private class ShowPreviousFailureAction extends Action {

        public ShowPreviousFailureAction() {
            super(showPreviousFailureLabel);
            setDescription(showPreviousFailureLabel);
            setToolTipText(showPreviousFailureLabel);
            setImageDescriptor(CeylonTestImageRegistry.getImageDescriptor(SHOW_PREV));
            setEnabled(false);
        }

        @Override
        public void run() {
            if( currentTestRun == null ) {
                return;
            }
            
            Object currentElement = ((IStructuredSelection) viewer.getSelection()).getFirstElement();
            Object prevElement = null;

            int fromIndex = currentTestRun.getTestElements().size() - 1;
            if (currentElement != null) {
                fromIndex = currentTestRun.getTestElements().indexOf(currentElement) - 1;
            }

            if (fromIndex >= 0) {
                for (int i = fromIndex; i >= 0; i--) {
                    TestElement testElement = currentTestRun.getTestElements().get(i);
                    if (testElement.getState() == State.FAILURE || testElement.getState() == State.ERROR) {
                        prevElement = testElement;
                        break;
                    }
                }
            }

            if (prevElement != null) {
                viewer.setSelection(new StructuredSelection(prevElement), true);
            }
        }
        
    }

    private class ShowFailuresOnlyAction extends Action {
        
        public ShowFailuresOnlyAction() {
            super(showFailuresOnlyLabel, AS_CHECK_BOX);
            setDescription(showFailuresOnlyLabel);
            setToolTipText(showFailuresOnlyLabel);
            setImageDescriptor(CeylonTestImageRegistry.getImageDescriptor(SHOW_FAILURES));
            
            IPreferenceStore preferenceStore = CeylonTestPlugin.getDefault().getPreferenceStore();
            setChecked(preferenceStore.getBoolean(PREF_SHOW_FAILURES_ONLY));
        }

        @Override
        public void run() {
            IPreferenceStore preferenceStore = CeylonTestPlugin.getDefault().getPreferenceStore();
            preferenceStore.setValue(PREF_SHOW_FAILURES_ONLY, isChecked());
            
            if( isChecked() ) {
                viewer.addFilter(showFailuresOnlyFilter);
            } else {
                viewer.removeFilter(showFailuresOnlyFilter);
            }
        }
        
    }
    
    private class ShowFailuresOnlyFilter extends ViewerFilter {
        
        @Override
        public boolean select(Viewer viewer, Object parentElement, Object element) {
            return select(((TestElement) element));
        }

        public boolean select(TestElement testElement) {
            State state = testElement.getState();
            if (state == State.FAILURE || state == State.ERROR) {
                return true;
            }
            return false;
        }
        
    }
    
    private class ShowTestsGroupedByPackagesAction extends Action {

        public ShowTestsGroupedByPackagesAction() {
            super(showTestsGroupedByPackages, AS_CHECK_BOX);
            setDescription(showTestsGroupedByPackages);
            setToolTipText(showTestsGroupedByPackages);

            IPreferenceStore preferenceStore = CeylonTestPlugin.getDefault().getPreferenceStore();
            setChecked(preferenceStore.getBoolean(PREF_SHOW_TESTS_GROUPED_BY_PACKAGES));
        }

        @Override
        public void run() {
            IPreferenceStore preferenceStore = CeylonTestPlugin.getDefault().getPreferenceStore();
            preferenceStore.setValue(PREF_SHOW_TESTS_GROUPED_BY_PACKAGES, isChecked());

            viewer.refresh();
        }

    }

    private class RelaunchAction extends Action {
        
        public RelaunchAction() {
            super(relaunchLabel);
            setDescription(relaunchLabel);
            setToolTipText(relaunchLabel);
            setImageDescriptor(CeylonTestImageRegistry.getImageDescriptor(RELAUNCH));
            setEnabled(false);
        }
        
        @Override
        public void run() {
            if( currentTestRun == null || currentTestRun.isRunning() )
                return;

            ILaunch launch = currentTestRun.getLaunch();
            if( launch == null )
                return;

            ILaunchConfiguration launchConfiguration = launch.getLaunchConfiguration();
            if( launchConfiguration == null )
                return;

            DebugUITools.launch(launchConfiguration, launch.getLaunchMode());
        }
        
    }
    
    private class StopAction extends Action {
        
        public StopAction() {
            super(stopLabel);
            setDescription(stopLabel);
            setToolTipText(stopLabel);
            setImageDescriptor(CeylonTestImageRegistry.getImageDescriptor(STOP));
            setEnabled(false);
        }
        
        @Override
        public void run() {
            if( currentTestRun == null || !currentTestRun.isRunning() )
                return;
            
            ILaunch launch = currentTestRun.getLaunch();
            if( launch == null || !launch.canTerminate() )
                return;
            
            try {
                launch.terminate();
            } catch (DebugException e) {
                CeylonTestPlugin.logError("", e);
            }
        }
        
    }

}