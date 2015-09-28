package de.oceanlabs.mcp.mcinjector.adaptors;

import java.util.logging.Logger;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import de.oceanlabs.mcp.mcinjector.MCInjectorImpl;
import static org.objectweb.asm.Opcodes.*;

public class ApplyMarker extends ClassVisitor
{
    private static final Logger log = Logger.getLogger("MCInjector");
    private MCInjectorImpl mci;
    private String className;
    private int FLAGS = ACC_PRIVATE | ACC_STATIC | ACC_FINAL;
    private boolean isAnon = false;
    private boolean hasContent = false;
    private int access;

    public ApplyMarker(ClassVisitor cv, MCInjectorImpl mci)
    {
        super(Opcodes.ASM5, cv);
        this.mci = mci;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces)
    {
        this.className = name;;
        this.access = access;
        if ((access & ACC_INTERFACE) == ACC_INTERFACE)
        {
            FLAGS = -1; // ACC_STATIC | ACC_FINAL;
        }
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value)
    {
        hasContent = true;
        return super.visitField(access, name, desc, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions)
    {
        hasContent = true;
        return super.visitMethod(access, name, desc, signature, exceptions);
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access)
    {
        if (this.className.equals(name) && innerName == null)
        {
            isAnon = true;
            if ((access & ACC_STATIC) != ACC_STATIC || (access & ACC_SYNTHETIC) != ACC_SYNTHETIC)
                hasContent = true;
        }
        super.visitInnerClass(name, outerName, innerName, access);
    }

    @Override
    public void visitEnd()
    {
        if (!hasContent && isAnon)
        {
            log.info("  Skipping Marker to " + className + " due to being anon synthetic");
            FLAGS = -1;
        }
        if (FLAGS != -1)
        {
            String marker = mci.getMarker(className);
            if (marker != null)
            {
                log.info(" MarkerID: " + marker + " " + className);
                this.visitField(FLAGS, "__OBFID", Type.getDescriptor(String.class), null, marker);
            }
        }
        super.visitEnd();
    }
}
