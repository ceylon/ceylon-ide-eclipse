package com.redhat.ceylon.eclipse.util;

import static com.redhat.ceylon.compiler.typechecker.model.Util.isOverloadedVersion;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.LIDENTIFIER;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.UIDENTIFIER;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.antlr.runtime.CommonToken;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.Region;

import com.redhat.ceylon.compiler.typechecker.context.PhasedUnit;
import com.redhat.ceylon.compiler.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.MethodOrValue;
import com.redhat.ceylon.compiler.typechecker.model.Parameter;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.model.Referenceable;
import com.redhat.ceylon.compiler.typechecker.model.Scope;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.TypeParameter;
import com.redhat.ceylon.compiler.typechecker.model.Unit;
import com.redhat.ceylon.compiler.typechecker.model.Util;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.DocLink;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Identifier;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Statement;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;
import com.redhat.ceylon.eclipse.code.parse.CeylonParseController;
import com.redhat.ceylon.eclipse.code.resolve.FindReferencedNodeVisitor;
import com.redhat.ceylon.eclipse.core.model.CeylonBinaryUnit;
import com.redhat.ceylon.eclipse.core.model.CeylonUnit;
import com.redhat.ceylon.eclipse.core.model.EditedSourceFile;
import com.redhat.ceylon.eclipse.core.model.ExternalSourceFile;
import com.redhat.ceylon.eclipse.core.model.ICrossProjectReference;
import com.redhat.ceylon.eclipse.core.model.ProjectSourceFile;
import com.redhat.ceylon.eclipse.core.typechecker.ProjectPhasedUnit;

public class Nodes {

    private static final String[] NO_STRINGS = new String[0];
    private static final Pattern IDPATTERN = 
            Pattern.compile("(^|[A-Z])([A-Z]*)([_a-z]+)");
    

    public static Tree.Declaration findDeclaration(Tree.CompilationUnit cu, Node node) {
        FindDeclarationVisitor fcv = new FindDeclarationVisitor(node);
        fcv.visit(cu);
        return fcv.getDeclarationNode();
    }

    public static Tree.Declaration findDeclarationWithBody(Tree.CompilationUnit cu, Node node) {
        FindBodyContainerVisitor fcv = new FindBodyContainerVisitor(node);
        fcv.visit(cu);
        return fcv.getDeclarationNode();
    }

    public static Tree.NamedArgument findArgument(Tree.CompilationUnit cu, Node node) {
        FindArgumentVisitor fcv = new FindArgumentVisitor(node);
        fcv.visit(cu);
        return fcv.getArgumentNode();
    }

    public static Statement findStatement(Tree.CompilationUnit cu, Node node) {
        FindStatementVisitor visitor = new FindStatementVisitor(node, false);
        cu.visit(visitor);
        return visitor.getStatement();
    }

    public static Statement findToplevelStatement(Tree.CompilationUnit cu, Node node) {
        FindStatementVisitor visitor = new FindStatementVisitor(node, true);
        cu.visit(visitor);
        return visitor.getStatement();
    }

    public static Declaration getAbstraction(Declaration d) {
        if (isOverloadedVersion(d)) {
            return d.getContainer().getDirectMember(d.getName(), null, false);
        }
        else {
            return d;
        }
    }

    public static Tree.Declaration getContainer(Tree.CompilationUnit cu,
            final Declaration dec) {
        class FindContainer extends Visitor {
            final Scope container = dec.getContainer();
            Tree.Declaration result;
            @Override
            public void visit(Tree.Declaration that) {
                super.visit(that);
                if (that.getDeclarationModel().equals(container)) {
                    result = that;
                }
            }
        }
        FindContainer fc = new FindContainer();
        cu.visit(fc);
        if (fc.result instanceof Tree.Declaration) {
            return (Tree.Declaration) fc.result;
        }
        else {
            return null;
        }
    }

    public static Tree.ImportMemberOrType findImport(Tree.CompilationUnit cu, Node node) {
        
        if (node instanceof Tree.ImportMemberOrType) {
            return (Tree.ImportMemberOrType) node;
        }
        
        final Declaration declaration;
        if (node instanceof Tree.MemberOrTypeExpression) {
            declaration = ((Tree.MemberOrTypeExpression) node).getDeclaration();
        }
        else if (node instanceof Tree.SimpleType) {
            declaration = ((Tree.SimpleType) node).getDeclarationModel();
        }
        else if (node instanceof Tree.MemberLiteral) {
            declaration = ((Tree.MemberLiteral) node).getDeclaration();
        }
        else {
            return null;
        }
        
        class FindImportVisitor extends Visitor {
            Tree.ImportMemberOrType result;
            @Override
            public void visit(Tree.Declaration that) {}
            @Override
            public void visit(Tree.ImportMemberOrType that) {
                super.visit(that);
                if (that.getDeclarationModel()!=null &&
                        that.getDeclarationModel().equals(declaration)) {
                    result = that;
                }
            }
        }
        
        if (declaration!=null) {
            FindImportVisitor visitor = new FindImportVisitor();
            visitor.visit(cu);
            return visitor.result;
        }
        else {
            return null;
        }
    
    }

    public static Node findNode(Tree.CompilationUnit cu, int offset) {
        return findNode(cu, offset, offset+1);
    }

    public static Node findNode(Node cu, int startOffset, int endOffset) {
        FindNodeVisitor visitor = new FindNodeVisitor(startOffset, endOffset);
        cu.visit(visitor);
        return visitor.getNode();
    }

    private static Node findScope(Tree.CompilationUnit cu, int startOffset, int endOffset) {
        FindScopeVisitor visitor = new FindScopeVisitor(startOffset, endOffset);
        cu.visit(visitor);
        return visitor.getNode();
    }

    public static Node findNode(Tree.CompilationUnit cu, ITextSelection s) {
        return findNode(cu, s.getOffset(), s.getOffset()+s.getLength());
    }

    public static Node findScope(Tree.CompilationUnit cu, ITextSelection s) {
        return findScope(cu, s.getOffset(), s.getOffset()+s.getLength());
    }

    private static Node toNode(Node node) {
        if (node instanceof Node) {
            return Nodes.getIdentifyingNode((Node) node);
        }
        else {
            return null;
        }
    }
    
    public static int getStartOffset(Node node) {
        return Nodes.getNodeStartOffset(toNode(node));
    }

    public static int getEndOffset(Node node) {
        return Nodes.getNodeEndOffset(toNode(node));
    }

    public static int getLength(Node node) {
        return getEndOffset(node) - getStartOffset(node);
    }

    public static int getNodeLength(Node node) {
        return Nodes.getNodeEndOffset(node) - Nodes.getNodeStartOffset(node);
    }

    public static Node getIdentifyingNode(Node node) {
        if (node instanceof Tree.Declaration) {
            return ((Tree.Declaration) node).getIdentifier();
        }
        else if (node instanceof Tree.ModuleDescriptor) {
            return ((Tree.ModuleDescriptor) node).getImportPath();
        }
        else if (node instanceof Tree.PackageDescriptor) {
            return ((Tree.PackageDescriptor) node).getImportPath();
        }
        else if (node instanceof Tree.Import) {
            return ((Tree.Import) node).getImportPath();
        }
        else if (node instanceof Tree.ImportModule) {
            return ((Tree.ImportModule) node).getImportPath();
        }
        else if (node instanceof Tree.NamedArgument) {
            Identifier id = ((Tree.NamedArgument) node).getIdentifier();
            if (id==null || id.getToken()==null) {
                return node;
            }
            else {
                return id;
            }
        }
        else if (node instanceof Tree.StaticMemberOrTypeExpression) {
            return ((Tree.StaticMemberOrTypeExpression) node).getIdentifier();
        }
        else if (node instanceof Tree.ExtendedTypeExpression) {
            //TODO: whoah! this is really ugly!
            return ((Tree.SimpleType) ((Tree.ExtendedTypeExpression) node).getChildren().get(0))
                    .getIdentifier();
        }
        else if (node instanceof Tree.SimpleType) {
            return ((Tree.SimpleType) node).getIdentifier();
        }
        else if (node instanceof Tree.ImportMemberOrType) {
            return ((Tree.ImportMemberOrType) node).getIdentifier();
        }
        else if (node instanceof Tree.InitializerParameter) {
            return ((Tree.InitializerParameter) node).getIdentifier();
        }
        else if (node instanceof Tree.MemberLiteral) {
            return ((Tree.MemberLiteral) node).getIdentifier();
        }
        else if (node instanceof Tree.TypeLiteral) {
            return getIdentifyingNode(((Tree.TypeLiteral) node).getType());
        }
        else {    
            return node;
        }
    }

    public static Iterator<CommonToken> getTokenIterator(List<CommonToken> tokens, IRegion region) {
        int regionOffset = region.getOffset();
        int regionLength = region.getLength();
        if (regionLength<=0) {
            return Collections.<CommonToken>emptyList().iterator();
        }
        int regionEnd = regionOffset + regionLength - 1;
        if (tokens==null) {
            return null;
        }
        else {
            int firstTokIdx = getTokenIndexAtCharacter(tokens, regionOffset);
            // getTokenIndexAtCharacter() answers the negative of the index of the
            // preceding token if the given offset is not actually within a token.
            if (firstTokIdx < 0) {
                firstTokIdx= -firstTokIdx + 1;
            }
            int lastTokIdx = getTokenIndexAtCharacter(tokens, regionEnd);
            if (lastTokIdx < 0) {
                lastTokIdx= -lastTokIdx;
            }
            return tokens.subList(firstTokIdx, lastTokIdx+1).iterator();
        }
    }

    //
    // This function returns the index of the token element
    // containing the offset specified. If such a token does
    // not exist, it returns the negation of the index of the 
    // element immediately preceding the offset.
    //
    public static int getTokenIndexAtCharacter(List<CommonToken> tokens, int offset) {
        //search using bisection
        int low = 0,
                high = tokens.size();
        while (high > low)
        {
            int mid = (high + low) / 2;
            CommonToken midElement = (CommonToken) tokens.get(mid);
            if (offset >= midElement.getStartIndex() &&
                    offset <= midElement.getStopIndex())
                return mid;
            else if (offset < midElement.getStartIndex())
                high = mid;
            else low = mid + 1;
        }
        
        return -(low - 1);
    }

    public static int getNodeStartOffset(Node node) {
        if (node==null) {
            return 0;
        }
        else {
            Integer index = node.getStartIndex();
            return index==null?0:index;
        }
    }

    public static int getNodeEndOffset(Node node) {
        if (node==null) {
            return 0;
        }
        else {
            Integer index = node.getStopIndex();
            return index==null?0:index+1;
        }
    }

    public static Node getReferencedNode(Node node, 
            CeylonParseController controller) {
        return getReferencedNode(getReferencedModel(node), 
                controller);
    }

    public static Node getReferencedNode(Referenceable dec, 
            CeylonParseController controller) {
        return getReferencedNode(dec, 
                getCompilationUnit(dec, controller));
    }

    public static Referenceable getReferencedModel(Node node) {
        if (node instanceof Tree.ImportPath) {
            return ((Tree.ImportPath) node).getModel();
        }
        else {
            Declaration dec = getReferencedDeclaration((Node) node);
            if (dec instanceof MethodOrValue && 
                    ((MethodOrValue) dec).isShortcutRefinement()) {
                dec = dec.getRefinedDeclaration();
            }
            return dec;
        }
    }

    public static Declaration getReferencedExplicitDeclaration(Node node, 
            Tree.CompilationUnit rn) {
        Declaration dec = getReferencedDeclaration(node);
        if (dec!=null && dec.getUnit().equals(node.getUnit())) {
            FindDeclarationNodeVisitor fdv = new FindDeclarationNodeVisitor(dec);
            fdv.visit(rn);
            Node decNode = fdv.getDeclarationNode();
            if (decNode instanceof Tree.Variable) {
                Tree.Variable var = (Tree.Variable) decNode;
                if (var.getType() instanceof Tree.SyntheticVariable) {
                    Tree.Term term = var.getSpecifierExpression()
                            .getExpression().getTerm();
                    return getReferencedExplicitDeclaration(term, rn);
                }
            }
        }
        return dec;
    }

    public static Declaration getReferencedDeclaration(Node node) {
        //NOTE: this must accept a null node, returning null!
        if (node instanceof Tree.MemberOrTypeExpression) {
            return ((Tree.MemberOrTypeExpression) node).getDeclaration();
        } 
        else if (node instanceof Tree.SimpleType) {
            return ((Tree.SimpleType) node).getDeclarationModel();
        } 
        else if (node instanceof Tree.ImportMemberOrType) {
            return ((Tree.ImportMemberOrType) node).getDeclarationModel();
        } 
        else if (node instanceof Tree.Declaration) {
            return ((Tree.Declaration) node).getDeclarationModel();
        } 
        else if (node instanceof Tree.NamedArgument) {
            Parameter p = ((Tree.NamedArgument) node).getParameter();
            return p==null ? null : p.getModel();
        }
        else if (node instanceof Tree.InitializerParameter) {
            Parameter p = ((Tree.InitializerParameter) node).getParameterModel();
            return  p==null ? null : p.getModel();
        }
        else if (node instanceof Tree.MetaLiteral) {
            return ((Tree.MetaLiteral) node).getDeclaration();
        }
        else if (node instanceof Tree.DocLink) {
            DocLink docLink = (Tree.DocLink) node;
            List<Declaration> qualified = docLink.getQualified();
            if (qualified!=null && !qualified.isEmpty()) {
                return qualified.get(qualified.size()-1);
            }
            else {
                return docLink.getBase();
            }
        }
        else {
            return null;
        }
    }

    public static Node getReferencedNode(Referenceable dec,
            Tree.CompilationUnit compilationUnit) {
        if (compilationUnit==null || dec==null) {
            return null;
        }
        else {
            FindReferencedNodeVisitor visitor = new FindReferencedNodeVisitor(dec);
            compilationUnit.visit(visitor);
            //System.out.println("referenced node: " + visitor.getDeclarationNode());
            return visitor.getDeclarationNode();
        }
    }

    public static Tree.CompilationUnit getCompilationUnit(Referenceable model,
            CeylonParseController cpc) {
        if (model==null) {
            return null;
        }
        else {
            Tree.CompilationUnit root = cpc==null ? 
                    null : cpc.getRootNode();            
            if (root!=null && root.getUnit()!=null && 
                    root.getUnit().equals(model.getUnit())) {
                return root;
            }
            else {
                Unit unit = model.getUnit();
                PhasedUnit pu = null; 
                if (unit instanceof ProjectSourceFile) {
                    pu = ((ProjectSourceFile) unit).getPhasedUnit();
                    // Here pu should never be null !
                }
                if (unit instanceof EditedSourceFile) {
                    pu = ((EditedSourceFile) unit).getPhasedUnit();
                    // Here pu should never be null !
                }
                
                if (unit instanceof ICrossProjectReference) {
                    ProjectPhasedUnit requiredProjectPhasedUnit = ((ICrossProjectReference) unit).getOriginalPhasedUnit();
                    if (requiredProjectPhasedUnit != null 
                            && requiredProjectPhasedUnit.isFullyTyped()) {
                        pu = requiredProjectPhasedUnit;
                    }
                    else {
                        System.err.println("ABNORMAL : cross reference with a null original PhasedUnit !");
                        pu = ((ICrossProjectReference) unit).getPhasedUnit();
                    }
                }
                
                if (pu == null && (unit instanceof ExternalSourceFile || 
                        unit instanceof CeylonBinaryUnit)) {
                    pu = ((CeylonUnit)unit).getPhasedUnit();
                }
                
                // TODO : When using binary ceylon archives, add a case here with
                //        unit instanceof CeylonBinaryUnit
                //        And perform the same sort of thing as for ExternalSourceFile :
                //           -> return the associated source PhasedUnit if any 
                
                if (pu!=null) {
                    return pu.getCompilationUnit();
                }
                return null;
            }
        }
    }

    public static String toString(Node term, List<CommonToken> theTokens) {
        Integer start = term.getStartIndex();
        int length = term.getStopIndex()-start+1;
        Region region = new Region(start, length);
        StringBuilder exp = new StringBuilder();
        for (Iterator<CommonToken> ti = getTokenIterator(theTokens, region); 
                ti.hasNext();) {
            CommonToken token = ti.next();
            int type = token.getType();
            String text = token.getText();
            if (type==LIDENTIFIER &&
                    getTokenLength(token)>text.length()) {
                exp.append("\\i");
            }
            else if (type==UIDENTIFIER &&
                    getTokenLength(token)>text.length()) {
                exp.append("\\I"); 
            }
            exp.append(text);
        }
        return exp.toString();
    }

    public static int getTokenLength(CommonToken token) {
        return token.getStopIndex()-token.getStartIndex()+1;
    }

    public static String[] nameProposals(Node node) {
        Set<String> names = new LinkedHashSet<String>();
        Node identifyingNode = node;
        if (identifyingNode instanceof Tree.Expression) {
            identifyingNode = ((Tree.Expression) identifyingNode).getTerm();
        }
        if (identifyingNode instanceof Tree.InvocationExpression) {
            identifyingNode = ((Tree.InvocationExpression) identifyingNode)
                    .getPrimary();
        }
        
        if (identifyingNode instanceof Tree.QualifiedMemberOrTypeExpression) {
            Tree.QualifiedMemberOrTypeExpression qmte = 
                    (Tree.QualifiedMemberOrTypeExpression) identifyingNode;
            Declaration d = qmte.getDeclaration();
            if (d!=null) {
                addNameProposals(names, d, false);
            }
        }
        if (identifyingNode instanceof Tree.SumOp) {
            names.add("sum");
        }
        else if (identifyingNode instanceof Tree.DifferenceOp) {
            names.add("difference");
        }
        else if (identifyingNode instanceof Tree.ProductOp) {
            names.add("product");
        }
        else if (identifyingNode instanceof Tree.QuotientOp) {
            names.add("ratio");
        }
        else if (identifyingNode instanceof Tree.RemainderOp) {
            names.add("remainder");
        }
        else if (identifyingNode instanceof Tree.UnionOp) {
            names.add("union");
        }
        else if (identifyingNode instanceof Tree.IntersectionOp) {
            names.add("intersection");
        }
        else if (identifyingNode instanceof Tree.ComplementOp) {
            names.add("complement");
        }
        else if (identifyingNode instanceof Tree.RangeOp) {
            names.add("range");
        }
        else  if (identifyingNode instanceof Tree.EntryOp) {
            names.add("entry");
        }

        if (identifyingNode instanceof Tree.Term) {
            ProducedType type = ((Tree.Term) node).getTypeModel();
            if (!Util.isTypeUnknown(type)) {
                TypeDeclaration d = type.getDeclaration();
                if (d instanceof ClassOrInterface || 
                    d instanceof TypeParameter) {
                    addNameProposals(names, d, false);
                }
                if (node.getUnit().isIterableType(type)) {
                    ProducedType iteratedType = node.getUnit().getIteratedType(type);
                    TypeDeclaration itd = iteratedType.getDeclaration();
                    if (itd instanceof ClassOrInterface || 
                        itd instanceof TypeParameter) {
                        addNameProposals(names, itd, true);
                    }
                }
            }
        }
        if (names.isEmpty()) {
            names.add("it");
        }
        return names.toArray(NO_STRINGS);
    }

    private static void addNameProposals(Set<String> names, Declaration d,
            boolean plural) {
        addNameProposals(names, plural, d.getName());
    }

    public static void addNameProposals(Set<String> names, boolean plural,
            String tn) {
        String name = Character.toLowerCase(tn.charAt(0)) + tn.substring(1);
        Matcher matcher = IDPATTERN.matcher(name);
        while (matcher.find()) {
            int loc = matcher.start(2);
            String initial = name.substring(matcher.start(1), loc);
            if (Character.isLowerCase(name.charAt(0))) {
                initial = initial.toLowerCase();
            }
            String subname = initial + name.substring(loc);
            if (plural) subname += "s";
            if (Escaping.KEYWORDS.contains(subname)) {
                names.add("\\i" + subname);
            }
            else {
                names.add(subname);
            }
        }
    }

    public static Tree.SpecifierOrInitializerExpression getDefaultArgSpecifier(
            Tree.Parameter p) {
        if (p instanceof Tree.ValueParameterDeclaration) {
            Tree.AttributeDeclaration pd = (Tree.AttributeDeclaration)
                    ((Tree.ValueParameterDeclaration) p).getTypedDeclaration();
            return pd.getSpecifierOrInitializerExpression();
        }
        else if (p instanceof Tree.FunctionalParameterDeclaration) {
            Tree.MethodDeclaration pd = (Tree.MethodDeclaration)
                    ((Tree.FunctionalParameterDeclaration) p).getTypedDeclaration();
            return pd.getSpecifierExpression();
        }
        else if (p instanceof Tree.InitializerParameter) {
            return ((Tree.InitializerParameter) p).getSpecifierExpression();
        }
        else {
            return null;
        }
    }

}
