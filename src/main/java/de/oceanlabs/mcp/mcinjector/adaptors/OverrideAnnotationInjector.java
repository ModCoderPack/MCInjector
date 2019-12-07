package de.oceanlabs.mcp.mcinjector.adaptors;

import de.oceanlabs.mcp.mcinjector.MCInjector;
import de.oceanlabs.mcp.mcinjector.MCInjectorImpl;
import de.oceanlabs.mcp.mcinjector.data.Overrides;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import java.util.ArrayList;
import java.util.Set;

public class OverrideAnnotationInjector extends ClassVisitor
{
    private Set<String> overrides;
    public OverrideAnnotationInjector(ClassVisitor cv)
    {
        super(Opcodes.ASM6, cv);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces)
    {
        this.overrides = Overrides.INSTANCE.getOverrides(name);
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public void visitEnd()
    {
        super.visitEnd();
        if (!this.overrides.isEmpty())
        {
            ClassNode cls = MCInjectorImpl.getClassNode(cv);
            for (MethodNode mn : cls.methods)
            {
                if (this.overrides.contains(mn.name + " " + mn.desc))
                {
                    MCInjector.LOG.fine("  Override annotation injected for " + cls.name + " " + mn.name + " " + mn.desc);
                    if (mn.invisibleAnnotations == null)
                    {
                        mn.invisibleAnnotations = new ArrayList<>(1);
                    }
                    mn.invisibleAnnotations.add(new AnnotationNode("Ljava/lang/Override;"));
                }
            }
        }
    }
}
