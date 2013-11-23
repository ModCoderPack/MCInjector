package de.oceanlabs.mcp.mcinjector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;

public class ApplyMapClassAdapter extends ClassVisitor
{
    private static final Logger log = Logger.getLogger("MCInjector");
    private MCInjectorImpl mci;
    String className;
    private ClassNode cn;

    public ApplyMapClassAdapter(ClassNode cn, MCInjectorImpl mci)
    {
        super(Opcodes.ASM4, cn);
        this.mci = mci;
        this.cn = cn;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces)
    {
        if (!mci.generate) log.log(Level.FINE, "Class: " + name + " Extends: " + superName);
        this.className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions)
    {
        // static constructors
        if (name.equals("<clinit>"))
        {
            return super.visitMethod(access, name, desc, signature, exceptions);
        }
        
        if (!mci.generate) log.log(Level.FINER, "  Name: " + name + " Desc: " + desc + " Sig: " + signature);

        String clsSig = this.className + "." + name + desc;
        
        exceptions = processExceptions(clsSig, exceptions);

        // abstract and native methods don't have a Code attribute
        if ((access & Opcodes.ACC_ABSTRACT) != 0 || (access & Opcodes.ACC_NATIVE) != 0)
        {
            return super.visitMethod(access, name, desc, signature, exceptions);
        }

        MethodNode mn = (MethodNode)cn.visitMethod(access, name, desc, signature, exceptions);
        mn = processLVT(className, clsSig, mn);
        return mn;
    }

    private String[] processExceptions(String clsSig, String[] exceptions)
    {
        List<String> map = mci.getExceptions(clsSig);
        //If we have exceptions lets add any that are not in the mapping
        if (exceptions != null)
        {
            for (String s : exceptions)
            {
            	if (!map.contains(s))
            	{
            		map.add(s);
            	}
            }
        }

        if (map.size() > 0)
        {
        	String excs = StringUtil.joinString(map, ",");
        	exceptions = map.toArray(new String[map.size()]);
            log.log(Level.FINE, "    Adding Exceptions: " + excs);
            mci.setExceptions(clsSig, excs);
        }

        return exceptions;
    }

    private MethodNode processLVT(String clsName, String classSig, MethodNode mn)
    {
    	List<String> params = mci.getParams(classSig);
    	//No params to add skip it.
    	if (params.size() == 0)
    	{
    		return mn;
    	}

        List<Type> types = new ArrayList<Type>();

        if ((mn.access & Opcodes.ACC_STATIC) == 0)
        {
            types.add(Type.getType("L" + clsName + ";"));
            params.add(0, "this");
        }

        types.addAll(Arrays.asList(Type.getArgumentTypes(mn.desc)));
        
        //Skip anything with no params
        if (types.size() == 0)
        {
            return mn;
        }
        
        log.fine("    Applying map:");        
        if (params.size() != types.size())
        {
            log.log(Level.SEVERE, "    Incorrect argument count: " + types.size() + " -> " + params.size());
            throw new RuntimeException("Incorrect argument count: " + types.size() + " -> " + params.size());
        }
        
        
        // Add labels to the start and end if they are not already labels
        AbstractInsnNode tmp = mn.instructions.getFirst();
        if (tmp == null)
        	mn.instructions.add(new LabelNode());
        else if (tmp.getType() != AbstractInsnNode.LABEL)
        	mn.instructions.insertBefore(tmp, new LabelNode());
        
        tmp = mn.instructions.getLast();
        if (tmp == null)
        	mn.instructions.add(new LabelNode());
        else if (tmp.getType() != AbstractInsnNode.LABEL)
        	mn.instructions.insert(tmp, new LabelNode());
        
        //Grab the start and end labels
        LabelNode start = (LabelNode)mn.instructions.getFirst();
        LabelNode end = (LabelNode)mn.instructions.getLast();

        mn.localVariables = new ArrayList<LocalVariableNode>();

        for (int x = 0, y = x; x < params.size(); x++, y++)
        {
            String arg = params.get(x);
            String desc = types.get(x).getDescriptor();

            if (arg.equals(""))
            {
                log.fine("      Skipping argument " + x + " (" + y + ") -> " + desc);
            }
            else
            {
                log.fine("      Naming argument " + x + " (" + y + ") -> " + arg + " " + desc);
                mn.localVariables.add(new LocalVariableNode(arg, desc, null, start, end, y));
            }

            if (desc.equals("J") || desc.equals("D")) y++;
        }
    	return mn;
    }
}
