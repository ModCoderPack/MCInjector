package de.oceanlabs.mcp.mcinjector.lvt;

import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;

public class LVTLvt extends LVTRenamer
{
    private MethodNode lastMethod = null;
    private Map<Integer, Integer> vers = null;

    public LVTLvt(ClassVisitor cn)
    {
        super(cn);
    }

    @Override
    protected String getNewName(MethodNode mtd, LocalVariableNode lvn)
    {
        if (lastMethod != mtd)
        {
            vers = new HashMap<Integer, Integer>();
            lastMethod = mtd;
        }

        int ver = 0;
        if (vers.containsKey(lvn.index))
        {
            ver = vers.get(lvn.index);
            vers.put(lvn.index, ver + 1);
        }
        else
        {
            ver = 1;
            vers.put(lvn.index, 2);
        }
        return "lvt_" + lvn.index + "_" + ver + "_";
    }
}
