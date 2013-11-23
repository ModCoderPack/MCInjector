package de.oceanlabs.mcp.mcinjector;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

public class JsonAttributeClassAdaptor extends ClassVisitor
{
    private static final Logger log = Logger.getLogger("MCInjector");
    private MCInjectorImpl mci;
    private String className;
    private JsonStruct json;
    private boolean visitedOuter = false;
    private Set<String> visitedInners = new HashSet<String>();

	public JsonAttributeClassAdaptor(ClassVisitor cv, MCInjectorImpl mci)
    {
        super(Opcodes.ASM4, cv);
        this.mci = mci;
    }

	@Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces)
    {
        this.className = name;
        json = mci.json.get(className);
        visitedOuter = false;
        visitedInners.clear();
        super.visit(version, access, name, signature, superName, interfaces);
    }
    
    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access)
    {
    	visitedInners.add(name);
    	super.visitInnerClass(name, outerName, innerName, access);
    }

    @Override
    public void visitOuterClass(String owner, String name, String desc)
    {
        visitedOuter = true;
        super.visitOuterClass(owner, name, desc);
    }

	@Override
    public void visitEnd()
    {
    	if (json == null)
    	{
    		super.visitEnd();
    		return;
    	}

    	JsonStruct.EnclosingMethod enc = json.enclosingMethod;
    	if (enc != null && !visitedOuter)
    	{
    		log.fine("Adding Outer Class: " + enc.owner + " " + enc.name + " " + enc.desc);
    		super.visitOuterClass(enc.owner, enc.name, enc.desc);
    	}

    	if (json.innerClasses != null)
		{
    		for (JsonStruct.InnerClass inner : json.innerClasses)
    		{
        		if (!visitedInners.contains(inner.inner_class))
        		{
        			log.fine("Adding Inner Class: " + inner.inner_class + " " + inner.getAccess() + " " + inner.outer_class + " " + inner.inner_name);
        			super.visitInnerClass(inner.inner_class, inner.outer_class, inner.inner_name, inner.getAccess());
    			}
    		}
		}
    }
}
