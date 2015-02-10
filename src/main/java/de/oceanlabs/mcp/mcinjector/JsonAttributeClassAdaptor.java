package de.oceanlabs.mcp.mcinjector;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class JsonAttributeClassAdaptor extends ClassVisitor
{
    private static final Logger log = Logger.getLogger("MCInjector");
    private MCInjectorImpl mci;
    private String className;
    private JsonStruct json;
    private boolean visitedOuter = false;
    private Set<String> visitedInners = new HashSet<String>();
    private Set<String> refedInners = new HashSet<String>();


    public JsonAttributeClassAdaptor(ClassVisitor cv, MCInjectorImpl mci)
    {
        super(Opcodes.ASM4, cv);
        this.mci = mci;
    }

    private String getAccess(int access)
    {
        StringBuilder buf = new StringBuilder();
             if ((access & Opcodes.ACC_PUBLIC)    != 0) buf.append("PUBLIC ");
        else if ((access & Opcodes.ACC_PRIVATE)   != 0) buf.append("PRIVATE ");
        else if ((access & Opcodes.ACC_PROTECTED) != 0) buf.append("PROTECTED ");
        else                                            buf.append("DEFAULT ");
        if ((access & Opcodes.ACC_FINAL)      != 0) buf.append("FINAL ");
        if ((access & Opcodes.ACC_SUPER)      != 0) buf.append("SUPER ");
        if ((access & Opcodes.ACC_INTERFACE)  != 0) buf.append("INTERFACE ");
        if ((access & Opcodes.ACC_ABSTRACT)   != 0) buf.append("ABSTRACT ");
        if ((access & Opcodes.ACC_SYNTHETIC)  != 0) buf.append("SYNTHETIC ");
        if ((access & Opcodes.ACC_ANNOTATION) != 0) buf.append("ANNOTATION ");
        if ((access & Opcodes.ACC_ENUM)       != 0) buf.append("ENUM ");
        return buf.toString().trim();
    }

    private boolean isInnerClass(String name)
    {
        return name.contains("$");
    }

    private void referenced(Type type)
    {
        if (type.getSort() == Type.ARRAY)
            type = type.getElementType();
        if (type.getSort() == Type.OBJECT)
        {
            String internal = type.getInternalName();
            if (isInnerClass(internal))
                refedInners.add(internal);
        }
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces)
    {
        this.className = name;
        json = mci.json.get(className);
        visitedOuter = false;
        visitedInners.clear();
        for (String i : interfaces)
            if (isInnerClass(i))
                refedInners.add(i);
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value)
    {
        referenced(Type.getType(desc));
        return super.visitField(access, name, desc, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions)
    {
        if (exceptions != null)
            for (String s : exceptions)
                if (isInnerClass(s))
                    refedInners.add(s);

        referenced(Type.getReturnType(desc));
        for (Type t : Type.getArgumentTypes(desc))
            referenced(t);

        return super.visitMethod(access, name, desc, signature, exceptions);
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access)
    {
        visitedInners.add(name);
        super.visitInnerClass(name, outerName, innerName, access);
    }

    @Override
    public void visitOuterClass(String owner, String name, String desc)
    {
        visitedOuter = true;
        super.visitOuterClass(owner, name, desc);
    }

    @Override
    public void visitEnd()
    {
        if (json == null)
        {
            super.visitEnd();
            return;
        }

        JsonStruct.EnclosingMethod enc = json.enclosingMethod;
        if (enc != null && !visitedOuter && enc.name != null && enc.desc != null)
        {
            log.fine("  Adding Outer Class:");
            log.fine("    Owner: " + enc.owner);
            log.fine("    Method: " + enc.name + enc.desc);
            super.visitOuterClass(enc.owner, enc.name, enc.desc);
        }

        if (json.innerClasses != null)
        {
            for (JsonStruct.InnerClass inner : json.innerClasses)
            {
                if (!visitedInners.contains(inner.inner_class))
                {
                    visitedInners.add(inner.inner_class);
                    log.fine("  Adding Inner Class:");
                    log.fine("    Inner: " + inner.inner_class);
                    log.fine("    Access: " + getAccess(inner.getAccess()));
                    if (inner.outer_class != null)
                        log.fine("    Outer: "+ inner.outer_class);
                    if (inner.inner_name != null)
                        log.fine("    Name: " + inner.inner_name);
                    super.visitInnerClass(inner.inner_class, inner.outer_class, inner.inner_name, inner.getAccess());
                }
            }
        }

        refedInners.removeAll(visitedInners);
        for (String inner : refedInners)
        {
            JsonStruct.InnerClass ic = mci.inners.get(inner);
            if (ic == null)
            {
                log.fine("  Referenced Inner Class: " + inner + " (missing)");
            }
            else
            {
                log.fine("  Referenced Inner Class:");
                log.fine("    Inner: " + ic.inner_class);
                log.fine("    Access: " + getAccess(ic.getAccess()));
                if (ic.outer_class != null)
                    log.fine("    Outer: "+ ic.outer_class);
                if (ic.inner_name != null)
                    log.fine("    Name: " + ic.inner_name);
            }
        }
    }
}
