package de.oceanlabs.mcp.mcinjector;

import java.util.logging.Logger;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import static org.objectweb.asm.Opcodes.*;

public class ApplyMarkerClassAdaptor extends ClassVisitor
{
    private static final Logger log = Logger.getLogger("MCInjector");
    private MCInjectorImpl mci;
    private String className;
    private int FLAGS = ACC_PRIVATE | ACC_STATIC | ACC_FINAL;

    public ApplyMarkerClassAdaptor(ClassVisitor cv, MCInjectorImpl mci)
    {
        super(Opcodes.ASM4, cv);
        this.mci = mci;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces)
    {
        this.className = name;;
        if ((access & ACC_INTERFACE) == ACC_INTERFACE)
        {
            FLAGS = -1; // ACC_STATIC | ACC_FINAL;
        }
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public void visitEnd()
    {
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
