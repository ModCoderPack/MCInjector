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
import java.util.Arrays;
import java.util.List;
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
import org.objectweb.asm.Type;
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

                    entryData = this.processClass(entryData);

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

    public byte[] processClass(byte[] input)
    {
        ClassReader reader = new ClassReader(input);

        ClassNode classNode = new ClassNode();

        reader.accept(classNode, 0);

        MCInjector.log.log(Level.FINE, "Class: " + classNode.name + " Extends: " + classNode.superName);

        for (MethodNode methodNode : classNode.methods)
        {
            MCInjector.log.log(Level.FINER, "Name: " + methodNode.name + " Desc: " + methodNode.desc);

            List<String> exceptions = this.processExceptions(classNode, methodNode);
            List<String> variables = this.processLVT(classNode, methodNode);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);

        return writer.toByteArray();
    }

    private List<String> processExceptions(ClassNode classNode, MethodNode methodNode)
    {
        String clsSig = classNode.name + "." + methodNode.name + methodNode.desc;

        String excList = this.mappings.getProperty(clsSig);
        if (excList != null)
        {
            MCInjector.log.log(Level.FINE, "Adding Exceptions: " + excList + " to " + clsSig);
            methodNode.exceptions = MCInjector.getExceptions(excList);
        }

        return methodNode.exceptions;
    }

    private static List<String> getExceptions(String exceptionList)
    {
        return Arrays.asList(exceptionList.split(","));
    }

    private List<String> processLVT(ClassNode classNode, MethodNode methodNode)
    {
        String clsSig = classNode.name + "." + methodNode.name + methodNode.desc;

        if (methodNode.localVariables == null)
        {
            methodNode.localVariables = new ArrayList<LocalVariableNode>();
        }

        if (methodNode.localVariables.size() == 0)
        {
            int idxOffset = 0;
            boolean addThis = false;
            if ((methodNode.access & Opcodes.ACC_STATIC) == 0)
            {
                idxOffset = 1;
                addThis = true;
            }

            // get first instruction, adding label if necessary
            AbstractInsnNode tmp = methodNode.instructions.getFirst();
            if (tmp == null)
            {
                methodNode.instructions.add(new LabelNode());
            }
            else if (tmp.getType() != AbstractInsnNode.LABEL)
            {
                methodNode.instructions.insertBefore(tmp, new LabelNode());
            }
            LabelNode start = (LabelNode)methodNode.instructions.getFirst();

            // get last instruction, adding label if necessary
            tmp = methodNode.instructions.getLast();
            if (tmp == null)
            {
                methodNode.instructions.add(new LabelNode());
            }
            else if (tmp.getType() != AbstractInsnNode.LABEL)
            {
                methodNode.instructions.insert(tmp, new LabelNode());
            }
            LabelNode end = (LabelNode)methodNode.instructions.getLast();

            Type[] argTypes = Type.getArgumentTypes(methodNode.desc);

            if (addThis)
            {
                int index = 0;
                String arg = "this";
                String desc = "L" + classNode.name + ";";

                MCInjector.log.log(Level.FINE, "Naming argument " + index + " -> " + arg + " " + desc);
                methodNode.localVariables.add(new LocalVariableNode(arg, desc, null, start, end, index));
            }

            for (int x = 0; x < argTypes.length; x++)
            {
                int index = x + idxOffset;
                String arg = "par" + (x + 1);
                String desc = argTypes[x].getDescriptor();

                MCInjector.log.log(Level.FINE, "Naming argument " + index + " -> " + arg + " " + desc);
                methodNode.localVariables.add(new LocalVariableNode(arg, desc, null, start, end, index));
            }
        }
        else
        {
            MCInjector.log.log(Level.FINE, "LVT present");
        }

        List<String> variables = new ArrayList<String>();

        for (LocalVariableNode lv : methodNode.localVariables)
        {
            variables.add(lv.name);
        }

        return variables;
    }
}
