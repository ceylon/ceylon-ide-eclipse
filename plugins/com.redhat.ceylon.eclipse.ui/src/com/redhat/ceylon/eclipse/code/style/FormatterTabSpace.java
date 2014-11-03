package com.redhat.ceylon.eclipse.code.style;

import static com.redhat.ceylon.eclipse.code.style.CeylonFormatterConstants.FORMATTER_space_AfterParamListClosingParen;
import static com.redhat.ceylon.eclipse.code.style.CeylonFormatterConstants.FORMATTER_space_AfterParamListOpeningParen;
import static com.redhat.ceylon.eclipse.code.style.CeylonFormatterConstants.FORMATTER_space_AfterSequenceEnumerationOpeningBrace;
import static com.redhat.ceylon.eclipse.code.style.CeylonFormatterConstants.FORMATTER_space_AfterTypeArgOrParamListComma;
import static com.redhat.ceylon.eclipse.code.style.CeylonFormatterConstants.FORMATTER_space_AfterValueIteratorOpeningParenthesis;
import static com.redhat.ceylon.eclipse.code.style.CeylonFormatterConstants.FORMATTER_space_AroundImportAliasEqualsSign;
import static com.redhat.ceylon.eclipse.code.style.CeylonFormatterConstants.FORMATTER_space_AroundSatisfiesOf;
import static com.redhat.ceylon.eclipse.code.style.CeylonFormatterConstants.FORMATTER_space_BeforeAnnotationPositionalArgumentList;
import static com.redhat.ceylon.eclipse.code.style.CeylonFormatterConstants.FORMATTER_space_BeforeMethodOrClassPositionalArgumentList;
import static com.redhat.ceylon.eclipse.code.style.CeylonFormatterConstants.FORMATTER_space_AfterControlStructureKeyword;
import static com.redhat.ceylon.eclipse.code.style.CeylonFormatterConstants.FORMATTER_space_BeforeParamListClosingParen;
import static com.redhat.ceylon.eclipse.code.style.CeylonFormatterConstants.FORMATTER_space_BeforeParamListOpeningParen;
import static com.redhat.ceylon.eclipse.code.style.CeylonFormatterConstants.FORMATTER_space_BeforeSequenceEnumerationClosingBrace;
import static com.redhat.ceylon.eclipse.code.style.CeylonFormatterConstants.FORMATTER_space_BeforeValueIteratorClosingParenthesis;
import static com.redhat.ceylon.eclipse.code.style.CeylonFormatterConstants.FORMATTER_forceSpaceAroundBinaryOp;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;

public class FormatterTabSpace extends FormatterTabPage {

    private final String PREVIEW =
            "import ceylon.file {\n"
            + "    pp=parsePath,\n"
            + "    File,\n"
            + "    Reader\n"
            + "}\n"
            + "import ceylon.collection {\n"
            + "    LL=LinkedList\n"
            + "}\n"
            + "\n"
            + "by (\"Someone\")\n"
            + "throws (`class Exception`, \"If anything goes wrong\")\n"
            + "shared class WithLineNumbersReader(Reader r)\n"
            + "        satisfies Reader & Iterable<String,Null> {\n"
            + "    \n"
            + "    LL<String> lines = LL<String>();\n"
            + "    variable Integer lineNum = 0;\n"
            + "    \n"
            + "    close() => r.close();\n"
            + "    shared actual Byte[] readBytes(Integer max) {\n"
            + "        throw AssertionError(\"Can't read bytes from line-oriented reader\");\n"
            + "    }\n"
            + "    shared actual Iterator<String> iterator() => lines.iterator();\n"
            + "    shared actual String? readLine() {\n"
            + "        if (exists line = r.readLine()) {\n"
            + "            value ret = lineNum.string + \"t\" + line;\n"
            + "            lines.add(ret);\n"
            + "            lineNum++;\n"
            + "            return ret;\n"
            + "        } else {\n"
            + "            return null;\n"
            + "        }\n"
            + "    }\n"
            + "}\n"
            + "\n"
            + "void run() {\n"
            + "    assert (is File f = pp(process.arguments.first else nothing).resource);\n"
            + "    try (r = WithLineNumbersReader(f.Reader())) {\n"
            + "        variable String? line = \"\";\n"
            + "        while (line exists) {\n"
            + "            line = r.readLine();\n"
            + "        }\n"
            + "        for (l in r) {\n"
            + "            print(l);\n"
            + "        }\n"
            + "    } catch (Exception e) {\n"
            + "        e.printStackTrace();\n"
            + "    }\n"
            + "    print({ \"Here\", \"have\", \"an\", \"iterable\", \"enumeration\" });\n"
            + "    value hollowCubeVol = w*h*d - iW*iH*iD;\n"
            + "}\n";

    private CeylonPreview ceylonPreview;

    public FormatterTabSpace(FormatterModifyProfileDialog modifyDialog,
            FormatterPreferences workingValues) {
        super(modifyDialog, workingValues);
    }

    @Override
    protected void doCreatePreferences(Composite composite, int numColumns) {

        final Group generalGroup = createGroup(numColumns, composite,
                "Spacing Options");
        createCheckboxPref(generalGroup, numColumns, "Space before positional argument list",
                FORMATTER_space_BeforeMethodOrClassPositionalArgumentList, FALSE_TRUE);
        createCheckboxPref(generalGroup, numColumns, "Space before annotation positional argument list",
                FORMATTER_space_BeforeAnnotationPositionalArgumentList, FALSE_TRUE);
        
        createCheckboxPref(generalGroup, numColumns, "Spaces around '&&' and '|' in 'satisfies' and 'of'",
                FORMATTER_space_AroundSatisfiesOf, FALSE_TRUE);
        createCheckboxPref(generalGroup, numColumns, "Spaces around '=' in 'import' alias",
                FORMATTER_space_AroundImportAliasEqualsSign, FALSE_TRUE);
        createCheckboxPref(generalGroup, numColumns, "Space after type argument or parameter list comma",
                FORMATTER_space_AfterTypeArgOrParamListComma, FALSE_TRUE);
        createCheckboxPref(generalGroup, numColumns, "Space after control structure keyword ('if', 'for', etc.)",
                FORMATTER_space_AfterControlStructureKeyword, FALSE_TRUE);
        createCheckboxPref(generalGroup, numColumns, "Force spaces around binary operator",
                FORMATTER_forceSpaceAroundBinaryOp, FALSE_TRUE);
  
        final Group parameter = createGroup(numColumns, composite, 
                "Parameter Lists");
        createCheckboxPref(parameter, 2, "Space before opening '('",
                FORMATTER_space_BeforeParamListOpeningParen, FALSE_TRUE);
        createCheckboxPref(parameter, 2, "Space after opening '('",
                FORMATTER_space_AfterParamListOpeningParen, FALSE_TRUE);
        createCheckboxPref(parameter, 2, "Space before closing ')'",
                FORMATTER_space_BeforeParamListClosingParen, FALSE_TRUE);  
        /*final CheckboxPreference saplcp =*/ 
        createCheckboxPref(parameter, 2, "Space after closing ')'",
                FORMATTER_space_AfterParamListClosingParen, FALSE_TRUE);

        /*final NumberPreference saplcpn = createNumberPref(generalGroup, numColumns, 
                "Detailed, " + MINIMUM_SPACE_AFTER_SHIFT + " to " + MAXIMUM_SPACE_AFTER_SHIFT,
                FORMATTER_space_AfterParamListClosingParen_Number, MINIMUM_SPACE_AFTER_SHIFT, MAXIMUM_SPACE_AFTER_SHIFT);
        
        Observer o = new Observer() {
            public void update(Observable o, Object arg) {
                saplcp.updateWidget();
                saplcpn.updateWidget();
            }
        };
        saplcp.addObserver(o);
        saplcpn.addObserver(o);*/
        
        final Group enumeration = createGroup(numColumns, composite, 
                "Iterable Enumerations");
        createCheckboxPref(enumeration, 1, "Space after opening '{'",
                FORMATTER_space_AfterSequenceEnumerationOpeningBrace, FALSE_TRUE);         
        createCheckboxPref(enumeration, 1, "Space before closing '}'",
                FORMATTER_space_BeforeSequenceEnumerationClosingBrace, FALSE_TRUE);
 
        final Group iterator = createGroup(numColumns, composite, 
                "Iterator in 'for' Loop");
        createCheckboxPref(iterator, 1, "Space after opening '('",
                FORMATTER_space_AfterValueIteratorOpeningParenthesis, FALSE_TRUE);  
        createCheckboxPref(iterator, 1, "Space before closing ')'",
                FORMATTER_space_BeforeValueIteratorClosingParenthesis, FALSE_TRUE);
        
    }

    @Override
    public void initializePage() {
        ceylonPreview.setPreviewText(PREVIEW);
    }

    @Override
    protected CeylonPreview doCreateCeylonPreview(Composite parent) {
        ceylonPreview = new CeylonPreview(this.workingValues, parent);
        return ceylonPreview;
    }

    @Override
    protected void doUpdatePreview() {
        super.doUpdatePreview();
        ceylonPreview.setPreviewText(PREVIEW);
        ceylonPreview.update();
    }
}
