package de.oceanlabs.mcp.mcinjector.adaptors;

import static org.objectweb.asm.Opcodes.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import de.oceanlabs.mcp.mcinjector.MCInjectorImpl;

public class AccessReader extends ClassVisitor
{
    private static final Logger log = Logger.getLogger("MCInjector");
    //private MCInjectorImpl mci;
    private String className;
    private Map<String, AccessInfo> methods = new HashMap<String, AccessInfo>();

    public AccessReader(ClassVisitor cv, MCInjectorImpl mci)
    {
        super(ASM5, cv);
        //this.mci = mci;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces)
    {
        this.className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions)
    {
        MethodVisitor ret = super.visitMethod(access, name, desc, signature, exceptions);
        if (className.startsWith("net/minecraft/") && name.startsWith("access$"))
        {
            String path = className + "/" + name + desc;
            final AccessInfo info = new AccessInfo(className, name, desc);
            info.access = access;
            methods.put(path, info);

            ret = new MethodVisitor(api, ret)
            {
                // GETSTATIC, PUTSTATIC, GETFIELD or PUTFIELD.
                @Override
                public void visitFieldInsn(int opcode, String owner, String name, String desc)
                {
                    info.add(opcode, owner, name, desc);
                    super.visitFieldInsn(opcode, owner, name, desc);
                }

                // INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC or INVOKEINTERFACE.
                public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf)
                {
                    info.add(opcode, owner, name, desc);
                    super.visitMethodInsn(opcode, owner, name, desc, itf);
                }
            };
        }
        return ret;
    }

    @Override
    public void visitEnd()
    {
        super.visitEnd();
        for (Map.Entry<String, AccessInfo> entry : methods.entrySet())
        {
            log.log(Level.FINE, "Access: " + entry.getKey() + " " + entry.getValue());
        }
    }

    @SuppressWarnings("unused")
    private static class AccessInfo
    {
        public String owner;
        public String name;
        public String desc;
        public int access;
        public List<Insn> insns = new ArrayList<Insn>();

        public static class Insn
        {
            public int opcode;
            public String target_owner;
            public String target_name;
            public String target_desc;

            Insn(int opcode, String owner, String name, String desc)
            {
                this.opcode = opcode;
                this.target_owner = owner;
                this.target_name = name;
                this.target_desc = desc;
            }
            @Override
            public String toString()
            {
                String op = "UNKNOWN_" + opcode;
                switch (opcode)
                {
                    case GETSTATIC:       op = "GETSTATIC";       break;
                    case PUTSTATIC:       op = "PUTSTATIC";       break;
                    case GETFIELD:        op = "GETFIELD";        break;
                    case PUTFIELD:        op = "PUTFIELD";        break;
                    case INVOKEVIRTUAL:   op = "INVOKEVIRTUAL";   break;
                    case INVOKESPECIAL:   op = "INVOKESPECIAL";   break;
                    case INVOKESTATIC:    op = "INVOKESTATIC";    break;
                    case INVOKEINTERFACE: op = "INVOKEINTERFACE"; break;
                }
                return op + " " + target_owner + "/" + target_name + " " + target_desc;
            }
        }

        public AccessInfo(String owner, String name, String desc)
        {
            this.owner = owner;
            this.name = name;
            this.desc = desc;
        }

        public void add(int opcode, String owner, String name, String desc)
        {
            insns.add(new Insn(opcode, owner, name, desc));
        }

        @Override
        public String toString()
        {
            StringBuilder buf = new StringBuilder();
            buf.append('[').append(insns.get(0));
            for (int x = 1; x < insns.size(); x++)
                buf.append(", ").append(insns.get(x));
            buf.append(']');
            return buf.toString();
        }

        public boolean targetEquals(AccessInfo o)
        {
            return toString().equals(o.toString());
        }
    }

}
