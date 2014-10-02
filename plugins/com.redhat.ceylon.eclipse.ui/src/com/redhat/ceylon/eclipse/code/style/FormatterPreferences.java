package com.redhat.ceylon.eclipse.code.style;

import static com.redhat.ceylon.eclipse.code.style.CeylonFormatterConstants.*;
import ceylon.formatter.options.FormattingOptions;
import ceylon.formatter.options.IndentMode;
import ceylon.formatter.options.Mixed;
import ceylon.formatter.options.Spaces;
import ceylon.formatter.options.SparseFormattingOptions;
import ceylon.formatter.options.Tabs;
import ceylon.formatter.options.VariableOptions;
import ceylon.formatter.options.addIndentBefore_;
import ceylon.formatter.options.combinedOptions_;
import ceylon.formatter.options.stack_;
import ceylon.formatter.options.unlimited_;
import ceylon.language.Range;
import ceylon.language.Singleton;
import ceylon.language.span_;

/**
 * Wrapper around VariableOptions
 *
 */
public class FormatterPreferences {
    private static final String FALSE = "false";
    private static final String TRUE = "true";
    private VariableOptions options;
    private String space_AfterParamListClosingParen_Number;
    private String maxLineLength_Number;

    public FormatterPreferences(FormattingOptions options) {
        this.options = new VariableOptions(options);
    }

    public String get(String key) {
        String ret = null;
        switch (key) {
        case FORMATTER_indentMode:
            ret = options.getIndentMode().getClass().getSimpleName()
                    .toLowerCase();
            break;
        case FORMATTER_indentMode_Spaces_Size:
        case FORMATTER_indentMode_Tabs_Size:
            ret = new Long(options.getIndentMode().getWidthOfLevel())
                    .toString();
            break;
        case FORMATTER_indent_Blank_Lines:
            ret = booleanString(options.getIndentBlankLines());
            break;
        case FORMATTER_indent_Before_Type_Info:
            ret = options.getIndentBeforeTypeInfo().toString();
            break;
        case FORMATTER_indent_After_Specifier_Expression_Start:
            String iases = options.getIndentationAfterSpecifierExpressionStart()
                    .getClass().getSimpleName().toLowerCase();
            ret = iases.substring(0,iases.length() -1);
            break;
        case FORMATTER_space_BeforeMethodOrClassPositionalArgumentList:
            ret = booleanString(options.getSpaceBeforeMethodOrClassPositionalArgumentList());
            break;            
        case FORMATTER_space_BeforeResourceList:
            ret = booleanString(options.getSpaceBeforeResourceList());
            break;
        case FORMATTER_space_BeforeCatchVariable:
            ret = booleanString(options.getSpaceBeforeCatchVariable());
            break;
        case FORMATTER_space_AroundSatisfiesOf:
            ret = booleanString(options.getSpaceAroundSatisfiesOf());
            break;
        case FORMATTER_space_AroundImportAliasEqualsSign:
            ret = booleanString(options.getSpaceAroundImportAliasEqualsSign());
            break;
        case FORMATTER_space_AfterTypeArgOrParamListComma:
            ret = booleanString(options.getSpaceAfterTypeArgOrParamListComma());
            break;
        case FORMATTER_space_BeforeIfOpeningParenthesis:
            ret = booleanString(options.getSpaceBeforeIfOpeningParenthesis());
            break;
        case FORMATTER_space_BeforeSequenceEnumerationClosingBrace:
            ret = booleanString(options.getSpaceBeforeSequenceEnumerationClosingBrace());
            break;
        case FORMATTER_space_BeforeParamListOpeningParen:
            ret = booleanString(options.getSpaceBeforeParamListOpeningParen());
            break;
        case FORMATTER_space_BeforeParamListClosingParen:
            ret = booleanString(options.getSpaceBeforeParamListClosingParen());
            break;
        case FORMATTER_space_AfterParamListClosingParen:
            if (!(options.getSpaceAfterParamListClosingParen() instanceof ceylon.language.Integer)) {
                ret = booleanString((ceylon.language.Boolean) options.getSpaceAfterParamListClosingParen());
            } else {
                ret = TRUE;
            }
            break;
        case FORMATTER_space_AfterParamListClosingParen_Number: // if queried, only if number
            if (options.getSpaceAfterParamListClosingParen() instanceof ceylon.language.Integer) {
                ret = ((ceylon.language.Integer)options.getSpaceAfterParamListClosingParen()).toString();
                this.space_AfterParamListClosingParen_Number = ret; // save the value in case user enables again
            }
            if (this.space_AfterParamListClosingParen_Number != null) {
                ret = this.space_AfterParamListClosingParen_Number;
            } else {
                ret = "0";
            }
            break;
        case FORMATTER_space_BeforeValueIteratorClosingParenthesis:
            ret = booleanString(options.getSpaceBeforeValueIteratorClosingParenthesis());
            break;
        case FORMATTER_space_AfterSequenceEnumerationOpeningBrace:
            ret = booleanString(options.getSpaceAfterSequenceEnumerationOpeningBrace());
            break;
        case FORMATTER_space_BeforeForOpeningParenthesis:
            ret = booleanString(options.getSpaceBeforeForOpeningParenthesis());
            break;
        case FORMATTER_space_BeforeWhileOpeningParenthesis:
            ret = booleanString(options.getSpaceBeforeWhileOpeningParenthesis());
            break;
        case FORMATTER_space_BeforeAnnotationPositionalArgumentList:
            ret = booleanString(options.getSpaceBeforeAnnotationPositionalArgumentList());
            break;
        case FORMATTER_space_AfterValueIteratorOpeningParenthesis:
            ret = booleanString(options.getSpaceAfterValueIteratorOpeningParenthesis());
            break;
        case FORMATTER_space_AfterParamListOpeningParen:
            ret = booleanString(options.getSpaceAfterParamListOpeningParen());
            break;
        case FORMATTER_maxLineLength:
            if ((options.getMaxLineLength() instanceof ceylon.formatter.options.Unlimited)) {
                ret = TRUE; // unlimited
            }
            break;
        case FORMATTER_maxLineLength_Number:
            if (options.getMaxLineLength() instanceof ceylon.language.Integer) {
                ret = ((ceylon.language.Integer)options.getMaxLineLength()).toString();
                this.maxLineLength_Number = ret; // save the value in case user enables again
            }
            if (this.maxLineLength_Number != null) {
                ret = this.maxLineLength_Number;
            } else {
                ret = "0";
            }
            break;
        case FORMATTER_lineBreakStrategy:
            ret = options.getLineBreakStrategy().toString();
            break;
        case FORMATTER_lineBreaksAfterLineComment_First:
            ret = options.getLineBreaksAfterLineComment().getFirst().toString();
            break;
        case FORMATTER_lineBreaksAfterLineComment_Last:
            ret = options.getLineBreaksAfterLineComment().getLast().toString();
            break;
        case FORMATTER_lineBreaksAfterSingleComment_First:
            ret = options.getLineBreaksAfterSingleComment().getFirst().toString();
            break;
        case FORMATTER_lineBreaksAfterSingleComment_Last:
            ret =  options.getLineBreaksAfterSingleComment().getLast().toString();
            break;
        case FORMATTER_lineBreaksBeforeMultiComment_First:
            ret = options.getLineBreaksBeforeMultiComment().getFirst().toString();
            break;
        case FORMATTER_lineBreaksBeforeMultiComment_Last:
            ret = options.getLineBreaksBeforeMultiComment().getLast().toString();
            break;
        case FORMATTER_lineBreaksAfterMultiComment_First:
            ret = options.getLineBreaksAfterMultiComment().getFirst().toString();
            break;
        case FORMATTER_lineBreaksAfterMultiComment_Last:
            ret = options.getLineBreaksAfterMultiComment().getLast().toString();
            break;
        case FORMATTER_lineBreaksBeforeSingleComment_First:
            ret = options.getLineBreaksBeforeSingleComment().getFirst().toString();
            break;
        case FORMATTER_lineBreaksBeforeSingleComment_Last:
            ret = options.getLineBreaksBeforeSingleComment().getLast().toString();
            break;
        case FORMATTER_lineBreaksInTypeParameterList_First:
            ret = options.getLineBreaksInTypeParameterList().getFirst().toString();
            break;
        case FORMATTER_lineBreaksInTypeParameterList_Last:
            ret = options.getLineBreaksInTypeParameterList().getLast().toString();
            break;
        case FORMATTER_lineBreaksBeforeLineComment_First:
            ret = options.getLineBreaksBeforeLineComment().getFirst().toString();
            break;
        case FORMATTER_lineBreaksBeforeLineComment_Last:
            ret = options.getLineBreaksBeforeLineComment().getLast().toString();
            break;


        // to set up previewer only
        case FORMATTER_LINE_SPLIT:
            ret = "80";
            break;
        case FORMATTER_TAB_SIZE:
            ret = "4";
            break;
        default:
            break;
        }
        return ret;
    }

    private String booleanString(ceylon.language.Boolean b) {
        return b.booleanValue() ? TRUE : FALSE;
    }

    public void put(String key, String value) {
        switch (key) {
        case FORMATTER_indentMode:
            IndentMode indentMode = getIndentMode(value, new Long(options
                    .getIndentMode().getWidthOfLevel()).intValue());
            options.setIndentMode(indentMode);
            break;
        case FORMATTER_indentMode_Spaces_Size:
        case FORMATTER_indentMode_Tabs_Size:
            String mode = options.getIndentMode().getClass().getSimpleName()
                    .toLowerCase();
            indentMode = getIndentMode(mode, Integer.parseInt(value));
            options.setIndentMode(indentMode);
            break;
        case FORMATTER_indent_Blank_Lines:
            options.setIndentBlankLines(ceylonBoolean(value));
            break;
        case FORMATTER_indent_Before_Type_Info:
            options.setIndentBeforeTypeInfo(new ceylon.language.Integer(new Long(value)));
            break;
        case FORMATTER_indent_After_Specifier_Expression_Start:
            if ("stack".equals(value)) {
                options.setIndentationAfterSpecifierExpressionStart(stack_.get_());
            } else { // lower, not camel case
                options.setIndentationAfterSpecifierExpressionStart(addIndentBefore_.get_());
            }
            break;
        case FORMATTER_space_BeforeMethodOrClassPositionalArgumentList:
            options.setSpaceBeforeMethodOrClassPositionalArgumentList(
                ceylonBoolean(value));
            break;
        case FORMATTER_space_BeforeResourceList:
            options.setSpaceBeforeResourceList(ceylonBoolean(value));
            break;
        case FORMATTER_space_BeforeCatchVariable:
            options.setSpaceBeforeCatchVariable( ceylonBoolean(value));
            break;
        case FORMATTER_space_AroundSatisfiesOf:
            options.setSpaceAroundSatisfiesOf(ceylonBoolean(value));
            break;
        case FORMATTER_space_AroundImportAliasEqualsSign:
            options.setSpaceAroundImportAliasEqualsSign(ceylonBoolean(value));
            break;
        case FORMATTER_space_AfterTypeArgOrParamListComma:
            options.setSpaceAfterTypeArgOrParamListComma(ceylonBoolean(value));
            break;
        case FORMATTER_space_BeforeIfOpeningParenthesis:
            options.setSpaceBeforeIfOpeningParenthesis(ceylonBoolean(value));
            break;
        case FORMATTER_space_BeforeSequenceEnumerationClosingBrace:
            options.setSpaceBeforeSequenceEnumerationClosingBrace(ceylonBoolean(value));
            break;
        case FORMATTER_space_BeforeParamListOpeningParen:
            options.setSpaceBeforeParamListOpeningParen(ceylonBoolean(value));
            break;
        case FORMATTER_space_BeforeParamListClosingParen:
            options.setSpaceBeforeParamListClosingParen(ceylonBoolean(value));
            break;
        case FORMATTER_space_AfterParamListClosingParen:
            options.setSpaceAfterParamListClosingParen(ceylonBoolean(value));
            break;
        case FORMATTER_space_AfterParamListClosingParen_Number:
            int num = Integer.parseInt(value);
            if (num != 0) {
                options.setSpaceAfterParamListClosingParen(num);
            }
            break;
        case FORMATTER_space_BeforeValueIteratorClosingParenthesis:
            options.setSpaceBeforeValueIteratorClosingParenthesis(ceylonBoolean(value));
            break;
        case FORMATTER_space_AfterSequenceEnumerationOpeningBrace:
            options.setSpaceAfterSequenceEnumerationOpeningBrace(ceylonBoolean(value));
            break;
        case FORMATTER_space_BeforeForOpeningParenthesis:
            options.setSpaceBeforeForOpeningParenthesis(ceylonBoolean(value));
            break;
        case FORMATTER_space_BeforeWhileOpeningParenthesis:
            options.setSpaceBeforeWhileOpeningParenthesis(ceylonBoolean(value));
            break;
        case FORMATTER_space_BeforeAnnotationPositionalArgumentList:
            options.setSpaceBeforeAnnotationPositionalArgumentList(ceylonBoolean(value));
            break;
        case FORMATTER_space_AfterValueIteratorOpeningParenthesis:
            options.setSpaceAfterValueIteratorOpeningParenthesis(ceylonBoolean(value));
            break;
        case FORMATTER_space_AfterParamListOpeningParen:
            options.setSpaceAfterParamListOpeningParen(ceylonBoolean(value));
            break;
            
        case FORMATTER_maxLineLength:
            if (TRUE.equals(value)) {
                options.setMaxLineLength(unlimited_.get_());
            } else {
                if (this.maxLineLength_Number == null) {
                    options.setMaxLineLength(0); // TODO magic number
                } else {
                    options.setMaxLineLength(this.maxLineLength_Number);
                }
            }
            break;
        case FORMATTER_maxLineLength_Number:
            num = Integer.parseInt(value);
            if (num != 0 && num >=20 && num <=1256) { // TODO magic numbers
                options.setMaxLineLength(num);
            }
            break;
        case FORMATTER_lineBreakStrategy:
            options.setLineBreakStrategy(options.getLineBreakStrategy());  // default only
            break;
        case FORMATTER_lineBreaksAfterLineComment_First:
            num = Integer.parseInt(value);
            options.setLineBreaksAfterLineComment(
                    setFirst(options.getLineBreaksAfterLineComment(), num));
            break;
        case FORMATTER_lineBreaksAfterLineComment_Last:
            num = Integer.parseInt(value);
            options.setLineBreaksAfterLineComment(
                    setLast(options.getLineBreaksAfterLineComment(), num));
            break;
        case FORMATTER_lineBreaksAfterSingleComment_First:
            num = Integer.parseInt(value);
            options.setLineBreaksAfterSingleComment(
                    setFirst(options.getLineBreaksAfterSingleComment(), num));
            break;
        case FORMATTER_lineBreaksAfterSingleComment_Last:
            num = Integer.parseInt(value);
            options.setLineBreaksAfterSingleComment(
                    setLast(options.getLineBreaksAfterSingleComment(), num));
            break;
        case FORMATTER_lineBreaksBeforeMultiComment_First:
            num = Integer.parseInt(value);
            options.setLineBreaksBeforeMultiComment(
                    setFirst(options.getLineBreaksBeforeMultiComment(), num));
            break;
        case FORMATTER_lineBreaksBeforeMultiComment_Last:
            num = Integer.parseInt(value);
            options.setLineBreaksBeforeMultiComment(
                    setLast(options.getLineBreaksBeforeMultiComment(), num));
            break;
        case FORMATTER_lineBreaksAfterMultiComment_First:
            num = Integer.parseInt(value);
            options.setLineBreaksAfterMultiComment(
                    setFirst(options.getLineBreaksAfterMultiComment(), num));
            break;
        case FORMATTER_lineBreaksAfterMultiComment_Last:
            num = Integer.parseInt(value);
            options.setLineBreaksAfterMultiComment(
                    setLast(options.getLineBreaksAfterMultiComment(), num));
            break;
        case FORMATTER_lineBreaksBeforeSingleComment_First:
            num = Integer.parseInt(value);
            options.setLineBreaksBeforeSingleComment(
                    setFirst(options.getLineBreaksBeforeSingleComment(), num));
            break;
        case FORMATTER_lineBreaksBeforeSingleComment_Last:
            num = Integer.parseInt(value);
            options.setLineBreaksBeforeSingleComment(
                    setLast(options.getLineBreaksBeforeSingleComment(), num));
            break;
        case FORMATTER_lineBreaksInTypeParameterList_First:
            num = Integer.parseInt(value);
            options.setLineBreaksBeforeSingleComment(
                    setFirst(options.getLineBreaksBeforeSingleComment(), num));
            break;
        case FORMATTER_lineBreaksInTypeParameterList_Last:
            num = Integer.parseInt(value);
            options.setLineBreaksBeforeSingleComment(
                    setLast(options.getLineBreaksBeforeSingleComment(), num));
            break;
        case FORMATTER_lineBreaksBeforeLineComment_First:
            num = Integer.parseInt(value);
            options.setLineBreaksBeforeLineComment(
                    setFirst(options.getLineBreaksBeforeLineComment(), num));
            break;
        case FORMATTER_lineBreaksBeforeLineComment_Last:
            num = Integer.parseInt(value);
            options.setLineBreaksBeforeLineComment(
                    setLast(options.getLineBreaksBeforeLineComment(), num));
            break;

            
        default:
            break;
        }
    }

    private Range<ceylon.language.Integer> setFirst(
            Range<ceylon.language.Integer> range, int num) {
        return span_.span(range.getFirst().$getType$(), 
                new ceylon.language.Integer(num), range.getLast());
    }

    private Range<ceylon.language.Integer> setLast(
            Range<ceylon.language.Integer> range, int num) {
        return span_.span(range.getFirst().$getType$(), 
                range.getFirst(), new ceylon.language.Integer(num));
    }
    
    private ceylon.language.Boolean ceylonBoolean(String value) {
        return Boolean.parseBoolean(value)? 
                new ceylon.language.true_() : new ceylon.language.false_();
    }

    private IndentMode getIndentMode(String mode, int n1) {
        if (FORMATTER_indentMode_Spaces.equalsIgnoreCase(mode)) {
            return new Spaces(n1);
        } else if (FORMATTER_indentMode_Tabs.equalsIgnoreCase(mode)) {
            return new Tabs(n1);
        } else if (FORMATTER_indentMode_Mixed.equalsIgnoreCase(mode)) {
            return new Mixed(new Tabs(n1), new Spaces(n1));
        }
        return options.getIndentMode();
    }

    public FormattingOptions getOptions() {
        return combinedOptions_.combinedOptions(new FormattingOptions(),
                new Singleton<SparseFormattingOptions>(
                        SparseFormattingOptions.$TypeDescriptor$, options));
    }
}
