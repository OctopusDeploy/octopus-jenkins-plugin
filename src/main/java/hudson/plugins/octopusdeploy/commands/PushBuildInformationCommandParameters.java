package hudson.plugins.octopusdeploy.commands;

import hudson.plugins.octopusdeploy.OverwriteMode;
import hudson.plugins.octopusdeploy.services.OctoCliService;
import hudson.plugins.octopusdeploy.services.ScmCommitService;

public class PushBuildInformationCommandParameters extends CommandParameters {

    private final String serverId;
    private final String spaceId;
    private final String packageId;
    private final String packageVersion;
    private final String commentParser;
    private final OverwriteMode overwriteMode;
    private final String gitUrl;
    private final String gitCommit;
    private final ScmCommitService scmCommitService;

    public PushBuildInformationCommandParameters(OctoCliService octoCliService, String toolId, String serverId, String spaceId, String packageId, String packageVersion, String commentParser, OverwriteMode overwriteMode, String gitUrl, String gitCommit, boolean verboseLogging, String additionalArgs, ScmCommitService scmCommitService) {
        super(octoCliService, toolId, verboseLogging, additionalArgs);
        this.serverId = serverId;
        this.spaceId = spaceId;
        this.packageId = packageId;
        this.packageVersion = packageVersion;
        this.commentParser = commentParser;
        this.overwriteMode = overwriteMode;
        this.gitUrl = gitUrl;
        this.gitCommit = gitCommit;
        this.scmCommitService = scmCommitService;
    }

    public String getServerId() {
        return serverId;
    }

    public String getPackageId() {
        return packageId;
    }

    public String getPackageVersion() {
        return packageVersion;
    }

    public String getCommentParser() {
        return commentParser;
    }

    public OverwriteMode getOverwriteMode() {
        return overwriteMode;
    }

    public String getGitUrl() {
        return gitUrl;
    }

    public String getGitCommit() {
        return gitCommit;
    }

    public String getSpaceId() {
        return spaceId;
    }

    public ScmCommitService getScmCommitService() {
        return scmCommitService;
    }
}
