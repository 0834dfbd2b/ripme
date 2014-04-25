package com.rarchives.ripme.ripper.rippers;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import com.rarchives.ripme.ripper.AlbumRipper;
import com.rarchives.ripme.utils.Utils;

public class MinusRipper extends AlbumRipper {

    private static final String DOMAIN = "minus.com",
                                HOST   = "minus";
    private static final Logger logger = Logger.getLogger(MinusRipper.class);

    private Document albumDoc = null;
    
    private static enum ALBUM_TYPE {
        GUEST,
        ACCOUNT_ALBUM,
        ACCOUNT
    }
    private ALBUM_TYPE albumType;

    public MinusRipper(URL url) throws IOException {
        super(url);
    }

    @Override
    public String getHost() {
        return HOST;
    }

    public URL sanitizeURL(URL url) throws MalformedURLException {
        getGID(url);
        return url;
    }
    
    public String getAlbumTitle(URL url) throws MalformedURLException {
        try {
            // Attempt to use album title as GID
            if (albumDoc == null) {
                albumDoc = Jsoup.connect(url.toExternalForm())
                            .userAgent(USER_AGENT)
                            .get();
            }
            Elements titles = albumDoc.select("meta[property=og:title]");
            if (titles.size() > 0) {
                return HOST + "_" + titles.get(0).attr("content");
            }
        } catch (IOException e) {
            // Fall back to default album naming convention
        }
        return super.getAlbumTitle(url);
    }

    @Override
    public String getGID(URL url) throws MalformedURLException {
        // http://vampyr3.minus.com/
        // http://vampyr3.minus.com/uploads
        // http://minus.com/mw7ztQ6xzP7ae
        // http://vampyr3.minus.com/mw7ztQ6xzP7ae
        String u = url.toExternalForm();
        u = u.replace("www.minus.com", "minus.com");
        u = u.replace("i.minus.com", "minus.com");
        Pattern p; Matcher m;

        p = Pattern.compile("^https?://minus\\.com/m([a-zA-Z0-9]+).*$");
        m = p.matcher(u);
        if (m.matches()) {
            albumType = ALBUM_TYPE.GUEST;
            return "guest_" + m.group(1);
        }

        p = Pattern.compile("^https?://([a-zA-Z0-9\\-_]+)\\.minus\\.com/m([a-zA-Z0-9]+).*$");
        m = p.matcher(u);
        if (m.matches()) {
            albumType = ALBUM_TYPE.ACCOUNT_ALBUM;
            return m.group(1) + "_" + m.group(2);
        }

        p = Pattern.compile("^https?://([a-zA-Z0-9]+)\\.minus\\.com/?(uploads)?$");
        m = p.matcher(u);
        if (m.matches()) {
            albumType = ALBUM_TYPE.ACCOUNT;
            return m.group(1);
        }

        throw new MalformedURLException(
                "Expected minus.com album URL formats: "
                        + "username.minus.com or "
                        + "username.minus.com/m... or "
                        + "minus.com/m..."
                        + " Got: " + url);
    }

    @Override
    public void rip() throws IOException {
        switch (albumType) {
        case ACCOUNT:
            ripAccount(this.url);
            break;
        case ACCOUNT_ALBUM:
            ripAlbum(this.url);
            break;
        case GUEST:
            ripAlbum(this.url);
            break;
        }
        waitForThreads();
    }
    
    private void ripAccount(URL url) throws IOException {
        Pattern p = Pattern.compile("^https?://([a-zA-Z0-9\\-_]+)\\.minus\\.com.*$");
        Matcher m = p.matcher(url.toExternalForm());
        if (!m.matches()) {
            throw new IOException("Could not find username from URL " + url);
        }
        String user = m.group(1);
        int page = 1;
        while (true) {
            String jsonUrl = "http://" + user
                           + ".minus.com/api/pane/user/"
                           + user + "/shares.json/"
                           + page;
            logger.info("    Retrieving " + jsonUrl);
            Response resp = Jsoup.connect(jsonUrl)
                                .userAgent(USER_AGENT)
                                .ignoreContentType(true)
                                .execute();
            System.err.println(resp.body());
            JSONObject json = new JSONObject(resp.body());
            JSONArray galleries = json.getJSONArray("galleries");
            for (int i = 0; i < galleries.length(); i++) {
                JSONObject gallery = galleries.getJSONObject(i);
                String title = gallery.getString("name");
                String albumUrl = "http://" + user + ".minus.com/m" + gallery.getString("reader_id");
                ripAlbum(new URL(albumUrl), Utils.filesystemSafe(title));
            }
            if (page >= json.getInt("total_pages")) {
                break;
            }
            page++;
        }
    }
    
    private void ripAlbum(URL url) throws IOException {
        ripAlbum(url, "");
    }
    private void ripAlbum(URL url, String subdir) throws IOException {
        logger.info("    Retrieving " + url.toExternalForm());
        if (albumDoc == null || !subdir.equals("")) {
            albumDoc = Jsoup.connect(url.toExternalForm())
                            .userAgent(USER_AGENT)
                            .get();
        }
        Pattern p = Pattern.compile("^.*var gallerydata = (\\{.*\\});.*$", Pattern.DOTALL);
        Matcher m = p.matcher(albumDoc.data());
        if (m.matches()) {
            JSONObject json = new JSONObject(m.group(1));
            JSONArray items = json.getJSONArray("items");
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                String extension = item.getString("name");
                extension = extension.substring(extension.lastIndexOf('.'));
                String image = "http://i.minus.com/i"
                               + item.getString("id")
                               + extension;
                addURLToDownload(new URL(image), String.format("%03d_", i + 1), subdir);
            }
        }
    }

    public boolean canRip(URL url) {
        return url.getHost().endsWith(DOMAIN);
    }

}