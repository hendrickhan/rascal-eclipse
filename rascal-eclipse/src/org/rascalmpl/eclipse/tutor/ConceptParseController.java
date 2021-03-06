package org.rascalmpl.eclipse.tutor;

import java.util.Arrays;
import java.util.Iterator;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.text.IRegion;

import io.usethesource.impulse.parser.IParseController;
import io.usethesource.impulse.parser.ISourcePositionLocator;
import io.usethesource.impulse.parser.ParseControllerBase;
import io.usethesource.impulse.services.IAnnotationTypeInfo;
import io.usethesource.impulse.services.ILanguageSyntaxProperties;

public class ConceptParseController extends ParseControllerBase {

    @Override
    public Object parse(String source, IProgressMonitor monitor) {
        return new Object();
    }

    @Override
    public Iterator<Object> getTokenIterator(IRegion region) {
        return Arrays.asList().iterator();
    }

    @Override
    public ISourcePositionLocator getSourcePositionLocator() {
        return new ISourcePositionLocator() {
            
            @Override
            public int getStartOffset(Object entity) {
                return 0;
            }
            
            @Override
            public IPath getPath(Object node) {
                return null;
            }
            
            @Override
            public int getLength(Object entity) {
                return 0;
            }
            
            @Override
            public int getEndOffset(Object entity) {
                return 0;
            }
            
            @Override
            public Object findNode(Object astRoot, int startOffset, int endOffset) {
                return null;
            }
            
            @Override
            public Object findNode(Object astRoot, int offset) {
                return null;
            }
        };
    }

    @Override
    public ILanguageSyntaxProperties getSyntaxProperties() {
        return new ILanguageSyntaxProperties() {
            
            @Override
            public boolean isWhitespace(char ch) {
                return Character.isWhitespace(ch);
            }
            
            @Override
            public boolean isIdentifierStart(char ch) {
                return Character.isJavaIdentifierStart(ch);
            }
            
            @Override
            public boolean isIdentifierPart(char ch) {
                return Character.isJavaIdentifierPart(ch);
            }
            
            @Override
            public String getSingleLineCommentPrefix() {
                return "//";
            }
            
            @Override
            public String getIdentifierConstituentChars() {
                return null;
            }
            
            @Override
            public int[] getIdentifierComponents(String ident) {
                return null;
            }
            
            @Override
            public String[][] getFences() {
                return new String[][] { { "(", ")"}, {"[", "]"}, {"{", "}"}};
            }
            
            @Override
            public IRegion getDoubleClickRegion(int offset, IParseController pc) {
                return null;
            }
            
            @Override
            public String getBlockCommentStart() {
                return "////";
            }
            
            @Override
            public String getBlockCommentEnd() {
                return "////";
            }
            
            @Override
            public String getBlockCommentContinuation() {
                return null;
            }
        };
    }

    @Override
    public IAnnotationTypeInfo getAnnotationTypeInfo() {
        return null;
    }

}
