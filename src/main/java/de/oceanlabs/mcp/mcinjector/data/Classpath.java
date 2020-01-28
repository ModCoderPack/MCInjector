package de.oceanlabs.mcp.mcinjector.data;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public enum Classpath {

    INSTANCE;

    private Map<String, ClassNode> classes;
    private List<ZipFile> classpath;

    public void load(Path[] classpath) throws IOException {
        this.classes = new HashMap<>();
        this.classpath = new ArrayList<>();
        if (classpath != null) {
            for (Path path : classpath) {
                add(path);
            }
        }
    }

    public void add(Path path) throws IOException {
        File toFile = path.toFile();
        ZipFile zipFile = new ZipFile(toFile);
        this.classpath.add(zipFile);
    }

    private String descriptorToPath(String s){
        if (s.startsWith("L") && s.endsWith(";")) {
            return s.substring(1, s.length()-1) + ".class";
        }
        throw new IllegalArgumentException("Invalid class " + s);
    }

    public ClassNode loadClass(String name)  {
        ClassNode node = classes.get(name);
        String path = descriptorToPath(name);
        if (node == null) {
            try {
                for (ZipFile zf : classpath) {
//                    System.out.println("Looking in " + zf.getName());
                    ZipEntry ze = zf.getEntry(path);
                    if (ze != null) {
                        ClassReader cr = new ClassReader(zf.getInputStream(ze));
                        node = new ClassNode();
                        cr.accept(node, 0);
                        classes.put(name, node);
                        return node;
                    }
                }
                ClassReader cr = new ClassReader(Classpath.class.getClassLoader().getResourceAsStream(path));
                node = new ClassNode();
                cr.accept(node, 0);
                classes.put(name, node);
            } catch (IOException e) {
                throw new RuntimeException("Class " + name + " = " + path, e);
            }
        }
        return node;
    }

    public List<ClassNode> findOverrides(ClassNode node, MethodNode method) {
        boolean found = false;
        for (MethodNode mn : node.methods) {
            if (mn.name.equals(method.name) && mn.desc.equals(method.desc)) {
                found = true;
                break;
            }
        }
        if (!found) {
            return new ArrayList<>();
        }
        if ((method.access & Opcodes.ACC_PRIVATE) != 0) {
            ArrayList<ClassNode> arr = new ArrayList<>();
            arr.add(node);
            return arr;
        }
        if (node.superName == null) {
            ArrayList<ClassNode> arr = new ArrayList<>();
            arr.add(node);
            return arr;
        }
        List<ClassNode> parent = findOverrides(loadClass("L" + node.superName + ";"), method);
        for (String ifn : node.interfaces) {
           parent.addAll(findOverrides(loadClass("L" + ifn + ";"), method));
        }
        parent.add(node);
        return parent;
    }

}
