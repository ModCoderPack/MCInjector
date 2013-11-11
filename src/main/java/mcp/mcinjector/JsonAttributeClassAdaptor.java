package mcp.mcinjector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

public class JsonAttributeClassAdaptor extends ClassVisitor
{
    private static final Logger log = Logger.getLogger("MCInjector");
    private MCInjectorImpl mci;
    private String className;
    private Map<String, Object> json;
    private boolean visitedOuter = false;
    private Set<String> visitedInners = new HashSet<String>();

	public JsonAttributeClassAdaptor(ClassVisitor cv, MCInjectorImpl mci)
    {
        super(Opcodes.ASM4, cv);
        this.mci = mci;
    }

    @SuppressWarnings("unchecked")
	@Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces)
    {
        this.className = name;
        json = (Map<String, Object>)mci.json.get(className);
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

    @SuppressWarnings("unchecked")
	@Override
    public void visitEnd()
    {
    	if (json == null)
    	{
    		super.visitEnd();
    		return;
    	}

    	ArrayList<String> tmp = (ArrayList<String>)json.get("OuterClass");
    	if (tmp != null && !visitedOuter)
    	{
    		String owner = tmp.get(0);
    		String name = (tmp.size() > 1 ? tmp.get(1) : null);
    		String desc = (tmp.size() > 2 ? tmp.get(2) : null);
    		log.fine("Adding Outer Class: " + owner + " " + name + " " + desc);
    		super.visitOuterClass(owner, name, desc);
    	}

    	ArrayList<ArrayList<String>> inners = (ArrayList<ArrayList<String>>)json.get("InnerClasses");
    	if (inners != null)
		{
    		for (ArrayList<String> a : inners)
    		{
        		String name = a.get(0);
        		int access  = Integer.parseInt(a.get(1), 16);
        		String outerName = (a.size() > 2 ? a.get(2) : null);
        		String innerName = (a.size() > 3 ? a.get(3) : null);
        		if (!visitedInners.contains(name))
        		{
        			log.fine("Adding Inner Class: " + name + " " + Integer.toHexString(access) + " " + outerName + " " + innerName);
        			super.visitInnerClass(name, outerName, innerName, access);
    			}
    		}
		}
    }
}
