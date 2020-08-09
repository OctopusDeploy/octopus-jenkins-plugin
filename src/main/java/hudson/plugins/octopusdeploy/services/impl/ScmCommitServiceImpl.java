package hudson.plugins.octopusdeploy.services.impl;

import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.model.Run;
import hudson.plugins.octopusdeploy.Commit;
import hudson.plugins.octopusdeploy.EnvironmentVariableValueInjector;
import hudson.plugins.octopusdeploy.Log;
import hudson.plugins.octopusdeploy.OctopusBuildInformation;
import hudson.plugins.octopusdeploy.services.OctopusBuildInformationBuilder;
import hudson.plugins.octopusdeploy.services.OctopusBuildInformationWriter;
import hudson.plugins.octopusdeploy.services.ScmCommitService;
import hudson.scm.ChangeLogSet;
import hudson.scm.SCM;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;

public class ScmCommitServiceImpl implements ScmCommitService {
    private Run<?, ?> run;
    private Log log;

    /**
     * Attempt to load release notes info from SCM.
     * @param envInjector the environment variable injector
     * @param workspace
     * @return path to build information file
     */
    @Override
    public @NotNull String getBuildInformationFromScm(EnvironmentVariableValueInjector envInjector, FilePath workspace, String gitUrl, String gitCommit, String commentParser, boolean verboseLogging) throws IOException, InterruptedException{
        checkNotNull(this.run, "build cannot be null");

        FilePath ws = workspace;
        Job project = run.getParent();

        String giturl = isNullOrEmpty(gitUrl) ? envInjector.injectEnvironmentVariableValues("${GIT_URL}") : envInjector.injectEnvironmentVariableValues(gitUrl);
        String gitcommit = isNullOrEmpty(gitCommit)?  envInjector.injectEnvironmentVariableValues("${GIT_COMMIT}") : envInjector.injectEnvironmentVariableValues(gitCommit);
        final OctopusBuildInformationBuilder builder = new OctopusBuildInformationBuilder();
        final OctopusBuildInformation buildInformation = builder.build(
                getVcsType(project),
                giturl,
                gitcommit,
                getCommits(run, project),
                commentParser,
                envInjector.injectEnvironmentVariableValues("${BUILD_URL}"),
                Integer.toString(run.getNumber())
        );

        final String buildInformationFile = "octopus.buildinfo";
        if (verboseLogging) {
            log.info("Creating " + buildInformationFile + " in " + ws.getRemote());
        }
        final OctopusBuildInformationWriter writer = new OctopusBuildInformationWriter(log, verboseLogging);
        writer.writeToFile(ws, buildInformation, buildInformationFile);

        return buildInformationFile;
    }

    @Override
    public Run<?, ?> getRun() {
        return this.run;
    }

    @Override
    public void setRun(Run<?, ?> run) {
        this.run = run;
    }

    @Override
    public Log getLog() {
        return this.log;
    }

    @Override
    public void setLog(Log log) {
        this.log = log;
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
}
