package de.oceanlabs.mcp.mcinjector.adaptors;

import java.util.Collections;
import java.util.Comparator;
import java.util.logging.Logger;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;

import de.oceanlabs.mcp.mcinjector.MCInjectorImpl;

public abstract class LVTRenamer extends ClassVisitor
{
    private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("mci.printLVTRenames", "false"));
    private static final Logger log = Logger.getLogger("MCInjector");
    private MCInjectorImpl mci;
    String className;
    private boolean isInMethod = false;

    public LVTRenamer(ClassVisitor cn, MCInjectorImpl mci)
    {
        super(Opcodes.ASM5, cn);
        this.mci = mci;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces)
    {
        this.className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public void visitOuterClass(String owner, String name, String desc) {
        super.visitOuterClass(owner, name, desc);
        if (desc != null) {
            isInMethod = true;
        }
    }

    @Override
    public void visitEnd() {
        super.visitEnd();

        String suffix = null;
        if (isInMethod)
        {
            String tmp = mci.getMarker(className, false);
            if (tmp != null) {
                tmp = tmp.replace("CL_", "");
                while (tmp.charAt(0) == '0')
                    tmp = tmp.substring(1);
                suffix = tmp;
            }
        }

        ClassNode cls = MCInjectorImpl.getClassNode(cv);
        for (final MethodNode mn : cls.methods)
        {
            if (mn.localVariables != null && mn.localVariables.size() > 0)
            {
                Collections.sort(mn.localVariables, new Comparator<LocalVariableNode>()
                {
                    @Override
                    public int compare(LocalVariableNode o1, LocalVariableNode o2)
                    {
                        if (o1.index < o2.index) return -1;
                        if (o1.index > o2.index) return 1;
                        int o1Start = mn.instructions.indexOf(o1.start);
                        int o2Start = mn.instructions.indexOf(o2.start);
                        if (o1Start < o2Start) return -1;
                        if (o2Start > o2Start) return 1;
                        return 0;
                    }

                });

                for (LocalVariableNode lvn : mn.localVariables)
                {
                    if (0x2603 != lvn.name.charAt(0)) // Snowmen, added in 1.8.2? rename them to FF names
                        continue;
                    String name = getNewName(mn, lvn);
                    if (suffix != null)
                    {
                        if (!name.endsWith("_"))
                        {
                            name += "_";
                        }
                        name += suffix + "_";
                    }
                    if (DEBUG)
                        log.info("    Renaming LVT: " + lvn.index + " " + lvn.name + " " + lvn.desc + " -> " + name);
                    lvn.name = name;
                }
            }
        }
    }

    protected abstract String getNewName(MethodNode mtd, LocalVariableNode lvn);
}
