package com.redhat.ceylon.eclipse.core.launch;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.LaunchConfigurationDelegate;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleConstants;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;

import com.redhat.ceylon.compiler.js.Runner;
import com.redhat.ceylon.eclipse.core.builder.CeylonBuilder;

import static org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME;

public class JsLaunchDelegate extends LaunchConfigurationDelegate {

    private MessageConsole findConsole() {
        ConsolePlugin plugin = ConsolePlugin.getDefault();
        IConsoleManager conman = plugin.getConsoleManager();
        MessageConsole cons = null;
        for (IConsole ccons : conman.getConsoles()) {
            if ("com.redhat.ceylon".equals(ccons.getName())) {
                cons = (MessageConsole)ccons;
                break;
            }
        }
        if (cons == null) {
            cons = new MessageConsole("com.redhat.ceylon", IConsoleConstants.MESSAGE_CONSOLE_TYPE,
                    null, "UTF-8", true);
            conman.addConsoles(new IConsole[]{cons});
        }
        cons.clearConsole();
        return cons;
    }

    @Override
    public void launch(ILaunchConfiguration configuration, String mode,
            ILaunch launch, IProgressMonitor monitor) throws CoreException {

        //Check that JS is enabled for the project
        final String qname = configuration.getAttribute(ATTR_MAIN_TYPE_NAME, "::run");
        final int tipple = qname.indexOf("::");
        final String methname = tipple >= 0 ? qname.substring(tipple+2) : qname;
        final String modname = configuration.getAttribute(ICeylonLaunchConfigurationConstants.ATTR_CEYLON_MODULE, "default");
        final IProject proj = ResourcesPlugin.getWorkspace().getRoot().getProject(
                configuration.getAttribute(ICeylonLaunchConfigurationConstants.ATTR_PROJECT_NAME, (String)null));
        final ArrayList<String> repos = new ArrayList<String>();
        //Add system repo
        repos.add(CeylonBuilder.interpolateVariablesInRepositoryPath(CeylonBuilder.getCeylonSystemRepo(proj)));
        //Add project repos
        repos.addAll(CeylonBuilder.getCeylonRepositories(proj));
        repos.add(CeylonBuilder.getCeylonModulesOutputDirectory(proj).getAbsolutePath());
        PrintStream pout = new PrintStream(findConsole().newOutputStream());
        try {
            Runner.run(repos, modname, methname, pout, configuration.getAttribute(
                    ICeylonLaunchConfigurationConstants.ATTR_JS_DEBUG, false));
        } catch (FileNotFoundException ex) {
            //Install node.js
            System.err.println(ex.getMessage());
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            pout.close();
        }
    }

}
