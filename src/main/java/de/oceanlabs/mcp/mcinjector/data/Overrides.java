package de.oceanlabs.mcp.mcinjector.data;

import de.oceanlabs.mcp.mcinjector.MCInjector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public enum Overrides
{
    INSTANCE;

    private Map<String, Set<String>> classMemberOverrides = new HashMap<>();

    public boolean load(Path file)
    {
        this.classMemberOverrides.clear();
        try
        {
            MCInjector.LOG.fine("Loading Override list from: " + file);
            Set<String> mthSet = null;
            for(String line : Files.readAllLines(file))
            {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#"))
                    continue;

                String[] parts = line.split(" " );
                if (parts[0].charAt(0) == '\t')
                {
                    if (mthSet == null)
                    {
                        throw new IOException("Invalid TSRG line, missing class: " + line);
                    }
                    mthSet.add(parts[1] + " " + parts[2]);
                }
                else
                {
                    mthSet = this.classMemberOverrides.computeIfAbsent(parts[0], k -> new HashSet<>());
                }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
            MCInjector.LOG.warning("Could not load Override list: " + e.toString());
            return false;
        }
        return true;
    }

    public Set<String> getOverrides(String className)
    {
        return this.classMemberOverrides.getOrDefault(className, Collections.emptySet());
    }
}
