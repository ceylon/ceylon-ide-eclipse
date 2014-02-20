package com.redhat.ceylon.eclipse.code.refactor;

import static com.redhat.ceylon.eclipse.code.parse.CeylonTokenColorer.keywords;
import static com.redhat.ceylon.eclipse.util.FindUtils.getContainer;
import static com.redhat.ceylon.eclipse.util.Indents.getDefaultIndent;
import static com.redhat.ceylon.eclipse.util.Indents.getDefaultLineDelimiter;
import static com.redhat.ceylon.eclipse.util.Indents.getIndent;
import static org.eclipse.ltk.core.refactoring.RefactoringStatus.createErrorStatus;
import static org.eclipse.ltk.core.refactoring.RefactoringStatus.createWarningStatus;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.ui.texteditor.ITextEditor;

import com.redhat.ceylon.compiler.typechecker.context.PhasedUnit;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Functional;
import com.redhat.ceylon.compiler.typechecker.model.Scope;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.CompilationUnit;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;
import com.redhat.ceylon.eclipse.code.editor.CeylonEditor;
import com.redhat.ceylon.eclipse.util.FindUtils;

public class MoveOutRefactoring extends AbstractRefactoring {
    
    private Tree.Declaration declaration;
    private boolean makeShared=true;
    private String newName;

    public MoveOutRefactoring(ITextEditor editor) {
        super(editor);
        if (editor instanceof CeylonEditor && 
                editor.getSelectionProvider()!=null) {
            init((ITextSelection) editor.getSelectionProvider()
                    .getSelection());
        }
    }

    private void init(ITextSelection selection) {
        if (node instanceof Tree.Declaration) {
            declaration = (Tree.Declaration) node;
            if (declaration.getDeclarationModel()!=null) {
                newName = defaultName(getContainer(declaration.getDeclarationModel(), rootNode));
            }
        }
    }

    @Override
    boolean isEnabled() {
        return (node instanceof Tree.AnyMethod || 
                node instanceof Tree.ClassDefinition) &&
                    ((Tree.Declaration) node).getDeclarationModel()!=null &&
                    ((Tree.Declaration) node).getDeclarationModel()
                            .isClassOrInterfaceMember();
    }
    
    public String getName() {
        return "Move Out";
    }

    public RefactoringStatus checkInitialConditions(IProgressMonitor pm)
            throws CoreException, OperationCanceledException {
        RefactoringStatus result = new RefactoringStatus();
        Declaration dec = ((Tree.Declaration) node).getDeclarationModel();
        if (!(dec instanceof Functional) || 
                ((Functional) dec).getParameterLists().isEmpty()) {
            result.merge(createErrorStatus("Selected declaration has no parameter list"));
        }
        if (!dec.isClassOrInterfaceMember()) {
            result.merge(createErrorStatus("Selected declaration is not a member of a class or interface"));
        }
        if (dec.isFormal()) {
            result.merge(createWarningStatus("Selected declaration is annotated formal")); 
        }
        if (dec.isDefault()) {
            result.merge(createWarningStatus("Selected declaration is annotated default")); 
        }
        if (dec.isActual()) {
            result.merge(createWarningStatus("Selected declaration is annotated actual")); 
        }
        //TODO: check if there are method references to this declaration
        return result;
    }

    public RefactoringStatus checkFinalConditions(IProgressMonitor pm)
            throws CoreException, OperationCanceledException {
        return new RefactoringStatus();
    }

    public Change createChange(IProgressMonitor pm) throws CoreException,
            OperationCanceledException {
        CompositeChange cc = new CompositeChange(getName());
        
        Declaration dec = declaration.getDeclarationModel();
        Tree.TypeDeclaration owner = (Tree.TypeDeclaration) FindUtils.getContainer(dec, rootNode);

        for (PhasedUnit pu: getAllUnits()) {
            if (searchInFile(pu)) {
                TextFileChange pufc = newTextFileChange(pu);
                pufc.setEdit(new MultiTextEdit());
                if (declaration.getUnit().equals(pu.getUnit())) {
                    move(owner, pufc);
                    if (makeShared) {
                        addSharedAnnotations(pufc, owner);
                    }
                }
                fixInvocations(dec, pu.getCompilationUnit(), pufc);
                if (pufc.getEdit().hasChildren()) {
                    cc.add(pufc);
                }
            }
        }
        
        if (searchInEditor()) {
            TextChange tfc = newLocalChange();
            tfc.setEdit(new MultiTextEdit());
            move(owner, tfc);
            fixInvocations(dec, rootNode, tfc);
            cc.add(tfc);
            if (makeShared) {
                addSharedAnnotations(tfc, owner);
            }
        }
        
        return cc;
    }

    private StringBuilder renderText(Tree.TypeDeclaration owner, 
            String indent, String originalIndent, String delim) {
        String qtype = owner.getDeclarationModel().getType()
                .getProducedTypeName(declaration.getUnit());
        StringBuilder sb = new StringBuilder();
        if (declaration instanceof Tree.AnyMethod) {
            Tree.AnyMethod md = (Tree.AnyMethod) declaration;
            appendAnnotations(sb, md, owner.getDeclarationModel());
            sb.append(toString(md.getType())).append(" ")
                .append(toString(md.getIdentifier()));
            List<Tree.ParameterList> parameterLists = md.getParameterLists();
            Tree.ParameterList first = parameterLists.get(0);
            sb.append(toString(first));
            if (!first.getParameters().isEmpty()) {
                sb.insert(sb.length()-1, ", ");
            }
            sb.insert(sb.length()-1, qtype+ " " + newName);
            for (int i=1; i<parameterLists.size(); i++) {
                sb.append(toString(parameterLists.get(i)));
            }
            if (md.getTypeConstraintList()!=null) {
                appendConstraints(indent, delim, sb, 
                        md.getTypeConstraintList());
            }
            sb.append(" ");
            if (md instanceof Tree.MethodDefinition &&
                    ((Tree.MethodDefinition) md).getBlock()!=null) {
                appendBody(owner.getDeclarationModel(), 
                        indent, originalIndent, 
                        delim, sb, 
                        ((Tree.MethodDefinition) md).getBlock());
            }
            if (md instanceof Tree.MethodDeclaration &&
                    ((Tree.MethodDeclaration) md).getSpecifierExpression()!=null) {
                appendBody(owner.getDeclarationModel(), 
                        indent, originalIndent, 
                        delim, sb, 
                        ((Tree.MethodDeclaration) md).getSpecifierExpression());
                sb.append(";");
            }
        }
        else if (declaration instanceof Tree.ClassDefinition) {
            Tree.ClassDefinition cd = (Tree.ClassDefinition) declaration;
            appendAnnotations(sb, cd, owner.getDeclarationModel());
            sb.append("class ")
                .append(toString(cd.getIdentifier()));
            Tree.ParameterList first = cd.getParameterList();
            sb.append(toString(first));
            if (!first.getParameters().isEmpty()) {
                sb.insert(sb.length()-1, ", ");
            }
            sb.insert(sb.length()-1, qtype+ " " + newName);
            if (cd.getCaseTypes()!=null) {
                appendClause(indent, delim, sb, cd.getCaseTypes());
            }
            if (cd.getExtendedType()!=null) {
                appendClause(indent, delim, sb, cd.getExtendedType());
            }
            if (cd.getSatisfiedTypes()!=null) {
                appendClause(indent, delim, sb, cd.getSatisfiedTypes());
            }
            if (cd.getTypeConstraintList()!=null) {
                appendConstraints(indent, delim, sb, 
                        cd.getTypeConstraintList());
            }
            sb.append(" ");
            if (cd.getClassBody()!=null) {
                appendBody(owner.getDeclarationModel(), 
                        indent, originalIndent, 
                        delim, sb, 
                        cd.getClassBody());
            }
        }
        return sb;
    }

    private void move(Tree.TypeDeclaration owner, TextChange tfc) {
        String indent = getIndent(owner, document);
        String originalIndent = getIndent(declaration, document);
        String delim = getDefaultLineDelimiter(document);
        StringBuilder sb = renderText(owner, indent, originalIndent, delim);
        tfc.addEdit(new InsertEdit(owner.getStopIndex()+1, 
                delim+indent+delim+indent+sb));
        tfc.addEdit(new DeleteEdit(declaration.getStartIndex(), 
                declaration.getStopIndex()-declaration.getStartIndex()+1));
    }

    private void addSharedAnnotations(final TextChange tfc,
            final Tree.TypeDeclaration owner) {
        final Set<Declaration> decs = new HashSet<Declaration>();
        new Visitor() {
            private void add(Declaration d) {
                if (d!=null && !d.isShared() && 
                        d.getContainer().equals(owner.getDeclarationModel())) {
                    decs.add(d);
                }
            }
            public void visit(Tree.BaseMemberOrTypeExpression that) {
                super.visit(that);
                add(that.getDeclaration());
            }
            public void visit(Tree.QualifiedMemberOrTypeExpression that) {
                super.visit(that);
                if (that.getPrimary() instanceof Tree.This) {
                    add(that.getDeclaration());
                }
            }
        }.visit(declaration);
        new Visitor() {
            public void visit(Tree.Declaration that) {
                if (decs.contains(that.getDeclarationModel())) {
                    tfc.addEdit(new InsertEdit(that.getStartIndex(), "shared "));
                }
                super.visit(that);
            }
        }.visit(owner);
    }

    private static String defaultName(Tree.Declaration owner) {
        if (owner==null) {
            return "it";
        }
        String name = owner.getIdentifier().getText();
        String paramName = Character.toLowerCase(name.charAt(0))+name.substring(1);
        if (keywords.contains(paramName)) {
            return "it";
        }
        return paramName;
    }

    private void fixInvocations(final Declaration dec, CompilationUnit cu,
            final TextChange tc) {
        new Visitor() {
            public void visit(Tree.InvocationExpression that) {
                super.visit(that);
                Tree.PositionalArgumentList pal = that.getPositionalArgumentList();
                Tree.NamedArgumentList nal = that.getNamedArgumentList();
                if (that.getPrimary() instanceof Tree.BaseMemberOrTypeExpression) {
                    Tree.BaseMemberOrTypeExpression bmte = 
                            (Tree.BaseMemberOrTypeExpression) that.getPrimary();
                    if (bmte.getDeclaration().equals(dec)) {
                        if (pal!=null) {
                            String arg = pal.getPositionalArguments().isEmpty() ? 
                                    "this" : ", this";
                            tc.addEdit(new InsertEdit(pal.getStopIndex(), arg));
                        }
                        if (nal!=null) {
                            try {
                                IDocument doc = tc.getCurrentDocument(null);
                                String arg = namedArgIndent(nal, doc) + 
                                        newName + " = this;";
                                List<Tree.NamedArgument> args = nal.getNamedArguments();
                                int offset = args.isEmpty() ? 
                                        nal.getStartIndex()+1 : 
                                        args.get(args.size()-1).getStopIndex()+1;
                                tc.addEdit(new InsertEdit(offset, arg));
                            }
                            catch (CoreException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
                if (that.getPrimary() instanceof Tree.QualifiedMemberOrTypeExpression) {
                    Tree.QualifiedMemberOrTypeExpression qmte = 
                            (Tree.QualifiedMemberOrTypeExpression) that.getPrimary();
                    if (qmte.getDeclaration().equals(dec)) {
                        Tree.Primary p = qmte.getPrimary();
                        String pt = MoveOutRefactoring.this.toString(p);
                        tc.addEdit(new DeleteEdit(p.getStartIndex(), 
                                qmte.getMemberOperator().getStopIndex()-p.getStartIndex()+1));
                        if (pal!=null) {
                            String arg = pal.getPositionalArguments().isEmpty() ? 
                                    pt : ", " + pt;
                            tc.addEdit(new InsertEdit(pal.getStopIndex(), arg));
                        }
                        if (nal!=null) {
                            try {
                                IDocument doc = tc.getCurrentDocument(null);
                                String arg = namedArgIndent(nal, doc) + 
                                        newName + " = " + pt + ";";
                                List<Tree.NamedArgument> args = nal.getNamedArguments();
                                int offset = args.isEmpty() ? 
                                        nal.getStartIndex()+1 : 
                                        args.get(args.size()-1).getStopIndex()+1;
                                tc.addEdit(new InsertEdit(offset, arg));
                            }
                            catch (CoreException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }

            private String namedArgIndent(Tree.NamedArgumentList nal,
                    IDocument doc) {
                return getDefaultLineDelimiter(doc) + 
                        getIndent(nal, doc) + 
                        getDefaultIndent();
            }
            
        }.visit(cu);
    }

    private void appendAnnotations(StringBuilder sb, Tree.Declaration d, TypeDeclaration od) {
        if (!d.getAnnotationList().getAnnotations().isEmpty()) {
            String annotations = toString(d.getAnnotationList());
            if (!od.isShared()) {
                annotations = annotations.replaceAll("shared", "");
            }
            annotations = annotations.replaceAll("default|formal|actual", "");
            sb.append(annotations.trim());
            if (sb.length()!=0) {
                sb.append(" ");
            }
        }
    }

    private void appendConstraints(String indent, String delim,
            StringBuilder sb, Tree.TypeConstraintList tcl) {
        for (Tree.TypeConstraint tc: tcl.getTypeConstraints()) {
            appendClause(indent, delim, sb, tc);
        }
    }

    private void appendClause(String indent, String delim, StringBuilder sb,
            Node clause) {
        sb.append(delim).append(indent)
            .append(getDefaultIndent())
            .append(getDefaultIndent())
            .append(toString(clause));
    }

    private void appendBody(final Scope container, String indent, String originalIndent, 
            String delim, StringBuilder sb, final Node body) {
//        sb.append(" {");
//        for (final Tree.Statement st: block.getStatements()) {
            final StringBuilder stb = new StringBuilder(toString(body));
            body.visit(new Visitor() {
                int offset = 0;
                @Override
                public void visit(Tree.BaseMemberOrTypeExpression that) {
                    super.visit(that);
                    if (that.getDeclaration().getContainer().equals(container)) {
                        int start = that.getStartIndex()-body.getStartIndex()+offset;
                        stb.insert(start, newName + ".");
                        offset+=newName.length()+1;
                    }
                }
                @Override
                public void visit(Tree.QualifiedMemberOrTypeExpression that) {
                    super.visit(that);
                    Tree.Primary p = that.getPrimary();
                    int start = p.getStartIndex()-body.getStartIndex()+offset;
                    int len = p.getStopIndex()-p.getStartIndex()+1;
                    if (p instanceof Tree.This) {
                        stb.replace(start, start+len, newName);
                        offset+=newName.length()-len;
                    }
                    if (p instanceof Tree.Outer) {
                        stb.replace(start, start+5, "this");
                        offset-=1;
                    }
                }
            });
//            sb.append(delim).append(indent)
//                .append(getDefaultIndent())
            sb.append(stb.toString().replaceAll(delim+originalIndent, delim+indent));
//        }
//        sb.append(delim).append(indent).append("}");
    }

    public void setMakeShared() {
        makeShared=!makeShared;
    }

    void setNewName(String name) {
        newName = name;
    }

    String getNewName() {
        return newName;
    }
    
}
