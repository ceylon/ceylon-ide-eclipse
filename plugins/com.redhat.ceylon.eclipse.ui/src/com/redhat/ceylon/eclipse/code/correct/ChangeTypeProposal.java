package com.redhat.ceylon.eclipse.code.correct;

import static com.redhat.ceylon.compiler.typechecker.model.Util.intersectionType;
import static com.redhat.ceylon.compiler.typechecker.model.Util.unionType;
import static com.redhat.ceylon.eclipse.code.correct.CorrectionUtil.getRootNode;
import static com.redhat.ceylon.eclipse.code.correct.CreateProposal.getDocument;
import static com.redhat.ceylon.eclipse.code.correct.ImportProposals.applyImports;
import static com.redhat.ceylon.eclipse.code.correct.ImportProposals.importType;
import static com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.getFile;
import static com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.getUnits;
import static com.redhat.ceylon.eclipse.util.FindUtils.findStatement;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;

import com.redhat.ceylon.compiler.typechecker.context.PhasedUnit;
import com.redhat.ceylon.compiler.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.IntersectionType;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.TypeParameter;
import com.redhat.ceylon.compiler.typechecker.model.TypedDeclaration;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.eclipse.code.editor.EditorUtil;
import com.redhat.ceylon.eclipse.util.FindDeclarationNodeVisitor;

class ChangeTypeProposal extends CorrectionProposal {

    final int offset;
    final int length;
    final IFile file;
    
    ChangeTypeProposal(ProblemLocation problem, IFile file, 
            String name, String type, int offset,
            TextFileChange change) {
        super("Change type of "+ name + " to '" + type + "'", 
                change);
        this.offset = offset;
        this.length = type.length();
        this.file = file;
    }
    
    @Override
    public void apply(IDocument document) {
        super.apply(document);
        EditorUtil.gotoLocation(file, offset, length);
    }
    
    static void addChangeTypeProposal(Node node, ProblemLocation problem, 
            Collection<ICompletionProposal> proposals, Declaration dec, 
            ProducedType newType, IFile file, Tree.CompilationUnit cu) {
        // better safe than throwing
        if(node.getStartIndex() == null || node.getStopIndex() == null)
            return;
        TextFileChange change =  new TextFileChange("Change Type", file);
        change.setEdit(new MultiTextEdit());
        IDocument doc = getDocument(change);
        String typeName = newType.getProducedTypeName();
        int offset = node.getStartIndex();
        int length = node.getStopIndex()-offset+1;
        HashSet<Declaration> decs = new HashSet<Declaration>();
        importType(decs, newType, cu);
        int il=applyImports(change, decs, cu, doc);
        change.addEdit(new ReplaceEdit(offset, length, typeName));
        String name;
        if (dec.isParameter()) {
            name = "parameter '" + dec.getName() + "' of '" + ((Declaration) dec.getContainer()).getName() + "'";
        }
        else if (dec.isClassOrInterfaceMember()) {
            name = "member '" +  dec.getName() + "' of '" + ((ClassOrInterface) dec.getContainer()).getName() + "'";
        }
        else {
            name = "'" + dec.getName() + "'";
        }
        proposals.add(new ChangeTypeProposal(problem, file, name, 
                typeName, offset+il, change));
    }
    
    static void addChangeTypeProposals(Tree.CompilationUnit cu, Node node, 
            ProblemLocation problem, Collection<ICompletionProposal> proposals, 
            IProject project) {
        if (node instanceof Tree.SimpleType) {
            TypeDeclaration decl = ((Tree.SimpleType)node).getDeclarationModel();
            if (decl instanceof TypeParameter) {
                Tree.Statement statement = findStatement(cu, node);
                if (statement instanceof Tree.AttributeDeclaration) {
                    Tree.AttributeDeclaration ad = (Tree.AttributeDeclaration) statement;
                    Tree.SimpleType st = (Tree.SimpleType) ad.getType();

                    TypeParameter stTypeParam = null;
                    if (st.getTypeArgumentList() != null) {
                        List<Tree.Type> stTypeArguments = 
                                st.getTypeArgumentList().getTypes();
                        for (int i = 0; i < stTypeArguments.size(); i++) {
                            Tree.SimpleType stTypeArgument = 
                                    (Tree.SimpleType)stTypeArguments.get(i);
                            if (decl.getName().equals(
                                    stTypeArgument.getDeclarationModel().getName())) {
                                TypeDeclaration stDecl = st.getDeclarationModel();
                                if (stDecl != null) {
                                    if (stDecl.getTypeParameters()!=null && 
                                            stDecl.getTypeParameters().size() > i) {
                                        stTypeParam = stDecl.getTypeParameters().get(i);
                                        break;
                                    }
                                }                            
                            }
                        }                    
                    }

                    if (stTypeParam != null && 
                            !stTypeParam.getSatisfiedTypes().isEmpty()) {
                        IntersectionType it = new IntersectionType(cu.getUnit());
                        it.setSatisfiedTypes(stTypeParam.getSatisfiedTypes());
                        addChangeTypeProposals(proposals, problem, project, node, 
                                it.canonicalize().getType(), decl, true);
                    }
                }
            }
        }
        if (node instanceof Tree.SpecifierExpression) {
            Tree.Expression e = ((Tree.SpecifierExpression) node).getExpression();
            if (e!=null) {
                node = e.getTerm();
            }
        }
        if (node instanceof Tree.Expression) {
            node = ((Tree.Expression) node).getTerm();
        }
        if (node instanceof Tree.Term) {
            ProducedType t = ((Tree.Term) node).getTypeModel();
            if (t==null) return;
            ProducedType type = node.getUnit().denotableType(t);
            FindInvocationVisitor fav = new FindInvocationVisitor(node);
            fav.visit(cu);
            TypedDeclaration td = fav.parameter;
            if (td!=null) {
                if (node instanceof Tree.BaseMemberExpression){
                    TypedDeclaration d = (TypedDeclaration) 
                            ((Tree.BaseMemberExpression) node).getDeclaration();
                    addChangeTypeProposals(proposals, problem, project, node, 
                            td.getType(), d, true);
                }
                if (node instanceof Tree.QualifiedMemberExpression){
                    TypedDeclaration d = (TypedDeclaration) 
                            ((Tree.QualifiedMemberExpression) node).getDeclaration();
                    addChangeTypeProposals(proposals, problem, project, node, 
                            td.getType(), d, true);
                }
                addChangeTypeProposals(proposals, problem, project, 
                        node, type, td, false);
            }
        }
    }
    
    static void addChangeTypeProposals(Collection<ICompletionProposal> proposals,
            ProblemLocation problem, IProject project, Node node, ProducedType type, 
            Declaration dec, boolean intersect) {
        if (dec!=null) {
            for (PhasedUnit unit: getUnits(project)) {
                if (dec.getUnit().equals(unit.getUnit())) {
                    ProducedType t = null;
                    Node typeNode = null;
                    
                    if (dec instanceof TypeParameter) {
                        t = ((TypeParameter) dec).getType();
                        typeNode = node;
                    }
                    
                    if (dec instanceof TypedDeclaration) {
                        TypedDeclaration typedDec = (TypedDeclaration)dec;
                        FindDeclarationNodeVisitor fdv = 
                                new FindDeclarationNodeVisitor(typedDec);
                        getRootNode(unit).visit(fdv);
                        Tree.TypedDeclaration decNode = 
                                (Tree.TypedDeclaration) fdv.getDeclarationNode();
                        if (decNode!=null) {
                            typeNode = decNode.getType();
                            if (typeNode!=null) {
                                t=((Tree.Type)typeNode).getTypeModel();
                            }
                        }
                    }
                    
                    if (typeNode != null) {
                        addChangeTypeProposal(typeNode, problem, proposals, dec, 
                                type, getFile(unit), unit.getCompilationUnit());
                        if (t != null) {
                            ProducedType newType = intersect ? 
                                    intersectionType(t, type, unit.getUnit()) : 
                                    unionType(t, type, unit.getUnit());
                            if (!newType.isExactly(t)) {
                                addChangeTypeProposal(typeNode, problem, 
                                        proposals, dec, newType, getFile(unit), 
                                        unit.getCompilationUnit());
                            }
                        }
                    }
                }
            }
        }
    }

}
