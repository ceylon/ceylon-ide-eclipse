/********************************************************************************
 * Copyright (c) 2011-2017 Red Hat Inc. and/or its affiliates and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-1.0
 ********************************************************************************/
package org.eclipse.ceylon.ide.eclipse.code.html;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;

import org.eclipse.jface.text.TextPresentation;


/**
 * Reads the text contents from a reader of HTML contents and translates
 * the tags or cut them out.
 * <p>
 * Moved into this package from <code>org.eclipse.jface.internal.text.revisions</code>.</p>
 */
public class HTML2TextReader extends SubstitutionTextReader {

    private static final String EMPTY_STRING= ""; //$NON-NLS-1$
    private static final Map<String,String> fgEntityLookup;
    private static final Set<String> fgTags;

    static {

        fgTags= new HashSet<String>();
        fgTags.add("b"); //$NON-NLS-1$
        fgTags.add("br"); //$NON-NLS-1$
        fgTags.add("br/"); //$NON-NLS-1$
        fgTags.add("div"); //$NON-NLS-1$
        fgTags.add("del"); //$NON-NLS-1$
        fgTags.add("h1"); //$NON-NLS-1$
        fgTags.add("h2"); //$NON-NLS-1$
        fgTags.add("h3"); //$NON-NLS-1$
        fgTags.add("h4"); //$NON-NLS-1$
        fgTags.add("h5"); //$NON-NLS-1$
        fgTags.add("p"); //$NON-NLS-1$
        fgTags.add("dl"); //$NON-NLS-1$
        fgTags.add("dt"); //$NON-NLS-1$
        fgTags.add("dd"); //$NON-NLS-1$
        fgTags.add("li"); //$NON-NLS-1$
        fgTags.add("ul"); //$NON-NLS-1$
        fgTags.add("pre"); //$NON-NLS-1$
        fgTags.add("head"); //$NON-NLS-1$

        fgEntityLookup= new HashMap<String,String>(7);
        fgEntityLookup.put("lt", "<"); //$NON-NLS-1$ //$NON-NLS-2$
        fgEntityLookup.put("gt", ">"); //$NON-NLS-1$ //$NON-NLS-2$
        fgEntityLookup.put("nbsp", " "); //$NON-NLS-1$ //$NON-NLS-2$
        fgEntityLookup.put("amp", "&"); //$NON-NLS-1$ //$NON-NLS-2$
        fgEntityLookup.put("circ", "^"); //$NON-NLS-1$ //$NON-NLS-2$
        fgEntityLookup.put("tilde", "~"); //$NON-NLS-2$ //$NON-NLS-1$
        fgEntityLookup.put("quot", "\"");         //$NON-NLS-1$ //$NON-NLS-2$
    }

    private int fCounter= 0;
    private TextPresentation fTextPresentation;
    private int fBold= 0;
    private int fBoldStartOffset= -1;
    private int fStrikeout= 0;
    private int fStrikeoutStartOffset= -1;
    private boolean fInParagraph= false;
    private boolean fIsPreformattedText= false;
    private boolean fIgnore= false;
    private boolean fHeaderDetected= false;

    /**
     * Transforms the HTML text from the reader to formatted text.
     *
     * @param reader the reader
     * @param presentation If not <code>null</code>, formattings will be applied to
     * the presentation.
    */
    public HTML2TextReader(Reader reader, TextPresentation presentation) {
        super(new PushbackReader(reader));
        fTextPresentation= presentation;
    }

    public int read() throws IOException {
        int c= super.read();
        if (c != -1)
            ++ fCounter;
        return c;
    }

    private void setBold(int offset, int length) {
        if (fTextPresentation != null && offset >= 0 && length > 0) {
            fTextPresentation.addStyleRange(new StyleRange(offset, length, null, null, SWT.BOLD));
        }
    }

    private void setStrikeout(int offset, int length) {
        if (fTextPresentation != null && offset >= 0 && length > 0) {
            StyleRange styleRange= new StyleRange(offset, length, null, null, SWT.NORMAL);
            styleRange.strikeout= true;
            fTextPresentation.addStyleRange(styleRange);
        }
    }

    private void setBoldAndStrikeOut(int offset, int length) {
        if (fTextPresentation != null && offset >= 0 && length > 0) {
            StyleRange styleRange= new StyleRange(offset, length, null, null, SWT.BOLD);
            styleRange.strikeout= true;
            fTextPresentation.addStyleRange(styleRange);
        }
    }

    protected void startBold() {
        if (fBold == 0) {
            fBoldStartOffset= fCounter;
            if (fStrikeout > 0) {
                setStrikeout(fStrikeoutStartOffset, fCounter - fStrikeoutStartOffset);
            }
        }
        ++fBold;
    }

    protected void stopBold() {
        --fBold;
        if (fBold == 0) {
            if (fStrikeout == 0) {
                setBold(fBoldStartOffset, fCounter - fBoldStartOffset);
            } else {
                setBoldAndStrikeOut(fBoldStartOffset, fCounter - fBoldStartOffset);
                fStrikeoutStartOffset= fCounter;
            }
            fBoldStartOffset= -1;
        }
    }

    protected void startStrikeout() {
        if (fStrikeout == 0) {
            fStrikeoutStartOffset= fCounter;
            if (fBold > 0) {
                setBold(fBoldStartOffset, fCounter - fBoldStartOffset);
            }
        }
        ++fStrikeout;
    }

    protected void stopStrikeout() {
        --fStrikeout;
        if (fStrikeout == 0) {
            if (fBold == 0) {
                setStrikeout(fStrikeoutStartOffset, fCounter - fStrikeoutStartOffset);
            } else {
                setBoldAndStrikeOut(fStrikeoutStartOffset, fCounter - fStrikeoutStartOffset);
                fBoldStartOffset= fCounter;
            }
            fStrikeoutStartOffset= -1;
        }
    }

    protected void startPreformattedText() {
        fIsPreformattedText= true;
        setSkipWhitespace(false);
    }

    protected void stopPreformattedText() {
        fIsPreformattedText= false;
        setSkipWhitespace(true);
    }


    /*
     * @see org.eclipse.jdt.internal.ui.text.SubstitutionTextReader#computeSubstitution(int)
     */
    protected String computeSubstitution(int c) throws IOException {

        if (c == '<')
            return  processHTMLTag();
        else if (fIgnore)
            return EMPTY_STRING;
        else if (c == '&')
            return processEntity();
        else if (fIsPreformattedText)
            return processPreformattedText(c);

        return null;
    }

    private String html2Text(String html) {

        if (html == null || html.length() == 0)
            return EMPTY_STRING;

        html= html.toLowerCase();

        String tag= html;
        if ('/' == tag.charAt(0))
            tag= tag.substring(1);

        if (!fgTags.contains(tag))
            return EMPTY_STRING;


        if ("pre".equals(html)) { //$NON-NLS-1$
            startPreformattedText();
            return EMPTY_STRING;
        }

        if ("/pre".equals(html)) { //$NON-NLS-1$
            stopPreformattedText();
            return EMPTY_STRING;
        }

        if (fIsPreformattedText)
            return EMPTY_STRING;

        if ("b".equals(html)) { //$NON-NLS-1$
            startBold();
            return EMPTY_STRING;
        }

        if ("del".equals(html)) { //$NON-NLS-1$
            startStrikeout();
            return EMPTY_STRING;
        }

        if ((html.length() > 1 && html.charAt(0) == 'h' && Character.isDigit(html.charAt(1))) || "dt".equals(html)) { //$NON-NLS-1$
            startBold();
            return EMPTY_STRING;
        }

        if ("dl".equals(html)) //$NON-NLS-1$
            return LINE_DELIM;

        if ("dd".equals(html)) //$NON-NLS-1$
            return "\t"; //$NON-NLS-1$

        if ("li".equals(html)) //$NON-NLS-1$
            // FIXME: this hard-coded prefix does not work for RTL languages, see https://bugs.eclipse.org/bugs/show_bug.cgi?id=91682
            return LINE_DELIM + "\t- ";

        if ("/b".equals(html)) { //$NON-NLS-1$
            stopBold();
            return EMPTY_STRING;
        }

        if ("/del".equals(html)) { //$NON-NLS-1$
            stopStrikeout();
            return EMPTY_STRING;
        }

        if ("p".equals(html))  { //$NON-NLS-1$
            fInParagraph= true;
            return LINE_DELIM;
        }

        if ("br".equals(html) || "br/".equals(html) || "div".equals(html)) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            return LINE_DELIM;

        if ("/p".equals(html))  { //$NON-NLS-1$
            boolean inParagraph= fInParagraph;
            fInParagraph= false;
            return inParagraph ? EMPTY_STRING : LINE_DELIM;
        }

        if ((html.startsWith("/h") && html.length() > 2 && Character.isDigit(html.charAt(2))) || "/dt".equals(html)) { //$NON-NLS-1$ //$NON-NLS-2$
            stopBold();
            return LINE_DELIM;
        }

        if ("/dd".equals(html)) //$NON-NLS-1$
            return LINE_DELIM;

        if ("head".equals(html) && !fHeaderDetected) { //$NON-NLS-1$
            fHeaderDetected= true;
            fIgnore= true;
            return EMPTY_STRING;
        }

        if ("/head".equals(html) && fHeaderDetected && fIgnore) { //$NON-NLS-1$
            fIgnore= false;
            return EMPTY_STRING;
        }

        return EMPTY_STRING;
    }

    /*
     * A '<' has been read. Process a html tag
     */
    private String processHTMLTag() throws IOException {

        StringBuffer buf= new StringBuffer();
        int ch;
        do {

            ch= nextChar();

            while (ch != -1 && ch != '>') {
                buf.append(Character.toLowerCase((char) ch));
                ch= nextChar();
                if (ch == '"'){
                    buf.append(Character.toLowerCase((char) ch));
                    ch= nextChar();
                    while (ch != -1 && ch != '"'){
                        buf.append(Character.toLowerCase((char) ch));
                        ch= nextChar();
                    }
                }
                if (ch == '<' && !isInComment(buf)) {
                    unread(ch);
                    return '<' + buf.toString();
                }
            }

            if (ch == -1)
                return null;

            if (!isInComment(buf) || isCommentEnd(buf)) {
                break;
            }
            // unfinished comment
            buf.append((char) ch);
        } while (true);

        return html2Text(buf.toString());
    }

    private static boolean isInComment(StringBuffer buf) {
        return buf.length() >= 3 && "!--".equals(buf.substring(0, 3)); //$NON-NLS-1$
    }

    private static boolean isCommentEnd(StringBuffer buf) {
        int tagLen= buf.length();
        return tagLen >= 5 && "--".equals(buf.substring(tagLen - 2)); //$NON-NLS-1$
    }

    private String processPreformattedText(int c) {
        if  (c == '\r' || c == '\n')
            fCounter++;
        return null;
    }


    private void unread(int ch) throws IOException {
        ((PushbackReader) getReader()).unread(ch);
    }

    protected String entity2Text(String symbol) {
        if (symbol.length() > 1 && symbol.charAt(0) == '#') {
            int ch;
            try {
                if (symbol.charAt(1) == 'x') {
                    ch= Integer.parseInt(symbol.substring(2), 16);
                } else {
                    ch= Integer.parseInt(symbol.substring(1), 10);
                }
                return EMPTY_STRING + (char)ch;
            } catch (NumberFormatException e) {
            }
        } else {
            String str= (String) fgEntityLookup.get(symbol);
            if (str != null) {
                return str;
            }
        }
        return "&" + symbol + ";"; // not found //$NON-NLS-1$ //$NON-NLS-2$
    }

    /*
     * A '&' has been read. Process a entity
     */
    private String processEntity() throws IOException {
        StringBuffer buf= new StringBuffer();
        int ch= nextChar();
        while (Character.isLetterOrDigit((char)ch) || ch == '#') {
            buf.append((char) ch);
            ch= nextChar();
        }

        if (ch == ';')
            return entity2Text(buf.toString());

        buf.insert(0, '&');
        if (ch != -1)
            buf.append((char) ch);
        return buf.toString();
    }
}
