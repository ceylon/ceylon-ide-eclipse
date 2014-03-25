package com.redhat.ceylon.eclipse.code.editor;

import java.util.List;

import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CommonToken;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;
import org.eclipse.jface.text.DocumentCommand;
import org.eclipse.jface.text.IAutoEditStrategy;
import org.eclipse.jface.text.IDocument;

import com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer;
import com.redhat.ceylon.compiler.typechecker.parser.CeylonParser;

public class CeylonAutoEditStrategy implements IAutoEditStrategy {

    @Override
    public void customizeDocumentCommand(IDocument document, 
            DocumentCommand command) {
        ANTLRStringStream stream = 
                new ANTLRStringStream(document.get());
        CeylonLexer lexer = new CeylonLexer(stream);
        CommonTokenStream ts = new CommonTokenStream(lexer);
        ts.fill();
        try {
            new CeylonParser(ts).compilationUnit();
        } 
        catch (RecognitionException e) {}
        List<CommonToken> tokens = ts.getTokens();
        new AutoEdit(document, tokens, command)
                .customizeDocumentCommand();
    }

}
