package de.oceanlabs.mcp.mcinjector.data;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import de.oceanlabs.mcp.mcinjector.MCInjector;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.objectweb.asm.Opcodes.*;

import de.oceanlabs.mcp.mcinjector.data.Classpath.ClassInfo.MethodInfo;

public enum Classpath {
    INSTANCE;

    private static final Handle LAMBDA_METAFACTORY = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);
    private Tree tree;

    public Tree getTree() {
        return tree;
    }

    public void load(Path input, Collection<Path> libraries) {
        tree = new Tree();

        Set<String> classes = tree.load(input);

        for (Path lib : libraries)
            tree.load(lib);

        for (String cls : classes)
            resolve(cls);
    }

    //Recursive, but should be fine as we don't have class super complex class trees
    private void resolve(String cls) {
        ClassInfo info = tree.getInfo(cls);
        if (info == null || info.resolved)
            return;

        if (info.getSuper() != null)
            resolve(info.getSuper());

        if (info.interfaces != null)
            for (String intf : info.interfaces)
                resolve(intf);

        //TODO: Synthetics/Accessors?

        if (info.methods != null) {
            //Synthetic Bouncers!
            for (MethodInfo mtd : info.methods.values()) {
                if (mtd.bouncer != null) {
                    Method owner = walkBouncers(mtd, info.name);
                    if (owner != null && !owner.owner.equals(info.name))
                        mtd.bouncer.setOwner(owner);
                }
            }

            //Resolve the 'root' owner of each method.
            for (MethodInfo mtd : info.methods.values()) {
                mtd.setOverrides(findOverrides(mtd, info.name, new ArrayList<>()));
            }
        }

        if (!info.isAbstract()) {
            resolveAbstract(info);
        }

        info.resolved = true;
    }

    private Method walkBouncers(MethodInfo mtd, String owner) {
        ClassInfo info = tree.getInfo(owner);

        if (info == null)
            return null;

        if (info.methods != null) {
            MethodInfo mine = info.methods.get(mtd.name + mtd.desc);
            if (mine != null && ((mine.getAccess() & (Opcodes.ACC_FINAL | Opcodes.ACC_PRIVATE)) == 0 || owner.equals(mtd.getOwner()))) {
                if (mine.bouncer == null) {
                    List<Method> owners = findOverrides(mine, owner, new ArrayList<>());
                    if (owners.isEmpty())
                        return new Method(owner, mine.name, mine.desc);
                    else if (owners.size() == 1)
                        return owners.iterator().next();
                    else //We can't find just one owner... something's fucky...
                        return owners.iterator().next(); //Just pick one...
                }

                for (MethodInfo m2 : info.methods.values()) {
                    Method target = m2.bouncer == null ? null : m2.bouncer.target;
                    if (target != null && mine.name.equals(target.name) && mine.desc.equals(target.desc)) {
                        if (m2.bouncer.owner != null)
                            return m2.bouncer.owner;

                        Method ret = walkBouncers(m2, owner);
                        if (ret != null && !ret.owner.equals(owner)) {
                            m2.bouncer.setOwner(ret);
                            return ret;
                        } else {
                            MCInjector.LOG.warning("    Unable to walk: " + m2.name + ' ' + m2.desc + " for " + owner + '/' + mine.name + ' ' + mine.desc);
                        }
                    }
                }
            }
        }

        if (info.getSuper() != null) {
            Method ret = walkBouncers(mtd, info.getSuper());
            if (ret != null)
                return ret;
        }

        if (info.interfaces != null) {
            for (String intf : info.interfaces) {
                Method ret = walkBouncers(mtd, intf);
                if (ret != null)
                    return ret;
            }
        }

        return null;
    }

    private List<Method> findOverrides(MethodInfo mtd, String owner, List<Method> overrides) {
        if (mtd.isStatic() || mtd.isPrivate() || mtd.name.startsWith("<"))
            return overrides;

        ClassInfo info = tree.getInfo(owner);

        if (info == null)
            return overrides;

        if (info.methods != null) {
            for (MethodInfo m : info.methods.values()) {
                Method target = m.bouncer == null ? null : m.bouncer.target;
                if (target != null && mtd.name.equals(target.name) && mtd.desc.equals(target.desc)) {
                    //overrides.add(new Method(info.name, m.name, m.desc)); //Don't add overrides for self-methods
                    findOverrides(m, info.name, overrides);
                }
            }

            MethodInfo mine = info.methods.get(mtd.name + mtd.desc);
            if (mine != null && mine != mtd && (mine.getAccess() & (Opcodes.ACC_FINAL | Opcodes.ACC_PRIVATE)) == 0) {
                if (mine.getOverrides().isEmpty()) {
                    overrides.add(new Method(info.name, mine.name, mine.desc));
                } else {
                    overrides.addAll(mine.getOverrides());
                }
            }
        }

        if (info.getSuper() != null)
            findOverrides(mtd, info.getSuper(), overrides);

        if (info.interfaces != null) {
            for (String intf : info.interfaces)
                findOverrides(mtd, intf, overrides);
        }

        return overrides;
    }

    private void resolveAbstract( ClassInfo cls) {
        Map<String, String> abs = new HashMap<>();
        Set<String> known = new TreeSet<>();
        Queue<String> que = new LinkedList<>();
        Consumer<String> add = c -> {
            if (!known.contains(c)) {
                que.add(c);
                known.add(c);
            }
        };

        add.accept(cls.name);

        while (!que.isEmpty()) {
            ClassInfo info = tree.getInfo(que.poll());
            if (info == null)
                continue;

            if (info.methods != null)
                info.methods.values().stream()
                            .filter(MethodInfo::isAbstract)
                            .filter(mtd -> mtd.overrides == null) //We only want the roots
                            .forEach(mtd -> abs.put(mtd.name + mtd.desc, info.name));

            if (info.getSuper() != null)
                add.accept(info.getSuper());

            if (info.interfaces != null)
                info.interfaces.forEach(add);
        }

        known.clear();
        add.accept(cls.name);

        while (!que.isEmpty()) {
            ClassInfo info = tree.getInfo(que.poll());
            if (info == null)
                continue;

            if (info.methods != null) {
                for (MethodInfo mtd : info.methods.values()) {
                    if (mtd.isAbstract())
                        continue;

                    String towner = abs.remove(mtd.name + mtd.desc);
                    if (towner == null)
                        continue;
                    Method target = new Method(towner, mtd.name, mtd.desc);

                    if (mtd.overrides != null) {
                        for (Method omh : mtd.overrides) {
                            ClassInfo ocls = tree.getInfo(omh.owner);
                            if (towner.equals(omh.owner) || ocls == null) //Error?
                                continue;
                            MethodInfo omtd = ocls.methods == null ? null : ocls.methods.get(omh.name + omh.desc);
                            if (omtd == null) //Error?
                                continue;
                            if (omtd.overrides != null) {
                                if (!omtd.overrides.contains(target))
                                    omtd.overrides.add(target);
                            } else {
                                omtd.setOverrides(Collections.singletonList(target));
                            }
                            break;
                        }
                    } else {
                        if (mtd.overrides != null) {
                            if (!mtd.overrides.contains(target))
                                mtd.overrides.add(target);
                        } else {
                            mtd.setOverrides(Collections.singletonList(target));
                        }
                    }
                }
            }

            if (info.getSuper() != null)
                add.accept(info.getSuper());

            if (info.interfaces != null)
                info.interfaces.forEach(add);
        }

        if (!abs.isEmpty()) {
            MCInjector.LOG.log(Level.SEVERE, "    Unresolved abstracts for: " + cls.name);
            abs.forEach((mtd,c) -> MCInjector.LOG.log(Level.SEVERE, "      " + c + "/" + mtd));
        }
    }

    public static class Tree {
        private Map<String, ClassInfo> classes = new HashMap<>();
        private Set<String> negative = new HashSet<>();
        private Map<String, byte[]> sources = new HashMap<>();

        public Set<String> load(Path path) {
            try (ZipInputStream jin = new ZipInputStream(Files.newInputStream(path))) {
                Set<String> classes = new TreeSet<>();

                ZipEntry entry = null;
                while ((entry = jin.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (entry.isDirectory() || !name.endsWith(".class"))
                        continue;

                    String cls = name.substring(0, name.length() - 6);
                    if (!sources.containsKey(cls)) {
                        byte[] data = readStreamFully(jin);
                        sources.put(cls, data);
                        classes.add(cls);
                    }
                }

                return classes;
            } catch (IOException e) {
                MCInjector.LOG.log(Level.SEVERE, "Failed to load: " + path.toString(), e);
            }
            return Collections.emptySet();
        }

        public ClassInfo getInfo(String cls) {
            if (negative.contains(cls))
                return null;

            ClassInfo ret = classes.get(cls);
            if (ret == null) {
                byte[] data = sources.remove(cls);
                if (data == null) {
                    try (InputStream in = Classpath.class.getClassLoader().getResourceAsStream(cls + ".class")) {
                        if (in == null) {
                            MCInjector.LOG.info("    Failed to find class: " + cls);
                            negative.add(cls);
                            return null;
                        }
                        data = readStreamFully(in);
                    } catch (Throwable e) {
                        MCInjector.LOG.info("    Failed to find class: " + cls);
                        negative.add(cls);
                        return null;
                    }
                }
                ClassNode classNode = new ClassNode();
                ClassReader classReader = new ClassReader(data);
                classReader.accept(classNode, 0);
                ret = new ClassInfo(classNode);
                classes.put(cls, ret);
            }
            return ret;
        }
    }

    public interface IAccessible {
        int getAccess();

        default boolean isInterface() {
            return ((getAccess() & Opcodes.ACC_INTERFACE) != 0);
        }

        default boolean isAbstract() {
            return ((getAccess() & Opcodes.ACC_ABSTRACT) != 0);
        }

        default boolean isSynthetic() {
            return ((getAccess() & Opcodes.ACC_SYNTHETIC) != 0);
        }

        default boolean isAnnotation() {
            return ((getAccess() & Opcodes.ACC_ANNOTATION) != 0);
        }

        default boolean isEnum() {
            return ((getAccess() & Opcodes.ACC_ENUM) != 0);
        }

        default boolean isPackagePrivate() {
            return (getAccess() & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) == 0;
        }

        default boolean isPublic() {
            return (getAccess() & Opcodes.ACC_PUBLIC) != 0;
        }

        default boolean isPrivate() {
            return (getAccess() & Opcodes.ACC_PRIVATE) != 0;
        }

        default boolean isProtected() {
            return (getAccess() & Opcodes.ACC_PROTECTED) != 0;
        }

        default boolean isStatic() {
            return (getAccess() & Opcodes.ACC_STATIC) != 0;
        }

        default boolean isFinal() {
            return (getAccess() & Opcodes.ACC_FINAL) != 0;
        }
    }

    @SuppressWarnings("unused")
    public static class ClassInfo implements IAccessible {
        private final transient String name;
        private final String superName;
        private final List<String> interfaces;
        private final Integer access;
        private final String signature;
        private final Map<String, FieldInfo> fields;
        private final Map<String, MethodInfo> methods;
        private transient boolean resolved = false;

        private ClassInfo(ClassNode node) {
            this.name = node.name;
            this.superName = "java/lang/Object".equals(node.superName) ? null : node.superName;
            this.interfaces = node.interfaces != null && !node.interfaces.isEmpty() ? new ArrayList<>(node.interfaces) : null;
            this.access = node.access == 0 ? null : node.access;
            this.signature = node.signature;

            if (node.fields == null || node.fields.isEmpty()) {
                this.fields = null;
            } else {
                this.fields = new TreeMap<>();
                node.fields.stream().forEach(fld -> this.fields.put(fld.name, new FieldInfo(fld)));
            }

            if (node.methods == null || node.methods.isEmpty()) {
                this.methods = null;
            } else {
                //Gather Lambda methods so we can skip them in bouncers?
                Set<String> lambdas = new HashSet<>();
                for (MethodNode m : node.methods) {
                    for (AbstractInsnNode asn : (Iterable<AbstractInsnNode>)() -> m.instructions.iterator()) {
                        if (asn instanceof InvokeDynamicInsnNode) {
                            InvokeDynamicInsnNode idn = (InvokeDynamicInsnNode)asn;
                            if (LAMBDA_METAFACTORY.equals(idn.bsm) && idn.bsmArgs != null && idn.bsmArgs.length == 3 && idn.bsmArgs[1] instanceof Handle) {
                                Handle target = ((Handle)idn.bsmArgs[1]);
                                lambdas.add(target.getOwner() + '/' + target.getName() + target.getDesc());
                            }
                        }
                    }
                }

                this.methods = new TreeMap<>();
                node.methods.forEach(mtd -> {
                    String key = mtd.name + mtd.desc;
                    this.methods.put(key, new MethodInfo(mtd, lambdas.contains(this.name + '/' + key)));
                });
            }
        }

        public String getSuper() {
            return this.superName == null && !"java/lang/Object".equals(this.name) ? "java/lang/Object" : this.superName;
        }

        @Override
        public int getAccess() {
            return access == null ? 0 : access;
        }

        @Override
        public String toString() {
            return Classpath.getAccess(getAccess()) + ' ' + this.name;
        }

        public MethodInfo getMethodInfo(String name, String desc) {
            return methods.get(name + desc);
        }

        public class FieldInfo implements IAccessible {
            private final transient String name;
            private final transient String desc;
            private final Integer access;
            private final String signature;
            private String force;

            private FieldInfo(FieldNode node) {
                this.name = node.name;
                this.desc = node.desc;
                this.access = node.access == 0 ? null : node.access;
                this.signature = node.signature;
            }

            public void forceName(String name) {
                this.force = name;
            }

            @Override
            public int getAccess() {
                return access == null ? 0 : access;
            }

            @Override
            public String toString() {
                return Classpath.getAccess(getAccess()) + ' ' + this.desc + ' ' + this.name;
            }
        }

        public class MethodInfo implements IAccessible {
            private final transient String name;
            private final transient String desc;
            private final Integer access;
            private final String signature;
            private final Bounce bouncer;
            private String force;
            private List<Method> overrides;

            private MethodInfo(MethodNode node, boolean lambda) {
                this.name = node.name;
                this.desc = node.desc;
                this.access = node.access == 0 ? null : node.access;
                this.signature = node.signature;

                Bounce bounce = null;
                if (!lambda && (node.access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0 && (node.access & Opcodes.ACC_STATIC) == 0) {
                    AbstractInsnNode start = node.instructions.getFirst();
                    if (start instanceof LabelNode && start.getNext() instanceof LineNumberNode)
                        start = start.getNext().getNext();

                    if (start instanceof VarInsnNode) {
                        VarInsnNode n = (VarInsnNode)start;
                        if (n.var == 0 && n.getOpcode() == Opcodes.ALOAD) {
                            AbstractInsnNode end = node.instructions.getLast();
                            if (end instanceof LabelNode)
                                end = end.getPrevious();

                            if (end.getOpcode() >= Opcodes.IRETURN && end.getOpcode() <= Opcodes.RETURN)
                                end = end.getPrevious();

                            if (end instanceof MethodInsnNode) {
                                Type[] args = Type.getArgumentTypes(node.desc);
                                int var = 1;
                                int index = 0;
                                start = start.getNext();
                                while (start != end) {
                                    if (start instanceof VarInsnNode) {
                                        if (((VarInsnNode)start).var != var || index + 1 > args.length) {
                                            //Arguments are switched around, so seems like lambda!
                                            end = null;
                                            break;
                                        }
                                        var += args[index++].getSize();
                                    } else if (start.getOpcode() == Opcodes.INSTANCEOF || start.getOpcode() == Opcodes.CHECKCAST) {
                                        //Valid!
                                    } else {
                                        // Anything else is invalid in a bouncer {As far as I know}, so we're most likely a lambda
                                        end = null;
                                        break;
                                    }
                                    start = start.getNext();
                                }

                                MethodInsnNode mtd = (MethodInsnNode)end;
                                if (end != null && mtd.owner.equals(ClassInfo.this.name) && Type.getArgumentsAndReturnSizes(node.desc) == Type.getArgumentsAndReturnSizes(mtd.desc))
                                    bounce = new Bounce(new Method(mtd.owner, mtd.name, mtd.desc));
                            }
                        }
                    }
                }
                this.bouncer = bounce;
            }

            @Override
            public int getAccess() {
                return access == null ? 0 : access;
            }

            public void forceName(String value) {
                this.force = value;
            }

            public void setOverrides(List<Method> value) {
                this.overrides = value.isEmpty() ? null : value;
            }

            public List<Method> getOverrides() {
                return this.overrides == null ? Collections.emptyList() : this.overrides;
            }

            public String getOwner() {
                return ClassInfo.this.name;
            }

            @Override
            public String toString() {
                return Classpath.getAccess(getAccess()) + ' ' + this.name + ' ' + this.desc;
            }

            public String getName() {
                return name;
            }

            public String getDesc() {
                return desc;
            }
        }
    }

    public static class Method implements Comparable<Method> {
        private final String owner;
        private final String name;
        private final String desc;

        private Method(String owner, String name, String desc) {
            this.owner = owner;
            this.name = name;
            this.desc = desc;
        }

        @Override
        public String toString() {
            return this.owner + '/' + this.name + this.desc;
        }

        @Override
        public int hashCode() {
            return this.toString().hashCode();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Method && o.toString().equals(toString());
        }

        private int compare(int a, int b) {
            return a != 0 ? a : b;
        }

        @Override
        public int compareTo(Method o) {
            return compare(owner.compareTo(o.owner), compare(name.compareTo(o.name), desc.compareTo(o.desc)));
        }

        public String getDesc() {
            return desc;
        }

        public String getName() {
            return name;
        }

        public String getOwner() {
            return owner;
        }
    }

    private static class Bounce {
        private final Method target;
        private Method owner;

        private Bounce(Method target) {
            this.target = target;
        }

        public void setOwner(Method value) {
            this.owner = value;
        }

        @Override
        public String toString() {
            return this.target + " -> " + this.owner;
        }
    }


    public static byte[] readStreamFully(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.max(8192, is.available()));
        byte[] buffer = new byte[8192];
        int read;
        while((read = is.read(buffer)) >= 0) {
            baos.write(buffer, 0, read);
        }
        return baos.toByteArray();
    }

    private static int[]    FLAGS = new int[]   {ACC_PUBLIC, ACC_PRIVATE, ACC_PROTECTED, ACC_STATIC, ACC_FINAL, ACC_SUPER, ACC_SYNCHRONIZED, ACC_VOLATILE, ACC_BRIDGE, ACC_VARARGS, ACC_TRANSIENT, ACC_NATIVE, ACC_INTERFACE, ACC_ABSTRACT, ACC_STRICT, ACC_SYNTHETIC, ACC_ANNOTATION, ACC_ENUM};
    private static String[] NAMES = new String[]{  "public",   "private",   "protected",   "static",   "final",   "super",   "synchronized",   "volitize",   "bridge",   "varargs",   "transient",   "native",   "interface",   "abstract",   "strict",   "synthetic",   "annotation",   "enum"};
    public static String getAccess(int access) {
        StringBuilder out = new StringBuilder();
        for (int x = 0; x < FLAGS.length; x++) {
            if ((access & FLAGS[x]) == FLAGS[x])
                out.append(NAMES[x]).append(" ");
        }
        if (out.length() > 0)
            return out.toString().trim();
        return "default";
    }
}