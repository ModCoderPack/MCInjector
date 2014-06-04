package de.oceanlabs.mcp.mcinjector;

import static org.objectweb.asm.ClassReader.SKIP_CODE;
import static org.objectweb.asm.ClassReader.SKIP_DEBUG;
import static org.objectweb.asm.ClassReader.SKIP_FRAMES;

import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import static org.objectweb.asm.Opcodes.*;


public class InheratanceMap
{
    private Map<String, Class> classes = new HashMap<String, Class>();

    public void processClass(byte[] data)
    {
        ClassNode node = new ClassNode();
        ClassReader reader = new ClassReader(data);
        reader.accept(node, SKIP_CODE | SKIP_DEBUG | SKIP_FRAMES);
        
        Class cls = getClass(node.name);
        cls.parent = getClass(node.superName);
        cls.wasRead = true;
        for (FieldNode n : node.fields)
            cls.fields.put(n.name + n.desc, new Node(cls, n.name, n.desc, n.access));
        for (MethodNode n : node.methods)
            cls.methods.put(n.name + n.desc, new Node(cls, n.name, n.desc, n.access));            
    }

    public Class getClass(String name)
    {
        Class cls = classes.get(name);
        if (cls == null)
        {
            cls = new Class(name);
            classes.put(name, cls);
        }
        return cls;
    }

    public static class Class
    {
        private boolean wasRead = false;
        public Class parent;
        public final String name;
        private Map<String, Node> fields = new HashMap<String, Node>();
        private Map<String, Node> methods = new HashMap<String, Node>();
        public Class(String name)
        {
            this.name = name;
        }

        public Node traverseMethod(String name, String desc)
        {
            String key = name + desc;
            Class cls = this;
            Node ret = cls.methods.get(key);
            if (name.startsWith("<")) return ret;
            while (cls != null && cls.wasRead)
            {
                if (cls.methods.containsKey(key))
                    ret = cls.methods.get(key);
                cls = cls.parent;
            }
            return ret;
        }
    }

    public static class Node
    {
        public final Class owner;
        public final String name;
        public final String desc;
        public final int access;
        private final int hash;
        
        Node(Class owner, String name, String desc, int access)
        {
            this.owner = owner;
            this.name = name;
            this.desc = desc;
            this.access = access;
            this.hash = (name + desc).hashCode();
        }

        @Override
        public int hashCode()
        {
            return hash;
        }
    }
    public static enum Access
    {
        PRIVATE, DEFAULT, PROTECTED, PUBLIC;
        public static Access getFromBytecode(int acc)
        {
            if ((acc & ACC_PRIVATE)   == ACC_PRIVATE  ) return PRIVATE;
            if ((acc & ACC_PROTECTED) == ACC_PROTECTED) return PROTECTED;
            if ((acc & ACC_PUBLIC)    == ACC_PUBLIC   ) return PUBLIC;
            return DEFAULT;
        }
        public int setAccess(int acc)
        {
            acc &= ~(ACC_PRIVATE | ACC_PROTECTED | ACC_PUBLIC);
            acc |= this == PRIVATE   ? ACC_PRIVATE   : 0;
            acc |= this == PROTECTED ? ACC_PROTECTED : 0;
            acc |= this == PUBLIC    ? ACC_PUBLIC    : 0;
            return acc;
        }
    }
}
