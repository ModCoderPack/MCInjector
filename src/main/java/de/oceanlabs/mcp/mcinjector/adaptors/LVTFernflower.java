package de.oceanlabs.mcp.mcinjector.adaptors;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;

import de.oceanlabs.mcp.mcinjector.MCInjectorImpl;

public class LVTFernflower extends LVTRenamer
{
    public LVTFernflower(ClassVisitor cn, MCInjectorImpl mci)
    {
        super(cn, mci);
    }

    @Override
    protected String getNewName(MethodNode mtd, LocalVariableNode lvn)
    {
        return "var" + lvn.index;
    }
}
