package com.denizenscript.denizencore.utilities;

import com.denizenscript.denizencore.objects.*;
import com.denizenscript.denizencore.objects.core.*;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import com.denizenscript.denizencore.objects.properties.Property;
import com.denizenscript.denizencore.objects.properties.PropertyParser;
import com.denizenscript.denizencore.tags.Attribute;
import com.denizenscript.denizencore.tags.TagContext;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class CoreUtilities {

    public static TagContext noDebugContext;

    public static String stringifyNullPass(Object obj) {
        return obj == null ? null : obj.toString();
    }

    public static ObjectTag fixType(ObjectTag input, TagContext context) {
        if (input instanceof ElementTag) {
            return ObjectFetcher.pickObjectFor(input.toString(), context);
        }
        return input;
    }

    public static void autoPropertyMechanism(ObjectTag object, Mechanism mechanism) {
        PropertyParser.ClassPropertiesInfo properties = PropertyParser.propertiesByClass.get(object.getObjectTagClass());
        if (properties == null) {
            return;
        }
        PropertyParser.PropertyGetter specificGetter = properties.propertiesByMechanism.get(mechanism.getName());
        if (specificGetter != null) {
            Property prop = specificGetter.get(object);
            if (prop == null) {
                return;
            }
            prop.adjust(mechanism);
            return;
        }
        for (PropertyParser.PropertyGetter listGetter : properties.propertiesAnyMechs) {
            Property prop = listGetter.get(object);
            if (prop != null) {
                prop.adjust(mechanism);
                if (mechanism.fulfilled()) {
                    return;
                }
            }
        }
    }

    public static ObjectTag autoPropertyTagObject(ObjectTag object, Attribute attribute) {
        if (attribute.isComplete()) {
            return null;
        }
        PropertyParser.ClassPropertiesInfo properties = PropertyParser.propertiesByClass.get(object.getObjectTagClass());
        if (properties == null) {
            return null;
        }
        String tagName = attribute.getAttributeWithoutContext(1);
        PropertyParser.PropertyGetter specificGetter = properties.propertiesByTag.get(tagName);
        if (specificGetter != null) {
            Property prop = specificGetter.get(object);
            if (prop == null) {
                String propName = properties.propertyNamesByTag.get(tagName);
                attribute.seemingSuccesses.add(attribute.getAttributeWithoutContext(1) + " - property " + propName + " matched, but is not valid for the object.");
                return null;
            }
            return prop.getObjectAttribute(attribute);
        }
        for (PropertyParser.PropertyGetter listGetter : properties.propertiesAnyTags) {
            Property prop = listGetter.get(object);
            if (prop != null) {
                ObjectTag returned = prop.getObjectAttribute(attribute);
                if (returned != null) {
                    return returned;
                }
            }
        }
        return null;
    }

    public static String autoPropertyTag(ObjectTag object, Attribute attribute) {
        if (attribute.isComplete()) {
            return null;
        }
        PropertyParser.ClassPropertiesInfo properties = PropertyParser.propertiesByClass.get(object.getObjectTagClass());
        if (properties == null) {
            return null;
        }
        String tagName = attribute.getAttributeWithoutContext(1);
        PropertyParser.PropertyGetter specificGetter = properties.propertiesByTag.get(tagName);
        if (specificGetter != null) {
            Property prop = specificGetter.get(object);
            if (prop == null) {
                String propName = properties.propertyNamesByTag.get(tagName);
                attribute.seemingSuccesses.add(attribute.getAttributeWithoutContext(1) + " - property " + propName + " matched, but is not valid for the object.");
                return null;
            }
            return prop.getAttribute(attribute);
        }
        for (PropertyParser.PropertyGetter listGetter : properties.propertiesAnyTags) {
            Property prop = listGetter.get(object);
            if (prop != null) {
                String returned = prop.getAttribute(attribute);
                if (returned != null) {
                    return returned;
                }
            }
        }
        return null;
    }

    public static ObjectTag autoAttrib(Property inp, Attribute attribute) {
        if (attribute.isComplete()) {
            return null;
        }
        return inp.getObjectAttribute(attribute);
    }

    public static ObjectTag autoAttribTyped(ObjectTag inp, Attribute attribute) {
        return autoAttrib(fixType(inp, attribute.context), attribute);
    }

    public static ObjectTag autoAttrib(ObjectTag inp, Attribute attribute) {
        if (inp == null) {
            Debug.echoError("Tag parse failed (null return) for tag <" + attribute.toString() + ">!");
            return null;
        }
        if (attribute.isComplete()) {
            return inp;
        }
        return inp.getObjectAttribute(attribute);
    }

    public static <T extends ObjectTag> T asType(ObjectTag inp, Class<T> type, TagContext context) {
        if (inp.getObjectTagClass() == type) {
            return (T) inp;
        }
        if (type == ElementTag.class) {
            return (T) new ElementTag(inp.toString());
        }
        return ObjectFetcher.getObjectFrom(type, inp.toString(), context);
    }

    public static abstract class TypeComparisonRunnable {
        public abstract boolean canBecome(ObjectTag inp);
    }

    public final static Map<Class<? extends ObjectTag>, TypeComparisonRunnable> typeCheckers = new HashMap<>();

    static {
        registerTypeAsTrueAlways(ElementTag.class);
        registerTypeAsTrueAlways(ListTag.class);
        registerTypeAsNoOtherTypeCode(ScriptTag.class, "s");
        registerTypeAsNoOtherTypeCode(DurationTag.class, "d");
        registerTypeAsNoOtherTypeCode(CustomObjectTag.class, "custom");
        registerTypeAsNoOtherTypeCode(QueueTag.class, "q");
    }

    public static void registerTypeAsNoOtherTypeCode(Class<? extends ObjectTag> type, final String knownCode) {
        typeCheckers.put(type, new TypeComparisonRunnable() {
            @Override
            public boolean canBecome(ObjectTag inp) {
                String simple = inp.identifySimple();
                int atIndex = simple.indexOf('@');
                if (atIndex != -1) {
                    String code = simple.substring(0, atIndex);
                    if (!code.equals(knownCode)) {
                        return false;
                    }
                }
                return true;
            }
        });
    }

    public static void registerTypeAsTrueAlways(Class<? extends ObjectTag> type) {
        typeCheckers.put(type, new TypeComparisonRunnable() {
            @Override
            public boolean canBecome(ObjectTag inp) {
                return true;
            }
        });
    }

    public static boolean canPossiblyBeType(ObjectTag inp, Class<? extends ObjectTag> type) {
        if (inp.getObjectTagClass() == type) {
            return true;
        }
        TypeComparisonRunnable comp = typeCheckers.get(type);
        if (comp != null && !comp.canBecome(inp)) {
            return false;
        }
        return ObjectFetcher.checkMatch(type, inp.toString());
    }

    public static void deleteDirectory(File directory) throws IOException {
        Files.walkFileTree(directory.toPath(),
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }
                });
    }

    public static void copyDirectory(File source, File destination) throws IOException {
        copyDirectory(source.toPath(), destination.toPath());
    }

    public static void copyDirectory(Path source, Path destination) throws IOException {
        Files.walk(source).forEach(file -> {
            try {
                Files.copy(file, destination.resolve(source.relativize(file)));
            }
            catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    static Random random = new Random();

    public static Random getRandom() {
        return random;
    }

    static FilenameFilter scriptsFilter;

    static {
        scriptsFilter = new FilenameFilter() {
            public boolean accept(File file, String fileName) {
                if (fileName.startsWith(".")) {
                    return false;
                }

                String ext = fileName.substring(fileName.lastIndexOf('.') + 1);
                if (ext.equalsIgnoreCase("DSCRIPT")) {
                    Debug.echoError("'.dscript' extension has never been officially supported. Please use '.dsc'. Regarding file " + fileName);
                    return true;
                }
                return ext.equalsIgnoreCase("YML") || ext.equalsIgnoreCase("DSC");
            }
        };
    }

    public static String bigDecToString(BigDecimal input) {
        String temp = input.toString();
        if (temp.contains(".")) {
            for (int i = temp.length() - 1; i >= 0; i--) {
                if (temp.charAt(i) != '0') {
                    if (temp.charAt(i) == '.') {
                        return temp.substring(0, i);
                    }
                    return temp.substring(0, i + 1);
                }
            }
        }
        return temp;
    }

    public static String doubleToString(double input) {
        String temp = String.valueOf(input);
        if (temp.endsWith(".0")) {
            return temp.substring(0, temp.length() - 2);
        }
        return temp;
    }

    /**
     * Lists all files in the given directory.
     *
     * @param dir The directory to search in
     * @return A {@link java.io.File} collection
     */
    public static List<File> listDScriptFiles(File dir) {
        List<File> files = new ArrayList<>();
        File[] entries = dir.listFiles();

        for (File file : entries) {
            // Add file
            if (scriptsFilter == null || scriptsFilter.accept(dir, file.getName())) {
                files.add(file);
            }

            // Add subdirectories
            if (file.isDirectory()) {
                files.addAll(listDScriptFiles(file));
            }
        }
        return files;
    }

    public static boolean contains(String str, char c) {
        return str.indexOf(c) >= 0;
    }

    public static String concat(List<String> str, String split) {
        StringBuilder sb = new StringBuilder();
        if (str.size() > 0) {
            sb.append(str.get(0));
        }
        for (int i = 1; i < str.size(); i++) {
            sb.append(split).append(str.get(i));
        }
        return sb.toString();
    }

    public static List<String> split(String str, char c) {
        List<String> strings = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == c) {
                strings.add(str.substring(start, i));
                start = i + 1;
            }
        }
        strings.add(str.substring(start));
        return strings;
    }

    public static List<String> split(String str, char c, int max) {
        List<String> strings = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == c) {
                strings.add(str.substring(start, i));
                start = i + 1;
                if (strings.size() + 1 == max) {
                    break;
                }
            }
        }
        strings.add(str.substring(start));
        if (Debug.verbose) {
            Debug.log("Splitting " + str + " around " + c + " limited to " + max + " returns " + concat(strings, ":::"));
        }
        return strings;
    }

    public static String toLowerCase(String input) {
        char[] data = input.toCharArray();
        for (int i = 0; i < data.length; i++) {
            if (data[i] >= 'A' && data[i] <= 'Z') {
                data[i] -= 'A' - 'a';
            }
        }
        return new String(data);
    }

    public static String getXthArg(int argc, String args) {
        char[] data = args.toCharArray();
        StringBuilder nArg = new StringBuilder();
        int arg = 0;
        int x = 0;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == ' ') {
                arg++;
                if (arg > argc) {
                    return nArg.toString();
                }
            }
            else if (arg == argc) {
                nArg.append(data[i]);
            }
        }
        return nArg.toString();
    }

    public static boolean xthArgEquals(int argc, String args, String input) {
        char[] data = args.toCharArray();
        char[] data2 = input.toCharArray();
        int arg = 0;
        int x = 0;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == ' ') {
                arg++;
            }
            else if (arg == argc) {
                if (x == data2.length) {
                    return false;
                }
                if (data2[x++] != data[i]) {
                    return false;
                }
            }
        }
        return x == data2.length;
    }

    public static String getClosestOption(List<String> strs, String opt) {
        int minDist = Integer.MAX_VALUE;
        opt = CoreUtilities.toLowerCase(opt);
        String closest = "";
        for (String cmd : strs) {
            String comp = CoreUtilities.toLowerCase(cmd);
            int distance = getLevenshteinDistance(opt, comp);
            if (minDist > distance) {
                minDist = distance;
                closest = cmd;
            }
        }

        return closest;
    }

    public static int getLevenshteinDistance(String s, String t) {
        if (s == null || t == null) {
            throw new IllegalArgumentException("Strings must not be null");
        }

        int n = s.length(); // length of s
        int m = t.length(); // length of t

        if (n == 0) {
            return m;
        }
        else if (m == 0) {
            return n;
        }

        int[] p = new int[n + 1]; // 'previous' cost array, horizontally
        int[] d = new int[n + 1]; // cost array, horizontally
        int[] _d; // placeholder to assist in swapping p and d

        // indexes into strings s and t
        int i; // iterates through s
        int j; // iterates through t

        char t_j; // jth character of t

        int cost; // cost

        for (i = 0; i <= n; i++) {
            p[i] = i;
        }

        for (j = 1; j <= m; j++) {
            t_j = t.charAt(j - 1);
            d[0] = j;

            for (i = 1; i <= n; i++) {
                cost = s.charAt(i - 1) == t_j ? 0 : 1;
                // minimum of cell to the left+1, to the top+1, diagonally left
                // and up +cost
                d[i] = Math.min(Math.min(d[i - 1] + 1, p[i] + 1), p[i - 1] + cost);
            }

            // copy current distance counts to 'previous row' distance counts
            _d = p;
            p = d;
            d = _d;
        }

        // our last action in the above loop was to switch d and p, so p now
        // actually has the most recent cost counts
        return p[n];
    }
}
