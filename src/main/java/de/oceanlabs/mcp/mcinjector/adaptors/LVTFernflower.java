package de.oceanlabs.mcp.mcinjector.adaptors;

import java.util.logging.Logger;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;

import de.oceanlabs.mcp.mcinjector.MCInjectorImpl;

public class LVTFernflower extends ClassVisitor
{
    private static final Logger log = Logger.getLogger("MCInjector");
    //private MCInjectorImpl mci;
    String className;

    public LVTFernflower(ClassVisitor cn, MCInjectorImpl mci)
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
                MethodNode mn = MCInjectorImpl.getMethodNode(mv);
                if (mn.localVariables != null && mn.localVariables.size() > 0)
                {
                    for (LocalVariableNode lvn : mn.localVariables)
                    {
                        if (0x2603 != lvn.name.charAt(0)) // Snowmen, added in 1.8.2? rename them to FF names
                            continue;
                        log.info("    Renaming LVT: " + lvn.index + " " + lvn.name + " " +lvn.desc + " -> " + "var" + lvn.index);
                        lvn.name = "var" + lvn.index;
                    }
                }
            }
        };
    }
}
