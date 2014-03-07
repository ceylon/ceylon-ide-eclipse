package com.redhat.ceylon.eclipse.code.correct;

import static com.redhat.ceylon.compiler.typechecker.model.Util.isTypeUnknown;

import java.util.Collection;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.InsertEdit;

import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;

public class SpecifyTypeArgumentsProposal extends CorrectionProposal {

    SpecifyTypeArgumentsProposal(String type, TextFileChange change) {
        super("Specify explicit type arguments '" + type + "'", change, null);
    }
    
    static void addSpecifyTypeArgumentsProposal(Tree.CompilationUnit cu, Node node,
            Collection<ICompletionProposal> proposals, IFile file) {
        Tree.MemberOrTypeExpression ref = (Tree.MemberOrTypeExpression) node;
        Tree.Identifier identifier;
        Tree.TypeArguments typeArguments;
        if (ref instanceof Tree.BaseMemberOrTypeExpression) {
            identifier = ((Tree.BaseMemberOrTypeExpression) ref).getIdentifier();
            typeArguments = ((Tree.BaseMemberOrTypeExpression) ref).getTypeArguments();
        }
        else if (ref instanceof Tree.QualifiedMemberOrTypeExpression) {
            identifier = ((Tree.QualifiedMemberOrTypeExpression) ref).getIdentifier();
            typeArguments = ((Tree.QualifiedMemberOrTypeExpression) ref).getTypeArguments();
        }
        else {
            return;
        }
        if (typeArguments instanceof Tree.InferredTypeArguments &&
                typeArguments.getTypeModels()!=null &&
                !typeArguments.getTypeModels().isEmpty()) {
            StringBuilder builder = new StringBuilder("<");
            for (ProducedType arg: typeArguments.getTypeModels()) {
                if (isTypeUnknown(arg)) {
                    return;
                }
                if (builder.length()!=1) {
                    builder.append(",");
                }
                builder.append(arg.getProducedTypeName(node.getUnit()));
            }
            builder.append(">");
            TextFileChange change = new TextFileChange("Specify Explicit Type Arguments", file);
            change.setEdit(new InsertEdit(identifier.getStopIndex()+1, builder.toString())); 
            proposals.add(new SpecifyTypeArgumentsProposal(builder.toString(), change));
        }
    }

    static ProducedType inferType(Tree.CompilationUnit cu,
            final Tree.Type type) {
        InferTypeVisitor itv = new InferTypeVisitor() {
            { unit = type.getUnit(); }
            @Override public void visit(Tree.TypedDeclaration that) {
                if (that.getType()==type) {
                    dec = that.getDeclarationModel();
                    union(that.getType().getTypeModel());
                }
                super.visit(that);
            }            
        };
        itv.visit(cu);
        return itv.inferredType;
    }
    
}
