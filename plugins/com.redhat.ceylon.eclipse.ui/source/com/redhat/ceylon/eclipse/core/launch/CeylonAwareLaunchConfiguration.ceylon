import ceylon.collection {
    HashSet
}
import ceylon.interop.java {
    javaString,
    createJavaObjectArray,
    javaClassFromInstance,
    javaClass
}

import com.redhat.ceylon.cmr.ceylon {
    CeylonUtils {
        CeylonRepoManagerBuilder
    }
}
import com.redhat.ceylon.common {
    Versions
}
import com.redhat.ceylon.eclipse.core.builder {
    CeylonBuilder
}
import com.redhat.ceylon.eclipse.core.model {
    ceylonModel
}
import com.redhat.ceylon.ide.common.platform {
    platformUtils,
    Status
}
import com.redhat.ceylon.tools.classpath {
    CeylonClasspathTool
}
import com.redhat.ceylon.tools.moduleloading {
    ToolModuleLoader
}

import java.io {
    File
}
import java.lang {
    JString=String,
    ObjectArray
}
import java.util {
    Arrays
}

import org.eclipse.core.runtime {
    IProgressMonitor
}
import org.eclipse.debug.core {
    ILaunch,
    ILaunchConfiguration,
    DebugPlugin {
        parseArguments,
        renderArguments
    },
    DebugEvent
}
import org.eclipse.debug.core.model {
    ILaunchConfigurationDelegate
}
import org.eclipse.debug.core.sourcelookup {
    ISourceLookupDirector
}
import org.eclipse.jdt.core {
    IJavaProject
}
import org.eclipse.jdt.internal.launching {
    JavaRemoteApplicationLaunchConfigurationDelegate
}
import org.eclipse.jdt.junit.launcher {
    JUnitLaunchConfigurationDelegate
}
import org.eclipse.jdt.launching {
    JavaLaunchDelegate
}
import org.eclipse.pde.internal.launching.launcher {
    VMHelper
}
import org.eclipse.pde.internal.launching.sourcelookup {
    PDESourceLookupDirector
}
import org.eclipse.pde.launching {
    EclipseApplicationLaunchConfiguration,
    PDEJUnitLaunchConfigurationDelegate=JUnitLaunchConfigurationDelegate
}

shared void setDefaultLaunchDelegateToNonCeylonAware() {
    try {
        value ceylonDelegatesClasses = [
        javaClass<CeylonAwareJavaLaunchDelegate>(),
        javaClass<CeylonAwareJavaRemoteApplicationLaunchConfigurationDelegate>(),
        javaClass<CeylonAwareJUnitLaunchConfigurationDelegate>(),
        javaClass<CeylonAwareEclipseApplicationLaunchConfiguration>(),
        javaClass<CeylonAwarePDEJUnitLaunchConfigurationDelegate>(),
        javaClass<CeylonAwareSWTBotJUnitLaunchConfigurationDelegate>()
        ];
        
        value launchManager = DebugPlugin.default.launchManager;
        for (type in launchManager.launchConfigurationTypes) {
            for (modeCombination in type.supportedModeCombinations) {
                value delegates = type.getDelegates(modeCombination);
                if (delegates.size != 2 ||
                    type.getPreferredDelegate(modeCombination) exists) {
                    continue;
                }
                value delegatesWithClasses = {
                    for (delegate in delegates)
                    delegate -> javaClassFromInstance(delegate.delegate)
                };
                if (delegatesWithClasses.any((delegate -> clazz) 
                    => clazz in ceylonDelegatesClasses)) {
                    value originalDelegate = delegatesWithClasses.find((delegate -> clazz) 
                        => ! clazz in ceylonDelegatesClasses)?.key;
                    if (exists originalDelegate) {
                        type.setPreferredDelegate(modeCombination, originalDelegate);
                    }
                }
            }
        }
    } catch(Exception e) {
        platformUtils.log(Status._WARNING, "Error when setting the default launch configurations", e);
    }
}


shared interface CeylonAwareLaunchConfigurationDelegate 
        of CeylonAwareJavaLaunchDelegate
        | CeylonAwareJavaRemoteApplicationLaunchConfigurationDelegate
        | CeylonAwareJUnitLaunchConfigurationDelegate
        | CeylonAwareEclipseApplicationLaunchConfiguration
        | CeylonAwarePDEJUnitLaunchConfigurationDelegate
        | CeylonAwareSWTBotJUnitLaunchConfigurationDelegate
        satisfies ILaunchConfigurationDelegate {
    shared default String overridenSourcePathComputerId
            => "com.redhat.ceylon.eclipse.ui.launching.sourceLookup.ceylonSourcePathComputer";

    shared default ISourceLookupDirector overridenSourceLocator()
            => CeylonSourceLookupDirector();

    shared formal void originalLaunch(
        ILaunchConfiguration configuration,
        String mode,
        ILaunch launch,
        IProgressMonitor monitor);

    shared void overrideSourceLocator(
        ILaunchConfiguration configuration,
        ILaunch launch) {
        value sourceLocator = overridenSourceLocator();
        sourceLocator.sourcePathComputer =
                DebugPlugin.default
                .launchManager
                .getSourcePathComputer(overridenSourcePathComputerId);
        sourceLocator.initializeDefaults(configuration);
        launch.sourceLocator = sourceLocator;
    }

    shared actual default void launch(
        ILaunchConfiguration configuration,
        String mode,
        ILaunch launch,
        IProgressMonitor monitor) {
        overrideSourceLocator(configuration, launch);
        originalLaunch(configuration, mode, launch, monitor);
    }
}

String[] requiredRuntimeJars = [
    "ceylon.bootstrap",
    "com.redhat.ceylon.module-resolver",
    "com.redhat.ceylon.common",
    "com.redhat.ceylon.model",
    "org.jboss.modules"
];


shared interface ClassPathEnricher {
    
    shared ObjectArray<JString> enrichClassPath(ObjectArray<JString> original, 
            ILaunchConfiguration launchConfig) {
        
        IJavaProject? javaProject = getTheJavaProject(launchConfig);
        if (!exists javaProject) {
            return original;
        }
        value project = javaProject.project;
        
        value classpathEntries = HashSet<String>();
        value ceylonProjects 
                = { for (p in project.referencedProjects)
                    if (exists cp = ceylonModel.getProject(p))
                    cp };
        for (referencedProject in ceylonProjects) {
            
            value repoManagerBuilder = CeylonRepoManagerBuilder()
                    .offline(referencedProject.configuration.offline)
                        .cwd(referencedProject.rootDirectory)
                        .systemRepo(referencedProject.systemRepository)
                        .outRepo(CeylonBuilder.getCeylonModulesOutputDirectory(
                            referencedProject.ideArtifact).absolutePath)
                        .extraUserRepos(Arrays.asList(
                            for (p in referencedProject.referencedCeylonProjects)
                            javaString(p.ceylonModulesOutputDirectory.absolutePath)))
                        .logger(platformUtils.cmrLogger)
                        .isJDKIncluded(false);
            
            if (exists modules = referencedProject.modules) {
                object tool extends CeylonClasspathTool() {
                    shared ToolModuleLoader theLoader => super.loader;
                    loadModule(String? namespace, String? moduleName, String? moduleVersion) => 
                            super.loadModule(namespace, moduleName, moduleVersion);
                    createRepositoryManagerBuilder() => repoManagerBuilder;
                }
                tool.initialize(null);
                tool.loadModule(null, "com.redhat.ceylon.java.main", Versions.ceylonVersionNumber);
                for (m in modules) {
                    if (m.isProjectModule && !m.defaultModule) {
                        tool.loadModule(m.namespace, m.nameAsString, m.version);
                    }
                }
                tool.theLoader.resolve();
                tool.theLoader.visitModules((m) { 
                    if (exists file = m.artifact.artifact()) {
                        classpathEntries.add(file.absolutePath);
                    }
                });
                value defaultCar 
                        = File(CeylonBuilder.getCeylonModulesOutputDirectory(
                    referencedProject.ideArtifact), "default.car");
                if (defaultCar.\iexists()) {
                    classpathEntries.add(defaultCar.absolutePath);
                }
            }
        }
        classpathEntries.addAll { for (cp in original) cp.string };
        return createJavaObjectArray(classpathEntries.map(javaString));
    }
    
    shared formal IJavaProject getTheJavaProject(ILaunchConfiguration launchConfiguration);
}

shared class CeylonAwareJavaLaunchDelegate()
        extends JavaLaunchDelegate()
        satisfies CeylonAwareLaunchConfigurationDelegate
                    & ClassPathEnricher & CeylonDebuggingSupportEnabled {

    launch(
        ILaunchConfiguration c,
        String m,
        ILaunch l,
        IProgressMonitor p)
            => (super of CeylonAwareLaunchConfigurationDelegate)
            .launch(c, m, l, p);

    originalLaunch(
        ILaunchConfiguration c,
        String m,
        ILaunch l,
        IProgressMonitor p)
            => (super of JavaLaunchDelegate)
            .launch(c, m, l, p);
    
    getClasspath(ILaunchConfiguration launchConfiguration) 
            => enrichClassPath(super.getClasspath(launchConfiguration), launchConfiguration);
    getTheJavaProject(ILaunchConfiguration launchConfiguration) 
            => getJavaProject(launchConfiguration);
    
    
    handleDebugEvents(ObjectArray<DebugEvent> _DebugEventArray) =>
            (super of CeylonDebuggingSupportEnabled).handleDebugEvents(_DebugEventArray);
    
    getOriginalVMArguments(ILaunchConfiguration configuration) => 
            (super of JavaLaunchDelegate).getVMArguments(configuration);
    
    getVMArguments(ILaunchConfiguration configuration) => 
            (super of CeylonDebuggingSupportEnabled).getOverridenVMArguments(configuration);
    
    getOriginalVMRunner(ILaunchConfiguration configuration, String mode) => 
            (super of JavaLaunchDelegate).getVMRunner(configuration, mode);
    
    getVMRunner(ILaunchConfiguration configuration, String mode) => 
            (super of CeylonDebuggingSupportEnabled).getOverridenVMRunner(configuration, mode);
    
    getStartLocation(ILaunchConfiguration configuration) => null;
    
    getOriginalVMInstall(ILaunchConfiguration configuration) =>
            (super of JavaLaunchDelegate).getVMInstall(configuration);
    
    shouldStopInMain(ILaunchConfiguration configuration) => false;
}

shared class CeylonAwareJavaRemoteApplicationLaunchConfigurationDelegate()
        extends JavaRemoteApplicationLaunchConfigurationDelegate()
        satisfies CeylonAwareLaunchConfigurationDelegate {

    shared actual void launch(
        ILaunchConfiguration c,
        String m,
        ILaunch l,
        IProgressMonitor p)
            => (super of CeylonAwareLaunchConfigurationDelegate)
            .launch(c, m, l, p);

    shared actual void originalLaunch(
        ILaunchConfiguration c,
        String m,
        ILaunch l,
        IProgressMonitor p)
            => (super of JavaRemoteApplicationLaunchConfigurationDelegate)
            .launch(c, m, l, p);
}

shared class CeylonAwareJUnitLaunchConfigurationDelegate()
        extends JUnitLaunchConfigurationDelegate()
        satisfies CeylonAwareLaunchConfigurationDelegate
                    & ClassPathEnricher {

    launch(
        ILaunchConfiguration c,
        String m,
        ILaunch l,
        IProgressMonitor p)
            => (super of CeylonAwareLaunchConfigurationDelegate)
            .launch(c, m, l, p);

    originalLaunch(
        ILaunchConfiguration c,
        String m,
        ILaunch l,
        IProgressMonitor p)
            => (super of JUnitLaunchConfigurationDelegate)
            .launch(c, m, l, p);

    getClasspath(ILaunchConfiguration launchConfiguration) 
            => enrichClassPath(super.getClasspath(launchConfiguration), 
                               launchConfiguration);
    getTheJavaProject(ILaunchConfiguration launchConfiguration) 
            => getJavaProject(launchConfiguration);
}

shared class CeylonAwareEclipseApplicationLaunchConfiguration()
        extends EclipseApplicationLaunchConfiguration()
        satisfies CeylonAwareLaunchConfigurationDelegate &
        CeylonDebuggingSupportEnabled {

    overridenSourceLocator()
            => object extends PDESourceLookupDirector()
            satisfies CeylonAwareSourceLookupDirector {};

    launch(
        ILaunchConfiguration c,
        String m,
        ILaunch l,
        IProgressMonitor p)
            => (super of CeylonAwareLaunchConfigurationDelegate)
            .launch(c, m, l, p);

    originalLaunch(
        ILaunchConfiguration c,
        String m,
        ILaunch l,
        IProgressMonitor p)
            => (super of EclipseApplicationLaunchConfiguration)
            .launch(c, m, l, p);
    
    handleDebugEvents(ObjectArray<DebugEvent> _DebugEventArray) =>
            (super of CeylonDebuggingSupportEnabled).handleDebugEvents(_DebugEventArray);
    
    getOriginalVMArguments(ILaunchConfiguration configuration) => 
            renderArguments(
                (super of EclipseApplicationLaunchConfiguration).getVMArguments(configuration), null);
    
    getVMArguments(ILaunchConfiguration configuration) => 
            parseArguments(
                (super of CeylonDebuggingSupportEnabled).getOverridenVMArguments(configuration));
    
    getOriginalVMRunner(ILaunchConfiguration configuration, String mode) => 
            (super of EclipseApplicationLaunchConfiguration).getVMRunner(configuration, mode);
    
    getVMRunner(ILaunchConfiguration configuration, String mode) => 
            (super of CeylonDebuggingSupportEnabled).getOverridenVMRunner(configuration, mode);
    
    getStartLocation(ILaunchConfiguration configuration) => null;
    
    getOriginalVMInstall(ILaunchConfiguration configuration) => VMHelper.createLauncher(configuration);
    
    shouldStopInMain(ILaunchConfiguration configuration) => false;
}

shared class CeylonAwarePDEJUnitLaunchConfigurationDelegate()
        extends PDEJUnitLaunchConfigurationDelegate()
        satisfies CeylonAwareLaunchConfigurationDelegate {

    overridenSourceLocator()
            => object extends PDESourceLookupDirector()
            satisfies CeylonAwareSourceLookupDirector {};

    launch(
        ILaunchConfiguration c,
        String m,
        ILaunch l,
        IProgressMonitor p)
            => (super of CeylonAwareLaunchConfigurationDelegate)
            .launch(c, m, l, p);

    originalLaunch(
        ILaunchConfiguration c,
        String m,
        ILaunch l,
        IProgressMonitor p)
            => (super of PDEJUnitLaunchConfigurationDelegate)
            .launch(c, m, l, p);
}

shared class CeylonAwareSWTBotJUnitLaunchConfigurationDelegate()
        extends PDEJUnitLaunchConfigurationDelegate()
        satisfies CeylonAwareLaunchConfigurationDelegate {

    overridenSourceLocator()
            => object extends PDESourceLookupDirector()
            satisfies CeylonAwareSourceLookupDirector {};

    launch(
        ILaunchConfiguration c,
        String m,
        ILaunch l,
        IProgressMonitor p)
            => (super of CeylonAwareLaunchConfigurationDelegate)
            .launch(c, m, l, p);

    originalLaunch(
        ILaunchConfiguration c,
        String m,
        ILaunch l,
        IProgressMonitor p)
            => (super of PDEJUnitLaunchConfigurationDelegate)
            .launch(c, m, l, p);

    getApplication(ILaunchConfiguration configuration)
            => "org.eclipse.swtbot.eclipse.core.swtbottestapplication";
}

