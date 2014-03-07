package com.redhat.ceylon.eclipse.code.complete;

import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.AIDENTIFIER;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.ASTRING_LITERAL;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.AVERBATIM_STRING;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.CASE_TYPES;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.CHAR_LITERAL;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.COMMA;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.EOF;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.FLOAT_LITERAL;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.IS_OP;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.LARGER_OP;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.LBRACE;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.LIDENTIFIER;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.LINE_COMMENT;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.LPAREN;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.MEMBER_OP;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.MULTI_COMMENT;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.NATURAL_LITERAL;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.PIDENTIFIER;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.RBRACE;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.SAFE_MEMBER_OP;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.SEMICOLON;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.SPREAD_OP;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.STRING_END;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.STRING_LITERAL;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.STRING_MID;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.STRING_START;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.UIDENTIFIER;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.VERBATIM_STRING;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.WS;
import static com.redhat.ceylon.eclipse.code.complete.BasicCompletionProposal.addDocLinkProposal;
import static com.redhat.ceylon.eclipse.code.complete.BasicCompletionProposal.addForProposal;
import static com.redhat.ceylon.eclipse.code.complete.BasicCompletionProposal.addIfExistsProposal;
import static com.redhat.ceylon.eclipse.code.complete.BasicCompletionProposal.addImportProposal;
import static com.redhat.ceylon.eclipse.code.complete.BasicCompletionProposal.addSwitchProposal;
import static com.redhat.ceylon.eclipse.code.complete.CompletionUtil.getLine;
import static com.redhat.ceylon.eclipse.code.complete.CompletionUtil.getOccurrenceLocation;
import static com.redhat.ceylon.eclipse.code.complete.CompletionUtil.getRealScope;
import static com.redhat.ceylon.eclipse.code.complete.CompletionUtil.isEmptyModuleDescriptor;
import static com.redhat.ceylon.eclipse.code.complete.CompletionUtil.isEmptyPackageDescriptor;
import static com.redhat.ceylon.eclipse.code.complete.CompletionUtil.isModuleDescriptor;
import static com.redhat.ceylon.eclipse.code.complete.CompletionUtil.isPackageDescriptor;
import static com.redhat.ceylon.eclipse.code.complete.CompletionUtil.nextTokenType;
import static com.redhat.ceylon.eclipse.code.complete.CompletionUtil.overloads;
import static com.redhat.ceylon.eclipse.code.complete.InvocationCompletionProposal.addFakeShowParametersCompletion;
import static com.redhat.ceylon.eclipse.code.complete.InvocationCompletionProposal.addInvocationProposals;
import static com.redhat.ceylon.eclipse.code.complete.InvocationCompletionProposal.addProgramElementReferenceProposal;
import static com.redhat.ceylon.eclipse.code.complete.InvocationCompletionProposal.addReferenceProposal;
import static com.redhat.ceylon.eclipse.code.complete.InvocationCompletionProposal.computeParameterContextInformation;
import static com.redhat.ceylon.eclipse.code.complete.KeywordCompletionProposal.addKeywordProposals;
import static com.redhat.ceylon.eclipse.code.complete.MemberNameCompletions.addMemberNameProposal;
import static com.redhat.ceylon.eclipse.code.complete.MemberNameCompletions.addMemberNameProposals;
import static com.redhat.ceylon.eclipse.code.complete.ModuleCompletions.addModuleCompletions;
import static com.redhat.ceylon.eclipse.code.complete.ModuleCompletions.addModuleDescriptorCompletion;
import static com.redhat.ceylon.eclipse.code.complete.OccurrenceLocation.ALIAS_REF;
import static com.redhat.ceylon.eclipse.code.complete.OccurrenceLocation.CASE;
import static com.redhat.ceylon.eclipse.code.complete.OccurrenceLocation.CATCH;
import static com.redhat.ceylon.eclipse.code.complete.OccurrenceLocation.CLASS_ALIAS;
import static com.redhat.ceylon.eclipse.code.complete.OccurrenceLocation.DOCLINK;
import static com.redhat.ceylon.eclipse.code.complete.OccurrenceLocation.EXPRESSION;
import static com.redhat.ceylon.eclipse.code.complete.OccurrenceLocation.EXTENDS;
import static com.redhat.ceylon.eclipse.code.complete.OccurrenceLocation.FUNCTION_REF;
import static com.redhat.ceylon.eclipse.code.complete.OccurrenceLocation.IMPORT;
import static com.redhat.ceylon.eclipse.code.complete.OccurrenceLocation.META;
import static com.redhat.ceylon.eclipse.code.complete.OccurrenceLocation.OF;
import static com.redhat.ceylon.eclipse.code.complete.OccurrenceLocation.PARAMETER_LIST;
import static com.redhat.ceylon.eclipse.code.complete.OccurrenceLocation.SATISFIES;
import static com.redhat.ceylon.eclipse.code.complete.OccurrenceLocation.TYPE_ALIAS;
import static com.redhat.ceylon.eclipse.code.complete.OccurrenceLocation.TYPE_ARGUMENT_LIST;
import static com.redhat.ceylon.eclipse.code.complete.OccurrenceLocation.TYPE_PARAMETER_LIST;
import static com.redhat.ceylon.eclipse.code.complete.OccurrenceLocation.TYPE_PARAMETER_REF;
import static com.redhat.ceylon.eclipse.code.complete.OccurrenceLocation.UPPER_BOUND;
import static com.redhat.ceylon.eclipse.code.complete.OccurrenceLocation.VALUE_REF;
import static com.redhat.ceylon.eclipse.code.complete.PackageCompletions.addCurrentPackageNameCompletion;
import static com.redhat.ceylon.eclipse.code.complete.PackageCompletions.addPackageCompletions;
import static com.redhat.ceylon.eclipse.code.complete.PackageCompletions.addPackageDescriptorCompletion;
import static com.redhat.ceylon.eclipse.code.complete.RefinementCompletionProposal.addInlineFunctionProposal;
import static com.redhat.ceylon.eclipse.code.complete.RefinementCompletionProposal.addNamedArgumentProposal;
import static com.redhat.ceylon.eclipse.code.complete.RefinementCompletionProposal.addRefinementProposal;
import static com.redhat.ceylon.eclipse.code.complete.RefinementCompletionProposal.getRefinedProducedReference;
import static com.redhat.ceylon.eclipse.code.complete.TypeArgumentListCompletions.addTypeArgumentListProposal;
import static com.redhat.ceylon.eclipse.code.editor.CeylonSourceViewerConfiguration.AUTO_ACTIVATION_CHARS;
import static com.redhat.ceylon.eclipse.util.Types.getRequiredType;
import static com.redhat.ceylon.eclipse.util.Types.getResultType;
import static java.lang.Character.isDigit;
import static java.lang.Character.isLetter;
import static java.util.Collections.emptyMap;
import static org.antlr.runtime.Token.HIDDEN_CHANNEL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.antlr.runtime.CommonToken;
import org.antlr.runtime.Token;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;

import com.redhat.ceylon.compiler.typechecker.model.Class;
import com.redhat.ceylon.compiler.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.DeclarationWithProximity;
import com.redhat.ceylon.compiler.typechecker.model.Functional;
import com.redhat.ceylon.compiler.typechecker.model.ImportList;
import com.redhat.ceylon.compiler.typechecker.model.Interface;
import com.redhat.ceylon.compiler.typechecker.model.Method;
import com.redhat.ceylon.compiler.typechecker.model.MethodOrValue;
import com.redhat.ceylon.compiler.typechecker.model.Package;
import com.redhat.ceylon.compiler.typechecker.model.ProducedReference;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.model.Scope;
import com.redhat.ceylon.compiler.typechecker.model.TypeAlias;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.TypeParameter;
import com.redhat.ceylon.compiler.typechecker.model.TypedDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.Unit;
import com.redhat.ceylon.compiler.typechecker.model.Value;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.MemberLiteral;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;
import com.redhat.ceylon.eclipse.code.editor.CeylonEditor;
import com.redhat.ceylon.eclipse.code.parse.CeylonParseController;
import com.redhat.ceylon.eclipse.util.Escaping;
import com.redhat.ceylon.eclipse.util.Nodes;

public class CeylonCompletionProcessor implements IContentAssistProcessor {
    
    private static final char[] CONTEXT_INFO_ACTIVATION_CHARS = ",(;{".toCharArray();
    
    private static final IContextInformation[] NO_CONTEXTS = new IContextInformation[0];
    static ICompletionProposal[] NO_COMPLETIONS = new ICompletionProposal[0];
    
    private ParameterContextValidator validator;
    private CeylonEditor editor;
    
    private boolean filter;
    private boolean returnedParamInfo;
    private int lastOffsetAcrossSessions=-1;
    private int lastOffset=-1;
    
    public void sessionStarted() {
        filter = false;
        lastOffset=-1;
    }
    
    public CeylonCompletionProcessor(CeylonEditor editor) {
        this.editor=editor;
    }
    
    public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, 
            int offset) {
        if (offset!=lastOffsetAcrossSessions) {
            returnedParamInfo = false;
            filter = false;
        }
        try {
            if (lastOffset>=0 && offset>0 && offset!=lastOffset &&
                    !isIdentifierCharacter(viewer, offset)) {
                //user typed a whitespace char with an open
                //completions window, so close the window
                return NO_COMPLETIONS;
            }
        } 
        catch (BadLocationException ble) {
            ble.printStackTrace();
            return NO_COMPLETIONS;
        }
        if (offset==lastOffset) {
            filter = !filter;
        }
        lastOffset = offset;
        lastOffsetAcrossSessions = offset;
        try {
            ICompletionProposal[] contentProposals = 
                    getContentProposals(editor.getParseController(), 
                            offset, viewer, filter, returnedParamInfo);
            if (contentProposals!=null && contentProposals.length==1 && 
                    contentProposals[0] instanceof 
                            InvocationCompletionProposal.ParameterInfo) {
                returnedParamInfo = true;
            }
            return contentProposals;
        }
        catch (Exception e) {
            e.printStackTrace();
            return NO_COMPLETIONS;
        }
    }

    private boolean isIdentifierCharacter(ITextViewer viewer, int offset)
            throws BadLocationException {
        char ch = viewer.getDocument().get(offset-1, 1).charAt(0);
        return isLetter(ch) || isDigit(ch) || ch=='_' || ch=='.';
    }

    public IContextInformation[] computeContextInformation(final ITextViewer viewer, 
            final int offset) {
        CeylonParseController cpc = editor.getParseController();
        cpc.parse(viewer.getDocument(), new NullProgressMonitor(), null);
        return computeParameterContextInformation(offset, cpc.getRootNode(), viewer)
                .toArray(NO_CONTEXTS);
    }

    public char[] getCompletionProposalAutoActivationCharacters() {
        return editor.getPrefStore().getString(AUTO_ACTIVATION_CHARS).toCharArray();
    }

    public char[] getContextInformationAutoActivationCharacters() {
        return CONTEXT_INFO_ACTIVATION_CHARS;
    }

    public IContextInformationValidator getContextInformationValidator() {
        if (validator==null) {
            validator = new ParameterContextValidator(editor);
        }
        return validator;
    }

    public String getErrorMessage() {
        return "No completions available";
    }
    
    public ICompletionProposal[] getContentProposals(CeylonParseController cpc,
            int offset, ITextViewer viewer, boolean filter, 
            boolean returnedParamInfo) {
        
        if (cpc==null || viewer==null || 
                cpc.getRootNode()==null || 
                cpc.getTokens()==null) {
            return null;
        }
        
        cpc.parse(viewer.getDocument(), new NullProgressMonitor(), null);
        cpc.getHandler().updateAnnotations();
        List<CommonToken> tokens = cpc.getTokens(); 
        Tree.CompilationUnit rn = cpc.getRootNode();
        
        //adjust the token to account for unclosed blocks
        //we search for the first non-whitespace/non-comment
        //token to the left of the caret
        int tokenIndex = Nodes.getTokenIndexAtCharacter(tokens, offset);
        if (tokenIndex<0) tokenIndex = -tokenIndex;
        CommonToken adjustedToken = adjust(tokenIndex, offset, tokens);
        int tt = adjustedToken.getType();
        
        if (offset<=adjustedToken.getStopIndex() && 
            offset>adjustedToken.getStartIndex()) {
            if (isCommentOrCodeStringLiteral(adjustedToken)) {
                return null;
            }
        }
        if (isLineComment(adjustedToken) &&
            offset>adjustedToken.getStartIndex() && 
            adjustedToken.getLine()==getLine(offset,viewer)+1) {
            return null;
        }
        
        //find the node at the token
        Node node = getTokenNode(adjustedToken.getStartIndex(), 
                adjustedToken.getStopIndex()+1, 
                tt, rn, offset);
        
        //it's useful to know the type of the preceding 
        //token, if any
        int index = adjustedToken.getTokenIndex();
        if (offset<=adjustedToken.getStopIndex()+1 && 
            offset>adjustedToken.getStartIndex()) {
            index--;
        }
        int previousTokenType = index>=0 ? 
                adjust(index, offset, tokens).getType() : 
                -1;
                
        //find the type that is expected in the current
        //location so we can prioritize proposals of that
        //type
        //TODO: this breaks as soon as the user starts typing
        //      an expression, since RequiredTypeVisitor
        //      doesn't know how to search up the tree for
        //      the containing InvocationExpression
        ProducedType requiredType = 
                getRequiredType(rn, node, adjustedToken);
        
        String prefix = "";
        String fullPrefix = "";
        if (isIdentifierOrKeyword(adjustedToken)) {
            String text = adjustedToken.getText();
            //work from the end of the token to
            //compute the offset, in order to
            //account for quoted identifiers, where
            //the \i or \I is not in the token text 
            int offsetInToken = offset-adjustedToken.getStopIndex()-1+text.length();
            int realOffsetInToken = offset-adjustedToken.getStartIndex();
            if (offsetInToken<=text.length()) {
                prefix = text.substring(0, offsetInToken);
                fullPrefix = getRealText(adjustedToken)
                        .substring(0, realOffsetInToken);
            }
        }
        boolean isMemberOp = isMemberOperator(adjustedToken);
        String qualified = null;
        
        // special handling for doc links
        boolean inDoc = isAnnotationStringLiteral(adjustedToken) &&
                offset>adjustedToken.getStartIndex() &&
                offset<adjustedToken.getStopIndex();
        if (inDoc) {
            Tree.DocLink docLink = (Tree.DocLink) node;
            int offsetInLink = offset-docLink.getStartIndex();
            String text = docLink.getToken().getText();
            int bar = text.indexOf('|')+1;
            if (offsetInLink<bar) {
                return null;
            }
            qualified = text.substring(bar, offsetInLink);
            int dcolon = qualified.indexOf("::");
            String pkg = null;
            if (dcolon>=0) {
                pkg = qualified.substring(0, dcolon+2);
                qualified = qualified.substring(dcolon+2);
            }
            int dot = qualified.indexOf('.')+1;
            isMemberOp = dot>0;
            prefix = qualified.substring(dot);
            if (dcolon>=0) {
                qualified = pkg + qualified;
            }
            fullPrefix = prefix;
        }
        
        Scope scope = getRealScope(node, rn);
        
        //construct completions when outside ordinary code
        ICompletionProposal[] completions = 
                constructCompletions(offset, fullPrefix, cpc, node, 
                        adjustedToken, scope, returnedParamInfo, 
                        isMemberOp, viewer.getDocument());
        if (completions==null) {
            //finally, construct and sort proposals
            Map<String, DeclarationWithProximity> proposals = 
                    getProposals(node, scope, prefix, isMemberOp, rn);
            filterProposals(filter, rn, requiredType, proposals);
            Set<DeclarationWithProximity> sortedProposals = 
                    sortProposals(prefix, requiredType, proposals);
            completions =
                    constructCompletions(offset, inDoc ? qualified : fullPrefix, 
                            sortedProposals, cpc, scope, node, adjustedToken, 
                            isMemberOp, viewer.getDocument(), filter, inDoc,
                            requiredType, previousTokenType);
        }
        return completions;
        
    }
    
    private String getRealText(CommonToken token) {
        String text = token.getText();
        int type = token.getType();
        int len = token.getStopIndex()-token.getStartIndex()+1;
        if (text.length()<len) {
            String quote;
            if (type==LIDENTIFIER) {
                quote = "\\i";
            }
            else if (type==UIDENTIFIER) {
                quote = "\\I";
            }
            else {
                quote = "";
            }
            return quote + text;
        }
        else {
            return text;
        }
    }

    private boolean isLineComment(CommonToken adjustedToken) {
        return adjustedToken.getType()==LINE_COMMENT;
    }

    private boolean isCommentOrCodeStringLiteral(CommonToken adjustedToken) {
        int tt = adjustedToken.getType();
        return tt==MULTI_COMMENT ||
            tt==LINE_COMMENT ||
            tt==STRING_LITERAL ||
            tt==STRING_END ||
            tt==STRING_MID ||
            tt==STRING_START ||
            tt==VERBATIM_STRING ||
            tt==CHAR_LITERAL ||
            tt==FLOAT_LITERAL ||
            tt==NATURAL_LITERAL;
    }

    private void filterProposals(boolean filter, Tree.CompilationUnit rn,
            ProducedType requiredType, Map<String, DeclarationWithProximity> proposals) {
        if (filter) {
            Iterator<Map.Entry<String, DeclarationWithProximity>> iter = 
                    proposals.entrySet().iterator();
            while (iter.hasNext()) {
                Declaration d = iter.next().getValue().getDeclaration();
                ProducedType type = getResultType(d);
                ProducedType fullType = d.getReference().getFullType();
                if (requiredType!=null && (type==null ||
                        (!type.isSubtypeOf(requiredType) && !fullType.isSubtypeOf(requiredType)) || 
                        type.isSubtypeOf(rn.getUnit().getNullDeclaration().getType()))) {
                    iter.remove();
                }
            }
        }
    }

    private static boolean isAnnotationStringLiteral(CommonToken token) {
        int type = token.getType();
        return type == ASTRING_LITERAL || 
                type == AVERBATIM_STRING;
    }
    
    private static CommonToken adjust(int tokenIndex, int offset, 
            List<CommonToken> tokens) {
        CommonToken adjustedToken = tokens.get(tokenIndex); 
        while (--tokenIndex>=0 && 
                (adjustedToken.getType()==WS //ignore whitespace
                || adjustedToken.getType()==EOF
                || adjustedToken.getStartIndex()==offset)) { //don't consider the token to the right of the caret
            adjustedToken = tokens.get(tokenIndex);
            if (adjustedToken.getType()!=WS &&
                    adjustedToken.getType()!=EOF &&
                    adjustedToken.getChannel()!=HIDDEN_CHANNEL) { //don't adjust to a ws token
                break;
            }
        }
        return adjustedToken;
    }
    
    private static Boolean isDirectlyInsideBlock(Node node,
            CeylonParseController cpc, Scope scope,
            CommonToken token) {
        if (scope instanceof Interface || 
                scope instanceof Package) {
            return false;
        }
        else {
            //TODO: check that it is not the opening/closing 
            //      brace of a named argument list!
            return !(node instanceof Tree.SequenceEnumeration) && 
                    occursAfterBraceOrSemicolon(token, cpc.getTokens());
        }
    }

    private static Boolean occursAfterBraceOrSemicolon(CommonToken token,
            List<CommonToken> tokens) {
        if (token.getTokenIndex()==0) {
            return false;
        }
        else {
            int tokenType = token.getType();
            if (tokenType==LBRACE || 
                    tokenType==RBRACE || 
                    tokenType==SEMICOLON) {
                return true;
            }
            int previousTokenType = adjust(token.getTokenIndex()-1, 
                    token.getStartIndex(), tokens).getType();
            return previousTokenType==LBRACE || 
                    previousTokenType==RBRACE || 
                    previousTokenType==SEMICOLON;
        }
    }

    private static Node getTokenNode(int adjustedStart, int adjustedEnd,
            int tokenType, Tree.CompilationUnit rn, int offset) {
        Node node = Nodes.findNode(rn, adjustedStart, adjustedEnd);
        if (node instanceof Tree.StringLiteral && 
                !((Tree.StringLiteral) node).getDocLinks().isEmpty()) {
            node = Nodes.findNode(node, offset, offset);
        }
        if (tokenType==RBRACE || tokenType==SEMICOLON) {
            //We are to the right of a } or ;
            //so the returned node is the previous
            //statement/declaration. Look for the
            //containing body.
            class BodyVisitor extends Visitor {
                Node node, currentBody, result;
                BodyVisitor(Node node, Node root) {
                    this.node = node;
                    currentBody = root;
                }
                @Override
                public void visitAny(Node that) {
                    if (that==node) {
                        result = currentBody;
                    }
                    else {
                        Node cb = currentBody;
                        if (that instanceof Tree.Body) {
                            currentBody = that;
                        }
                        if (that instanceof Tree.NamedArgumentList) {
                            currentBody = that;
                        }
                        super.visitAny(that);
                        currentBody = cb;
                    }
                }
            }
            BodyVisitor mv = new BodyVisitor(node, rn);
            mv.visit(rn);
            node = mv.result;
        }
        
        if (node==null) node = rn; //we're in whitespace at the start of the file
        return node;
    }
    
    private static boolean isIdentifierOrKeyword(Token token) {
        int type = token.getType();
        return type==LIDENTIFIER || 
                type==UIDENTIFIER ||
                type==AIDENTIFIER ||
                type==PIDENTIFIER ||
                Escaping.KEYWORDS.contains(token.getText());
    }
    
    private static ICompletionProposal[] constructCompletions(final int offset, 
            final String prefix, final CeylonParseController cpc, final Node node, 
            final CommonToken token, final Scope scope, boolean returnedParamInfo, 
            boolean memberOp, final IDocument document) {
        
        final List<ICompletionProposal> result = new ArrayList<ICompletionProposal>();
        
        if (!returnedParamInfo && atStartOfPositionalArgument(node, token)) {
            addFakeShowParametersCompletion(node, cpc, result);
        }
        else if (node instanceof Tree.PackageLiteral) {
            addPackageCompletions(cpc, offset, prefix, null, node, result, false);
        }
        else if (node instanceof Tree.ModuleLiteral) {
            addModuleCompletions(cpc, offset, prefix, null, node, result, false);
        }
        else if (isDescriptorPackageNameMissing(node)) {
            addCurrentPackageNameCompletion(cpc, offset, prefix, result);
        }
        else if (node instanceof Tree.Import && offset>token.getStopIndex()+1) {
            addPackageCompletions(cpc, offset, prefix, null, node, result, 
                    nextTokenType(cpc, token)!=LBRACE);
        }
        else if (node instanceof Tree.ImportModule && offset>token.getStopIndex()+1) {
            addModuleCompletions(cpc, offset, prefix, null, node, result, 
                    nextTokenType(cpc, token)!=STRING_LITERAL);
        }
        else if (node instanceof Tree.ImportPath) {
            new ImportVisitor(prefix, token, offset, node, cpc, result)
                    .visit(cpc.getRootNode());
        }
        else if (isEmptyModuleDescriptor(cpc)) {
            addModuleDescriptorCompletion(cpc, offset, prefix, result);
            addKeywordProposals(cpc, offset, prefix, result, node, null);
        }
        else if (isEmptyPackageDescriptor(cpc)) {
            addPackageDescriptorCompletion(cpc, offset, prefix, result);
            addKeywordProposals(cpc, offset, prefix, result, node, null);
        }
        else if (node instanceof Tree.TypeArgumentList && 
                token.getType()==LARGER_OP) {
            if (offset==token.getStopIndex()+1) {
                addTypeArgumentListProposal(offset, cpc, node, scope, document, result);
            }
            else if (isMemberNameProposable(offset, node, memberOp)) {
                addMemberNameProposals(offset, cpc, node, result);
            }
            else {
                return null;
            }
        }
        else {
            return null;
        }
        return result.toArray(new ICompletionProposal[result.size()]);
    }

    private static boolean atStartOfPositionalArgument(final Node node,
            final CommonToken token) {
        if (node instanceof Tree.PositionalArgumentList) {
            int type = token.getType();
            return type==LPAREN || type==COMMA;
        }
        else if (node instanceof Tree.NamedArgumentList) {
            int type = token.getType();
            return type==LBRACE || type==SEMICOLON;
        }
        else {
            return false;
        }
    }
    
    private static boolean isDescriptorPackageNameMissing(final Node node) {
        Tree.ImportPath path;
        if (node instanceof Tree.ModuleDescriptor) {
            Tree.ModuleDescriptor md = (Tree.ModuleDescriptor) node;
            path = md.getImportPath();
        }
        else if (node instanceof Tree.PackageDescriptor) {
            Tree.PackageDescriptor pd = (Tree.PackageDescriptor) node;
            path = pd.getImportPath();
        }
        else {
            return false;
        }
        return path==null || 
                path.getIdentifiers().isEmpty();
    }

    private static boolean isMemberNameProposable(int offset, Node node, 
            boolean memberOp) {
        return !memberOp &&
                node.getEndToken()!=null && 
               ((CommonToken)node.getEndToken()).getStopIndex()>=offset-2;
    }
    
    private static ICompletionProposal[] constructCompletions(final int offset, 
            final String prefix, Set<DeclarationWithProximity> set, 
            CeylonParseController cpc, Scope scope, Node node, CommonToken token, 
            boolean memberOp, IDocument doc, boolean filter, boolean inDoc,
            ProducedType requiredType, int previousTokenType) {
        
        final List<ICompletionProposal> result = new ArrayList<ICompletionProposal>();
        OccurrenceLocation ol = getOccurrenceLocation(cpc.getRootNode(), node);
        
        if (node instanceof Tree.TypeConstraint) {
            for (DeclarationWithProximity dwp: set) {
                Declaration dec = dwp.getDeclaration();
                if (isTypeParameterOfCurrentDeclaration(node, dec)) {
                    addReferenceProposal(offset, prefix, cpc, result, dwp, dec, scope, false);
                }
            }
        }
        else if ((node instanceof Tree.SimpleType || 
                node instanceof Tree.BaseTypeExpression ||
                node instanceof Tree.QualifiedTypeExpression) 
                && prefix.isEmpty() && 
                isMemberNameProposable(offset, node, memberOp)) {
            
            //TODO: we get to here after "if (is Type", which is 
            //     not quite right - we should also propose 
            //     narrowable value references in that case 
                  
            //member names we can refine
            ProducedType t=null;
            if (node instanceof Tree.Type) {
                t = ((Tree.Type) node).getTypeModel();
            }
            else if (node instanceof Tree.BaseTypeExpression) {
                ProducedReference target = ((Tree.BaseTypeExpression) node).getTarget();
                if (target!=null) {
                    t = target.getType();
                }
            }
            else if (node instanceof Tree.QualifiedTypeExpression) {
                ProducedReference target = ((Tree.BaseTypeExpression) node).getTarget();
                if (target!=null) {
                    t = target.getType();
                }
            }
            if (t!=null) {
                addRefinementProposals(offset, set, cpc, scope, node, doc,
                        filter, result, ol, t, false);
            }
            //otherwise guess something from the type
            addMemberNameProposal(offset, prefix, node, result);
        }
        else if (node instanceof Tree.TypedDeclaration && 
                !(node instanceof Tree.Variable && 
                        ((Tree.Variable) node).getType() instanceof Tree.SyntheticVariable) &&
                !(node instanceof Tree.InitializerParameter) &&
                isMemberNameProposable(offset, node, memberOp)) {
            //member names we can refine
            Tree.Type dnt = ((Tree.TypedDeclaration) node).getType();
            if (dnt!=null && dnt.getTypeModel()!=null) {
                ProducedType t = dnt.getTypeModel();
                addRefinementProposals(offset, set, cpc, scope, node, doc,
                        filter, result, ol, t, true);
            }
            //otherwise guess something from the type
            addMemberNameProposal(offset, prefix, node, result);
        }
        else {
            boolean isMember = 
                    node instanceof Tree.QualifiedMemberOrTypeExpression ||
                    node instanceof Tree.QualifiedType ||
                    node instanceof Tree.MemberLiteral && 
                            ((Tree.MemberLiteral) node).getType()!=null;
            
            if (!filter && !inDoc && !isMember) {
                addKeywordProposals(cpc, offset, prefix, result, node, ol);
                //addTemplateProposal(offset, prefix, result);
            }
            
            boolean isPackageOrModuleDescriptor = 
                    isModuleDescriptor(cpc) || isPackageDescriptor(cpc);
            for (DeclarationWithProximity dwp: set) {
                Declaration dec = dwp.getDeclaration();
                
                if (isPackageOrModuleDescriptor && !inDoc && 
                        ol!=META && (ol==null||!ol.reference)) {
                    if (!dec.isAnnotation()||!(dec instanceof Method)) {
                        continue;
                    }
                }
                
                if (isParameterOfNamedArgInvocation(scope, dwp)) {
                    if (isDirectlyInsideNamedArgumentList(cpc, node, token)) {
                        addNamedArgumentProposal(offset, prefix, cpc, result, dwp, dec, scope);
                        addInlineFunctionProposal(offset, dec, scope, node, prefix, cpc, doc, result);
                    }
                }
                
                CommonToken nextToken = getNextToken(cpc, token);
                boolean noParamsFollow = noParametersFollow(nextToken);
                if (isInvocationProposable(dwp, ol, previousTokenType) && 
                        !isQualifiedType(node) && 
                        !inDoc && noParamsFollow) {
                    for (Declaration d: overloads(dec)) {
                        ProducedReference pr = isMember ? 
                                getQualifiedProducedReference(node, d) :
                                getRefinedProducedReference(scope, d);
                        addInvocationProposals(offset, prefix, cpc, result, 
                                new DeclarationWithProximity(d, dwp), 
                                pr, scope, ol, null, isMember);
                    }
                }
                
                if (isProposable(dwp, ol, scope, node.getUnit(), requiredType, 
                            previousTokenType) && 
                        (definitelyRequiresType(ol) || noParamsFollow || 
                                dwp.getDeclaration() instanceof Functional)) {
                    if (ol==DOCLINK) {
                        addDocLinkProposal(offset, prefix, cpc, result, dwp, dec, scope);
                    }
                    else if (ol==IMPORT) {
                        addImportProposal(offset, prefix, cpc, result, dwp, dec, scope);
                    }
                    else if (ol!=null && ol.reference) {
                        if (isReferenceProposable(ol, dec)) {
                            addProgramElementReferenceProposal(offset, prefix, cpc, result, 
                                    dwp, dec, scope, isMember);
                        }
                    }
                    else {
                        addReferenceProposal(offset, prefix, cpc, result, dwp, dec, scope, isMember);
                    }
                }
                
                if (isProposable(dwp, ol, scope, node.getUnit(), requiredType, 
                            previousTokenType) && 
                        ol!=IMPORT && ol!=CASE && ol!=CATCH &&
                        isDirectlyInsideBlock(node, cpc, scope, token) && 
                        !memberOp && !filter) {
                    addForProposal(offset, prefix, cpc, result, dwp, dec);
                    addIfExistsProposal(offset, prefix, cpc, result, dwp, dec);
                    addSwitchProposal(offset, prefix, cpc, result, dwp, dec, node, doc);
                }
                
                if (isRefinementProposable(dec, ol, scope) && 
                        !memberOp && !isMember && !filter) {
                    for (Declaration d: overloads(dec)) {
                        if (d.isDefault() || d.isFormal()) {
                            addRefinementProposal(offset, d, (ClassOrInterface) scope, 
                                    node, scope, prefix, cpc, doc, result, true);
                        }
                    }
                }
            }
        }
        return result.toArray(new ICompletionProposal[result.size()]);
    }

    private static boolean isReferenceProposable(OccurrenceLocation ol,
            Declaration dec) {
        return (ol==VALUE_REF || !(dec instanceof Value)) &&
            (ol==FUNCTION_REF || !(dec instanceof Method)) &&
            (ol==ALIAS_REF || !(dec instanceof TypeAlias)) &&
            (ol==TYPE_PARAMETER_REF || !(dec instanceof TypeParameter)) &&
            //note: classes and interfaces are almost always proposable 
            //      because they are legal qualifiers for other refs
            (ol!=TYPE_PARAMETER_REF || dec instanceof TypeParameter);
    }

    private static void addRefinementProposals(int offset,
            Set<DeclarationWithProximity> set, CeylonParseController cpc,
            Scope scope, Node node, IDocument doc, boolean filter,
            final List<ICompletionProposal> result, OccurrenceLocation ol,
            ProducedType t, boolean preamble) {
        for (DeclarationWithProximity dwp: set) {
            Declaration dec = dwp.getDeclaration();
            if (isRefinementProposable(dec, ol, scope) && !filter && 
                    dec instanceof MethodOrValue) {
                MethodOrValue m = (MethodOrValue) dec;
                for (Declaration d: overloads(dec)) {
                    if ((d.isDefault() || d.isFormal()) &&
                            t.isSubtypeOf(m.getType())) {
                        try {
                            String pfx = doc.get(node.getStartIndex(), 
                                    offset-node.getStartIndex());
                            addRefinementProposal(offset, d, (ClassOrInterface) scope, 
                                    node, scope, pfx, cpc, doc, result, preamble);
                        }
                        catch (BadLocationException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    private static boolean isQualifiedType(Node node) {
        return (node instanceof Tree.QualifiedType) ||
                       (node instanceof Tree.QualifiedMemberOrTypeExpression &&
                       ((Tree.QualifiedMemberOrTypeExpression) node)
                               .getStaticMethodReference());
    }

    private static boolean noParametersFollow(CommonToken nextToken) {
        return nextToken==null ||
                //should we disable this, since a statement
                //can in fact begin with an LPAREN??
                nextToken.getType()!=LPAREN
                //disabled now because a declaration can
                //begin with an LBRACE (an Iterable type)
                /*&& nextToken.getType()!=CeylonLexer.LBRACE*/;
    }
    
    private static boolean definitelyRequiresType(OccurrenceLocation ol) {
        return ol==SATISFIES || 
                ol==OF || 
                ol==UPPER_BOUND || 
                ol==TYPE_ALIAS;
    }

    private static CommonToken getNextToken(final CeylonParseController cpc,
            CommonToken token) {
        int i = token.getTokenIndex();
        CommonToken nextToken=null;
        List<CommonToken> tokens = cpc.getTokens();
        do {
            if (++i<tokens.size()) {
                nextToken = tokens.get(i);
            }
            else {
                break;
            }
        }
        while (nextToken.getChannel()==HIDDEN_CHANNEL);
        return nextToken;
    }

    private static boolean isDirectlyInsideNamedArgumentList(
            CeylonParseController cpc, Node node, CommonToken token) {
        return node instanceof Tree.NamedArgumentList ||
                (!(node instanceof Tree.SequenceEnumeration) &&
                        occursAfterBraceOrSemicolon(token, cpc.getTokens()));
    }
    
    private static boolean isMemberOperator(Token token) {
        int type = token.getType();
        return type==MEMBER_OP || 
                type==SPREAD_OP ||
                type==SAFE_MEMBER_OP;
    }

    /*private static boolean isKeywordProposable(OccurrenceLocation ol) {
        return ol==null || ol==EXPRESSION;
    }*/
    
    private static boolean isRefinementProposable(Declaration dec, 
            OccurrenceLocation ol, Scope scope) {
        return ol==null && 
                (dec instanceof MethodOrValue || dec instanceof Class) && 
                scope instanceof ClassOrInterface &&
                ((ClassOrInterface) scope).isInheritedFromSupertype(dec);
    }
    
    private static boolean isInvocationProposable(DeclarationWithProximity dwp, 
            OccurrenceLocation ol, int previousTokenType) {
        Declaration dec = dwp.getDeclaration();
        return dec instanceof Functional && 
                previousTokenType!=IS_OP && (previousTokenType!=CASE_TYPES||ol==OF) &&
                (ol==null || 
                 ol==EXPRESSION && (!(dec instanceof Class) || !((Class) dec).isAbstract()) || 
                 ol==EXTENDS && dec instanceof Class && !((Class) dec).isFinal() && 
                         ((Class) dec).getTypeParameters().isEmpty() ||
                 ol==CLASS_ALIAS && dec instanceof Class ||
                 ol==PARAMETER_LIST && dec instanceof Method && 
                         dec.isAnnotation()) &&
                dwp.getNamedArgumentList()==null &&
                (!dec.isAnnotation() || !(dec instanceof Method) || 
                        !((Method) dec).getParameterLists().isEmpty() &&
                        !((Method) dec).getParameterLists().get(0).getParameters().isEmpty());
    }

    private static boolean isProposable(DeclarationWithProximity dwp, 
            OccurrenceLocation ol, Scope scope, Unit unit, 
            ProducedType requiredType, int previousTokenType) {
        Declaration dec = dwp.getDeclaration();
        return (ol!=EXTENDS || dec instanceof Class && !((Class) dec).isFinal()) && 
               (ol!=CLASS_ALIAS || dec instanceof Class) &&
               (ol!=SATISFIES || dec instanceof Interface) &&
               (ol!=OF || dec instanceof Class || isAnonymousClassValue(dec)) && 
               ((ol!=TYPE_ARGUMENT_LIST && ol!=UPPER_BOUND && ol!=TYPE_ALIAS && ol!=CATCH) || 
                       dec instanceof TypeDeclaration) &&
               (ol!=CATCH || isExceptionType(unit, dec)) &&
               (ol!=PARAMETER_LIST ||
                       dec instanceof TypeDeclaration || 
                       dec instanceof Method && dec.isAnnotation() || //i.e. an annotation 
                       dec instanceof Value && dec.getContainer().equals(scope)) && //a parameter ref
               (ol!=IMPORT || !dwp.isUnimported()) &&
               (ol!=CASE || isCaseOfSwitch(requiredType, dec, previousTokenType)) &&
               (previousTokenType!=IS_OP && (previousTokenType!=CASE_TYPES||ol==OF) || 
                       dec instanceof TypeDeclaration) &&
               ol!=TYPE_PARAMETER_LIST && 
               dwp.getNamedArgumentList()==null;
    }

    private static boolean isCaseOfSwitch(ProducedType requiredType,
            Declaration dec, int previousTokenType) {
        return dec instanceof TypeDeclaration && previousTokenType==IS_OP &&
                    (requiredType==null ||
                    !dec.equals(requiredType.getDeclaration()) &&
                    ((TypeDeclaration) dec).inherits(requiredType.getDeclaration())) || 
                isAnonymousClassValue(dec) && 
                    (requiredType==null ||
                    ((TypedDeclaration) dec).getType().isSubtypeOf(requiredType));
    }

    private static boolean isExceptionType(Unit unit, Declaration dec) {
        return dec instanceof TypeDeclaration && 
                   ((TypeDeclaration) dec).inherits(unit.getExceptionDeclaration());
    }

    private static boolean isAnonymousClassValue(Declaration dec) {
        return dec instanceof Value && 
                ((Value) dec).getTypeDeclaration()!=null && 
                ((Value) dec).getTypeDeclaration().isAnonymous();
    }

    private static boolean isTypeParameterOfCurrentDeclaration(Node node, Declaration d) {
        //TODO: this is a total mess and totally error-prone - figure out something better!
        return d instanceof TypeParameter && (((TypeParameter) d).getContainer()==node.getScope() ||
                        ((Tree.TypeConstraint) node).getDeclarationModel()!=null &&
                        ((TypeParameter) d).getContainer()==((Tree.TypeConstraint) node)
                                .getDeclarationModel().getContainer());
    }
    
    /*private static boolean isParameterOfNamedArgInvocation(Node node, Declaration d) {
        if (node instanceof Tree.NamedArgumentList) {
            ParameterList pl = ((Tree.NamedArgumentList) node).getNamedArgumentList()
                    .getParameterList();
            return d instanceof Parameter && pl!=null &&
                    pl.getParameters().contains(d);
        }
        else if (node.getScope() instanceof NamedArgumentList) {
            ParameterList pl = ((NamedArgumentList) node.getScope()).getParameterList();
            return d instanceof Parameter && pl!=null &&
                    pl.getParameters().contains(d);
        }
        else {
            return false;
        }
    }*/
    
    private static boolean isParameterOfNamedArgInvocation(Scope scope, 
            DeclarationWithProximity d) {
        return scope==d.getNamedArgumentList();
    }

    private static ProducedReference getQualifiedProducedReference(Node node, 
            Declaration d) {
        ProducedType pt = ((Tree.QualifiedMemberOrTypeExpression) node)
                    .getPrimary().getTypeModel();
        if (pt!=null && d.isClassOrInterfaceMember()) {
            pt = pt.getSupertype((TypeDeclaration)d.getContainer());
        }
        return d.getProducedReference(pt, Collections.<ProducedType>emptyList());
    }

    private static Set<DeclarationWithProximity> sortProposals(final String prefix, 
            final ProducedType type, Map<String, DeclarationWithProximity> proposals) {
        Set<DeclarationWithProximity> set = new TreeSet<DeclarationWithProximity>(
                new ProposalComparator(prefix, type));
        set.addAll(proposals.values());
        return set;
    }
    
    public static Map<String, DeclarationWithProximity> getProposals(Node node, 
            Scope scope, Tree.CompilationUnit cu) {
       return getProposals(node, scope, "", false, cu); 
    }
    
    private static Map<String, DeclarationWithProximity> getProposals(Node node, 
            Scope scope, String prefix, boolean memberOp, Tree.CompilationUnit cu) {
        if (node instanceof MemberLiteral) { //this case is rather ugly!
            Tree.StaticType mlt = ((Tree.MemberLiteral) node).getType();
            if (mlt!=null) {
                ProducedType type = mlt.getTypeModel();
                if (type!=null) {
                    return type.resolveAliases().getDeclaration()
                            .getMatchingMemberDeclarations(scope, prefix, 0);
                }
                else {
                    return emptyMap();
                }
            }
        }
        if (node instanceof Tree.QualifiedMemberOrTypeExpression) {
            Tree.QualifiedMemberOrTypeExpression qmte = 
                    (Tree.QualifiedMemberOrTypeExpression) node;
            ProducedType type = 
                    getPrimaryType((Tree.QualifiedMemberOrTypeExpression) node);
            if (qmte.getStaticMethodReference()) {
                type = node.getUnit().getCallableReturnType(type);
            }
            if (type!=null) {
                return type.resolveAliases().getDeclaration()
                        .getMatchingMemberDeclarations(scope, prefix, 0);
            }
            else if (qmte.getPrimary() instanceof Tree.MemberOrTypeExpression) {
                //it might be a qualified type or even a static method reference
                Declaration pmte = ((Tree.MemberOrTypeExpression) qmte.getPrimary())
                        .getDeclaration();
                if (pmte instanceof TypeDeclaration) {
                    type = ((TypeDeclaration) pmte).getType();
                    if (type!=null) {
                        return type.resolveAliases().getDeclaration()
                                .getMatchingMemberDeclarations(scope, prefix, 0);
                    }
                }
            }
            return emptyMap();
        } 
        else if (node instanceof Tree.QualifiedType) {
            ProducedType type = 
                    ((Tree.QualifiedType) node).getOuterType().getTypeModel();
            if (type!=null) {
                return type.resolveAliases().getDeclaration()
                        .getMatchingMemberDeclarations(scope, prefix, 0);
            }
            else {
                return emptyMap();
            }
        }
        else if (memberOp && 
                (node instanceof Tree.Term || 
                node instanceof Tree.DocLink)) {
            ProducedType type = null;
            if (node instanceof Tree.DocLink) {
                Declaration d = ((Tree.DocLink)node).getBase();
                if (d != null) {
                    type = getResultType(d);
                    if (type == null) {
                        type = d.getReference().getFullType();
                    }
                }
            }
            else if (node instanceof Tree.StringLiteral) {
                type = null;
            }
            else if (node instanceof Tree.Term) {
                type = ((Tree.Term)node).getTypeModel();
            } 

            
            if (type!=null) {
                return type.resolveAliases().getDeclaration()
                        .getMatchingMemberDeclarations(scope, prefix, 0);
            }
            else {
                return emptyMap();
            }
        }
        else {
            if (scope instanceof ImportList) {
                return ((ImportList) scope).getMatchingDeclarations(null, prefix, 0);
            }
            else {
                return scope==null ? //a null scope occurs when we have not finished parsing the file
                        getUnparsedProposals(cu, prefix) :
                        scope.getMatchingDeclarations(node.getUnit(), prefix, 0);
            }
        }
    }

    private static ProducedType getPrimaryType(Tree.QualifiedMemberOrTypeExpression qme) {
        ProducedType type = qme.getPrimary().getTypeModel();
        if (type==null) {
            return null;
        }
        else if (qme.getMemberOperator() instanceof Tree.SafeMemberOp) {
            return qme.getUnit().getDefiniteType(type);
        }
        else if (qme.getMemberOperator() instanceof Tree.SpreadOp) {
            return qme.getUnit().getIteratedType(type);
        }
        else {
            return type;
        }
    }
    
    private static Map<String, DeclarationWithProximity> getUnparsedProposals(Node node, 
            String prefix) {
        if (node == null) {
            return newEmptyProposals();
        }
        Unit unit = node.getUnit();
        if (unit == null) {
            return newEmptyProposals();
        }
        Package pkg = unit.getPackage();
        if (pkg == null) {
            return newEmptyProposals();
        }
        return pkg.getModule().getAvailableDeclarations(prefix);
    }
    
    private static TreeMap<String, DeclarationWithProximity> newEmptyProposals() {
        return new TreeMap<String,DeclarationWithProximity>();
    }
    
}
