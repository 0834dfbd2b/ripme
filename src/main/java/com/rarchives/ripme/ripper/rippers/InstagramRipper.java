package com.rarchives.ripme.ripper.rippers;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.rarchives.ripme.ripper.AbstractRipper;

public class InstagramRipper extends AbstractRipper {

    private static final String DOMAIN = "instagram.com",
                                HOST   = "instagram";
    private static final Logger logger = Logger.getLogger(ImagearnRipper.class);

    public InstagramRipper(URL url) throws IOException {
        super(url);
    }

    @Override
    public boolean canRip(URL url) {
        return url.getHost().endsWith(DOMAIN);
    }

    @Override
    public URL sanitizeURL(URL url) throws MalformedURLException {
        Pattern p = Pattern.compile("^https?://instagram\\.com/p/([a-zA-Z0-9]{1,}).*$");
        Matcher m = p.matcher(url.toExternalForm());
        if (m.matches()) {
            // Link to photo, not the user account
            try {
                url = getUserPageFromImage(url);
            } catch (Exception e) {
                logger.error("[!] Failed to get user page from " + url, e);
                throw new MalformedURLException("Failed to retrieve user page from " + url);
            }
        }
        p = Pattern.compile("^.*instagram.com/([a-zA-Z0-9]{3,}).*$");
        m = p.matcher(url.toExternalForm());
        if (!m.matches()) {
            throw new MalformedURLException("Expected username in URL (instagram.com/username and not " + url);
        }
        return new URL("http://statigr.am/" + m.group(1));
    }
    
    private URL getUserPageFromImage(URL url) throws IOException {
        Document doc = Jsoup.connect(url.toExternalForm()).get();
        for (Element element : doc.select("meta[property='og:description']")) {
            String content = element.attr("content");
            if (content.endsWith("'s photo on Instagram")) {
                return new URL("http://statigr.am/" + content.substring(0, content.indexOf("'")));
            }
        }
        throw new MalformedURLException("Expected username in URL (instagram.com/username and not " + url);
    }
    
    private String getUserID(URL url) throws IOException {
        logger.info("   Retrieving " + url);
        Document doc = Jsoup.connect(this.url.toExternalForm()).get();
        for (Element element : doc.select("input[id=user_public]")) {
            return element.attr("value");
        }
        throw new IOException("Unable to find userID at " + this.url);
    }

    @Override
    public void rip() throws IOException {
        int index = 0;
        String userID = getUserID(this.url);
        String baseURL = "http://statigr.am/controller_nl.php?action=getPhotoUserPublic&user_id=" + userID;
        String params = "";
        while (true) {
            String url = baseURL + params;
            logger.info("    Retrieving " + url);
            String jsonString = Jsoup.connect(url)
                                     .ignoreContentType(true)
                                     .execute()
                                     .body();
            JSONObject json = new JSONObject(jsonString);
            JSONArray datas = json.getJSONArray("data");
            String nextMaxID = "";
            if (datas.length() == 0) {
                break;
            }
            for (int i = 0; i < datas.length(); i++) {
                JSONObject data = (JSONObject) datas.get(i);
                if (data.has("id")) {
                    nextMaxID = data.getString("id");
                }
                if (data.has("videos")) {
                    index += 1;
                    String video = data.getJSONObject("videos").getJSONObject("standard_resolution").getString("url");
                    addURLToDownload(new URL(video), String.format("%03d_", index));
                } else if (data.has("images")) {
                    index += 1;
                    String image = data.getJSONObject("images").getJSONObject("standard_resolution").getString("url");
                    // addURLToDownload(new URL(image), String.format("%03d_", index));
                    addURLToDownload(new URL(image));
                }
            }
            JSONObject pagination = json.getJSONObject("pagination");
            if (nextMaxID.equals("")) {
                if (!pagination.has("next_max_id")) {
                    break;
                } else {
                    nextMaxID = pagination.getString("next_max_id");
                }
            }
            params = "&max_id=" + nextMaxID;
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                logger.error("[!] Interrupted while waiting to load next album:", e);
                break;
            }
        }
        waitForThreads();
    }

    @Override
    public String getHost() {
        return HOST;
    }

    @Override
    public String getGID(URL url) throws MalformedURLException {
        Pattern p = Pattern.compile("^https?://statigr.am/([a-zA-Z0-9]{3,}).*$");
        Matcher m = p.matcher(url.toExternalForm());
        if (m.matches()) {
            return m.group(1);
        }
        throw new MalformedURLException("Unable to find user in " + url);
    }

}
