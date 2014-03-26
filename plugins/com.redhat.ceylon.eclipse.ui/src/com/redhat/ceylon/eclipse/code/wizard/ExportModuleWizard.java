package com.redhat.ceylon.eclipse.code.wizard;

import static com.redhat.ceylon.eclipse.code.wizard.ExportModuleWizardPage.CLEAN_BUILD_BEFORE_EXPORT;
import static com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.getCeylonModulesOutputDirectory;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.eclipse.core.resources.IncrementalProjectBuilder.CLEAN_BUILD;
import static org.eclipse.core.resources.ResourcesPlugin.getWorkspace;
import static org.eclipse.ui.PlatformUI.getWorkbench;

import java.io.File;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.IExportWizard;
import org.eclipse.ui.IWorkbench;

import com.redhat.ceylon.eclipse.ui.CeylonPlugin;

public class ExportModuleWizard extends Wizard implements IExportWizard {

    private IStructuredSelection selection;
    private ExportModuleWizardPage page;
    
    public ExportModuleWizard() {
        setDialogSettings(CeylonPlugin.getInstance().getDialogSettings());
    }
    
    @Override
    public void init(IWorkbench workbench, IStructuredSelection selection) {
        this.selection = selection;
    }

    @Override
    public void addPages() {
        super.addPages();
        if (page == null) {
            IJavaElement selectedElement = getSelectedElement();
            String repoPath=null;
            IProject project=null;
            if (selectedElement!=null) {
                project = selectedElement.getJavaProject().getProject();
                /*List<String> paths = getCeylonRepositories(project.getProject());
                if (paths!=null) {
                    for (int i=paths.size()-1; i>=0; i--) {
                        String path = paths.get(i);
                        if (!path.startsWith("http://")) {
                            repoPath = path;
                            break;
                        }
                    }
                }*/
            }
            if (repoPath==null) repoPath = getDefaultRepositoryPath();
            page = new ExportModuleWizardPage(repoPath, project, selectedElement);
            //page.init(selection);
        }
        addPage(page);
    }

    public static String getDefaultRepositoryPath() {
        String repositoryPath = CeylonPlugin.getInstance().getDialogSettings()
                .get("repositoryPath");
        if (repositoryPath==null || repositoryPath.startsWith("http://")) {
            repositoryPath = System.getProperty("user.home") + "/.ceylon/repo";
        }
        return repositoryPath;
    }

    //TODO: fix copy/paste from NewUnitWizardPage
    private IJavaElement getSelectedElement() {
        if (selection!=null && selection.size()==1) {
            Object element = selection.getFirstElement();
            if (element instanceof IFile) {
                return JavaCore.create(((IFile) element).getParent());
            }
            else {
                return (IJavaElement) ((IAdaptable) element)
                        .getAdapter(IJavaElement.class);
            }
        }
        else {
            return null;
        }
    }
    
    private Exception ex;
    
    @Override
    public boolean performFinish() {
        final String repositoryPath = page.getRepositoryPath();
        final IProject project = page.getProject();
        if (project==null) {
            MessageDialog.openError(getShell(), "Export Module Error", 
                    "No Java project selected.");
            return false;
        }
        else {
            /*IProject project = javaProject.getProject();
            List<PhasedUnit> list = CeylonBuilder.getProjectTypeChecker(project)
                .getPhasedUnits().getPhasedUnits();
            Set<String> moduleNames = new HashSet<String>();
            for (PhasedUnit phasedUnit: list) {
                Module module = phasedUnit.getUnit().getPackage().getModule();
                moduleNames.add(module.getNameAsString());
            }*/
            ex = null;
            TableItem[] selectedItems = page.getModules().getSelection();
            final String[] selectedModules = new String[selectedItems.length];
            for (int i=0; i<selectedItems.length; i++) {
                selectedModules[i] = selectedItems[i].getText();
            }
            try {
                Job job = new Job("Exporting modules") {
                    @Override
                    protected IStatus run(IProgressMonitor monitor) {
                        monitor.setTaskName("Exporting modules to repository");
                        getDialogSettings().put(CLEAN_BUILD_BEFORE_EXPORT, page.isClean());
                        if (page.isClean()) {
                            try {
                                project.build(CLEAN_BUILD, monitor);
                            }
                            catch (CoreException e) {
                                ex = e;
                                return Status.CANCEL_STATUS;
                            }
                            yieldRule(monitor);
                        }
                        File outputDir = getCeylonModulesOutputDirectory(project);
                        Path outputPath = Paths.get(outputDir.getAbsolutePath());
                        Path repoPath = Paths.get(repositoryPath);
                        if (!Files.exists(repoPath)) {
                            MessageDialog.openError(getShell(), "Export Module Error", 
                                    "No repository at location: " + repositoryPath);
                            return Status.CANCEL_STATUS;
                        }
                        for (String moduleNameVersion: selectedModules) {
                            int i = moduleNameVersion.indexOf('/');
                            String name = moduleNameVersion.substring(0, i);
                            String version = moduleNameVersion.substring(i+1);
                            String glob = name + '-' + version + ".*";
                            String dir = name.replace('.', File.separatorChar) + File.separatorChar + version;
                            Path repoOutputPath = outputPath.resolve(dir);
                            Path repoModulePath = repoPath.resolve(dir);
                            try {
                                Files.createDirectories(repoModulePath);
                                DirectoryStream<Path> ds = Files.newDirectoryStream(repoOutputPath, glob);
                                try {
                                    for (Path path: ds) {
                                        Files.copy(path, repoModulePath.resolve(path.getFileName()), 
                                                REPLACE_EXISTING);
                                    }
                                }
                                finally {
                                    ds.close();
                                }
                            }
                            catch (Exception e) {
                                ex = e;
                                return Status.CANCEL_STATUS;
                            }
                        }
                        return Status.OK_STATUS;
                    }
                };
                getWorkbench().getProgressService().showInDialog(getShell(), job);
                job.setUser(true);
                job.setRule(getWorkspace().getRuleFactory().buildRule());
                job.schedule();
            } 
            catch (Exception e) {
                e.printStackTrace();
            }
            if (ex!=null) {
                ex.printStackTrace();
                MessageDialog.openError(getShell(), "Export Module Error", 
                        "Error occurred exporting module: " + ex.getMessage());
            }
            persistDefaultRepositoryPath(repositoryPath);
        }
        return true;
    }

    public static void persistDefaultRepositoryPath(String repositoryPath) {
        if (repositoryPath!=null && !repositoryPath.isEmpty()) {
            CeylonPlugin.getInstance().getDialogSettings()
                    .put("repositoryPath", repositoryPath);
        }
    }
    
    /*public static void copyFolder(File src, File dest)
            throws IOException{
        if (src.isDirectory()) {
            if ( !dest.exists() ) dest.mkdir();
            for (String file: src.list()) {
               File srcFile = new File(src, file);
               File destFile = new File(dest, file);
               copyFolder(srcFile, destFile);
            }
        }
        else if (src.getName().endsWith(".car") ||
                src.getName().endsWith(".src") ||
                src.getName().endsWith(".sha1")) {
            FileChannel source = null;
            FileChannel destination = null;
            try {
                source = new FileInputStream(src).getChannel();
                destination = new FileOutputStream(dest).getChannel();
                destination.transferFrom(source, 0, source.size());
            }
            finally {
                if (source != null) {
                    source.close();
                }
                if (destination != null) {
                    destination.close();
                }
            }
            System.out.println("Archive exported from " + src + " to " + dest);
        }
    }*/
    
    @Override
    public boolean canFinish() {
        return page.isPageComplete();
    }
    
}
