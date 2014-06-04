package de.oceanlabs.mcp.mcinjector;

import static org.objectweb.asm.Opcodes.*;

import java.util.logging.Logger;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import de.oceanlabs.mcp.mcinjector.InheratanceMap.Access;

public class AccessFixerClassAdaptor extends ClassVisitor
{
    private static final Logger log = Logger.getLogger("MCInjector");
    private MCInjectorImpl mci;
    private String className;
    private InheratanceMap.Class meta;

    public AccessFixerClassAdaptor(ClassVisitor cv, MCInjectorImpl mci)
    {
        super(ASM4, cv);
        this.mci = mci;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces)
    {
        className = name;
        if (mci.inheratance != null)
            meta = mci.inheratance.getClass(name);
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions)
    {
        String sig = className + "." + name + desc;
        if (mci.inheratance == null)
        {
            Access change = mci.getAccess(sig);
            if (change != null)
            {
                log.info("  Access Change: " + Access.getFromBytecode(access) + " -> " + change + " " + sig);
                access = change.setAccess(access);
            }
        }
        else if ((access & ACC_PRIVATE) != ACC_PRIVATE && (access & ACC_STATIC) != ACC_STATIC)
        {
            InheratanceMap.Node parent = meta.traverseMethod(name, desc);
            if (!meta.name.equals(parent.owner.name))
            {
                Access old = Access.getFromBytecode(access);
                Access top = Access.getFromBytecode(parent.access);
                if (old.compareTo(top) < 0)
                {
                    log.info("  Access Change: " + old + " -> " + top + " " + sig);
                    access = top.setAccess(access);
                    mci.setAccess(sig, top);
                }
            }
        }
        return super.visitMethod(access, name, desc, signature, exceptions);
    }
}
