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


public class Exceptor
{
    private final static Logger log = Logger.getLogger("Exceptor");
    public final Properties mappings;

    public static void main(String[] args)
    {
        if(args.length < 4)
        {
            System.out.println("Exceptor [IN] [OUT] [MAPFILE] [LOGFILE]");
            System.exit(1);
        }

        Formatter formatter = new ExceptorFormatter();
        log.setUseParentHandlers(false);
        log.setLevel(Level.ALL);

        try
        {
            FileHandler filehandler = new FileHandler(args[3], false);
            filehandler.setFormatter(formatter);
            log.addHandler(filehandler);
        }
        catch(Exception exception)
        {
            System.out.println("Could not create logfile");
            System.exit(1);
        }

        System.out.println("Exceptor v1.0 by Searge");
        log.log(Level.INFO, "Exceptor v1.0 by Searge");
        log.log(Level.INFO, "Input: " + args[0]);
        log.log(Level.INFO, "Output: " + args[1]);
        log.log(Level.INFO, "Mappings: " + args[2]);

        Exceptor exc = new Exceptor();
        if(!exc.processJar(args[0], args[1], args[2]))
        {
            System.out.println("Error processing the jar");
            System.exit(1);
        }

        System.out.println("Processed " + args[0]);
    }

    public Exceptor()
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
                if(reader != null)
                    reader.close();
                if(instream != null)
                    instream.close();
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }

        return true;
    }

    public boolean processJar(String inFileName, String outFileName, String configFile)
    {
        if(!loadMappings(configFile))
        {
            System.out.println("Can't load mappings");
            return false;
        }

        File inFile = new File(inFileName);
        File outFile = new File(outFileName);

        if(!inFile.isFile())
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

        boolean result = processJar(inStream, outStream);

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
        while(reading)
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

            if(entry == null)
            {
                reading = false;
                continue;
            }

            if(entry.isDirectory())
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
                    if(len > 0)
                        entryBuffer.write(data, 0, len);
                }
                while(len != -1);
            }
            catch (IOException e)
            {
                e.printStackTrace();
                continue;
            }

            byte[] entryData = entryBuffer.toByteArray();

            String entryName = entry.getName();
            log.log(Level.INFO, "Processing " + entryName);

            if(entryName.endsWith(".class"))
                entryData = process(entryData);

            log.log(Level.INFO, "Processed " + entryBuffer.size() + " -> " + entryData.length);

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
}
