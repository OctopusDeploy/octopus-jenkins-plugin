package hudson.plugins.octopusdeploy.services;

import hudson.FilePath;
import hudson.model.Run;
import hudson.plugins.octopusdeploy.EnvironmentVariableValueInjector;
import hudson.plugins.octopusdeploy.Log;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public interface ScmCommitService {
    @NotNull
    String getBuildInformationFromScm(
            EnvironmentVariableValueInjector envInjector,
            FilePath workspace,
            String gitUrl,
            String gitCommit,
            String commentParser,
            boolean verboseLogging) throws IOException, InterruptedException;

    Run<?,?> getRun();
    void setRun(Run<?,?> run);

    Log getLog();
    void setLog(Log log);
}
