package mcp.mcinjector;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import mcp.StringUtil;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

public class MCInjectorImpl
{
    private final static Logger log = Logger.getLogger("MCInjector");
    public final Properties mappings = new Properties();
    public final Properties outMappings = new Properties()
    {
        private static final long serialVersionUID = 4112578634029874840L;

        @Override
        @SuppressWarnings({ "unchecked", "rawtypes" })
        public synchronized Enumeration keys()
        {
            Enumeration keysEnum = super.keys();
            Vector keyList = new Vector();
            while (keysEnum.hasMoreElements())
            {
                keyList.add(keysEnum.nextElement());
            }
            Collections.sort(keyList);
            return keyList.elements();
        }
    };
    public int initIndex = 0;
    public boolean generate = false; 

    public static void process(String inFile, String outFile, String mapFile, String logFile, String outMapFile, int index)
        throws IOException
    {
        MCInjectorImpl mci = new MCInjectorImpl(index, outMapFile != null);
        mci.loadMap(mapFile);
        mci.processJar(inFile, outFile);
        if (outMapFile != null)
        {
            mci.saveMap(outMapFile);
        }

        log.info("Processed " + inFile);
    }

    private MCInjectorImpl(int index, boolean generate)
    {
        this.initIndex = index;
        this.generate = generate;
    }

    public void loadMap(String mapFile) throws IOException
    {
        Reader mapReader = null;
        try
        {
            mapReader = new FileReader(mapFile);
            this.mappings.load(mapReader);
            if (initIndex == 0)
            {
            	initIndex = Integer.parseInt(mappings.getProperty("max_constructor_index", "10000"));
            	log.info("Loaded Max Constructor Index: " + initIndex);
            }
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

    public void saveMap(String mapFile) throws IOException
    {
        Writer mapWriter = null;
        try
        {
            mapWriter = new FileWriter(mapFile);
            if (this.initIndex > 0)
            {
            	this.outMappings.put("max_constructor_index", Integer.toString(initIndex));
                this.outMappings.store(mapWriter, "max index=" + this.initIndex);
            }
            else
            {
                this.outMappings.store(mapWriter, null);
            }
        }
        catch (IOException e)
        {
            throw new IOException("Could not write map file: " + e.getMessage());
        }
        finally
        {
            if (mapWriter != null)
            {
                try
                {
                    mapWriter.close();
                }
                catch (IOException e)
                {
                    // ignore;
                }
            }
        }
    }

	public List<String> getExceptions(String signature)
	{
	    String curMap = this.mappings.getProperty(signature);
	    if (curMap == null) return new ArrayList<String>();
        List<String> splitMap = StringUtil.splitString(curMap, "|", -1);
        if (splitMap.get(0).equals("")) return new ArrayList<String>();
        return  StringUtil.splitString(splitMap.get(0), ",");
	}
	
	public List<String> getParams(String signature)
	{
	    String curMap = mappings.getProperty(signature);
	    if (curMap == null) return new ArrayList<String>();
        List<String> split = StringUtil.splitString(curMap, "|", -1);
        if (split.size() <= 1 || split.get(1).equals("")) return new ArrayList<String>();
        return StringUtil.splitString(split.get(1), ",");
	}

	public void setExceptions(String signature, String excs)
	{
		String curMap = outMappings.getProperty(signature);   
	    if (curMap == null) curMap = excs + "|"; 
        List<String> splitMap = StringUtil.splitString(curMap, "|", -1);
    	outMappings.put(signature, excs + "|" + splitMap.get(1));
	}

	public void setParams(String signature, String params)
	{
		String curMap = outMappings.getProperty(signature);   
	    if (curMap == null) curMap = "|" + params;
        List<String> splitMap = StringUtil.splitString(curMap, "|", -1);
    	outMappings.put(signature, splitMap.get(0) + "|" + params);

    	// Add to the input mappings so the generator will power the applier.
		curMap = mappings.getProperty(signature);   
	    if (curMap == null) curMap = "|" + params;
        splitMap = StringUtil.splitString(curMap, "|", -1);
        mappings.put(signature, splitMap.get(0) + "|" + params);
	}

    public void processJar(String inFile, String outFile) throws IOException
    {
        ZipInputStream inJar = null;
        ZipOutputStream outJar = null;

        try
        {
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
                    MCInjectorImpl.log.log(Level.INFO, "Processing " + entryName);

                    entryData = this.processClass(entryData);

                    MCInjectorImpl.log.log(Level.INFO, "Processed " + entryBuffer.size() + " -> " + entryData.length);
                }
                else
                {
                    MCInjectorImpl.log.log(Level.INFO, "Copying " + entryName);
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

    public byte[] processClass(byte[] cls)
    {
        ClassReader cr = new ClassReader(cls);
        ClassNode cn = new ClassNode();
        
        ClassVisitor ca = new ApplyMapClassAdapter(cn, this);
        
        if (generate)
        {
        	ca = new GenerateMapClassAdapter(ca, this);
        }
        
        cr.accept(ca, 0);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(writer);
        return writer.toByteArray();
    }

}
