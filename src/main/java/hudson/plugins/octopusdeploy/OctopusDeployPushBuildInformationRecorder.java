package hudson.plugins.octopusdeploy;

import com.google.inject.Guice;
import com.google.inject.Inject;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.plugins.octopusdeploy.commands.PushBuildInformationCommand;
import hudson.plugins.octopusdeploy.commands.PushBuildInformationCommandParameters;
import hudson.plugins.octopusdeploy.services.ScmCommitService;
import hudson.plugins.octopusdeploy.services.ServiceModule;
import hudson.util.ListBoxModel;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.io.Serializable;

import static hudson.plugins.octopusdeploy.services.StringUtil.sanitizeValue;

public class OctopusDeployPushBuildInformationRecorder extends AbstractOctopusDeployRecorderBuildStep implements Serializable {

    private transient Log log;

    private final String packageId;
    private ScmCommitService scmCommitService;

    public String getPackageId() { return packageId; }

    private final String packageVersion;
    public String getPackageVersion() { return packageVersion; }

    private final String commentParser;
    public String getCommentParser() { return commentParser; }

    private final OverwriteMode overwriteMode;
    public OverwriteMode getOverwriteMode() { return overwriteMode; }

    private String gitUrl;
    public String getGitUrl() {
        return this.gitUrl;
    }

    @DataBoundSetter
    public void setGitUrl(String gitUrl) {
        this.gitUrl = sanitizeValue(gitUrl);
    }

    private String gitCommit;
    public String getGitCommit() {
        return this.gitCommit;
    }

    @DataBoundSetter
    public void setGitCommit(String gitCommit) { this.gitCommit = sanitizeValue(gitCommit); }

    @Inject
    public void setScmCommitService(ScmCommitService scmCommitService) {
        this.scmCommitService = scmCommitService;
    }

    public ScmCommitService getScmCommitService() {
        return this.scmCommitService;
    }

    @DataBoundConstructor
    public OctopusDeployPushBuildInformationRecorder(String serverId, String spaceId, String toolId, String packageId,
                                                     String packageVersion, String commentParser, OverwriteMode overwriteMode) {
        this.serverId = sanitizeValue(serverId);
        this.spaceId = sanitizeValue(spaceId);
        this.toolId = sanitizeValue(toolId);
        this.packageId = sanitizeValue(packageId);
        this.packageVersion = sanitizeValue(packageVersion);
        this.commentParser = sanitizeValue(commentParser);
        this.overwriteMode = overwriteMode;
        this.verboseLogging = false;
        Guice.createInjector(new ServiceModule()).injectMembers(this);
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) {
        Log log = getLog(listener);
        if (CheckForFailedTask(run, log)) return;

        EnvVars envVars = getEnvVars(run, listener, log);
        if (envVars == null) return;

        ScmCommitService commitService = this.getScmCommitService();
        commitService.setRun(run);
        commitService.setLog(log);
        PushBuildInformationCommand command = new PushBuildInformationCommand(
                new PushBuildInformationCommandParameters(
                        this.getOctoCliService(),
                        this.getToolId(),
                        this.getServerId(),
                        this.getSpaceId(),
                        this.getPackageId(),
                        this.getPackageVersion(),
                        this.getCommentParser(),
                        this.getOverwriteMode(),
                        this.getGitUrl(),
                        this.getGitCommit(),
                        this.getVerboseLogging(),
                        this.getAdditionalArgs(),
                        commitService));
        Result result = command.perform(workspace, launcher, listener, envVars);
        run.setResult(result);
        return;
    }

    @Extension
    @Symbol("octopusPushBuildInformation")
    public static final class DescriptorImpl extends AbstractOctopusDeployDescriptorImplStep {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Octopus Deploy: Push build information";
        }

        public ListBoxModel doFillCommentParserItems() {
            final ListBoxModel items = new ListBoxModel();
            items.add("", "");
            items.add(CommentParser.Jira.name(), CommentParser.Jira.name());
            items.add(CommentParser.GitHub.name(), CommentParser.GitHub.name());
            return items;
        }
    }
}
