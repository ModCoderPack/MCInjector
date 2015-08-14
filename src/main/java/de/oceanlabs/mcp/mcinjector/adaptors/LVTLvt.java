package de.oceanlabs.mcp.mcinjector.adaptors;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;

import de.oceanlabs.mcp.mcinjector.MCInjectorImpl;

public class LVTLvt extends ClassVisitor
{
    private static final Logger log = Logger.getLogger("MCInjector");
    //private MCInjectorImpl mci;
    String className;

    public LVTLvt(ClassVisitor cn, MCInjectorImpl mci)
    {
        super(Opcodes.ASM5, cn);
        //this.mci = mci;
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
        return new MethodVisitor(api, cv.visitMethod(access, name, desc, signature, exceptions))
        {
            @Override
            public void visitEnd()
            {
                super.visitEnd();
                final MethodNode mn = MCInjectorImpl.getMethodNode(mv);
                if (mn.localVariables != null && mn.localVariables.size() > 0)
                {
                    Collections.sort(mn.localVariables, new Comparator<LocalVariableNode>()
                    {
                        @Override
                        public int compare(LocalVariableNode o1, LocalVariableNode o2)
                        {
                            if (o1.index < o2.index) return -1;
                            if (o1.index > o2.index) return 1;
                            int o1Start = mn.instructions.indexOf(o1.start);
                            int o2Start = mn.instructions.indexOf(o2.start);
                            if (o1Start < o2Start) return -1;
                            if (o2Start > o2Start) return 1;
                            return 0;
                        }

                    });

                    Map<Integer, Integer> vers = new HashMap<Integer, Integer>();
                    for (LocalVariableNode lvn : mn.localVariables)
                    {
                        if (0x2603 != lvn.name.charAt(0)) // Snowmen, added in 1.8.2? rename them
                            continue;
                        int ver = 0;
                        if (vers.containsKey(lvn.index))
                        {
                            ver = vers.get(lvn.index);
                            vers.put(lvn.index, ver + 1);
                        }
                        else
                        {
                            ver = 1;
                            vers.put(lvn.index, 2);
                        }
                        String name = "lvt_" + lvn.index + "_" + ver + "_";
                        log.info("    Renaming LVT: " + lvn.index + " " + lvn.name + " " + lvn.desc + " -> " + name);
                        lvn.name = name;
                    }
                }
            }
        };
    }
}
