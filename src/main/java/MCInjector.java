import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;

public class MCInjector
{
    private final static Logger log = Logger.getLogger("MCInjector");
    public final Properties mappings = new Properties();

    public static void main(String[] args)
    {
        if (args.length < 4)
        {
            System.out.println("MCInjector IN OUT MAPFILE [LOGFILE]");
            System.exit(1);
        }

        File inFile = new File(args[0]);
        File outFile = new File(args[1]);
        File mapFile = new File(args[2]);
        String logFile = null;
        if (args.length >= 4)
        {
            logFile = args[3];
        }

        System.out.println("MCInjector v2.0 by Searge, LexManos, Fesh0r");

        MCInjector.log.setUseParentHandlers(false);
        MCInjector.log.setLevel(Level.ALL);

        if (logFile != null)
        {
            try
            {
                FileHandler filehandler = new FileHandler(logFile);
                filehandler.setFormatter(new LogFormatter());
                MCInjector.log.addHandler(filehandler);
            }
            catch (Exception e)
            {
                System.err.println("ERROR: Could not create logfile: " + e.getMessage());
                System.exit(1);
            }
        }

        try
        {
            new MCInjector(mapFile).processJar(inFile, outFile);
        }
        catch (Exception e)
        {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        System.out.println("Processed " + args[0]);
    }

    public MCInjector(File mapFile) throws IOException
    {
        this.loadMap(mapFile);
    }

    private void loadMap(File mapFile) throws IOException
    {
        Reader mapReader = null;
        try
        {
            mapReader = new FileReader(mapFile);
            this.mappings.load(mapReader);
        }
        catch (IOException e)
        {
            throw new IOException("Could not open map file: " + e.getMessage());
        }
        finally
        {
            if (mapReader != null)
            {
                try
                {
                    mapReader.close();
                }
                catch (IOException e)
                {
                    // ignore;
                }
            }
        }
    }

    private void processJar(File inFile, File outFile) throws IOException
    {
        ZipInputStream inJar = null;
        ZipOutputStream outJar = null;

        try
        {
            if (inFile.getCanonicalPath().equals(outFile.getCanonicalPath()))
            {
                throw new IOException("Output and input files must differ");
            }

            try
            {
                inJar = new ZipInputStream(new BufferedInputStream(new FileInputStream(inFile)));
            }
            catch (FileNotFoundException e)
            {
                throw new FileNotFoundException("Could not open input file: " + e.getMessage());
            }

            try
            {
                outJar = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outFile)));
            }
            catch (FileNotFoundException e)
            {
                throw new FileNotFoundException("Could not open output file: " + e.getMessage());
            }

            while (true)
            {
                ZipEntry entry = inJar.getNextEntry();

                if (entry == null)
                {
                    break;
                }

                if (entry.isDirectory())
                {
                    outJar.putNextEntry(entry);
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

                if (entryName.endsWith(".class") && !entryName.startsWith("."))
                {
                    MCInjector.log.log(Level.INFO, "Processing " + entryName);

                    entryData = this.process(entryData);
                    entryData = this.processLVT(entryData);

                    MCInjector.log.log(Level.INFO, "Processed " + entryBuffer.size() + " -> " + entryData.length);
                }
                else
                {
                    MCInjector.log.log(Level.INFO, "Copying " + entryName);
                }

                ZipEntry newEntry = new ZipEntry(entryName);
                outJar.putNextEntry(newEntry);
                outJar.write(entryData);
            }
        }
        finally
        {
            if (outJar != null)
            {
                try
                {
                    outJar.close();
                }
                catch (IOException e)
                {
                    // ignore
                }
            }

            if (inJar != null)
            {
                try
                {
                    inJar.close();
                }
                catch (IOException e)
                {
                    // ignore
                }
            }
        }
    }

    public byte[] process(byte[] cls)
    {
        ClassReader cr = new ClassReader(cls);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ExceptorClassAdapter ca = new ExceptorClassAdapter(cw, this);
        cr.accept(ca, 0);
        return cw.toByteArray();
    }

    public byte[] processLVT(byte[] input)
    {

        ClassReader reader = new ClassReader(input);

        ClassNode classNode = new ClassNode();

        reader.accept(classNode, 0);

        for (MethodNode method : classNode.methods)
        {
            MCInjector.log.log(Level.INFO, classNode.name + "." + method.name);

            if (method.localVariables == null)
            {
                method.localVariables = new ArrayList<LocalVariableNode>();
            }

            if (method.localVariables.size() == 0)
            {
                int idxOffset = 0;
                boolean addThis = false;
                if ((method.access & Opcodes.ACC_STATIC) == 0)
                {
                    idxOffset = 1;
                    addThis = true;
                }

                AbstractInsnNode tmp = method.instructions.getFirst();
                if (tmp == null)
                {
                    method.instructions.add(new LabelNode());
                }
                else if (tmp.getType() != AbstractInsnNode.LABEL)
                {
                    method.instructions.insertBefore(tmp, new LabelNode());
                }
                LabelNode start = (LabelNode)method.instructions.getFirst();

                tmp = method.instructions.getLast();
                if (tmp == null)
                {
                    method.instructions.add(new LabelNode());
                }
                else if (tmp.getType() != AbstractInsnNode.LABEL)
                {
                    method.instructions.insert(tmp, new LabelNode());
                }
                LabelNode end = (LabelNode)method.instructions.getLast();

                if (addThis)
                {
                    MCInjector.log.log(Level.INFO, "Naming argument 0 -> this L" + classNode.name + ";");
                    method.localVariables.add(new LocalVariableNode("this", "L" + classNode.name + ";", null, start, end, 0));
                }

                String[] argTypes = MCInjector.splitArgTypes(null, method.desc);
                for (int x = 0; x < argTypes.length; x++)
                {
                    String arg = "par" + (x + 1);
                    MCInjector.log.log(Level.INFO, "Naming argument " + (x + idxOffset) + " -> " + arg + " " + argTypes[x]);
                    method.localVariables.add(new LocalVariableNode(arg, argTypes[x], null, start, end, x + idxOffset));
                }
            }
            else
            {
                MCInjector.log.log(Level.INFO, "LVT present");
            }
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);

        return writer.toByteArray();
    }

    private static String[] splitArgTypes(String className, String desc)
    {
        ArrayList<String> parts = new ArrayList<String>();
        if ((className != null) && (className.length() != 0))
        {
            parts.add("L" + className + ";");
        }
        int x = 1;
        while (x < desc.length())
        {
            switch (desc.charAt(x))
            {
                case '[':
                    if (desc.charAt(x + 1) == 'L')
                    {
                        int len = desc.indexOf(';', x + 1) + 1;
                        parts.add(desc.substring(x, len));
                        x = len;
                    }
                    else
                    {
                        parts.add(desc.substring(x, x + 2));
                        x += 2;
                    }
                    break;
                case 'L':
                    int len = desc.indexOf(';', x) + 1;
                    parts.add(desc.substring(x, len));
                    x = len;
                    break;
                case ')':
                    x = desc.length();
                    break;
                default:
                    parts.add(desc.substring(x, x + 1));
                    x++;
                    break;
            }
        }
        return parts.toArray(new String[0]);
    }
}
