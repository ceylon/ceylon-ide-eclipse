import com.redhat.ceylon.eclipse.core.model {
    ceylonModel
}
import com.redhat.ceylon.ide.common.model {
    AnyCeylonProject
}
import com.redhat.ceylon.model.typechecker.model {
    Module
}

import java.io {
    File
}
import java.lang {
    ProcessBuilder
}

import org.eclipse.debug.core {
    ILaunchConfiguration
}
import org.eclipse.jdt.launching {
    IJavaLaunchConfigurationConstants
}

shared class JarPackagingTool(
    shared String type,
    String outputFileName(Module moduleToJar),
    shared ProcessBuilder doCreateFile(File ceylonBinary, File outputFile, AnyCeylonProject ceylonProject, Module moduleToJar, File workingDirectory),
    shared Boolean canStopInMain = false) {
    
    shared Module? getModule(ILaunchConfiguration config) {
        value project = LaunchHelper.getProjectFromName(config.getAttribute(IJavaLaunchConfigurationConstants.attrProjectName, ""));
        if (!exists project) {
            return null;
        }
        value ceylonProject = ceylonModel.getProject(project);
        if (!exists ceylonProject) {
            return null;
        }

        value moduleName = config.getAttribute(ICeylonLaunchConfigurationConstants.attrModuleName, "");
        if (moduleName.empty) {
            return null;
        }
        
        return ceylonProject.modules?.fromProject?.find((m) => "``m.nameAsString``/``m.version``" == moduleName);
    }
    
    shared AnyCeylonProject? getCeylonProject(ILaunchConfiguration config) {
        value project = LaunchHelper.getProjectFromName(config.getAttribute(IJavaLaunchConfigurationConstants.attrProjectName, ""));
        if (!exists project) {
            return null;
        }
        value ceylonProject = ceylonModel.getProject(project);
        if (!exists ceylonProject) {
            return null;
        }
        
        return ceylonProject;
    }

    shared default File? outputFile(ILaunchConfiguration config) {
        value projectRootDirectory = getCeylonProject(config)?.rootDirectory;
        if (!exists projectRootDirectory) {
            return null;
        }
        
        value ideModule = getModule(config);
        if (!exists ideModule) {
            return null;
        }
        
        value toolName = config.getAttribute(ICeylonLaunchConfigurationConstants.attrJarCreationToolName, "");
        if (toolName.empty) {
            return null;
        }
        
        return File(projectRootDirectory, outputFileName(ideModule));
    }    
}