package de.oceanlabs.mcp.mcinjector.adaptors;

import java.util.logging.Logger;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;

import de.oceanlabs.mcp.mcinjector.MCInjectorImpl;

public class LVTStrip extends ClassVisitor
{
    private static final Logger log = Logger.getLogger("MCInjector");
    //private MCInjectorImpl mci;
    String className;

    public LVTStrip(ClassVisitor cn, MCInjectorImpl mci)
    {
        super(Opcodes.ASM5, cn);
        //this.mci = mci;
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
                    log.info("    Stripping LVT: " + mn.localVariables.size());
                    mn.localVariables = null;
                }
                super.visitEnd();
            }
        };
    }
}
