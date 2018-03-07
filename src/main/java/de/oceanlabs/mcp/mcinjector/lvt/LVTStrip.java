package de.oceanlabs.mcp.mcinjector.lvt;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;

import de.oceanlabs.mcp.mcinjector.MCInjector;
import de.oceanlabs.mcp.mcinjector.MCInjectorImpl;

public class LVTStrip extends ClassVisitor
{
    String className;

    public LVTStrip(ClassVisitor cn)
    {
        super(Opcodes.ASM6, cn);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions)
    {
        return new MethodVisitor(api, cv.visitMethod(access, name, desc, signature, exceptions))
        {
            @Override
            public void visitEnd()
            {
                MethodNode mn = MCInjectorImpl.getMethodNode(mv);
                if (mn.localVariables != null && mn.localVariables.size() > 0)
                {
                    MCInjector.LOG.info("    Stripping LVT: " + mn.localVariables.size());
                    mn.localVariables = null;
                }
                super.visitEnd();
            }
        };
    }
}
