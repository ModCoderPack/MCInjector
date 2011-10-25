import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
                System.err.println("Could not create logfile: " + e.getMessage());
                System.exit(1);
            }
        }

        try
        {
            new MCInjector().processJar(inFile, outFile, mapFile);
        }
        catch (IOException e)
        {
            System.err.println(e.getMessage());
            System.exit(1);
        }

        System.out.println("Processed " + args[0]);
    }

    public void processJar(File inFile, File outFile, File mapFile) throws IOException
    {
        this.loadMap(mapFile);

        InputStream inStream = null;
        OutputStream outStream = null;

        try
        {
            if (inFile.getCanonicalPath().equals(outFile.getCanonicalPath()))
            {
                throw new IOException("Output and input files must differ");
            }

            try
            {
                inStream = new FileInputStream(inFile);
            }
            catch (FileNotFoundException e)
            {
                throw new FileNotFoundException("Could not open input file: " + e.getMessage());
            }

            try
            {
                outStream = new FileOutputStream(outFile);
            }
            catch (FileNotFoundException e)
            {
                throw new FileNotFoundException("Could not open output file: " + e.getMessage());
            }

            this.processJar(inStream, outStream);
        }
        finally
        {
            if (outStream != null)
            {
                try
                {
                    outStream.close();
                }
                catch (IOException e)
                {
                    // ignore
                }
            }

            if (inStream != null)
            {
                try
                {
                    inStream.close();
                }
                catch (IOException e)
                {
                    // ignore
                }
            }
        }
    }

    private void loadMap(File mapFile) throws IOException
    {
        InputStream mapStream = null;
        try
        {
            mapStream = new FileInputStream(mapFile);
            this.mappings.load(mapStream);
        }
        catch (IOException e)
        {
            throw new IOException("Could not open map file: " + e.getMessage());
        }
        finally
        {
            if (mapStream != null)
            {
                try
                {
                    mapStream.close();
                }
                catch (IOException e)
                {
                    // ignore;
                }
            }
        }
    }

    public boolean processJar(InputStream inStream, OutputStream outStream)
    {
        ZipInputStream inJar = new ZipInputStream(inStream);
        ZipOutputStream outJar = new ZipOutputStream(outStream);

        boolean reading = true;
        while (reading)
        {
            ZipEntry entry;
            try
            {
                entry = inJar.getNextEntry();
            }
            catch (IOException e)
            {
                System.out.println("Could not get entry");
                return false;
            }

            if (entry == null)
            {
                reading = false;
                continue;
            }

            if (entry.isDirectory())
            {
                try
                {
                    outJar.putNextEntry(entry);
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                    return false;
                }
                continue;
            }

            byte[] data = new byte[4096];
            ByteArrayOutputStream entryBuffer = new ByteArrayOutputStream();

            try
            {
                int len;
                do
                {
                    len = inJar.read(data);
                    if (len > 0)
                    {
                        entryBuffer.write(data, 0, len);
                    }
                } while (len != -1);
            }
            catch (IOException e)
            {
                e.printStackTrace();
                continue;
            }

            byte[] entryData = entryBuffer.toByteArray();

            String entryName = entry.getName();
            MCInjector.log.log(Level.INFO, "Processing " + entryName);

            if (entryName.endsWith(".class"))
            {
                entryData = this.process(entryData);
                entryData = this.processLVT(entryData);
            }

            MCInjector.log.log(Level.INFO, "Processed " + entryBuffer.size() + " -> " + entryData.length);

            try
            {
                ZipEntry newEntry = new ZipEntry(entryName);
                outJar.putNextEntry(newEntry);
            }
            catch (IOException e)
            {
                e.printStackTrace();
                return false;
            }

            try
            {
                outJar.write(entryData);
            }
            catch (IOException e)
            {
                e.printStackTrace();
                return false;
            }
        }

        try
        {
            outJar.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return false;
        }
        finally
        {
            try
            {
                inJar.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
                return false;
            }
        }

        return true;
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
