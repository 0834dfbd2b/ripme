package com.rarchives.ripme.ripper.rippers;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.rarchives.ripme.ripper.AbstractRipper;
import com.rarchives.ripme.ui.RipStatusMessage.STATUS;

public class ImgurRipper extends AbstractRipper {

    private static final String DOMAIN = "imgur.com",
                                HOST   = "imgur";
    private static final Logger logger = Logger.getLogger(ImgurRipper.class);

    private final int SLEEP_BETWEEN_ALBUMS;

    static enum ALBUM_TYPE {
        ALBUM,
        USER,
        USER_ALBUM,
        SERIES_OF_IMAGES
    };
    private ALBUM_TYPE albumType;

    public ImgurRipper(URL url) throws IOException {
        super(url);
        SLEEP_BETWEEN_ALBUMS = 1;
    }

    public void processURL(URL url, String prefix, String subdirectory) {
       logger.debug("Found URL: " + url);
       addURLToDownload(url, prefix, subdirectory);
    }

    public boolean canRip(URL url) {
        if (!url.getHost().endsWith(DOMAIN)) {
           return false;
        }
        try {
            getGID(url);
        } catch (Exception e) {
            // Can't get GID, can't rip it.
            return false;
        }
        return true;
    }

    public URL sanitizeURL(URL url) throws MalformedURLException {
        String u = url.toExternalForm();
        if (u.indexOf('#') >= 0) {
            u = u.substring(0,  u.indexOf('#'));
        }
        u = u.replace("https?://m\\.imgur\\.com", "http://imgur.com");
        u = u.replace("https?://i\\.imgur\\.com", "http://imgur.com");
        return new URL(u);
    }

    @Override
    public void rip() throws IOException {
        switch (albumType) {
        case ALBUM:
            // Fall-through
        case USER_ALBUM:
            ripAlbum(this.url);
            break;

        case SERIES_OF_IMAGES:
            ripAlbum(this.url);
            break;

        case USER:
            // TODO Get all albums by user
            ripUserAccount(url);
            break;
        }
        waitForThreads();
    }

    private void ripAlbum(URL url) throws IOException {
        ripAlbum(url, "");
    }

    private void ripAlbum(URL url, String subdirectory) throws IOException {
        int index = 0;
        logger.info("    Retrieving " + url.toExternalForm());
        this.sendUpdate(STATUS.LOADING_RESOURCE, url.toExternalForm());
        Document doc = Jsoup.connect(url.toExternalForm()).get();

        // Try to use embedded JSON to retrieve images
        Pattern p = Pattern.compile("^.*Imgur\\.Album\\.getInstance\\((.*)\\);.*$", Pattern.DOTALL);
        Matcher m = p.matcher(doc.body().html());
        if (m.matches()) {
            try {
                JSONObject json = new JSONObject(m.group(1));
                JSONArray images = json.getJSONObject("images").getJSONArray("items");
                int imagesLength = images.length();
                for (int i = 0; i < imagesLength; i++) {
                    JSONObject image = images.getJSONObject(i);
                    URL imageURL = new URL(
                            // CDN url is provided elsewhere in the document
                            "http://i.imgur.com/"
                                    + image.get("hash")
                                    + image.get("ext"));
                    index += 1;
                    processURL(imageURL, String.format("%03d_", index), subdirectory);
                }
                return;
            } catch (JSONException e) {
                logger.debug("Error while parsing JSON at " + url + ", continuing", e);
            }
        }
        p = Pattern.compile("^.*= new ImgurShare\\((.*)\\);.*$", Pattern.DOTALL);
        m = p.matcher(doc.body().html());
        if (m.matches()) {
            try {
                JSONObject json = new JSONObject(m.group(1));
                JSONArray images = json.getJSONArray("hashes");
                int imagesLength = images.length();
                for (int i = 0; i < imagesLength; i++) {
                    JSONObject image = images.getJSONObject(i);
                    URL imageURL = new URL(
                            "http:" + json.get("cdnUrl")
                                    + "/"
                                    + image.get("hash")
                                    + image.get("ext"));
                    index += 1;
                    processURL(imageURL, String.format("%03d_", index), subdirectory);
                }
                return;
            } catch (JSONException e) {
                logger.debug("Error while parsing JSON at " + url + ", continuing", e);
            }
        }

        // TODO If album is empty, use this to check for cached images:
        // http://i.rarchives.com/search.cgi?cache=http://imgur.com/a/albumID
        // At the least, get the thumbnails.
        logger.info("[!] Falling back to elemental retrieval method");

        // Fall back to parsing HTML elements
        // NOTE: This does not always get the highest-resolution images!
        for (Element thumb : doc.select("div.image")) {
            String image;
            if (thumb.select("a.zoom").size() > 0) {
                // Clickably full-size
                image = "http:" + thumb.select("a").attr("href");
            } else if (thumb.select("img").size() > 0) {
                image = "http:" + thumb.select("img").attr("src");
            } else {
                // Unable to find image in this div
                logger.error("[!] Unable to find image in div: " + thumb.toString());
                continue;
            }
            index += 1;
            processURL(new URL(image), String.format("%03d_", index), subdirectory);
        }
    }
    
    /**
     * Rips all albums in an imgur user's account.
     * @param url
     *      URL to imgur user account (http://username.imgur.com)
     * @throws IOException
     */
    private void ripUserAccount(URL url) throws IOException {
        logger.info("[ ] Retrieving " + url.toExternalForm());
        Document doc = Jsoup.connect(url.toExternalForm()).get();
        for (Element album : doc.select("div.cover a")) {
            if (!album.hasAttr("href")
                    || !album.attr("href").contains("imgur.com/a/")) {
                continue;
            }
            String albumID = album.attr("href").substring(album.attr("href").lastIndexOf('/') + 1);
            URL albumURL = new URL("http:" + album.attr("href") + "/noscript");
            try {
                ripAlbum(albumURL, albumID);
                Thread.sleep(SLEEP_BETWEEN_ALBUMS * 1000);
            } catch (Exception e) {
                logger.error("Error while ripping album: " + e.getMessage(), e);
                continue;
            }
        }
    }

    @Override
    public String getHost() {
        return HOST;
    }

    @Override
    public String getGID(URL url) throws MalformedURLException {
        Pattern p = Pattern.compile("^https?://(m\\.)?imgur\\.com/a/([a-zA-Z0-9]{5,8}).*$");
        Matcher m = p.matcher(url.toExternalForm());
        if (m.matches()) {
            // Imgur album
            albumType = ALBUM_TYPE.ALBUM;
            String gid = m.group(m.groupCount());
            this.url = new URL("http://imgur.com/a/" + gid);
            return gid;
        }
        p = Pattern.compile("^https?://([a-zA-Z0-9\\-]{3,})\\.imgur\\.com/?$");
        m = p.matcher(url.toExternalForm());
        if (m.matches()) {
            // Root imgur account
            String gid = m.group(1);
            if (gid.equals("i")) {
                throw new MalformedURLException("Ripping i.imgur.com links not supported");
            }
            albumType = ALBUM_TYPE.USER;
            return gid;
        }
        p = Pattern.compile("^https?://([a-zA-Z0-9\\-]{3,})\\.imgur\\.com/([a-zA-Z0-9])?$");
        m = p.matcher(url.toExternalForm());
        if (m.matches()) {
            // Imgur account album
            albumType = ALBUM_TYPE.USER_ALBUM;
            return m.group();
        }
        p = Pattern.compile("^https?://(i\\.)?imgur\\.com/([a-zA-Z0-9,]{5,}).*$");
        m = p.matcher(url.toExternalForm());
        if (m.matches()) {
            // Series of imgur images
            albumType = ALBUM_TYPE.SERIES_OF_IMAGES;
            String gid = m.group(m.groupCount());
            if (!gid.contains(",")) {
                throw new MalformedURLException("Imgur image doesn't contain commas");
            }
            return gid.replaceAll(",", "-");
        }
        throw new MalformedURLException("Unexpected URL format: " + url.toExternalForm());
    }

    public ALBUM_TYPE getAlbumType() {
        return albumType;
    }
}
