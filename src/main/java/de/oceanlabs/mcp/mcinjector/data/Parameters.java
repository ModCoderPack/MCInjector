package de.oceanlabs.mcp.mcinjector.data;

import de.oceanlabs.mcp.mcinjector.MCInjector;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;

public enum Parameters
{
    INSTANCE;

    private Map<String, Integer> fromDesc = new HashMap<>();
    private Map<Integer, String> fromID = new HashMap<>();
    private int maxID  = 0;

    public boolean load(Path file)
    {
        this.fromDesc.clear();
        this.fromID.clear();
        System.out.println(file.toFile().exists());
        try
        {
            MCInjector.LOG.fine("Loading Parameters from: " + file);
            Files.readAllLines(file).forEach(line ->
            {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#"))
                    return;

                String[] parts = line.split(" " );
                int id = Integer.parseInt(parts[0]);
                MCInjector.LOG.fine("    Method parameter ID loaded " + id + " " + parts[0] + " " + parts[1] + " " + parts[3]);
                this.setName(parts[1], parts[2], parts[3], id);
            });
        }
        catch (IOException e)
        {
            e.printStackTrace();
            MCInjector.LOG.warning("Could not load Parameters list: " + e.toString());
            return false;
        }
        return true;
    }

    public boolean loadLegacy(Path file)
    {
        try {
            MCInjector.LOG.fine("Loading Constructors from: " + file);
            Files.readAllLines(file).forEach(line ->
            {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#"))
                    return;

                String[] parts = line.split(" " );
                int id = Integer.parseInt(parts[0]);
                MCInjector.LOG.fine("    Legacy Constructor ID loaded " + id + " " + parts[0] + " " + parts[1]);
                this.setName(parts[1], "<init>", parts[2], id);
            });
            return true;
        }
        catch (IOException e)
        {
            e.printStackTrace();
            MCInjector.LOG.warning("Could not import Constructors list: " + e.toString());
            return false;
        }
    }

    public boolean dump(Path file)
    {
        try
        {
            List<String> ret = this.fromID.entrySet().stream()
            .sorted((e1, e2) -> e1.getKey().compareTo(e2.getKey()))
            .map(e -> e.getKey() + " " + e.getValue())
            .collect(Collectors.toList());
            Files.write(file, String.join("\n", ret).getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
        }
        catch (IOException e)
        {
            e.printStackTrace();
            MCInjector.LOG.warning("Could not dump Parameters list: " + e.toString());
            return false;
        }
        return true;
    }

    public void setName(String cls, String method, String desc, int id)
    {
        if (id < 0)
            throw new IllegalArgumentException("ID must be positive: " + id);
        this.maxID = Math.max(this.maxID, id);
        this.fromDesc.put(cls + " " + method + " " + desc, id);
        this.fromID  .put(id, cls + " " + method + " " + desc);
    }

    public String getName(List<Classpath.Method> overrides, String cls, String method, String desc, boolean generate)
    {
        OptionalInt id;
        if (!method.startsWith("<") && !overrides.isEmpty()) {
            Classpath.Method canonicalSuper = overrides.get(0);
            Integer cid = this.fromDesc.get(canonicalSuper.getOwner() + " " + canonicalSuper.getName() + " " + canonicalSuper.getDesc());
            if (cid != null) {
                System.out.println("Using param name from superclass canon " + canonicalSuper + " for " + cls + " " + method + " " + desc);
                return cid.toString();
            }
            id = overrides.stream().skip(1)
                                   .map(s -> s.getOwner() + " " + s.getName() + " " + s.getDesc())
                                   .map(fromDesc::get)
                                   .filter(Objects::nonNull)
                                   .mapToInt(x -> x)
                                   .min();

            if (!id.isPresent()) {
                Integer i = fromDesc.get(cls + " " + method + " " + desc);
                id = i == null ? OptionalInt.empty() : OptionalInt.of(i);
            }
        } else {
            Integer i = fromDesc.get(cls + " " + method + " " + desc);
            id = i == null ? OptionalInt.empty() : OptionalInt.of(i);
        }
        if (!id.isPresent())
        {
            if (!generate) {
                if (!method.equals("<init>") && !desc.equals("()V")) {
                    System.out.println("Could not find param id for " + cls + " " + method + " " + desc);
                    for (Classpath.Method md : overrides) {
                        System.out.println("Tried: " + md.toString());
                    }
                }
                return method; //if we are not generating new names we will return the old parameter format, _p_funcname_x_
            }
            int newId = ++maxID;
            this.setName(cls, method, desc, newId);
            return Integer.toString(newId);
        } else {
            for(Classpath.Method mi : overrides) {
                if (fromDesc.containsKey(mi.getOwner() + " " + mi.getName() + " " + mi.getDesc())) {
                    if (fromDesc.get(mi.getOwner() + " " + mi.getName() + " " + mi.getDesc()) == id.getAsInt()) {
                        System.out.println("Using param name from superclass noc" + mi.getOwner() + " " + mi.getName()+mi.getDesc() + " for " + cls + " " + method + " " + desc);
                        break;
                    }
                }
            }
        }
        return id.toString();
    }
}
