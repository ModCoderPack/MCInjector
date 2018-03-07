package de.oceanlabs.mcp.mcinjector.adaptors;

import static org.objectweb.asm.Opcodes.*;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

import de.oceanlabs.mcp.mcinjector.MCInjector;
import de.oceanlabs.mcp.mcinjector.data.Access;
import de.oceanlabs.mcp.mcinjector.data.Access.Level;

public class AccessFixer extends ClassVisitor
{
    private String className;
    public AccessFixer(ClassVisitor cv)
    {
        super(ASM6, cv);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces)
    {
        this.className = name;
        Level old = Level.getFromBytecode(access);
        Level _new = Access.INSTANCE.getLevel(className);
        if (_new != null && old != _new)
        {
            MCInjector.LOG.info("  Access Change: " + old + " -> " + _new + " " + className);
            access = _new.setAccess(access);
        }
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value)
    {

        Level old = Level.getFromBytecode(access);
        Level _new = Access.INSTANCE.getLevel(className, name);
        if (_new != null && old != _new)
        {
            MCInjector.LOG.info("  Access Change: " + old + " -> " + _new + " " + className + " " + name);
            access = _new.setAccess(access);
        }
        return super.visitField(access, name, desc, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions)
    {
        Level old = Level.getFromBytecode(access);
        Level _new = Access.INSTANCE.getLevel(className, name, desc);
        if (_new != null && old != _new)
        {
            MCInjector.LOG.info("  Access Change: " + old + " -> " + _new + " " + className + " " + name + " " + desc);
            access = _new.setAccess(access);
        }
        return super.visitMethod(access, name, desc, signature, exceptions);
    }
}
