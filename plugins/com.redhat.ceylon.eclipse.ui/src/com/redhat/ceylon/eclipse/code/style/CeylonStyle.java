package com.redhat.ceylon.eclipse.code.style;

import java.io.File;
import java.io.IOException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension4;

import ceylon.formatter.options.LineBreak;
import ceylon.formatter.options.Spaces;
import ceylon.formatter.options.SparseFormattingOptions;
import ceylon.formatter.options.Tabs;
import ceylon.formatter.options.crlf_;
import ceylon.formatter.options.lf_;
import ceylon.formatter.options.os_;
import ceylon.formatter.options.saveProfile_;

import com.redhat.ceylon.common.config.CeylonConfig;
import com.redhat.ceylon.common.config.ConfigWriter;
import com.redhat.ceylon.eclipse.code.style.FormatterProfileManager.Profile;
import com.redhat.ceylon.eclipse.ui.CeylonPlugin;
import com.redhat.ceylon.eclipse.util.Indents;

import static com.redhat.ceylon.eclipse.code.style.CeylonFormatterConstants.*;


/**
 * Utility query and update for style options
 * 
 */
public class CeylonStyle {

    private CeylonStyle() {
        // only static methods
    }

    private static final String PREF_STYLE_FORMATTER_PROFILE = "formattool.profile";

    public static String getFormatterProfile(IProject project) {
        CeylonConfig config = CeylonConfig.createFromLocalDir(project
                .getLocation().toFile());
        if (config != null
                && config.isOptionDefined(PREF_STYLE_FORMATTER_PROFILE)) {
            return config.getOption(PREF_STYLE_FORMATTER_PROFILE);
        } else {
            return DEFAULT_PROFILE_NAME;
        }
    }

    public static boolean setFormatterProfile(IProject project, String name) {
        CeylonConfig options = CeylonConfig.createFromLocalDir(project
                .getLocation().toFile());
        options.setOption(PREF_STYLE_FORMATTER_PROFILE, name);
        return writeProjectConfig(project, options);
    }

    public static void writeProfileToFile(Profile profile, File file)
            throws CoreException {
        try {
            saveProfile_.saveProfile(
                    profile.getSettings(),
                    profile.getName(),
                    file.isDirectory() ? file.getAbsolutePath() : file
                            .getParent());
        } catch (Exception e) {
            throw new CoreException(new Status(IStatus.ERROR,
                    CeylonPlugin.PLUGIN_ID, e.getMessage()));
        }
    }

    /**
     * Creates {@link SparseFormattingOptions} that respect whitespace-relevant settings:
     * <ul>
     * <li>{@link SparseFormattingOptions#getIndentMode() indentMode} from spaces-for-tabs and editor-tab-width</li>
     * <li>{@link SparseFormattingOptions#getLineBreak() lineBreak} from document newline character</li>
     * </ul>
     */
    public static SparseFormattingOptions getEclipseWsOptions(IDocument document) {
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
                /* indentMode = */ Indents.getIndentWithSpaces() ? 
                        new Spaces(Indents.getIndentSpaces()) : 
                        new Tabs(Indents.getIndentSpaces()),
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
    
    private static boolean writeProjectConfig(IProject project,
            CeylonConfig options) {
        if (project != null) {
            try {
                ConfigWriter.write(options, new File(project.getLocation()
                        .toFile(), ".ceylon/config"));
                return true;
            } catch (IOException e) {
                CeylonPlugin.getInstance().getLog()
                        .log(new Status(IStatus.ERROR, 
                                CeylonPlugin.PLUGIN_ID, e.getMessage()));
                return false;
            }
        } else {
            return false;
        }
    }
}
