/********************************************************************************
 * Copyright (c) 2011-2017 Red Hat Inc. and/or its affiliates and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * http://www.eclipse.org/legal/epl-v10.html.
 *
 * SPDX-License-Identifier: EPL-1.0
 ********************************************************************************/
package org.eclipse.ceylon.ide.eclipse.code.search;

import static org.eclipse.ceylon.ide.eclipse.code.editor.Navigation.gotoLocation;
import static org.eclipse.ceylon.ide.eclipse.code.preferences.CeylonPreferenceInitializer.FULL_LOC_SEARCH_RESULTS;
import static org.eclipse.ceylon.ide.eclipse.code.search.CeylonSearchResultTreeContentProvider.LEVEL_FILE;
import static org.eclipse.ceylon.ide.eclipse.code.search.CeylonSearchResultTreeContentProvider.LEVEL_FOLDER;
import static org.eclipse.ceylon.ide.eclipse.code.search.CeylonSearchResultTreeContentProvider.LEVEL_MODULE;
import static org.eclipse.ceylon.ide.eclipse.code.search.CeylonSearchResultTreeContentProvider.LEVEL_PACKAGE;
import static org.eclipse.ceylon.ide.eclipse.code.search.CeylonSearchResultTreeContentProvider.LEVEL_PROJECT;
import static org.eclipse.ceylon.ide.eclipse.ui.CeylonPlugin.PLUGIN_ID;
import static org.eclipse.ceylon.ide.eclipse.ui.CeylonPlugin.getOutlineFont;
import static org.eclipse.ceylon.ide.eclipse.ui.CeylonResources.CEYLON_SEARCH;
import static org.eclipse.ceylon.ide.eclipse.ui.CeylonResources.CONFIG_LABELS;
import static org.eclipse.ceylon.ide.eclipse.ui.CeylonResources.FLAT_MODE;
import static org.eclipse.ceylon.ide.eclipse.ui.CeylonResources.FOLDER_MODE;
import static org.eclipse.ceylon.ide.eclipse.ui.CeylonResources.MODULE_MODE;
import static org.eclipse.ceylon.ide.eclipse.ui.CeylonResources.PACKAGE_MODE;
import static org.eclipse.ceylon.ide.eclipse.ui.CeylonResources.PROJECT_MODE;
import static org.eclipse.ceylon.ide.eclipse.ui.CeylonResources.TREE_MODE;
import static org.eclipse.ceylon.ide.eclipse.ui.CeylonResources.UNIT_MODE;
import static org.eclipse.jface.action.IAction.AS_CHECK_BOX;
import static org.eclipse.search.ui.IContextMenuConstants.GROUP_VIEWER_SETUP;
import static org.eclipse.ui.PlatformUI.getWorkbench;
import static org.eclipse.ui.dialogs.PreferencesUtil.createPreferenceDialogOn;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.internal.ui.search.JavaSearchEditorOpener;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.search.ui.text.AbstractTextSearchViewPage;
import org.eclipse.search.ui.text.Match;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.contexts.IContextActivation;
import org.eclipse.ui.contexts.IContextService;
import org.eclipse.ui.part.IPageSite;
import org.eclipse.ui.texteditor.ITextEditor;

import org.eclipse.ceylon.ide.eclipse.code.editor.CeylonEditor;
import org.eclipse.ceylon.ide.eclipse.code.preferences.CeylonFiltersPreferencePage;
import org.eclipse.ceylon.ide.eclipse.code.preferences.CeylonOutlinesPreferencePage;
import org.eclipse.ceylon.ide.eclipse.ui.CeylonPlugin;

public class CeylonSearchResultPage extends AbstractTextSearchViewPage {
    
    private CeylonStructuredContentProvider contentProvider;
    
    private IPropertyChangeListener propertyChangeListener;
    
    @Override
    public void init(IPageSite site) {
        super.init(site);
        propertyChangeListener = 
                new IPropertyChangeListener() {
            @Override
            public void propertyChange(
                    PropertyChangeEvent event) {
                StructuredViewer viewer = getViewer();
                viewer.getControl()
                    .setFont(getOutlineFont());
                viewer.refresh();
            }
        };
        CeylonPlugin.getPreferences()
            .addPropertyChangeListener(
                    propertyChangeListener);
        getWorkbench()
            .getThemeManager()
            .addPropertyChangeListener(
                    propertyChangeListener);
    }
    
    @Override
    public void dispose() {
        super.dispose();
        if (propertyChangeListener!=null) {
            CeylonPlugin.getPreferences()
                .removePropertyChangeListener(
                        propertyChangeListener);
            getWorkbench()
                .getThemeManager()
                .removePropertyChangeListener(
                        propertyChangeListener);
            propertyChangeListener = null;
        }
    }
    
    public CeylonSearchResultPage() {
        super(FLAG_LAYOUT_FLAT|FLAG_LAYOUT_TREE);
        setElementLimit(50);
        initGroupingActions();
    }
    
    @Override
    protected void clear() {
        if (contentProvider!=null) {
            contentProvider.clear();
        }
        //getViewer().refresh();
    }

    private void configureViewer(StructuredViewer viewer) {
        viewer.setContentProvider(contentProvider);
        viewer.setLabelProvider(new MatchCountingLabelProvider(this));
        viewer.setComparator(new CeylonViewerComparator());
        viewer.getControl().setFont(getOutlineFont());
        final IContextService contextService = 
                (IContextService) 
                    getWorkbench()
                        .getService(IContextService.class);
        viewer.getControl()
                .addFocusListener(new FocusListener() {
            private IContextActivation activation;
            @Override
            public void focusLost(FocusEvent e) {
                if (activation!=null) {
                    contextService.deactivateContext(activation);
                }
            }
            @Override
            public void focusGained(FocusEvent e) {
                activation = 
                        contextService.activateContext(
                                PLUGIN_ID + ".context");
            }
        });
    }

    @Override
    protected void configureTableViewer(TableViewer viewer) {
        contentProvider = 
                new CeylonSearchResultContentProvider(
                        viewer, this);
        configureViewer(viewer);
    }

    @Override
    protected void configureTreeViewer(TreeViewer viewer) {
        contentProvider = 
                new CeylonSearchResultTreeContentProvider(
                        viewer, this);
        configureViewer(viewer);
    }

    @Override
    protected void elementsChanged(Object[] elements) {
        if (contentProvider!=null) {
            contentProvider.elementsChanged(elements);
        }
//        getViewer().refresh();
    }
    
    @Override
    protected void showMatch(Match match, 
            int offset, int length, 
            boolean activate)
                    throws PartInitException {
        Object elem = match.getElement();
        if (elem instanceof CeylonElement) {
            CeylonElement ce = (CeylonElement) elem;
            open(ce, offset, length, activate);
        }
        else if (elem instanceof IJavaElement) {
            IJavaElement je = (IJavaElement) elem;
            open(je, match, offset, length, activate);
        }
    }

    private void open(IJavaElement element, Match match, 
            int offset, int length, 
            boolean activate) 
                    throws PartInitException {
        IFile file = (IFile) element.getResource();
        if (file==null) {
            //a binary archive
            IEditorPart editor = 
                    new JavaSearchEditorOpener()
                        .openMatch(match);
            if (editor instanceof ITextEditor) {
                ITextEditor textEditor = 
                        (ITextEditor) editor;
                textEditor.selectAndReveal(offset, length);
            }
        }
        else {
            //a source file in the workspace
            IWorkbenchPage page = getSite().getPage();
            if (offset>=0 && length!=0) {
                openAndSelect(page, file, 
                        offset, length, activate);
            } 
            else {
                open(page, file, activate);
            }
        }
    }

    private void open(CeylonElement element, 
            int offset, int length,
            boolean activate) 
                    throws PartInitException {
        IFile file = element.getFile();
        if (file==null) {
            String path = element.getVirtualFile().getPath();
            gotoLocation(new Path(path), offset, length);
        }
        else {
            IWorkbenchPage page = getSite().getPage();
            if (offset>=0 && length!=0) {
                openAndSelect(page, file, 
                        offset, length, activate);
            } 
            else {
                open(page, file, activate);
            }
        }
    }
    
    private static final String GROUP_CATEGORIES = 
            PLUGIN_ID + ".search.CeylonSearchResultPage.categories";
    private static final String GROUP_LAYOUT = 
            PLUGIN_ID + ".search.CeylonSearchResultPage.layout";
    private static final String GROUP_GROUPING = 
            PLUGIN_ID + ".search.CeylonSearchResultPage.grouping";
    private static final String KEY_GROUPING = 
            PLUGIN_ID + ".search.CeylonSearchResultPage.grouping";
    private static final String KEY_CATEGORIES = 
            PLUGIN_ID + ".search.CeylonSearchResultPage.categories";
    
    private GroupAction fGroupFileAction;
    private GroupAction fGroupPackageAction;
    private GroupAction fGroupModuleAction;
    private GroupAction fGroupFolderAction;
    private GroupAction fGroupProjectAction;
    
    private LayoutAction fLayoutFlatAction;
    private LayoutAction fLayoutTreeAction;
    
    private Action fCategoriesAction;
    
    private int fCurrentGrouping;
    private boolean fShowCategories;
    
    private void initGroupingActions() {
        fGroupProjectAction = 
                new GroupAction("Project", 
                        "Group by Project", 
                        PROJECT_MODE, LEVEL_PROJECT);
        fGroupFolderAction = 
                new GroupAction("Source Folder", 
                        "Group by Source Folder", 
                        FOLDER_MODE, LEVEL_FOLDER);
        fGroupModuleAction = 
                new GroupAction("Module", 
                        "Group by Module", 
                        MODULE_MODE, LEVEL_MODULE);
        fGroupPackageAction = 
                new GroupAction("Package", 
                        "Group by Package", 
                        PACKAGE_MODE, LEVEL_PACKAGE);
        fGroupFileAction = 
                new GroupAction("Source File", 
                        "Group by Source File", 
                        UNIT_MODE, LEVEL_FILE);
        
        fLayoutTreeAction = 
                new LayoutAction("Tree", 
                        "Show as Tree", 
                        TREE_MODE, FLAG_LAYOUT_TREE);
        fLayoutFlatAction = 
                new LayoutAction("Flat", 
                        "Show as List", 
                        FLAT_MODE, FLAG_LAYOUT_FLAT);
        
        fCategoriesAction = 
                new Action("Categories") {
            {
                setToolTipText("Group by Match Category");
                ImageDescriptor desc = 
                        CeylonPlugin.imageRegistry()
                            .getDescriptor(CEYLON_SEARCH);
                setImageDescriptor(desc);
                setChecked(fShowCategories);
            }
            @Override
            public void run() {
                setShowCategories();
                setChecked(fShowCategories);
            }
        };
    }
    
    private void updateGroupingActions() {
        fGroupProjectAction.setChecked(
                fCurrentGrouping == LEVEL_PROJECT);
        fGroupFolderAction.setChecked(
                fCurrentGrouping == LEVEL_FOLDER);
        fGroupModuleAction.setChecked(
                fCurrentGrouping == LEVEL_MODULE);
        fGroupPackageAction.setChecked(
                fCurrentGrouping == LEVEL_PACKAGE);
        fGroupFileAction.setChecked(
                fCurrentGrouping == LEVEL_FILE);
    }
    
    private void updateLayoutActions() {
        int layout = getLayout();
        fLayoutFlatAction.setChecked(
                layout==FLAG_LAYOUT_FLAT);
        fLayoutTreeAction.setChecked(
                layout==FLAG_LAYOUT_TREE);
    }
    
    @Override
    protected void fillToolbar(IToolBarManager tbm) {
        super.fillToolbar(tbm);
        
        tbm.appendToGroup(GROUP_VIEWER_SETUP, 
                new Separator(GROUP_LAYOUT));
        tbm.appendToGroup(GROUP_LAYOUT, fLayoutFlatAction);
        tbm.appendToGroup(GROUP_LAYOUT, fLayoutTreeAction);
        updateLayoutActions();
        
        if (getLayout() != FLAG_LAYOUT_FLAT) {
            
            tbm.appendToGroup(GROUP_VIEWER_SETUP, 
                    new Separator(GROUP_CATEGORIES));
            tbm.appendToGroup(GROUP_CATEGORIES, fCategoriesAction);
            fShowCategories = 
                    getSettings()
                        .getBoolean(KEY_CATEGORIES);
            contentProvider.setShowCategories(fShowCategories);
            updateCategoriesAction();
            
            tbm.appendToGroup(GROUP_VIEWER_SETUP, 
                    new Separator(GROUP_GROUPING));
            tbm.appendToGroup(GROUP_GROUPING, 
                    fGroupProjectAction);
            tbm.appendToGroup(GROUP_GROUPING, 
                    fGroupFolderAction);
            tbm.appendToGroup(GROUP_GROUPING, 
                    fGroupModuleAction);
            tbm.appendToGroup(GROUP_GROUPING, 
                    fGroupPackageAction);
            tbm.appendToGroup(GROUP_GROUPING, 
                    fGroupFileAction);
            try {
                fCurrentGrouping = 
                        getSettings()
                            .getInt(KEY_GROUPING);
            }
            catch (NumberFormatException nfe) {
                //missing key
                fCurrentGrouping = LEVEL_PROJECT;
            }
            contentProvider.setLevel(fCurrentGrouping);
            updateGroupingActions();
            
        }
        
        getSite()
            .getActionBars()
            .updateActionBars();
    }

    private void updateCategoriesAction() {
        fCategoriesAction.setChecked(fShowCategories);
    }
    
    @Override
    protected void fillContextMenu(IMenuManager mgr) {
        super.fillContextMenu(mgr);
        MenuManager submenu = new MenuManager("Find");
        submenu.setActionDefinitionId(CeylonEditor.FIND_MENU_ID);
        ISelection selection = getViewer().getSelection();
        submenu.add(new FindReferencesAction(this, selection));
        submenu.add(new FindAssignmentsAction(this, selection));
        submenu.add(new FindSubtypesAction(this, selection));
        submenu.add(new FindRefinementsAction(this, selection));
        mgr.add(submenu);
    }
    
    @Override
    public void makeContributions(IMenuManager menuManager,
            IToolBarManager toolBarManager, 
            IStatusLineManager statusLineManager) {
        final IPreferenceStore prefs = CeylonPlugin.getPreferences();
        final Action showLocAction = 
                new Action("Show Full Paths", AS_CHECK_BOX) {
            @Override
            public void run() {
                prefs.setValue(FULL_LOC_SEARCH_RESULTS, 
                        isChecked());
                getViewer().refresh();
            }
        };
        showLocAction.setChecked(prefs.getBoolean(
                FULL_LOC_SEARCH_RESULTS));
        //pref can be changed from ReferencesPopup!
        prefs.addPropertyChangeListener(new IPropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent event) {
                if (FULL_LOC_SEARCH_RESULTS.equals(event.getProperty())) {
                    showLocAction.setChecked(prefs.getBoolean(
                            FULL_LOC_SEARCH_RESULTS));
                }
            }
        });
        menuManager.add(showLocAction);
        super.makeContributions(menuManager, 
                toolBarManager, 
                statusLineManager);
        ImageDescriptor desc = 
                CeylonPlugin.imageRegistry()
                    .getDescriptor(CONFIG_LABELS);
        Action configureAction = 
                new Action("Configure Labels...", desc) {
            @Override
            public void run() {
                Shell shell = getSite().getShell();
                createPreferenceDialogOn(shell, 
                        CeylonOutlinesPreferencePage.ID,
                        new String[] {
                                CeylonOutlinesPreferencePage.ID,
                                CeylonPlugin.COLORS_AND_FONTS_PAGE_ID,
                                CeylonFiltersPreferencePage.ID
                        }, 
                        null).open();
            }
        };
        menuManager.add(configureAction);
    }
    
    private class GroupAction extends Action {
        private int fGrouping;

        public GroupAction(String label, String tooltip, 
                String imageKey, int grouping) {
            super(label);
            setToolTipText(tooltip);
            ImageDescriptor desc = 
                    CeylonPlugin.imageRegistry()
                        .getDescriptor(imageKey);
            setImageDescriptor(desc);
            fGrouping = grouping;
        }
        
        @Override
        public boolean isEnabled() {
            return getLayout()!= FLAG_LAYOUT_FLAT;
        }

        @Override
        public void run() {
            setGrouping(fGrouping);
        }

    }

    private class LayoutAction extends Action {
        private int layout;

        public LayoutAction(String label, String tooltip, 
                String imageKey, int layout) {
            super(label);
            setToolTipText(tooltip);
            ImageDescriptor desc = 
                    CeylonPlugin.imageRegistry()
                        .getDescriptor(imageKey);
            setImageDescriptor(desc);
            this.layout = layout;
        }

        @Override
        public void run() {
            setLayout(layout);
            setChecked(getLayout()==layout);
        }
    }

    void setGrouping(int grouping) {
        fCurrentGrouping = grouping;
        contentProvider.setLevel(grouping);
        updateGroupingActions();
        getSettings().put(KEY_GROUPING, fCurrentGrouping);
        getViewPart().updateLabel();
    }
    
    void setShowCategories() {
        fShowCategories = !fShowCategories;
        contentProvider.setShowCategories(fShowCategories);
        updateCategoriesAction();
        getSettings().put(KEY_CATEGORIES, fShowCategories);
    }
}
