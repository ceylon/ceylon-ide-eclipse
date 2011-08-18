package com.redhat.ceylon.eclipse.imp.parser;

import org.antlr.runtime.CommonToken;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.tree.CommonTree;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.imp.editor.ModelTreeNode;
import org.eclipse.imp.parser.IParseController;
import org.eclipse.imp.parser.ISourcePositionLocator;

import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Identifier;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;

/**
 * NOTE:  This version of the ISourcePositionLocator is for use when the Source
 * Position Locator and corresponding Parse Controller are generated separately from
 * a corresponding set of LPG grammar templates and possibly in the absence
 * of the lexer, parser, and AST-related types that would be generated from
 * those templates.  To enable compilation of the Locator and Controller,
 * dummy types have been defined as member types of the Controller in place
 * of possibly missing lexer, parser, and AST-related types.  This version
 * of the Node Locator refers to some of those types.  When those types
 * are replaced by real implementation types, the Locator must be modified
 * to refer to those.  Apart from statements to import needed types from
 * the Parse Controller, this SourcePositionLocator is the same as that used
 * with LPG.
 * @see the corresponding ParseController type
 * 
 * @author Stan Sutton (suttons@us.ibm.com)
 * @since May 15, 2007
 */
public class CeylonSourcePositionLocator implements ISourcePositionLocator {

  private CeylonParseController parseController;

  public CeylonSourcePositionLocator(IParseController parseController) {
    this.parseController= (CeylonParseController) parseController;
  }

  private final class NodeVisitor extends Visitor {
    
    private NodeVisitor(int fStartOffset, int fEndOffset) {
    this.fStartOffset = fStartOffset;
    this.fEndOffset = fEndOffset;
  }

  private Node node;
  private int fStartOffset;
  private int fEndOffset;
  
  public Node getNode() {
    return node;
  }
    
  @Override
  public void visit(Tree.StaticMemberOrTypeExpression that) {
    if (inBounds(that.getIdentifier())) {
        node = that;
    }
    else {
        super.visit(that);
    }
  }
  
  @Override
  public void visit(Tree.SimpleType that) {
    if (inBounds(that.getIdentifier())) {
        node = that;
    }
    else {
        super.visit(that);
    }
  }
  
  @Override
  public void visit(Tree.Declaration that) {
    if (inBounds(that.getIdentifier())) {
        node = that;
    }
    else {
        super.visit(that);
    }
  }
  
    private boolean inBounds(Node that) {
      CommonTree antlrTreeNode = that.getAntlrTreeNode();

      int tokenStartIndex = antlrTreeNode.getTokenStartIndex();
      CommonToken tokenStart = (CommonToken) getTokenStream().get(tokenStartIndex);
      int nodeStartOffset = tokenStart.getStartIndex();
      
      int tokenStopIndex = antlrTreeNode.getTokenStopIndex();
      CommonToken tokenStop = (CommonToken) getTokenStream().get(tokenStopIndex);
      int nodeEndOffset = tokenStop.getStopIndex();
      
      // If this node contains the span of interest then record it and continue visiting the subtrees
      return nodeStartOffset <= fStartOffset && nodeEndOffset >= fEndOffset;
    }
    
  }

  private CommonTokenStream getTokenStream()
  {
    return (CommonTokenStream) parseController.getParser().getTokenStream();

  }
  
  public Object findNode(Object ast, int offset) {
    return findNode(ast, offset, offset);
  }

  public Object findNode(Object ast, int startOffset, int endOffset) {
    NodeVisitor visitor = new NodeVisitor(startOffset, endOffset);
    //System.out.println("Looking for node spanning offsets " + startOffset + " => " + endOffset);
    
    if (!(ast instanceof Tree.CompilationUnit))
      return ast;
  
    Tree.CompilationUnit cu = (Tree.CompilationUnit) ast;
    
    cu.visit(visitor);
    System.out.println("Selected node: " + visitor.getNode());
    return visitor.getNode();
  }

  
  public int getStartOffset(Object node) {
    CommonToken token = getToken(node);
    return token==null?0:token.getStartIndex();
  }

  CommonToken getToken(Object node) {
    if (node instanceof ModelTreeNode) {
      ModelTreeNode treeNode = (ModelTreeNode) node;
      return (CommonToken) ((Node) treeNode.getASTNode()).getAntlrTreeNode().getToken();
    }
    if (node instanceof CommonToken) {
      return ((CommonToken) node);
    }
    if (node instanceof Tree.Declaration) {
      Tree.Declaration decl = (Tree.Declaration) node;
      Identifier identifier = decl.getIdentifier();
      if (identifier != null)
      {
        return (CommonToken) identifier.getAntlrTreeNode().getToken();
      }
    }
    if (node instanceof Node) {    
      Node n = (Node) node;
      CommonTokenStream tokenStream = (CommonTokenStream) parseController.getParser().getTokenStream();
      return (CommonToken) tokenStream.get(n.getAntlrTreeNode().getTokenStartIndex());      
    }    
    System.out.println("Unknown node type !!!!");
    return null;
  }
  

  public int getEndOffset(Object node) {
    CommonToken token = getToken(node);
    return token == null ? 0 : token.getStopIndex();
  }

  public int getLength(Object node) {
    return getEndOffset(node) - getStartOffset(node);
  }

  public IPath getPath(Object node) {
    if (parseController.getPath() != null) {
      return parseController.getPath();
    }
    return new Path("");
  }
}
