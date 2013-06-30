import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import mcp.mcinjector.LogFormatter;
import mcp.mcinjector.MCInjectorImpl;

public class MCInjector
{
    private final static Logger log = Logger.getLogger("MCInjector");
	public static final String VERSION = "MCInjector v3.0 by Searge, LexManos, Fesh0r";
	
    public static void main(String[] args) throws Exception
    {
        if (args.length < 3)
        {
            if (args.length > 0)
            {
                if (args[0].equalsIgnoreCase("-help") || args[0].equalsIgnoreCase("--help"))
                {
                    MCInjector.showUsage();
                    System.exit(0);
                }

                if (args[0].equalsIgnoreCase("-version") || args[0].equalsIgnoreCase("--version"))
                {
                    System.out.println(MCInjector.VERSION);
                    System.exit(0);
                }
            }

            MCInjector.showUsage();
            System.exit(1);
        }

        String inFile = args[0];
        String outFile = args[1];
        String inMapFile = args[2];
        String logFile = (args.length > 3 ? args[3] : null);
        String outMapFile = (args.length > 4 ? args[4] : null);
        int index = (args.length > 5 ? Integer.parseInt(args[5]) : 0);

        MCInjector.log.setUseParentHandlers(false);
        MCInjector.log.setLevel(Level.ALL);

        if (logFile != null)
        {
            FileHandler filehandler = new FileHandler(logFile);
            filehandler.setFormatter(new LogFormatter());
            MCInjector.log.addHandler(filehandler);
        }

        System.out.println(MCInjector.VERSION);
        MCInjector.log.info(MCInjector.VERSION);
        MCInjector.log.info("Input: "          + inFile);
        MCInjector.log.info("Output: "         + outFile);
        MCInjector.log.info("Log: "            + logFile);
        MCInjector.log.info("MappingsInput: "  + inMapFile);
        MCInjector.log.info("MappingsOutput: " + outMapFile);
        MCInjector.log.info("Mappings: "       + index);

        try
        {
            MCInjectorImpl.process(inFile, outFile, inMapFile, logFile, outMapFile, index);
        }
        catch (Exception e)
        {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void showUsage()
    {
        System.err.println(MCInjector.VERSION);
        System.err.println("MCInjector IN OUT MAPFILE [LOGFILE] [OUTMAP] [INDEX]");
    }
}
