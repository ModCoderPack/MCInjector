package de.oceanlabs.mcp.mcinjector;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import static org.objectweb.asm.Opcodes.*;

public class JsonAttributeClassAdaptor extends ClassVisitor
{
    private static final Logger log = Logger.getLogger("MCInjector");
    private MCInjectorImpl mci;
    private String className;
    private JsonStruct json;
    private boolean visitedOuter = false;
    private Set<String> visitedInners = new HashSet<String>();

    public JsonAttributeClassAdaptor(ClassVisitor cv, MCInjectorImpl mci)
    {
        super(Opcodes.ASM4, cv);
        this.mci = mci;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces)
    {
        this.className = name;
        json = mci.json.get(className);
        visitedOuter = false;
        visitedInners.clear();
        super.visit(version, access, name, signature, superName, interfaces);
    }

    private int mostVisibleAccess(int access1, int access2)
    {
        int ret = access1 & ~7;
        int t = access2 & 7;

        switch (access1 & 7)
        {
            case ACC_PRIVATE: ret |= t; break;
            case 0: ret |= (t != ACC_PRIVATE ? t : 0); break;
            case ACC_PROTECTED: ret |= (t != ACC_PRIVATE && t != 0 ? t : ACC_PROTECTED); break;
            case ACC_PUBLIC: ret |= ACC_PUBLIC; break;
        }

        // Unset final bit if they are different (one final, one not-final)
        if (((access1 & ACC_FINAL) ^ (access2 & ACC_FINAL)) == 1)
        {
            ret &= ~ACC_FINAL;
        }

        return ret;
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access)
    {
        visitedInners.add(name);

        if (json != null && json.innerClasses != null)
        {
            for (JsonStruct.InnerClass inner : json.innerClasses)
            {
                if (inner.inner_class.equals(name))
                {
                    int newAccess = mostVisibleAccess(access, inner.getAccess());
                    log.fine("Overwriting Inner Class: " + name +  " -> " + inner.inner_class + " " + access + " -> " + newAccess + " " + outerName + " -> " + inner.outer_class + " " + innerName + " -> " + inner.inner_name);
                    super.visitInnerClass(inner.inner_class, inner.outer_class, inner.inner_name, newAccess);
                    return;
                }
            }
        }

        super.visitInnerClass(name, outerName, innerName, access);
    }

    @Override
    public void visitOuterClass(String owner, String name, String desc)
    {
        visitedOuter = true;

        if (json != null && json.enclosingMethod != null)
        {
            JsonStruct.EnclosingMethod enc = json.enclosingMethod;
            if (enc != null && !visitedOuter && enc.name != null && enc.desc != null)
            {
                log.fine("Overwriting Outer Class: " + owner + " -> " + enc.owner + " " + name + " -> " + enc.name + " " + desc + " -> " + enc.desc);
                super.visitOuterClass(enc.owner, enc.name, enc.desc);
                return;
            }
        }

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
            log.fine("Adding Outer Class: " + enc.owner + " " + enc.name + " " + enc.desc);
            super.visitOuterClass(enc.owner, enc.name, enc.desc);
        }

        if (json.innerClasses != null)
        {
            for (JsonStruct.InnerClass inner : json.innerClasses)
            {
                if (!visitedInners.contains(inner.inner_class))
                {
                    log.fine("Adding Inner Class: " + inner.inner_class + " " + inner.getAccess() + " " + inner.outer_class + " " + inner.inner_name);
                    super.visitInnerClass(inner.inner_class, inner.outer_class, inner.inner_name, inner.getAccess());
                }
            }
        }
    }
}
