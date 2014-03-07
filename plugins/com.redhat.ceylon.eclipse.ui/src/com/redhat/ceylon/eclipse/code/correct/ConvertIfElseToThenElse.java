package com.redhat.ceylon.eclipse.code.correct;

import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.swt.graphics.Point;
import org.eclipse.text.edits.ReplaceEdit;

import com.redhat.ceylon.compiler.typechecker.tree.CustomTree.AttributeDeclaration;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.AssignOp;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Block;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.BooleanCondition;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.CompilationUnit;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Condition;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Expression;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.IfStatement;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.IsOp;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.SpecifierStatement;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Statement;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Term;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.ThenOp;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Variable;
import com.redhat.ceylon.eclipse.util.Nodes;

class ConvertIfElseToThenElse extends CorrectionProposal {
    
    ConvertIfElseToThenElse(int offset, TextChange change) {
        super("Convert to then-else", change, new Point(offset, 0));
    }
    
    static void addConvertToThenElseProposal(CompilationUnit cu, IDocument doc,
            Collection<ICompletionProposal> proposals, IFile file,
            Statement statement) {
            TextChange change = createTextChange(cu, doc, statement, file);
            if (change != null) {
                proposals.add(new ConvertIfElseToThenElse(change.getEdit().getOffset(), change));
            }
   }

    static TextChange createTextChange(CompilationUnit cu,
            IDocument doc, Statement statement, IFile file) {
        if (! (statement instanceof Tree.IfStatement)) {
            return null;
        }
        IfStatement ifStmt = (IfStatement) statement;
        if (ifStmt.getElseClause() == null) {
            return null;
        }
        
        Block ifBlock = ifStmt.getIfClause().getBlock();
        if (ifBlock.getChildren().size() != 1) {
            return null;
        }
        Block elseBlock = ifStmt.getElseClause().getBlock();
        if (elseBlock.getChildren().size() != 1) {
            return null;
        }

        Node ifBlockNode = ifBlock.getChildren().get(0);
        Node elseBlockNode = elseBlock.getChildren().get(0);
        List<Condition> conditions = ifStmt.getIfClause()
                .getConditionList().getConditions();
        if (conditions.size()!=1) {
            return null;
        }
        Condition condition = conditions.get(0);
        Integer replaceFrom = statement.getStartIndex();
        String test = removeEnclosingParentesis(getTerm(doc, condition));
        String thenStr = null;
        String elseStr = null;
        String attributeIdentifier = null;
        String operator = null;
        String action;
        if (ifBlockNode instanceof Tree.Return) {
            Tree.Return ifReturn = (Tree.Return) ifBlockNode;
            if (! (elseBlockNode instanceof Tree.Return)) {
                return null;
            }
            Tree.Return elseReturn = (Tree.Return) elseBlockNode;
            action = "return ";
            thenStr = getOperands(doc, ifReturn.getExpression());
            elseStr = getOperands(doc, elseReturn.getExpression());
        } else if (ifBlockNode instanceof Tree.SpecifierStatement) {
            SpecifierStatement ifSpecifierStmt = (Tree.SpecifierStatement) ifBlockNode;
            attributeIdentifier = getTerm(doc, ifSpecifierStmt.getBaseMemberExpression());
            operator = " = ";
            action = attributeIdentifier + operator;
            if (!(elseBlockNode instanceof Tree.SpecifierStatement)) {
                return null;
            }
            String elseId = getTerm(doc, ((Tree.SpecifierStatement)elseBlockNode).getBaseMemberExpression());
            if (!attributeIdentifier.equals(elseId)) {
                return null;
            }
            thenStr = getOperands(doc, ifSpecifierStmt.getSpecifierExpression().getExpression().getTerm());
            elseStr = getOperands(doc, ((Tree.SpecifierStatement) elseBlockNode).getSpecifierExpression().getExpression().getTerm());
        } else if (ifBlockNode instanceof Tree.ExpressionStatement) {
            if (!(elseBlockNode instanceof Tree.ExpressionStatement)) {
                return null;
            }
            Term ifOperator = ((Tree.ExpressionStatement) ifBlockNode).getExpression().getTerm();
            if (!(ifOperator instanceof AssignOp)) {
                return null;
            } 
            Term elseOperator = ((Tree.ExpressionStatement) elseBlockNode).getExpression().getTerm();
            if (!(elseOperator instanceof AssignOp)) {
                return null;
            } 
            AssignOp ifAssign = (AssignOp) ifOperator;
            AssignOp elseAssign = (AssignOp) elseOperator;
            attributeIdentifier = getTerm(doc, ifAssign.getLeftTerm());
            String elseId = getTerm(doc, elseAssign.getLeftTerm());
            if (!attributeIdentifier.equals(elseId)) {
                return null;
            }
            
            operator = " = ";
            action = attributeIdentifier + operator;
            thenStr = getOperands(doc, ifAssign.getRightTerm());
            elseStr = getOperands(doc, elseAssign.getRightTerm());
        } else {
            return null;
        }
        
        if (attributeIdentifier != null) {
            Statement prevStatement = findPreviousStatement(cu, doc, statement);
            if (prevStatement != null) {
                if (prevStatement instanceof AttributeDeclaration) {
                    AttributeDeclaration attrDecl = (AttributeDeclaration) prevStatement;
                    if (attributeIdentifier.equals(getTerm(doc, attrDecl.getIdentifier()))) {
                        action = removeSemiColon(getTerm(doc, attrDecl)) + operator;
                        replaceFrom = attrDecl.getStartIndex();
                    }
                }
            }
        }
        
        if (condition instanceof Tree.ExistsCondition) {
            Tree.ExistsCondition existsCond = (Tree.ExistsCondition) condition;
            Variable variable = existsCond.getVariable();
            if (thenStr.equals(getTerm(doc, variable.getIdentifier()))) {
                Expression existsExpr = variable.getSpecifierExpression().getExpression();
                test = getTerm(doc, existsExpr);
                thenStr = null;
            } else {
                return null; //Disabling because type narrowing does not work with then.
            }            
        } else if (! (condition instanceof Tree.BooleanCondition)) {
            return null; //Disabling because type narrowing does not work with then.
        } else if (((BooleanCondition)condition).getExpression().getTerm() instanceof IsOp){
            return null; //Disabling because type narrowing does not work with then.
        }
        
        StringBuilder replace = new StringBuilder();
        replace.append(action).append(test);
        if (thenStr != null) {
            replace.append(" then ").append(thenStr);
        }
        if (!elseStr.equals("null")) {
            replace.append(" else ").append(elseStr);
        }
        replace.append(";");

        TextChange change = new TextFileChange("Convert To then-else", file);
//      TextChange change = new DocumentChange("Convert To then-else", doc);
        change.setEdit(new ReplaceEdit(replaceFrom, 
                statement.getStopIndex() - replaceFrom + 1, 
                replace.toString()));
        return change;
    }
    
    private static String getOperands(IDocument doc, Term operand) {
        String term = getTerm(doc, operand);
        if (hasLowerPrecedenceThenElse(operand)) {
            return "(" + term + ")";
        }
        return term;
    }

    private static boolean hasLowerPrecedenceThenElse(Term operand) {
        Term node;
        if (operand instanceof Tree.Expression) {
            Tree.Expression exp = (Tree.Expression) operand;
            node = exp.getTerm();
        } else {
            node = operand;
        }
        return node instanceof Tree.DefaultOp || 
                node instanceof ThenOp || 
                node instanceof AssignOp;
    }

    private static String removeSemiColon(String term) {
        if (term.endsWith(";")) {
            return term.substring(0, term.length() - 1);
        }
        return term;
    }

    private static Statement findPreviousStatement(CompilationUnit cu, IDocument doc, 
            Statement statement) {
        try {
            int previousLineNo = doc.getLineOfOffset(statement.getStartIndex());
            while (previousLineNo > 1) {
                previousLineNo--;
                IRegion lineInfo = doc.getLineInformation(previousLineNo);
                String prevLine = doc.get(lineInfo.getOffset(), lineInfo.getLength());
                Matcher m = Pattern.compile("(\\s*)\\w+").matcher(prevLine);
                if (m.find()) {
                    int whitespaceLen = m.group(1).length();
                    Node node = Nodes.findNode(cu, lineInfo.getOffset() + whitespaceLen, 
                            lineInfo.getOffset() + whitespaceLen + 1);
                    return Nodes.findStatement(cu, node);
                }
            }
        } catch (BadLocationException e) {
            e.printStackTrace();
        } 
        return null;
    }

    private static String removeEnclosingParentesis(String s) {
        if (s.charAt(0) == '(' && s.charAt(s.length() - 1) == ')') {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
    
    private static String getTerm(IDocument doc, Node node) {
        try {
            return doc.get(node.getStartIndex(), 
                    node.getStopIndex() - node.getStartIndex() + 1);
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }
    }
}