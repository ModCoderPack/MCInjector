package de.oceanlabs.mcp.mcinjector.adaptors;

import static org.objectweb.asm.Opcodes.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class ClassInitAdder extends ClassVisitor
{
    private static final Logger log = Logger.getLogger("MCInjector");
    private String className, superName;
    private boolean hasInit = false;
    private Map<String, String> fields = new LinkedHashMap<>();

    public ClassInitAdder(ClassVisitor cv)
    {
        super(Opcodes.ASM6, cv);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces)
    {
        this.className = name;
        this.superName = superName;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value)
    {
        if ((access & ACC_FINAL) == ACC_FINAL && ((access & ACC_STATIC) == 0))
        {
            this.fields.put(name, desc);
        }
        return super.visitField(access, name, desc, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions)
    {
        if ("<init>".equals(name))
        {
            hasInit = true;
        }
        return super.visitMethod(access, name, desc, signature, exceptions);
    }

    @Override
    public void visitEnd()
    {
        if (!hasInit && !fields.isEmpty())
        {
            log.fine("  Adding synthetic <init>");
            MethodVisitor mv = this.visitMethod(ACC_PRIVATE, "<init>", "()V", null, null);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, this.superName, "<init>", "()V", false);

            for (Entry<String, String> entry : fields.entrySet())
            {
                mv.visitVarInsn(ALOAD, 0);
                switch (Type.getType(entry.getValue()).getSort())
                {
                    case Type.BOOLEAN:
                    case Type.CHAR:
                    case Type.BYTE:
                    case Type.SHORT:
                    case Type.INT:
                        mv.visitInsn(ICONST_0);
                        break;
                    case Type.FLOAT:
                        mv.visitInsn(FCONST_0);
                        break;
                    case Type.LONG:
                        mv.visitInsn(LCONST_0);
                        break;
                    case Type.DOUBLE:
                        mv.visitInsn(DCONST_0);
                        break;
                    default:
                        mv.visitInsn(ACONST_NULL);
                        break;

                }
                mv.visitFieldInsn(PUTFIELD, this.className, entry.getKey(), entry.getValue());
                log.fine("    Field: " + entry.getKey());
            }
            mv.visitTypeInsn(NEW, "java/lang/RuntimeException");
            mv.visitInsn(DUP);
            mv.visitLdcInsn("Synthetic constructor added by MCP, do not call");
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/String;)V", false);
            mv.visitInsn(ATHROW);
        }
        super.visitEnd();
    }
}
