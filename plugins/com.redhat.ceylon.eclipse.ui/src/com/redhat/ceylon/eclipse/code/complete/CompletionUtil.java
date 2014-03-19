package com.redhat.ceylon.eclipse.code.complete;

import static com.redhat.ceylon.eclipse.code.outline.CeylonLabelProvider.ID_STYLER;
import static com.redhat.ceylon.eclipse.code.outline.CeylonLabelProvider.KW_STYLER;
import static com.redhat.ceylon.eclipse.code.outline.CeylonLabelProvider.PACKAGE_STYLER;
import static com.redhat.ceylon.eclipse.code.outline.CeylonLabelProvider.TYPE_ID_STYLER;
import static com.redhat.ceylon.eclipse.code.outline.CeylonLabelProvider.VERSION_STYLER;
import static java.lang.Character.isLowerCase;
import static java.lang.Character.isUpperCase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.StringTokenizer;

import org.antlr.runtime.CommonToken;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.viewers.StyledString;

import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.DeclarationWithProximity;
import com.redhat.ceylon.compiler.typechecker.model.Functional;
import com.redhat.ceylon.compiler.typechecker.model.Parameter;
import com.redhat.ceylon.compiler.typechecker.model.ParameterList;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.model.Scope;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.Unit;
import com.redhat.ceylon.compiler.typechecker.model.Value;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.AnnotationList;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.CompilationUnit;
import com.redhat.ceylon.compiler.typechecker.tree.Util;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;
import com.redhat.ceylon.eclipse.code.parse.CeylonParseController;
import com.redhat.ceylon.eclipse.util.Escaping;
import com.redhat.ceylon.eclipse.util.Nodes;

public class CompletionUtil {

    public static List<Declaration> overloads(Declaration dec) {
        if (dec instanceof Functional && ((Functional) dec).isAbstraction()) {
            return ((Functional) dec).getOverloads();
        }
        else {
            return Collections.singletonList(dec);
        }
    }

    static List<Parameter> getParameters(ParameterList pl,
            boolean includeDefaults, boolean namedInvocation) {
        List<Parameter> ps = pl.getParameters();
        if (includeDefaults) {
            return ps;
        }
        else {
            List<Parameter> list = new ArrayList<Parameter>();
            for (Parameter p: ps) {
                if (!p.isDefaulted() || 
                        (namedInvocation && 
                                p==ps.get(ps.size()-1) && 
                                p.getModel() instanceof Value &&
                                p.getType()!=null &&
                                p.getDeclaration().getUnit()
                                        .isIterableParameterType(p.getType()))) {
                    list.add(p);
                }
            }
            return list;
        }
    }

    static String fullPath(int offset, String prefix,
            Tree.ImportPath path) {
        StringBuilder fullPath = new StringBuilder();
        if (path!=null) {
            fullPath.append(Util.formatPath(path.getIdentifiers()));
            fullPath.append('.');
            fullPath.setLength(offset-path.getStartIndex()-prefix.length());
        }
        return fullPath.toString();
    }

    static boolean isPackageDescriptor(CeylonParseController cpc) {
        return cpc.getRootNode() != null && 
                cpc.getRootNode().getUnit() != null &&
                cpc.getRootNode().getUnit().getFilename().equals("package.ceylon"); 
    }

    static boolean isModuleDescriptor(CeylonParseController cpc) {
        return cpc.getRootNode() != null && 
                cpc.getRootNode().getUnit() != null &&
                cpc.getRootNode().getUnit().getFilename().equals("module.ceylon"); 
    }

    static boolean isEmptyModuleDescriptor(CeylonParseController cpc) {
        return isModuleDescriptor(cpc) && 
                cpc.getRootNode() != null && 
                cpc.getRootNode().getModuleDescriptors().isEmpty(); 
    }

    static boolean isEmptyPackageDescriptor(CeylonParseController cpc) {
        return cpc.getRootNode() != null &&
                cpc.getRootNode().getUnit() != null &&
                cpc.getRootNode().getUnit().getFilename().equals("package.ceylon") && 
                cpc.getRootNode().getPackageDescriptors().isEmpty();
    }

    public static OccurrenceLocation getOccurrenceLocation(Tree.CompilationUnit cu, Node node) {
        if (node.getToken()==null) return null;
        FindOccurrenceLocationVisitor visitor = new FindOccurrenceLocationVisitor(node);
        cu.visit(visitor);
        return visitor.getOccurrenceLocation();
    }

    static int nextTokenType(final CeylonParseController cpc,
            final CommonToken token) {
        for (int i=token.getTokenIndex()+1; i<cpc.getTokens().size(); i++) {
            CommonToken tok = cpc.getTokens().get(i);
            if (tok.getChannel()!=CommonToken.HIDDEN_CHANNEL) {
                return tok.getType();
            }
        }
        return -1;
    }

    /**
     * BaseMemberExpressions in Annotations have funny lying
     * scopes, but we can extract the real scope out of the
     * identifier! (Yick)
     */
    static Scope getRealScope(final Node node, CompilationUnit cu) {
        
        class FindScopeVisitor extends Visitor {
            Scope scope;
            public void visit(Tree.Declaration that) {
                super.visit(that);
                AnnotationList al = that.getAnnotationList();
                if (al!=null) {
                    for (Tree.Annotation a: al.getAnnotations()) {
                        Integer i = a.getPrimary().getStartIndex();
                        Integer j = node.getStartIndex();
                        if (i.intValue()==j.intValue()) {
                            scope = that.getDeclarationModel().getScope();
                        }
                    }
                }
            }
            
            public void visit(Tree.DocLink that) {
                super.visit(that);
                scope = ((Tree.DocLink)node).getPkg();
            }
        };
        FindScopeVisitor fsv = new FindScopeVisitor();
        fsv.visit(cu);
        return fsv.scope==null ? node.getScope() : fsv.scope;
    }

    static int getLine(final int offset, ITextViewer viewer) {
        int line=-1;
        try {
            line = viewer.getDocument().getLineOfOffset(offset);
        }
        catch (BadLocationException e) {
            e.printStackTrace();
        }
        return line;
    }

    public static boolean isInBounds(List<ProducedType> upperBounds, ProducedType t) {
        boolean ok = true;
        for (ProducedType ub: upperBounds) {
            if (!t.isSubtypeOf(ub) &&
                    !(ub.containsTypeParameters() &&
                            t.getDeclaration().inherits(ub.getDeclaration()))) {
                ok = false;
                break;
            }
        }
        return ok;
    }

    public static List<DeclarationWithProximity> getSortedProposedValues(Scope scope, Unit unit) {
        List<DeclarationWithProximity> results = new ArrayList<DeclarationWithProximity>(
                scope.getMatchingDeclarations(unit, "", 0).values());
        Collections.sort(results, new Comparator<DeclarationWithProximity>() {
            public int compare(DeclarationWithProximity x, DeclarationWithProximity y) {
                if (x.getProximity()<y.getProximity()) return -1;
                if (x.getProximity()>y.getProximity()) return 1;
                int c = x.getDeclaration().getName().compareTo(y.getDeclaration().getName());
                if (c!=0) return c;  
                return x.getDeclaration().getQualifiedNameString()
                        .compareTo(y.getDeclaration().getQualifiedNameString());
            }
        });
        return results;
    }

    static boolean isIgnoredLanguageModuleClass(final String name) {
        return name.equals("String") ||
                name.equals("Integer") ||
                name.equals("Float") ||
                name.equals("Character");
    }

    static boolean isIgnoredLanguageModuleValue(final String name) {
        return name.equals("process") ||
                name.equals("runtime") ||
                name.equals("system") ||
                name.equals("operatingSystem") ||
                name.equals("language") ||
                name.equals("emptyIterator") ||
                name.equals("infinity") ||
                name.endsWith("IntegerValue") ||
                name.equals("finished");
    }

    static boolean isIgnoredLanguageModuleType(TypeDeclaration td) {
        return !td.getName().equals("Object") && 
                !td.getName().equals("Anything") &&
                !td.getName().equals("String") &&
                !td.getName().equals("Integer") &&
                !td.getName().equals("Character") &&
                !td.getName().equals("Float") &&
                !td.getName().equals("Boolean");
    }

    public static void styleProposal(StyledString result, String string) {
        StringTokenizer tokens = new StringTokenizer(string, " ()<>.*+?,{}[]", true);
        while (tokens.hasMoreTokens()) {
            String token = tokens.nextToken();
            if (isUpperCase(token.charAt(0))) {
                result.append(token, TYPE_ID_STYLER);
            }
            else if (isLowerCase(token.charAt(0))) {
                if (Escaping.KEYWORDS.contains(token)) {
                    result.append(token, KW_STYLER);
                }
                else if (token.contains(".")) {
                    result.append(token, PACKAGE_STYLER);
                }
                else {
                    result.append(token, ID_STYLER);
                }
            }
            else if (token.charAt(0)=='\"') {
                result.append(token, VERSION_STYLER);
            }
            else {
                result.append(token);
            }
        }
    }

    public static String getInitalValueDescription(Declaration dec, CeylonParseController cpc) {
        Node refnode = Nodes.getReferencedNode(dec, cpc);
        Tree.SpecifierOrInitializerExpression sie = null;
        String arrow = null;
        if (refnode instanceof Tree.AttributeDeclaration) {
            sie = ((Tree.AttributeDeclaration) refnode).getSpecifierOrInitializerExpression();
            arrow = " = ";
        }
        else if (refnode instanceof Tree.MethodDeclaration) {
            sie = ((Tree.MethodDeclaration) refnode).getSpecifierExpression();
            arrow = " => ";
        }
        if (sie!=null) {
            if (sie.getExpression()!=null) {
                Tree.Term term = sie.getExpression().getTerm();
                if (term instanceof Tree.Literal) {
                    return arrow + term.getToken().getText();
                }
                else if (term instanceof Tree.BaseMemberOrTypeExpression) {
                    Tree.BaseMemberOrTypeExpression bme = 
                            (Tree.BaseMemberOrTypeExpression) term;
                    if (bme.getIdentifier()!=null) {
                        return arrow + bme.getIdentifier().getText();
                    }
                }
                else if (term.getUnit().equals(cpc.getRootNode().getUnit())) {
                    return arrow + Nodes.toString(term, cpc.getTokens());
                }
                return arrow + "...";
            }
        }
        return "";
    }

    public static String getDefaultValueDescription(Parameter p, 
            CeylonParseController cpc) {
        if (p.isDefaulted()) {
            if (p.getModel() instanceof Functional) {
                return " => ...";
            }
            else {
                return getInitalValueDescription(p.getModel(), cpc);
            }
        }
        else {
            return "";
        }
    }

}
