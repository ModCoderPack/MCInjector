import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import mcp.mcinjector.LogFormatter;
import mcp.mcinjector.MCInjectorImpl;

public class MCInjector
{
    private final static Logger log = Logger.getLogger("MCInjector");
	public static final String VERSION = "MCInjector v3.1 by Searge, LexManos, Fesh0r";
	
    public static void main(String[] args) throws Exception
    {
    	
    	OptionParser parser = new OptionParser();
    	parser.accepts("help").forHelp();
    	parser.accepts("version").forHelp();
    	parser.accepts("jarIn").withRequiredArg().required();
    	parser.accepts("jarOut").withRequiredArg();
    	parser.accepts("mapIn").withRequiredArg().required();
    	parser.accepts("mapOut").withRequiredArg();
    	parser.accepts("log").withRequiredArg();
    	parser.accepts("index").withRequiredArg().ofType(Integer.class).defaultsTo(0);
    	parser.accepts("jsonIn").withRequiredArg();
    	parser.accepts("applyMarkers");

    	try
    	{
    		OptionSet options = parser.parse(args);
    		if (options.has("help"))
    		{
    			System.out.println(VERSION);
    			parser.printHelpOn(System.out);
    			return;
    		}
    		else if (options.has("version"))
    		{
    			System.out.println(VERSION);
    			return;
    		}

	        String jarIn   = (String)options.valueOf("jarIn");
	        String jarOut  = (String)options.valueOf("jarOut");
	        String mapIn   = (String)options.valueOf("mapIn");
	        String mapOut  = (String)options.valueOf("mapOut");
	        String log     = (String)options.valueOf("log");
	        String jsonIn  = (String)options.valueOf("jsonIn");
	        int index      = (Integer)options.valueOf("index");
	        boolean applyM = options.has("applyMarkers");
	
	        MCInjector.log.setUseParentHandlers(false);
	        MCInjector.log.setLevel(Level.ALL);
	
	        if (log != null)
	        {
	            FileHandler filehandler = new FileHandler(log);
	            filehandler.setFormatter(new LogFormatter());
	            MCInjector.log.addHandler(filehandler);
	            MCInjector.log.addHandler(new Handler()
	            {
					@Override
					public void publish(LogRecord record)
					{
						System.out.println(String.format(record.getMessage(), record.getParameters()));
						
					}
					@Override public void flush() {}
					@Override public void close() throws SecurityException {}
	            });
	        }
	
	        log(MCInjector.VERSION);
	        log("Input:          " + jarIn);
	        log("Output:         " + jarOut);
	        log("Log:            " + log);
	        log("MappingsInput:  " + mapIn);
	        log("MappingsOutput: " + mapOut);
	        log("Mappings:       " + index);
	        log("Json:           " + jsonIn);
	        log("ApplyMarker:    " + applyM);
	
	        try
	        {
	            MCInjectorImpl.process(jarIn, jarOut, mapIn, log, mapOut, index, jsonIn, applyM);
	        }
	        catch (Exception e)
	        {
	            System.err.println("ERROR: " + e.getMessage());
	            e.printStackTrace();
	            System.exit(1);
	        }
    	}
    	catch (OptionException e)
    	{
    		parser.printHelpOn(System.out);
    		e.printStackTrace();
    	}
    }

    private static void log(String line)
    {
    	log.info(line);
    }
}
