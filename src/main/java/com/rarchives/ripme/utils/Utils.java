package com.rarchives.ripme.utils;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.log4j.Logger;

/**
 * Common utility functions used in various places throughout the project.
 */
public class Utils {

    public  static final String RIP_DIRECTORY = "rips";
    private static final String configFile = "rip.properties";
    private static final Logger logger = Logger.getLogger(Utils.class);

    private static Configuration config;
    static {
        try {
            config = new PropertiesConfiguration(configFile);
        } catch (ConfigurationException e) {
            logger.error("[!] Failed to load properties file from " + configFile, e);
        }
    }

    /**
     * Get the root rips directory.
     * @return
     *      Root directory to save rips to.
     * @throws IOException
     */
    public static File getWorkingDirectory() throws IOException {
        String path = new File(".").getCanonicalPath() + File.separator;
        path += RIP_DIRECTORY + File.separator;
        File workingDir = new File(path);
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

    public static void setConfigBoolean(String key, boolean value) {
        config.setProperty(key, value);
    }

    /**
     * Removes the current working directory (CWD) from a File.
     * @param saveAs
     *      The File path
     * @return
     *      saveAs in relation to the CWD
     */
    public static String removeCWD(File saveAs) {
        String prettySaveAs;
        try {
            String cwd = new File(".").getCanonicalPath() + File.separator;
            prettySaveAs = saveAs.getCanonicalPath().replace(
                    cwd,
                    "");
        } catch (Exception e) {
            prettySaveAs = saveAs.toString();
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
                String jarPath = fullPath.replaceFirst("[.]jar[!].*", ".jar").replaceFirst("file:", "");
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
    
    public static String getBuildVersion() {
        return getConfigInteger("version.major", 0)
                + "." + getConfigInteger("version.minor", 0)
                + "." + getConfigInteger("version.build", 0);
    }
}