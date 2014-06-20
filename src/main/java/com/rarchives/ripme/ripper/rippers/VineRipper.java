package com.rarchives.ripme.ripper.rippers;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.HttpStatusException;
import org.jsoup.nodes.Document;

import com.rarchives.ripme.ripper.AlbumRipper;
import com.rarchives.ripme.ui.RipStatusMessage.STATUS;

public class VineRipper extends AlbumRipper {

    private static final String DOMAIN = "vine.co",
                                HOST   = "vine";

    public VineRipper(URL url) throws IOException {
        super(url);
    }

    @Override
    public boolean canRip(URL url) {
        return url.getHost().endsWith(DOMAIN);
    }

    @Override
    public URL sanitizeURL(URL url) throws MalformedURLException {
        return new URL("http://vine.co/u/" + getGID(url));
    }

    @Override
    public void rip() throws IOException {
        int page = 0;
        String baseURL = "https://vine.co/api/timelines/users/" + getGID(this.url);
        Document doc;
        while (true) {
            page++;
            String theURL = baseURL;
            if (page > 1) {
                theURL += "?page=" + page;
            }
            try {
                logger.info("    Retrieving " + theURL);
                sendUpdate(STATUS.LOADING_RESOURCE, theURL);
                doc = getResponse(theURL, true).parse();
            } catch (HttpStatusException e) {
                logger.debug("Hit end of pages at page " + page, e);
                break;
            }
            String jsonString = doc.body().html();
            jsonString = jsonString.replace("&quot;", "\"");
            JSONObject json = new JSONObject(jsonString);
            JSONArray records = json.getJSONObject("data").getJSONArray("records");
            for (int i = 0; i < records.length(); i++) {
                String videoURL = records.getJSONObject(i).getString("videoUrl");
                addURLToDownload(new URL(videoURL));
            }
            if (records.length() == 0) {
                logger.info("Zero records returned");
                break;
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                logger.error("[!] Interrupted while waiting to load next page", e);
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
        Pattern p = Pattern.compile("^https?://(www\\.)?vine\\.co/u/([0-9]{1,}).*$");
        Matcher m = p.matcher(url.toExternalForm());
        if (!m.matches()) {
            throw new MalformedURLException("Expected format: http://vine.co/u/######");
        }
        return m.group(m.groupCount());
    }

}
