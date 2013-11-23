package de.oceanlabs.mcp.mcinjector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

public class StringUtil
{
    public static List<String> splitString(String in, String delimiter)
    {
        return StringUtil.splitString(in, delimiter, 0);
    }

    public static List<String> splitString(String in, String delimiter, int limit)
    {
        if (in == null)
        {
            return null;
        }

        List<String> out = new ArrayList<String>(Arrays.asList(in.split(Pattern.quote(delimiter), limit)));

        while (out.size() < limit)
        {
            out.add("");
        }

        return out;
    }


    public static String joinString(List<String> in, String delimiter)
    {
        return StringUtil.joinString(in, delimiter, 0);
    }

    public static String joinString(List<String> in, String delimiter, int limit)
    {
        if (in == null)
        {
            return null;
        }

        in = new ArrayList<String>(in);

        while (in.size() < limit)
        {
            in.add("");
        }

        StringBuilder out = new StringBuilder();

        Iterator<String> iter = in.iterator();

        while (iter.hasNext())
        {
            out.append(iter.next());

            if (iter.hasNext())
            {
                out.append(delimiter);
            }
        }

        return out.toString();
    }

}
