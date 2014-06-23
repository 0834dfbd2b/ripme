package com.rarchives.ripme.ripper.rippers;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Connection.Method;
import org.jsoup.Connection.Response;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.rarchives.ripme.ripper.AbstractHTMLRipper;
import com.rarchives.ripme.utils.Base64;
import com.rarchives.ripme.utils.Http;
import com.rarchives.ripme.utils.Utils;

public class DeviantartRipper extends AbstractHTMLRipper {

    private static final int SLEEP_TIME = 2000;

    private Map<String,String> cookies = new HashMap<String,String>();
    private Set<String> triedURLs = new HashSet<String>();

    public DeviantartRipper(URL url) throws IOException {
        super(url);
    }

    @Override
    public String getHost() {
        return "deviantart";
    }
    @Override
    public String getDomain() {
        return "deviantart.com";
    }

    @Override
    public URL sanitizeURL(URL url) throws MalformedURLException {
        String u = url.toExternalForm();
        String subdir = "/";
        if (u.contains("catpath=scraps")) {
            subdir = "scraps";
        }
        u = u.replaceAll("\\?.*", "?catpath=" + subdir);
        return new URL(u);
    }

    @Override
    public String getGID(URL url) throws MalformedURLException {
        Pattern p = Pattern.compile("^https?://([a-zA-Z0-9\\-]+)\\.deviantart\\.com(/gallery)?/?(\\?.*)?$");
        Matcher m = p.matcher(url.toExternalForm());
        if (m.matches()) {
            // Root gallery
            if (url.toExternalForm().contains("catpath=scraps")) {
                return m.group(1) + "_scraps";
            }
            else {
                return m.group(1);
            }
        }
        p = Pattern.compile("^https?://([a-zA-Z0-9\\-]{1,})\\.deviantart\\.com/gallery/([0-9]{1,}).*$");
        m = p.matcher(url.toExternalForm());
        if (m.matches()) {
            // Subgallery
            return m.group(1) + "_" + m.group(2);
        }
        throw new MalformedURLException("Expected URL format: http://username.deviantart.com/[/gallery/#####], got: " + url);
    }

    @Override
    public Document getFirstPage() throws IOException {
        // Login
        try {
            cookies = loginToDeviantart();
        } catch (Exception e) {
            logger.warn("Failed to login: ", e);
        }
        return Http.url(this.url)
                   .cookies(cookies)
                   .get();
    }

    @Override
    public List<String> getURLsFromPage(Document page) {
        List<String> imageURLs = new ArrayList<String>();

        // Iterate over all thumbnails
        for (Element thumb : page.select("div.zones-container a.thumb")) {
            if (isStopped()) {
                break;
            }
            Element img = thumb.select("img").get(0);
            if (img.attr("transparent").equals("false")) {
                continue; // a.thumbs to other albums are invisible
            }

            // Get full-sized image via helper methods
            String fullSize = null;
            try {
                fullSize = thumbToFull(img.attr("src"), true);
            } catch (Exception e) {
                logger.info("Attempting to get full size image from " + thumb.attr("href"));
                fullSize = smallToFull(img.attr("src"), thumb.attr("href"));
            }

            if (fullSize == null) {
                continue;
            }
            if (triedURLs.contains(fullSize)) {
                logger.warn("Already tried to download " + fullSize);
                continue;
            }
            triedURLs.add(fullSize);
            imageURLs.add(fullSize);
        }
        return imageURLs;
    }
    
    @Override
    public Document getNextPage(Document page) throws IOException {
        Elements nextButtons = page.select("li.next > a");
        if (nextButtons.size() == 0) {
            throw new IOException("No next page found");
        }
        Element a = nextButtons.first();
        if (a.hasClass("disabled")) {
            throw new IOException("Hit end of pages");
        }
        String nextPage = a.attr("href");
        if (nextPage.startsWith("/")) {
            nextPage = "http://" + this.url.getHost() + nextPage;
        }
        if (!sleep(SLEEP_TIME)) {
            throw new IOException("Interrupted while waiting to load next page: " + nextPage);
        }
        logger.info("Found next page: " + nextPage);
        return Http.url(nextPage)
                   .cookies(cookies)
                   .get();
    }

    @Override
    public void downloadURL(URL url, int index) {
        addURLToDownload(url, getPrefix(index), "", this.url.toExternalForm(), cookies);
    }

    /**
     * Tries to get full size image from thumbnail URL
     * @param thumb Thumbnail URL
     * @param throwException Whether or not to throw exception when full size image isn't found
     * @return Full-size image URL
     * @throws Exception If it can't find the full-size URL
     */
    public static String thumbToFull(String thumb, boolean throwException) throws Exception {
        thumb = thumb.replace("http://th", "http://fc");
        List<String> fields = new ArrayList<String>(Arrays.asList(thumb.split("/")));
        fields.remove(4);
        if (!fields.get(4).equals("f") && throwException) {
            // Not a full-size image
            throw new Exception("Can't get full size image from " + thumb);
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                result.append("/");
            }
            result.append(fields.get(i));
        }
        return result.toString();
    }

    /**
     * If largest resolution for image at 'thumb' is found, starts downloading
     * and returns null.
     * If it finds a larger resolution on another page, returns the image URL.
     * @param thumb Thumbnail URL
     * @param page Page the thumbnail is retrieved from
     * @return Highest-resolution version of the image based on thumbnail URL and the page.
     */
    public String smallToFull(String thumb, String page) {
        try {
            // Fetch the image page
            Response resp = Http.url(page)
                                .referrer(this.url)
                                .cookies(cookies)
                                .response();
            cookies.putAll(resp.cookies());

            // Try to find the "Download" box
            Elements els = resp.parse().select("a.dev-page-download");
            if (els.size() == 0) {
                throw new IOException("No download page found");
            }
            // Full-size image
            String fsimage = els.get(0).attr("href");
            return fsimage;
        } catch (IOException ioe) {
            try {
                logger.info("Failed to get full size download image at " + page + " : '" + ioe.getMessage() + "'");
                String lessThanFull = thumbToFull(thumb, false);
                logger.info("Falling back to less-than-full-size image " + lessThanFull);
                return lessThanFull;
            } catch (Exception e) {
                return null;
            }
        }
    }

    /**
     * Logs into deviant art. Required to rip full-size NSFW content.
     * @return Map of cookies containing session data.
     */
    private Map<String, String> loginToDeviantart() throws IOException {
        // Populate postData fields
        Map<String,String> postData = new HashMap<String,String>();
        String username = Utils.getConfigString("deviantart.username", new String(Base64.decode("Z3JhYnB5")));
        String password = Utils.getConfigString("deviantart.password", new String(Base64.decode("ZmFrZXJz")));
        if (username == null || password == null) {
            throw new IOException("could not find username or password in config");
        }
        Response resp = Http.url("http://www.deviantart.com/")
                            .response();
        for (Element input : resp.parse().select("form#form-login input[type=hidden]")) {
            postData.put(input.attr("name"), input.attr("value"));
        }
        postData.put("username", username);
        postData.put("password", password);
        postData.put("remember_me", "1");

        // Send login request
        resp = Http.url("https://www.deviantart.com/users/login")
                    .userAgent(USER_AGENT)
                    .data(postData)
                    .cookies(resp.cookies())
                    .method(Method.POST)
                    .response();

        // Assert we are logged in
        if (resp.hasHeader("Location") && resp.header("Location").contains("password")) {
            // Wrong password
            throw new IOException("Wrong password");
        }
        if (resp.url().toExternalForm().contains("bad_form")) {
            throw new IOException("Login form was incorrectly submitted");
        }
        if (resp.cookie("auth_secure") == null ||
            resp.cookie("auth") == null) {
            throw new IOException("No auth_secure or auth cookies received");
        }
        // We are logged in, save the cookies
        return resp.cookies();
    }
}
