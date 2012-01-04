/*******************************************************************************
 * Copyright (c) 2009-2011 CWI
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   * Jurgen J. Vinju - Jurgen.Vinju@cwi.nl - CWI
 *   * Mark Hills - Mark.Hills@cwi.nl (CWI)
 *   * Arnold Lankamp - Arnold.Lankamp@cwi.nl
*******************************************************************************/
package org.rascalmpl.eclipse.outline;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.imp.pdb.facts.IConstructor;
import org.eclipse.imp.pdb.facts.ISourceLocation;
import org.eclipse.imp.services.base.TreeModelBuilderBase;
import org.rascalmpl.ast.AbstractAST;
import org.rascalmpl.ast.Declaration.Alias;
import org.rascalmpl.ast.Declaration.Annotation;
import org.rascalmpl.ast.Declaration.Data;
import org.rascalmpl.ast.Declaration.DataAbstract;
import org.rascalmpl.ast.Declaration.Function;
import org.rascalmpl.ast.Declaration.Tag;
import org.rascalmpl.ast.Declaration.Variable;
import org.rascalmpl.ast.Import;
import org.rascalmpl.ast.Module;
import org.rascalmpl.ast.Module.Default;
import org.rascalmpl.ast.NullASTVisitor;
import org.rascalmpl.ast.Prod;
import org.rascalmpl.ast.Prod.All;
import org.rascalmpl.ast.Prod.AssociativityGroup;
import org.rascalmpl.ast.Prod.First;
import org.rascalmpl.ast.Prod.Labeled;
import org.rascalmpl.ast.Prod.Others;
import org.rascalmpl.ast.Prod.Reference;
import org.rascalmpl.ast.Prod.Unlabeled;
import org.rascalmpl.ast.SyntaxDefinition;
import org.rascalmpl.ast.Toplevel;
import org.rascalmpl.ast.Toplevel.GivenVisibility;
import org.rascalmpl.ast.Variant;
import org.rascalmpl.parser.ASTBuilder;
import org.rascalmpl.values.uptr.TreeAdapter;

public class TreeModelBuilder extends TreeModelBuilderBase {
	public static final int CATEGORY_ALIAS = 1;
	public static final int CATEGORY_DATA = 2;
	public static final int CATEGORY_ANNOTATION = 3;
	public static final int CATEGORY_FUNCTION = 4;
	public static final int CATEGORY_TAG = 6;
	public static final int CATEGORY_VARIABLE = 7;
	public static final int CATEGORY_SYNTAX = 9;
	
	
	private Group<AbstractAST> functions;
	private Group<Group<AbstractAST>> syntax;
	private Group<AbstractAST> variables;
	private Group<AbstractAST> aliases;
	private Group<Group<AbstractAST>> adts;
	private Group<AbstractAST> annos;
	private Group<AbstractAST> tags;
	private Group<AbstractAST> imports;
	
	private String module;
	private ISourceLocation loc;

	@Override
	protected void visitTree(Object root) {
		if (root == null) {
			return;
		}
		
		if(!(root instanceof IConstructor)) return;
		IConstructor tree = (IConstructor) root;
		if(!TreeAdapter.isAppl(tree)) return;
		
		ASTBuilder builder = new ASTBuilder();
		try {
			Module mod = builder.buildModule((IConstructor) root);
			if(mod == null) return;

			loc = mod.getLocation();

			functions = new Group<AbstractAST>("Functions", loc);
			variables = new Group<AbstractAST>("Variables",loc);
			aliases = new Group<AbstractAST>("Aliases",loc);
			adts = new Group<Group<AbstractAST>>("Types",loc);
			annos = new Group<AbstractAST>("Annotations",loc);
			tags = new Group<AbstractAST>("Tags",loc);
			imports = new Group<AbstractAST>("Imports", loc);
			syntax = new Group<Group<AbstractAST>>("Syntax", loc);
			
			mod.accept(new Visitor());

			createTopItem(module);
			addGroups(syntax);
			addGroup(imports);
			addGroup(variables);
			addGroup(functions);
			addGroups(adts);
			addGroup(aliases);
			addGroup(annos);
			addGroup(tags);
		}
		catch (Throwable e) {
			//Activator.getInstance().logException("could not create outline", e);
			return;
		}
	}

	private <T> void addGroup(Group<T> group) {
		pushSubItem(group);
		for (T t : group) {
			createSubItem(t);
		}
		popSubItem();
	}
	
	private <T> void addGroups(Group<Group<T>> nested) {
		pushSubItem(nested);
		
		for (Group<T> group : nested) {
			addGroup(group);
		}
		
		popSubItem();
	}
	
	private Group<AbstractAST> findGroup(Group<Group<AbstractAST>> nested,
			String string) {
		for (Group<AbstractAST> group : nested) {
			if (group.getName().equals(string)) {
				return group;
			}
		}
		
		Group<AbstractAST> group = new Group<AbstractAST>(string, loc);
		nested.add(group);
		return group;
	}
	
	private class Visitor extends NullASTVisitor<AbstractAST> {

		@Override
		public AbstractAST visitModuleDefault(Default x) {
			module = x.getHeader().toString();
			for (Toplevel t : x.getBody().getToplevels()) {
				t.accept(this);
			}
			
			for (Import i : x.getHeader().getImports()) {
				if (i.hasModule()) {
					imports.add(i.getModule());
				}
				if (i.isSyntax()) {
					SyntaxDefinition d = i.getSyntax();
					Group<AbstractAST> nonterminal = findGroup(syntax, d.getDefined().toString());
					
					for (AbstractAST prod : getProductions(d.getProduction())) {
						nonterminal.add(prod);
					}
				}
			}
			return x;
		}
		
		private List<AbstractAST> getProductions(Prod production) {
			final List<AbstractAST> result = new LinkedList<AbstractAST>();
			
			production.accept(new NullASTVisitor<AbstractAST>() {
				public AbstractAST visitProdOthers(Others x) {
					// do nothing
					return x;
				}
				public AbstractAST visitProdAll(All x) {
					x.getLhs().accept(this);
					x.getRhs().accept(this);
					return x;
				}
				public AbstractAST visitProdAssociativityGroup(
						AssociativityGroup x) {
					x.getGroup().accept(this);
					return x;
				}
				public AbstractAST visitProdFirst(First x) {
					x.getLhs().accept(this);
					x.getRhs().accept(this);
					return x;
				}
				public AbstractAST visitProdLabeled(Labeled x) {
					result.add(x);
					return x;
				}
				public AbstractAST visitProdUnlabeled(Unlabeled x) {
					result.add(x);
					return x;
				}
				@Override
				public AbstractAST visitProdReference(Reference x) {
					// do nothing
					return x;
				}
			});
			
			return Collections.unmodifiableList(result);
		}

		@Override
		public AbstractAST visitToplevelGivenVisibility(GivenVisibility x) {
			return x.getDeclaration().accept(this);
		}
		
		@Override
		public AbstractAST visitDeclarationAlias(Alias x) {
			return aliases.add(x);
		}
		
		@Override
		public AbstractAST visitDeclarationData(Data x) {
			Group<AbstractAST> adt = findGroup(adts, x.getUser().toString());
			
			for (Variant a : x.getVariants()) {
				adt.add(a);
			}
			
			return x;
		}
		
		@Override
		public AbstractAST visitDeclarationDataAbstract(DataAbstract x) {
			findGroup(adts, x.getUser().toString());
			return x;
		}

		@Override
		public AbstractAST visitDeclarationAnnotation(Annotation x) {
			return annos.add(x);
		}
		
		@Override
		public AbstractAST visitDeclarationFunction(Function x) {
			return functions.add(x);
		}
		
		@Override
		public AbstractAST visitDeclarationTag(Tag x) {
			return tags.add(x);
		}
		
		@Override
		public AbstractAST visitDeclarationVariable(Variable x) {
			for (org.rascalmpl.ast.Variable v : x.getVariables()) {
				variables.add(v);
			}
			return x;
		}
	}

	static public class Group<T> implements Iterable<T> {
		private final String name;
		private final List<T> contents = new LinkedList<T>();
		private ISourceLocation loc;
		
		public Group(String name, ISourceLocation loc) {
			this.name = name;
			this.loc = loc;
		}
		
		public String getName() {
			return name;
		}
		
		public T add(T node) {
			contents.add(node);
			return node;
		}
		
		public Iterator<T> iterator() {
			return contents.iterator();
		}

		public ISourceLocation getLocation() {
			return loc;
		}
		
		public void setLocation(ISourceLocation loc) {
			this.loc = loc;
		}
		
		@Override
		public String toString() {
			return name + ":" + contents;
		}
	}
}
