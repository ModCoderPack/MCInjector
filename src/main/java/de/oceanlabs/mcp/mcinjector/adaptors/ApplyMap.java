package de.oceanlabs.mcp.mcinjector.adaptors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;

import de.oceanlabs.mcp.mcinjector.MCInjector;
import de.oceanlabs.mcp.mcinjector.MCInjectorImpl;
import de.oceanlabs.mcp.mcinjector.data.Constructors;
import de.oceanlabs.mcp.mcinjector.data.Exceptions;

public class ApplyMap extends ClassVisitor
{
    String className;
    MCInjectorImpl injector;

    public ApplyMap(MCInjectorImpl injector, ClassVisitor cn)
    {
        super(Opcodes.ASM6, cn);
        this.injector = injector;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces)
    {
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

        exceptions = processExceptions(className, name, desc, exceptions);

        // abstract and native methods don't have a Code attribute
        /*if ((access & Opcodes.ACC_ABSTRACT) != 0 || (access & Opcodes.ACC_NATIVE) != 0)
        {
            return super.visitMethod(access, name, desc, signature, exceptions);
        }*/

        return new MethodVisitor(api, cv.visitMethod(access, name, desc, signature, exceptions))
        {
            @Override
            public void visitEnd()
            {
                super.visitEnd();
                processLVT(className, name, desc, MCInjectorImpl.getMethodNode(mv));
            }
        };
    }

    private String[] processExceptions(String cls, String name, String desc, String[] exceptions)
    {
        Set<String> set = new HashSet<>();
        for (String s :  Exceptions.INSTANCE.getExceptions(cls, name, desc))
            set.add(s);
        if (exceptions != null)
        {
            for (String s : exceptions)
                set.add(s);
        }

        if (set.size() > (exceptions == null ? 0 : exceptions.length))
        {
            exceptions = set.stream().sorted().toArray(x -> new String[x]);
            Exceptions.INSTANCE.setExceptions(cls, name, desc, exceptions);
            MCInjector.LOG.log(Level.FINE, "    Adding Exceptions: " + String.join(", ", exceptions));
        }

        return exceptions;
    }

    private void processLVT(String cls, String name, String desc, MethodNode mn)
    {
        List<String> params = new ArrayList<>();
        List<Type> types = new ArrayList<>();

        if ((mn.access & Opcodes.ACC_STATIC) == 0)
        {
            types.add(Type.getType("L" + cls + ";"));
            params.add(0, "this");
        }

        types.addAll(Arrays.asList(Type.getArgumentTypes(mn.desc)));

        //Skip anything with no params
        if (types.size() == 0)
            return;

        MCInjector.LOG.fine("    Generating map:");
        String nameFormat = "p_" + name + "_%d_";
        if (name.matches("func_\\d+_.+")) // A srg name method params are just p_MethodID_ParamIndex_
            nameFormat = "p_" + name.substring(5, name.indexOf('_', 5)) + "_%s_";
        else if (name.equals("<init>")) // Every constructor is given a unique ID, try to load the ID from the map, if none is found assign a new one
            nameFormat = "p_i" + Constructors.INSTANCE.getID(className, desc, types.size() > 1) + "_%s_";

        for (int x = params.size(), y = x; x < types.size(); x++)
        {
            String par_name = String.format(nameFormat, y);
            params.add(par_name);
            MCInjector.LOG.fine("      Naming argument " + x + " (" + y + ") -> " + par_name + " " + types.get(x).getDescriptor());
            y += types.get(x).getSize();
        }

        if ((mn.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) //Abstract and native methods dont have code so we need to store the names elseware.
        {
            if ((mn.access & Opcodes.ACC_STATIC) == 0)
                params.remove(0); //remove 'this'
            if (params.size() > 0)
                injector.storeAbstractParameters(className, name, desc, params);
            return;
        }

        MCInjector.LOG.fine("    Applying map:");
        if (params.size() != types.size())
        {
            MCInjector.LOG.log(Level.SEVERE, "    Incorrect argument count: " + types.size() + " -> " + params.size());
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

        Map<Integer, String> parNames = new HashMap<Integer, String>();
        for (int x = 0, y = 0; x < params.size(); x++)
        {
            parNames.put(y, params.get(x));
            y += types.get(x).getSize();
        }

        //Grab the start and end labels
        LabelNode start = (LabelNode)mn.instructions.getFirst();
        LabelNode end = (LabelNode)mn.instructions.getLast();
        Set<Integer> found = new HashSet<Integer>();

        if (mn.localVariables == null)
            mn.localVariables = new ArrayList<LocalVariableNode>();

        for (LocalVariableNode lvn : mn.localVariables)
        {
            String par_name = parNames.get(lvn.index);
            if (par_name != null)
            {
                MCInjector.LOG.fine("      ReNaming argument (" + lvn.index + "): " + lvn.name + " -> " + par_name);
                lvn.name = par_name;
                found.add(lvn.index);
            }
        }
        for (int x = 0, y = 0; x < params.size(); x++)
        {
            String arg = params.get(x);
            if (!found.contains(y))
            {
                MCInjector.LOG.fine("      Naming argument " + x + " (" + y + ") -> " + arg + " " + types.get(x).getDescriptor());
                mn.localVariables.add(new LocalVariableNode(arg, types.get(x).getDescriptor(), null, start, end, y));
            }
            y += types.get(x).getSize();
        }

        Collections.sort(mn.localVariables, (o1, o2) -> o1.index < o2.index ? -1 : (o1.index == o2.index ? 0 : 1));
    }
}
