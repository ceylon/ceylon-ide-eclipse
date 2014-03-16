package com.redhat.ceylon.eclipse.code.editor;

import static com.redhat.ceylon.eclipse.code.complete.CodeCompletions.getDescriptionFor;
import static com.redhat.ceylon.eclipse.code.editor.EditorUtility.getEditorInput;
import static com.redhat.ceylon.eclipse.code.editor.Navigation.getNodePath;
import static com.redhat.ceylon.eclipse.code.editor.Navigation.gotoNode;
import static com.redhat.ceylon.eclipse.ui.CeylonPlugin.PLUGIN_ID;
import static com.redhat.ceylon.eclipse.ui.CeylonResources.CEYLON_SOURCE;

import java.util.StringTokenizer;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.bindings.TriggerSequence;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.PopupDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlExtension2;
import org.eclipse.jface.text.IInformationControlExtension3;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.StyledString.Styler;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.TextStyle;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.texteditor.IDocumentProvider;

import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.eclipse.code.complete.CompletionUtil;
import com.redhat.ceylon.eclipse.code.parse.CeylonParseController;
import com.redhat.ceylon.eclipse.ui.CeylonPlugin;
import com.redhat.ceylon.eclipse.util.Nodes;

final class PeekDefinitionPopup extends PopupDialog 
        implements IInformationControl, IInformationControlExtension2,
                   IInformationControlExtension3 {
    
    private final class GotoListener implements KeyListener {
        @Override
        public void keyReleased(KeyEvent e) {}

        @Override
        public void keyPressed(KeyEvent e) {
            if (e.character == 0x1B) // ESC
                dispose();
            if (EditorUtil.triggersBinding(e, getCommandBinding())) {
                e.doit=false;
                dispose();
                gotoNode(referencedNode);
            }
        }
    }

    private ISourceViewer viewer;
    private final CeylonEditor editor;
    private Node referencedNode;
    private CeylonParseController pc = new CeylonParseController();
    
    public ISourceViewer getViewer() {
        return viewer;
    }
    
    private StyledText titleLabel;

    private TriggerSequence commandBinding;
    
    protected TriggerSequence getCommandBinding() {
        return commandBinding;
    }
    
    PeekDefinitionPopup(Shell parent, int shellStyle, CeylonEditor editor) {
        super(parent, shellStyle, true, true, false, true,
                true, null, null);
        this.editor = editor;
        commandBinding = EditorUtil.getCommandBinding(PLUGIN_ID + 
                ".editor.code");
        if (commandBinding!=null) {
            setInfoText(commandBinding.format() + " to open editor");
        }
        
        create();
        
        Color bg = parent.getDisplay().getSystemColor(SWT.COLOR_INFO_BACKGROUND);
        getShell().setBackground(bg);
        setBackgroundColor(bg);

        //setBackgroundColor(getEditorWidget(editor).getBackground());
        setForegroundColor(getEditorWidget(editor).getForeground());
    }

    private StyledText getEditorWidget(CeylonEditor editor) {
        return editor.getCeylonSourceViewer().getTextWidget();
    }

    protected Control createContents(Composite parent) {
        Composite composite = (Composite) super.createContents(parent);
        Control[] children = composite.getChildren();
        GridLayout layout = (GridLayout) composite.getLayout();
        layout.verticalSpacing=8;
        layout.marginLeft=8;
        layout.marginRight=8;
        layout.marginTop=8;
        layout.marginBottom=8;
        children[children.length-2].setVisible(false);
        return composite;
    }
    
    @Override
    protected Control createDialogArea(Composite parent) {
        int styles= SWT.V_SCROLL | SWT.H_SCROLL | SWT.MULTI | SWT.FULL_SELECTION;
        viewer = new CeylonSourceViewer(editor, parent, null, null, false, styles);
        viewer.setEditable(false);
        StyledText textWidget = viewer.getTextWidget();
        textWidget.setFont(getEditorWidget(editor).getFont());
        textWidget.setBackground(getEditorWidget(editor).getBackground());
        textWidget.addKeyListener(new GotoListener());
        return textWidget;
    }
    
    private static GridLayoutFactory popupLayoutFactory;
    protected static GridLayoutFactory getPopupLayout() {
        if (popupLayoutFactory == null) {
            popupLayoutFactory = GridLayoutFactory.fillDefaults()
                    .margins(POPUP_MARGINWIDTH, POPUP_MARGINHEIGHT)
                    .spacing(POPUP_HORIZONTALSPACING, POPUP_VERTICALSPACING);
        }
        return popupLayoutFactory;
    }
    
    protected StyledString styleTitle(final StyledText title) {
        StyledString result = new StyledString();
        StringTokenizer tokens = 
                new StringTokenizer(title.getText(), "-", false);
        styleDescription(title, result, tokens.nextToken());
        result.append("-");
        CompletionUtil.styleProposal(result, tokens.nextToken());
        return result;
    }

    protected void styleDescription(final StyledText title, StyledString result,
            String desc) {
        final FontData[] fontDatas = title.getFont().getFontData();
        for (int i = 0; i < fontDatas.length; i++) {
            fontDatas[i].setStyle(SWT.BOLD);
        }
        result.append(desc, new Styler() {
            @Override
            public void applyStyles(TextStyle textStyle) {
                textStyle.font=new Font(title.getDisplay(), fontDatas);
            }
        });
    }

    @Override
    protected Control createTitleControl(Composite parent) {
        getPopupLayout().copy().numColumns(3).spacing(6, 6).applyTo(parent);
        Label iconLabel = new Label(parent, SWT.NONE);
        iconLabel.setImage(CeylonPlugin.getInstance().getImageRegistry().get(CEYLON_SOURCE));
        getShell().addKeyListener(new GotoListener());
        titleLabel = new StyledText(parent, SWT.NONE);
        titleLabel.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent e) {
                titleLabel.setStyleRanges(styleTitle(titleLabel).getStyleRanges());
            }
        });
        titleLabel.setEditable(false);
        GridDataFactory.fillDefaults().align(SWT.FILL, SWT.CENTER)
            .grab(true,false).span(1, 1).applyTo(titleLabel);
        return null;
    }
    
    @Override
    protected void setTitleText(String text) {
        if (titleLabel!=null)
            titleLabel.setText(text);
    }
    
    /*@Override
    protected void adjustBounds() {
        Rectangle bounds = getShell().getBounds();
        int h = bounds.height;
        if (h>400) {
            bounds.height=400;
            bounds.y = bounds.y + (h-400)/3;
            getShell().setBounds(bounds);
        }
        int w = bounds.width;
        if (w<600) {
            bounds.width=600;
            getShell().setBounds(bounds);
        }
    }*/
    
    public void setInformation(String information) {
        // this method is ignored, see IInformationControlExtension2
    }

    public void setSize(int width, int height) {
        getShell().setSize(width, height);
    }

    public void addDisposeListener(DisposeListener listener) {
        getShell().addDisposeListener(listener);
    }

    public void removeDisposeListener(DisposeListener listener) {
        getShell().removeDisposeListener(listener);
    }

    public void setForegroundColor(Color foreground) {
        applyForegroundColor(foreground, getContents());
    }

    public void setBackgroundColor(Color background) {
        applyBackgroundColor(background, getContents());
    }

    public boolean isFocusControl() {
        return getShell().getDisplay().getActiveShell() == getShell();
    }

    public void setFocus() {
        getShell().forceFocus();
    }

    public void addFocusListener(FocusListener listener) {
        getShell().addFocusListener(listener);
    }

    public void removeFocusListener(FocusListener listener) {
        getShell().removeFocusListener(listener);
    }


    public void setSizeConstraints(int maxWidth, int maxHeight) {
        // ignore
    }

    public void setLocation(Point location) {
        /*
         * If the location is persisted, it gets managed by PopupDialog - fine. Otherwise, the location is
         * computed in Window#getInitialLocation, which will center it in the parent shell / main
         * monitor, which is wrong for two reasons:
         * - we want to center over the editor / subject control, not the parent shell
         * - the center is computed via the initalSize, which may be also wrong since the size may
         *   have been updated since via min/max sizing of AbstractInformationControlManager.
         * In that case, override the location with the one computed by the manager. Note that
         * the call to constrainShellSize in PopupDialog.open will still ensure that the shell is
         * entirely visible.
         */
        if (!getPersistLocation() || getDialogSettings() == null)
            getShell().setLocation(location);
    }

    public Point computeSizeHint() {
        // return the shell's size - note that it already has the persisted size if persisting
        // is enabled.
        return getShell().getSize();
    }

    public void setVisible(boolean visible) {
        if (visible) {
            open();
        } else {
            saveDialogBounds(getShell());
            getShell().setVisible(false);
        }
    }

    public final void dispose() {
        docProvider.disconnect(ei);
        ei = null;
        close();
    }

    IDocumentProvider docProvider = new SourceArchiveDocumentProvider();
    IEditorInput ei;
    
    @Override
    public void setInput(Object input) {
        CeylonParseController epc = editor.getParseController();
        IRegion r = editor.getSelection();
        Node node = Nodes.findNode(epc.getRootNode(), r.getOffset(), 
                r.getOffset()+r.getLength());
        referencedNode = Nodes.getReferencedNode(node, epc);
        if (referencedNode==null) return;
        IPath path = getNodePath(referencedNode);
        //CeylonParseController treats files with full paths subtly
        //differently to files with relative paths, so make the
        //path relative
        if (epc.getProject()!=null && 
                epc.getProject().getLocation().isPrefixOf(path)) {
            path = path.makeRelativeTo(epc.getProject().getLocation());
        }
        IDocument doc;
        if (path.equals(epc.getPath())) {
            doc = epc.getDocument();
        }
        else {
            ei = getEditorInput(path);
            try {
                docProvider.connect(ei);
                doc = docProvider.getDocument(ei);
            } 
            catch (CoreException e) {
                e.printStackTrace();
                return;
            }
        }
        viewer.setDocument(doc);
        try {
            IRegion firstLine = doc.getLineInformationOfOffset(referencedNode.getStartIndex());
            IRegion lastLine = doc.getLineInformationOfOffset(referencedNode.getStopIndex());
            viewer.setVisibleRegion(firstLine.getOffset(), 
                    lastLine.getOffset()+lastLine.getLength()-firstLine.getOffset());
        }
        catch (BadLocationException e) {
            e.printStackTrace();
        }
        pc.initialize(path, epc.getProject(), null);
        pc.parse(doc, new NullProgressMonitor(), null);
        /*try {
            int lines = doc.getLineOfOffset(refDec.getStopIndex())-
                        doc.getLineOfOffset(refDec.getStartIndex())+1;
            setSize(getShell().getBounds().width, 
                    viewer.getTextWidget().getLineHeight()*lines);
        } 
        catch (BadLocationException e) {
            e.printStackTrace();
        }*/
        if (referencedNode instanceof Tree.Declaration) {
            Declaration model = ((Tree.Declaration) referencedNode).getDeclarationModel();
            setTitleText("Peek Definition - " + getDescriptionFor(model));
        }
    }

    @Override
    public boolean restoresLocation() {
        return false;
    }
    
    @Override
    public boolean restoresSize() {
        return true;
    }
    
    @Override
    public Rectangle getBounds() {
        return getShell().getBounds();
    }
    
    @Override
    public Rectangle computeTrim() {
        return getShell().computeTrim(0, 0, 0, 0);
    }

    public CeylonParseController getParseController() {
        return pc;
    }

    @Override
    protected IDialogSettings getDialogSettings() {
        String sectionName= "com.redhat.ceylon.eclipse.ui.PeekDefinition";
        IDialogSettings dialogSettings = CeylonPlugin.getInstance()
                .getDialogSettings();
        IDialogSettings settings= dialogSettings.getSection(sectionName);
        if (settings == null)
            settings= dialogSettings.addNewSection(sectionName);
        return settings;
    }

}