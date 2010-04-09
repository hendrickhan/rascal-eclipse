package org.rascalmpl.eclipse.terms;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Iterator;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.imp.language.Language;
import org.eclipse.imp.model.ISourceProject;
import org.eclipse.imp.parser.IMessageHandler;
import org.eclipse.imp.parser.IParseController;
import org.eclipse.imp.parser.ISourcePositionLocator;
import org.eclipse.imp.pdb.facts.IConstructor;
import org.eclipse.imp.pdb.facts.ISourceLocation;
import org.eclipse.imp.pdb.facts.IValueFactory;
import org.eclipse.imp.pdb.facts.exceptions.FactTypeUseException;
import org.eclipse.imp.pdb.facts.type.Type;
import org.eclipse.imp.services.IAnnotationTypeInfo;
import org.eclipse.imp.services.ILanguageSyntaxProperties;
import org.eclipse.jface.text.IRegion;
import org.rascalmpl.eclipse.Activator;
import org.rascalmpl.eclipse.console.ProjectURIResolver;
import org.rascalmpl.eclipse.editor.NodeLocator;
import org.rascalmpl.eclipse.editor.TokenIterator;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.interpreter.IEvaluatorContext;
import org.rascalmpl.interpreter.control_exceptions.Throw;
import org.rascalmpl.interpreter.env.ModuleEnvironment;
import org.rascalmpl.interpreter.load.ModuleLoader;
import org.rascalmpl.interpreter.staticErrors.SyntaxError;
import org.rascalmpl.interpreter.utils.RuntimeExceptionFactory;
import org.rascalmpl.library.ParseTree;
import org.rascalmpl.parser.ConcreteObjectParser;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.values.ValueFactoryFactory;
import org.rascalmpl.values.uptr.Factory;
import org.rascalmpl.values.uptr.ParsetreeAdapter;
import org.rascalmpl.values.uptr.ProductionAdapter;
import org.rascalmpl.values.uptr.TreeAdapter;

public class TermParseController implements IParseController {
	private IMessageHandler handler;
	private ISourceProject project;
	private IConstructor parseTree;
	private IPath path;
	private Language language;
	private IEvaluatorContext evaluator;
	private IConstructor start;
	private ConcreteObjectParser parser = new ConcreteObjectParser();;
	
	public Object getCurrentAst(){
		return parseTree;
	}
	
	public void setCurrentAst(IConstructor parseTree) {
		this.parseTree = parseTree;
	}
	
	public IAnnotationTypeInfo getAnnotationTypeInfo() {
		return null;
	}

	public Language getLanguage() {
		return language;
	}

	public IPath getPath() {
		return path;
	}

	public ISourceProject getProject() {
		return project;
	}

	public ISourcePositionLocator getSourcePositionLocator() {
		return new NodeLocator();
	}

	public ILanguageSyntaxProperties getSyntaxProperties() {
		return null;
	}

	@SuppressWarnings("unchecked")
	public Iterator getTokenIterator(IRegion region) {
		return new TokenIterator(parseTree);
	}

	public void initialize(IPath filePath, ISourceProject project, IMessageHandler handler) {
		this.path = filePath;
		this.project = project;
		this.handler = handler;
		TermLanguageRegistry reg = TermLanguageRegistry.getInstance();
		this.language = reg.getLanguage(path.getFileExtension());
		this.evaluator = reg.getEvaluator(this.language);
		this.start = reg.getStart(this.language);
	}

	public Object parse(String input, IProgressMonitor monitor){
		parseTree = null;
		IValueFactory vf = ValueFactoryFactory.getValueFactory();
		URI location = ProjectURIResolver.constructProjectURI(project, path);
		
		
		try{
			handler.clearMessages();
			monitor.beginTask("parsing " + language.getName(), 1);
			
			
			Evaluator eval = evaluator.getEvaluator();
			ModuleLoader loader = eval.getModuleLoader();

			InputStream stream = new ByteArrayInputStream(input.getBytes());
			parseTree = parser.parseStream(loader.getSdfSearchPath(), ((ModuleEnvironment) evaluator.getCurrentEnvt().getRoot()).getSDFImports(), stream);
			parseTree = ParsetreeAdapter.addPositionInformation(parseTree, location);
			
			IConstructor tree = (IConstructor) TreeAdapter.getArgs(ParsetreeAdapter.getTop(parseTree)).get(1);
			IConstructor prod = TreeAdapter.getProduction(tree);
			IConstructor rhs = ProductionAdapter.getRhs(prod);

			if (!rhs.isEqual(start.get(0))) {
				parseTree = null;
			}

			monitor.worked(1);
			return parseTree;
		}
		catch (SyntaxError e) {
			ISourceLocation loc = e.getLocation();
			loc = vf.sourceLocation(location, loc.getOffset(), loc.getLength(), loc.getBeginLine(),
					loc.getEndLine(), loc.getBeginColumn(), loc.getEndColumn());
			handler.handleSimpleMessage("parse error: " + loc, loc.getOffset(), loc.getOffset() + loc.getLength(), loc.getBeginColumn(), loc.getEndColumn(), loc.getBeginLine(), loc.getEndLine());
		} 
		catch (Throw e) {
			ISourceLocation loc = e.getLocation();
			
			if (loc == null) {
				loc = (ISourceLocation) ((IConstructor) e.getException()).get(0);
			}
			
			handler.handleSimpleMessage("parse error: " + loc, loc.getOffset(), loc.getOffset() + loc.getLength(), loc.getBeginColumn(), loc.getEndColumn(), loc.getBeginLine(), loc.getEndLine());
		}
		catch (FactTypeUseException ftuex) {
			Activator.getInstance().logException("parsing " + language.getName() + " failed", ftuex);
		}
		catch (NullPointerException npex){
			Activator.getInstance().logException("parsing " + language.getName() + " failed", npex);
		} 
		catch (IOException e) {
			Activator.getInstance().logException("parsing " + language.getName() + " failed", e);
		}
		finally{
			monitor.done();
		}
		
		return null;
	}
}