package declarations;

import ceylon.language.Deprecation$annotation;

@Deprecated
public class JavaDeprecated {

    @Deprecated
    public String s;
    
    @Deprecated
    public void m(@Deprecated String p) {}
    
    @com.redhat.ceylon.compiler.java.metadata.Annotations({@com.redhat.ceylon.compiler.java.metadata.Annotation(
            value = "deprecated",
            arguments = {"Foo"})})
    @Deprecation$annotation(description="Foo")
    public void ceylonDeprecation() {}
    
    @com.redhat.ceylon.compiler.java.metadata.Annotations({@com.redhat.ceylon.compiler.java.metadata.Annotation(
            value = "deprecated",
            arguments = {"Foo"})})
    @Deprecation$annotation(description="Foo")
    @Deprecated
    public void bothDeprecation() {}
}
