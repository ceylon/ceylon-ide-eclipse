"""Test module for module
   [[com.redhat.ceylon.eclipse|module com.redhat.ceylon.eclipse]]."""
by("David Festal")
native("jvm")
module test.com.redhat.ceylon.eclipse "1.3.3" {
    import com.redhat.ceylon.eclipse "1.3.3";
    import "com.redhat.ceylon.module-resolver" "1.3.3";
    import java.base "7";
    import ceylon.interop.java "1.3.3";
    import ceylon.test "1.3.3";
}
