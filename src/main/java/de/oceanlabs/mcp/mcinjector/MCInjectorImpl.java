package de.oceanlabs.mcp.mcinjector;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import de.oceanlabs.mcp.mcinjector.adaptors.AccessFixer;
import de.oceanlabs.mcp.mcinjector.adaptors.ApplyMap;
import de.oceanlabs.mcp.mcinjector.adaptors.InnerClassInitAdder;
import de.oceanlabs.mcp.mcinjector.adaptors.ParameterAnnotationFixer;
import de.oceanlabs.mcp.mcinjector.data.Access;
import de.oceanlabs.mcp.mcinjector.data.Constructors;
import de.oceanlabs.mcp.mcinjector.data.Exceptions;
import de.oceanlabs.mcp.mcinjector.lvt.LVTFernflower;
import de.oceanlabs.mcp.mcinjector.lvt.LVTLvt;
import de.oceanlabs.mcp.mcinjector.lvt.LVTNaming;
import de.oceanlabs.mcp.mcinjector.lvt.LVTStrip;

public class MCInjectorImpl
{
    public LVTNaming naming = LVTNaming.STRIP;
    private Map<String, List<String>> abstractParameters = new HashMap<>();

    static void process(
            Path in, Path out,
            Path accIn, Path accOut,
            Path ctrIn, Path ctrOut,
            Path excIn, Path excOut,
            LVTNaming naming)
        throws IOException
    {
        if (accIn != null)
            Access.INSTANCE.load(accIn);
        if (ctrIn != null)
            Constructors.INSTANCE.load(ctrIn);
        if (excIn != null)
            Exceptions.INSTANCE.load(excIn);

        MCInjector.LOG.info("Processing: " + in);
        MCInjector.LOG.info("  Output: " + out);

        MCInjectorImpl mci = new MCInjectorImpl();
        mci.naming = naming;

        mci.processJar(in, out);

        if (accOut != null)
            Access.INSTANCE.dump(accOut);
        if (ctrOut != null)
            Constructors.INSTANCE.dump(ctrOut);
        if (excOut != null)
            Exceptions.INSTANCE.dump(excOut);

        MCInjector.LOG.info("Processed " + in);
    }

    private MCInjectorImpl(){}

    private void processJar(Path inFile, Path outFile) throws IOException
    {
        Set<String> entries = new HashSet<>();
        try (ZipInputStream inJar = new ZipInputStream(Files.newInputStream(inFile)))
        {
            try (ZipOutputStream outJar = new ZipOutputStream(outFile == null ? new ByteArrayOutputStream() : Files.newOutputStream(outFile, StandardOpenOption.CREATE)))
            {
                for (ZipEntry entry = inJar.getNextEntry(); entry != null; entry = inJar.getNextEntry())
                {
                    if (entry.isDirectory())
                    {
                        outJar.putNextEntry(entry);
                        outJar.closeEntry();
                        continue;
                    }

                    byte[] data = new byte[4096];
                    ByteArrayOutputStream entryBuffer = new ByteArrayOutputStream();

                    int len;
                    do
                    {
                        len = inJar.read(data);
                        if (len > 0)
                        {
                            entryBuffer.write(data, 0, len);
                        }
                    } while (len != -1);

                    byte[] entryData = entryBuffer.toByteArray();

                    String entryName = entry.getName();

                    boolean mojang = entryName.startsWith("net/minecraft/") || entryName.startsWith("com/mojang/");

                    if (entryName.endsWith(".class") && mojang) //TODO: Remove this hardcoding? SRG input? process all?
                    {
                        MCInjector.LOG.log(Level.INFO, "Processing " + entryName);

                        entryData = this.processClass(entryData, outFile == null);

                        MCInjector.LOG.log(Level.INFO, "Processed " + entryBuffer.size() + " -> " + entryData.length);
                    }
                    else
                    {
                        MCInjector.LOG.log(Level.INFO, "Copying " + entryName);
                    }

                    ZipEntry newEntry = new ZipEntry(entryName);
                    newEntry.setTime(0); //Stabilize time.
                    outJar.putNextEntry(newEntry);
                    outJar.write(entryData);
                    outJar.closeEntry();
                    entries.add(entryName);
                }

                if (!abstractParameters.isEmpty() && !entries.contains("fernflower_abstract_parameter_names.txt"))
                {
                    ZipEntry entry = new ZipEntry("fernflower_abstract_parameter_names.txt");
                    entry.setTime(0); //Stabilize time.
                    outJar.putNextEntry(entry);
                    for (String key : abstractParameters.keySet().stream().sorted().collect(Collectors.toList()))
                    {
                        outJar.write(key.getBytes(StandardCharsets.UTF_8));//class method desc
                        outJar.write(' ');
                        outJar.write(abstractParameters.get(key).stream().collect(Collectors.joining(" ")).getBytes(StandardCharsets.UTF_8));
                        outJar.write('\n');
                    }
                    outJar.closeEntry();
                }

                outJar.flush();
            }
        }
    }

    public byte[] processClass(byte[] cls, boolean readOnly)
    {
        ClassReader cr = new ClassReader(cls);
        ClassNode cn = new ClassNode();

        ClassVisitor ca = cn;
        if (readOnly)
        {
        }
        else
        {
            ca = new ApplyMap(this, ca);

            switch (naming)
            {
                case STRIP:      ca = new LVTStrip     (ca); break;
                case FERNFLOWER: ca = new LVTFernflower(ca); break;
                case LVT:        ca = new LVTLvt       (ca); break;
            }

            ca = new AccessFixer(ca);

            ca = new ParameterAnnotationFixer(ca, this);
        }

        ca = new InnerClassInitAdder(ca);

        ca = new ClassVisitor(Opcodes.ASM6, ca) //Top level, so we can print the logs.
        {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces)
            {
                MCInjector.LOG.log(Level.FINE, "Class: " + name + " Extends: " + superName);
                super.visit(version, access, name, signature, superName, interfaces);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions)
            {
                MCInjector.LOG.log(Level.FINE, "  Name: " + name + " Desc: " + desc + " Sig: " + signature);
                return super.visitMethod(access, name, desc, signature, exceptions);
            }
        };

        cr.accept(ca, 0);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(writer);
        return writer.toByteArray();
    }

    private static Field field_mv;
    public static MethodNode getMethodNode(MethodVisitor mv)
    {
        try
        {
            if (field_mv == null)
            {
                field_mv = MethodVisitor.class.getDeclaredField("mv");
                field_mv.setAccessible(true);
            }
            MethodVisitor tmp = mv;
            while (!(tmp instanceof MethodNode) && tmp != null)
                tmp = (MethodVisitor)field_mv.get(tmp);
            return (MethodNode)tmp;
        }
        catch (Exception e)
        {
            if (e instanceof RuntimeException)
                throw (RuntimeException)e;
            throw new RuntimeException(e);

        }
    }

    private static Field field_cv;
    public static ClassNode getClassNode(ClassVisitor cv)
    {
        try
        {
            if (field_cv == null)
            {
                field_cv = ClassVisitor.class.getDeclaredField("cv");
                field_cv.setAccessible(true);
            }
            ClassVisitor tmp = cv;
            while (!(tmp instanceof ClassNode) && tmp != null)
                tmp = (ClassVisitor)field_cv.get(tmp);
            return (ClassNode)tmp;
        }
        catch (Exception e)
        {
            if (e instanceof RuntimeException)
                throw (RuntimeException)e;
            throw new RuntimeException(e);

        }
    }

    public void storeAbstractParameters(String className, String name, String desc, List<String> params)
    {
        abstractParameters.put(className + ' ' + name  + ' ' + desc, params);
    }
}
