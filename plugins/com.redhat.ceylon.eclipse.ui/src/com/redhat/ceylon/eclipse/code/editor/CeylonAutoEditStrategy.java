package com.redhat.ceylon.eclipse.code.editor;

import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.ASTRING_LITERAL;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.AVERBATIM_STRING;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.CHAR_LITERAL;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.LINE_COMMENT;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.MULTI_COMMENT;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.STRING_END;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.STRING_LITERAL;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.STRING_MID;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.STRING_START;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.VERBATIM_STRING;
import static com.redhat.ceylon.eclipse.code.parse.CeylonSourcePositionLocator.getTokenIndexAtCharacter;
import static java.lang.Character.isWhitespace;
import static org.eclipse.core.runtime.Platform.getPreferencesService;
import static org.eclipse.ui.texteditor.AbstractDecoratedTextEditorPreferenceConstants.EDITOR_SPACES_FOR_TABS;
import static org.eclipse.ui.texteditor.AbstractDecoratedTextEditorPreferenceConstants.EDITOR_TAB_WIDTH;

import java.util.List;

import org.antlr.runtime.CommonToken;
import org.eclipse.core.runtime.preferences.IPreferencesService;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentCommand;
import org.eclipse.jface.text.IAutoEditStrategy;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.TextUtilities;

import com.redhat.ceylon.eclipse.code.parse.CeylonParseController;

public class CeylonAutoEditStrategy implements IAutoEditStrategy {
	
    //TODO: when pasting inside an existing string literal, should
    //      we automagically escape unescaped quotes in the pasted
    //      text?
    
	CeylonEditor editor;
	
	public CeylonAutoEditStrategy(CeylonEditor editor) {
		this.editor = editor;
	}
    
    public void customizeDocumentCommand(IDocument doc, DocumentCommand cmd) {
    	
        //Note that IMP's Correct Indentation sends us a tab
    	//character at the start of each line of selected
    	//text. This is amazingly sucky because it's very
    	//difficult to distinguish Correct Indentation from
    	//an actual typed tab.
    	//Note also that typed tabs are replaced with spaces
    	//before this method is called if the spacesfortabs 
    	//setting is enabled.
        if (cmd.doit == false) {
            return;
        }
        
        //cmd.length>0 means we are replacing or deleting text
        else if (cmd.text!=null && cmd.length==0) { 
            if (cmd.text.isEmpty()) {
                //workaround for a really annoying bug where we 
                //get sent "" instead of "\t" or "    " by IMP
                //reconstruct what we would have been sent 
                //without the bug
                if (getIndentWithSpaces()) {
                    int overhang = getPrefix(doc, cmd).length() % getIndentSpaces();
                    cmd.text = getDefaultIndent().substring(overhang);
                }
                else {
                    cmd.text = "\t";
                }
                smartIndentOnKeypress(doc, cmd);
            }
            else if (cmd.text.length()==1 && isLineEnding(doc, cmd.text)) {
            	//a typed newline
                smartIndentAfterNewline(doc, cmd);
            }
            else if (cmd.text.length()==1 || 
                    //when spacesfortabs is enabled, we get 
                    //sent spaces instead of a tab
                    getIndentWithSpaces() && isIndent(getPrefix(doc, cmd))) {
            	//anything that might represent a single 
                //keypress or a Correct Indentation
                smartIndentOnKeypress(doc, cmd);
            }
        }
        
        closeOpening(doc, cmd);
    }

	private static String[][] FENCES = {
			{ "'", "'" },
			{ "\"", "\"" },
			{ "`", "`" },
			{ "<", ">" },
			{ "(", ")" },
			{ "[", "]" }};

	public void closeOpening(IDocument doc, DocumentCommand cmd) {
		
		try {
			// TODO: improve this, check the surrounding token type!
			if (doc.getChar(cmd.offset - 1) == '\\') {
				return;
			}
		} 
		catch (BadLocationException e) {}

		String current = cmd.text;
		String opening = null;
		String closing = null;

		boolean found=false;
		for (String[] type : FENCES) {
			if (type[0].equals(current) || 
					type[1].equals(current)) {
				opening = type[0];
				closing = type[1];
				found = true;
				break;
			}
		}
		
		if (found) {
		
			if (current.equals(closing)) {
				//typed character is a closing fence
				try {
					// skip one ahead if next char is already a closing fence
					if (skipClosingFence(doc, cmd, closing)) {
						cmd.text = "";
						cmd.shiftsCaret = false;
						cmd.caretOffset = cmd.offset + 1;
						return;
					}
				} 
				catch (BadLocationException e) {}
			}

            if (current.equals(opening) && (!isQuotedOrCommented(doc,cmd.offset) || 
            		isGraveAccentCharacterInStringLiteral(cmd.offset, opening) ||
            		isWikiRefInAnnotatedStringLiteral(cmd.offset, opening))) {
				//typed character is an opening fence
				if (closeOpeningFence(doc, cmd, opening, closing)) {
					//add a closing fence
				    cmd.shiftsCaret = false;
                    cmd.caretOffset = cmd.offset + 1;
				    if (isGraveAccentCharacterInStringLiteral(cmd.offset, opening)) {
				        try {
				            if (cmd.offset>1 &&
				                    doc.get(cmd.offset-1,1).equals("`") &&
				                    !doc.get(cmd.offset-2,1).equals("`")) {
				                cmd.text += "``";
				            }
				        } 
				        catch (BadLocationException e) {}
				    }
				    else if (opening.equals("\"")) {
				        try {
				            if (cmd.offset<=1 ||
				                    !doc.get(cmd.offset-2,1).equals("\"")) {
                                cmd.text += closing;
                            }
				            else if (cmd.offset>1 &&
				                    doc.get(cmd.offset-2,2).equals("\"\"") &&
				                    !(cmd.offset>2 && doc.get(cmd.offset-3,1).equals("\""))) {
				                cmd.text += "\"\"\"";
				            }
				        } 
				        catch (BadLocationException e) {}
				    }
				    else {
				        cmd.text += closing;
				    }
				}
			}

		}
	}

    private boolean skipClosingFence(IDocument doc, DocumentCommand cmd,
            String closing) throws BadLocationException {
        return String.valueOf(doc.getChar(cmd.offset)).equals(closing);
    }

	private boolean closeOpeningFence(IDocument doc, DocumentCommand cmd,
			String opening, String closing) {
		boolean closeOpening;
		if (opening.equals(closing)) { 
			closeOpening = count(opening)%2==0;
		}
		else { 
			closeOpening = count(opening)>=count(closing);
		}

		if (opening.equals("<")) {
			// only close angle brackets if it's after a UIdentifier
			// if(a< -> don't close
			// if(Some< -> close
			// A< -> close
			int currOfset = cmd.offset - 1;
			char currChar;
			try {
				while (Character.isAlphabetic(currChar = doc.getChar(currOfset))) {
					currOfset--;
				}
				currChar = doc.getChar(currOfset + 1);
				if (!Character.isUpperCase(currChar)) {
					closeOpening = false;
				}
			} catch (BadLocationException e) {
			}
		}
		return closeOpening;
	}

	private String getPrefix(IDocument doc, DocumentCommand cmd) {
		try {
			int lineOffset = doc.getLineInformationOfOffset(cmd.offset).getOffset();
			return doc.get(lineOffset, cmd.offset-lineOffset) + cmd.text;
		} 
		catch (BadLocationException e) {
			return cmd.text;
		}
	}
    
    public boolean isIndent(String text) {
        if (!text.isEmpty() && 
        		text.length() % getIndentSpaces()==0) {
            for (char c: text.toCharArray()) {
                if (c!=' ') return false;
            }
            return true;
        }
        else {
            return false;
        }
    }
    
    private void smartIndentAfterNewline(IDocument d, DocumentCommand c) {
        if (c.offset==-1 || d.getLength()==0) {
            return;
        }

        try {
            //if (end > start) {
                indentNewLine(d, c);
            //}
        } 
        catch (BadLocationException bleid ) {
            bleid.printStackTrace();
        }
    }

    private void smartIndentOnKeypress(IDocument d, DocumentCommand c) {
        if (c.offset==-1 || d.getLength()==0) {
            return;
        }
         
        try {
            adjustIndentOfCurrentLine(d, c);
        }
        catch (BadLocationException ble) {
            ble.printStackTrace();
        }
    }
    
    private boolean isStringOrCommentContinuation(int offset) {
        int type = tokenType(offset);
        return type==STRING_LITERAL || 
        		type==STRING_MID ||
        		type==STRING_START ||
        		type==STRING_END ||
        		type==VERBATIM_STRING ||
        		type==ASTRING_LITERAL ||
        		type==AVERBATIM_STRING ||
        		type==MULTI_COMMENT;
    }

    private boolean isQuotedOrCommented(IDocument d, int offset) {
    	int type = tokenType(offset);
    	return type==STRING_LITERAL ||
    			type==CHAR_LITERAL || 
    			type==STRING_MID ||
    			type==STRING_START ||
    			type==STRING_END ||
    			type==VERBATIM_STRING ||
    			type==ASTRING_LITERAL ||
    			type==AVERBATIM_STRING ||
    			type==LINE_COMMENT ||
    			type==MULTI_COMMENT;
    }
    
    private boolean isGraveAccentCharacterInStringLiteral(int offset, String fence) {
        if ("`".equals(fence)) {
            int type = tokenType(offset);
            return type == STRING_LITERAL ||
                    type == STRING_START ||
                    type == STRING_END ||
                    type == STRING_MID;
        }
        return false;
    }

    private boolean isWikiRefInAnnotatedStringLiteral(int offset, String fence) {
       return ("[".equals(fence) && tokenType(offset) == ASTRING_LITERAL); 
    }
    
    private boolean isMultilineCommentStart(int offset, IDocument d) {
    	CommonToken token = token(offset);
    	if (token==null) return false;
        try {
			return token.getType()==MULTI_COMMENT && 
					!token.getText().endsWith("*/") &&
					d.getLineOfOffset(offset)+1==token.getLine();
		} 
        catch (BadLocationException e) {
			return false;
		}
    }

    public int tokenType(int offset) {
    	CommonToken token = token(offset);
		return token==null ? -1 : token.getType();
    }
    
	public CommonToken token(int offset) {
		if (editor==null) return null;
        CeylonParseController pc = editor.getParseController();
        List<CommonToken> tokens = pc.getTokens();
		if (tokens!=null) {
    		if (tokens.size()>1) {
    			if (pc.getDocument().getLength()==offset) { //at very end of file
    				//check to see if last token is an
    				//unterminated string or comment
    				CommonToken token = tokens.get(tokens.size()-2);
    				int type = token==null ? -1 : token.getType();
    				if ((type==STRING_LITERAL ||
    						type==STRING_END ||
    						type==ASTRING_LITERAL) && 
    						(!token.getText().endsWith("\"") ||
    						token.getText().length()==1) ||
    						(type==VERBATIM_STRING || type==AVERBATIM_STRING) && 
    						(!token.getText().endsWith("\"\"\"")||
    						token.getText().length()==3) ||
    						(type==MULTI_COMMENT) && 
    						(!token.getText().endsWith("*/")||
    						token.getText().length()==2) ||
    						type==LINE_COMMENT) {
    					return token;
    				}
    			}
    			else {
    				int tokenIndex = getTokenIndexAtCharacter(tokens, offset);
    				if (tokenIndex>=0) {
    					CommonToken token = tokens.get(tokenIndex);
    					if (token.getStartIndex()<offset) {
    						return token;
    					}
    				}
    			}
    		}
        }
		return null;
	}

    /*private boolean isLineComment(int offset) {
        IEditorPart editor = Util.getCurrentEditor();
        if (editor instanceof CeylonEditor) {
            CeylonParseController pc = ((CeylonEditor) editor).getParseController();
            if (pc.getTokens()==null) return false;
            int tokenIndex = getTokenIndexAtCharacter(pc.getTokens(), offset);
            if (tokenIndex>=0) {
  	            CommonToken token = pc.getTokens().get(tokenIndex);
                return token!=null 
                		&& token.getType()==CeylonLexer.LINE_COMMENT
                		&& token.getStartIndex()<=offset;
            }
        }
        return false;
    }*/

	private int getStringIndent(IDocument d, int offset) {
		CommonToken token = token(offset);
		if (token!=null) {
			int type = token.getType();
			if ((type==STRING_LITERAL || 
					type==STRING_MID ||
					type==STRING_START ||
					type==STRING_END ||
					type==ASTRING_LITERAL ||
					type==VERBATIM_STRING ||
					type==AVERBATIM_STRING) &&
					token.getStartIndex()<offset) {
				return getStartOfQuotedText(token, type);
			}
		}
		return -1;
	}

	private int getStartOfQuotedText(CommonToken token, int type) {
		int quote = type==VERBATIM_STRING ||
				type==AVERBATIM_STRING ? 3 : 1;
		return token.getCharPositionInLine()+quote;
	}

    private void adjustIndentOfCurrentLine(IDocument d, DocumentCommand c)
            throws BadLocationException {
        switch (c.text.charAt(0)) {
            case '}':
                reduceIndentOfCurrentLine(d, c);
                break;
            case '{':
            case '\t':
                if (isStringOrCommentContinuation(c.offset)) {
                    shiftToBeginningOfStringOrCommentContinuation(d, c);
                }
                else {
                    fixIndentOfCurrentLine(d, c);
                }
                break;
            default:
                //when spacesfortabs is enabled, we get sent spaces instead of a tab
                if (getIndentWithSpaces() && isIndent(getPrefix(d, c))) {
                    if (isStringOrCommentContinuation(c.offset)) {
                        shiftToBeginningOfStringOrCommentContinuation(d, c);
                    }
                    else {
                        fixIndentOfCurrentLine(d, c);
                    }
                }
        }
    }

    private void shiftToBeginningOfStringOrCommentContinuation(IDocument d, DocumentCommand c)
            throws BadLocationException {
        //int start = getStartOfCurrentLine(d, c);
        int end = getEndOfCurrentLine(d, c);
        int loc = firstEndOfWhitespace(d, c.offset/*start*/, end);
        if (loc>c.offset) {
            c.length = 0;
            c.text = "";
            c.caretOffset = loc;
        }
    }
    
    private void indentNewLine(IDocument d, DocumentCommand c)
            throws BadLocationException {
        int stringIndent = getStringIndent(d, c.offset);
        int start = getStartOfCurrentLine(d, c);
        if (stringIndent>=0) {
            StringBuilder sb = new StringBuilder();
            for (int i=0; i<stringIndent; i++) {
                char ws = d.getChar(start+i)=='\t' ? 
                        '\t' : ' ';
                sb.append(ws);
            }
            c.text = c.text + sb.toString();
        }
        else {
            char lastNonWhitespaceChar = getPreviousNonWhitespaceCharacter(d, c.offset-1);
            char endOfLastLineChar = getPreviousNonWhitespaceCharacterInLine(d, c.offset-1);
            char startOfNewLineChar = getNextNonWhitespaceCharacterInLine(d, c.offset);

            //let's attempt to account for line ending comments in determining if it is a
            //continuation, but only by looking at the previous line
            //TODO: make this handle line ending comments further back
            char lastNonWhitespaceCharAccountingForComments = getLastNonWhitespaceCharacterInLine(d, start, c.offset);
            if (lastNonWhitespaceCharAccountingForComments!='\n') {
                lastNonWhitespaceChar = lastNonWhitespaceCharAccountingForComments;
                endOfLastLineChar = lastNonWhitespaceCharAccountingForComments;
            }
            
            StringBuilder buf = new StringBuilder(c.text);
            boolean closeBrace = count("{")>count("}");
            appendIndent(d, start, getEndOfCurrentLine(d, c), 
                    startOfNewLineChar, endOfLastLineChar, lastNonWhitespaceChar, 
                    false, closeBrace, buf, c); //false, because otherwise it indents after annotations, which I guess we don't want
            if (buf.length()>2 && buf.charAt(buf.length()-1)=='}') {
                String hanging = d.get(c.offset, getEndOfCurrentLine(d, c)-c.offset); //stuff after the { on the current line
                buf.insert(c.caretOffset-c.offset, hanging);
                c.length = hanging.length();
            }
            c.text = buf.toString();
            
        }
        if (isMultilineCommentStart(c.offset, d)) {
        	c.shiftsCaret=false;
        	c.caretOffset=c.offset+c.text.length();
        	c.text = c.text + c.text + "*/";
        }
    }
    
    int count(String token) {
    	int count = 0;
    	if (editor!=null) {
    		CeylonParseController pc = editor.getParseController();
    		if (pc.getTokens()!=null) {
    			for (CommonToken tok: pc.getTokens()) {
    				if (tok.getText().equals(token)) {
    					count++;
    				}
    			}
    		}
    	}
    	return count;
//    	int result = 0;
//    	for (int i=0; i<string.length(); i++) {
//    		char c = string.charAt(i);
//			if (c==ch) result++;
//			if (c=='\\') i++;
//    	}
//    	return result;
    }
    
    private void fixIndentOfCurrentLine(IDocument d, DocumentCommand c)
            throws BadLocationException {
        int start = getStartOfCurrentLine(d, c);
        int end = getEndOfCurrentLine(d, c);
        //System.out.println(d.get(start, end-start));
        int endOfWs = firstEndOfWhitespace(d, start, end);
        if (c.offset<endOfWs || 
                c.offset==start && c.shiftsCaret==false) { //Test for IMP's "Correct Indent"
            if (start==0) { //Start of file
                c.text="";
                c.offset=start;
                c.length=0;
            }
            else {
                int endOfPrev = getEndOfPreviousLine(d, c);
                int startOfPrev = getStartOfPreviousLine(d, c);
                char endOfLastLineChar = getLastNonWhitespaceCharacterInLine(d, startOfPrev, endOfPrev);
                char lastNonWhitespaceChar = endOfLastLineChar=='\n' ? 
                        getPreviousNonWhitespaceCharacter(d, startOfPrev) : endOfLastLineChar;
                char startOfCurrentLineChar = c.text.equals("{") ? 
                        '{' : getNextNonWhitespaceCharacter(d, start, end);
                //TODO: improve this 'cos should check tabs vs spaces
                boolean correctContinuation = endOfWs-start!=firstEndOfWhitespace(d, startOfPrev, endOfPrev)-startOfPrev
                		/*&& !isLineComment(endOfPrev)*/ && startOfCurrentLineChar!='\n';
                
                StringBuilder buf = new StringBuilder();
                appendIndent(d, startOfPrev, endOfPrev, startOfCurrentLineChar, endOfLastLineChar,
                        lastNonWhitespaceChar, correctContinuation, false, buf, c);
                if (c.text.equals("{")) {
                    buf.append("{");
                }
                c.text = buf.toString();
                c.offset=start;
                c.length=endOfWs-start;
                //System.out.println(c.text);
            }
        }
    }

    private void appendIndent(IDocument d, int startOfPrev, int endOfPrev,
            char startOfCurrentLineChar, char endOfLastLineChar, char lastNonWhitespaceChar,
            boolean correctContinuation, boolean closeBraces, StringBuilder buf, DocumentCommand c)
            		throws BadLocationException {
        boolean isContinuation = startOfCurrentLineChar!='{' && startOfCurrentLineChar!='}' &&
                lastNonWhitespaceChar!=';' && lastNonWhitespaceChar!='}' && lastNonWhitespaceChar!='{';
        boolean isOpening = endOfLastLineChar=='{' && startOfCurrentLineChar!='}';
        boolean isClosing = startOfCurrentLineChar=='}' && endOfLastLineChar!='{';
        appendIndent(d, isContinuation, isOpening, isClosing, correctContinuation, 
                startOfPrev, endOfPrev, closeBraces, buf, c);
    }

    protected void reduceIndent(DocumentCommand c) {
        int spaces = getIndentSpaces();
        if (endsWithSpaces(c.text, spaces)) {
            c.text = c.text.substring(0, c.text.length()-spaces);
        }
        else if (c.text.endsWith("\t")) {
            c.text = c.text.substring(0, c.text.length()-1);
        }
    }

    private void reduceIndentOfCurrentLine(IDocument d, DocumentCommand c)
            throws BadLocationException {
        int spaces = getIndentSpaces();
        if (endsWithSpaces(d.get(c.offset-spaces, spaces),spaces)) {
            c.offset = c.offset-spaces;
            c.length = spaces;
        }
        else if (d.get(c.offset-1,1).equals("\t")) {
            c.offset = c.offset-1;
            c.length = 1;
        }
    }

    private void decrementIndent(StringBuilder buf, String indent)
            throws BadLocationException {
        int spaces = getIndentSpaces();
        if (endsWithSpaces(indent,spaces)) {
            buf.setLength(buf.length()-spaces);
        }
        else if (endsWithTab(indent)) {
            buf.setLength(buf.length()-1);
        }
    }

    private int getStartOfPreviousLine(IDocument d, DocumentCommand c)
            throws BadLocationException {
        return getStartOfPreviousLine(d, c.offset);
    }

    private int getStartOfPreviousLine(IDocument d, int offset) 
            throws BadLocationException {
        int os;
        int line = d.getLineOfOffset(offset);
        do {
            os = d.getLineOffset(--line);
        }
        while (isStringOrCommentContinuation(os));
        return os;
    }
    
    /*private int getStartOfNextLine(IDocument d, int offset) 
            throws BadLocationException {
        return d.getLineOffset(d.getLineOfOffset(offset)+1);
    }*/
    
    private void appendIndent(IDocument d, boolean isContinuation, boolean isBeginning,
            boolean isEnding,  boolean correctContinuation, int start, int end,
            boolean closeBraces, StringBuilder buf, DocumentCommand c) 
            		throws BadLocationException {
        String indent = getIndent(d, start, end, isContinuation&&!correctContinuation);
        buf.append(indent);
        if (isBeginning) {
            //increment the indent level
            incrementIndent(buf, indent);
            if (closeBraces) {
            	c.shiftsCaret=false;
            	c.caretOffset=c.offset+buf.length();
            	String newlineChar;
            	if (end<d.getLength()) {
            		newlineChar = d.get(end, 1);
            	}
            	else if (start>0) {
            		newlineChar = d.get(start-1, 1);
            	}
            	else {
            		newlineChar = System.lineSeparator(); //TODO: is this right?
            	}
				buf.append(newlineChar)
            		.append(indent)
            		.append('}');
            }
        }
        else if (isContinuation&&correctContinuation) {
            incrementIndent(buf, indent);
            incrementIndent(buf, indent);
        }
        if (isEnding) {
            decrementIndent(buf, indent);
            if (isContinuation) decrementIndent(buf, indent);
        }
    }

    private String getIndent(IDocument d, int start, int end, boolean isUncorrectedContinuation) 
            throws BadLocationException {
        if (!isUncorrectedContinuation) {
            while (true) {
                if (start==0) break;
                //We're searching for an earlier line whose 
                //immediately preceding line ends cleanly 
                //with a {, }, or ; or which itelf starts 
                //with a }. We will use that to infer the 
                //indent for the current line
                char ch1 = getNextNonWhitespaceCharacterInLine(d, start);
                if (ch1=='}') break;
                int end1 = getEndOfPreviousLine(d, start);
                int start1 = getStartOfPreviousLine(d, start);
                char ch = getLastNonWhitespaceCharacterInLine(d, start1, end1);
                if (ch==';' || ch=='{' || ch=='}') break;
                end = end1;
                start=start1;
            }
        }
        while (isStringOrCommentContinuation(start)) {
            end = getEndOfPreviousLine(d, start);
            start = getStartOfPreviousLine(d, start);
        }
        int endOfWs = firstEndOfWhitespace(d, start, end);
        return d.get(start, endOfWs-start);
    }

    private char getLastNonWhitespaceCharacterInLine(IDocument d, int offset, int end) 
            throws BadLocationException {
        char result = '\n'; //ahem, ugly null!
        int commentDepth=0;
        for (;offset<end; offset++) {
            char ch = d.getChar(offset);
            char next = d.getLength()>offset+1 ? 
                    d.getChar(offset+1) : '\n'; //another ugly null
            if (commentDepth==0) {
                if (ch=='/') {
                    if (next=='*') {
                        commentDepth++;
                    }
                    else if  (next=='/') {
                        return result;
                    }
                }
                else if (!isWhitespace(ch)) {
                    result=ch;
                }
            }
            else {
                if (ch=='*' && next=='/') {
                    commentDepth--;
                }
            }
        }
        return result;
    }
    
    private void incrementIndent(StringBuilder buf, String indent) {
        int spaces = getIndentSpaces();
        if (endsWithSpaces(indent,spaces)) {
            for (int i=1; i<=spaces; i++) {
                buf.append(' ');                            
            }
        }
        else if (endsWithTab(indent)) {
            buf.append('\t');
        }
        else {
            initialIndent(buf);
        }
    }

    private boolean endsWithTab(String indent) {
        return !indent.isEmpty() &&
                indent.charAt(indent.length()-1)=='\t';
    }
    
    private char getPreviousNonWhitespaceCharacter(IDocument d, int offset)
            throws BadLocationException {
        for (;offset>=0; offset--) {
            String ch = d.get(offset,1);
            if (!isWhitespace(ch.charAt(0)) && 
            	!isQuotedOrCommented(d,offset)) {
                return ch.charAt(0);
            }
        }
        return '\n';
    }

    private char getPreviousNonWhitespaceCharacterInLine(IDocument d, int offset)
            throws BadLocationException {
        //TODO: handle end-of-line comments
        for (;offset>=0; offset--) {
            String ch = d.get(offset,1);
            if (!isWhitespace(ch.charAt(0)) && 
            	!isQuotedOrCommented(d,offset) ||
                    isLineEnding(d, ch)) {
                return ch.charAt(0);
            }
        }
        return '\n';
    }

    private char getNextNonWhitespaceCharacterInLine(IDocument d, int offset)
            throws BadLocationException {
        for (;offset<d.getLength(); offset++) {
            String ch = d.get(offset,1);
            if (!isWhitespace(ch.charAt(0)) && 
                !isQuotedOrCommented(d,offset) ||
                    isLineEnding(d, ch)) {
                return ch.charAt(0);
            }
        }
        return '\n';
    }

    private char getNextNonWhitespaceCharacter(IDocument d, int offset, int end)
            throws BadLocationException {
        for (;offset<end; offset++) {
            String ch = d.get(offset,1);
            if (!isWhitespace(ch.charAt(0)) && 
                !isQuotedOrCommented(d,offset)) {
                return ch.charAt(0);
            }
        }
        return '\n';
    }

    private static void initialIndent(StringBuilder buf) {
        //guess an initial indent level
        if (getIndentWithSpaces()) {
            int spaces = getIndentSpaces();
            for (int i=1; i<=spaces; i++) {
                buf.append(' ');                          
            }
        }
        else {
            buf.append('\t');
        }
    }

    private int getStartOfCurrentLine(IDocument d, DocumentCommand c) 
            throws BadLocationException {
        int p = c.offset == d.getLength() ? c.offset-1 : c.offset;
        return d.getLineInformationOfOffset(p).getOffset();
    }
    
    private int getEndOfCurrentLine(IDocument d, DocumentCommand c) 
            throws BadLocationException {
        int p = c.offset == d.getLength() ? c.offset-1 : c.offset;
        IRegion lineInfo = d.getLineInformationOfOffset(p);
        return lineInfo.getOffset() + lineInfo.getLength();
    }
    
    private int getEndOfPreviousLine(IDocument d, DocumentCommand c) 
            throws BadLocationException {
        return getEndOfPreviousLine(d, c.offset);
    }

    private int getEndOfPreviousLine(IDocument d, int offset) 
            throws BadLocationException {
        int p = offset == d.getLength() ? offset-1 : offset;
        IRegion lineInfo = d.getLineInformation(d.getLineOfOffset(p)-1);
        return lineInfo.getOffset() + lineInfo.getLength();
    }
    
    private boolean endsWithSpaces(String string, int spaces) {
        if (string.length()<spaces) return false;
        for (int i=1; i<=spaces; i++) {
            if (string.charAt(string.length()-i)!=' ') {
                return false;
            }
        }
        return true;
    }
 
    private static int getIndentSpaces() {
        IPreferencesService ps = getPreferencesService();
        return ps==null ? 4 :
            ps.getInt("org.eclipse.ui.editors", EDITOR_TAB_WIDTH, 4, null);
    }
    
    private static boolean getIndentWithSpaces() {
        IPreferencesService ps = getPreferencesService();
        return ps==null ? false :
            ps.getBoolean("org.eclipse.ui.editors", EDITOR_SPACES_FOR_TABS, false, null);
    }
    
    public static String getDefaultIndent() {
        StringBuilder result = new StringBuilder();
        initialIndent(result);
        return result.toString();
    }
    
    /**
     * Returns the first offset greater than <code>offset</code> and smaller than
     * <code>end</code> whose character is not a space or tab character. If no such
     * offset is found, <code>end</code> is returned.
     *
     * @param d the document to search in
     * @param offset the offset at which searching start
     * @param end the offset at which searching stops
     * @return the offset in the specified range whose character is not a space or tab
     * @exception BadLocationException if position is an invalid range in the given document
     */
    private int firstEndOfWhitespace(IDocument d, int offset, int end)
            throws BadLocationException {
        while (offset < end) {
            char ch= d.getChar(offset);
            if (ch!=' ' && ch!='\t') {
                return offset;
            }
            offset++;
        }
        return end;
    }

    private boolean isLineEnding(IDocument doc, String text) {
        String[] delimiters = doc.getLegalLineDelimiters();
        if (delimiters != null) {
            return TextUtilities.endsWith(delimiters, text)!=-1;
        }
        return false;
    }
    
}
