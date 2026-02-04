package hudson.plugins.octopusdeploy;

import com.google.common.base.Splitter;
import hudson.*;
import hudson.model.*;
import hudson.plugins.octopusdeploy.cli.OctopusCliExecutor;
import hudson.plugins.octopusdeploy.cli.OctopusCliWrapperBuilder;
import hudson.plugins.octopusdeploy.constants.OctoConstants;
import hudson.plugins.octopusdeploy.exception.ServerConfigurationNotFoundException;
import hudson.plugins.octopusdeploy.services.OctopusBuildInformationBuilder;
import hudson.plugins.octopusdeploy.services.OctopusBuildInformationWriter;
import hudson.scm.ChangeLogSet;
import hudson.scm.SCM;
import hudson.util.ListBoxModel;
import hudson.util.VariableResolver;
import jenkins.util.BuildListenerAdapter;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jetbrains.annotations.NotNull;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;
import static hudson.plugins.octopusdeploy.services.StringUtil.sanitizeValue;

public class OctopusDeployPushBuildInformationRecorder extends AbstractOctopusDeployRecorderBuildStep implements Serializable {

    private transient Log log;

    private final String packageId;
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

    private String gitBranch;
    public String getGitBranch() {
        return this.gitBranch;
    }

    @DataBoundSetter
    public void setGitBranch(String gitBranch) { this.gitBranch = sanitizeValue(gitBranch); }

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
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws AbortException {
        boolean success = true;
        BuildListenerAdapter listenerAdapter = new BuildListenerAdapter(listener);

        log = new Log(listenerAdapter);
        if (Result.FAILURE.equals(run.getResult())) {
            log.info("Not pushing build information due to job being in FAILED state.");
            return;
        }

        EnvVars envVars;
        try {
            envVars = run.getEnvironment(listener);
        } catch (Exception ex) {
            log.fatal(String.format("Failed to retrieve environment variables for this build '%s' - '%s'",
                    run.getParent().getName(), getExceptionMessage(ex)));
            run.setResult(Result.FAILURE);
            return;
        }
        VariableResolver resolver =  new VariableResolver.ByMap<>(envVars);
        EnvironmentVariableValueInjector envInjector = new EnvironmentVariableValueInjector(resolver, envVars);

        //logStartHeader


        try {
            String packageIds = envInjector.injectEnvironmentVariableValues(this.packageId);
            String additionalArgs = envInjector.injectEnvironmentVariableValues(this.additionalArgs);

            // Parse package IDs
            List<String> packageIdsList = null;
            if (StringUtils.isNotBlank(packageIds)) {
                packageIdsList = Splitter.on("\n")
                        .trimResults()
                        .omitEmptyStrings()
                        .splitToList(packageIds);
            }

            // Get build information file
            final String buildInformationFile = getBuildInformationFromScm(run, envInjector, workspace);

            // Create wrapper
            OctopusCliExecutor wrapper = new OctopusCliWrapperBuilder(
                    getToolId(), workspace, launcher, envVars, listenerAdapter)
                    .serverId(serverId)
                    .spaceId(spaceId)
                    .verboseLogging(verboseLogging)
                    .build();

            // Execute push build information command
            String overwriteModeValue = (overwriteMode != OverwriteMode.FailIfExists) ? overwriteMode.name() : null;
            Result result = wrapper.pushBuildInformation(
                    packageIdsList,
                    packageVersion,
                    buildInformationFile,
                    overwriteModeValue,
                    additionalArgs
            );

            success = result.equals(Result.SUCCESS);
        } catch (Exception ex) {
            log.fatal("Failed to push the build information: " + getExceptionMessage(ex));
            success = false;
        }

        if (!success) {
            throw new AbortException("Failed to push build information");
        }
    }
    /**
     * Attempt to load release notes info from SCM.
     * @param build the jenkins build
     * @param envInjector the environment variable injector
     * @param workspace
     * @return path to build information file
     */
    private String getBuildInformationFromScm(Run<?, ?> build, EnvironmentVariableValueInjector envInjector, FilePath workspace) throws IOException, InterruptedException {
        FilePath ws = workspace;
        Job project = build.getParent();

        String gitUrl = isNullOrEmpty(this.getGitUrl()) ? envInjector.injectEnvironmentVariableValues("${GIT_URL}") : envInjector.injectEnvironmentVariableValues(this.getGitUrl());
        String gitCommit = isNullOrEmpty(this.getGitCommit())?  envInjector.injectEnvironmentVariableValues("${GIT_COMMIT}") : envInjector.injectEnvironmentVariableValues(this.getGitCommit());
        String gitBranch = isNullOrEmpty(this.getGitBranch())?  envInjector.injectEnvironmentVariableValues("${GIT_BRANCH}") : envInjector.injectEnvironmentVariableValues(this.getGitBranch());
        final OctopusBuildInformationBuilder builder = new OctopusBuildInformationBuilder();
        final OctopusBuildInformation buildInformation = builder.build(
                getVcsType(project),
                gitUrl,
                gitCommit,
                getCommits(build, project),
                commentParser,
                envInjector.injectEnvironmentVariableValues("${BUILD_URL}"),
                Integer.toString(build.getNumber()),
                gitBranch
        );

        final String buildInformationFile = "octopus.buildinfo";
        if (verboseLogging) {
            log.info("Creating " + buildInformationFile + " in " + ws.getRemote());
        }
        final OctopusBuildInformationWriter writer = new OctopusBuildInformationWriter(log, verboseLogging);
        writer.writeToFile(ws, buildInformation, buildInformationFile);

        return buildInformationFile;
    }

    private String getVcsType(Job job) {
        SCM scm;
        if (job instanceof AbstractProject) {
            AbstractProject project = (AbstractProject) job;
            scm = project.getScm();
        }
        else {
            WorkflowJob workflowJob = (WorkflowJob) job;
            scm = workflowJob.getTypicalSCM();
        }

        if (scm == null)
            return "Unknown";

        final String scmType = scm.getType().toLowerCase();
        if (scmType.contains("git")) {
            return "Git";
        } else if (scmType.contains("cvs")) {
            return "CVS";
        }
        return "Unknown";
    }

    private List<Commit> getCommits(Run<?,?> build, Job project) {
        List<Commit> commits = new ArrayList<>();
        Run lastSuccessfulBuild = project.getLastSuccessfulBuild();
        Run currentBuild = null;
        if (lastSuccessfulBuild == null) {
            Run lastBuild = project.getLastBuild();
            currentBuild = lastBuild;
        }
        else
        {
            currentBuild = lastSuccessfulBuild.getNextBuild();
        }
        if (currentBuild != null) {
            while (currentBuild != build)
            {
                commits.addAll(convertChangeSetToCommits(currentBuild));

                currentBuild = currentBuild.getNextBuild();
            }
            // Also include the current build
            commits.addAll(convertChangeSetToCommits(build));
        }
        return commits;
    }

    /**
     * Convert a build's change set to a string, each entry on a new line
     * @param run The build to poll changesets from
     * @return The changeset as a string
     */
    private List<Commit> convertChangeSetToCommits(Run<?,?> run) {
        List<Commit> commits = new ArrayList<>();
        if (run != null) {
            List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets = getChangeSets(run);

            for (ChangeLogSet<? extends ChangeLogSet.Entry> changeSet : changeSets)
                for (Object item : changeSet.getItems()) {
                    ChangeLogSet.Entry entry = (ChangeLogSet.Entry) item;
                    final Commit commit = new Commit();
                    commit.Id = entry.getCommitId();
                    commit.Comment = entry.getMsg();
                    commits.add(commit);
                }
        }
        return commits;
    }

    @NotNull
    private List<ChangeLogSet<? extends ChangeLogSet.Entry>> getChangeSets(Run<?, ?> run) {
        if (run instanceof AbstractBuild) {
            AbstractBuild build = (AbstractBuild) run;
            return Collections.singletonList(build.getChangeSet());
        }
        else {
            WorkflowRun workflowRun = (WorkflowRun) run;
            return workflowRun.getChangeSets();
        }
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
