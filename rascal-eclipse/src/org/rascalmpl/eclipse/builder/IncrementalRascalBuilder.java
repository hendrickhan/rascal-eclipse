package org.rascalmpl.eclipse.builder;

import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.osgi.framework.Bundle;
import org.rascalmpl.eclipse.Activator;
import org.rascalmpl.eclipse.IRascalResources;
import org.rascalmpl.eclipse.editor.IDEServicesModelProvider;
import org.rascalmpl.eclipse.editor.MessagesToMarkers;
import org.rascalmpl.eclipse.preferences.RascalPreferences;
import org.rascalmpl.eclipse.util.ProjectConfig;
import org.rascalmpl.eclipse.util.RascalEclipseManifest;
import org.rascalmpl.eclipse.util.ResourcesToModules;
import org.rascalmpl.interpreter.load.IRascalSearchPathContributor;
import org.rascalmpl.interpreter.load.RascalSearchPath;
import org.rascalmpl.library.experiments.Compiler.RVM.Interpreter.java2rascal.Java2Rascal;
import org.rascalmpl.library.lang.rascal.boot.IKernel;
import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.uri.ProjectURIResolver;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.values.ValueFactoryFactory;

import io.usethesource.impulse.builder.MarkerCreator;
import io.usethesource.impulse.runtime.RuntimePlugin;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;

/** 
 * This builder manages the execution of the Rascal compiler on all Rascal files which have been changed while editing them in Eclipse.
 * It also interacts with Project Clean actions to clear up files and markers on request.  
 */
public class IncrementalRascalBuilder extends IncrementalProjectBuilder {
    // A kernel is 100Mb, so we can't have one for every project; that's why it's static:
    private static IKernel kernel;
	private static PrintStream out;
    private static PrintStream err;
    private static IValueFactory vf;
    private static List<String> binaryExtension = Arrays.asList("imps","rvm", "rvmx", "tc","sig","sigs");
    
    private ISourceLocation projectLoc;
    private PathConfig pathConfig;

    static {
        synchronized(IncrementalRascalBuilder.class){ 
            try {
                out = new PrintStream(RuntimePlugin.getInstance().getConsoleStream());
                err = new PrintStream(RuntimePlugin.getInstance().getConsoleStream());
                vf = ValueFactoryFactory.getValueFactory();
                
                Bundle rascalBundle = Activator.getInstance().getBundle();
                URL entry = FileLocator.toFileURL(rascalBundle.getEntry("lib/rascal.jar"));
                ISourceLocation rascalJarLoc = vf.sourceLocation(entry.toURI());
                PathConfig pcfg = new PathConfig()
                        .addJavaCompilerPath(rascalJarLoc)
                        .addClassloader(rascalJarLoc);
                        
                kernel = Java2Rascal.Builder
                        .bridge(vf, pcfg, IKernel.class)
                        .stderr(err)
                        .stdout(out)
                        .build();
            } catch (IOException | URISyntaxException e) {
                Activator.log("could not initialize incremental Rascal builder", e);
            }
        }
    }
    
    public IncrementalRascalBuilder() {
        
	}

	@Override
	protected void clean(IProgressMonitor monitor) throws CoreException {
		cleanBinFiles(monitor);
		cleanProblemMarkers(monitor);
	}

    private void cleanProblemMarkers(IProgressMonitor monitor) throws CoreException {
        RascalEclipseManifest manifest = new RascalEclipseManifest();
		 
        for (String src : manifest.getSourceRoots(getProject())) {
            getProject().findMember(src).accept(new IResourceVisitor() {
                @Override
                public boolean visit(IResource resource) throws CoreException {
                    if (IRascalResources.RASCAL_EXT.equals(resource.getFileExtension())) {
                        resource.deleteMarkers(IRascalResources.ID_RASCAL_MARKER, true, IResource.DEPTH_ONE);
                        return false;
                    }
                    
                    return true;
                }
            });
        }
    }

    private void cleanBinFiles(IProgressMonitor monitor) throws CoreException {
        getProject().findMember(ProjectConfig.BIN_FOLDER).accept(new IResourceVisitor() {
            @Override
            public boolean visit(IResource resource) throws CoreException {
                if (binaryExtension.contains(resource.getFileExtension())) {
                    resource.delete(true, monitor);
                    return false;
                }
                
                return true;
            }
        });
    }
	
	@Override
	protected IProject[] build(int kind, Map<String, String> args, IProgressMonitor monitor) throws CoreException {
	    switch (kind) {
	    case INCREMENTAL_BUILD:
	    case AUTO_BUILD:
	        buildIncremental(getDelta(getProject()), monitor);
	        break;
	    case FULL_BUILD:
	        buildMain(monitor);
	        break;
	    }
	    
	    // TODO: return project this project depends on?
		return new IProject[0];
	}

	private void buildMain(IProgressMonitor monitor) throws CoreException {
	    IDEServicesModelProvider.getInstance().invalidateEverything();
	    
	    initializeParameters(false);
	    
	    RascalSearchPath p = new RascalSearchPath();
	    p.addPathContributor(new IRascalSearchPathContributor() {
            @Override
            public String getName() {
                return "config";
            }
            
            @Override
            public void contributePaths(List<ISourceLocation> path) {
                for (IValue val :pathConfig.getSrcs()) {
                    path.add((ISourceLocation) val);
                }
            }
        });
	    
	    try {
	        for (IValue srcv : pathConfig.getSrcs()) {
	            ISourceLocation src = (ISourceLocation) srcv;
	            
	            if (!URIResolverRegistry.getInstance().isDirectory(src)) {
	                Activator.log("Source config is not a directory?", new IllegalArgumentException(src.toString()));
	                continue;
	            }
               
	            // the pathConfig source path currently still contains library sources,
	            // which we want to compile on-demand only:
	            if (src.getScheme().equals("project") && src.getAuthority().equals(projectLoc.getAuthority())) {
	                IList programs = kernel.compileAll((ISourceLocation) srcv, pathConfig.asConstructor(kernel), kernel.kw_compile());
	                markErrors(programs);
	            }
	        }
	    }
	    catch (Throwable e) {
	        Activator.log("error during compilation of project " + projectLoc, e);
	    }
	    finally {
	        monitor.done();
	    }
    }

   

    private void buildIncremental(IResourceDelta delta, IProgressMonitor monitor) {
        if (!RascalPreferences.isRascalCompilerEnabled()) {
            return;
        }
        
	    try {
            delta.accept(new IResourceDeltaVisitor() {
                @Override
                public boolean visit(IResourceDelta delta) throws CoreException {
                    IPath path = delta.getProjectRelativePath();
                    
                    if (RascalEclipseManifest.META_INF_RASCAL_MF.equals(path.toPortableString())) {
                        // if the meta information has changed, we need to recompile everything
                        clean(monitor);
                        initializeParameters(true);
                        buildMain(monitor);
                        return false;
                    }
                    else if (IRascalResources.RASCAL_EXT.equals(path.getFileExtension() /* could be null */)) {
                        if ((delta.getFlags() & IResourceDelta.CONTENT) == 0) {
                            // if no content changed, we can bail out now.
                            return false;
                        }
                        
                        ISourceLocation loc = ProjectURIResolver.constructProjectURI(delta.getFullPath());
                        
                        if (loc == null) {
                            return false;
                        }
                        
                       
                        
                        monitor.beginTask("Compiling " + loc, 100);
                        try {
                            IFile file = (IFile) delta.getResource();
                            file.deleteMarkers(IMarker.PROBLEM, true, 1);
                            String module = ResourcesToModules.moduleFromFile(file);
                            
                            
                            if (module != null) {
                                initializeParameters(false);
                                synchronized (kernel) {
                                    IConstructor result = kernel.compile(vf.string(module), pathConfig.asConstructor(kernel), kernel.kw_compile());
                                    markErrors(loc, result);
                                    IDEServicesModelProvider.getInstance().clearUseDefCache(loc);
                                }
                            }
                            else {
                                // this module is not on the source search path
                            }
                        }
                        catch (Throwable e) {
                            Activator.log("Error during compilation of " + loc, e);
                        }
                        finally {
                            monitor.done();
                        }
                        
                        return false;
                    }
                    
                    return !ProjectConfig.BIN_FOLDER.equals(path.toPortableString());
                }
            });
        } catch (CoreException e) {
            Activator.log("error during Rascal compilation", e);
        }
    }
    
    private void markErrors(IList programs) throws MalformedURLException, IOException {
        for (IValue iprogram : programs){
            IConstructor program = (IConstructor) iprogram;
            
            if (program.has("main_module")) {
                program = (IConstructor) program.get("main_module");
            }
            
            if (!program.has("src")) {
               Activator.log("could not get src for errors", new IllegalArgumentException()); 
            }
            
            markErrors((ISourceLocation) program.get("src"), program);
        }
    }
    
    private void markErrors(ISourceLocation loc, IConstructor result) throws MalformedURLException, IOException {
        if (result.has("main_module")) {
            result = (IConstructor) result.get("main_module");
        }
        
        if (!result.has("messages")) {
            Activator.log("Unexpected Rascal compiler result: " + result, new IllegalArgumentException());
        }
        
        new MessagesToMarkers().process(loc, (ISet) result.get("messages"), new MarkerCreator(new ProjectURIResolver().resolveFile(loc)));
    }

    private void initializeParameters(boolean force) throws CoreException {
        if (projectLoc != null && !force) {
            return;
        }
        
        IProject project = getProject();
        
        // TODO: these should not be fields
        projectLoc = ProjectURIResolver.constructProjectURI(project.getFullPath());
        pathConfig = new ProjectConfig(vf).getPathConfig(project);
    }
}