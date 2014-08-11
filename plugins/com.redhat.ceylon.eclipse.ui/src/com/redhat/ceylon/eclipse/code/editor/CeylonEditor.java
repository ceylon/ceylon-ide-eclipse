/*******************************************************************************
* Copyright (c) 2007 IBM Corporation.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*    Robert Fuhrer (rfuhrer@watson.ibm.com) - initial API and implementation
*******************************************************************************/

package com.redhat.ceylon.eclipse.code.editor;

import static com.redhat.ceylon.eclipse.code.editor.CeylonSourceViewerConfiguration.CLEAN_IMPORTS;
import static com.redhat.ceylon.eclipse.code.editor.CeylonSourceViewerConfiguration.FORMAT;
import static com.redhat.ceylon.eclipse.code.editor.CeylonSourceViewerConfiguration.NORMALIZE_NL;
import static com.redhat.ceylon.eclipse.code.editor.CeylonSourceViewerConfiguration.NORMALIZE_WS;
import static com.redhat.ceylon.eclipse.code.editor.CeylonSourceViewerConfiguration.STRIP_TRAILING_WS;
import static com.redhat.ceylon.eclipse.code.editor.CeylonSourceViewerConfiguration.configCompletionPopup;
import static com.redhat.ceylon.eclipse.code.editor.EditorActionIds.ADD_BLOCK_COMMENT;
import static com.redhat.ceylon.eclipse.code.editor.EditorActionIds.CORRECT_INDENTATION;
import static com.redhat.ceylon.eclipse.code.editor.EditorActionIds.GOTO_MATCHING_FENCE;
import static com.redhat.ceylon.eclipse.code.editor.EditorActionIds.REMOVE_BLOCK_COMMENT;
import static com.redhat.ceylon.eclipse.code.editor.EditorActionIds.RESTORE_PREVIOUS;
import static com.redhat.ceylon.eclipse.code.editor.EditorActionIds.SELECT_ENCLOSING;
import static com.redhat.ceylon.eclipse.code.editor.EditorActionIds.SHOW_OUTLINE;
import static com.redhat.ceylon.eclipse.code.editor.EditorActionIds.TOGGLE_COMMENT;
import static com.redhat.ceylon.eclipse.code.editor.EditorInputUtils.getFile;
import static com.redhat.ceylon.eclipse.code.editor.EditorInputUtils.getPath;
import static com.redhat.ceylon.eclipse.code.editor.SourceArchiveDocumentProvider.isSrcArchive;
import static com.redhat.ceylon.eclipse.code.outline.CeylonLabelProvider.getImageForFile;
import static com.redhat.ceylon.eclipse.ui.CeylonPlugin.PLUGIN_ID;
import static com.redhat.ceylon.eclipse.util.Indents.getDefaultIndent;
import static com.redhat.ceylon.eclipse.util.Indents.getDefaultLineDelimiter;
import static com.redhat.ceylon.eclipse.util.Nodes.findNode;
import static java.util.ResourceBundle.getBundle;
import static org.eclipse.core.resources.IncrementalProjectBuilder.CLEAN_BUILD;
import static org.eclipse.core.resources.ResourcesPlugin.getWorkspace;
import static org.eclipse.jdt.ui.PreferenceConstants.EDITOR_FOLDING_ENABLED;
import static org.eclipse.jface.text.DocumentRewriteSessionType.SEQUENTIAL;
import static org.eclipse.ui.PlatformUI.getWorkbench;
import static org.eclipse.ui.texteditor.AbstractDecoratedTextEditorPreferenceConstants.EDITOR_SPACES_FOR_TABS;
import static org.eclipse.ui.texteditor.ITextEditorActionConstants.GROUP_RULERS;
import static org.eclipse.ui.texteditor.ITextEditorActionDefinitionIds.CONTENT_ASSIST_PROPOSALS;
import static org.eclipse.ui.texteditor.ITextEditorActionDefinitionIds.DELETE_NEXT_WORD;
import static org.eclipse.ui.texteditor.ITextEditorActionDefinitionIds.DELETE_PREVIOUS_WORD;
import static org.eclipse.ui.texteditor.ITextEditorActionDefinitionIds.SELECT_WORD_NEXT;
import static org.eclipse.ui.texteditor.ITextEditorActionDefinitionIds.SELECT_WORD_PREVIOUS;
import static org.eclipse.ui.texteditor.ITextEditorActionDefinitionIds.WORD_NEXT;
import static org.eclipse.ui.texteditor.ITextEditorActionDefinitionIds.WORD_PREVIOUS;

import java.lang.reflect.Method;
import java.text.BreakIterator;
import java.text.CharacterIterator;
import java.util.Iterator;
import java.util.List;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.ui.actions.IToggleBreakpointsTarget;
import org.eclipse.debug.ui.actions.ToggleBreakpointAction;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.commands.ActionHandler;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.DocumentRewriteSession;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension4;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.link.LinkedModeModel;
import org.eclipse.jface.text.link.LinkedPosition;
import org.eclipse.jface.text.source.CompositeRuler;
import org.eclipse.jface.text.source.DefaultCharacterPairMatcher;
import org.eclipse.jface.text.source.ICharacterPairMatcher;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.IVerticalRuler;
import org.eclipse.jface.text.source.IVerticalRulerInfo;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.text.source.projection.ProjectionSupport;
import org.eclipse.jface.text.source.projection.ProjectionViewer;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CaretEvent;
import org.eclipse.swt.custom.CaretListener;
import org.eclipse.swt.custom.ST;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.custom.StyledTextContent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IPropertyListener;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.editors.text.TextEditor;
import org.eclipse.ui.handlers.IHandlerActivation;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.texteditor.AnnotationPreference;
import org.eclipse.ui.texteditor.ContentAssistAction;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.IEditorStatusLine;
import org.eclipse.ui.texteditor.ITextEditorActionConstants;
import org.eclipse.ui.texteditor.IUpdate;
import org.eclipse.ui.texteditor.MarkerAnnotationPreferences;
import org.eclipse.ui.texteditor.SourceViewerDecorationSupport;
import org.eclipse.ui.texteditor.TextNavigationAction;
import org.eclipse.ui.texteditor.TextOperationAction;
import org.eclipse.ui.themes.ITheme;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.eclipse.code.imports.CleanImportsHandler;
import com.redhat.ceylon.eclipse.code.outline.CeylonOutlinePage;
import com.redhat.ceylon.eclipse.code.outline.NavigateMenuItems;
import com.redhat.ceylon.eclipse.code.parse.CeylonParseController;
import com.redhat.ceylon.eclipse.code.parse.CeylonParserScheduler;
import com.redhat.ceylon.eclipse.code.parse.TreeLifecycleListener;
import com.redhat.ceylon.eclipse.code.preferences.CeylonCompletionPreferencesPage;
import com.redhat.ceylon.eclipse.code.preferences.CeylonEditorPreferencesPage;
import com.redhat.ceylon.eclipse.code.preferences.CeylonSavePreferencesPage;
import com.redhat.ceylon.eclipse.code.refactor.RefactorMenuItems;
import com.redhat.ceylon.eclipse.code.search.FindMenuItems;
import com.redhat.ceylon.eclipse.core.external.ExternalSourceArchiveManager;
import com.redhat.ceylon.eclipse.util.EditorUtil;

/**
 * An editor for Ceylon source code.
 * 
 * @author Gavin King
 * @author Chris Laffra
 * @author Robert M. Fuhrer
 */
public class CeylonEditor extends TextEditor {
    
    private static final Pattern TRAILING_WS = 
            Pattern.compile("[ \\t]+$", Pattern.MULTILINE);

    public static final String MESSAGE_BUNDLE = 
            "com.redhat.ceylon.eclipse.code.editor.EditorActionMessages";

    private static final int REPARSE_SCHEDULE_DELAY = 200;

    //preference keys
    public final static String MATCHING_BRACKET = "matchingBrackets";
    public final static String MATCHING_BRACKETS_COLOR = "matchingBracketsColor";    
    public final static String SELECTED_BRACKET = "highlightBracketAtCaretLocation";
    public final static String ENCLOSING_BRACKETS = "enclosingBrackets";
    public final static String SUB_WORD_NAVIGATION = "subWordNavigation";
    public final static String AUTO_FOLD_IMPORTS = "autoFoldImports";
    public final static String AUTO_FOLD_COMMENTS = "autoFoldComments";
    
    private CeylonParserScheduler parserScheduler;
    private ProblemMarkerManager problemMarkerManager;
    private ICharacterPairMatcher bracketMatcher;
    private ToggleBreakpointAction toggleBreakpointAction;
    private IAction enableDisableBreakpointAction;
    private FoldingActionGroup foldingActionGroup;
    private SourceArchiveDocumentProvider sourceArchiveDocumentProvider;
    private ToggleBreakpointAdapter toggleBreakpointTarget;
    private CeylonOutlinePage outlinePage;
    private boolean backgroundParsingPaused;
    private CeylonParseController parseController;
    private ProjectionSupport projectionSupport;
    private LinkedModeModel linkedMode;
    private Object linkedModeOwner;
    
    private MarkerAnnotationUpdater markerAnnotationUpdater = 
            new MarkerAnnotationUpdater(this);
    private ProjectionAnnotationManager projectionAnnotationManager = 
            new ProjectionAnnotationManager(this);
    private AnnotationCreator annotationCreator = 
            new AnnotationCreator(this);
    
    ToggleFoldingRunner fFoldingRunner;
    
    public CeylonEditor() {
        setSourceViewerConfiguration(createSourceViewerConfiguration());
        setRangeIndicator(new CeylonRangeIndicator());
        configureInsertMode(SMART_INSERT, true);
        setInsertMode(SMART_INSERT);
        problemMarkerManager= new ProblemMarkerManager();
        parseController = new CeylonParseController();
    }
    
    static String[][] getFences() {
        return new String[][] { { "(", ")" }, { "[", "]" }, { "{", "}" } };
    }
    
    public synchronized void pauseBackgroundParsing() {
        backgroundParsingPaused = true;
    }
    
    public synchronized void unpauseBackgroundParsing() {
        backgroundParsingPaused = false;
    }
    
    public synchronized boolean isBackgroundParsingPaused() {
        return backgroundParsingPaused;
    }
    
    public boolean isInLinkedMode() {
        return linkedMode!=null;
    }
    
    public void setLinkedMode(LinkedModeModel linkedMode,
            Object linkedModeOwner) {
        this.linkedMode = linkedMode;
        this.linkedModeOwner = linkedModeOwner;
    }
    
    public void clearLinkedMode() {
        this.linkedMode = null;
        this.linkedModeOwner = null;
    }
    
    public LinkedModeModel getLinkedMode() {
        return linkedMode;
    }
    
    public Object getLinkedModeOwner() {
        return linkedModeOwner;
    }
    
    /**
     * Sub-classes may override this method to extend the behavior provided by IMP's
     * standard StructuredSourceViewerConfiguration.
     * @return the StructuredSourceViewerConfiguration to use with this editor
     */
    protected CeylonSourceViewerConfiguration createSourceViewerConfiguration() {
        return new CeylonSourceViewerConfiguration(this);
    }

    public IPreferenceStore getPrefStore() {
        return super.getPreferenceStore();
    }
    
    public Object getAdapter(@SuppressWarnings("rawtypes") Class required) {
        if (IContentOutlinePage.class.equals(required)) {
            return getOutlinePage();
        }
        if (IToggleBreakpointsTarget.class.equals(required)) {
            return getToggleBreakpointAdapter();
        }
        return super.getAdapter(required);
    }

    public Object getToggleBreakpointAdapter() {
        if (toggleBreakpointTarget == null) {
            toggleBreakpointTarget = new ToggleBreakpointAdapter();
        }
        return toggleBreakpointTarget;
    }

    public CeylonOutlinePage getOutlinePage() {
        if (outlinePage == null) {
            outlinePage = new CeylonOutlinePage(getParseController(),
                    getCeylonSourceViewer());
            parserScheduler.addModelListener(outlinePage);
         }
         return outlinePage;
    }
    
    @Override
    protected void editorContextMenuAboutToShow(IMenuManager menu) {
        super.editorContextMenuAboutToShow(menu);
        menu.remove(ITextEditorActionConstants.SAVE);
        menu.remove(ITextEditorActionConstants.REVERT);
        menu.remove(ITextEditorActionConstants.SHIFT_LEFT);
        menu.remove(ITextEditorActionConstants.SHIFT_RIGHT);
        menu.remove(ITextEditorActionConstants.QUICK_ASSIST);    
        menu.remove(ITextEditorActionConstants.CUT);
        menu.remove(ITextEditorActionConstants.COPY);
        menu.remove(ITextEditorActionConstants.PASTE);
        menu.remove(ITextEditorActionConstants.UNDO);
    }
    
    protected void createActions() {
        super.createActions();

        final ResourceBundle bundle= getBundle(MESSAGE_BUNDLE);
        
        Action action= new ContentAssistAction(bundle, "ContentAssistProposal.", this);
        action.setActionDefinitionId(CONTENT_ASSIST_PROPOSALS);
        setAction("ContentAssistProposal", action);
        markAsStateDependentAction("ContentAssistProposal", true);

        IVerticalRuler verticalRuler = getVerticalRuler();
        if (verticalRuler!=null) {
            toggleBreakpointAction= new ToggleBreakpointAction(this, 
                    getDocumentProvider().getDocument(getEditorInput()), 
                    verticalRuler);
            setAction("ToggleBreakpoint", action);

            enableDisableBreakpointAction= new RulerEnableDisableBreakpointAction(this, 
                    verticalRuler);
            setAction("ToggleBreakpoint", action);
        }

//        action= new TextOperationAction(bundle, "Format.", this, 
//                CeylonSourceViewer.FORMAT);
//        action.setActionDefinitionId(FORMAT);
//        setAction("Format", action);
//        markAsStateDependentAction("Format", true);
//        markAsSelectionDependentAction("Format", true);
        //getWorkbench().getHelpSystem().setHelp(action, IJavaHelpContextIds.FORMAT_ACTION);

        action= new TextOperationAction(bundle, "AddBlockComment.", this,
                CeylonSourceViewer.ADD_BLOCK_COMMENT);
        action.setActionDefinitionId(ADD_BLOCK_COMMENT);
        setAction(ADD_BLOCK_COMMENT, action); 
        markAsStateDependentAction(ADD_BLOCK_COMMENT, true); 
        markAsSelectionDependentAction(ADD_BLOCK_COMMENT, true); 
        //PlatformUI.getWorkbench().getHelpSystem().setHelp(action, IJavaHelpContextIds.ADD_BLOCK_COMMENT_ACTION);

        action= new TextOperationAction(bundle, "RemoveBlockComment.", this,
                CeylonSourceViewer.REMOVE_BLOCK_COMMENT);
        action.setActionDefinitionId(REMOVE_BLOCK_COMMENT);
        setAction(REMOVE_BLOCK_COMMENT, action); 
        markAsStateDependentAction(REMOVE_BLOCK_COMMENT, true); 
        markAsSelectionDependentAction(REMOVE_BLOCK_COMMENT, true); 
        //PlatformUI.getWorkbench().getHelpSystem().setHelp(action, IJavaHelpContextIds.REMOVE_BLOCK_COMMENT_ACTION);
        
        action= new TextOperationAction(bundle, "ShowOutline.", this, 
                CeylonSourceViewer.SHOW_OUTLINE, true /* runsOnReadOnly */);
        action.setActionDefinitionId(SHOW_OUTLINE);
        setAction(SHOW_OUTLINE, action);
        //getWorkbench().getHelpSystem().setHelp(action, IJavaHelpContextIds.SHOW_OUTLINE_ACTION);

        action= new TextOperationAction(bundle, "ToggleComment.", this, 
                CeylonSourceViewer.TOGGLE_COMMENT);
        action.setActionDefinitionId(TOGGLE_COMMENT);
        setAction(TOGGLE_COMMENT, action);
        //getWorkbench().getHelpSystem().setHelp(action, IJavaHelpContextIds.TOGGLE_COMMENT_ACTION);

        action= new TextOperationAction(bundle, "CorrectIndentation.", this, 
                CeylonSourceViewer.CORRECT_INDENTATION);
        action.setActionDefinitionId(CORRECT_INDENTATION);
        setAction(CORRECT_INDENTATION, action);

        action= new GotoMatchingFenceAction(this);
        action.setActionDefinitionId(GOTO_MATCHING_FENCE);
        setAction(GOTO_MATCHING_FENCE, action);

//        action= new GotoPreviousTargetAction(this);
//        action.setActionDefinitionId(GOTO_PREVIOUS_TARGET);
//        setAction(GOTO_PREVIOUS_TARGET, action);
//
//        action= new GotoNextTargetAction(this);
//        action.setActionDefinitionId(GOTO_NEXT_TARGET);
//        setAction(GOTO_NEXT_TARGET, action);

        action= new SelectEnclosingAction(this);
        action.setActionDefinitionId(SELECT_ENCLOSING);
        setAction(SELECT_ENCLOSING, action);

        action= new RestorePreviousSelectionAction(this);
        action.setActionDefinitionId(RESTORE_PREVIOUS);
        setAction(RESTORE_PREVIOUS, action);

        action= new TextOperationAction(bundle, "ShowHierarchy.", this, 
                CeylonSourceViewer.SHOW_HIERARCHY, true);
        action.setActionDefinitionId(EditorActionIds.SHOW_CEYLON_HIERARCHY);
        setAction(EditorActionIds.SHOW_CEYLON_HIERARCHY, action);

        action= new TextOperationAction(bundle, "ShowInHierarchyView.", this, 
                CeylonSourceViewer.SHOW_IN_HIERARCHY_VIEW, true);
        action.setActionDefinitionId(EditorActionIds.SHOW_IN_CEYLON_HIERARCHY_VIEW);
        setAction(EditorActionIds.SHOW_IN_CEYLON_HIERARCHY_VIEW, action);

        action= new TextOperationAction(bundle, "ShowReferences.", this, 
                CeylonSourceViewer.SHOW_REFERENCES, true);
        action.setActionDefinitionId(EditorActionIds.SHOW_CEYLON_REFERENCES);
        setAction(EditorActionIds.SHOW_CEYLON_REFERENCES, action);
        
        action= new TextOperationAction(bundle, "ShowCode.", this, 
                CeylonSourceViewer.SHOW_DEFINITION, true);
        action.setActionDefinitionId(EditorActionIds.SHOW_CEYLON_CODE);
        setAction(EditorActionIds.SHOW_CEYLON_CODE, action);
        
        action= new TerminateStatementAction(this);
        action.setActionDefinitionId(EditorActionIds.TERMINATE_STATEMENT);
        setAction(EditorActionIds.TERMINATE_STATEMENT, action);

        action= new FormatBlockAction(this);
        action.setActionDefinitionId(EditorActionIds.FORMAT_BLOCK);
        setAction(EditorActionIds.FORMAT_BLOCK, action);

        action= new FormatAction(this);
        action.setActionDefinitionId(EditorActionIds.FORMAT);
        setAction(EditorActionIds.FORMAT, action);

        foldingActionGroup= new FoldingActionGroup(this, this.getSourceViewer());
        
//        getAction(ITextEditorActionConstants.SHIFT_LEFT)
//            .setImageDescriptor(CeylonPlugin.getInstance().getImageRegistry()
//                    .getDescriptor(CeylonResources.SHIFT_LEFT));
//        getAction(ITextEditorActionConstants.SHIFT_RIGHT)
//            .setImageDescriptor(CeylonPlugin.getInstance().getImageRegistry()
//                .getDescriptor(CeylonResources.SHIFT_RIGHT));
        
//        IAction qaa=getAction(ITextEditorActionConstants.QUICK_ASSIST);
//        qaa.setImageDescriptor(CeylonPlugin.getInstance().getImageRegistry()
//                .getDescriptor(CeylonResources.QUICK_ASSIST));
//        qaa.setText("Quick Fix/Assist");
        
        installQuickAccessAction();
        
    }
    
    @Override
    protected String[] collectContextMenuPreferencePages() {
        String[] pages = super.collectContextMenuPreferencePages();
        String[] result = new String[pages.length+3];
        System.arraycopy(pages, 0, result, 3, pages.length);
        result[0] = CeylonEditorPreferencesPage.ID;
        result[1] = CeylonCompletionPreferencesPage.ID;
        result[2] = CeylonSavePreferencesPage.ID;
        return result;
    }
    
    @Override
    protected void createNavigationActions() {
        super.createNavigationActions();
        
        final StyledText textWidget= getSourceViewer().getTextWidget();
        
        /*IAction action= new SmartLineStartAction(textWidget, false);
        action.setActionDefinitionId(ITextEditorActionDefinitionIds.LINE_START);
        editor.setAction(ITextEditorActionDefinitionIds.LINE_START, action);

        action= new SmartLineStartAction(textWidget, true);
        action.setActionDefinitionId(ITextEditorActionDefinitionIds.SELECT_LINE_START);
        editor.setAction(ITextEditorActionDefinitionIds.SELECT_LINE_START, action);*/
        
        getPreferenceStore().setDefault(SUB_WORD_NAVIGATION, true);
        
        IAction action = new NavigatePreviousSubWordAction();
        action.setActionDefinitionId(WORD_PREVIOUS);
        setAction(WORD_PREVIOUS, action);
        textWidget.setKeyBinding(SWT.CTRL | SWT.ARROW_LEFT, SWT.NULL);
        
        action = new NavigateNextSubWordAction();
        action.setActionDefinitionId(WORD_NEXT);
        setAction(WORD_NEXT, action);
        textWidget.setKeyBinding(SWT.CTRL | SWT.ARROW_RIGHT, SWT.NULL);
        
        action = new SelectPreviousSubWordAction();
        action.setActionDefinitionId(SELECT_WORD_PREVIOUS);
        setAction(SELECT_WORD_PREVIOUS, action);
        textWidget.setKeyBinding(SWT.CTRL | SWT.SHIFT | SWT.ARROW_LEFT, SWT.NULL);
        
        action = new SelectNextSubWordAction();
        action.setActionDefinitionId(SELECT_WORD_NEXT);
        setAction(SELECT_WORD_NEXT, action);
        textWidget.setKeyBinding(SWT.CTRL | SWT.SHIFT | SWT.ARROW_RIGHT, SWT.NULL);
        
        action = new DeletePreviousSubWordAction();
        action.setActionDefinitionId(DELETE_PREVIOUS_WORD);
        setAction(DELETE_PREVIOUS_WORD, action);
        textWidget.setKeyBinding(SWT.CTRL | SWT.BS, SWT.NULL);
        markAsStateDependentAction(DELETE_PREVIOUS_WORD, true);
        
        action = new DeleteNextSubWordAction();
        action.setActionDefinitionId(DELETE_NEXT_WORD);
        setAction(DELETE_NEXT_WORD, action);
        textWidget.setKeyBinding(SWT.CTRL | SWT.DEL, SWT.NULL);
        markAsStateDependentAction(DELETE_NEXT_WORD, true);
    }
    
    /**
     * Text navigation action to navigate to the next sub-word.
     *
     * @since 3.0
     */
    protected abstract class NextSubWordAction extends TextNavigationAction {

        protected CeylonWordIterator fIterator= new CeylonWordIterator();

        /**
         * Creates a new next sub-word action.
         *
         * @param code Action code for the default operation. Must be an action code from @see org.eclipse.swt.custom.ST.
         */
        protected NextSubWordAction(int code) {
            super(getSourceViewer().getTextWidget(), code);
        }

        @Override
        public void run() {
            // Check whether we are in a java code partition and the preference is enabled
            final IPreferenceStore store= getPreferenceStore();
            if (!store.getBoolean(SUB_WORD_NAVIGATION)) {
                super.run();
                return;
            }

            final ISourceViewer viewer= getSourceViewer();
            final IDocument document= viewer.getDocument();
            try {
                fIterator.setText((CharacterIterator) new DocumentCharacterIterator(document));
                int position= widgetOffset2ModelOffset(viewer, viewer.getTextWidget().getCaretOffset());
                if (position == -1)
                    return;

                int next= findNextPosition(position);
                if (isBlockSelectionModeEnabled() && 
                        document.getLineOfOffset(next) != document.getLineOfOffset(position)) {
                    super.run(); // may navigate into virtual white space
                } else if (next != BreakIterator.DONE) {
                    setCaretPosition(next);
                    getTextWidget().showSelection();
                    fireSelectionChanged();
                }
            } catch (BadLocationException x) {
                // ignore
            }
        }

        /**
         * Finds the next position after the given position.
         *
         * @param position the current position
         * @return the next position
         */
        protected int findNextPosition(int position) {
            ISourceViewer viewer= getSourceViewer();
            int widget= -1;
            int next= position;
            while (next != BreakIterator.DONE && widget == -1) { // XXX: optimize
                next= fIterator.following(next);
                if (next != BreakIterator.DONE)
                    widget= modelOffset2WidgetOffset(viewer, next);
            }

            IDocument document= viewer.getDocument();
            LinkedModeModel model= LinkedModeModel.getModel(document, position);
            if (model != null && next != BreakIterator.DONE) {
                LinkedPosition linkedPosition= 
                        model.findPosition(new LinkedPosition(document, position, 0));
                if (linkedPosition != null) {
                    int linkedPositionEnd= 
                            linkedPosition.getOffset() + linkedPosition.getLength();
                    if (position != linkedPositionEnd && linkedPositionEnd < next)
                        next= linkedPositionEnd;
                } else {
                    LinkedPosition nextLinkedPosition= 
                            model.findPosition(new LinkedPosition(document, next, 0));
                    if (nextLinkedPosition != null) {
                        int nextLinkedPositionOffset= nextLinkedPosition.getOffset();
                        if (position != nextLinkedPositionOffset && nextLinkedPositionOffset < next)
                            next= nextLinkedPositionOffset;
                    }
                }
            }

            return next;
        }

        /**
         * Sets the caret position to the sub-word boundary given with <code>position</code>.
         *
         * @param position Position where the action should move the caret
         */
        protected abstract void setCaretPosition(int position);
    }

    /**
     * Text navigation action to navigate to the next sub-word.
     *
     * @since 3.0
     */
    protected class NavigateNextSubWordAction extends NextSubWordAction {

        /**
         * Creates a new navigate next sub-word action.
         */
        public NavigateNextSubWordAction() {
            super(ST.WORD_NEXT);
        }

        @Override
        protected void setCaretPosition(final int position) {
            getTextWidget().setCaretOffset(modelOffset2WidgetOffset(getSourceViewer(), position));
        }
    }

    /**
     * Text operation action to delete the next sub-word.
     *
     * @since 3.0
     */
    protected class DeleteNextSubWordAction extends NextSubWordAction implements IUpdate {

        /**
         * Creates a new delete next sub-word action.
         */
        public DeleteNextSubWordAction() {
            super(ST.DELETE_WORD_NEXT);
        }

        @Override
        protected void setCaretPosition(final int position) {
            if (!validateEditorInputState())
                return;

            final ISourceViewer viewer= getSourceViewer();
            StyledText text= viewer.getTextWidget();
            Point widgetSelection= text.getSelection();
            if (isBlockSelectionModeEnabled() && widgetSelection.y != widgetSelection.x) {
                final int caret= text.getCaretOffset();
                final int offset= modelOffset2WidgetOffset(viewer, position);

                if (caret == widgetSelection.x)
                    text.setSelectionRange(widgetSelection.y, offset - widgetSelection.y);
                else
                    text.setSelectionRange(widgetSelection.x, offset - widgetSelection.x);
                text.invokeAction(ST.DELETE_NEXT);
            } else {
                Point selection= viewer.getSelectedRange();
                final int caret, length;
                if (selection.y != 0) {
                    caret= selection.x;
                    length= selection.y;
                } else {
                    caret= widgetOffset2ModelOffset(viewer, text.getCaretOffset());
                    length= position - caret;
                }

                try {
                    viewer.getDocument().replace(caret, length, ""); //$NON-NLS-1$
                } catch (BadLocationException exception) {
                    // Should not happen
                }
            }
        }

        public void update() {
            setEnabled(isEditorInputModifiable());
        }
    }

    /**
     * Text operation action to select the next sub-word.
     *
     * @since 3.0
     */
    protected class SelectNextSubWordAction extends NextSubWordAction {

        /**
         * Creates a new select next sub-word action.
         */
        public SelectNextSubWordAction() {
            super(ST.SELECT_WORD_NEXT);
        }

        @Override
        protected void setCaretPosition(final int position) {
            final ISourceViewer viewer= getSourceViewer();

            final StyledText text= viewer.getTextWidget();
            if (text != null && !text.isDisposed()) {

                final Point selection= text.getSelection();
                final int caret= text.getCaretOffset();
                final int offset= modelOffset2WidgetOffset(viewer, position);

                if (caret == selection.x)
                    text.setSelectionRange(selection.y, offset - selection.y);
                else
                    text.setSelectionRange(selection.x, offset - selection.x);
            }
        }
    }

    /**
     * Text navigation action to navigate to the previous sub-word.
     *
     * @since 3.0
     */
    protected abstract class PreviousSubWordAction extends TextNavigationAction {

        protected CeylonWordIterator fIterator= new CeylonWordIterator();

        /**
         * Creates a new previous sub-word action.
         *
         * @param code Action code for the default operation. Must be an action code from @see org.eclipse.swt.custom.ST.
         */
        protected PreviousSubWordAction(final int code) {
            super(getSourceViewer().getTextWidget(), code);
        }

        @Override
        public void run() {
            // Check whether we are in a java code partition and the preference is enabled
            final IPreferenceStore store= getPreferenceStore();
            if (!store.getBoolean(SUB_WORD_NAVIGATION)) {
                super.run();
                return;
            }

            final ISourceViewer viewer= getSourceViewer();
            final IDocument document= viewer.getDocument();
            try {
                fIterator.setText((CharacterIterator) new DocumentCharacterIterator(document));
                int position= widgetOffset2ModelOffset(viewer, viewer.getTextWidget().getCaretOffset());
                if (position == -1)
                    return;

                int previous= findPreviousPosition(position);
                if (isBlockSelectionModeEnabled() && 
                        document.getLineOfOffset(previous)!=document.getLineOfOffset(position)) {
                    super.run(); // may navigate into virtual white space
                } else if (previous != BreakIterator.DONE) {
                    setCaretPosition(previous);
                    getTextWidget().showSelection();
                    fireSelectionChanged();
                }
            } catch (BadLocationException x) {
                // ignore - getLineOfOffset failed
            }

        }

        /**
         * Finds the previous position before the given position.
         *
         * @param position the current position
         * @return the previous position
         */
        protected int findPreviousPosition(int position) {
            ISourceViewer viewer= getSourceViewer();
            int widget= -1;
            int previous= position;
            while (previous != BreakIterator.DONE && widget == -1) { // XXX: optimize
                previous= fIterator.preceding(previous);
                if (previous != BreakIterator.DONE)
                    widget= modelOffset2WidgetOffset(viewer, previous);
            }

            IDocument document= viewer.getDocument();
            LinkedModeModel model= LinkedModeModel.getModel(document, position);
            if (model != null && previous != BreakIterator.DONE) {
                LinkedPosition linkedPosition= 
                        model.findPosition(new LinkedPosition(document, position, 0));
                if (linkedPosition != null) {
                    int linkedPositionOffset= linkedPosition.getOffset();
                    if (position != linkedPositionOffset && previous < linkedPositionOffset)
                        previous= linkedPositionOffset;
                } else {
                    LinkedPosition previousLinkedPosition= 
                            model.findPosition(new LinkedPosition(document, previous, 0));
                    if (previousLinkedPosition != null) {
                        int previousLinkedPositionEnd= 
                                previousLinkedPosition.getOffset() + previousLinkedPosition.getLength();
                        if (position != previousLinkedPositionEnd && previous < previousLinkedPositionEnd)
                            previous= previousLinkedPositionEnd;
                    }
                }
            }

            return previous;
        }

        /**
         * Sets the caret position to the sub-word boundary given with <code>position</code>.
         *
         * @param position Position where the action should move the caret
         */
        protected abstract void setCaretPosition(int position);
    }

    /**
     * Text navigation action to navigate to the previous sub-word.
     *
     * @since 3.0
     */
    protected class NavigatePreviousSubWordAction extends PreviousSubWordAction {

        /**
         * Creates a new navigate previous sub-word action.
         */
        public NavigatePreviousSubWordAction() {
            super(ST.WORD_PREVIOUS);
        }

        @Override
        protected void setCaretPosition(final int position) {
            getTextWidget().setCaretOffset(modelOffset2WidgetOffset(getSourceViewer(), position));
        }
    }

    /**
     * Text operation action to delete the previous sub-word.
     *
     * @since 3.0
     */
    protected class DeletePreviousSubWordAction extends PreviousSubWordAction implements IUpdate {

        /**
         * Creates a new delete previous sub-word action.
         */
        public DeletePreviousSubWordAction() {
            super(ST.DELETE_WORD_PREVIOUS);
        }

        @Override
        protected void setCaretPosition(int position) {
            if (!validateEditorInputState())
                return;

            final int length;
            final ISourceViewer viewer= getSourceViewer();
            StyledText text= viewer.getTextWidget();
            Point widgetSelection= text.getSelection();
            if (isBlockSelectionModeEnabled() && widgetSelection.y != widgetSelection.x) {
                final int caret= text.getCaretOffset();
                final int offset= modelOffset2WidgetOffset(viewer, position);

                if (caret == widgetSelection.x)
                    text.setSelectionRange(widgetSelection.y, offset - widgetSelection.y);
                else
                    text.setSelectionRange(widgetSelection.x, offset - widgetSelection.x);
                text.invokeAction(ST.DELETE_PREVIOUS);
            } else {
                Point selection= viewer.getSelectedRange();
                if (selection.y != 0) {
                    position= selection.x;
                    length= selection.y;
                } else {
                    length= widgetOffset2ModelOffset(viewer, text.getCaretOffset()) - position;
                }

                try {
                    viewer.getDocument().replace(position, length, ""); //$NON-NLS-1$
                } catch (BadLocationException exception) {
                    // Should not happen
                }
            }
        }

        public void update() {
            setEnabled(isEditorInputModifiable());
        }
    }

    /**
     * Text operation action to select the previous sub-word.
     *
     * @since 3.0
     */
    protected class SelectPreviousSubWordAction extends PreviousSubWordAction {

        /**
         * Creates a new select previous sub-word action.
         */
        public SelectPreviousSubWordAction() {
            super(ST.SELECT_WORD_PREVIOUS);
        }

        @Override
        protected void setCaretPosition(final int position) {
            final ISourceViewer viewer= getSourceViewer();

            final StyledText text= viewer.getTextWidget();
            if (text != null && !text.isDisposed()) {

                final Point selection= text.getSelection();
                final int caret= text.getCaretOffset();
                final int offset= modelOffset2WidgetOffset(viewer, position);

                if (caret == selection.x)
                    text.setSelectionRange(selection.y, offset - selection.y);
                else
                    text.setSelectionRange(selection.x, offset - selection.x);
            }
        }
    }

    protected void initializeKeyBindingScopes() {
        setKeyBindingScopes(new String[] { PLUGIN_ID + ".context", PLUGIN_ID + ".wizardContext" });
    }

    private IHandlerActivation fSourceQuickAccessHandlerActivation;
    private IHandlerActivation fFindQuickAccessHandlerActivation;
    private IHandlerActivation fRefactorQuickAccessHandlerActivation;
    private IHandlerActivation fNavigateQuickAccessHandlerActivation;
    private IHandlerService fHandlerService;

    public static final String REFACTOR_MENU_ID = "com.redhat.ceylon.eclipse.ui.menu.refactorQuickMenu";
    public static final String NAVIGATE_MENU_ID = "com.redhat.ceylon.eclipse.ui.menu.navigateQuickMenu";
    public static final String FIND_MENU_ID = "com.redhat.ceylon.eclipse.ui.menu.findQuickMenu";
    public static final String SOURCE_MENU_ID = "com.redhat.ceylon.eclipse.ui.menu.sourceQuickMenu";
    
    private class NavigateQuickAccessAction extends QuickMenuAction {
        public NavigateQuickAccessAction() {
            super(NAVIGATE_MENU_ID);
        }
        protected void fillMenu(IMenuManager menu) {
            IContributionItem[] cis = new NavigateMenuItems().getContributionItems();
            for (IContributionItem ci: cis) {
                menu.add(ci);
            }
        }
    }
    
    private class RefactorQuickAccessAction extends QuickMenuAction {
        public RefactorQuickAccessAction() {
            super(REFACTOR_MENU_ID);
        }
        protected void fillMenu(IMenuManager menu) {
            IContributionItem[] cis = new RefactorMenuItems().getContributionItems();
            for (IContributionItem ci: cis) {
                menu.add(ci);
            }
        }
    }
    
    private class FindQuickAccessAction extends QuickMenuAction {
        public FindQuickAccessAction() {
            super(FIND_MENU_ID);
        }
        protected void fillMenu(IMenuManager menu) {
            IContributionItem[] cis = new FindMenuItems().getContributionItems();
            for (IContributionItem ci: cis) {
                menu.add(ci);
            }
        }
    }
    
    private class SourceQuickAccessAction extends QuickMenuAction {
        public SourceQuickAccessAction() {
            super(SOURCE_MENU_ID);
        }
        protected void fillMenu(IMenuManager menu) {
            IContributionItem[] cis = new SourceMenuItems().getContributionItems();
            for (IContributionItem ci: cis) {
                menu.add(ci);
            }
        }
    }
    
    private void installQuickAccessAction() {
        fHandlerService= (IHandlerService) getSite().getService(IHandlerService.class);
        if (fHandlerService != null) {
            QuickMenuAction navigateQuickAccessAction= new NavigateQuickAccessAction();
            fNavigateQuickAccessHandlerActivation= 
                    fHandlerService.activateHandler(navigateQuickAccessAction.getActionDefinitionId(), 
                            new ActionHandler(navigateQuickAccessAction));
            QuickMenuAction refactorQuickAccessAction= new RefactorQuickAccessAction();
            fRefactorQuickAccessHandlerActivation= 
                    fHandlerService.activateHandler(refactorQuickAccessAction.getActionDefinitionId(), 
                            new ActionHandler(refactorQuickAccessAction));
            QuickMenuAction findQuickAccessAction= new FindQuickAccessAction();
            fFindQuickAccessHandlerActivation= 
                    fHandlerService.activateHandler(findQuickAccessAction.getActionDefinitionId(), 
                            new ActionHandler(findQuickAccessAction));
            QuickMenuAction sourceQuickAccessAction= new SourceQuickAccessAction();
            fSourceQuickAccessHandlerActivation= 
                    fHandlerService.activateHandler(sourceQuickAccessAction.getActionDefinitionId(), 
                            new ActionHandler(sourceQuickAccessAction));
        }
    }
    
    protected void uninstallQuickAccessAction() {
        if (fHandlerService != null) {
            fHandlerService.deactivateHandler(fNavigateQuickAccessHandlerActivation); 
            fHandlerService.deactivateHandler(fRefactorQuickAccessHandlerActivation); 
            fHandlerService.deactivateHandler(fFindQuickAccessHandlerActivation); 
            fHandlerService.deactivateHandler(fSourceQuickAccessHandlerActivation); 
        }
    }

    protected boolean isOverviewRulerVisible() {
        return true;
    }

    protected void rulerContextMenuAboutToShow(IMenuManager menu) {
        addDebugActions(menu);
        super.rulerContextMenuAboutToShow(menu);
        menu.appendToGroup(GROUP_RULERS, new Separator());        
        menu.appendToGroup(GROUP_RULERS, getAction("FoldingToggle"));
        menu.appendToGroup(GROUP_RULERS, getAction("FoldingExpandAll"));
        menu.appendToGroup(GROUP_RULERS, getAction("FoldingCollapseAll"));
        menu.appendToGroup(GROUP_RULERS, getAction("FoldingCollapseImports"));
        menu.appendToGroup(GROUP_RULERS, getAction("FoldingCollapseComments"));
    }

    private void addDebugActions(IMenuManager menu) {
        menu.add(toggleBreakpointAction);
        menu.add(enableDisableBreakpointAction);
    }

    /**
     * Sets the given message as error message to this editor's status line.
     *
     * @param msg message to be set
     */
    protected void setStatusLineErrorMessage(String msg) {
        IEditorStatusLine statusLine= (IEditorStatusLine) 
                getAdapter(IEditorStatusLine.class);
        if (statusLine != null)
            statusLine.setMessage(true, msg, null);
    }

    /**
     * Sets the given message as message to this editor's status line.
     *
     * @param msg message to be set
     * @since 3.0
     */
    protected void setStatusLineMessage(String msg) {
        IEditorStatusLine statusLine= (IEditorStatusLine) 
                getAdapter(IEditorStatusLine.class);
        if (statusLine != null)
            statusLine.setMessage(false, msg, null);
    }

    public ProblemMarkerManager getProblemMarkerManager() {
        return problemMarkerManager;
    }
    
    @Override
    protected void setTitleImage(Image titleImage) {
        super.setTitleImage(titleImage);
    }

    public IDocumentProvider getDocumentProvider() {
        if (isSrcArchive(getEditorInput())) {
            //Note: I would prefer to register the
            //document provider in plugin.xml but
            //I don't know how to uniquely identity
            //that a IURIEditorInput is a source
            //archive there
            if (sourceArchiveDocumentProvider==null) {
                sourceArchiveDocumentProvider = 
                        new SourceArchiveDocumentProvider();
            }
            return sourceArchiveDocumentProvider;
        }
        else {
            return super.getDocumentProvider();
        }
    }
    
    public CeylonSourceViewer getCeylonSourceViewer() {
        return (CeylonSourceViewer) super.getSourceViewer();
    }

    public void createPartControl(Composite parent) {
        
        // Initialize the parse controller first, since the 
        // initialization of other things (like the context 
        // help support) might depend on it.
        initializeParseController();
        
        super.createPartControl(parent);

        initiateServiceControllers();

        updateTitleImage();
        //setSourceFontFromPreference();
        
        /*((IContextService) getSite().getService(IContextService.class))
                .activateContext(PLUGIN_ID + ".context");*/
        
        ITheme currentTheme = 
                getTheme();
        currentTheme.getColorRegistry().addListener(colorChangeListener);
        updateFontAndCaret();
        currentTheme.getFontRegistry().addListener(fontChangeListener);
    }

    public synchronized void scheduleParsing() {
        if (parserScheduler!=null && !backgroundParsingPaused) {
            parserScheduler.cancel();
            parserScheduler.schedule(REPARSE_SCHEDULE_DELAY);
        }
    }

    private void initializeParseController() {
        IEditorInput editorInput = getEditorInput();
        IFile file = getFile(editorInput);
        IPath filePath = getPath(editorInput);
        IProject project = file!=null && file.exists() ? file.getProject() : null;
        parseController.initialize(filePath, project, annotationCreator);
    }
    
    private IProblemChangedListener editorIconUpdater = 
            new IProblemChangedListener() {
        @Override
        public void problemsChanged(IResource[] changedResources, boolean isMarkerChange) {
            if (isMarkerChange) {
                IEditorInput input= getEditorInput();
                if (input instanceof IFileEditorInput) { // The editor might be looking at something outside the workspace (e.g. system include files).
                    IFileEditorInput fileInput = (IFileEditorInput) input;
                    IFile file = fileInput.getFile();
                    if (file != null) {
                        for (int i= 0; i<changedResources.length; i++) {
                            if (changedResources[i].equals(file)) {
                                Shell shell= getEditorSite().getShell();
                                if (shell!=null && !shell.isDisposed()) {
                                    shell.getDisplay().syncExec(new Runnable() {
                                        @Override
                                        public void run() {
                                            updateTitleImage();
                                        }
                                    });
                                }
                            }
                        }
                    }
                }
            }
        }
    };
    
    private IDocumentListener documentListener = 
            new IDocumentListener() {
        public void documentAboutToBeChanged(DocumentEvent event) {
            if (parseController!=null) {
                parseController.scheduled();
            }
        }
        public void documentChanged(DocumentEvent event) {
            synchronized (CeylonEditor.this) {
                if (parserScheduler!=null && !backgroundParsingPaused) {
                    parserScheduler.cancel();
                    parserScheduler.schedule(REPARSE_SCHEDULE_DELAY);
                }
            }
        }
    };
    
    private IResourceChangeListener buildListener = 
            new IResourceChangeListener() {
        public void resourceChanged(IResourceChangeEvent event) {
            if (event.getBuildKind()!=CLEAN_BUILD) {
                scheduleParsing();
            }
        }
    };

    /**
     * The following listener is intended to detect when the document associated
     * with this editor changes its identity, which happens when, e.g., the
     * underlying resource gets moved or renamed. We need to see when the editor 
     * input changes, so we can watch the new document.
     */
    private IPropertyListener editorInputPropertyListener = 
            new IPropertyListener() {
        public void propertyChanged(Object source, int propId) {
            if (source == CeylonEditor.this && propId == IEditorPart.PROP_INPUT) {
                IDocument oldDoc = getParseController().getDocument();
                IDocument curDoc = getDocumentProvider().getDocument(getEditorInput()); 
                if (curDoc!=oldDoc) {
                    // Need to unwatch the old document and watch the new document
                    if (oldDoc!=null) {
                        oldDoc.removeDocumentListener(documentListener);
                    }
                    curDoc.addDocumentListener(documentListener);
                }
                initializeParseController();
            }
        }
    };
    
    private IResourceChangeListener moveListener = 
            new IResourceChangeListener() {
        public void resourceChanged(IResourceChangeEvent event) {
            if (parseController==null) return;
            IProject project = parseController.getProject();
            if (project!=null) { //things external to the workspace don't move
                IPath oldWSRelPath = project.getFullPath().append(parseController.getPath());
                IResourceDelta rd = event.getDelta().findMember(oldWSRelPath);
                if (rd != null) {
                    if ((rd.getFlags() & IResourceDelta.MOVED_TO) != 0) {
                        // The net effect of the following is to re-initialize() the parse controller with the new path
                        IPath newPath = rd.getMovedToPath();
                        IPath newProjRelPath = newPath.removeFirstSegments(1);
                        String newProjName = newPath.segment(0);
                        IProject proj = project.getName().equals(newProjName) ? 
                                project : project.getWorkspace().getRoot()
                                .getProject(newProjName);
                        // Tell the parse controller about the move - it caches the path
                        // parserScheduler.cancel(); // avoid a race condition if ParserScheduler was starting/in the middle of a run
                        parseController.initialize(newProjRelPath, proj, annotationCreator);
                    }
                }
            }
        }
    };
    
    private IProblemChangedListener annotationUpdater = 
            new IProblemChangedListener() {
        public void problemsChanged(IResource[] changedResources, 
                boolean isMarkerChange) {
            // Remove annotations that were resolved by changes to 
            // other resources.
            // TODO: It would be better to match the markers to the 
            // annotations, and decide which annotations to remove.
            scheduleParsing();
        }
    };
    
    private void initiateServiceControllers() {

        problemMarkerManager.addListener(annotationUpdater);            
        problemMarkerManager.addListener(editorIconUpdater);
        
        parserScheduler = new CeylonParserScheduler(parseController, this, 
                annotationCreator);
        
        addModelListener(new AdditionalAnnotationCreator(this));
        
        installProjectionSupport();
        
        updateProjectionAnnotationManager();
        
        if (isEditable()) {
            addModelListener(markerAnnotationUpdater);
        }
        
        getSourceViewer().getDocument().addDocumentListener(documentListener);
        addPropertyListener(editorInputPropertyListener);
        getWorkspace().addResourceChangeListener(moveListener, IResourceChangeEvent.POST_CHANGE);
        getWorkspace().addResourceChangeListener(buildListener, IResourceChangeEvent.POST_BUILD);
        
        parserScheduler.schedule();
        
    }
    
    private void installProjectionSupport() {
        final CeylonSourceViewer sourceViewer = getCeylonSourceViewer();
        
        projectionSupport = 
                new ProjectionSupport(sourceViewer, getAnnotationAccess(), getSharedColors());
        MarkerAnnotationPreferences markerAnnotationPreferences = 
                (MarkerAnnotationPreferences) getAdapter(MarkerAnnotationPreferences.class);
        if (markerAnnotationPreferences != null) {
            @SuppressWarnings("unchecked")
            List<AnnotationPreference> annPrefs = 
                    markerAnnotationPreferences.getAnnotationPreferences();
            for (Iterator<AnnotationPreference> e = annPrefs.iterator(); e.hasNext();) {
                Object annotationType = e.next().getAnnotationType();
                if (annotationType instanceof String) {
                    projectionSupport.addSummarizableAnnotationType((String) annotationType);
                }
            }
        } 
        /*else {
            projectionSupport.addSummarizableAnnotationType(PARSE_ANNOTATION_TYPE_ERROR);
            projectionSupport.addSummarizableAnnotationType(PARSE_ANNOTATION_TYPE_WARNING);
            projectionSupport.addSummarizableAnnotationType("org.eclipse.ui.workbench.texteditor.error");
            projectionSupport.addSummarizableAnnotationType("org.eclipse.ui.workbench.texteditor.warning");
        }*/
        projectionSupport.install();

        IPreferenceStore store = EditorsUI.getPreferenceStore();
        store.setDefault(EDITOR_FOLDING_ENABLED, true);
        if (store.getBoolean(EDITOR_FOLDING_ENABLED)) {
            sourceViewer.doOperation(ProjectionViewer.TOGGLE);
        }
        
        sourceViewer.addProjectionListener(projectionAnnotationManager);
    }
    
    private void updateProjectionAnnotationManager() {
        CeylonSourceViewer sourceViewer = getCeylonSourceViewer();
        if (sourceViewer!=null) {
            if (sourceViewer.isProjectionMode()) {
                addModelListener(projectionAnnotationManager);
            }
            else if (projectionAnnotationManager!=null) {
                removeModelListener(projectionAnnotationManager);
            }
        }
    }
    
    @Override
    protected void handlePreferenceStoreChanged(PropertyChangeEvent event) {
        super.handlePreferenceStoreChanged(event);
        if (EDITOR_FOLDING_ENABLED.equals(event.getProperty())) {
            updateProjectionAnnotationManager();
            new ToggleFoldingRunner(this).runWhenNextVisible();
        }
        configCompletionPopup(getCeylonSourceViewer().getContentAssistant());
    }
    
    public void updateTitleImage() {
        IFile file = getFile(getEditorInput());
        if (file!=null) {
            setTitleImage(getImageForFile(file));
        }
    }
    
    public void dispose() {
        
        if (editorIconUpdater!=null) {
            problemMarkerManager.removeListener(editorIconUpdater);
            editorIconUpdater = null;
        }
        if (annotationUpdater!=null) {
            problemMarkerManager.removeListener(annotationUpdater);
            annotationUpdater = null;
        }
        
        outlinePage = null;
        
        if (buildListener!=null) {
            getWorkspace().removeResourceChangeListener(buildListener);
            buildListener = null;
        }
        if (moveListener!=null) {
            getWorkspace().removeResourceChangeListener(moveListener);
            moveListener = null;
        }
        
        IDocument document = getParseController().getDocument();
        if (document!=null) {
            document.removeDocumentListener(documentListener);
        }
        removePropertyListener(editorInputPropertyListener);
        
        if (toggleBreakpointAction!=null) {
            toggleBreakpointAction.dispose(); // this holds onto the IDocument
        }
        if (foldingActionGroup!=null) {
            foldingActionGroup.dispose();
        }
        
        if (projectionSupport!=null) {
            projectionSupport.dispose();
            projectionSupport = null;
        }

        if (parserScheduler!=null) {
            parserScheduler.cancel(); // avoid unnecessary work after the editor is asked to close down
            parserScheduler.dispose();
            parserScheduler = null;
        }
        
        parseController = null;
        
        uninstallQuickAccessAction();

        super.dispose();
        
        /*if (fResourceListener != null) {
            ResourcesPlugin.getWorkspace().removeResourceChangeListener(fResourceListener);
        }*/
        ITheme currentTheme = PlatformUI.getWorkbench().getThemeManager().getCurrentTheme();
        currentTheme.getColorRegistry().removeListener(colorChangeListener);
        currentTheme.getFontRegistry().removeListener(fontChangeListener);
        
    }

    private IPropertyChangeListener colorChangeListener = new IPropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent event) {
            if (event.getProperty().startsWith(PLUGIN_ID + ".theme.color.")) {
                getSourceViewer().invalidateTextPresentation();
            }
        }
    };
    
    IPropertyChangeListener fontChangeListener = new IPropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent event) {
            if (event.getProperty().equals(EDITOR_FONT_PREFERENCE)) {
                updateFontAndCaret();
            }
        }
    };
    
    private static final String EDITOR_FONT_PREFERENCE = PLUGIN_ID + ".editorFont";
    private static final String HOVER_FONT_PREFERENCE = PLUGIN_ID + ".hoverFont";
    
    private static ITheme getTheme() {
        return getWorkbench().getThemeManager().getCurrentTheme();
    }

    private static class GetFont implements Runnable {
        private Font result;
        private final String pref;
        private GetFont(String pref) {
            this.pref = pref;
        }
        @Override
        public void run() {
            result = getTheme().getFontRegistry().get(pref);
        }
    } 

    public static Font getEditorFont() {
        GetFont gf = new GetFont(EDITOR_FONT_PREFERENCE);
        Display.getDefault().syncExec(gf);
        return gf.result;
    }

    public static Font getHoverFont() {
        GetFont gf = new GetFont(HOVER_FONT_PREFERENCE);
        Display.getDefault().syncExec(gf);
        return gf.result;
    }
    
    private void updateFontAndCaret() {
        getSourceViewer().getTextWidget().setFont(getEditorFont());
        try {
            Method updateCaretMethod = AbstractTextEditor.class.getDeclaredMethod("updateCaret");
            updateCaretMethod.setAccessible(true);
            updateCaretMethod.invoke(this);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    protected final SourceViewer createSourceViewer(Composite parent, IVerticalRuler ruler, int styles) {
        
        fAnnotationAccess = getAnnotationAccess();
        fOverviewRuler = createOverviewRuler(getSharedColors());
        
        SourceViewer viewer= new CeylonSourceViewer(this, parent, ruler, 
                getOverviewRuler(), isOverviewRulerVisible(), styles);
        
        // ensure decoration support has been created and configured.
        getSourceViewerDecorationSupport(viewer);
        
        viewer.getTextWidget().addCaretListener(new CaretListener() {
            @Override
            public void caretMoved(CaretEvent event) {
                Object adapter = getAdapter(IVerticalRulerInfo.class);
                if (adapter instanceof CompositeRuler) {
                    // redraw initializer annotations according to cursor position
                    ((CompositeRuler) adapter).update();
                }
            }
        });
    
        return viewer;
    }
    
    protected void configureSourceViewerDecorationSupport(SourceViewerDecorationSupport support) {
        installBracketMatcher(support);
        super.configureSourceViewerDecorationSupport(support);
    }
    
    private void installBracketMatcher(SourceViewerDecorationSupport support) {
        IPreferenceStore store = getPreferenceStore();
        store.setDefault(MATCHING_BRACKET, true);
        ITheme currentTheme = PlatformUI.getWorkbench().getThemeManager().getCurrentTheme();
        Color color = currentTheme.getColorRegistry()
                    .get(PLUGIN_ID + ".theme.matchingBracketsColor");
        store.setDefault(MATCHING_BRACKETS_COLOR, 
                color.getRed() +"," + color.getGreen() + "," + color.getBlue());
        store.setDefault(MATCHING_BRACKET, true);
        store.setDefault(ENCLOSING_BRACKETS, false);
        store.setDefault(SELECTED_BRACKET, false);
        String[][] fences= getFences();
        if (fences != null) {
            StringBuilder sb= new StringBuilder();
            for (int i=0; i<fences.length; i++) {
                sb.append(fences[i][0]);
                sb.append(fences[i][1]);
            }
            bracketMatcher = new DefaultCharacterPairMatcher(sb.toString().toCharArray());
            support.setCharacterPairMatcher(bracketMatcher);
            support.setMatchingCharacterPainterPreferenceKeys(
                    MATCHING_BRACKET, MATCHING_BRACKETS_COLOR, 
                    SELECTED_BRACKET, ENCLOSING_BRACKETS);
        }
    }
    
    public ICharacterPairMatcher getBracketMatcher() {
        return bracketMatcher;
    }
    
    public void saveWithoutActions() {
        super.doSave(getProgressMonitor());
    }
    
    @Override
    public void doSave(IProgressMonitor progressMonitor) {
        CeylonSourceViewer viewer = getCeylonSourceViewer();
        IDocument doc = viewer.getDocument();
        IPreferenceStore prefs = EditorsUI.getPreferenceStore();
        boolean normalizeWs = prefs.getBoolean(NORMALIZE_WS) &&
                prefs.getBoolean(EDITOR_SPACES_FOR_TABS);
        boolean normalizeNl = prefs.getBoolean(NORMALIZE_NL);
        boolean stripTrailingWs = prefs.getBoolean(STRIP_TRAILING_WS);
        boolean cleanImports = prefs.getBoolean(CLEAN_IMPORTS);
        boolean format = prefs.getBoolean(FORMAT);
        if (cleanImports) {
            try {
                CleanImportsHandler.cleanImports(parseController, doc);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (format) {
            try {
                FormatAction.format(parseController, viewer.getDocument(), 
                        EditorUtil.getSelection(this), false, getSelectionProvider());
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        else if (normalizeWs || normalizeNl || stripTrailingWs) {
            normalize(viewer, doc, normalizeWs, normalizeNl, stripTrailingWs);
        }
        super.doSave(progressMonitor);
    }

    private static void normalize(CeylonSourceViewer viewer, IDocument doc,
            boolean normalizeWs, boolean normalizeNl, boolean stripTrailingWs) {
        DocumentRewriteSession rewriteSession= null;
        if (doc instanceof IDocumentExtension4) {
            rewriteSession = 
                    ((IDocumentExtension4) doc).startRewriteSession(SEQUENTIAL);
        }
        
        Point range = viewer.getSelectedRange();
        int modelOffset = range.x;
        int modelLength = range.y;
        try {
            String text = doc.get();
            String normalized = normalize(text, doc, normalizeWs, normalizeNl, 
                    stripTrailingWs);
            if (!normalized.equals(text)) {
                StyledText widget = viewer.getTextWidget();
                Point selection = widget.getSelectionRange();
                StyledTextContent content = widget.getContent();
                int offset = normalize(content.getTextRange(0, selection.x), 
                        doc, normalizeWs, normalizeNl, stripTrailingWs).length();
                int length = normalize(content.getTextRange(selection.x, selection.y), 
                        doc, normalizeWs, normalizeNl, stripTrailingWs).length();
                modelOffset = viewer.widgetOffset2ModelOffset(offset);
                modelLength = viewer.widgetOffset2ModelOffset(offset+length)-modelOffset;
                new ReplaceEdit(0, text.length(), normalized).apply(doc);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            if (doc instanceof IDocumentExtension4) {
                ((IDocumentExtension4) doc).stopRewriteSession(rewriteSession);
            }
            viewer.setSelectedRange(modelOffset, modelLength);
        }
    }

    private static String normalize(String text, IDocument doc, 
            boolean normalizeWs, boolean normalizeNl, 
            boolean stripTrailingWs) {
        if (stripTrailingWs) {
            text = TRAILING_WS.matcher(text).replaceAll("");
        }
        if (normalizeWs) {
            text = text.replace("\t", getDefaultIndent());
        }
        if (normalizeNl) {
            String delim = getDefaultLineDelimiter(doc);
            for (String s: doc.getLegalLineDelimiters()) {
                text = text.replace(s, delim);
            }
        }
        return text;
    }

//    protected void doSetInput(IEditorInput input) throws CoreException {
//        // Catch CoreExceptions here, since it's possible that things like IOExceptions occur
//        // while retrieving the input's contents, e.g., if the given input doesn't exist.
//        try {
//            super.doSetInput(input);
//        } 
//        catch (CoreException e) {
//            if (e.getCause() instanceof IOException) {
//                throw new CoreException(new Status(IStatus.ERROR, CeylonPlugin.PLUGIN_ID, 
//                        0, "Unable to read source text", e.getStatus().getException()));
//            }
//        }
//        setInsertMode(SMART_INSERT);    
//    }

    @Override
    protected void doSetInput(IEditorInput input) throws CoreException {
        if (input instanceof IFileEditorInput && 
                ! (input instanceof SourceArchiveEditorInput)) {
            IFile file = ((IFileEditorInput) input).getFile();
            if (file != null) {
                if (ExternalSourceArchiveManager.isInSourceArchive(file)) {
                    IPath fullPath = ExternalSourceArchiveManager.toFullPath(file);
                    if (fullPath != null) {
                        input = EditorUtil.getEditorInput(fullPath);
                    } else {
                        // Problem => close the editor
                        input = null;
                    }
                }
            }
        }

        if (input != null) {
            //the following crazy stuff seems to be needed in
            //order to get syntax highlighting in structured
            //compare viewer
            CeylonSourceViewer sourceViewer = getCeylonSourceViewer();
            if (sourceViewer!=null) {
                // uninstall & unregister preference store listener
                getSourceViewerDecorationSupport(sourceViewer).uninstall();
                sourceViewer.unconfigure();
                //setPreferenceStore(createCombinedPreferenceStore(input));
                // install & register preference store listener
                sourceViewer.configure(getSourceViewerConfiguration());
                getSourceViewerDecorationSupport(sourceViewer).install(getPreferenceStore());
            }
        }
        
        super.doSetInput(input);
        
        if (input != null) {
            //have to do this or we get a funny-looking caret
            setInsertMode(SMART_INSERT);
        }
    }

    /**
     * Add a Model listener to this editor. Any time the underlying AST is recomputed, the listener is notified.
     * 
     * @param listener the listener to notify of Model changes
     */
    public void addModelListener(TreeLifecycleListener listener) {
        parserScheduler.addModelListener(listener);
    }

    /**
     * Remove a Model listener from this editor.
     * 
     * @param listener the listener to remove
     */
    public void removeModelListener(TreeLifecycleListener listener) {
        parserScheduler.removeModelListener(listener);
    }
    
    public String getSelectionText() {
        IRegion sel = getSelection();
        IDocument document = getDocumentProvider().getDocument(getEditorInput());
        try {
            return document.get(sel.getOffset(), sel.getLength());
        } 
        catch (BadLocationException e) {
            e.printStackTrace();
            return "";
        }
    }

    public IRegion getSelection() {
        ITextSelection ts = (ITextSelection) getSelectionProvider().getSelection();
        return new Region(ts.getOffset(), ts.getLength());
    }

    /**
     * Returns the signed current selection.
     * The length will be negative if the resulting selection
     * is right-to-left (RtoL).
     * The selection offset is model based.
     */
    public IRegion getSignedSelection() {
        ISourceViewer sourceViewer = getSourceViewer();
        StyledText text= sourceViewer.getTextWidget();
        Point selection= text.getSelectionRange();
        if (text.getCaretOffset() == selection.x) {
            selection.x= selection.x + selection.y;
            selection.y= -selection.y;
        }
        selection.x= widgetOffset2ModelOffset(sourceViewer, selection.x);
        return new Region(selection.x, selection.y);
    }
    
    public boolean canPerformFind() {
        return true;
    }

    public CeylonParseController getParseController() {
        return parseController;
    }

    public String toString() {
        return "Ceylon Editor for " + getEditorInput().getName();
    }
    
    boolean isFoldingEnabled() {
        return getPreferenceStore().getBoolean(EDITOR_FOLDING_ENABLED);
    }

    public ITextSelection getSelectionFromThread() {
        final class GetSelection implements Runnable {
            ITextSelection selection;
            @Override
            public void run() {
                ISelectionProvider sp = 
                        CeylonEditor.this.getSelectionProvider();
                selection = sp==null ? 
                        null : (ITextSelection) sp.getSelection();
            }
            ITextSelection getSelection() {
                Display.getDefault().syncExec(this);
                return selection;
            }
        }
        return new GetSelection().getSelection();
    }
    
    public Node getSelectedNode() {
        CeylonParseController cpc = getParseController();
        if (cpc==null || cpc.getRootNode()==null) {
            return null;
        }
        else {
            return findNode(cpc.getRootNode(), 
                    EditorUtil.getSelection(this));
        }
    }
    
}
