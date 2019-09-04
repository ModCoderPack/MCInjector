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
            Files.readAllLines(file).forEach(line ->
            {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#"))
                    return;

                String[] parts = line.split(" " );
                if (parts.length == 3)
                {
                    // 'class method_name desc'
                    this.classMemberOverrides.computeIfAbsent(parts[0], k -> new HashSet<>()).add(parts[1] + " " + parts[2]);
                }
                else
                {
                    // 'class/method_name desc'
                    int index = parts[0].lastIndexOf('/');
                    if (index != -1)
                    {
                        String cls = parts[0].substring(0, index);
                        String name = parts[0].substring(index + 1);
                        this.classMemberOverrides.computeIfAbsent(cls, k -> new HashSet<>()).add(name + " " + parts[1]);
                    }
                }
            });
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
