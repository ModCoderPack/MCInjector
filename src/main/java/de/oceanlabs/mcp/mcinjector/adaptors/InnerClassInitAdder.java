package de.oceanlabs.mcp.mcinjector.adaptors;

import static org.objectweb.asm.Opcodes.*;

import java.util.logging.Logger;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class InnerClassInitAdder extends ClassVisitor
{
    private static final Logger log = Logger.getLogger("MCInjector");
    private String className, parentName, parentField;
    private boolean hasInit = false;
    private boolean isStatic = false;
    private int FIELD_ACCESS = ACC_FINAL | ACC_SYNTHETIC;

    public InnerClassInitAdder(ClassVisitor cv)
    {
        super(Opcodes.ASM6, cv);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces)
    {
        this.isStatic = (access & ACC_STATIC) != 0;
        this.className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override // The reader *should* read this before any fields/methods, so we can set the parent name to find the field
    public void visitInnerClass(String name, String outerName, String innerName, int access)
    {
        if (this.className.equals(name))
        {
            this.parentName = "L" + outerName + ";";
        }
        super.visitInnerClass(name, outerName, innerName, access);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value)
    {
        if ((access & FIELD_ACCESS) == FIELD_ACCESS && desc.equals(this.parentName))
        {
            this.parentField = name;
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
        if (!hasInit && !isStatic && parentName != null && parentField != null)
        {
            log.fine("  Adding synthetic <init> " + parentName + " " + parentField);
            MethodVisitor mv = this.visitMethod(ACC_PRIVATE | ACC_SYNTHETIC, "<init>", "(" + parentName + ")V", null, null);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitFieldInsn(PUTFIELD, className, parentField, parentName);
            mv.visitInsn(RETURN);
        }
        super.visitEnd();
    }
}
