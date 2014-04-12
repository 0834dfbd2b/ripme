package com.rarchives.ripme.utils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.rarchives.ripme.ripper.rippers.ImgurRipper;
import com.rarchives.ripme.ripper.rippers.ImgurRipper.ImgurAlbum;
import com.rarchives.ripme.ripper.rippers.ImgurRipper.ImgurImage;

public class RipUtils {
    private static final Logger logger = Logger.getLogger(RipUtils.class);

    public static List<URL> getFilesFromURL(URL url) {
        List<URL> result = new ArrayList<URL>();

        // Imgur album
        if ((url.getHost().equals("m.imgur.com") || url.getHost().equals("imgur.com")) 
                && url.toExternalForm().contains("imgur.com/a/")) {
            try {
                ImgurAlbum imgurAlbum = ImgurRipper.getImgurAlbum(url);
                for (ImgurImage imgurImage : imgurAlbum.images) {
                    result.add(imgurImage.url);
                }
                return result;
            } catch (IOException e) {
                logger.error("[!] Exception while loading album " + url, e);
            }
           
        }

        // Direct link to image
        Pattern p = Pattern.compile("(https?://[a-zA-Z0-9\\-\\.]+\\.[a-zA-Z]{2,3}(/\\S*)\\.(jpg|jpeg|gif|png|mp4))");
        Matcher m = p.matcher(url.toExternalForm());
        if (m.matches()) {
            try {
                URL singleURL = new URL(m.group(1));
                result.add(singleURL);
                return result;
            } catch (MalformedURLException e) {
                logger.error("[!] Not a valid URL: '" + url + "'", e);
            }
        }
        
        if(url.getHost().equals("imgur.com") || 
                url.getHost().equals("m.imgur.com")){
            try {
                result.add(new URL(url.toExternalForm() + ".png"));
                return result;
            } catch (MalformedURLException ex) {
                logger.error("[!] Exception while loading album " + url, ex);
            }
            
        }
        
        logger.error("[!] Unable to rip URL: " + url);
        return result;
    }
    
    public static Pattern getURLRegex() {
        return Pattern.compile("(https?://[a-zA-Z0-9\\-\\.]+\\.[a-zA-Z]{2,3}(/\\S*))");
    }
}
