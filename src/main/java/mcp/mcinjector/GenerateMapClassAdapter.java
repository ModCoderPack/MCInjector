package mcp.mcinjector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import mcp.StringUtil;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class GenerateMapClassAdapter extends ClassVisitor
{
    private static final Logger log = Logger.getLogger("MCInjector");
    private MCInjectorImpl mci;
    String className;

    public GenerateMapClassAdapter(ClassVisitor cv, MCInjectorImpl mci)
    {
        super(Opcodes.ASM4, cv);
        this.mci = mci;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces)
    {
        log.log(Level.FINE, "Class: " + name + " Extends: " + superName);
        this.className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions)
    {
        // ignore static constructors
        if (name.equals("<clinit>"))
        {
            return super.visitMethod(access, name, desc, signature, exceptions);
        }
        
        log.log(Level.FINE, "  Name: " + name + " Desc: " + desc + " Sig: " + signature);
        String clsSig = this.className + "." + name + desc;
        

        // abstract and native methods don't have a Code attribute
        //if ((access & Opcodes.ACC_ABSTRACT) != 0 || (access & Opcodes.ACC_NATIVE) != 0)
        //{
        //    return super.visitMethod(access, name, desc, signature, exceptions);
        //}
        
        List<String> names = new ArrayList<String>();
        List<Type> types = new ArrayList<Type>();
        int idx = 0;
        
        if ((access & Opcodes.ACC_STATIC) == 0)
        {
            idx = 1;
            names.add("this");
            types.add(Type.getType("L" + className + ";"));
        }

        types.addAll(Arrays.asList(Type.getArgumentTypes(desc)));
        
        //Skip anything with no params
        if (types.size() == 0)
        {
            return super.visitMethod(access, name, desc, signature, exceptions);
        }

    	log.fine("    Generating map:");
        if (mci.initIndex < 0)
        {
        	generateGenerics(idx, names, types);
        }
        else
        {
        	generateUnique(idx, clsSig, name, names, types);
        }

        if (names.size() > idx)	
        {
            mci.setParams(clsSig, StringUtil.joinString(names.subList(idx, names.size()), ","));
        }
        else
        {
            mci.setParams(clsSig, "");
        }
        
        return super.visitMethod(access, name, desc, signature, exceptions);
    }

    private void generateGenerics(int idx, List<String> names, List<Type> types)
    {
        // generate standard parameters everywhere, used for official mappings, where we don't have unique method names
        for (int x = idx, y = x; x < types.size(); x++, y++)
        {
            names.add("par" + y);
            log.fine("      Naming argument " + x + " (" + y + ") -> par" + y + " " + types.get(x).getDescriptor());
            String desc = types.get(x).getDescriptor();
            if (desc.equals("J") || desc.equals("D")) y++;
        }
    }

    private void generateUnique(int idx, String clsSig, String methodName, List<String> names, List<Type> types)
    {
    	// A srg name method params are just p_MethodID_ParamIndex_
        if (methodName.matches("func_\\d+_.+"))
        {
            String funcId = methodName.substring(5, methodName.indexOf('_', 5));
            for (int x = idx, y = x; x < types.size(); x++, y++)
            {
                String desc = types.get(x).getDescriptor();
                String name = String.format("p_%s_%d_", funcId, y); 
                names.add(name);
                log.fine("      Naming argument " + x + " (" + y + ") -> " + name + " " + desc);
                if (desc.equals("J") || desc.equals("D")) y++;
            }
        }
        // Every constructor is given a unique ID, try to load the ID from the map, if none is found assign a new one
        else if (methodName.equals("<init>"))
        {
            if (types.size() > idx)
            {
            	int conID = -1;
            	List<String> old = mci.getParams(clsSig);
            	String tmp = (old.size() > 0 ? old.get(0) : "");
            	if (tmp.matches("p_i\\d+_\\d+_"))
            	{
            		conID = Integer.parseInt(tmp.substring(3, tmp.indexOf('_', 3)));
                    log.fine("      Constructor ID loaded " + conID);
            	}
            	else
            	{
            		conID = mci.initIndex++;
                    log.fine("      Constructor ID assigned " + conID);
            	}

                for (int x = idx, y = x; x < types.size(); x++, y++)
                {
                    String desc = types.get(x).getDescriptor();
                	String name = String.format("p_i%d_%d_", conID, y);
                    names.add(name);
                    log.fine("      Naming argument " + x + " (" + y + ") -> " + name + " " + desc);
                    if (desc.equals("J") || desc.equals("D")) y++;
                }
            }
        }
        else
        {
            for (int x = idx, y = x; x < types.size(); x++, y++)
            {
                String desc = types.get(x).getDescriptor();
                String name = String.format("p_%s_%d_", methodName, y);
                names.add(name);
                log.fine("      Naming argument " + x + " (" + y + ") -> " + name + " " + desc);
                if (desc.equals("J") || desc.equals("D")) y++;
            }
        }
    }
}
