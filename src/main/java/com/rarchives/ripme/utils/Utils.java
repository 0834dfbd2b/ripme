package com.rarchives.ripme.utils;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.log4j.Logger;

/**
 * Common utility functions used in various places throughout the project.
 */
public class Utils {

    public  static final String RIP_DIRECTORY = "rips";
    private static final String configFile = "rip.properties";
    private static final Logger logger = Logger.getLogger(Utils.class);

    private static PropertiesConfiguration config;
    static {
        try {
            String configPath = getConfigPath();
            File f = new File(configPath);
            if (!f.exists()) {
                // Use default bundled with .jar
                configPath = configFile;
            }
            config = new PropertiesConfiguration(configPath);
            config.setReloadingStrategy(new FileChangedReloadingStrategy());
            logger.info("Loaded " + config.getPath());
        } catch (Exception e) {
            logger.error("[!] Failed to load properties file from " + configFile, e);
        }
    }

    /**
     * Get the root rips directory.
     * @return
     *      Root directory to save rips to.
     * @throws IOException
     */
    public static File getWorkingDirectory() {
        String currentDir = ".";
        try {
            currentDir = new File(".").getCanonicalPath() + File.separator + RIP_DIRECTORY + File.separator;
        } catch (IOException e) {
            logger.error("Error while finding working dir: ", e);
        }
        if (config != null) {
            currentDir = getConfigString("rips.directory", currentDir);
        }
        File workingDir = new File(currentDir);
        if (!workingDir.exists()) {
            workingDir.mkdirs();
        }
        return workingDir;
    }

    public static String getConfigString(String key, String defaultValue) {
        return config.getString(key, defaultValue);
    }
    public static int getConfigInteger(String key, int defaultValue) {
        return config.getInt(key, defaultValue);
    }
    public static boolean getConfigBoolean(String key, boolean defaultValue) {
        return config.getBoolean(key, defaultValue);
    }
    public static List<String> getConfigList(String key) {
        List<String> result = new ArrayList<String>();
        for (Object obj : config.getList(key, new ArrayList<String>())) {
            if (obj instanceof String) {
                result.add( (String) obj);
            }
        }
        return result;
    }
    public static void setConfigBoolean(String key, boolean value)  { config.setProperty(key, value); }
    public static void setConfigString(String key, String value)    { config.setProperty(key, value); }
    public static void setConfigInteger(String key, int value)      { config.setProperty(key, value); }
    public static void setConfigList(String key, List<Object> list) {
        config.clearProperty(key);
        config.addProperty(key, list);
    }

    public static void saveConfig() {
        try {
            config.save(getConfigPath());
            logger.info("Saved configuration to " + getConfigPath());
        } catch (ConfigurationException e) {
            logger.error("Error while saving configuration: ", e);
        }
    }
    private static String getConfigPath() {
        try {
            return new File(".").getCanonicalPath() + File.separator + configFile;
        } catch (Exception e) {
            return "." + File.separator + configFile;
        }
    }

    /**
     * Removes the current working directory (CWD) from a File.
     * @param saveAs
     *      The File path
     * @return
     *      saveAs in relation to the CWD
     */
    public static String removeCWD(File saveAs) {
        String prettySaveAs = saveAs.toString(); 
        try {
            prettySaveAs = saveAs.getCanonicalPath();
            String cwd = new File(".").getCanonicalPath() + File.separator;
            prettySaveAs = prettySaveAs.replace(
                    cwd,
                    "." + File.separator);
        } catch (Exception e) {
            logger.error("Exception: ", e);
        }
        return prettySaveAs;
    }
    
    public static String stripURLParameter(String url, String parameter) {
        int paramIndex = url.indexOf("?" + parameter);
        boolean wasFirstParam = true;
        if(paramIndex < 0) {
            wasFirstParam = false;
            paramIndex = url.indexOf("&" + parameter);
        }
        
        if(paramIndex > 0) {
            int nextParam = url.indexOf("&", paramIndex+1);
            if(nextParam != -1) {
                String c = "&";
                if(wasFirstParam) c = "?";
                url = url.substring(0, paramIndex) + c + url.substring(nextParam+1, url.length());
            } else {
                url = url.substring(0, paramIndex);
            }
        }
        
        return url;
    }

    /**
     * Removes the current working directory from a given filename
     * @param file
     * @return
     *      'file' without the leading current working directory
     */
    public static String removeCWD(String file) {
        return removeCWD(new File(file));
    }

    /**
     * Get a list of all Classes within a package.
     * Works with file system projects and jar files!
     * Borrowed from StackOverflow, but I don't have a link :[
     * @param pkgname
     *      The name of the package
     * @return
     *      List of classes within the package
     */
    public static ArrayList<Class<?>> getClassesForPackage(String pkgname) {
        ArrayList<Class<?>> classes = new ArrayList<Class<?>>();
        String relPath = pkgname.replace('.', '/');
        URL resource = ClassLoader.getSystemClassLoader().getResource(relPath);
        if (resource == null) {
            throw new RuntimeException("No resource for " + relPath);
        }

        String fullPath = resource.getFile();
        File directory = null;
        try {
            directory = new File(resource.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(pkgname + " (" + resource + ") does not appear to be a valid URL / URI.  Strange, since we got it from the system...", e);
        } catch (IllegalArgumentException e) {
            directory = null;
        }

        if (directory != null && directory.exists()) {
            // Get the list of the files contained in the package
            String[] files = directory.list();
            for (String file : files) {
                if (file.endsWith(".class") && !file.contains("$")) {
                    String className = pkgname + '.' + file.substring(0, file.length() - 6);
                    try {
                        classes.add(Class.forName(className));
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException("ClassNotFoundException loading " + className);
                    }
                }
            }
        }
        else {
            try {
                logger.debug("fullPath = " + fullPath);
                String jarPath = fullPath
                        .replaceFirst("[.]jar[!].*", ".jar")
                        .replaceFirst("file:", "")
                        .replaceAll("%20", " ");
                logger.debug("jarPath = " + jarPath);
                JarFile jarFile = new JarFile(jarPath);
                Enumeration<JarEntry> entries = jarFile.entries();
                while(entries.hasMoreElements()) {
                    String entryName = entries.nextElement().getName();
                    if(entryName.startsWith(relPath)
                            && entryName.length() > (relPath.length() + "/".length())) {
                        String className = entryName.replace('/', '.').replace('\\', '.').replace(".class", "");
                        try {
                            classes.add(Class.forName(className));
                        } catch (ClassNotFoundException e) {
                            throw new RuntimeException("ClassNotFoundException loading " + className);
                        }
                    }
                }
            } catch (IOException e) {
                logger.error("Error while loading jar file:", e);
                throw new RuntimeException(pkgname + " (" + directory + ") does not appear to be a valid package", e);
            }
        }
        return classes;
    }
    
    public static final int SHORTENED_PATH_LENGTH = 12;
    public static String shortenPath(String path) {
        return shortenPath(new File(path));
    }
    public static String shortenPath(File file) {
        String path = removeCWD(file);
        if (path.length() < SHORTENED_PATH_LENGTH * 2) {
            return path;
        }
        return path.substring(0, SHORTENED_PATH_LENGTH)
                + "..."
                + path.substring(path.length() - SHORTENED_PATH_LENGTH);
    }
}