package com.octopusdeploy.api;

import com.octopusdeploy.api.data.Release;
import com.octopusdeploy.api.data.SelectedPackage;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;

public class ReleasesApi {
    private final static String UTF8 = "UTF-8";
    private final AuthenticatedWebClient webClient;

    public ReleasesApi(AuthenticatedWebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Get all releases for a given project from the Octopus server;
     * @param projectId the id of the project to get the releases for
     * @return A set of all releases for a given project
     * @throws IllegalArgumentException when the web client receives a bad parameter
     * @throws IOException When the AuthenticatedWebClient receives and error response code
     */
    public Set<Release> getReleasesForProject(String projectId) throws IllegalArgumentException, IOException {
        HashSet<Release> releases = new HashSet<Release>();
        AuthenticatedWebClient.WebResponse response = webClient.get("projects/" + projectId + "/releases");
        if (response.isErrorCode()) {
            throw new IOException(String.format("Code %s - %n%s", response.getCode(), response.getContent()));
        }
        JSONObject json = (JSONObject)JSONSerializer.toJSON(response.getContent());
        for (Object obj : json.getJSONArray("Items")) {
            JSONObject jsonObj = (JSONObject)obj;
            String id = jsonObj.getString("Id");
            String version = jsonObj.getString("Version");
            String channelId = jsonObj.getString("ChannelId");
            String ReleaseNotes = jsonObj.getString("ReleaseNotes");
            releases.add(new Release(id, projectId, channelId, ReleaseNotes, version));
        }
        return releases;
    }

    /**
     * Get the partial Octopus portal URL for a given release version of a project;
     * @param projectId the id of the project to get the releases for
     * @param releaseVersion the version of the release to get
     * @return A version of releases for a given project
     * @throws IllegalArgumentException when the web client receives a bad parameter
     * @throws IOException When the AuthenticatedWebClient receives and error response code
     */
    public String getPortalUrlForRelease(String projectId, String releaseVersion) throws IllegalArgumentException, IOException {
        AuthenticatedWebClient.WebResponse response = webClient.get("projects/" + projectId + "/releases/" + releaseVersion);
        if (response.isErrorCode()) {
            throw new IOException(String.format("Code %s - %n%s", response.getCode(), response.getContent()));
        }
        JSONObject json = (JSONObject)JSONSerializer.toJSON(response.getContent());
        JSONObject links = json.getJSONObject("Links");
        return links.getString("Web");
    }

    /**
     * Get the partial Octopus portal URL for the latest release of a project
     * @param projectId the id of the project to get the releases for
     * @return A version of releases for a given project
     * @throws IllegalArgumentException when the web client receives a bad parameter
     * @throws IOException When the AuthenticatedWebClient receives and error response code
     */
    public String getPortalUrlForLatestRelease(String projectId) throws IllegalArgumentException, IOException {
        AuthenticatedWebClient.WebResponse response = webClient.get("projects/" + projectId + "/releases");
        if (response.isErrorCode()) {
            throw new IOException(String.format("Code %s - %n%s", response.getCode(), response.getContent()));
        }
        JSONObject json = (JSONObject)JSONSerializer.toJSON(response.getContent());
        JSONArray items = json.getJSONArray("Items");
        if (items.size() != 0) {
            return items
                    .getJSONObject(0)
                    .getJSONObject("Links")
                    .getString("Web");
        }

        return null;
    }
}
