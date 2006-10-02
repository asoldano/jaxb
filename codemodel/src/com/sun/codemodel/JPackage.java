/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the "License").  You may not use this file except
 * in compliance with the License.
 * 
 * You can obtain a copy of the license at
 * https://jwsdp.dev.java.net/CDDLv1.0.html
 * See the License for the specific language governing
 * permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL
 * HEADER in each file and include the License file at
 * https://jwsdp.dev.java.net/CDDLv1.0.html  If applicable,
 * add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your
 * own identifying information: Portions Copyright [yyyy]
 * [name of copyright owner]
 */

package com.sun.codemodel;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;


/**
 * A Java package.
 */
public final class JPackage implements JDeclaration, JGenerable, JClassContainer, JAnnotatable, Comparable<JPackage> {

    /**
     * Name of the package.
     * May be the empty string for the root package.
     */
    private String name;

    private final JCodeModel owner;

    /**
     * List of classes contained within this package keyed by their name.
     */
    private final Map<String,JDefinedClass> classes = new TreeMap<String,JDefinedClass>();

    /**
     * List of resources files inside this package.
     */
    private final Set<JResourceFile> resources = new HashSet<JResourceFile>();
    
    /**
     * All {@link JClass}s in this package keyed the upper case class name.
     * 
     * This field is non-null only on Windows, to detect
     * "Foo" and "foo" as a collision. 
     */
    private final Map<String,JDefinedClass> upperCaseClassMap;

    /**
     * Lazily created list of package annotations.
     */
    private List<JAnnotationUse> annotations = null;

    /**
     * package javadoc.
     */
    private JDocComment jdoc = null;

    /**
     * JPackage constructor
     *
     * @param name
     *        Name of package
     *
     * @param  cw  The code writer being used to create this package
     *
     * @throws IllegalArgumentException
     *         If each part of the package name is not a valid identifier
     */
    JPackage(String name, JCodeModel cw) {
        this.owner = cw;
        if (name.equals(".")) {
            String msg = "JPackage name . is not allowed";
            throw new IllegalArgumentException(msg);
        }
        
        int dots = 1;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '.') {
                dots++;
                continue;
            }
            if (dots > 1) {
                String msg = "JPackage name " + name + " missing identifier";
                throw new IllegalArgumentException(msg);
            } else if (dots == 1 && !Character.isJavaIdentifierStart(c)) {
                String msg =
                    "JPackage name " + name + " contains illegal " + "character for beginning of identifier: " + c;
                throw new IllegalArgumentException(msg);
            } else if (!Character.isJavaIdentifierPart(c)) {
                String msg = "JPackage name " + name + "contains illegal " + "character: " + c;
                throw new IllegalArgumentException(msg);
            }
            dots = 0;
        }
        if (!name.trim().equals("") && dots != 0) {
            String msg = "JPackage name not allowed to end with .";
            throw new IllegalArgumentException(msg);
        }
        
        if(JCodeModel.isCaseSensitiveFileSystem)
            upperCaseClassMap = null;
        else
            upperCaseClassMap = new HashMap<String,JDefinedClass>();
        
        this.name = name;
    }


    public JClassContainer parentContainer() {
        return parent();
    }
    
    /**
     * Gets the parent package, or null if this class is the root package.
     */
    public JPackage parent() {
        if(name.length()==0)    return null;
        
        int idx = name.lastIndexOf('.');
        return owner._package(name.substring(0,idx));
    }

    public boolean isClass() { return false; }
    public boolean isPackage() { return true; }
    public JPackage getPackage() { return this; }
    
    /**
     * Add a class to this package.
     *
     * @param mods
     *        Modifiers for this class declaration
     *
     * @param name
     *        Name of class to be added to this package
     *
     * @return Newly generated class
     * 
     * @exception JClassAlreadyExistsException
     *      When the specified class/interface was already created.
     */
    public JDefinedClass _class(int mods, String name) throws JClassAlreadyExistsException {
        return _class(mods,name,ClassType.CLASS);
    }

    /**
     * {@inheritDoc}
     * @deprecated
     */
    public JDefinedClass _class( int mods, String name, boolean isInterface ) throws JClassAlreadyExistsException {
    	return _class(mods,name, isInterface?ClassType.INTERFACE:ClassType.CLASS );
    }
    
    public JDefinedClass _class( int mods, String name, ClassType classTypeVal ) throws JClassAlreadyExistsException {
        if(classes.containsKey(name))
            throw new JClassAlreadyExistsException(classes.get(name));
        else {
            // XXX problems caught in the NC constructor
            JDefinedClass c = new JDefinedClass(this, mods, name, classTypeVal);
            
            if( upperCaseClassMap!=null ) {
                JDefinedClass dc = upperCaseClassMap.get(name.toUpperCase());
                if(dc!=null)
                    throw new JClassAlreadyExistsException(dc);
                upperCaseClassMap.put(name.toUpperCase(),c);
            }            
            classes.put(name,c);
            return c;
        }
    }

	/**
	 * Adds a public class to this package.
	 */
    public JDefinedClass _class(String name) throws JClassAlreadyExistsException {
		return _class( JMod.PUBLIC, name );
	}

    /**
     * Gets a reference to the already created {@link JDefinedClass}.
     * 
     * @return null
     *      If the class is not yet created.
     */
    public JDefinedClass _getClass(String name) {
        if(classes.containsKey(name))
            return classes.get(name);
        else
            return null;
    }

    /**
     * Order is based on the lexicological order of the package name.
     */
    public int compareTo(JPackage that) {
        return this.name.compareTo(that.name);
    }

    /**
     * Add an interface to this package.
     *
     * @param mods
     *        Modifiers for this interface declaration
     *
     * @param name
     *        Name of interface to be added to this package
     *
     * @return Newly generated interface
     */
    public JDefinedClass _interface(int mods, String name) throws JClassAlreadyExistsException {
        return _class(mods,name,ClassType.INTERFACE);
    }

    /**
     * Adds a public interface to this package.
     */
    public JDefinedClass _interface(String name) throws JClassAlreadyExistsException {
        return _interface(JMod.PUBLIC, name);
    }
    
    /**
     * Add an annotationType Declaration to this package
     * @param name
     *      Name of the annotation Type declaration to be added to this package
     * @return
     *      newly created Annotation Type Declaration
     * @exception JClassAlreadyExistsException
     *      When the specified class/interface was already created.
     
     */
    public JDefinedClass _annotationTypeDeclaration(String name) throws JClassAlreadyExistsException {
    	return _class (JMod.PUBLIC,name,ClassType.ANNOTATION_TYPE_DECL);
    }
	
    /**
     * Add a public enum to this package
     * @param name
     *      Name of the enum to be added to this package
     * @return
     *      newly created Enum
     * @exception JClassAlreadyExistsException
     *      When the specified class/interface was already created.
     
     */
    public JDefinedClass _enum (String name) throws JClassAlreadyExistsException {
    	return _class (JMod.PUBLIC,name,ClassType.ENUM);
    }
    /**
     * Adds a new resource file to this package.
     */
    public JResourceFile addResourceFile(JResourceFile rsrc) {
        resources.add(rsrc);
        return rsrc;
    }
    
    /**
     * Checks if a resource of the given name exists.
     */
    public boolean hasResourceFile(String name) {
        for (JResourceFile r : resources)
            if (r.name().equals(name))
                return true;
        return false;
    }
    
    /**
     * Iterates all resource files in this package.
     */
    public Iterator propertyFiles() {
        return resources.iterator();
    }

    /**
     * Creates, if necessary, and returns the package javadoc for this
     * JDefinedClass.
     *
     * @return JDocComment containing javadocs for this class
     */
    public JDocComment javadoc() {
        if (jdoc == null)
            jdoc = new JDocComment(owner());
        return jdoc;
    }

    /**
     * Removes a class from this package.
     */
    public void remove(JClass c) {
        if (c._package() != this)
            throw new IllegalArgumentException(
                "the specified class is not a member of this package," + " or it is a referenced class");

        // note that c may not be a member of classes.
        // this happens when someone is trying to remove a non generated class
        classes.remove(c.name());
        if (upperCaseClassMap != null)
            upperCaseClassMap.remove(c.name().toUpperCase());
    }
	
    /**
     * Reference a class within this package.
     */
    public JClass ref(String name) throws ClassNotFoundException {
        if (name.indexOf('.') >= 0)
            throw new IllegalArgumentException("JClass name contains '.': " + name);

        String n = "";
        if (!isUnnamed())
            n = this.name + '.';
        n += name;

        return owner.ref(Class.forName(n));
    }
    
    /**
     * Gets a reference to a sub package of this package.
     */
    public JPackage subPackage( String pkg ) {
        if(isUnnamed())     return owner()._package(pkg);
        else                return owner()._package(name+'.'+pkg);
    }

    /**
     * Returns an iterator that walks the top-level classes defined in this
     * package.
     */
    public Iterator<JDefinedClass> classes() {
        return classes.values().iterator();
    }
    
    /**
     * Checks if a given name is already defined as a class/interface
     */
    public boolean isDefined(String classLocalName) {
        Iterator itr = classes();
        while (itr.hasNext()) {
            if (((JClass)itr.next()).name().equals(classLocalName))
                return true;
        }

        return false;
    }

    /**
     * Checks if this package is the root, unnamed package.
     */
    public final boolean isUnnamed() { return name.length() == 0; }

    /**
     * Get the name of this package
     *
     * @return
     *		The name of this package, or the empty string if this is the
     *		null package. For example, this method returns strings like
     *		<code>"java.lang"</code>
     */
    public String name() {
        return name;
    }

    /**
     * Return the code model root object being used to create this package.
     */
    public final JCodeModel owner() { return owner; }


    public JAnnotationUse annotate(JClass clazz) {
        if(isUnnamed())
            throw new IllegalArgumentException("the root package cannot be annotated");
        if(annotations==null)
           annotations = new ArrayList<JAnnotationUse>();
        JAnnotationUse a = new JAnnotationUse(clazz);
        annotations.add(a);
        return a;
    }

    public JAnnotationUse annotate(Class<? extends Annotation> clazz) {
        return annotate(owner.ref(clazz));
    }

    public <W extends JAnnotationWriter> W annotate2(Class<W> clazz) {
        return TypedAnnotationWriter.create(clazz,this);
    }


    /**
     * Convert the package name to directory path equivalent
     */
    File toPath(File dir) {
        if (name == null) return dir;
        return new File(dir, name.replace('.', File.separatorChar));
    }

    public void declare(JFormatter f ) {
        if (name.length() != 0)
            f.p("package").p(name).p(';').nl();
    }

    public void generate(JFormatter f) {
        f.p(name);
    }


    void build( CodeWriter src, CodeWriter res ) throws IOException {

        // write classes
        for (JDefinedClass c : classes.values()) {
            if (c.isHidden())
                continue;   // don't generate this file

            JFormatter f = createJavaSourceFileWriter(src, c.name());
            f.write(c);
            f.close();
        }

        // write package annotations
        if(annotations!=null || jdoc!=null) {
            JFormatter f = createJavaSourceFileWriter(src,"package-info");

            if (jdoc != null)
                f.g(jdoc);

            // TODO: think about importing
            if (annotations != null){
                for (JAnnotationUse a : annotations)
                    f.g(a).nl();
            }
            f.d(this);

            f.close();
        }

        // write resources
        for (JResourceFile rsrc : resources) {
            CodeWriter cw = rsrc.isResource() ? res : src;
            OutputStream os = new BufferedOutputStream(cw.openBinary(this, rsrc.name()));
            rsrc.build(os);
            os.close();
        }
    }

    /*package*/ int countArtifacts() {
        int r = 0;
        for (JDefinedClass c : classes.values()) {
            if (c.isHidden())
                continue;   // don't generate this file
            r++;
        }

        if(annotations!=null || jdoc!=null) {
            r++;
        }

        r+= resources.size();

        return r;
    }

    private JFormatter createJavaSourceFileWriter(CodeWriter src, String className) throws IOException {
        Writer bw = new BufferedWriter(src.openSource(this,className+".java"));
        return new JFormatter(new PrintWriter(bw));
    }
}