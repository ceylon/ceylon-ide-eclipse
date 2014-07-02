package com.redhat.ceylon.eclipse.code.editor;

import static com.redhat.ceylon.eclipse.code.editor.EditorUtil.getSelection;

import java.util.List;

import org.antlr.runtime.BufferedTokenStream;
import org.antlr.runtime.CommonToken;
import org.antlr.runtime.Token;
import org.antlr.runtime.TokenSource;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension4;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.ltk.core.refactoring.DocumentChange;
import org.eclipse.text.edits.ReplaceEdit;

import ceylon.file.Writer;
import ceylon.file.Writer$impl;
import ceylon.formatter.format_;
import ceylon.formatter.options.CombinedOptions;
import ceylon.formatter.options.FormattingOptions;
import ceylon.formatter.options.LineBreak;
import ceylon.formatter.options.Spaces;
import ceylon.formatter.options.SparseFormattingOptions;
import ceylon.formatter.options.Tabs;
import ceylon.formatter.options.crlf_;
import ceylon.formatter.options.lf_;
import ceylon.formatter.options.os_;
import ceylon.language.Singleton;
import ceylon.language.AssertionError;

import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.eclipse.code.parse.CeylonParseController;
import com.redhat.ceylon.eclipse.util.Indents;
import com.redhat.ceylon.eclipse.util.Nodes;

final class FormatAction extends Action {
    private final CeylonEditor editor;
    private final boolean respectSelection;

    FormatAction(CeylonEditor editor) {
        this(editor, true);
    }
    
    FormatAction(CeylonEditor editor, boolean respectSelection) {
        super(null);
        this.editor = editor;
        this.respectSelection = respectSelection;
    }
    
    /**
     * Creates {@link SparseFormattingOptions} that respect whitespace-relevant settings:
     * <ul>
     * <li>{@link SparseFormattingOptions#getIndentMode() indentMode} from spaces-for-tabs and editor-tab-width</li>
     * <li>{@link SparseFormattingOptions#getLineBreak() lineBreak} from document newline character</li>
     * </ul>
     */
    private SparseFormattingOptions getWsOptions() {
        IDocument document = editor.getCeylonSourceViewer().getDocument();
        LineBreak lb;
        if (document instanceof IDocumentExtension4) {
            switch(((IDocumentExtension4)document).getDefaultLineDelimiter()){
            case "\n":
                lb = lf_.get_();
                break;
            case "\r\n":
                lb = crlf_.get_();
                break;
            default:
                lb = os_.get_();
                break;
            }
        } else {
            lb = os_.get_();
        }
        return new SparseFormattingOptions(
                /* indentMode = */ Indents.getIndentWithSpaces() ? new Spaces(Indents.getIndentSpaces()) : new Tabs(Indents.getIndentSpaces()),
                /* maxLineLength = */ null,
                /* lineBreakStrategy = */ null,
                /* braceOnOwnLine = */ null,
                /* spaceBeforeParamListOpeningParen = */ null,
                /* spaceAfterParamListOpeningParen = */ null,
                /* spaceBeforeParamListClosingParen = */ null,
                /* spaceAfterParamListClosingParen = */ null,
                /* inlineAnnotations = */ null,
                /* spaceBeforeMethodOrClassPositionalArgumentList = */ null,
                /* spaceBeforeAnnotationPositionalArgumentList = */ null,
                /* importStyle = */ null,
                /* spaceAroundImportAliasEqualsSign = */ null,
                /* lineBreaksBeforeLineComment = */ null,
                /* lineBreaksAfterLineComment = */ null,
                /* lineBreaksBeforeSingleComment = */ null,
                /* lineBreaksAfterSingleComment = */ null,
                /* lineBreaksBeforeMultiComment = */ null,
                /* lineBreaksAfterMultiComment = */ null,
                /* lineBreaksInTypeParameterList = */ null,
                /* spaceAfterSequenceEnumerationOpeningBrace = */ null,
                /* spaceBeforeSequenceEnumerationClosingBrace = */ null,
                /* spaceBeforeForOpeningParenthesis = */ null,
                /* spaceAfterValueIteratorOpeningParenthesis = */ null,
                /* spaceBeforeValueIteratorClosingParenthesis = */ null,
                /* spaceBeforeIfOpeningParenthesis = */ null,
                /* failFast = */ null,
                /* spaceBeforeResourceList = */ null,
                /* spaceBeforeCatchVariable = */ null,
                /* spaceBeforeWhileOpeningParenthesis = */ null,
                /* spaceAfterTypeArgOrParamListComma = */ null,
                /* indentBeforeTypeInfo = */ null,
                /* indentationAfterSpecifierExpressionStart = */ null,
                /* indentBlankLines = */ null,
                /* lineBreak = */ lb
                );
    }
    
    @Override
    public void run() {
        IDocument document = editor.getCeylonSourceViewer().getDocument();
        final ITextSelection ts = getSelection(editor);
        final boolean selected = respectSelection && ts.getLength() > 0;
        final CeylonParseController pc = editor.getParseController();
        final List<CommonToken> tokenList = pc.getTokens();
        final Node node;
        final CommonToken startToken, endToken;
        if (selected) {
            // a node was selected, format only that
            node = Nodes.findNode(pc.getRootNode(), ts);
            if (node == null)
                return;
            startToken = (CommonToken)node.getToken();
            endToken = (CommonToken)node.getEndToken();
        } else {
            // format everything
            node = pc.getRootNode();
            startToken = tokenList.get(0);
            endToken = tokenList.get(tokenList.size() - 1);
        }
        if (node == null || startToken == null || endToken == null) {
            return;
        }
        final int startTokenIndex = startToken.getTokenIndex();
        final int endTokenIndex = endToken.getTokenIndex();
        final int startIndex = startToken.getStartIndex();
        final int stopIndex = endToken.getStopIndex();
        final TokenSource tokens = new TokenSource() {
            int i = startTokenIndex;
            @Override
            public Token nextToken() {
                if (i <= endTokenIndex)
                    return tokenList.get(i++);
                else if (i == endTokenIndex + 1)
                    return tokenList.get(tokenList.size() - 1); // EOF token
                else
                    return null;
            }
            @Override
            public String getSourceName() {
                throw new IllegalStateException("No one should need this");
            }
        };
        
        final StringBuilder builder = new StringBuilder(document.getLength());
        final SparseFormattingOptions wsOptions = getWsOptions();
        try {
            format_.format(
                    node,
                    new CombinedOptions(format_.format$options(node), new Singleton<SparseFormattingOptions>(SparseFormattingOptions.$TypeDescriptor$, wsOptions)),
                    new StringBuilderWriter(builder),
                    new BufferedTokenStream(tokens),
                    Indents.getIndent(node, document).length() / Indents.getIndentSpaces()
                    );
        } catch (Exception e) {
            return;
        } catch (AssertionError e) {
            return;
        }
        
        final String text;
        if (selected) {
            // remove the trailing line break
            text = builder.substring(0, builder.length() - wsOptions.getLineBreak().toString().length());
        } else {
            text = builder.toString();
        }
        try {
            if (!document.get().equals(text)) {
                DocumentChange change = 
                        new DocumentChange("Format", document);
                change.setEdit(new ReplaceEdit(
                        selected ? startIndex : 0,
                                selected ? stopIndex - startIndex + 1 : document.getLength(),
                                        text));
                change.perform(new NullProgressMonitor());
                if (selected) {
                    editor.getSelectionProvider().setSelection(new TextSelection(startIndex, text.length()));
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static class StringBuilderWriter implements Writer {
        private final StringBuilder builder;
        public StringBuilderWriter(StringBuilder builder) {
            this.builder = builder;
        }
        
        @Override
        public Object write(String string) {
            builder.append(string);
            return null; // void
        }

        // the rest is boring

        @Override
        public Writer$impl $ceylon$file$Writer$impl() {
            return new Writer$impl(this);
        }
        @Override
        public Object close() {
            return null; // void
        }
        @Override
        public Object destroy(Throwable arg0) {
            return null; // void
        }
        @Override
        public Object flush() {
            return null; // void
        }
        @Override
        public Object writeLine() {
            // unused; ceylon.formatter has its own newline handling
            throw new UnsupportedOperationException();
        }
        @Override
        public Object writeLine(String line) {
            // unused; ceylon.formatter has its own newline handling
            throw new UnsupportedOperationException();
        }
        @Override
        public String writeLine$line() {
            return ""; // default value for "line" parameter
        }
    }
}
