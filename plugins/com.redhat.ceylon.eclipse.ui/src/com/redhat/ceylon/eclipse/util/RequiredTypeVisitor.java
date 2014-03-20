package com.redhat.ceylon.eclipse.util;

import static com.redhat.ceylon.eclipse.util.Types.getResultType;

import java.util.List;

import org.antlr.runtime.CommonToken;
import org.antlr.runtime.Token;

import com.redhat.ceylon.compiler.typechecker.model.Functional;
import com.redhat.ceylon.compiler.typechecker.model.Parameter;
import com.redhat.ceylon.compiler.typechecker.model.ParameterList;
import com.redhat.ceylon.compiler.typechecker.model.ProducedReference;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.tree.NaturalVisitor;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.NamedArgumentList;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.SwitchCaseList;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;

class RequiredTypeVisitor extends Visitor 
        implements NaturalVisitor {
    
    private Node node;
    private ProducedType requiredType = null;
    private ProducedType finalResult = null;
    private ProducedReference namedArgTarget = null;
    private Token token;
    
    public ProducedType getType() {
        return finalResult;
    }
    
    public RequiredTypeVisitor(Node node, Token token) {
        this.node = node;
        this.token = token;
    }
    
    @Override
    public void visitAny(Node that) {
        if (node==that) {
            finalResult=requiredType;  
        }
        super.visitAny(that);
    }
    
    @Override
    public void visit(Tree.InvocationExpression that) {
        ProducedType ort = requiredType;
        ProducedReference onat = namedArgTarget;
        Tree.PositionalArgumentList pal = that.getPositionalArgumentList();
        if (pal!=null) {
            int pos;
            pos = pal.getPositionalArguments().size();
            for (int i=0; i<pos; i++) {
                Tree.PositionalArgument pa=pal.getPositionalArguments().get(i);
                if (token!=null) {
                    if (pa.getStartIndex()>((CommonToken) token).getStopIndex()) {
                        pos = i;
                        break;
                    }
                }
                else {
                    if (node.getStartIndex()>=pa.getStartIndex() && 
                            node.getStopIndex()<=pa.getStopIndex()) {
                        pos = i;
                        break;
                    }
                }
            }
            ProducedReference pr = getTarget(that);
            if (pr!=null) {
                List<Parameter> params = getParameters(pr);
                if (params!=null && params.size()>pos) {
                    Parameter param = params.get(pos);
                    requiredType = pr.getTypedParameter(param).getFullType();
                    if (param.isSequenced()) {
                        requiredType = that.getUnit().getIteratedType(requiredType);
                    }
                }
            }
        }
        NamedArgumentList nal = that.getNamedArgumentList();
        if (nal!=null) {
            namedArgTarget = getTarget(that);
            if (namedArgTarget!=null) {
                List<Parameter> params = getParameters(namedArgTarget);
                if (params!=null && !params.isEmpty()) {
                    Parameter param = params.get(params.size()-1);
                    if (param.isSequenced()) {
                        requiredType = namedArgTarget.getTypedParameter(param).getFullType();
                        requiredType = that.getUnit().getIteratedType(requiredType);
                    }
                }
            }
        }
        super.visit(that);
        requiredType = ort;
        namedArgTarget = onat;
    }

    private static ProducedReference getTarget(Tree.InvocationExpression that) {
        Tree.Primary p = that.getPrimary();
        if (p instanceof Tree.MemberOrTypeExpression) {
            return ((Tree.MemberOrTypeExpression) p).getTarget();
        }
        else {
            return null;
        }
    }
    
    private static List<Parameter> getParameters(ProducedReference pr) {
        List<ParameterList> pls = ((Functional) pr.getDeclaration()).getParameterLists();
        return pls.isEmpty() ? null : pls.get(0).getParameters();
    }
    
    @Override
    public void visit(Tree.SpecifiedArgument that) {
        ProducedType ort = requiredType;
        Parameter p = that.getParameter();
        if (p!=null) {
            if (namedArgTarget!=null) {
                requiredType = namedArgTarget.getTypedParameter(p).getType();
            }
            else {
                requiredType = p.getType();            
            }
        }
        super.visit(that);
        requiredType = ort;
    }
    
    @Override
    public void visit(Tree.ForIterator that) {
        ProducedType ort = requiredType;
        requiredType = that.getUnit()
                .getIterableType(that.getUnit()
                        .getAnythingDeclaration().getType());
        super.visit(that);
        requiredType = ort;
    }
    
    @Override
    public void visit(Tree.SpecifierStatement that) {
        ProducedType ort = requiredType;
        requiredType = that.getBaseMemberExpression().getTypeModel();
        super.visit(that);
        requiredType = ort;
    }
    
    @Override
    public void visit(Tree.SwitchStatement that) {
        ProducedType ort = requiredType;
        Tree.SwitchClause switchClause = that.getSwitchClause();
        ProducedType srt = that.getUnit().getAnythingDeclaration().getType();
        if (switchClause!=null) {
            switchClause.visit(this);
            if (switchClause.getExpression()!=null) {
                srt = switchClause.getExpression().getTypeModel();
            }
            else {
                srt = null;
            }
        }
        SwitchCaseList switchCaseList = that.getSwitchCaseList();
        if (switchCaseList!=null) {
            for (Tree.CaseClause cc: switchCaseList.getCaseClauses()) {
                if (cc==node || cc.getCaseItem()==node) {
                    finalResult = srt;
                }
                if (cc.getCaseItem()!=null) {
                    requiredType = srt;
                    cc.getCaseItem().visit(this);
                }
                if (cc.getBlock()!=null) {
                    requiredType = ort;
                    cc.getBlock().visit(this);
                }
            }
        }
        requiredType = ort;
    }
    
    @Override
    public void visit(Tree.AttributeDeclaration that) {
        ProducedType ort = requiredType;
        requiredType = that.getType().getTypeModel();
        super.visit(that);
        requiredType = ort;
    }
    
    @Override
    public void visit(Tree.MethodDeclaration that) {
        ProducedType ort = requiredType;
        requiredType = that.getType().getTypeModel();
        super.visit(that);
        requiredType = ort;
    }
    
    @Override
    public void visit(Tree.FunctionArgument that) {
        ProducedType ort = requiredType;
        requiredType = that.getType().getTypeModel();
        super.visit(that);
        requiredType = ort;
    }
    
    @Override
    public void visit(Tree.AssignmentOp that) {
        ProducedType ort = requiredType;
        requiredType = that.getLeftTerm().getTypeModel();
        super.visit(that);
        requiredType = ort;
    }
    
    @Override
    public void visit(Tree.Return that) {
        ProducedType ort = requiredType;
        requiredType = getResultType(that.getDeclaration());
        super.visit(that);
        requiredType = ort;
    }
    
    @Override
    public void visit(Tree.Throw that) {
        ProducedType ort = requiredType;
        requiredType = that.getUnit().getExceptionDeclaration().getType();
        super.visit(that);
        requiredType = ort;
    }
    
    @Override
    public void visit(Tree.ConditionList that) {
        ProducedType ort = requiredType;
        requiredType = that.getUnit().getBooleanDeclaration().getType();
        super.visit(that);
        requiredType = ort;
    }
    
    @Override
    public void visit(Tree.ResourceList that) {
        ProducedType ort = requiredType;
        requiredType = that.getUnit().getCloseableDeclaration().getType();
        super.visit(that);
        requiredType = ort;
    }
    
    @Override
    public void visit(Tree.StringLiteral that) {
        ProducedType ort = requiredType;
        super.visit(that); // pass on
        requiredType = ort;
    }
    
    @Override
    public void visit(Tree.DocLink that) {
        ProducedType ort = requiredType;
        requiredType = getResultType(that.getBase());
        if (requiredType == null && that.getBase()!=null) {
            requiredType = that.getBase().getReference().getFullType();
        }
        super.visit(that);
        requiredType = ort;
    }
}