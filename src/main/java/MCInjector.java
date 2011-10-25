import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
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
    public final Properties mappings;

    public static void main(String[] args)
    {
        if (args.length < 4)
        {
            System.out.println("MCInjector [IN] [OUT] [MAPFILE] [LOGFILE]");
            System.exit(1);
        }

        Formatter formatter = new LogFormatter();
        MCInjector.log.setUseParentHandlers(false);
        MCInjector.log.setLevel(Level.ALL);

        try
        {
            FileHandler filehandler = new FileHandler(args[3], false);
            filehandler.setFormatter(formatter);
            MCInjector.log.addHandler(filehandler);
        }
        catch (Exception exception)
        {
            System.out.println("Could not create logfile");
            System.exit(1);
        }

        System.out.println("MCInjector v1.0 by Searge");
        MCInjector.log.log(Level.INFO, "MCInjector v1.0 by Searge");
        MCInjector.log.log(Level.INFO, "Input: " + args[0]);
        MCInjector.log.log(Level.INFO, "Output: " + args[1]);
        MCInjector.log.log(Level.INFO, "Mappings: " + args[2]);

        MCInjector exc = new MCInjector();
        if (!exc.processJar(args[0], args[1], args[2]))
        {
            System.out.println("Error processing the jar");
            System.exit(1);
        }

        System.out.println("Processed " + args[0]);
    }

    public MCInjector()
    {
        this.mappings = new Properties();
    }

    public boolean loadMappings(String fileName)
    {
        InputStream instream = null;
        Reader reader = null;
        try
        {
            instream = new FileInputStream(fileName);
            reader = new InputStreamReader(instream);
            this.mappings.load(reader);
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
                if (reader != null)
                {
                    reader.close();
                }
                if (instream != null)
                {
                    instream.close();
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        return true;
    }

    public boolean processJar(String inFileName, String outFileName, String configFile)
    {
        if (!this.loadMappings(configFile))
        {
            System.out.println("Can't load mappings");
            return false;
        }

        File inFile = new File(inFileName);
        File outFile = new File(outFileName);

        if (!inFile.isFile())
        {
            System.out.println("Can't find input file");
            return false;
        }

        OutputStream outStream;
        try
        {
            outStream = new FileOutputStream(outFile);
        }
        catch (FileNotFoundException e1)
        {
            e1.printStackTrace();
            return false;
        }

        InputStream inStream;
        try
        {
            inStream = new FileInputStream(inFile);
        }
        catch (FileNotFoundException e)
        {
            try
            {
                outStream.close();
                outFile.delete();
            }
            catch (IOException e1)
            {
                e1.printStackTrace();
            }
            e.printStackTrace();
            return false;
        }

        boolean result = this.processJar(inStream, outStream);

        try
        {
            outStream.close();
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
                inStream.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
                return false;
            }
        }

        return result;
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

        for (MethodNode method : (List<MethodNode>)classNode.methods)
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
