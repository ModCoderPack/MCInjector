package de.oceanlabs.mcp.mcinjector.lvt;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;

public class LVTFernflower extends LVTRenamer
{
    public LVTFernflower(ClassVisitor cn)
    {
        super(cn);
    }

    @Override
    protected String getNewName(MethodNode mtd, LocalVariableNode lvn)
    {
        return "var" + lvn.index;
    }
}
